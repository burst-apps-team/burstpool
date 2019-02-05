package burst.pool.miners;

import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.Props;
import burst.pool.storage.persistent.MinerStore;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class Miner implements Payable {
    private final MinerMaths minerMaths;
    private final PropertyService propertyService;

    private final Object processDeadlineLock = new Object();

    private final BurstAddress address;
    private final MinerStore store;

    public Miner(MinerMaths minerMaths, PropertyService propertyService, BurstAddress address, MinerStore store) {
        this.minerMaths = minerMaths;
        this.propertyService = propertyService;
        this.address = address;
        this.store = store;
    }

    public void recalculateCapacity(long currentBlockHeight) {
        // Prune older deadlines
        store.getDeadlines().forEach(deadline -> {
            if (currentBlockHeight - deadline.getHeight() >= propertyService.getInt(Props.nAvg)) {
                store.removeDeadline(deadline.getHeight());
            }
        });
        // Calculate hitSum
        AtomicReference<BigInteger> hitSum = new AtomicReference<>(BigInteger.ZERO);
        store.getDeadlines().forEach(deadline -> hitSum.set(hitSum.get().add(deadline.calculateHit())));
        // Calculate estimated capacity
        store.setEstimatedCapacity(minerMaths.estimatedEffectivePlotSize(store.getDeadlineCount(), hitSum.get()));
    }

    public void recalculateShare(double poolCapacity) {
        if (poolCapacity == 0d) {
            store.setShare(0d);
        }
        double newShare = store.getEstimatedCapacity() / poolCapacity;
        if (Double.isNaN(newShare)) newShare = 0d;
        store.setShare(newShare);
    }

    @Override
    public void increasePending(BurstValue delta) {
        store.setPendingBalance(store.getPendingBalance() + Double.parseDouble(delta.toPlainString())); // TODO workaround for bug in burstkit4j
    }

    @Override
    public void decreasePending(BurstValue delta) {
        store.setPendingBalance(store.getPendingBalance() - Double.parseDouble(delta.toPlainString())); // TODO workaround for bug in burstkit4j
    }

    @Override
    public BurstValue getMinimumPayout() {
        return BurstValue.fromBurst(store.getMinimumPayout());
    }

    @Override
    public BurstValue takeShare(BurstValue availableReward) {
        BurstValue share = new BurstValue(availableReward.multiply(BigDecimal.valueOf(store.getShare())));
        increasePending(share);
        return share;
    }

    public void processNewDeadline(Deadline deadline) {
        synchronized (processDeadlineLock) {
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
    }

    public double getCapacity() {
        return store.getEstimatedCapacity();
    }

    @Override
    public BurstValue getPending() {
        return BurstValue.fromBurst(store.getPendingBalance());
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
}
