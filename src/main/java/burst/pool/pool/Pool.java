package burst.pool.pool;

import burst.kit.crypto.BurstCrypto;
import burst.kit.entity.BurstAddress;
import burst.kit.entity.response.Block;
import burst.kit.entity.response.MiningInfo;
import burst.kit.entity.response.http.MiningInfoResponse;
import burst.kit.service.BurstNodeService;
import burst.pool.miners.MinerTracker;
import burst.pool.payout.PayoutService;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.Props;
import burst.pool.storage.persistent.StorageService;
import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class Pool {
    private static final Logger logger = LoggerFactory.getLogger(Pool.class);

    private final BurstNodeService nodeService;
    private final BurstCrypto burstCrypto = BurstCrypto.getInstance();
    private final StorageService storageService;
    private final PropertyService propertyService;
    private final MinerTracker minerTracker;
    private final PayoutService payoutService;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final Semaphore processBlockSemaphore = new Semaphore(1);
    private final Semaphore resetRoundSemaphore = new Semaphore(1);
    private final Semaphore processDeadlineSemaphore = new Semaphore(1);

    // Variables
    private final AtomicReference<Instant> roundStartTime = new AtomicReference<>(Instant.now());
    private final AtomicReference<Submission> bestSubmission = new AtomicReference<>();
    private final AtomicReference<BigInteger> bestDeadline = new AtomicReference<>();
    private final AtomicReference<MiningInfo> miningInfo = new AtomicReference<>();
    private final Set<BurstAddress> myRewardRecipients = new HashSet<>();

    public Pool(BurstNodeService nodeService, StorageService storageService, PropertyService propertyService, MinerTracker minerTracker, PayoutService payoutService) {
        this.storageService = storageService;
        this.minerTracker = minerTracker;
        this.propertyService = propertyService;
        this.nodeService = nodeService;
        this.payoutService = payoutService;
        disposables.add(refreshMiningInfoThread());
        disposables.add(processBlocksThread());
        resetRound(null);
    }

    private Disposable processBlocksThread() {
        return Observable.interval(0, 1, TimeUnit.SECONDS)
                .flatMapCompletable(l -> processNextBlock().onErrorComplete(e -> {
                    onProcessBlocksError(e, false);
                    return true;
                }))
                .retry()
                .subscribeOn(Schedulers.io())
                .subscribe(() -> {}, e -> onProcessBlocksError(e, true));
    }

    private void onProcessBlocksError(Throwable throwable, boolean fatal) {
        if (fatal) {
            logger.error("Fatal error processing blocks (Thread now shutdown)", throwable);
        } else {
            logger.warn("Non-fatal error processing blocks", throwable);
        }
    }

    private Disposable refreshMiningInfoThread() {
        return nodeService.getMiningInfo()
                .retry()
                .subscribeOn(Schedulers.io())
                .subscribe(this::onMiningInfo, e -> onMiningInfoError(e, true));
    }

    private void onMiningInfo(MiningInfo newMiningInfo) {
        if (miningInfo.get() == null || !Arrays.equals(miningInfo.get().getGenerationSignature(), newMiningInfo.getGenerationSignature())
                || !Objects.equals(miningInfo.get().getHeight(), newMiningInfo.getHeight())) {
            logger.info("NEW BLOCK (block " + newMiningInfo.getHeight() + ", gensig " + burstCrypto.toHexString(newMiningInfo.getGenerationSignature()) +", diff " + newMiningInfo.getBaseTarget() + ")");
            resetRound(newMiningInfo);
        }
    }

    private void onMiningInfoError(Throwable throwable, boolean fatal) {
        if (fatal) {
            logger.error("Fatal error fetching mining info (Thread now shutdown)", throwable);
        } else {
            logger.warn("Non-fatal error fetching mining info", throwable);
        }
    }

    private Completable processNextBlock() {
        return Completable.fromAction(() -> {
            StorageService transactionalStorageService = null;
            try {
                if (miningInfo.get() == null || processBlockSemaphore.availablePermits() == 0 || miningInfo.get().getHeight() - 1 <= storageService.getLastProcessedBlock() + propertyService.getInt(Props.processLag)) {
                    return;
                }

                try {
                    processBlockSemaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                try {
                    transactionalStorageService = storageService.beginTransaction();
                } catch (Exception e) {
                    logger.error("Could not open transactional storage service", e);
                    processBlockSemaphore.release();
                    return;
                }

                minerTracker.setCurrentlyProcessingBlock(true);

                List<Long> fastBlocks = new ArrayList<>();
                transactionalStorageService.getBestSubmissions().forEach((height, deadline) -> {
                    long lowestDeadline = deadline.stream()
                            .map(StoredSubmission::getDeadline)
                            .min(Long::compare)
                            .orElse((long) propertyService.getInt(Props.tMin));
                    if (lowestDeadline < propertyService.getInt(Props.tMin)) {
                        fastBlocks.add(height);
                    }
                });

                List<StoredSubmission> storedSubmissions = transactionalStorageService.getBestSubmissionsForBlock(transactionalStorageService.getLastProcessedBlock() + 1);
                if (storedSubmissions == null || storedSubmissions.isEmpty()) {
                    onProcessedBlock(transactionalStorageService, false);
                    return;
                }

                Block block = nodeService.getBlock(transactionalStorageService.getLastProcessedBlock() + 1).blockingGet();

                List<? extends Submission> submissions = transactionalStorageService.getBestSubmissionsForBlock(block.getHeight());
                boolean won = false;
                if (submissions != null && !submissions.isEmpty()) {
                    for (Submission submission : submissions) {
                        if (Objects.equals(block.getGenerator(), submission.getMiner()) && Objects.equals(block.getNonce(), submission.getNonce())) {
                            won = true;
                        }
                    }
                }
                if (won) {
                    minerTracker.onBlockWon(transactionalStorageService, transactionalStorageService.getLastProcessedBlock() + 1, block.getId(), block.getNonce(), block.getGenerator(), block.getBlockReward().add(block.getTotalFee()), fastBlocks);
                } else {
                    if (myRewardRecipients.contains(block.getGenerator())) {
                        logger.error("Our miner forged but did not detect block won. Height " + block.getHeight());
                    }
                    minerTracker.onBlockNotWon(transactionalStorageService, transactionalStorageService.getLastProcessedBlock() + 1, fastBlocks);
                }
                onProcessedBlock(transactionalStorageService, true);
            } catch (Exception e) {
                if (transactionalStorageService != null) {
                    logger.warn("Error processing block " + transactionalStorageService.getLastProcessedBlock() + 1, e);
                    try {
                        transactionalStorageService.rollbackTransaction();
                        transactionalStorageService.close();
                    } catch (Exception e1) {
                        logger.error("Error rolling back transaction", e1);
                    }
                }
                minerTracker.setCurrentlyProcessingBlock(false);
                processBlockSemaphore.release();
            }
        });
    }

    private void onProcessedBlock(StorageService transactionalStorageService, boolean actuallyProcessed) {
        // TODO this needs to be done if block is behind nAvg otherwise fast block calculation breaks
        //storageService.removeBestSubmission(storageService.getLastProcessedBlock() + 1);
        transactionalStorageService.incrementLastProcessedBlock();
        try {
            transactionalStorageService.commitTransaction();
            transactionalStorageService.close();
        } catch (Exception e) {
            logger.error("Error committing transaction", e);
        }
        minerTracker.setCurrentlyProcessingBlock(false);
        processBlockSemaphore.release();
        if (actuallyProcessed) {
            payoutService.payoutIfNeeded(storageService);
        }
    }

    private void resetRound(MiningInfo newMiningInfo) {
        // Traffic flow - we want to stop new requests but let old ones finish before we go ahead.
        try {
            // Lock the reset round semaphore to stop accepting new requests
            resetRoundSemaphore.acquire();
            // Wait for all requests to be processed
            while (processDeadlineSemaphore.getQueueLength() > 0) {
                Thread.sleep(1);
            }
            // Lock the process block semaphore as we are going to be modifying bestSubmission
            processDeadlineSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        bestSubmission.set(null);
        bestDeadline.set(BigInteger.valueOf(Long.MAX_VALUE));
        disposables.add(nodeService.getAccountsWithRewardRecipient(burstCrypto.getBurstAddressFromPassphrase(propertyService.getString(Props.passphrase)))
                .subscribe(this::onRewardRecipients, this::onRewardRecipientsError));
        roundStartTime.set(Instant.now());
        miningInfo.set(newMiningInfo);
        // Unlock to signal we have finished modifying bestSubmission
        processDeadlineSemaphore.release();
        // Unlock to start accepting requests again
        resetRoundSemaphore.release();
    }

    BigInteger checkNewSubmission(Submission submission, String userAgent) throws SubmissionException {
        if (miningInfo.get() == null) {
            throw new SubmissionException("Pool does not have mining info");
        }

        if (!myRewardRecipients.contains(submission.getMiner())) {
            throw new SubmissionException("Reward recipient not set to pool");
        }

        // If we are resetting the request must be for the previous round and no longer matters - reject
        if (resetRoundSemaphore.availablePermits() < 0) {
            throw new SubmissionException("Cannot submit - new round starting");
        }

        MiningInfo localMiningInfo = miningInfo.get();

        // TODO poc2 switch
        BigInteger deadline = burstCrypto.calculateDeadline(submission.getMiner(), Long.parseUnsignedLong(submission.getNonce().toString()), localMiningInfo.getGenerationSignature(), burstCrypto.calculateScoop(localMiningInfo.getGenerationSignature(), localMiningInfo.getHeight()), localMiningInfo.getBaseTarget(), 2);
        BigInteger maxDeadline = BigInteger.valueOf(propertyService.getLong(Props.maxDeadline));

        if (deadline.compareTo(maxDeadline) > 0) {
            throw new SubmissionException("Deadline exceeds maximum allowed deadline (Submitted " + deadline.toString() + ", maximum is " + maxDeadline.toString() + ")");
        }

        if (logger.isDebugEnabled()) {
            logger.debug("New submission from {} of nonce {}, calculated deadline {} seconds.", submission.getMiner(), submission.getNonce(), deadline.toString());
        }

        try {
            try {
                processDeadlineSemaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SubmissionException("Server Interrupted");
            }

            if (bestSubmission.get() != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Best deadline is {}, new deadline is {}", bestDeadline.get(), deadline);
                }
                if (deadline.compareTo(bestDeadline.get()) < 0) {
                    logger.debug("Newer deadline is better! Submitting...");
                    onNewBestDeadline(miningInfo.get().getHeight(), submission, deadline);
                }
            } else {
                logger.debug("This is the first deadline, submitting...");
                onNewBestDeadline(miningInfo.get().getHeight(), submission, deadline);
            }

            minerTracker.onMinerSubmittedDeadline(storageService, submission.getMiner(), deadline, BigInteger.valueOf(miningInfo.get().getBaseTarget()), miningInfo.get().getHeight(), userAgent);
            return deadline;
        } finally {
            processDeadlineSemaphore.release();
        }
    }

    private void onNewBestDeadline(long blockHeight, Submission submission, BigInteger deadline) {
        bestSubmission.set(submission);
        bestDeadline.set(deadline);
        submitDeadline(submission);
        storageService.addBestSubmissionForBlock(blockHeight, new StoredSubmission(submission.getMiner(), submission.getNonce(), deadline.longValue()));
    }

    private void submitDeadline(Submission submission) {
        disposables.add(nodeService.submitNonce(propertyService.getString(Props.passphrase), submission.getNonce().toString(), submission.getMiner().getBurstID()) // TODO burstkit4j accept nonce as bigint
                .retry(propertyService.getInt(Props.submitNonceRetryCount))
                .subscribe(this::onNonceSubmitted, this::onSubmitNonceError));
    }

    private void onRewardRecipients(BurstAddress[] rewardRecipients) {
        myRewardRecipients.clear();
        myRewardRecipients.addAll(Arrays.asList(rewardRecipients));
    }

    private void onRewardRecipientsError(Throwable t) {
        logger.error("Error fetching pool's reward recipients", t);
    }

    private void onNonceSubmitted(long deadline) {
        logger.debug("Submitted nonce to node. Deadline is {}", Long.toUnsignedString(deadline));
    }

    private void onSubmitNonceError(Throwable t) {
        logger.error("Error submitting nonce to node", t);
    }

    MiningInfo getMiningInfo() {
        return miningInfo.get();
    }

    public JsonObject getCurrentRoundInfo(Gson gson) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("roundStart", roundStartTime.get().getEpochSecond());
        if (bestSubmission.get() != null) {
            JsonObject bestDeadlineJson = new JsonObject();
            bestDeadlineJson.addProperty("miner", bestSubmission.get().getMiner().getID());
            bestDeadlineJson.addProperty("minerRS", bestSubmission.get().getMiner().getFullAddress());
            bestDeadlineJson.addProperty("nonce", bestSubmission.get().getNonce());
            bestDeadlineJson.addProperty("deadline", bestDeadline.get());
            jsonObject.add("bestDeadline", bestDeadlineJson);
        } else {
            jsonObject.add("bestDeadline", JsonNull.INSTANCE);
        }
        MiningInfo miningInfo = Pool.this.miningInfo.get();
        if (miningInfo != null) {
            jsonObject.add("miningInfo", gson.toJsonTree(new MiningInfoResponse(burstCrypto.toHexString(miningInfo.getGenerationSignature()), miningInfo.getBaseTarget(), miningInfo.getHeight())));
        }
        return jsonObject;
    }

    public BurstAddress getAccount() {
        return burstCrypto.getBurstAddressFromPassphrase(propertyService.getString(Props.passphrase));
    }
}
