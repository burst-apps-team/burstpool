package burst.pool.payout

import burst.kit.crypto.BurstCrypto
import burst.kit.entity.BurstAddress
import burst.kit.entity.BurstID
import burst.kit.entity.BurstValue
import burst.kit.entity.response.TransactionBroadcast
import burst.kit.service.BurstNodeService
import burst.pool.entity.Payout
import burst.pool.miners.MinerTracker
import burst.pool.miners.Payable
import burst.pool.storage.config.PropertyService
import burst.pool.storage.config.Props
import burst.pool.storage.persistent.StorageService
import io.reactivex.disposables.CompositeDisposable
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class BurstPayoutService(private val nodeService: BurstNodeService, propertyService: PropertyService, private val minerTracker: MinerTracker) : AbstractPayoutService(propertyService), AutoCloseable {
    private val burstCrypto = BurstCrypto.getInstance()
    private val compositeDisposable = CompositeDisposable()
    override fun payOut(storageService: StorageService, payableMiners: Array<Payable>) {
        val publicKey = burstCrypto.getPublicKey(propertyService.get(Props.passphrase))
        val transactionFee = BurstValue.fromBurst(propertyService.get(Props.transactionFee).toDouble())
        val transactionFeePaidPerMiner = transactionFee.divide(payableMiners.size.toLong())
        logger.info("TFPM is {}", transactionFeePaidPerMiner.toPlanck())
        val payees: MutableMap<Payable, BurstValue> = HashMap() // Does not have subtracted transaction fee
        val recipients: MutableMap<BurstAddress?, BurstValue> = HashMap()
        val logMessage = StringBuilder("Paying out to miners")
        val transactionAttachment = ByteBuffer.allocate(8 * 2 * payableMiners.size)
        for (payable in payableMiners) {
            val pending = payable.pending
            payees[payable] = pending
            val actualPayout = pending.subtract(transactionFeePaidPerMiner)
            recipients[payable.address] = actualPayout
            transactionAttachment.putLong(payable.address.burstID.signedLongId)
            transactionAttachment.putLong(actualPayout.toPlanck().toLong())
            logMessage.append(", ").append(payable.address.fullAddress).append("(").append(actualPayout.toPlanck()).append("/").append(pending.toPlanck()).append(")")
        }
        logger.info("{}", logMessage)
        val transactionId = AtomicReference<BurstID>()
        compositeDisposable.add(nodeService.generateMultiOutTransaction(publicKey, transactionFee, 1440, recipients)
                .retry(propertyService.get(Props.payoutRetryCount).toLong())
                .map { response -> burstCrypto.signTransaction(propertyService.get(Props.passphrase), response) }
                .map { signedBytes ->
                    // TODO somehow integrate this into burstkit4j
                    val unsigned = ByteArray(signedBytes.size)
                    val signature = ByteArray(64)
                    System.arraycopy(signedBytes, 0, unsigned, 0, signedBytes.size)
                    System.arraycopy(signedBytes, 96, signature, 0, 64)
                    for (i in 96 until 96 + 64) {
                        unsigned[i] = 0
                    }
                    val sha256 = burstCrypto.sha256
                    val signatureHash = sha256.digest(signature)
                    sha256.update(unsigned)
                    val fullHash = sha256.digest(signatureHash)
                    transactionId.set(burstCrypto.hashToId(fullHash))
                    signedBytes
                }
                .flatMap { signedBytes ->
                    nodeService.broadcastTransaction(signedBytes)
                            .retry(propertyService.get(Props.payoutRetryCount).toLong())
                }
                .subscribe({ onPaidOut(storageService, transactionId.get(), payees, publicKey, transactionFee, 1440, transactionAttachment.array()) }) { throwable -> onPayoutError(throwable) })
    }

    private fun onPaidOut(storageService: StorageService, transactionID: BurstID, paidMiners: Map<Payable, BurstValue>, senderPublicKey: ByteArray, fee: BurstValue, deadline: Int, transactionAttachment: ByteArray) {
        minerTracker.waitUntilNotProcessingBlock()
        for ((key, value) in paidMiners) {
            key.decreasePending(value)
        }
        storageService.addPayout(Payout(transactionID, senderPublicKey, fee, deadline, transactionAttachment))
        logger.info("Paid out, transaction id {}", transactionID)
        payoutSemaphore.release()
    }

    private fun onPayoutError(throwable: Throwable) {
        logger.error("Error occurred whilst paying out", throwable)
        payoutSemaphore.release()
    }

    override fun close() {
        compositeDisposable.dispose()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BurstPayoutService::class.java)
    }
}
