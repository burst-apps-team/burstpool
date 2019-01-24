package burst.pool.miners;

import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;

import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class MinerTracker {
    private final Map<BurstAddress, Miner> miners = new ConcurrentHashMap<>();

    public void onMinerSubmittedDeadline(BurstAddress minerAddress, BigInteger deadline, BigInteger baseTarget, long blockHeight) {
        Miner miner;
        if (miners.containsKey(minerAddress)) {
            miner = miners.get(minerAddress);
        } else {
            miner = new Miner(minerAddress, BurstValue.fromBurst(0), 0, 0);
            miners.put(minerAddress, miner);
        }

        miner.processNewDeadline(new Deadline(deadline, baseTarget, blockHeight));
    }

    public void onBlockWon(long blockHeight, BurstValue reward) { // todo give amount to winner, take fee
        // Update each miner's effective capacity
        miners.forEach((address, miner) -> miner.recalculateCapacity(blockHeight));

        // Calculate pool capacity
        AtomicReference<Double> poolCapacity = new AtomicReference<>(0d);
        miners.forEach((address, miner) -> poolCapacity.updateAndGet(v -> (double) (v + miner.getCapacity())));

        // Update each miner's share
        miners.forEach((address, miner) -> miner.recalculateShare(poolCapacity.get()));

        // Update each miner's pending
        miners.forEach((address, miner) -> miner.increasePending(reward));

        System.out.println(miners);
    }
}
