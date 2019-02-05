package burst.pool.miners;

import java.math.BigInteger;

public class Deadline {
    private final BigInteger deadline;
    private final BigInteger baseTarget;
    private final long height;

    public Deadline(BigInteger deadline, BigInteger baseTarget, long height) {
        this.deadline = deadline;
        this.baseTarget = baseTarget;
        this.height = height;
    }

    public BigInteger getDeadline() {
        return deadline;
    }

    public BigInteger getBaseTarget() {
        return baseTarget;
    }

    public long getHeight() {
        return height;
    }

    public BigInteger calculateHit() {
        return baseTarget.multiply(deadline);
    }
}
