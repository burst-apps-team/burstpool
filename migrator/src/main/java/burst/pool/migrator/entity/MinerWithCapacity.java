package burst.pool.migrator.entity;

import burst.pool.migrator.nogroddb.tables.records.AccountRecord;

public class MinerWithCapacity {
    private final AccountRecord account;
    private final long capacity;

    public MinerWithCapacity(AccountRecord account, long capacity) {
        this.account = account;
        this.capacity = capacity;
    }

    public AccountRecord getAccount() {
        return account;
    }

    public long getCapacity() {
        return capacity;
    }
}
