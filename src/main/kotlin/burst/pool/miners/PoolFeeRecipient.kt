package burst.pool.miners

import burst.kit.entity.BurstAddress
import burst.kit.entity.BurstValue
import burst.pool.storage.config.PropertyService
import burst.pool.storage.config.Props
import burst.pool.storage.persistent.MinerStore.FeeRecipientStore

class PoolFeeRecipient(private val propertyService: PropertyService, private val store: FeeRecipientStore) : Payable {
    override fun increasePending(delta: BurstValue) {
        store.pendingBalance = store.pendingBalance
                .add(delta)
    }

    override fun decreasePending(delta: BurstValue) {
        store.pendingBalance = store.pendingBalance.subtract(delta)
    }

    override val minimumPayout: BurstValue
        get() = BurstValue.fromBurst(propertyService.get(Props.defaultMinimumPayout).toDouble())

    override fun takeShare(availableReward: BurstValue): BurstValue {
        return BurstValue.fromBurst(0.0)
    }

    override val pending: BurstValue
        get() = store.pendingBalance

    override val address: BurstAddress
        get() = propertyService.get(Props.feeRecipient)!!
}
