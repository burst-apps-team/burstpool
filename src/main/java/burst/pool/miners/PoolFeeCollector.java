package burst.pool.miners;

import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;

import java.util.concurrent.atomic.AtomicReference;

public class PoolFeeCollector implements IMiner {
    private final BurstAddress address;
    private final AtomicReference<BurstValue> pending;

    public PoolFeeCollector(BurstAddress address, BurstValue pending) {
        this.address = address;
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
        return address;
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
