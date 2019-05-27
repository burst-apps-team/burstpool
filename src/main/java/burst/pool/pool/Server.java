package burst.pool.pool;

import burst.kit.crypto.BurstCrypto;
import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstTimestamp;
import burst.kit.entity.BurstValue;
import burst.kit.entity.response.MiningInfo;
import burst.kit.entity.response.http.MiningInfoResponse;
import burst.kit.util.BurstKitUtils;
import burst.pool.Constants;
import burst.pool.entity.WonBlock;
import burst.pool.miners.Miner;
import burst.pool.miners.MinerTracker;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.Props;
import burst.pool.storage.persistent.StorageService;
import com.google.gson.*;
import fi.iki.elonen.NanoHTTPD;
import org.bouncycastle.util.encoders.DecoderException;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.URLConnection;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

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
            BigInteger nonce = null;
            try {
                nonce = new BigInteger(params.get("nonce"));
            } catch (Exception ignored) {}
            Submission submission = new Submission(BurstAddress.fromEither(params.get("accountId")), nonce);
            try {
                if (submission.getMiner() == null) {
                    throw new SubmissionException("Account ID not set");
                }
                if (submission.getNonce() == null) {
                    throw new SubmissionException("Nonce not set or invalid");
                }
                String userAgent = session.getHeaders().get("user-agent");
                if (userAgent == null) userAgent = "";
                return gson.toJson(new NonceSubmissionResponse("success", pool.checkNewSubmission(submission, userAgent)));
            } catch (SubmissionException e) {
                return gson.toJson(new NonceSubmissionResponse(e.getMessage(), null));
            }
        } else if (Objects.equals(params.get("requestType"), "getMiningInfo")) {
            MiningInfo miningInfo = pool.getMiningInfo();
            return gson.toJson(new MiningInfoResponse(Hex.toHexString(pool.getMiningInfo().getGenerationSignature()), miningInfo.getBaseTarget(), miningInfo.getHeight()));
        } else {
            return "404 not found";
        }
    }

    private String handleApiCall(IHTTPSession session, Map<String, String> params) {
        if (session.getUri().startsWith("/api/getMiners")) {
            JsonArray minersJson = new JsonArray();
            AtomicReference<Double> poolCapacity = new AtomicReference<>(0d);
            storageService.getMiners()
                    .stream()
                    .sorted(Comparator.comparing(Miner::getCapacity).reversed())
                    .forEach(miner -> {
                        poolCapacity.updateAndGet(v -> v + miner.getCapacity());
                        minersJson.add(minerToJson(miner));
                    });
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("miners", minersJson);
            jsonObject.addProperty("poolCapacity", poolCapacity.get());
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
            if (stringTokenizer.countTokens() != 4) {
                return new JsonPrimitive("Incorrect assignment").toString();
            }
            BurstAddress minerAddress = BurstAddress.fromEither(stringTokenizer.nextToken());
            BurstAddress poolAddress = BurstAddress.fromEither(stringTokenizer.nextToken());
            long currentTime = Long.parseLong(stringTokenizer.nextToken());
            BurstValue newMinimumPayout = BurstValue.fromPlanck(stringTokenizer.nextToken());
            if (minerAddress == null || storageService.getMiner(minerAddress) == null) {
                return new JsonPrimitive("Address not found").toString();
            }
            if (!Objects.equals(poolAddress, burstCrypto.getBurstAddressFromPassphrase(propertyService.getString(Props.passphrase)))) {
                return new JsonPrimitive("Address does not match pool").toString();
            }
            if (Instant.now().getEpochSecond() - currentTime > 60*60) { // 1 Hour
                return new JsonPrimitive("Assignment has expired").toString();
            }
            if (Float.parseFloat(newMinimumPayout.toUnformattedString()) < propertyService.getFloat(Props.minimumMinimumPayout)) { // TODO to bypass burstkit4j bug
                return new JsonPrimitive("New minimum payout is below the amount allowed by the pool").toString();
            }
            byte[] signatureBytes;
            try {
                signatureBytes = Hex.decode(signature);
            } catch (DecoderException e) {
                return new JsonPrimitive("Could not parse signature").toString();
            }
            if (signatureBytes.length != 64) {
                return new JsonPrimitive("Incorrect signature length").toString();
            }
            byte[] publicKeyBytes;
            try {
                publicKeyBytes = Hex.decode(publicKey);
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
        } else if (session.getUri().startsWith("/api/getTop10Miners")) {
            AtomicReference<Double> othersShare = new AtomicReference<>(1d);
            JsonArray topMiners = new JsonArray();
            storageService.getMiners().stream()
                    .sorted((m1, m2) -> Double.compare(m2.getShare(), m1.getShare())) // Reverse order - highest to lowest
                    .limit(10)
                    .forEach(miner -> {
                        topMiners.add(minerToJson(miner));
                        othersShare.updateAndGet(share -> share - miner.getShare());
                    });
            JsonObject response = new JsonObject();
            response.add("topMiners", topMiners);
            response.addProperty("othersShare", othersShare.get());
            return response.toString();
        } else if (session.getUri().startsWith("/api/getSetMinimumMessage")) {
            String currentTime = Long.toString(Instant.now().getEpochSecond());
            String address = BurstAddress.fromEither(params.get("address")).getFullAddress();
            String newPayout = BurstValue.fromBurst(params.get("newPayout")).toPlanck().toString();
            String poolAddress = burstCrypto.getBurstAddressFromPassphrase(propertyService.getString(Props.passphrase)).getFullAddress();
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("message", address + ":" + poolAddress + ":" + currentTime + ":" + newPayout);
            return jsonObject.toString();
        } else if (session.getUri().startsWith("/api/getWonBlocks")) {
            JsonArray wonBlocks = new JsonArray();
            storageService.getWonBlocks(100)
                    .forEach(wonBlock -> {
                        JsonObject wonBlockJson = new JsonObject();
                        wonBlockJson.addProperty("height", wonBlock.getBlockHeight());
                        wonBlockJson.addProperty("id", wonBlock.getBlockId().getID());
                        wonBlockJson.addProperty("generator", wonBlock.getGeneratorId().getFullAddress());
                        wonBlockJson.addProperty("reward", wonBlock.getFullReward().toFormattedString());
                        wonBlocks.add(wonBlockJson);
                    });
            JsonObject response = new JsonObject();
            response.add("wonBlocks", wonBlocks);
            return response.toString();
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
        if (!allowedFile || session.getUri().contains("../")) {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.FORBIDDEN, "text/html", "<h1>Access Forbidden</h1>");
        }
        InputStream inputStream = session.getUri().contains("favicon.ico") ? new FileInputStream(propertyService.getString(Props.siteIcon)) : getClass().getResourceAsStream("/html" + session.getUri());
        if (inputStream == null) {
            return redirect("/404.html");
        }
        StringWriter stringWriter = new StringWriter();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            stringWriter.write(new String(buffer), 0, len);
        }
        String response = stringWriter.toString() // TODO cache files
                // Minimizing
                .replace("    ", "")
                .replace(" + ", "+")
                .replace(" = ", "=")
                .replace(" == ", "==")
                .replace(" === ", "===")
                .replace(" (", "(")
                .replace(") ", ")")
                .replace(", ", ",")
                .replace("\r", "")
                .replace("\n", "")
                // Replace links TODO strip tags in links
                .replace("<<<PUBLICNODE>>>", propertyService.getString(Props.siteNodeAddress))
                .replace("<<<SOFTWARE>>>", propertyService.getString(Props.softwarePackagesAddress))
                .replace("<<<DISCORD>>>", propertyService.getString(Props.siteDiscordLink));
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
        minerJson.addProperty("minimumPayout", miner.getMinimumPayout().toFormattedString());
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
