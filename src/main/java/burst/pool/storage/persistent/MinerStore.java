package burst.pool.storage.persistent;

import burst.pool.miners.Deadline;

import java.util.List;

public interface MinerStore {
    double getPendingBalance();
    void setPendingBalance(double pendingBalance);
    
    double getEstimatedCapacity();
    void setEstimatedCapacity(double estimatedCapacity);
    
    double getShare();
    void setShare(double share);
    
    double getHitSum();
    void setHitSum(double hitSum);

    double getMinimumPayout();
    void setMinimumPayout(double minimumPayout);
    
    String getName();
    void setName(String name);
    
    String getUserAgent();
    void setUserAgent(String userAgent);

    List<Deadline> getDeadlines();
    int getDeadlineCount();
    void removeDeadline(long height);
    Deadline getDeadline(long height);
    void setDeadline(long height, Deadline deadline);

    interface FeeRecipientStore {
        double getPendingBalance();
        void setPendingBalance(double pending);
    }
}
