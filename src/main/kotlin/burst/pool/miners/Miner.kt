package burst.pool.miners

import burst.kit.entity.BurstAddress
import burst.kit.entity.BurstValue
import burst.pool.storage.config.PropertyService
import burst.pool.storage.config.Props
import burst.pool.storage.persistent.MinerStore
import java.math.BigInteger
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

class Miner(private val minerMaths: MinerMaths, private val propertyService: PropertyService, override val address: BurstAddress, private val store: MinerStore) : Payable {
    fun recalculateCapacity(currentBlockHeight: Long, fastBlocks: List<Long>) { // Prune older deadlines
        store.deadlines.forEach { deadline ->
            if (currentBlockHeight - deadline.height >= propertyService.get(Props.nAvg)) {
                store.removeDeadline(deadline.height)
            }
        }
        // Calculate hitSum
        val hitSum = AtomicReference(BigInteger.ZERO)
        val deadlineCount = AtomicInteger(store.deadlineCount)
        val deadlines = store.deadlines.toMutableList()
        val outliers = calculateOutliers(deadlines)
        deadlines.forEach { deadline ->
            if (fastBlocks.contains(deadline.height) || outliers.contains(deadline.height)) {
                deadlineCount.getAndDecrement()
            } else {
                hitSum.set(hitSum.get().add(deadline.calculateHit()))
            }
        }
        // Calculate estimated capacity
        try {
            store.estimatedCapacity = minerMaths.estimatedEffectivePlotSize(deadlines.size, deadlineCount.get(), hitSum.get())
        } catch (ignored: ArithmeticException) {
        }
    }

    private fun calculateOutliers(input: MutableList<Deadline>): List<Long> {
        if (input.size < 2) return ArrayList()
        input.sortBy { it.calculateHit() }
        val output: MutableList<Long> = ArrayList()
        val data1: List<Deadline>
        val data2: List<Deadline>
        if (input.size % 2 == 0) {
            data1 = input.subList(0, input.size / 2)
            data2 = input.subList(input.size / 2, input.size)
        } else {
            data1 = input.subList(0, input.size / 2)
            data2 = input.subList(input.size / 2 + 1, input.size)
        }
        val q1 = getMedian(data1).toDouble()
        val q3 = getMedian(data2).toDouble()
        val iqr = q3 - q1
        val upperFence = q3 + 100 * iqr
        for (deadline in input) {
            if (deadline.deadline.toLong() > upperFence) output.add(deadline.height)
        }
        return output
    }

    fun recalculateShare(poolCapacity: Double) {
        if (poolCapacity == 0.0 || poolCapacity.isNaN()) {
            store.share = 0.0
            return
        }
        var newShare = store.estimatedCapacity / poolCapacity
        if (newShare.isNaN()) newShare = 0.0
        store.share = newShare
    }

    override fun increasePending(delta: BurstValue) {
        store.pendingBalance = store.pendingBalance.add(delta)
    }

    override fun decreasePending(delta: BurstValue) {
        store.pendingBalance = store.pendingBalance.subtract(delta)
    }

    override var minimumPayout: BurstValue
        get() = store.minimumPayout
        set(minimumPayout) {
            store.minimumPayout = minimumPayout
        }

    override fun takeShare(availableReward: BurstValue): BurstValue {
        val share = availableReward.multiply(store.share)
        increasePending(share)
        return share
    }

    fun processNewDeadline(deadline: Deadline) { // Check if deadline is for an older block
        val deadlines = store.deadlines
        var previousDeadlineExists = false
        for (existingDeadline in deadlines) {
            if (existingDeadline.height > deadline.height) return
            if (existingDeadline.height == deadline.height) previousDeadlineExists = true
        }
        if (previousDeadlineExists) {
            val previousDeadline = store.getDeadline(deadline.height)
            if (previousDeadline == null || deadline.deadline.compareTo(previousDeadline.deadline) < 0) { // If new deadline is better
                store.setOrUpdateDeadline(deadline.height, deadline)
            }
        } else {
            store.setOrUpdateDeadline(deadline.height, deadline)
        }
    }

    val capacity: Double
        get() = store.estimatedCapacity

    override val pending: BurstValue
        get() = store.pendingBalance

    val share: Double
        get() = store.share

    val nConf: Int
        get() = store.deadlineCount

    var name: String?
        get() = store.name
        set(name) {
            store.name = name
        }

    var userAgent: String?
        get() = store.userAgent
        set(userAgent) {
            store.userAgent = userAgent
        }

    fun getBestDeadline(height: Long): BigInteger? {
        val deadline = store.getDeadline(height)
        return deadline?.deadline
    }

    companion object {
        private fun getMedian(data: List<Deadline>): Long { // TODO use calculateHit if it is worth the performance hit
            return if (data.size % 2 == 0) (data[data.size / 2].deadline.toLong() + data[data.size / 2 - 1].deadline.toLong()) / 2 else data[data.size / 2].deadline.toLong()
        }
    }
}
