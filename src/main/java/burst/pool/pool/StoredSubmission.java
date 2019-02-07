package burst.pool.pool;

import burst.kit.entity.BurstAddress;

public class StoredSubmission extends Submission {
    private final long deadline;
    public StoredSubmission(BurstAddress miner, String nonce, long deadline) {
        super(miner, nonce);
        this.deadline = deadline;
    }

    public long getDeadline() {
        return deadline;
    }
}
