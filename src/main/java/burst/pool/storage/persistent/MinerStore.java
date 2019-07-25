package burst.pool.storage.persistent;

import burst.kit.entity.BurstValue;
import burst.pool.miners.Deadline;

import java.util.List;

public interface MinerStore {
    BurstValue getPendingBalance();
    void setPendingBalance(BurstValue pendingBalance);
    
    double getEstimatedCapacity();
    void setEstimatedCapacity(double estimatedCapacity);
    
    double getShare();
    void setShare(double share);

    BurstValue getMinimumPayout();
    void setMinimumPayout(BurstValue minimumPayout);
    
    String getName();
    void setName(String name);
    
    String getUserAgent();
    void setUserAgent(String userAgent);

    List<Deadline> getDeadlines();
    int getDeadlineCount();
    void removeDeadline(long height);
    Deadline getDeadline(long height);
    void setOrUpdateDeadline(long height, Deadline deadline);

    interface FeeRecipientStore {
        BurstValue getPendingBalance();
        void setPendingBalance(BurstValue pending);
    }
}
