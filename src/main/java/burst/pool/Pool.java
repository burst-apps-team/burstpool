package burst.pool;

import burst.kit.burst.BurstCrypto;
import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;
import burst.kit.entity.HexStringByteArray;
import burst.kit.entity.response.MiningInfoResponse;
import burst.kit.entity.response.SubmitNonceResponse;
import burst.kit.service.BurstNodeService;
import burst.kit.util.BurstKitUtils;
import com.google.gson.Gson;
import fi.iki.elonen.NanoHTTPD;
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
        System.out.println("onMiningInfo (block " + newMiningInfo.getHeight() + ", gensig " + newMiningInfo.getGenerationSignature() +", diff " + newMiningInfo.getBaseTarget() + ")");
        if (miningInfo.get() == null || !Objects.equals(miningInfo.get().getGenerationSignature(), newMiningInfo.getGenerationSignature())
                || !Objects.equals(miningInfo.get().getHeight(), newMiningInfo.getHeight())) {
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
            //todo throw new SubmissionException("Reward recipient not set to pool");
        }

        BigInteger deadline = calcDeadline(submission);

        if (deadline.compareTo(BigInteger.valueOf(maxDeadline)) >= 0) {
            throw new SubmissionException("Deadline exceeds maximum allowed deadline");
        }

        submissions.remove(submission.getMiner());
        submissions.put(submission.getMiner(), submission.getNonce());

        if (bestDeadline.get() != null) {
            if (calcDeadline(bestDeadline.get()).compareTo(calcDeadline(submission)) < 0) {
                onNewBestDeadline(submission);
            }
        } else {
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

    private BigInteger calcDeadline(Submission submission) throws SubmissionException {
        MiningInfoResponse mMiningInfo = miningInfo.get();
        if (mMiningInfo == null) {
            throw new SubmissionException("Pool does not have mining info");
        }
        return calculateDeadline(submission.getMiner().getBurstID().getSignedLongId(), parseUnsignedLong(submission.getNonce()), mMiningInfo.getGenerationSignature().getBytes(), calculateScoop(mMiningInfo.getGenerationSignature().getBytes(), mMiningInfo.getHeight()), mMiningInfo.getBaseTarget(), Math.toIntExact(mMiningInfo.getHeight())); // todo height -> long
    }



    public static long parseUnsignedLong(String number) {
        if (number == null) {
            return 0;
        }
        BigInteger bigInt = new BigInteger(number.trim());
        if (bigInt.signum() < 0 || bigInt.compareTo(new BigInteger("18446744073709551616")) > -1) {
            throw new IllegalArgumentException("overflow: " + number);
        }
        return bigInt.longValue();
    }

    public int calculateScoop(byte[] genSig, long height) {
        ByteBuffer posbuf = ByteBuffer.allocate(32 + 8);
        posbuf.put(genSig);
        posbuf.putLong(height);

        Shabal256 md = new Shabal256();
        md.update(posbuf.array());
        BigInteger hashnum = new BigInteger(1, md.digest());
        return hashnum.mod(BigInteger.valueOf(MiningPlot.SCOOPS_PER_PLOT)).intValue();
    }

    public BigInteger calculateHit(long accountId, long nonce, byte[] genSig, int scoop, int blockHeight) {
        MiningPlot plot = new MiningPlot(accountId, nonce, blockHeight);
        Shabal256 md = new Shabal256();
        md.update(genSig);
        plot.hashScoop(md, scoop);
        byte[] hash = md.digest();
        return new BigInteger(1, new byte[] { hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0] });
    }

    public BigInteger calculateDeadline(long accountId, long nonce, byte[] genSig, int scoop, long baseTarget, int blockHeight) {
        BigInteger hit = calculateHit(accountId, nonce, genSig, scoop, blockHeight);
        return hit.divide(BigInteger.valueOf(baseTarget));
    }

    private void onNonceSubmitted(SubmitNonceResponse response) {
        System.out.println(new Gson().toJson(response));
    }

    private void onSubmitNonceError(Throwable t) {
        t.printStackTrace();
    }
}

class Submission {
    private final BurstAddress miner;
    private final String nonce;

    Submission(BurstAddress miner, String nonce) {
        this.miner = miner;
        this.nonce = nonce;
    }

    public BurstAddress getMiner() {
        return miner;
    }

    public String getNonce() {
        return nonce;
    }
}

class SubmissionException extends Exception {
    public SubmissionException() {
    }

    public SubmissionException(String message) {
        super(message);
    }
}

class MinerTracker { // todo
    private Set<Miner> miners;
}

class Miner {
    private final BurstAddress address;
    private final AtomicReference<BurstValue> pendingBalance;
    private final AtomicReference<Double> historicalShare;
    private final AtomicReference<String> userAgent;

    public Miner(BurstAddress address, BurstValue pendingBalance, double historicalShare, String userAgent) {
        this.address = address;
        this.pendingBalance = new AtomicReference<>(pendingBalance);
        this.historicalShare = new AtomicReference<>(historicalShare);
        this.userAgent = new AtomicReference<>(userAgent);
    }
}

class NonceSubmissionResponse {
    private final String result;
    private final String deadline;

    public NonceSubmissionResponse(String result, String deadline) {
        this.result = result;
        this.deadline = deadline;
    }
}

class Server extends NanoHTTPD {
    private final Pool pool;
    private final Gson gson = BurstKitUtils.buildGson().create();

    public Server(Pool pool) {
        super(8124);
        this.pool = pool;
        pool.resetRound();
    }

    private BurstAddress parseAddressOrNull(String address) {
        if (address == null) {
            return null;
        } else {
            return BurstAddress.fromEither(address); // todo make this return null on null input
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            StringBuilder response = new StringBuilder();
            Map<String, String> params = queryToMap(session.getQueryParameterString());
            session.parseBody(new HashMap<>());
            params.putAll(session.getParms());
            if (session.getMethod().equals(Method.POST) && session.getUri().contains("/burst") && Objects.equals(params.get("requestType"), "submitNonce")) {
                Submission submission = new Submission(parseAddressOrNull(params.get("accountId")), params.get("nonce"));
                try {
                    if (submission.getMiner() == null) {
                        throw new SubmissionException("Account ID not set");
                    }
                    if (submission.getNonce() == null) {
                        throw new SubmissionException("Nonce not set");
                    }

                    response.append(gson.toJson(new NonceSubmissionResponse("success", pool.checkNewSubmission(submission).toString())));
                } catch (SubmissionException e) {
                    response.append(gson.toJson(new NonceSubmissionResponse(e.getMessage(), null)));
                }
            } else if (session.getUri().contains("/burst") && Objects.equals(params.get("requestType"), "getMiningInfo")) {
                response.append(gson.toJson(pool.miningInfo.get()));
            }
            return NanoHTTPD.newFixedLengthResponse(response.toString());
        } catch (Throwable t) {
            t.printStackTrace();
            return NanoHTTPD.newFixedLengthResponse(t.getMessage());
        }
    }

    private static Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) return result;
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }
}
