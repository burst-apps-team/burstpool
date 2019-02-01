package burst.pool.miners;

import burst.kit.burst.BurstCrypto;
import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;
import burst.kit.entity.response.BroadcastTransactionResponse;
import burst.kit.service.BurstNodeService;
import io.reactivex.disposables.CompositeDisposable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class MinerTracker {
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private final Map<BurstAddress, IMiner> miners = new ConcurrentHashMap<>();
    private final BurstCrypto burstCrypto = BurstCrypto.getInstance();
    private final BurstNodeService nodeService;
    private final MinerMaths minerMaths;

    // cannot be lower than 2
    private int minimumPayoutCount = 2;
    private double poolTakePercentage = 0.1;
    private double winnerTakePercentage = 0.1;
    private BurstValue minimumPayout = BurstValue.fromBurst(0);
    private String poolPassphrase = "a";
    private PoolFeeCollector poolFeeCollector = new PoolFeeCollector(burstCrypto.getBurstAddressFromPassphrase("b"), BurstValue.fromBurst(0));
    private BurstValue transactionFee = BurstValue.fromBurst(1);

    public MinerTracker(BurstNodeService nodeService, int nAvg, int nMin) {
        this.nodeService = nodeService;
        this.minerMaths = new MinerMaths(nAvg, nMin);
    }

    public void onMinerSubmittedDeadline(BurstAddress minerAddress, BigInteger deadline, BigInteger baseTarget, long blockHeight) {
        getOrCreate(minerAddress).processNewDeadline(new Deadline(deadline, baseTarget, blockHeight));
    }

    private IMiner getOrCreate(BurstAddress minerAddress) {
        IMiner miner;
        if (miners.containsKey(minerAddress)) {
            miner = miners.get(minerAddress);
        } else {
            miner = new Miner(minerMaths, minerAddress, BurstValue.fromBurst(0), 0, 0);
            miners.put(minerAddress, miner);
        }
        return miner;
    }

    public void onBlockWon(long blockHeight, BurstAddress winner, BurstValue reward) {
        System.out.println("Block won!");
        BurstValue ogReward = reward;

        // Take pool fee
        BurstValue poolTake = new BurstValue(reward.multiply(BigDecimal.valueOf(poolTakePercentage)));
        reward = new BurstValue(reward.subtract(poolTake));
        poolFeeCollector.increasePending(poolTake);

        // Take winner fee
        BurstValue winnerTake = new BurstValue(reward.multiply(BigDecimal.valueOf(winnerTakePercentage)));
        reward = new BurstValue(reward.subtract(winnerTake));
        getOrCreate(winner).increasePending(winnerTake);

        // Update each miner's effective capacity
        miners.forEach((address, miner) -> miner.recalculateCapacity(blockHeight));

        // Calculate pool capacity
        AtomicReference<Double> poolCapacity = new AtomicReference<>(0d);
        miners.forEach((address, miner) -> poolCapacity.updateAndGet(v -> (double) (v + miner.getCapacity())));

        // Update each miner's share
        miners.forEach((address, miner) -> miner.recalculateShare(poolCapacity.get()));

        // Update each miner's pending
        AtomicReference<BurstValue> amountTaken = new AtomicReference<>(BurstValue.fromBurst(0));
        BurstValue poolReward = reward;
        miners.forEach((address, miner) -> amountTaken.updateAndGet(a -> new BurstValue(a.add(miner.takeShare(poolReward)))));

        System.out.println("Reward is " + ogReward + ", pool take is " + poolTake + ", winner take is " + winnerTake + ", amount left is " + reward + ", miners took " + amountTaken.get());

        // Payout if needed
        payoutIfNeeded();
    }

    private void payoutIfNeeded() {
        Set<IMiner> payableMinersSet = new HashSet<>();
        for (IMiner miner : miners.values()) {
            if (minimumPayout.compareTo(miner.getPending()) <= 0) {
                payableMinersSet.add(miner);
            }
        }

        if (minimumPayout.compareTo(poolFeeCollector.getPending()) <= 0) {
            payableMinersSet.add(poolFeeCollector);
        }

        if ((payableMinersSet.size() < minimumPayoutCount && payableMinersSet.size() != miners.size()) || payableMinersSet.size() < 2) {
            return;
        }

        IMiner[] payableMiners = payableMinersSet.size() <= 64 ? payableMinersSet.toArray(new IMiner[0]) : Arrays.copyOfRange(payableMinersSet.toArray(new Miner[0]), 0, 64);

        Map<BurstAddress, BurstValue> recipients = new HashMap<>();
        for (IMiner miner : payableMiners) {
            recipients.put(miner.getAddress(), miner.getPending());
        }

        compositeDisposable.add(nodeService.generateMultiOutTransaction(burstCrypto.getPublicKey(poolPassphrase), transactionFee, 1440, recipients)
                .map(response -> burstCrypto.signTransaction(poolPassphrase, response.getUnsignedTransactionBytes().getBytes()))
                .flatMap(nodeService::broadcastTransaction)
                .subscribe(response -> onPaidOut(response, payableMiners), this::onPayoutError));
    }

    private void onPaidOut(BroadcastTransactionResponse response, IMiner[] paidMiners) {
        for (IMiner miner : paidMiners) {
            miner.zeroPending();
        }
        System.out.println("Paid out, transaction id " + response.getTransactionID());
    }

    private void onPayoutError(Throwable throwable) {
        throwable.printStackTrace();
    }
}
