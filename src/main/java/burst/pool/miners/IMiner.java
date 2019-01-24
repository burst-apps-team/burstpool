package burst.pool.miners;

import burst.kit.entity.BurstValue;

public interface IMiner {
    void processNewDeadline(Deadline deadline);
    void recalculateCapacity(long currentBlockHeight);
    void recalculateShare(double poolCapacity);
    void increasePending(BurstValue availableReward);

    double getCapacity();
    double getShare();
}
