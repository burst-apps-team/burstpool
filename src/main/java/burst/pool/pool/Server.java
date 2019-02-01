package burst.pool.pool;

import burst.kit.entity.BurstAddress;
import burst.kit.util.BurstKitUtils;
import burst.pool.miners.IMiner;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.Props;
import burst.pool.storage.persistent.StorageService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Server extends NanoHTTPD {
    private final StorageService storageService;
    private final PropertyService propertyService;
    private final Pool pool;
    private final Gson gson = BurstKitUtils.buildGson().create();

    public Server(StorageService storageService, PropertyService propertyService, Pool pool) {
        super(propertyService.getInt(Props.serverPort));
        this.storageService = storageService;
        this.propertyService = propertyService;
        this.pool = pool;
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            Map<String, String> params = queryToMap(session.getQueryParameterString());
            session.parseBody(new HashMap<>());
            params.putAll(session.getParms());
            if (session.getUri().startsWith("/burst")) {
                return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", handleBurstApiCall(session, params));
            } else if (session.getUri().startsWith("/api")) {
                return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", handleApiCall(session, params));
            } else {
                return handleCall(session, params);
            }
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
        if (session.getUri().startsWith("/api/getMiners")) {
            JsonArray jsonArray = new JsonArray();
            List<IMiner> miners = storageService.getMiners();
            miners.forEach(miner -> {
                JsonObject minerJson = new JsonObject();
                minerJson.addProperty("address", miner.getAddress().getID());
                minerJson.addProperty("pending", miner.getPending().toUnformattedString());
                minerJson.addProperty("estimatedCapacity", miner.getCapacity());
                minerJson.addProperty("nConf", miner.getNConf());
                minerJson.addProperty("share", miner.getShare());
                jsonArray.add(minerJson);
            });
            return jsonArray.toString();
        } else if (session.getUri().startsWith("/api/getConfig")) {
            JsonObject response = new JsonObject();
            response.addProperty(Props.nAvg.getName(), propertyService.getInt(Props.nAvg));
            response.addProperty(Props.nMin.getName(), propertyService.getInt(Props.nMin));
            response.addProperty(Props.maxDeadline.getName(), propertyService.getLong(Props.maxDeadline));
            response.addProperty(Props.processLag.getName(), propertyService.getInt(Props.processLag));
            response.addProperty(Props.feeRecipient.getName(), propertyService.getBurstAddress(Props.feeRecipient).getID());
            response.addProperty(Props.poolFeePercentage.getName(), propertyService.getFloat(Props.poolFeePercentage));
            response.addProperty(Props.winnerRewardPercentage.getName(), propertyService.getFloat(Props.winnerRewardPercentage));
            response.addProperty(Props.minimumPayout.getName(), propertyService.getFloat(Props.minimumPayout));
            response.addProperty(Props.minPayoutsPerTransaction.getName(), propertyService.getInt(Props.minPayoutsPerTransaction));
            response.addProperty(Props.transactionFee.getName(), propertyService.getFloat(Props.transactionFee));
            return response.toString();
        } else {
            return "404 not found";
        }
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
