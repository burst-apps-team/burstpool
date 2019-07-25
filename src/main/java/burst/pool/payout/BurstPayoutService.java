package burst.pool.payout;

import burst.kit.crypto.BurstCrypto;
import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstID;
import burst.kit.entity.BurstValue;
import burst.kit.service.BurstNodeService;
import burst.pool.entity.Payout;
import burst.pool.miners.MinerTracker;
import burst.pool.miners.Payable;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.Props;
import burst.pool.storage.persistent.StorageService;
import io.reactivex.disposables.CompositeDisposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class BurstPayoutService extends AbstractPayoutService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(BurstPayoutService.class);

    private final BurstCrypto burstCrypto = BurstCrypto.getInstance();
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private final BurstNodeService nodeService;
    private final MinerTracker minerTracker;

    public BurstPayoutService(BurstNodeService nodeService, PropertyService propertyService, MinerTracker minerTracker) {
        super(propertyService);
        this.nodeService = nodeService;
        this.minerTracker = minerTracker;
    }

    protected void payOut(StorageService storageService, Payable[] payableMiners) {
        byte[] publicKey = burstCrypto.getPublicKey(propertyService.getString(Props.passphrase));

        BurstValue transactionFee = BurstValue.fromBurst(propertyService.getFloat(Props.transactionFee));
        BurstValue transactionFeePaidPerMiner = transactionFee.divide(payableMiners.length);
        logger.info("TFPM is {}", transactionFeePaidPerMiner.toPlanck());

        Map<Payable, BurstValue> payees = new HashMap<>(); // Does not have subtracted transaction fee
        Map<BurstAddress, BurstValue> recipients = new HashMap<>();
        StringBuilder logMessage = new StringBuilder("Paying out to miners");
        ByteBuffer transactionAttachment = ByteBuffer.allocate(8 * 2 * payableMiners.length);
        for (Payable payable : payableMiners) {
            BurstValue pending = payable.getPending();
            payees.put(payable, pending);
            BurstValue actualPayout = pending.subtract(transactionFeePaidPerMiner);
            recipients.put(payable.getAddress(), actualPayout);
            transactionAttachment.putLong(payable.getAddress().getBurstID().getSignedLongId());
            transactionAttachment.putLong(actualPayout.toPlanck().longValue());
            logMessage.append(", ").append(payable.getAddress().getFullAddress()).append("(").append(actualPayout.toPlanck()).append("/").append(pending.toPlanck()).append(")");
        }
        logger.info("{}", logMessage);

        AtomicReference<BurstID> transactionId = new AtomicReference<>();
        compositeDisposable.add(nodeService.generateMultiOutTransaction(publicKey, transactionFee, 1440, recipients)
                .retry(propertyService.getInt(Props.payoutRetryCount))
                .map(response -> burstCrypto.signTransaction(propertyService.getString(Props.passphrase), response))
                .map(signedBytes -> { // TODO somehow integrate this into burstkit4j
                    byte[] unsigned = new byte[signedBytes.length];
                    byte[] signature = new byte[64];
                    System.arraycopy(signedBytes, 0, unsigned, 0, signedBytes.length);
                    System.arraycopy(signedBytes, 96, signature, 0, 64);
                    for (int i = 96; i < 96 + 64; i++) {
                        unsigned[i] = 0;
                    }
                    MessageDigest sha256 = burstCrypto.getSha256();
                    byte[] signatureHash = sha256.digest(signature);
                    sha256.update(unsigned);
                    byte[] fullHash = sha256.digest(signatureHash);
                    transactionId.set(burstCrypto.hashToId(fullHash));
                    return signedBytes;
                })
                .flatMap(signedBytes -> nodeService.broadcastTransaction(signedBytes)
                        .retry(propertyService.getInt(Props.payoutRetryCount)))
                .subscribe(response -> onPaidOut(storageService, transactionId.get(), payees, publicKey, transactionFee, 1440, transactionAttachment.array()), this::onPayoutError));
    }

    private void onPaidOut(StorageService storageService, BurstID transactionID, Map<Payable, BurstValue> paidMiners, byte[] senderPublicKey, BurstValue fee, int deadline, byte[] transactionAttachment) {
        minerTracker.waitUntilNotProcessingBlock();
        for (Map.Entry<Payable, BurstValue> payment : paidMiners.entrySet()) {
            payment.getKey().decreasePending(payment.getValue());
        }
        storageService.addPayout(new Payout(transactionID, senderPublicKey, fee, deadline, transactionAttachment));
        logger.info("Paid out, transaction id {}", transactionID);
        payoutSemaphore.release();
    }

    private void onPayoutError(Throwable throwable) {
        logger.error("Error occurred whilst paying out", throwable);
        payoutSemaphore.release();
    }

    @Override
    public void close() {
        compositeDisposable.dispose();
    }
}
