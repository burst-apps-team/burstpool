package burst.pool.pool;

import burst.kit.burst.BurstCrypto;
import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;
import burst.kit.entity.HexStringByteArray;
import burst.kit.util.BurstKitUtils;
import burst.pool.Constants;
import burst.pool.miners.Miner;
import burst.pool.miners.MinerTracker;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.Props;
import burst.pool.storage.persistent.StorageService;
import com.google.gson.*;
import fi.iki.elonen.NanoHTTPD;
import org.bouncycastle.util.encoders.DecoderException;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URLConnection;
import java.util.*;

public class Server extends NanoHTTPD {
    private static final String[] allowedFileExtensions = new String[]{".html", ".css", ".js", ".ico"};

    private final StorageService storageService;
    private final PropertyService propertyService;
    private final Pool pool;
    private final MinerTracker minerTracker;
    private final Gson gson = BurstKitUtils.buildGson().create();
    private final BurstCrypto burstCrypto = BurstCrypto.getInstance();

    public Server(StorageService storageService, PropertyService propertyService, Pool pool, MinerTracker minerTracker) {
        super(propertyService.getInt(Props.serverPort));
        this.storageService = storageService;
        this.propertyService = propertyService;
        this.pool = pool;
        this.minerTracker = minerTracker;
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
            LoggerFactory.getLogger(Server.class).error("Error getting response", t);
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
                String userAgent = session.getHeaders().get("user-agent");
                if (userAgent == null) userAgent = "";
                return gson.toJson(new NonceSubmissionResponse("success", pool.checkNewSubmission(submission, userAgent)));
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
            JsonArray minersJson = new JsonArray();
            List<Miner> miners = storageService.getMiners();
            miners.forEach(miner -> minersJson.add(minerToJson(miner)));
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("miners", minersJson);
            double poolCapacity = 0;
            for (Miner miner : storageService.getMiners()) {
                poolCapacity += miner.getCapacity();
            }
            jsonObject.addProperty("poolCapacity", poolCapacity);
            return jsonObject.toString();
        } else if (session.getUri().startsWith("/api/getMiner/")) {
            BurstAddress minerAddress = BurstAddress.fromEither(session.getUri().substring(14));
            return minerToJson(storageService.getMiner(minerAddress)).toString();
        } else if (session.getUri().startsWith("/api/getConfig")) {
            JsonObject response = new JsonObject();
            response.addProperty("version", Constants.VERSION);
            response.addProperty(Props.poolName.getName(), propertyService.getString(Props.poolName));
            response.addProperty("poolAccount", pool.getAccount().getID());
            response.addProperty("poolAccountRS", pool.getAccount().getFullAddress());
            response.addProperty(Props.nAvg.getName(), propertyService.getInt(Props.nAvg));
            response.addProperty(Props.nMin.getName(), propertyService.getInt(Props.nMin));
            response.addProperty(Props.maxDeadline.getName(), propertyService.getLong(Props.maxDeadline));
            response.addProperty(Props.processLag.getName(), propertyService.getInt(Props.processLag));
            response.addProperty(Props.feeRecipient.getName(), propertyService.getBurstAddress(Props.feeRecipient).getID());
            response.addProperty(Props.feeRecipient.getName() + "RS", propertyService.getBurstAddress(Props.feeRecipient).getFullAddress());
            response.addProperty(Props.poolFeePercentage.getName(), propertyService.getFloat(Props.poolFeePercentage));
            response.addProperty(Props.winnerRewardPercentage.getName(), propertyService.getFloat(Props.winnerRewardPercentage));
            response.addProperty(Props.defaultMinimumPayout.getName(), propertyService.getFloat(Props.defaultMinimumPayout));
            response.addProperty(Props.minimumMinimumPayout.getName(), propertyService.getFloat(Props.minimumMinimumPayout));
            response.addProperty(Props.minPayoutsPerTransaction.getName(), propertyService.getInt(Props.minPayoutsPerTransaction));
            response.addProperty(Props.transactionFee.getName(), propertyService.getFloat(Props.transactionFee));
            return response.toString();
        } else if (session.getUri().startsWith("/api/getCurrentRound")) {
            return pool.getCurrentRoundInfo(gson).toString();
        } else if (session.getUri().startsWith("/api/setMinerMinimumPayout")) { // TODO the flow of this is horrible
            if (session.getMethod() != Method.POST) {
                return new JsonPrimitive("This endpoint requires POST").toString();
            }
            String assignment = params.get("assignment");
            String publicKey = params.get("publicKey");
            String signature = params.get("signature");
            if (assignment == null || signature == null || publicKey == null || Objects.equals(assignment, "") || Objects.equals(publicKey, "") || Objects.equals(signature, "")) {
                return new JsonPrimitive("Missing parameter").toString();
            }
            StringTokenizer stringTokenizer = new StringTokenizer(assignment, ":");
            if (stringTokenizer.countTokens() != 2) {
                return new JsonPrimitive("Incorrect assignment").toString();
            }
            BurstAddress minerAddress = BurstAddress.fromEither(stringTokenizer.nextToken());
            BurstValue newMinimumPayout = BurstValue.fromBurst(stringTokenizer.nextToken());
            if (minerAddress == null || storageService.getMiner(minerAddress) == null) {
                return new JsonPrimitive("Address not found").toString();
            }
            if (newMinimumPayout.floatValue() < propertyService.getFloat(Props.minimumMinimumPayout)) {
                return new JsonPrimitive("New minimum payout is below the amount allowed by the pool").toString();
            }
            byte[] signatureBytes;
            try {
                signatureBytes = new HexStringByteArray(signature).getBytes();
            } catch (DecoderException e) {
                return new JsonPrimitive("Could not parse signature").toString();
            }
            if (signatureBytes.length != 64) {
                return new JsonPrimitive("Incorrect signature length").toString();
            }
            byte[] publicKeyBytes;
            try {
                publicKeyBytes = new HexStringByteArray(publicKey).getBytes();
            } catch (DecoderException e) {
                return new JsonPrimitive("Could not parse publicKey").toString();
            }
            if (publicKeyBytes.length != 32) {
                return new JsonPrimitive("Incorrect public key length").toString();
            }
            if (!Objects.equals(burstCrypto.getBurstAddressFromPublic(publicKeyBytes), minerAddress)) { // TODO ideally would validate with node to avoid collisions
                return new JsonPrimitive("Public key did not match miner's address.").toString();
            }
            if (!BurstCrypto.getInstance().verify(signatureBytes, assignment.getBytes(), publicKeyBytes, true)) {
                return new JsonPrimitive("Invalid signature").toString();
            }
            minerTracker.setMinerMinimumPayout(storageService, minerAddress, newMinimumPayout);
            return new JsonPrimitive("Success").toString();
        } else {
            return "null";
        }
    }

    private Response handleCall(IHTTPSession session, Map<String, String> params) throws IOException {
        if (Objects.equals(session.getUri(), "") || Objects.equals(session.getUri(), "/")) {
            return redirect("/index.html");
        }
        boolean allowedFile = false;
        for (String extension : allowedFileExtensions) {
            if (session.getUri().endsWith(extension)) allowedFile = true;
        }
        if (!allowedFile) {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.FORBIDDEN, "text/html", "<h1>Access Forbidden</h1>");
        }
        InputStream inputStream = getClass().getResourceAsStream("/html" + session.getUri());
        if (inputStream == null) {
            return redirect("/404.html");
        }
        StringWriter stringWriter = new StringWriter();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            stringWriter.write(new String(buffer), 0, len);
        }
        String response = stringWriter.toString().replace("\n", "").replace("    ", "");
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, URLConnection.guessContentTypeFromName(session.getUri()), response);
    }

    private Response redirect(String redirectTo) {
        Response r = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "");
        r.addHeader("Location", redirectTo);
        return r;
    }

    private JsonElement minerToJson(Miner miner) {
        if (miner == null) return JsonNull.INSTANCE;
        JsonObject minerJson = new JsonObject();
        minerJson.addProperty("address", miner.getAddress().getID());
        minerJson.addProperty("addressRS", miner.getAddress().getFullAddress());
        minerJson.addProperty("pendingBalance", miner.getPending().toFormattedString());
        minerJson.addProperty("estimatedCapacity", miner.getCapacity());
        minerJson.addProperty("nConf", miner.getNConf());
        minerJson.addProperty("share", miner.getShare());
        minerJson.addProperty("minimumPayout", miner.getMinimumPayout().toUnformattedString());
        if (!Objects.equals(miner.getName(), "")) {
            minerJson.addProperty("name", miner.getName());
        }
        if (!Objects.equals(miner.getUserAgent(), "")) {
            minerJson.addProperty("userAgent", miner.getUserAgent());
        }
        return minerJson;
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
