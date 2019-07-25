package burst.pool.payout;

import burst.pool.miners.Payable;
import burst.pool.miners.PoolFeeRecipient;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.Props;
import burst.pool.storage.persistent.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;

public abstract class AbstractPayoutService implements PayoutService {
    private static final Logger logger = LoggerFactory.getLogger(AbstractPayoutService.class);

    protected final Semaphore payoutSemaphore = new Semaphore(1);

    protected final PropertyService propertyService;

    protected AbstractPayoutService(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    public void payoutIfNeeded(StorageService storageService) {
        logger.info("Attempting payout...");
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
        for (Payable miner : storageService.getMiners()) {
            if (miner.getMinimumPayout().compareTo(miner.getPending()) <= 0) {
                payableMinersSet.add(miner);
            }
        }

        PoolFeeRecipient poolFeeRecipient = storageService.getPoolFeeRecipient();
        if (poolFeeRecipient.getMinimumPayout().compareTo(poolFeeRecipient.getPending()) <= 0) {
            payableMinersSet.add(poolFeeRecipient);
        }

        if (payableMinersSet.size() < 2 || (payableMinersSet.size() < propertyService.getInt(Props.minPayoutsPerTransaction) && payableMinersSet.size() < storageService.getMinerCount())) {
            payoutSemaphore.release();
            logger.info("Cannot payout. There are {} payable miners, required {}, miner count {}", payableMinersSet.size(), propertyService.getInt(Props.minPayoutsPerTransaction), storageService.getMinerCount());
            return;
        }

        Payable[] payableMiners = payableMinersSet.size() <= 64 ? payableMinersSet.toArray(new Payable[0]) : Arrays.copyOfRange(payableMinersSet.toArray(new Payable[0]), 0, 64);

        payOut(storageService, payableMiners);
    }

    protected abstract void payOut(StorageService storageService, Payable[] payableMiners);
}
