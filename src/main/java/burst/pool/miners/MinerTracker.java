package burst.pool.miners;

import burst.kit.burst.BurstCrypto;
import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;
import burst.kit.entity.response.AccountResponse;
import burst.kit.entity.response.BroadcastTransactionResponse;
import burst.kit.service.BurstNodeService;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.Props;
import burst.pool.storage.persistent.StorageService;
import io.reactivex.disposables.CompositeDisposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

public class MinerTracker {
    private static final Logger logger = LoggerFactory.getLogger(MinerTracker.class);

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private final PropertyService propertyService;
    private final BurstCrypto burstCrypto = BurstCrypto.getInstance();
    private final BurstNodeService nodeService;

    private final Semaphore payoutSemaphore = new Semaphore(1);

    public MinerTracker(BurstNodeService nodeService, PropertyService propertyService) {
        this.nodeService = nodeService;
        this.propertyService = propertyService;
    }

    public void onMinerSubmittedDeadline(StorageService storageService, BurstAddress minerAddress, BigInteger deadline, BigInteger baseTarget, long blockHeight, String userAgent) {
        Miner miner = getOrCreate(storageService, minerAddress);
        miner.processNewDeadline(new Deadline(deadline, baseTarget, blockHeight));
        miner.setUserAgent(userAgent);
        compositeDisposable.add(nodeService.getAccount(minerAddress).subscribe(accountResponse -> onMinerAccount(storageService, accountResponse), this::onMinerAccountError));
    }

    private Miner getOrCreate(StorageService storageService, BurstAddress minerAddress) {
        Miner miner = storageService.getMiner(minerAddress);
        if (miner == null) {
            miner = storageService.newMiner(minerAddress);
        }
        return miner;
    }

    public void onBlockWon(StorageService transactionalStorageService, long blockHeight, BurstAddress winner, BurstValue blockReward, List<Long> fastBlocks) {
        logger.info("Block won! Block height: " + blockHeight + ", forger: " + winner.getFullAddress());
        BurstValue reward = blockReward;

        // Take pool fee
        BurstValue poolTake = new BurstValue(reward.multiply(BigDecimal.valueOf(propertyService.getFloat(Props.poolFeePercentage))));
        reward = new BurstValue(reward.subtract(poolTake));
        PoolFeeRecipient poolFeeRecipient = transactionalStorageService.getPoolFeeRecipient();
        poolFeeRecipient.increasePending(poolTake);

        // Take winner fee
        BurstValue winnerTake = new BurstValue(reward.multiply(BigDecimal.valueOf(propertyService.getFloat(Props.winnerRewardPercentage))));
        reward = new BurstValue(reward.subtract(winnerTake));
        Miner winningMiner = getOrCreate(transactionalStorageService, winner);
        winningMiner.increasePending(winnerTake);

        List<Miner> miners = transactionalStorageService.getMiners();

        updateMiners(miners, blockHeight, fastBlocks);

        // Update each miner's pending
        AtomicReference<BurstValue> amountTaken = new AtomicReference<>(BurstValue.fromBurst(0));
        BurstValue poolReward = reward;
        miners.forEach(miner -> amountTaken.updateAndGet(a -> new BurstValue(a.add(miner.takeShare(poolReward)))));

        // Evenly share result. This makes sure that poolReward is taken, even if the amountTaken was greater than poolReward
        // Essentially prevents the pool from overpaying or underpaying. Even if it gave out too much to the fee recipient and reward recipient, it will now take the extra from the pending of miners.
        if (miners.size() > 0) {
            BurstValue amountRemainingEach = new BurstValue(poolReward.subtract(amountTaken.get()).divide(BigDecimal.valueOf(miners.size()), RoundingMode.DOWN));
            miners.forEach(miner -> miner.increasePending(amountRemainingEach));
        }

        logger.info("Finished processing winnings for block " + blockHeight + ". Reward ( + fees) is " + blockReward + ", pool fee is " + poolTake + ", forger take is " + winnerTake + ", miners took " + amountTaken.get());

        payoutIfNeeded(transactionalStorageService);
    }

    public void onBlockNotWon(StorageService transactionalStorageService, long blockHeight, List<Long> fastBlocks) {
        updateMiners(transactionalStorageService.getMiners(), blockHeight, fastBlocks);
    }

