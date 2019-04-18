package burst.pool.brs;

import burst.kit.entity.response.MiningInfoResponse;
import burst.pool.pool.Submission;
import burst.pool.pool.SubmissionException;

import java.math.BigInteger;
import java.nio.ByteBuffer;

public class Generator {

    public static int calculateScoop(byte[] genSig, long height) {
        ByteBuffer posbuf = ByteBuffer.allocate(32 + 8);
        posbuf.put(genSig);
        posbuf.putLong(height);

        Shabal256 md = new Shabal256();
        md.update(posbuf.array());
        BigInteger hashnum = new BigInteger(1, md.digest());
        return hashnum.mod(BigInteger.valueOf(MiningPlot.SCOOPS_PER_PLOT)).intValue();
    }

    public static BigInteger calculateHit(long accountId, long nonce, byte[] genSig, int scoop, int blockHeight) {
        MiningPlot plot = new MiningPlot(accountId, nonce, blockHeight);
        Shabal256 md = new Shabal256();
        md.update(genSig);
        plot.hashScoop(md, scoop);
        byte[] hash = md.digest();
        return new BigInteger(1, new byte[] { hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0] });
    }

    public static BigInteger calculateDeadline(long accountId, long nonce, byte[] genSig, int scoop, long baseTarget, int blockHeight) {
        BigInteger hit = calculateHit(accountId, nonce, genSig, scoop, blockHeight);
        return hit.divide(BigInteger.valueOf(baseTarget));
    }

    /*
    java.lang.NullPointerException: null
	at burst.pool.brs.Generator.calcDeadline(Generator.java:53) ~[burstpool.jar:?]
	at burst.pool.pool.Pool.checkNewSubmission(Pool.java:205) ~[burstpool.jar:?]
	at burst.pool.pool.Server.handleBurstApiCall(Server.java:74) ~[burstpool.jar:?]
	at burst.pool.pool.Server.serve(Server.java:50) ~[burstpool.jar:?]
	at fi.iki.elonen.NanoHTTPD$HTTPSession.execute(NanoHTTPD.java:840) ~[burstpool.jar:?]
	at fi.iki.elonen.NanoHTTPD$ClientHandler.run(NanoHTTPD.java:189) ~[burstpool.jar:?]
	at java.lang.Thread.run(Thread.java:834) ~[?:?]
     */

    public static BigInteger calcDeadline(MiningInfoResponse miningInfo, Submission submission) throws SubmissionException {
        if (miningInfo == null) {
            throw new SubmissionException("Pool does not have mining info");
        }
        return calculateDeadline(submission
                .getMiner()
                .getBurstID()
                .getSignedLongId(), parseUnsignedLong(submission
                .getNonce()), miningInfo
                .getGenerationSignature()
                .getBytes(), calculateScoop(miningInfo
                .getGenerationSignature()
                .getBytes(), miningInfo
                .getHeight()), miningInfo
                .getBaseTarget(), Math.toIntExact(miningInfo
                .getHeight())); // todo height -> long
    }

    private static long parseUnsignedLong(String number) {
        if (number == null) {
            return 0;
        }
        BigInteger bigInt = new BigInteger(number.trim());
        if (bigInt.signum() < 0 || bigInt.compareTo(new BigInteger("18446744073709551616")) > -1) {
            throw new IllegalArgumentException("overflow: " + number);
        }
        return bigInt.longValue();
    }
}
