package burst.pool.storage.persistent

import burst.kit.entity.BurstValue
import burst.pool.miners.Deadline

interface MinerStore {
    var pendingBalance: BurstValue
    var estimatedCapacity: Double
    var share: Double
    var minimumPayout: BurstValue
    var name: String?
    var userAgent: String?
    val deadlines: List<Deadline>
    val deadlineCount: Int
    fun removeDeadline(height: Long)
    fun getDeadline(height: Long): Deadline?
    fun setOrUpdateDeadline(height: Long, deadline: Deadline)
    interface FeeRecipientStore {
        var pendingBalance: BurstValue
    }
}
