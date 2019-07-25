package burst.pool.miners;

import burst.kit.crypto.BurstCrypto;
import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstID;
import burst.kit.entity.BurstValue;
import burst.kit.entity.response.Account;
import burst.kit.service.BurstNodeService;
import burst.pool.entity.Payout;
import burst.pool.entity.WonBlock;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.Props;
import burst.pool.storage.persistent.StorageService;
import io.reactivex.disposables.CompositeDisposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MinerTracker implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MinerTracker.class);

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private final PropertyService propertyService;
    private final BurstNodeService nodeService;
    private final AtomicBoolean currentlyProcessingBlock = new AtomicBoolean(false);

    public MinerTracker(BurstNodeService nodeService, PropertyService propertyService) {
        this.nodeService = nodeService;
        this.propertyService = propertyService;
    }

    public void onMinerSubmittedDeadline(StorageService storageService, BurstAddress minerAddress, BigInteger deadline, BigInteger baseTarget, long blockHeight, String userAgent) {
        waitUntilNotProcessingBlock();
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

    public void onBlockWon(StorageService transactionalStorageService, long blockHeight, BurstID blockId, BigInteger nonce, BurstAddress winner, BurstValue blockReward, List<Long> fastBlocks) {
        logger.info("Block won! Block height: " + blockHeight + ", forger: " + winner.getFullAddress());

        transactionalStorageService.addWonBlock(new WonBlock((int) blockHeight, blockId, winner, nonce, blockReward));

        BurstValue reward = blockReward;

        // Take pool fee
        BurstValue poolTake = reward.multiply(propertyService.getFloat(Props.poolFeePercentage));
        reward = reward.subtract(poolTake);
        PoolFeeRecipient poolFeeRecipient = transactionalStorageService.getPoolFeeRecipient();
        poolFeeRecipient.increasePending(poolTake);

        // Take winner fee
        BurstValue winnerTake = reward.multiply(propertyService.getFloat(Props.winnerRewardPercentage));
        reward = reward.subtract(winnerTake);
        Miner winningMiner = getOrCreate(transactionalStorageService, winner);
        winningMiner.increasePending(winnerTake);

        List<Miner> miners = transactionalStorageService.getMiners();

        updateMiners(miners, blockHeight, fastBlocks);

        // Update each miner's pending
        AtomicReference<BurstValue> amountTaken = new AtomicReference<>(BurstValue.fromBurst(0));
        BurstValue poolReward = reward;
        miners.forEach(miner -> amountTaken.updateAndGet(a -> a.add(miner.takeShare(poolReward))));

        // Evenly share result. This makes sure that poolReward is taken, even if the amountTaken was greater than poolReward
        // Essentially prevents the pool from overpaying or underpaying. Even if it gave out too much to the fee recipient and reward recipient, it will now take the extra from the pending of miners.
        if (!miners.isEmpty()) {
            BurstValue amountRemainingEach = poolReward.subtract(amountTaken.get()).divide(miners.size());
            if (logger.isInfoEnabled()) {
                logger.info("Amount remaining each is {}", amountRemainingEach.toPlanck());
            }
            miners.forEach(miner -> miner.increasePending(amountRemainingEach));
        }

        logger.info("Finished processing winnings for block " + blockHeight + ". Reward ( + fees) is " + blockReward + ", pool fee is " + poolTake + ", forger take is " + winnerTake + ", miners took " + amountTaken.get());
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

    public void setMinerMinimumPayout(StorageService storageService, BurstAddress minerAddress, BurstValue amount) {
        waitUntilNotProcessingBlock();
        Miner miner = storageService.getMiner(minerAddress);
        if (miner == null) return;
        miner.setMinimumPayout(amount);
    }

    private void onMinerAccount(StorageService storageService, Account accountResponse) {
        waitUntilNotProcessingBlock();
        Miner miner = storageService.getMiner(accountResponse.getId());
        if (miner == null) return;
        if (accountResponse.getName() == null) return;
        miner.setName(accountResponse.getName());
    }

    private void onMinerAccountError(Throwable throwable) {
        logger.warn("Error obtaining miner account info", throwable);
    }

    public void waitUntilNotProcessingBlock() {
        while (currentlyProcessingBlock.get()) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void setCurrentlyProcessingBlock(boolean currentlyProcessingBlock){
        this.currentlyProcessingBlock.set(currentlyProcessingBlock);
    }

    @Override
    public void close() {
        compositeDisposable.dispose();
    }
}
