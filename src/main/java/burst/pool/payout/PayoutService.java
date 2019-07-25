package burst.pool.payout;

import burst.pool.storage.persistent.StorageService;

public interface PayoutService {
    void payoutIfNeeded(StorageService storageService);
}
