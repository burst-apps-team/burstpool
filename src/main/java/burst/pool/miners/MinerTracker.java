package burst.pool.miners;

import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;
import burst.pool.pool.Submission;

import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MinerTracker {
    private final Map<BurstAddress, Miner> miners = new ConcurrentHashMap<>();

    public void onMinerSubmittedDeadline(long blockheight, Submission submission, BigInteger deadline) {
        Miner miner;
        if (miners.containsKey(submission.getMiner())) {
            miner = miners.get(submission.getMiner());
        } else {
            miner = new Miner(submission.getMiner(), BurstValue.fromBurst(0), 0, "");
            miners.put(submission.getMiner(), miner);
        }
    }
}
