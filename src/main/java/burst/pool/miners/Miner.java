package burst.pool.miners;

import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class Miner implements IMiner {
    private static final int nAvg = 10; // todo config for these
    private static final int nMin = 0;
    private static final int tMin = 20;

    private final MinerMaths minerMaths;
    private final BurstAddress address;
    private final AtomicReference<BurstValue> pendingBalance;
    private final AtomicReference<Double> estimatedCapacity;
    private final AtomicReference<Double> share;

    private final Map<Long, Deadline> deadlines = new ConcurrentHashMap<>();
    private final AtomicReference<Double> hitSum = new AtomicReference<>(0d);

    public Miner(MinerMaths minerMaths, BurstAddress address, BurstValue pendingBalance, double estimatedCapacity, double share) {
        this.minerMaths = minerMaths;
        this.address = address;
        this.pendingBalance = new AtomicReference<>(pendingBalance);
        this.estimatedCapacity = new AtomicReference<>(estimatedCapacity);
        this.share = new AtomicReference<>(share);
    }

    @Override
    public void recalculateCapacity(long currentBlockHeight) {
        // Prune older deadlines
        deadlines.entrySet().removeIf(deadline -> isOldDeadline(deadline.getValue(), currentBlockHeight));
        // Calculate estimated capacity
        estimatedCapacity.set(minerMaths.estimatedEffectivePlotSize(deadlines.size(), hitSum.get()));
    }

    @Override
    public void recalculateShare(double poolCapacity) {
        if (poolCapacity == 0d) {
            share.set(0d);
        }
        share.set(estimatedCapacity.get() / poolCapacity);
        if (Double.isNaN(share.get())) {
            share.set(0d);
        }
    }

    @Override
    public void increasePending(BurstValue availableReward) {
        pendingBalance.updateAndGet(pending -> new BurstValue(pending.add(availableReward.multiply(BigDecimal.valueOf(share.get())))));
    }

    @Override
    public void zeroPending() {
        pendingBalance.set(BurstValue.fromBurst(0));
    }

    private boolean isOldDeadline(Deadline deadline, long blockHeight) {
        if (blockHeight - deadline.getHeight() >= nAvg) {
            adjustHitSum(-deadline.calculateHit());
            return true;
        } else {
            return false;
        }
    }

    private void adjustHitSum(double adjustBy) {
        hitSum.updateAndGet(value -> value + adjustBy);
    }

    @Override
    public void processNewDeadline(Deadline deadline) {
        // Check if deadline is for an older block
        for (Map.Entry<Long, Deadline> entry : deadlines.entrySet()) {
            if (entry.getKey() > deadline.getHeight()) return;
        }

        if (deadlines.containsKey(deadline.getHeight())) {
            Deadline previousDeadline = deadlines.get(deadline.getHeight());
            if (deadline.getDeadline().compareTo(previousDeadline.getDeadline()) >= 0) {
                Deadline oldDeadline = deadlines.replace(deadline.getHeight(), deadline);
                if (oldDeadline != null) {
                    adjustHitSum(-oldDeadline.calculateHit());
                }
            } else {
                return;
            }
        } else {
            deadlines.put(deadline.getHeight(), deadline);
        }
        adjustHitSum(deadline.calculateHit());
    }

    @Override
    public double getCapacity() {
        return estimatedCapacity.get();
    }

    @Override
    public BurstValue getPending() {
        return pendingBalance.get();
    }

    @Override
    public BurstAddress getAddress() {
        return address;
    }

    @Override
    public double getShare() {
        return share.get();
    }

    @Override
    public int getNConf() {
        return deadlines.size();
    }

    @Override
    public String toString() {
        return "Miner{" +
                "address=" + address +
                ", pendingBalance=" + pendingBalance +
                ", estimatedCapacity=" + estimatedCapacity +
                ", share=" + share +
                ", deadlines=" + deadlines.size() +
                ", hitSum=" + hitSum +
                '}';
    }
}
