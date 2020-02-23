package burst.pool.miners

import burst.kit.entity.BurstAddress
import burst.kit.entity.BurstValue

interface Payable {
    fun increasePending(delta: BurstValue)
    fun decreasePending(delta: BurstValue)
    val minimumPayout: BurstValue
    fun takeShare(availableReward: BurstValue): BurstValue
    val pending: BurstValue
    val address: BurstAddress
}
