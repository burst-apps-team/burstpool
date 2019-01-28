package burst.pool.miners;

import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;
import burst.kit.service.BurstNodeService;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class MinerTracker {
    private final Map<BurstAddress, Miner> miners = new ConcurrentHashMap<>();

    private double percentageNeededToPayout;
    private BurstValue minimumPayout;
    private byte[] poolPublicKey;
    private BurstValue transactionFee;

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

        // Payout if needed
        payoutIfNeeded();
        System.out.println(miners);
    }

    public void payoutIfNeeded() {
        BurstNodeService nodeService = BurstNodeService.getInstance("");

        Set<Miner> payableMinersSet = new HashSet<>();
        for (Miner miner : miners.values()) {
            if (minimumPayout.compareTo(miner.getPendingBalance()) <= 0) {
                payableMinersSet.add(miner);
            }
        }

        Miner[] payableMiners = payableMinersSet.toArray(new Miner[0]);

        if (((double) payableMiners.length) / ((double) miners.size()) < percentageNeededToPayout) {
            // Not enough miners have reached payout point yet.
            return;
        }

        int numberOfTransactionsNeeded = ((payableMiners.length - 1) / 64) + 1;
        Miner[][] splitMiners = new Miner[numberOfTransactionsNeeded][];
        int chunk = 64; // chunk size to divide
        for(int i=0;i<payableMiners.length;i+=chunk){
            splitMiners[i/chunk] = Arrays.copyOfRange(payableMiners, i, Math.min(payableMiners.length,i+chunk));
        }

        List<Map<BurstAddress, BurstValue>> transactions = new ArrayList<>();


        for (Miner[] miners : splitMiners) {
            Map<BurstAddress, BurstValue> recipients = new HashMap<>();
            for (Miner miner: miners) {
                recipients.put(miner.getAddress(), miner.getPendingBalance());
            }
            transactions.add(recipients);
        }


    }
}
