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
    List<Miner> getMinersFiltered();
    Miner getMiner(BurstAddress address);
    Miner newMiner(BurstAddress address);

    PoolFeeRecipient getPoolFeeRecipient();

    int getLastProcessedBlock();
    void incrementLastProcessedBlock();

    Map<Long, List<StoredSubmission>> getBestSubmissions();
    List<StoredSubmission> getBestSubmissionsForBlock(long blockHeight);
    void addBestSubmissionForBlock(long blockHeight, StoredSubmission submission);
    void removeBestSubmission(long blockHeight); // TODO unused

    void addWonBlock(WonBlock wonBlock);
    List<WonBlock> getWonBlocks(int limit);

    void addPayout(Payout payout);
}
