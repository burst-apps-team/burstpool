package burst.pool.storage.persistent

import burst.kit.entity.BurstAddress
import burst.pool.entity.Payout
import burst.pool.entity.WonBlock
import burst.pool.miners.Miner
import burst.pool.miners.PoolFeeRecipient
import burst.pool.pool.StoredSubmission

interface StorageService : AutoCloseable {
    fun beginTransaction(): StorageService?
    fun commitTransaction()
    fun rollbackTransaction()
    val minerCount: Int
    val miners: List<Miner>
    val minersFiltered: List<Miner>
    fun getMiner(address: BurstAddress): Miner?
    fun getOrNewMiner(address: BurstAddress): Miner
    val poolFeeRecipient: PoolFeeRecipient
    val lastProcessedBlock: Int
    fun incrementLastProcessedBlock()
    val bestSubmissions: Map<Long, List<StoredSubmission>>
    fun getBestSubmissionsForBlock(blockHeight: Long): List<StoredSubmission>
    fun addBestSubmissionForBlock(blockHeight: Long, submission: StoredSubmission)
    fun removeBestSubmission(blockHeight: Long) // TODO unused
    fun addWonBlock(wonBlock: WonBlock)
    fun getWonBlocks(limit: Int): List<WonBlock>
    fun addPayout(payout: Payout)
}
