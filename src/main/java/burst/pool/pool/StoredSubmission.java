package burst.pool.pool;

import burst.kit.entity.BurstAddress;

import java.math.BigInteger;

public class StoredSubmission extends Submission {
    private final long deadline;
    public StoredSubmission(BurstAddress miner, BigInteger nonce, long deadline) {
        super(miner, nonce);
        this.deadline = deadline;
    }

    public long getDeadline() {
        return deadline;
    }
}
