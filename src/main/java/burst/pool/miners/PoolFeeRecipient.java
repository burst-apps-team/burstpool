package burst.pool.miners;

import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.Props;

import java.util.concurrent.atomic.AtomicReference;

public class PoolFeeRecipient implements IMiner {
    private final PropertyService propertyService;
    private final AtomicReference<BurstValue> pending;

    public PoolFeeRecipient(PropertyService propertyService, BurstValue pending) {
        this.propertyService = propertyService;
        this.pending = new AtomicReference<>(pending);
    }

    @Override
    public void processNewDeadline(Deadline deadline) {
    }

    @Override
    public void recalculateCapacity(long currentBlockHeight) {
    }

    @Override
    public void recalculateShare(double poolCapacity) {
    }

    @Override
    public void increasePending(BurstValue delta) {
        pending.updateAndGet(pending -> new BurstValue(pending.add(delta)));
    }

    @Override
    public BurstValue takeShare(BurstValue availableReward) {
        return BurstValue.fromBurst(0);
    }

    @Override
    public void zeroPending() {
        pending.set(BurstValue.fromBurst(0));
    }

    @Override
    public double getCapacity() {
        return 0;
    }

    @Override
    public BurstValue getPending() {
        return pending.get();
    }

    @Override
    public BurstAddress getAddress() {
        return propertyService.getBurstAddress(Props.feeRecipient);
    }

    @Override
    public double getShare() {
        return 0;
    }

    @Override
    public int getNConf() {
        return 0;
    }
}
