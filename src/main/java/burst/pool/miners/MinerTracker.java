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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

public class MinerTracker {
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private final StorageService storageService;
    private final PropertyService propertyService;
    private final BurstCrypto burstCrypto = BurstCrypto.getInstance();
    private final BurstNodeService nodeService;
    private final MinerMaths minerMaths;

    private final Semaphore payoutSemaphore = new Semaphore(1);

    public MinerTracker(BurstNodeService nodeService, StorageService storageService, PropertyService propertyService) {
        this.nodeService = nodeService;
        this.storageService = storageService;
        this.propertyService = propertyService;
        this.minerMaths = new MinerMaths(propertyService.getInt(Props.nAvg), propertyService.getInt(Props.nMin));
    }

    public void onMinerSubmittedDeadline(BurstAddress minerAddress, BigInteger deadline, BigInteger baseTarget, long blockHeight, String userAgent) {
        Miner miner = getOrCreate(minerAddress);
        miner.processNewDeadline(new Deadline(deadline, baseTarget, blockHeight));
        miner.setUserAgent(userAgent);
        storageService.setMiner(minerAddress, miner);
        compositeDisposable.add(nodeService.getAccount(minerAddress).subscribe(this::onMinerAccount, this::onMinerAccountError));
    }

    private Miner getOrCreate(BurstAddress minerAddress) {
        Miner miner = storageService.getMiner(minerAddress);
        if (miner == null) {
            miner = new Miner(minerMaths, propertyService, minerAddress, BurstValue.fromBurst(0), 0, 0, "", "");
            storageService.setMiner(minerAddress, miner);
        }
        return miner;
    }

    public void onBlockWon(long blockHeight, BurstAddress winner, BurstValue reward) {
        System.out.println("Block won!");
        BurstValue ogReward = reward;

        // Take pool fee
        BurstValue poolTake = new BurstValue(reward.multiply(BigDecimal.valueOf(propertyService.getFloat(Props.poolFeePercentage))));
        reward = new BurstValue(reward.subtract(poolTake));
        PoolFeeRecipient poolFeeRecipient = storageService.getPoolFeeRecipient();
        poolFeeRecipient.increasePending(poolTake);
        storageService.setPoolFeeRecipient(poolFeeRecipient);

        // Take winner fee
        BurstValue winnerTake = new BurstValue(reward.multiply(BigDecimal.valueOf(propertyService.getFloat(Props.winnerRewardPercentage))));
        reward = new BurstValue(reward.subtract(winnerTake));
        Miner winningMiner = getOrCreate(winner);
        winningMiner.increasePending(winnerTake);
        storageService.setMiner(winner, winningMiner);

        List<Miner> miners = storageService.getMiners();

        // Update each miner's effective capacity
        miners.forEach(miner -> miner.recalculateCapacity(blockHeight));

        // Calculate pool capacity
        AtomicReference<Double> poolCapacity = new AtomicReference<>(0d);
        miners.forEach(miner -> poolCapacity.updateAndGet(v -> (double) (v + miner.getCapacity())));

        // Update each miner's share
        miners.forEach(miner -> miner.recalculateShare(poolCapacity.get()));

        // Update each miner's pending
        AtomicReference<BurstValue> amountTaken = new AtomicReference<>(BurstValue.fromBurst(0));
        BurstValue poolReward = reward;
        miners.forEach(miner -> amountTaken.updateAndGet(a -> new BurstValue(a.add(miner.takeShare(poolReward)))));

        storageService.setMiners(miners);

        System.out.println("Reward is " + ogReward + ", pool take is " + poolTake + ", winner take is " + winnerTake + ", amount left is " + reward + ", miners took " + amountTaken.get());

        // Payout if needed
        payoutIfNeeded();
    }

