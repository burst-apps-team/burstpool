package burst.pool.miners;


import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;

import java.util.concurrent.atomic.AtomicReference;

public class Miner {
    private final BurstAddress address;
    private final AtomicReference<BurstValue> pendingBalance;
    private final AtomicReference<Double> historicalShare;
    private final AtomicReference<String> userAgent;

    public Miner(BurstAddress address, BurstValue pendingBalance, double historicalShare, String userAgent) {
        this.address = address;
        this.pendingBalance = new AtomicReference<>(pendingBalance);
        this.historicalShare = new AtomicReference<>(historicalShare);
        this.userAgent = new AtomicReference<>(userAgent);
    }
}
