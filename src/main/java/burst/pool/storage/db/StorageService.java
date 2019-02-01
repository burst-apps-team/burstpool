package burst.pool.storage.db;

import burst.kit.entity.BurstAddress;
import burst.pool.miners.IMiner;
import burst.pool.miners.Miner;
import burst.pool.miners.PoolFeeRecipient;
import burst.pool.pool.Submission;

import java.util.List;

public interface StorageService {
    int getMinerCount();
    List<Miner> getMiners();
    IMiner getMiner(BurstAddress address);
    void setMiners(IMiner[] miners);
    void setMiner(BurstAddress address, IMiner miner);

    PoolFeeRecipient getPoolFeeRecipient();
    void setPoolFeeRecipient(PoolFeeRecipient poolFeeRecipient);

    int getLastProcessedBlock();
    void incrementLastProcessedBlock();

    Submission getBestSubmissionForBlock(long blockHeight);
    void setBestSubmissionForBlock(long blockHeight, Submission submission);
}
