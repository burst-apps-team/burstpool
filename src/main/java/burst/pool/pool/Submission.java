package burst.pool.pool;

import burst.kit.entity.BurstAddress;

public class Submission {
    private final BurstAddress miner;
    private final String nonce;

    Submission(BurstAddress miner, String nonce) {
        this.miner = miner;
        this.nonce = nonce;
    }

    public BurstAddress getMiner() {
        return miner;
    }

    public String getNonce() {
        return nonce;
    }
}
