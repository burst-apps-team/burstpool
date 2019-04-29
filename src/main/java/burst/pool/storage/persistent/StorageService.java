package burst.pool.storage.persistent;

import burst.kit.entity.BurstAddress;
import burst.pool.entity.Payout;
import burst.pool.entity.WonBlock;
import burst.pool.miners.Miner;
import burst.pool.miners.PoolFeeRecipient;
import burst.pool.pool.StoredSubmission;

import java.util.List;
import java.util.Map;

public interface StorageService extends AutoCloseable {
    StorageService beginTransaction() throws Exception;
    void commitTransaction() throws Exception;
    void rollbackTransaction() throws Exception;

    int getMinerCount();
    List<Miner> getMiners();
    Miner getMiner(BurstAddress address);
    Miner newMiner(BurstAddress address);

    PoolFeeRecipient getPoolFeeRecipient();

    int getLastProcessedBlock();
    void incrementLastProcessedBlock();

    Map<Long, StoredSubmission> getBestSubmissions();
    StoredSubmission getBestSubmissionForBlock(long blockHeight);
    void setOrUpdateBestSubmissionForBlock(long blockHeight, StoredSubmission submission);
    void removeBestSubmission(long blockHeight);

    void addWonBlock(WonBlock wonBlock);

    void addPayout(Payout payout);
}
