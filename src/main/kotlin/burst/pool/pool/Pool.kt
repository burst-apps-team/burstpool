package burst.pool.pool

import burst.kit.crypto.BurstCrypto
import burst.kit.entity.BurstAddress
import burst.kit.entity.response.MiningInfo
import burst.kit.entity.response.http.MiningInfoResponse
import burst.kit.service.BurstNodeService
import burst.pool.miners.MinerTracker
import burst.pool.payout.PayoutService
import burst.pool.storage.config.PropertyService
import burst.pool.storage.config.Props
import burst.pool.storage.persistent.StorageService
import com.google.gson.Gson
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.time.Instant
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class Pool(private val nodeService: BurstNodeService, private val storageService: StorageService, private val propertyService: PropertyService, private val minerTracker: MinerTracker, private val payoutService: PayoutService) {
    private val burstCrypto = BurstCrypto.getInstance()
    private val disposables = CompositeDisposable()
    private val processBlockSemaphore = Semaphore(1)
    private val resetRoundSemaphore = Semaphore(1)
    private val processDeadlineSemaphore = Semaphore(1)
    // Variables
    private val roundStartTime = AtomicReference(Instant.now())
    private val bestSubmission = AtomicReference<Submission?>()
    private val bestDeadline = AtomicReference<BigInteger>()
    private val miningInfo = AtomicReference<MiningInfo?>()
    private val myRewardRecipients: MutableSet<BurstAddress> = HashSet()
    private fun processBlocksThread(): Disposable {
        return Observable.interval(0, 1, TimeUnit.SECONDS)
                .flatMapCompletable {
                    processNextBlock().onErrorComplete { e ->
                        onProcessBlocksError(e, false)
                        true
                    }
                }
                .retry()
                .subscribeOn(Schedulers.io())
                .subscribe({ }, { e: Throwable -> onProcessBlocksError(e, true) })
    }

    private fun onProcessBlocksError(throwable: Throwable, fatal: Boolean) {
        if (fatal) {
            logger.error("Fatal error processing blocks (Thread now shutdown)", throwable)
        } else {
            logger.warn("Non-fatal error processing blocks", throwable)
        }
    }

    private fun refreshMiningInfoThread(): Disposable {
        return nodeService.miningInfo
                .retry()
                .subscribeOn(Schedulers.io())
                .subscribe({ newMiningInfo: MiningInfo -> onMiningInfo(newMiningInfo) }) { e -> onMiningInfoError(e, true) }
    }

    private fun onMiningInfo(newMiningInfo: MiningInfo) {
        if (miningInfo.get() == null || !Arrays.equals(miningInfo.get()!!.generationSignature, newMiningInfo.generationSignature)
                || miningInfo.get()!!.height != newMiningInfo.height) {
            logger.info("NEW BLOCK (block " + newMiningInfo.height + ", gensig " + burstCrypto.toHexString(newMiningInfo.generationSignature) + ", diff " + newMiningInfo.baseTarget + ")")
            resetRound(newMiningInfo)
        }
    }

    private fun onMiningInfoError(throwable: Throwable, fatal: Boolean) {
        if (fatal) {
            logger.error("Fatal error fetching mining info (Thread now shutdown)", throwable)
        } else {
            logger.warn("Non-fatal error fetching mining info", throwable)
        }
    }

    private fun processNextBlock(): Completable {
        return Completable.fromAction {
            var transactionalStorageService: StorageService? = null
            try {
                if (miningInfo.get() == null || processBlockSemaphore.availablePermits() == 0 || miningInfo.get()!!.height - 1 <= storageService.lastProcessedBlock + propertyService.get(Props.processLag)) {
                    return@fromAction
                }
                try {
                    processBlockSemaphore.acquire()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                transactionalStorageService = try {
                    storageService.beginTransaction()
                } catch (e: Exception) {
                    logger.error("Could not open transactional storage service", e)
                    processBlockSemaphore.release()
                    return@fromAction
                }
                minerTracker.setCurrentlyProcessingBlock(true)
                val fastBlocks: MutableList<Long> = ArrayList()
                transactionalStorageService!!.bestSubmissions.forEach { (height: Long, deadline: List<StoredSubmission?>) ->
                    val lowestDeadline = deadline.stream()
                            .map(StoredSubmission::deadline)
                            .min(Long::compareTo)
                            .orElse(propertyService.get(Props.tMin).toLong())
                    if (lowestDeadline < propertyService.get(Props.tMin)) {
                        fastBlocks.add(height)
                    }
                }
                val storedSubmissions = transactionalStorageService.getBestSubmissionsForBlock(transactionalStorageService.lastProcessedBlock + 1.toLong())
                if (storedSubmissions.isEmpty()) {
                    onProcessedBlock(transactionalStorageService, false)
                    return@fromAction
                }
                val block = nodeService.getBlock(transactionalStorageService.lastProcessedBlock + 1).blockingGet()
                val submissions: List<Submission> = transactionalStorageService.getBestSubmissionsForBlock(block.height.toLong())
                var won = false
                if (submissions.isNotEmpty()) {
                    for (submission in submissions) {
                        if (block.generator == submission.miner && block.nonce == submission.nonce) {
                            won = true
                        }
                    }
                }
                if (won) {
                    minerTracker.onBlockWon(transactionalStorageService, transactionalStorageService.lastProcessedBlock + 1.toLong(), block.id, block.nonce, block.generator, block.blockReward.add(block.totalFee), fastBlocks)
                } else {
                    if (myRewardRecipients.contains(block.generator)) {
                        logger.error("Our miner forged but did not detect block won. Height " + block.height)
                    }
                    minerTracker.onBlockNotWon(transactionalStorageService, transactionalStorageService.lastProcessedBlock + 1.toLong(), fastBlocks)
                }
                onProcessedBlock(transactionalStorageService, true)
            } catch (e: Exception) {
                if (transactionalStorageService != null) {
                    logger.warn("Error processing block " + transactionalStorageService.lastProcessedBlock + 1, e)
                    try {
                        transactionalStorageService.rollbackTransaction()
                        transactionalStorageService.close()
                    } catch (e1: Exception) {
                        logger.error("Error rolling back transaction", e1)
                    }
                }
                minerTracker.setCurrentlyProcessingBlock(false)
                processBlockSemaphore.release()
            }
        }
    }

    private fun onProcessedBlock(transactionalStorageService: StorageService?, actuallyProcessed: Boolean) { // TODO this needs to be done if block is behind nAvg otherwise fast block calculation breaks
//storageService.removeBestSubmission(storageService.getgetLastProcessedBlock()() + 1);
        transactionalStorageService!!.incrementLastProcessedBlock()
        try {
            transactionalStorageService.commitTransaction()
            transactionalStorageService.close()
        } catch (e: Exception) {
            logger.error("Error committing transaction", e)
        }
        minerTracker.setCurrentlyProcessingBlock(false)
        processBlockSemaphore.release()
        if (actuallyProcessed) {
            payoutService.payoutIfNeeded(storageService)
        }
    }

    private fun resetRound(newMiningInfo: MiningInfo?) { // Traffic flow - we want to stop new requests but let old ones finish before we go ahead.
        try { // Lock the reset round semaphore to stop accepting new requests
            resetRoundSemaphore.acquire()
            // Wait for all requests to be processed
            while (processDeadlineSemaphore.queueLength > 0) {
                Thread.sleep(1)
            }
            // Lock the process block semaphore as we are going to be modifying bestSubmission
            processDeadlineSemaphore.acquire()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return
        }
        bestSubmission.set(null)
        bestDeadline.set(BigInteger.valueOf(Long.MAX_VALUE))
        disposables.add(nodeService.getAccountsWithRewardRecipient(burstCrypto.getBurstAddressFromPassphrase(propertyService.get(Props.passphrase)))
                .subscribe({ rewardRecipients: Array<BurstAddress> -> onRewardRecipients(rewardRecipients) }) { t: Throwable -> onRewardRecipientsError(t) })
        roundStartTime.set(Instant.now())
        miningInfo.set(newMiningInfo)
        // Unlock to signal we have finished modifying bestSubmission
        processDeadlineSemaphore.release()
        // Unlock to start accepting requests again
        resetRoundSemaphore.release()
    }

    @Throws(SubmissionException::class)
    fun checkNewSubmission(submission: Submission, userAgent: String?): BigInteger {
        if (miningInfo.get() == null) {
            throw SubmissionException("Pool does not have mining info")
        }
        if (!myRewardRecipients.contains(submission.miner)) {
            throw SubmissionException("Reward recipient not set to pool")
        }
        // If we are resetting the request must be for the previous round and no longer matters - reject
        if (resetRoundSemaphore.availablePermits() < 0) {
            throw SubmissionException("Cannot submit - new round starting")
        }
        val localMiningInfo = miningInfo.get()
        // TODO poc2 switch
        val deadline = burstCrypto.calculateDeadline(submission.miner, java.lang.Long.parseUnsignedLong(submission.nonce.toString()), localMiningInfo!!.generationSignature, burstCrypto.calculateScoop(localMiningInfo.generationSignature, localMiningInfo.height), localMiningInfo.baseTarget, 2)
        val maxDeadline = BigInteger.valueOf(propertyService.get(Props.maxDeadline))
        if (deadline > maxDeadline) {
            throw SubmissionException("Deadline exceeds maximum allowed deadline (Submitted $deadline, maximum is $maxDeadline)")
        }
        if (logger.isDebugEnabled) {
            logger.debug("New submission from {} of nonce {}, calculated deadline {} seconds.", submission.miner, submission.nonce, deadline.toString())
        }
        return try {
            try {
                processDeadlineSemaphore.acquire()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw SubmissionException("Server Interrupted")
            }
            if (bestSubmission.get() != null) {
                if (logger.isDebugEnabled) {
                    logger.debug("Best deadline is {}, new deadline is {}", bestDeadline.get(), deadline)
                }
                if (deadline < bestDeadline.get()) {
                    logger.debug("Newer deadline is better! Submitting...")
                    onNewBestDeadline(miningInfo.get()!!.height, submission, deadline)
                }
            } else {
                logger.debug("This is the first deadline, submitting...")
                onNewBestDeadline(miningInfo.get()!!.height, submission, deadline)
            }
            minerTracker.onMinerSubmittedDeadline(storageService, submission.miner, deadline, BigInteger.valueOf(miningInfo.get()!!.baseTarget), miningInfo.get()!!.height, userAgent)
            deadline
        } finally {
            processDeadlineSemaphore.release()
        }
    }

    private fun onNewBestDeadline(blockHeight: Long, submission: Submission, deadline: BigInteger) {
        bestSubmission.set(submission)
        bestDeadline.set(deadline)
        submitDeadline(submission)
        storageService.addBestSubmissionForBlock(blockHeight, StoredSubmission(submission.miner, submission.nonce, deadline.toLong()))
    }

    private fun submitDeadline(submission: Submission) {
        disposables.add(nodeService.submitNonce(propertyService.get(Props.passphrase), submission.nonce.toString(), submission.miner.burstID) // TODO burstkit4j accept nonce as bigint
                .retry(propertyService.get(Props.submitNonceRetryCount).toLong())
                .subscribe({ deadline: Long -> onNonceSubmitted(deadline) }, { t: Throwable -> onSubmitNonceError(t) }))
    }

    private fun onRewardRecipients(rewardRecipients: Array<BurstAddress>) {
        myRewardRecipients.clear()
        myRewardRecipients.addAll(Arrays.asList(*rewardRecipients))
    }

    private fun onRewardRecipientsError(t: Throwable) {
        logger.error("Error fetching pool's reward recipients", t)
    }

    private fun onNonceSubmitted(deadline: Long) {
        logger.debug("Submitted nonce to node. Deadline is {}", java.lang.Long.toUnsignedString(deadline))
    }

    private fun onSubmitNonceError(t: Throwable) {
        logger.error("Error submitting nonce to node", t)
    }

    fun getMiningInfo(): MiningInfo? {
        return miningInfo.get()
    }

    fun getCurrentRoundInfo(gson: Gson): JsonObject {
        val jsonObject = JsonObject()
        jsonObject.addProperty("roundStart", roundStartTime.get().epochSecond)
        if (bestSubmission.get() != null) {
            val bestDeadlineJson = JsonObject()
            bestDeadlineJson.addProperty("miner", bestSubmission.get()!!.miner.id)
            bestDeadlineJson.addProperty("minerRS", bestSubmission.get()!!.miner.fullAddress)
            bestDeadlineJson.addProperty("nonce", bestSubmission.get()!!.nonce)
            bestDeadlineJson.addProperty("deadline", bestDeadline.get())
            jsonObject.add("bestDeadline", bestDeadlineJson)
        } else {
            jsonObject.add("bestDeadline", JsonNull.INSTANCE)
        }
        val miningInfo = miningInfo.get()
        if (miningInfo != null) {
            jsonObject.add("miningInfo", gson.toJsonTree(MiningInfoResponse(burstCrypto.toHexString(miningInfo.generationSignature), miningInfo.baseTarget, miningInfo.height)))
        }
        return jsonObject
    }

    val account: BurstAddress
        get() = burstCrypto.getBurstAddressFromPassphrase(propertyService.get(Props.passphrase))

    companion object {
        private val logger = LoggerFactory.getLogger(Pool::class.java)
    }

    init {
        disposables.add(refreshMiningInfoThread())
        disposables.add(processBlocksThread())
        resetRound(null)
    }
}
