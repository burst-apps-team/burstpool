package burst.pool.payout

import burst.pool.miners.Payable
import burst.pool.storage.config.PropertyService
import burst.pool.storage.config.Props
import burst.pool.storage.persistent.StorageService
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Semaphore

abstract class AbstractPayoutService protected constructor(protected val propertyService: PropertyService) : PayoutService {
    protected val payoutSemaphore = Semaphore(1)
    override fun payoutIfNeeded(storageService: StorageService) {
        logger.info("Attempting payout...")
        if (payoutSemaphore.availablePermits() == 0) {
            logger.info("Cannot payout - payout is already in progress.")
            return
        }
        try {
            payoutSemaphore.acquire()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        val payableMinersSet: MutableSet<Payable> = HashSet()
        for (miner in storageService.miners) {
            if (miner.minimumPayout!!.compareTo(miner.pending) <= 0) {
                payableMinersSet.add(miner)
            }
        }
        val poolFeeRecipient = storageService.poolFeeRecipient
        if (poolFeeRecipient.minimumPayout!!.compareTo(poolFeeRecipient.pending) <= 0) {
            payableMinersSet.add(poolFeeRecipient)
        }
        if (payableMinersSet.size < 2 || payableMinersSet.size < propertyService.get(Props.minPayoutsPerTransaction) && payableMinersSet.size < storageService.minerCount) {
            payoutSemaphore.release()
            logger.info("Cannot payout. There are {} payable miners, required {}, miner count {}", payableMinersSet.size, propertyService.get(Props.minPayoutsPerTransaction), storageService.minerCount)
            return
        }
        val payableMiners = if (payableMinersSet.size <= 64) payableMinersSet.toTypedArray() else Arrays.copyOfRange(payableMinersSet.toTypedArray(), 0, 64)
        payOut(storageService, payableMiners)
    }

    protected abstract fun payOut(storageService: StorageService, payableMiners: Array<Payable>)

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractPayoutService::class.java)
    }
}
