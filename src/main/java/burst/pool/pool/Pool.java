package burst.pool.pool;

import burst.kit.burst.BurstCrypto;
import burst.kit.entity.BurstAddress;
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

import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class Pool {
    private final BurstNodeService nodeService;
    private final BurstCrypto burstCrypto = BurstCrypto.getInstance();
    private final StorageService storageService;
    private final PropertyService propertyService;
    private final MinerTracker minerTracker;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final Semaphore processBlockSemaphore = new Semaphore(1);

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
                .flatMapCompletable(l -> processNextBlock())
                .doOnError(Throwable::printStackTrace) // todo thread stops on error :(
                .subscribe(() -> {}, this::onProcessBlocksError);
    }

    private void onProcessBlocksError(Throwable throwable) {
        System.out.println("opbe");
        throwable.printStackTrace();
    }

    private Disposable refreshMiningInfoThread() {
        return Observable.interval(1, TimeUnit.SECONDS)
                .flatMapSingle(l -> nodeService.getMiningInfo())
                .doOnError(Throwable::printStackTrace) // todo thread stops on error :(
                .forEach(this::onMiningInfo);
    }

    private void onMiningInfo(MiningInfoResponse newMiningInfo) {
        if (miningInfo.get() == null || !Objects.equals(miningInfo.get().getGenerationSignature(), newMiningInfo.getGenerationSignature())
                || !Objects.equals(miningInfo.get().getHeight(), newMiningInfo.getHeight())) {
            System.out.println("NEW BLOCK (block " + newMiningInfo.getHeight() + ", gensig " + newMiningInfo.getGenerationSignature() +", diff " + newMiningInfo.getBaseTarget() + ")");
            miningInfo.set(newMiningInfo);
            resetRound();
        }
    }

    private Completable processNextBlock() {
        if (miningInfo.get() == null || miningInfo.get().getHeight() <= storageService.getLastProcessedBlock() + propertyService.getInt(Props.processLag)) {
            return Completable.complete();
        }

        try {
            processBlockSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (storageService.getBestSubmissionForBlock(storageService.getLastProcessedBlock()) == null) {
            storageService.incrementLastProcessedBlock();
            processBlockSemaphore.release();
            return Completable.complete();
        }

        System.out.println(storageService.getLastProcessedBlock() + 1);
        return nodeService.getBlock(storageService.getLastProcessedBlock() + 1)
                .flatMapCompletable(block -> Completable.fromAction(() -> {
                    Submission submission = storageService.getBestSubmissionForBlock(block.getHeight());
                    if (submission == null || !Objects.equals(block.getGenerator(), submission.getMiner()) || !Objects.equals(block.getNonce(), submission.getNonce())) return;
                    minerTracker.onBlockWon(storageService.getLastProcessedBlock() + 1, block.getGenerator(), block.getBlockReward());
                }))
                .doOnComplete(() -> {
                    storageService.incrementLastProcessedBlock();
                    processBlockSemaphore.release(); // todo what if errored?
                });
    }

    private void resetRound() {
        bestDeadline.set(null);
        disposables.add(nodeService.getAccountsWithRewardRecipient(burstCrypto.getBurstAddressFromPassphrase(propertyService.getString(Props.passphrase)))
                .subscribe(this::onRewardRecipients, this::onRewardRecipientsError));
        roundStartTime.set(Instant.now());
    }

    BigInteger checkNewSubmission(Submission submission) throws SubmissionException {
        if (miningInfo.get() == null) {
            throw new SubmissionException("Pool does not have mining info");
        }

        if (!myRewardRecipients.contains(submission.getMiner())) {
            throw new SubmissionException("Reward recipient not set to pool");
        }

        BigInteger deadline = Generator.calcDeadline(miningInfo.get(), submission);
        System.out.println("New submission from " + submission.getMiner() + ", calculated deadline " + deadline.toString() + " seconds.");

        if (deadline.compareTo(BigInteger.valueOf(propertyService.getLong(Props.maxDeadline))) >= 0) {
            throw new SubmissionException("Deadline exceeds maximum allowed deadline");
        }

        if (bestDeadline.get() != null) {
            System.out.println("Best deadline is " + Generator.calcDeadline(miningInfo.get(), bestDeadline.get()) + ", new deadline is " + deadline);
            if (deadline.compareTo(Generator.calcDeadline(miningInfo.get(), bestDeadline.get())) < 0) {
                System.out.println("Newer deadline is better! Submitting...");
                onNewBestDeadline(miningInfo.get().getHeight(), submission);
            }
        } else {
            System.out.println("This is the first deadline, submitting...");
            onNewBestDeadline(miningInfo.get().getHeight(), submission);
        }

        minerTracker.onMinerSubmittedDeadline(submission.getMiner(), deadline, BigInteger.valueOf(miningInfo.get().getBaseTarget()), miningInfo.get().getHeight());

        return deadline;
    }

    private void onNewBestDeadline(long blockHeight, Submission submission) {
        bestDeadline.set(submission);
        submitDeadline(submission);
        storageService.setBestSubmissionForBlock(blockHeight, submission);
    }

    private void submitDeadline(Submission submission) {
        disposables.add(nodeService.submitNonce(propertyService.getString(Props.passphrase), submission.getNonce(), submission.getMiner().getBurstID())
                .subscribe(this::onNonceSubmitted, this::onSubmitNonceError));
    }

    private void onRewardRecipients(AccountsWithRewardRecipientResponse rewardRecipients) {
        myRewardRecipients.clear();
        myRewardRecipients.addAll(Arrays.asList(rewardRecipients.getAccounts()));
    }

    private void onRewardRecipientsError(Throwable t) {
        System.out.println("orre");
        t.printStackTrace();
    }

    private void onNonceSubmitted(SubmitNonceResponse response) {
        System.out.println(new Gson().toJson(response));
    }

    private void onSubmitNonceError(Throwable t) {
        System.out.println("osne");
        t.printStackTrace();
    }

    MiningInfoResponse getMiningInfo() {
        return miningInfo.get();
    }

    public JsonObject getCurrentRoundInfo(Gson gson) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("roundStart", roundStartTime.get().getEpochSecond());
        if (bestDeadline.get() != null) {
            JsonObject bestDeadlineJson = new JsonObject();
            bestDeadlineJson.addProperty("miner", bestDeadline.get().getMiner().getID());
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
}
