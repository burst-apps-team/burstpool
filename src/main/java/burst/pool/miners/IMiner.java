package burst.pool.miners;

import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;

public interface IMiner {
    void processNewDeadline(Deadline deadline);
    void recalculateCapacity(long currentBlockHeight);
    void recalculateShare(double poolCapacity);
    void increasePending(BurstValue availableReward);
    void zeroPending();

    double getCapacity();
    BurstValue getPending();
    BurstAddress getAddress();

    // Needed for API only.
    double getShare();
    int getNConf();
}
