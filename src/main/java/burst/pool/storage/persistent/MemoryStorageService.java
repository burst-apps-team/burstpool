package burst.pool.storage.persistent;

import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;
import burst.pool.miners.IMiner;
import burst.pool.miners.PoolFeeRecipient;
import burst.pool.pool.Submission;
import burst.pool.storage.config.PropertyService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class MemoryStorageService implements StorageService {
    private final PropertyService propertyService;

    private final Map<BurstAddress, IMiner> miners = new HashMap<>();
    private final Map<Long, Submission> bestSubmissionForBlock = new HashMap<>();
    private final AtomicReference<PoolFeeRecipient> poolFeeRecipient = new AtomicReference<>();
    private final AtomicInteger lastProcessedBlock = new AtomicInteger(0);

    public MemoryStorageService(PropertyService propertyService) {
        this.propertyService = propertyService;
        poolFeeRecipient.set(new PoolFeeRecipient(propertyService, BurstValue.fromBurst(0)));
    }

    @Override
    public int getMinerCount() {
        synchronized (miners) {
            return miners.size();
        }
    }

    @Override
    public List<IMiner> getMiners() {
        synchronized (miners) {
            return new ArrayList<>(miners.values());
        }
    }

    @Override
    public IMiner getMiner(BurstAddress address) {
        synchronized (miners) {
            return miners.get(address);
        }
    }

    @Override
    public void setMiners(List<IMiner> miners) {
        synchronized (this.miners) {
            this.miners.entrySet().removeIf(a -> true);
            for (IMiner miner : miners) {
                this.miners.put(miner.getAddress(), miner);
            }
        }
    }

    @Override
    public void setMiner(BurstAddress address, IMiner miner) {
        synchronized (miners) {
            miners.put(address, miner);
        }
    }

    @Override
    public PoolFeeRecipient getPoolFeeRecipient() {
        synchronized (poolFeeRecipient) {
            return poolFeeRecipient.get();
        }
    }

    @Override
    public void setPoolFeeRecipient(PoolFeeRecipient poolFeeRecipient) {
        synchronized (this.poolFeeRecipient) {
            this.poolFeeRecipient.set(poolFeeRecipient);
        }
    }

    @Override
    public int getLastProcessedBlock() {
        synchronized (lastProcessedBlock) {
            return lastProcessedBlock.get();
        }
    }

    @Override
    public void incrementLastProcessedBlock() {
        synchronized (lastProcessedBlock) {
            lastProcessedBlock.updateAndGet(val -> val+1);
        }
    }

    @Override
    public Submission getBestSubmissionForBlock(long blockHeight) {
        synchronized (bestSubmissionForBlock) {
            return bestSubmissionForBlock.get(blockHeight);
        }
    }

    @Override
    public void setBestSubmissionForBlock(long blockHeight, Submission submission) {
        synchronized (bestSubmissionForBlock) {
            bestSubmissionForBlock.put(blockHeight, submission);
        }
    }
}
