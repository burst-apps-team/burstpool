package burst.pool.pool;

import burst.kit.entity.BurstAddress;
import burst.kit.util.BurstKitUtils;
import com.google.gson.Gson;
import fi.iki.elonen.NanoHTTPD;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Server extends NanoHTTPD {
    private final Pool pool;
    private final Gson gson = BurstKitUtils.buildGson().create();

    public Server(Pool pool) {
        super(8124);
        this.pool = pool;
    }

    private BurstAddress parseAddressOrNull(String address) {
        if (address == null) {
            return null;
        } else {
            return BurstAddress.fromEither(address); // todo make this return null on null input
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            StringBuilder response = new StringBuilder();
            Map<String, String> params = queryToMap(session.getQueryParameterString());
            session.parseBody(new HashMap<>());
            params.putAll(session.getParms());
            if (session.getMethod().equals(Method.POST) && session.getUri().contains("/burst") && Objects.equals(params.get("requestType"), "submitNonce")) {
                Submission submission = new Submission(parseAddressOrNull(params.get("accountId")), params.get("nonce"));
                try {
                    if (submission.getMiner() == null) {
                        throw new SubmissionException("Account ID not set");
                    }
                    if (submission.getNonce() == null) {
                        throw new SubmissionException("Nonce not set");
                    }

                    response.append(gson.toJson(new NonceSubmissionResponse("success", pool.checkNewSubmission(submission).toString())));
                } catch (SubmissionException e) {
                    response.append(gson.toJson(new NonceSubmissionResponse(e.getMessage(), null)));
                }
            } else if (session.getUri().contains("/burst") && Objects.equals(params.get("requestType"), "getMiningInfo")) {
                response.append(gson.toJson(pool.getMiningInfo()));
            }
            return NanoHTTPD.newFixedLengthResponse(response.toString());
        } catch (Throwable t) {
            t.printStackTrace();
            return NanoHTTPD.newFixedLengthResponse(t.getMessage());
        }
    }

    private static Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) return result;
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }
}
