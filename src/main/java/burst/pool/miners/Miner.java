package burst.pool.miners;

import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.Props;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class Miner implements IMiner {
    private final MinerMaths minerMaths;
    private final PropertyService propertyService;

    private final BurstAddress address;
    private final AtomicReference<BurstValue> pendingBalance;
    private final AtomicReference<Double> estimatedCapacity;
    private final AtomicReference<Double> share;

    // storage
    private final Map<Long, Deadline> deadlines = new ConcurrentHashMap<>();
    private final AtomicReference<Double> hitSum = new AtomicReference<>(0d);

    public Miner(MinerMaths minerMaths, PropertyService propertyService, BurstAddress address, BurstValue pendingBalance, double estimatedCapacity, double share) {
        this.minerMaths = minerMaths;
        this.propertyService = propertyService;
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
    public void increasePending(BurstValue delta) {
        pendingBalance.updateAndGet(pending -> new BurstValue(pending.add(delta)));
    }

    @Override
    public void decreasePending(BurstValue delta) {
        pendingBalance.updateAndGet(pending -> new BurstValue(pending.subtract(delta)));
    }

    @Override
    public BurstValue takeShare(BurstValue availableReward) {
        BurstValue share = new BurstValue(availableReward.multiply(BigDecimal.valueOf(this.share.get())));
        pendingBalance.updateAndGet(pending -> new BurstValue(pending.add(share)));
        return share;
    }

    private boolean isOldDeadline(Deadline deadline, long blockHeight) {
        if (blockHeight - deadline.getHeight() >= propertyService.getInt(Props.nAvg)) {
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
