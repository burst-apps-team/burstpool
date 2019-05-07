package burst.pool.pool;

import burst.kit.entity.BurstAddress;

import java.math.BigInteger;

public class Submission {
    private final BurstAddress miner;
    private final BigInteger nonce;

    public Submission(BurstAddress miner, BigInteger nonce) {
        this.miner = miner;
        this.nonce = nonce;
    }

    public BurstAddress getMiner() {
        return miner;
    }

    public BigInteger getNonce() {
        return nonce;
    }
}
