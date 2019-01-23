package burst.pool.pool;

public class NonceSubmissionResponse {
    private final String result;
    private final String deadline;

    public NonceSubmissionResponse(String result, String deadline) {
        this.result = result;
        this.deadline = deadline;
    }
}
