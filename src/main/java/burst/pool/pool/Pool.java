package burst.pool.pool;

import burst.kit.burst.BurstCrypto;
import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;
import burst.kit.entity.response.AccountsWithRewardRecipientResponse;
import burst.kit.entity.response.MiningInfoResponse;
import burst.kit.entity.response.SubmitNonceResponse;
import burst.kit.service.BurstNodeService;
import burst.pool.brs.Generator;
import burst.pool.miners.MinerTracker;
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
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final Semaphore processBlockSemaphore = new Semaphore(1);
    private final Object processDeadlineLock = new Object();

    // Variables
    private final AtomicReference<Instant> roundStartTime = new AtomicReference<>();
    private final AtomicReference<Submission> bestDeadline = new AtomicReference<>();
    private final AtomicReference<MiningInfoResponse> miningInfo = new AtomicReference<>();
    private final Set<BurstAddress> myRewardRecipients = new HashSet<>();

    public Pool(BurstNodeService nodeService, StorageService storageService, PropertyService propertyService, MinerTracker minerTracker) {
        this.storageService = storageService;
        this.minerTracker = minerTracker;
        this.propertyService = propertyService;
        this.nodeService = nodeService;
        disposables.add(refreshMiningInfoThread());
        disposables.add(processBlocksThread());
        resetRound();
    }

    private Disposable processBlocksThread() {
        return Observable.interval(0, 1, TimeUnit.MILLISECONDS)
                .flatMapCompletable(l -> processNextBlock().onErrorComplete(e -> {
                    onProcessBlocksError(e, false);
                    return true;
                }))
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
        return Observable.interval(1, TimeUnit.SECONDS)
                .flatMapSingle(l -> nodeService.getMiningInfo())
                .retryWhen(throwableObservable -> throwableObservable.flatMap(throwable -> {
                    onMiningInfoError(throwable, false);
                    return Observable.timer(1, TimeUnit.SECONDS);
                }))
                .subscribe(this::onMiningInfo, e -> onMiningInfoError(e, true));
    }

    private void onMiningInfo(MiningInfoResponse newMiningInfo) {
        if (miningInfo.get() == null || !Objects.equals(miningInfo.get().getGenerationSignature(), newMiningInfo.getGenerationSignature())
                || !Objects.equals(miningInfo.get().getHeight(), newMiningInfo.getHeight())) {
            logger.info("NEW BLOCK (block " + newMiningInfo.getHeight() + ", gensig " + newMiningInfo.getGenerationSignature() +", diff " + newMiningInfo.getBaseTarget() + ")");
            miningInfo.set(newMiningInfo);
            resetRound();
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
        if (miningInfo.get() == null || processBlockSemaphore.availablePermits() == 0 || miningInfo.get().getHeight() - 1 <= storageService.getLastProcessedBlock() + propertyService.getInt(Props.processLag)) {
            return Completable.complete();
        }

        try {
            processBlockSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<Long> fastBlocks = new ArrayList<>();
        storageService.getBestSubmissions().forEach((height, deadline) -> {
            if (deadline.getDeadline() < propertyService.getInt(Props.tMin)) {
                fastBlocks.add(height);
            }
        });

        if (storageService.getBestSubmissionForBlock(storageService.getLastProcessedBlock() + 1) == null) {
            onProcessedBlock();
            return Completable.complete();
        }

        return nodeService.getBlock(storageService.getLastProcessedBlock() + 1)
                .flatMapCompletable(block -> Completable.fromAction(() -> {
                    Submission submission = storageService.getBestSubmissionForBlock(block.getHeight());
                    if (submission != null && Objects.equals(block.getGenerator(), submission.getMiner()) && Objects.equals(block.getNonce(), submission.getNonce())) {
                        minerTracker.onBlockWon(storageService.getLastProcessedBlock() + 1, block.getGenerator(), new BurstValue(block.getBlockReward().add(block.getTotalFeeNQT())), fastBlocks);
                    } else {
                        minerTracker.onBlockNotWon(storageService.getLastProcessedBlock() + 1, fastBlocks);
                    }
                }))
                .doOnComplete(this::onProcessedBlock)
                .onErrorComplete(t -> {
                    logger.warn("Error processing block " + storageService.getLastProcessedBlock() + 1, t);
                    processBlockSemaphore.release();
                    return true;
                });
    }

    private void onProcessedBlock() {
        storageService.removeBestSubmission(storageService.getLastProcessedBlock() + 1);
        storageService.incrementLastProcessedBlock();
        processBlockSemaphore.release();
    }

    private void resetRound() {
        bestDeadline.set(null);
        disposables.add(nodeService.getAccountsWithRewardRecipient(burstCrypto.getBurstAddressFromPassphrase(propertyService.getString(Props.passphrase)))
                .subscribe(this::onRewardRecipients, this::onRewardRecipientsError));
        roundStartTime.set(Instant.now());
    }

    BigInteger checkNewSubmission(Submission submission, String userAgent) throws SubmissionException {
        if (miningInfo.get() == null) {
            throw new SubmissionException("Pool does not have mining info");
        }

        if (!myRewardRecipients.contains(submission.getMiner())) {
            throw new SubmissionException("Reward recipient not set to pool");
        }

        BigInteger deadline = Generator.calcDeadline(miningInfo.get(), submission);

        if (deadline.compareTo(BigInteger.valueOf(propertyService.getLong(Props.maxDeadline))) >= 0) {
            throw new SubmissionException("Deadline exceeds maximum allowed deadline");
        }

        synchronized (processDeadlineLock) {
            logger.debug("New submission from " + submission.getMiner() + " of nonce " + submission.getNonce() + ", calculated deadline " + deadline.toString() + " seconds.");

            if (bestDeadline.get() != null) {
                logger.debug("Best deadline is " + Generator.calcDeadline(miningInfo.get(), bestDeadline.get()) + ", new deadline is " + deadline);
                if (deadline.compareTo(Generator.calcDeadline(miningInfo.get(), bestDeadline.get())) < 0) {
                    logger.debug("Newer deadline is better! Submitting...");
                    onNewBestDeadline(miningInfo.get().getHeight(), submission);
                }
            } else {
                logger.debug("This is the first deadline, submitting...");
                onNewBestDeadline(miningInfo.get().getHeight(), submission);
            }

            minerTracker.onMinerSubmittedDeadline(submission.getMiner(), deadline, BigInteger.valueOf(miningInfo.get().getBaseTarget()), miningInfo.get().getHeight(), userAgent);

            return deadline;
        }
    }

    private void onNewBestDeadline(long blockHeight, Submission submission) throws SubmissionException {
        bestDeadline.set(submission);
        submitDeadline(submission);
        storageService.setOrUpdateBestSubmissionForBlock(blockHeight, new StoredSubmission(submission.getMiner(), submission.getNonce(), Generator.calcDeadline(miningInfo.get(), submission).longValue()));
    }

    private void submitDeadline(Submission submission) {
        disposables.add(nodeService.submitNonce(propertyService.getString(Props.passphrase), submission.getNonce(), submission.getMiner().getBurstID())
                .retry(propertyService.getInt(Props.submitNonceRetryCount))
                .subscribe(this::onNonceSubmitted, this::onSubmitNonceError));
    }

    private void onRewardRecipients(AccountsWithRewardRecipientResponse rewardRecipients) {
        myRewardRecipients.clear();
        myRewardRecipients.addAll(Arrays.asList(rewardRecipients.getAccounts()));
    }

    private void onRewardRecipientsError(Throwable t) {
        logger.error("Error fetching pool's reward recipients", t);
    }

    private void onNonceSubmitted(SubmitNonceResponse response) {
        logger.debug("Submitted nonce to node. Result is \"" + response.getResult() + "\", deadline is " + Long.toUnsignedString(response.getDeadline()));
    }

    private void onSubmitNonceError(Throwable t) {
        logger.error("Error submitting nonce to node", t);
    }

    MiningInfoResponse getMiningInfo() {
        return miningInfo.get();
    }

    public JsonObject getCurrentRoundInfo(Gson gson) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("roundStart", roundStartTime.get().getEpochSecond());
        jsonObject.addProperty("roundElapsed", Instant.now().getEpochSecond() - roundStartTime.get().getEpochSecond());
        if (bestDeadline.get() != null) {
            JsonObject bestDeadlineJson = new JsonObject();
            bestDeadlineJson.addProperty("miner", bestDeadline.get().getMiner().getID());
            bestDeadlineJson.addProperty("minerRS", bestDeadline.get().getMiner().getFullAddress());
            bestDeadlineJson.addProperty("nonce", bestDeadline.get().getNonce());
            try {
                bestDeadlineJson.addProperty("deadline", Generator.calcDeadline(miningInfo.get(), bestDeadline.get()));
            } catch (SubmissionException ignored) {
            }
            jsonObject.add("bestDeadline", bestDeadlineJson);
        } else {
            jsonObject.add("bestDeadline", JsonNull.INSTANCE);
        }
        jsonObject.add("miningInfo", gson.toJsonTree(miningInfo.get()));
        return jsonObject;
    }

    public BurstAddress getAccount() {
        return burstCrypto.getBurstAddressFromPassphrase(propertyService.getString(Props.passphrase));
    }
}
