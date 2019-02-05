package burst.pool.pool;

import java.math.BigInteger;

public class NonceSubmissionResponse {
    private final String result;
    private final BigInteger deadline;

    public NonceSubmissionResponse(String result, BigInteger deadline) {
        this.result = result;
        this.deadline = deadline;
    }
}
