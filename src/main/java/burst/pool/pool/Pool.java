package burst.pool.pool;

import burst.kit.burst.BurstCrypto;
import burst.kit.entity.BurstAddress;
import burst.kit.entity.response.AccountsWithRewardRecipientResponse;
import burst.kit.entity.response.MiningInfoResponse;
import burst.kit.entity.response.SubmitNonceResponse;
import burst.kit.service.BurstNodeService;
import burst.pool.brs.Generator;
import burst.pool.miners.MinerTracker;
import com.google.gson.Gson;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class Pool {
    private final BurstNodeService nodeService;
    private final BurstCrypto burstCrypto = BurstCrypto.getInstance();
    private final String passphrase = "a";
    private final long maxDeadline = Long.MAX_VALUE;

    private final AtomicReference<Submission> bestDeadline = new AtomicReference<>(); // todo
    private final AtomicReference<MiningInfoResponse> miningInfo = new AtomicReference<>();
    private final Set<BurstAddress> myRewardRecipients = new HashSet<>();

    private final MinerTracker minerTracker;
    private final CompositeDisposable disposables = new CompositeDisposable();

    public Pool(MinerTracker minerTracker) {
        this.minerTracker = minerTracker;
        this.nodeService = BurstNodeService.getInstance("http://localhost:6876");
        disposables.add(refreshMiningInfoThread());
        resetRound();
    }

    private Disposable refreshMiningInfoThread() {
        return Observable.interval(0, 1, TimeUnit.SECONDS)
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

    private void resetRound() {
        bestDeadline.set(null);
        disposables.add(nodeService.getAccountsWithRewardRecipient(burstCrypto.getBurstAddressFromPassphrase(passphrase))
                .subscribe(this::onRewardRecipients, this::onRewardRecipientsError));
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

        if (deadline.compareTo(BigInteger.valueOf(maxDeadline)) >= 0) {
            throw new SubmissionException("Deadline exceeds maximum allowed deadline");
        }

        if (bestDeadline.get() != null) {
            System.out.println("Best deadline is " + Generator.calcDeadline(miningInfo.get(), bestDeadline.get()) + ", new deadline is " + deadline);
            if (deadline.compareTo(Generator.calcDeadline(miningInfo.get(), bestDeadline.get())) < 0) {
                System.out.println("Newer deadline is better! Submitting...");
                onNewBestDeadline(submission);
            }
        } else {
            System.out.println("This is the first deadline, submitting...");
            onNewBestDeadline(submission);
        }

        minerTracker.onMinerSubmittedDeadline(miningInfo.get().getHeight(), submission, deadline);

        return deadline;
    }

    private void onNewBestDeadline(Submission submission) {
        bestDeadline.set(submission);
        submitDeadline(submission);
    }

    private void submitDeadline(Submission submission) {
        disposables.add(nodeService.submitNonce(passphrase, submission.getNonce(), submission.getMiner().getBurstID())
                .subscribe(this::onNonceSubmitted, this::onSubmitNonceError));
    }

    private void onRewardRecipients(AccountsWithRewardRecipientResponse rewardRecipients) {
        if (rewardRecipients.hasError()) {
            System.out.println("ERROR: " + rewardRecipients.getErrorDescription());
            return;
        }
        myRewardRecipients.clear();
        myRewardRecipients.addAll(Arrays.asList(rewardRecipients.getAccounts()));
    }

    private void onRewardRecipientsError(Throwable t) {
        t.printStackTrace();
    }

    private void onNonceSubmitted(SubmitNonceResponse response) {
        System.out.println(new Gson().toJson(response));
    }

    private void onSubmitNonceError(Throwable t) {
        t.printStackTrace();
    }

    MiningInfoResponse getMiningInfo() {
        return miningInfo.get();
    }
}
