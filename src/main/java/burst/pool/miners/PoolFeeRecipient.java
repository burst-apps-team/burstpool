package burst.pool.miners;

import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.Props;
import burst.pool.storage.persistent.MinerStore;

public class PoolFeeRecipient implements Payable {
    private final PropertyService propertyService;
    private final MinerStore.FeeRecipientStore store;

    public PoolFeeRecipient(PropertyService propertyService, MinerStore.FeeRecipientStore store) {
        this.propertyService = propertyService;
        this.store = store;
    }

    @Override
    public void increasePending(BurstValue delta) {
        store.setPendingBalance(store.getPendingBalance()
                .add(delta));
    }

    @Override
    public void decreasePending(BurstValue delta) {
        store.setPendingBalance(store.getPendingBalance().subtract(delta));
    }

    @Override
    public BurstValue getMinimumPayout() {
        return BurstValue.fromBurst(propertyService.getFloat(Props.defaultMinimumPayout));
    }

    @Override
    public BurstValue takeShare(BurstValue availableReward) {
        return BurstValue.fromBurst(0);
    }

    @Override
    public BurstValue getPending() {
        return store.getPendingBalance();
    }

    @Override
    public BurstAddress getAddress() {
        return propertyService.getBurstAddress(Props.feeRecipient);
    }
}
