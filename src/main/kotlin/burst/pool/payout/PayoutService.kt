package burst.pool.payout

import burst.pool.storage.persistent.StorageService

interface PayoutService {
    fun payoutIfNeeded(storageService: StorageService)
}
