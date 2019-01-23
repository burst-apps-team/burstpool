package burst.pool.pool;

import burst.kit.burst.BurstCrypto;
import burst.kit.entity.BurstAddress;
import burst.kit.entity.response.MiningInfoResponse;
import burst.kit.entity.response.SubmitNonceResponse;
import burst.kit.service.BurstNodeService;
import burst.pool.brs.Generator;
import burst.pool.brs.MiningPlot;
import burst.pool.brs.Shabal256;
import com.google.gson.Gson;
import fi.iki.elonen.util.ServerRunner;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

public class Pool {
    private final BurstNodeService nodeService;
    private final BurstCrypto burstCrypto = BurstCrypto.getInstance();
    private final String passphrase = "a";
    private final long maxDeadline = Long.MAX_VALUE;

    private final AtomicReference<Submission> bestDeadline = new AtomicReference<>(); // todo
    public final AtomicReference<MiningInfoResponse> miningInfo = new AtomicReference<>();

    private final Object submitLock = new Object();

    private final Map<BurstAddress, String> submissions = new ConcurrentHashMap<>();
    private final Set<BurstAddress> myRewardRecipients = new HashSet<>();

    private Pool() {
        this.nodeService = BurstNodeService.getInstance("http://localhost:6876");
        refreshMiningInfoThread();
    }

    public static void main(String[] args) {
        ServerRunner.executeInstance(new Server(new Pool()));
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            System.out.println("interrupted!");
            Thread.currentThread().interrupt();
        }
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

    public void resetRound() {
        submissions.clear();
        bestDeadline.set(null);
        nodeService.getAccountsWithRewardRecipient(burstCrypto.getBurstAddressFromPassphrase(passphrase))
                .subscribe(response -> {
                    if (response.hasError()) {
                        System.out.println("ERROR: " + response.getErrorDescription());
                    }
                    myRewardRecipients.clear();
                    myRewardRecipients.addAll(Arrays.asList(response.getAccounts()));
                }, Throwable::printStackTrace);
    }

    public BigInteger checkNewSubmission(Submission submission) throws SubmissionException {
        if (!myRewardRecipients.contains(submission.getMiner())) {
            throw new SubmissionException("Reward recipient not set to pool");
        }

        BigInteger deadline = Generator.calcDeadline(miningInfo.get(), submission);
        System.out.println("New submission from " + submission.getMiner() + ", calculated deadline " + deadline.toString() + " seconds.");

        if (deadline.compareTo(BigInteger.valueOf(maxDeadline)) >= 0) {
            throw new SubmissionException("Deadline exceeds maximum allowed deadline");
        }

        submissions.remove(submission.getMiner());
        submissions.put(submission.getMiner(), submission.getNonce());

        if (bestDeadline.get() != null) {
            System.out.println("Best deadline is " + Generator.calcDeadline(miningInfo.get(), bestDeadline.get()) + ", new deadline is " + deadline);
            if (deadline.compareTo(Generator.calcDeadline(miningInfo.get(), bestDeadline.get())) < 0) {
                System.out.println("Newer deadline is better!");
                onNewBestDeadline(submission);
            }
        } else {
            System.out.println("This is the first deadline, submitting...");
            onNewBestDeadline(submission);
        }

        return deadline;
    }

    private void onNewBestDeadline(Submission submission) {
        bestDeadline.set(submission);
        submitDeadline(submission);
    }

    private void submitDeadline(Submission submission) {
        synchronized (submitLock) {
            nodeService.submitNonce(passphrase, submission.getNonce(), submission.getMiner().getBurstID())
                    .subscribe(this::onNonceSubmitted, this::onSubmitNonceError);
        }
    }

    private void onNonceSubmitted(SubmitNonceResponse response) {
        System.out.println(new Gson().toJson(response));
    }

    private void onSubmitNonceError(Throwable t) {
        t.printStackTrace();
    }
}
