package burst.pool.miners;

import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.Props;
import burst.pool.storage.persistent.MinerStore;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class Miner implements Payable {
    private final MinerMaths minerMaths;
    private final PropertyService propertyService;

    private final BurstAddress address;
    private final MinerStore store;

    public Miner(MinerMaths minerMaths, PropertyService propertyService, BurstAddress address, MinerStore store) {
        this.minerMaths = minerMaths;
        this.propertyService = propertyService;
        this.address = address;
        this.store = store;
    }

    public void recalculateCapacity(long currentBlockHeight, List<Long> fastBlocks) {
        // Prune older deadlines
        store.getDeadlines().forEach(deadline -> {
            if (currentBlockHeight - deadline.getHeight() >= propertyService.getInt(Props.nAvg)) {
                store.removeDeadline(deadline.getHeight());
            }
        });
        // Calculate hitSum
        AtomicReference<BigInteger> hitSum = new AtomicReference<>(BigInteger.ZERO);
        AtomicInteger deadlineCount = new AtomicInteger(store.getDeadlineCount());
        List<Deadline> deadlines = store.getDeadlines();
        List<Long> outliers = calculateOutliers(deadlines);
        deadlines.forEach(deadline -> {
            if (fastBlocks.contains(deadline.getHeight()) || outliers.contains(deadline.getHeight())) {
                deadlineCount.getAndDecrement();
            } else {
                hitSum.set(hitSum.get().add(deadline.calculateHit()));
            }
        });
        // Calculate estimated capacity
        try {
            store.setEstimatedCapacity(minerMaths.estimatedEffectivePlotSize(deadlines.size(), deadlineCount.get(), hitSum.get()));
        } catch (ArithmeticException ignored) {
        }
    }

    public List<Long> calculateOutliers(List<Deadline> input) {
        if (input.size() < 2) return new ArrayList<>();
        input.sort(Comparator.comparing(Deadline::calculateHit));
        List<Long> output = new ArrayList<>();
        List<Deadline> data1;
        List<Deadline> data2;
        if (input.size() % 2 == 0) {
            data1 = input.subList(0, input.size() / 2);
            data2 = input.subList(input.size() / 2, input.size());
        } else {
            data1 = input.subList(0, input.size() / 2);
            data2 = input.subList(input.size() / 2 + 1, input.size());
        }
        double q1 = getMedian(data1);
        double q3 = getMedian(data2);
        double iqr = q3 - q1;
        double upperFence = q3 + 100 * iqr;
        for (Deadline deadline : input) {
            if (deadline.getDeadline().longValue() > upperFence)
                output.add(deadline.getHeight());
        }
        return output;
    }

    private static long getMedian(List<Deadline> data) { // TODO use calculateHit if it is worth the performance hit
        if (data.size() % 2 == 0)
            return (data.get(data.size() / 2).getDeadline().longValue() + data.get(data.size() / 2 - 1).getDeadline().longValue()) / 2;
        else
            return data.get(data.size() / 2).getDeadline().longValue();
    }

    public void recalculateShare(double poolCapacity) {
        if (poolCapacity == 0d) {
            store.setShare(0d);
            return;
        }
        double newShare = store.getEstimatedCapacity() / poolCapacity;
        if (Double.isNaN(newShare)) newShare = 0d;
        store.setShare(newShare);
    }

    @Override
    public void increasePending(BurstValue delta) {
        store.setPendingBalance(store.getPendingBalance().add(delta));
    }

    @Override
    public void decreasePending(BurstValue delta) {
        store.setPendingBalance(store.getPendingBalance().subtract(delta));
    }

    @Override
    public BurstValue getMinimumPayout() {
        return store.getMinimumPayout();
    }

    @Override
    public BurstValue takeShare(BurstValue availableReward) {
        BurstValue share = availableReward.multiply(store.getShare());
        increasePending(share);
        return share;
    }

    public void processNewDeadline(Deadline deadline) {
        // Check if deadline is for an older block
        List<Deadline> deadlines = store.getDeadlines();
        boolean previousDeadlineExists = false;
        for (Deadline existingDeadline : deadlines) {
            if (existingDeadline.getHeight() > deadline.getHeight()) return;
            if (existingDeadline.getHeight() == deadline.getHeight()) previousDeadlineExists = true;
        }

        if (previousDeadlineExists) {
            Deadline previousDeadline = store.getDeadline(deadline.getHeight());
            if (previousDeadline == null || deadline.getDeadline().compareTo(previousDeadline.getDeadline()) < 0) { // If new deadline is better
                store.setOrUpdateDeadline(deadline.getHeight(), deadline);
            }
        } else {
            store.setOrUpdateDeadline(deadline.getHeight(), deadline);
        }
    }

    public double getCapacity() {
        return store.getEstimatedCapacity();
    }

    @Override
    public BurstValue getPending() {
        return store.getPendingBalance();
    }

    @Override
    public BurstAddress getAddress() {
        return address;
    }

    public double getShare() {
        return store.getShare();
    }

    public int getNConf() {
        return store.getDeadlineCount();
    }

    public String getName() {
        return store.getName();
    }

    public void setName(String name) {
        store.setName(name);
    }

    public String getUserAgent() {
        return store.getUserAgent();
    }

    public void setUserAgent(String userAgent) {
        store.setUserAgent(userAgent);
    }

    public void setMinimumPayout(BurstValue minimumPayout) {
        store.setMinimumPayout(minimumPayout);
    }

    public BigInteger getBestDeadline(long height) {
        Deadline deadline = store.getDeadline(height);
        return deadline == null ? null : deadline.getDeadline();
    }
}
