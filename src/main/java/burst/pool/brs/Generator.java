package burst.pool.brs;

import burst.kit.entity.response.MiningInfo;
import burst.pool.pool.Submission;
import burst.pool.pool.SubmissionException;

import java.math.BigInteger;
import java.nio.ByteBuffer;

public class Generator { // TODO move all of this to burstkit4j

    public static int calculateScoop(byte[] genSig, long height) {
        ByteBuffer posbuf = ByteBuffer.allocate(32 + 8);
        posbuf.put(genSig);
        posbuf.putLong(height);

        Shabal256 md = new Shabal256();
        md.update(posbuf.array());
        BigInteger hashnum = new BigInteger(1, md.digest());
        return hashnum.mod(BigInteger.valueOf(MiningPlot.SCOOPS_PER_PLOT)).intValue();
    }

    public static BigInteger calculateHit(long accountId, long nonce, byte[] genSig, int scoop) {
        MiningPlot plot = new MiningPlot(accountId, nonce);
        Shabal256 md = new Shabal256();
        md.update(genSig);
        plot.hashScoop(md, scoop);
        byte[] hash = md.digest();
        return new BigInteger(1, new byte[] { hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0] });
    }

    public static BigInteger calculateDeadline(long accountId, long nonce, byte[] genSig, int scoop, long baseTarget) {
        BigInteger hit = calculateHit(accountId, nonce, genSig, scoop);
        return hit.divide(BigInteger.valueOf(baseTarget));
    }

    public static BigInteger calcDeadline(MiningInfo miningInfo, Submission submission) throws SubmissionException {
        if (miningInfo == null) {
            throw new SubmissionException("Pool does not have mining info");
        }
        return calculateDeadline(submission.getMiner().getBurstID().getSignedLongId(), submission.getNonce().longValue(), miningInfo.getGenerationSignature(), calculateScoop(miningInfo.getGenerationSignature(), miningInfo.getHeight()), miningInfo.getBaseTarget());
    }
}
