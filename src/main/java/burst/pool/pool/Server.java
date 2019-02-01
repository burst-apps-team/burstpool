package burst.pool.pool;

import burst.kit.entity.BurstAddress;
import burst.kit.util.BurstKitUtils;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.Props;
import com.google.gson.Gson;
import fi.iki.elonen.NanoHTTPD;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Server extends NanoHTTPD {
    private final Pool pool;
    private final Gson gson = BurstKitUtils.buildGson().create();

    public Server(PropertyService propertyService, Pool pool) {
        super(propertyService.getInt(Props.serverPort));
        this.pool = pool;
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            StringBuilder response = new StringBuilder();
            Map<String, String> params = queryToMap(session.getQueryParameterString());
            session.parseBody(new HashMap<>());
            params.putAll(session.getParms());
            if (session.getUri().startsWith("/burst")) {
                response.append(handleBurstApiCall(session, params));
            } else if (session.getUri().startsWith("/api")) {
                response.append(handleApiCall(session, params));
            } else {
                return handleCall(session, params);
            }
            return NanoHTTPD.newFixedLengthResponse(response.toString());
        } catch (Throwable t) {
            t.printStackTrace();
            return NanoHTTPD.newFixedLengthResponse(t.getMessage());
        }
    }

    private String handleBurstApiCall(IHTTPSession session, Map<String, String> params) {
        if (session.getMethod().equals(Method.POST) && Objects.equals(params.get("requestType"), "submitNonce")) {
            Submission submission = new Submission(BurstAddress.fromEither(params.get("accountId")), params.get("nonce"));
            try {
                if (submission.getMiner() == null) {
                    throw new SubmissionException("Account ID not set");
                }
                if (submission.getNonce() == null) {
                    throw new SubmissionException("Nonce not set");
                }

                return gson.toJson(new NonceSubmissionResponse("success", pool.checkNewSubmission(submission).toString()));
            } catch (SubmissionException e) {
                return gson.toJson(new NonceSubmissionResponse(e.getMessage(), null));
            }
        } else if (Objects.equals(params.get("requestType"), "getMiningInfo")) {
            return gson.toJson(pool.getMiningInfo());
        } else {
            return "404 not found";
        }
    }

    private String handleApiCall(IHTTPSession session, Map<String, String> params) {
        return "404 not found";
    }

    private Response handleCall(IHTTPSession session, Map<String, String> params) throws IOException {
        String uri = session.getUri();
        if (Objects.equals(uri, "") || Objects.equals(uri, "/")) {
            return redirect("/index.html");
        }
        InputStream inputStream = getClass().getResourceAsStream("/html" + uri); // TODO vulnerabilities?
        if (inputStream == null) {
            return redirect("/404.html");
        }
        StringWriter stringWriter = new StringWriter();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            stringWriter.write(new String(buffer), 0, len);
        }
        return NanoHTTPD.newFixedLengthResponse(stringWriter.toString());
    }

    private Response redirect(String redirectTo) {
        Response r = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "");
        r.addHeader("Location", redirectTo);
        return r;
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
