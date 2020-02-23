package burst.pool.miners

import burst.kit.entity.BurstAddress
import burst.kit.entity.BurstID
import burst.kit.entity.BurstValue
import burst.kit.entity.response.Account
import burst.kit.service.BurstNodeService
import burst.pool.entity.WonBlock
import burst.pool.storage.config.PropertyService
import burst.pool.storage.config.Props
import burst.pool.storage.persistent.StorageService
import io.reactivex.disposables.CompositeDisposable
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

class MinerTracker(private val nodeService: BurstNodeService, private val propertyService: PropertyService) : AutoCloseable {
    private val compositeDisposable = CompositeDisposable()
    private val currentlyProcessingBlock = AtomicBoolean(false)
    fun onMinerSubmittedDeadline(storageService: StorageService, minerAddress: BurstAddress, deadline: BigInteger, baseTarget: BigInteger, blockHeight: Long, userAgent: String?) {
        waitUntilNotProcessingBlock()
        val miner = getOrCreate(storageService, minerAddress)
        miner!!.processNewDeadline(Deadline(deadline, baseTarget, blockHeight))
        miner.userAgent = userAgent
        compositeDisposable.add(nodeService.getAccount(minerAddress).subscribe({ accountResponse -> onMinerAccount(storageService, accountResponse) }) { throwable -> onMinerAccountError(throwable) })
    }

    private fun getOrCreate(storageService: StorageService, minerAddress: BurstAddress): Miner? {
        var miner = storageService.getMiner(minerAddress)
        if (miner == null) {
            miner = storageService.getOrNewMiner(minerAddress)
        }
        return miner
    }

    fun onBlockWon(transactionalStorageService: StorageService, blockHeight: Long, blockId: BurstID?, nonce: BigInteger?, winner: BurstAddress, blockReward: BurstValue, fastBlocks: List<Long>) {
        logger.info("Block won! Block height: " + blockHeight + ", forger: " + winner.fullAddress)
        transactionalStorageService.addWonBlock(WonBlock(blockHeight.toInt(), blockId!!, winner, nonce!!, blockReward))
        var reward = blockReward
        // Take pool fee
        val poolTake = reward.multiply(propertyService.get(Props.poolFeePercentage).toDouble())
        reward = reward.subtract(poolTake)
        val poolFeeRecipient = transactionalStorageService.poolFeeRecipient
        poolFeeRecipient.increasePending(poolTake)
        // Take winner fee
        val winnerTake = reward.multiply(propertyService.get(Props.winnerRewardPercentage).toDouble())
        reward = reward.subtract(winnerTake)
        val winningMiner = getOrCreate(transactionalStorageService, winner)
        winningMiner!!.increasePending(winnerTake)
        val miners = transactionalStorageService.miners
        updateMiners(miners, blockHeight, fastBlocks)
        // Update each miner's pending
        val amountTaken = AtomicReference(BurstValue.fromBurst(0.0))
        val poolReward = reward
        miners.forEach(Consumer { miner -> amountTaken.updateAndGet { a -> a.add(miner.takeShare(poolReward)) } })
        // Evenly share result. This makes sure that poolReward is taken, even if the amountTaken was greater than poolReward
// Essentially prevents the pool from overpaying or underpaying. Even if it gave out too much to the fee recipient and reward recipient, it will now take the extra from the pending of miners.
        if (!miners.isEmpty()) {
            val amountRemainingEach = poolReward.subtract(amountTaken.get()).divide(miners.size.toLong())
            if (logger.isInfoEnabled) {
                logger.info("Amount remaining each is {}", amountRemainingEach.toPlanck())
            }
            miners.forEach(Consumer { miner -> miner.increasePending(amountRemainingEach) })
        }
        logger.info("Finished processing winnings for block " + blockHeight + ". Reward ( + fees) is " + blockReward + ", pool fee is " + poolTake + ", forger take is " + winnerTake + ", miners took " + amountTaken.get())
    }

    fun onBlockNotWon(transactionalStorageService: StorageService, blockHeight: Long, fastBlocks: List<Long>) {
        updateMiners(transactionalStorageService.miners, blockHeight, fastBlocks)
    }

    private fun updateMiners(miners: List<Miner>, blockHeight: Long, fastBlocks: List<Long>) { // Update each miner's effective capacity
        miners.forEach(Consumer { miner -> miner.recalculateCapacity(blockHeight, fastBlocks) })
        // Calculate pool capacity
        val poolCapacity = AtomicReference(0.0)
        miners.forEach(Consumer { miner -> poolCapacity.updateAndGet { v -> (v + miner.capacity) } })
        // Update each miner's share
        miners.forEach(Consumer { miner -> miner.recalculateShare(poolCapacity.get()) })
    }

    fun setMinerMinimumPayout(storageService: StorageService, minerAddress: BurstAddress, amount: BurstValue) {
        waitUntilNotProcessingBlock()
        val miner = storageService.getMiner(minerAddress) ?: return
        miner.minimumPayout = amount
    }

    private fun onMinerAccount(storageService: StorageService, accountResponse: Account) {
        waitUntilNotProcessingBlock()
        val miner = storageService.getMiner(accountResponse.id) ?: return
        if (accountResponse.name == null) return
        miner.name = accountResponse.name
    }

    private fun onMinerAccountError(throwable: Throwable) {
        logger.warn("Error obtaining miner account info", throwable)
    }

    fun waitUntilNotProcessingBlock() {
        while (currentlyProcessingBlock.get()) {
            try {
                Thread.sleep(1)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    fun setCurrentlyProcessingBlock(currentlyProcessingBlock: Boolean) {
        this.currentlyProcessingBlock.set(currentlyProcessingBlock)
    }

    override fun close() {
        compositeDisposable.dispose()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MinerTracker::class.java)
    }
}
