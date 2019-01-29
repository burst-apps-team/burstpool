package burst.pool.miners;

import burst.kit.burst.BurstCrypto;
import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;
import burst.kit.entity.response.BroadcastTransactionResponse;
import burst.kit.service.BurstNodeService;
import io.reactivex.disposables.CompositeDisposable;

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
    private int minimumPayoutCount;
    private BurstValue minimumPayout;
    private String poolPassphrase;
    private BurstValue transactionFee;

    public MinerTracker(BurstNodeService nodeService, int nAvg, int nMin) {
        this.nodeService = nodeService;
        this.minerMaths = new MinerMaths(nAvg, nMin);

    }

    public void onMinerSubmittedDeadline(BurstAddress minerAddress, BigInteger deadline, BigInteger baseTarget, long blockHeight) {
        IMiner miner;
        if (miners.containsKey(minerAddress)) {
            miner = miners.get(minerAddress);
        } else {
            miner = new Miner(minerMaths, minerAddress, BurstValue.fromBurst(0), 0, 0);
            miners.put(minerAddress, miner);
        }

        miner.processNewDeadline(new Deadline(deadline, baseTarget, blockHeight));
    }

    public void onBlockWon(long blockHeight, BurstAddress winner, BurstValue reward) { // todo give amount to winner, take fee
        // Update each miner's effective capacity
        miners.forEach((address, miner) -> miner.recalculateCapacity(blockHeight));

        // Calculate pool capacity
        AtomicReference<Double> poolCapacity = new AtomicReference<>(0d);
        miners.forEach((address, miner) -> poolCapacity.updateAndGet(v -> (double) (v + miner.getCapacity())));

        // Update each miner's share
        miners.forEach((address, miner) -> miner.recalculateShare(poolCapacity.get()));

        // Update each miner's pending
        miners.forEach((address, miner) -> miner.increasePending(reward));

        // Payout if needed
        payoutIfNeeded();
        System.out.println(miners);
    }

    private void payoutIfNeeded() {
        Set<IMiner> payableMinersSet = new HashSet<>();
        for (IMiner miner : miners.values()) {
            if (minimumPayout.compareTo(miner.getPending()) <= 0) {
                payableMinersSet.add(miner);
            }
        }

        if (payableMinersSet.size() < minimumPayoutCount && payableMinersSet.size() != miners.size()) {
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