    private void payoutIfNeeded() {
        if (payoutSemaphore.availablePermits() == 0) {
            System.out.println("Cannot payout, payout is in progress!");
            return;
        }

        try {
            payoutSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Set<IMiner> payableMinersSet = new HashSet<>();
        for (IMiner miner : storageService.getMiners()) {
            if (BurstValue.fromBurst(propertyService.getFloat(Props.minimumPayout)).compareTo(miner.getPending()) <= 0) {
                payableMinersSet.add(miner);
            }
        }

        PoolFeeRecipient poolFeeRecipient = storageService.getPoolFeeRecipient();
        if (BurstValue.fromBurst(propertyService.getFloat(Props.minimumPayout)).compareTo(poolFeeRecipient.getPending()) <= 0) {
            payableMinersSet.add(poolFeeRecipient);
        }

        if (payableMinersSet.size() < 2 || (payableMinersSet.size() < propertyService.getInt(Props.minPayoutsPerTransaction) && payableMinersSet.size() < storageService.getMinerCount())) {
            payoutSemaphore.release();
            return;
        }

        IMiner[] payableMiners = payableMinersSet.size() <= 64 ? payableMinersSet.toArray(new IMiner[0]) : Arrays.copyOfRange(payableMinersSet.toArray(new IMiner[0]), 0, 64);

        BurstValue transactionFee = BurstValue.fromBurst(propertyService.getFloat(Props.transactionFee));
        BurstValue transactionFeePaidPerMiner = new BurstValue(transactionFee.divide(BigDecimal.valueOf(payableMiners.length), BigDecimal.ROUND_CEILING));
        Map<BurstAddress, BurstValue> payouts = new HashMap<>(); // Does not have subtracted transaction fee
        Map<BurstAddress, BurstValue> recipients = new HashMap<>();
        for (IMiner miner : payableMiners) {
            payouts.put(miner.getAddress(), miner.getPending());
            recipients.put(miner.getAddress(), new BurstValue(miner.getPending().subtract(transactionFeePaidPerMiner)));
        }

        compositeDisposable.add(nodeService.generateMultiOutTransaction(burstCrypto.getPublicKey(propertyService.getString(Props.passphrase)), transactionFee, 1440, recipients)
                .map(response -> burstCrypto.signTransaction(propertyService.getString(Props.passphrase), response.getUnsignedTransactionBytes().getBytes()))
                .flatMap(nodeService::broadcastTransaction)
                .subscribe(response -> onPaidOut(response, payouts), this::onPayoutError));
    }

    private void onPaidOut(BroadcastTransactionResponse response, Map<BurstAddress, BurstValue> paidMiners) {
        BurstAddress feeRecipientAddress = propertyService.getBurstAddress(Props.feeRecipient);
        for (Map.Entry<BurstAddress, BurstValue> payment : paidMiners.entrySet()) {
            boolean isFeeRecipient = Objects.equals(feeRecipientAddress, payment.getKey());
            IMiner miner = isFeeRecipient ? storageService.getPoolFeeRecipient() : storageService.getMiner(payment.getKey());
            if (miner == null) return;
            miner.decreasePending(payment.getValue());
            if (isFeeRecipient) {
                storageService.setPoolFeeRecipient((PoolFeeRecipient) miner);
            } else {
                storageService.setMiner(miner.getAddress(), (Miner) miner);
            }
        }
        // todo store
        System.out.println("Paid out, transaction id " + response.getTransactionID());
        payoutSemaphore.release();
    }

    private void onPayoutError(Throwable throwable) {
        throwable.printStackTrace();
        payoutSemaphore.release();
    }


    private void onMinerAccount(AccountResponse accountResponse) {
        Miner miner = storageService.getMiner(accountResponse.getAccount());
        if (miner == null) return;
        if (accountResponse.getName() == null) return;
        miner.setName(accountResponse.getName());
        storageService.setMiner(miner.getAddress(), miner);
    }

    private void onMinerAccountError(Throwable throwable) {
        throwable.printStackTrace();
    }
}