    private void updateMiners(List<Miner> miners, long blockHeight, List<Long> fastBlocks) {
        // Update each miner's effective capacity
        miners.forEach(miner -> miner.recalculateCapacity(blockHeight, fastBlocks));

        // Calculate pool capacity
        AtomicReference<Double> poolCapacity = new AtomicReference<>(0d);
        miners.forEach(miner -> poolCapacity.updateAndGet(v -> (double) (v + miner.getCapacity())));

        // Update each miner's share
        miners.forEach(miner -> miner.recalculateShare(poolCapacity.get()));
    }

    private void payoutIfNeeded(StorageService transactionalStorageService) {
        if (payoutSemaphore.availablePermits() == 0) {
            logger.info("Cannot payout - payout is already in progress.");
            return;
        }

        try {
            payoutSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Set<Payable> payableMinersSet = new HashSet<>();
        for (Payable miner : transactionalStorageService.getMiners()) {
            if (miner.getMinimumPayout().compareTo(miner.getPending()) <= 0) {
                payableMinersSet.add(miner);
            }
        }

        PoolFeeRecipient poolFeeRecipient = transactionalStorageService.getPoolFeeRecipient();
        if (poolFeeRecipient.getMinimumPayout().compareTo(poolFeeRecipient.getPending()) <= 0) {
            payableMinersSet.add(poolFeeRecipient);
        }

        if (payableMinersSet.size() < 2 || (payableMinersSet.size() < propertyService.getInt(Props.minPayoutsPerTransaction) && payableMinersSet.size() < transactionalStorageService.getMinerCount())) {
            payoutSemaphore.release();
            return;
        }

        Payable[] payableMiners = payableMinersSet.size() <= 64 ? payableMinersSet.toArray(new Payable[0]) : Arrays.copyOfRange(payableMinersSet.toArray(new Payable[0]), 0, 64);

        BurstValue transactionFee = BurstValue.fromBurst(propertyService.getFloat(Props.transactionFee));
        BurstValue transactionFeePaidPerMiner = new BurstValue(transactionFee.divide(BigDecimal.valueOf(payableMiners.length), BigDecimal.ROUND_CEILING));
        Map<Payable, BurstValue> payees = new HashMap<>(); // Does not have subtracted transaction fee
        Map<BurstAddress, BurstValue> recipients = new HashMap<>();
        StringBuilder logMessage = new StringBuilder("Paying out to miners");
        for (Payable payable : payableMiners) {
            payees.put(payable, payable.getPending());
            BurstValue actualPayout = new BurstValue(payable.getPending().subtract(transactionFeePaidPerMiner));
            recipients.put(payable.getAddress(), actualPayout);
            logMessage.append(", ").append(payable.getAddress().getFullAddress()).append("(").append(actualPayout).append(")");
        }
        logger.info(logMessage.toString());

        compositeDisposable.add(nodeService.generateMultiOutTransaction(burstCrypto.getPublicKey(propertyService.getString(Props.passphrase)), transactionFee, 1440, recipients)
                .retry(propertyService.getInt(Props.payoutRetryCount))
                .map(response -> burstCrypto.signTransaction(propertyService.getString(Props.passphrase), response.getUnsignedTransactionBytes().getBytes()))
                .flatMap(signedBytes -> nodeService.broadcastTransaction(signedBytes).retry(propertyService.getInt(Props.payoutRetryCount)))
                .subscribe(response -> onPaidOut(response, payees), this::onPayoutError));
    }

    private void onPaidOut(BroadcastTransactionResponse response, Map<Payable, BurstValue> paidMiners) {
        for (Map.Entry<Payable, BurstValue> payment : paidMiners.entrySet()) {
            payment.getKey().decreasePending(payment.getValue());
        }
        // todo store
        logger.info("Paid out, transaction id " + response.getTransactionID());
        payoutSemaphore.release();
    }

    private void onPayoutError(Throwable throwable) {
        logger.error("Error occurred whilst paying out", throwable);
        payoutSemaphore.release();
    }


    private void onMinerAccount(StorageService storageService, AccountResponse accountResponse) {
        Miner miner = storageService.getMiner(accountResponse.getAccount());
        if (miner == null) return;
        if (accountResponse.getName() == null) return;
        miner.setName(accountResponse.getName());
    }

    private void onMinerAccountError(Throwable throwable) {
        logger.warn("Error obtaining miner account info", throwable);
    }
}
