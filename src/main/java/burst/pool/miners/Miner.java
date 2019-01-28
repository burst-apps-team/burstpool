package burst.pool.miners;

import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class Miner implements IMiner {
    private static final double GenesisBaseTarget = 18325193796d;
    private static final double[] alphas;
    private static final int nAvg = 10; // todo config for these
    private static final int nMin = 0;
    private static final int tMin = 20;

    private final BurstAddress address;
    private final AtomicReference<BurstValue> pendingBalance;
    private final AtomicReference<Double> estimatedCapacity;
    private final AtomicReference<Double> share;

    private final Map<Long, Deadline> deadlines = new ConcurrentHashMap<>();
    private final AtomicReference<Double> hitSum = new AtomicReference<>(0d);

    public Miner(BurstAddress address, BurstValue pendingBalance, double estimatedCapacity, double share) {
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
        estimatedCapacity.set(estimatedEffectivePlotSize(deadlines.size(), hitSum.get()));
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
        pendingBalance.updateAndGet(pending -> bdtobv(pending.add(availableReward.multiply(BigDecimal.valueOf(share.get())))));
    }

    private static BurstValue bdtobv(BigDecimal bd) {
        return BurstValue.fromPlanck(bd.multiply(BigDecimal.TEN.pow(8)).toPlainString());
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

    private static double estimatedEffectivePlotSize(int nConf, double hitSum) {
        if (hitSum == 0) {
            return 0;
        }
        return alpha(nConf) * 240d * (((double)nConf)-1d) / (hitSum / GenesisBaseTarget);
    }

    static {
        alphas = new double[nAvg];
        for (int i = 0; i < nAvg; i++) {
            if (i < nMin-1) {
                alphas[i] = 0d;
            } else {
                double nConf = i + 1;
                alphas[i] = 1d - ((double)nAvg-nConf)/ nConf *Math.log(nAvg/((double)nAvg-nConf));
            }
        }
        alphas[nAvg-1] = 1d;
    }

    private static double alpha(int nConf) {
        if (nConf == 0) {
            return 0d;
        }
        if (alphas.length < nConf) {
            return 1d;
        }
        return alphas[nConf-1];
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
    public double getShare() {
        return share.get();
    }

    public BurstValue getPendingBalance() {
        return pendingBalance.get();
    }

    public BurstAddress getAddress() {
        return address;
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
