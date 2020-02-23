package burst.pool.pool

import burst.kit.crypto.BurstCrypto
import burst.kit.entity.BurstAddress
import burst.kit.entity.BurstValue
import burst.kit.entity.response.http.MiningInfoResponse
import burst.kit.util.BurstKitUtils
import burst.pool.Constants
import burst.pool.entity.WonBlock
import burst.pool.miners.Miner
import burst.pool.miners.MinerTracker
import burst.pool.storage.config.PropertyService
import burst.pool.storage.config.Props
import burst.pool.storage.persistent.StorageService
import com.google.gson.*
import fi.iki.elonen.NanoHTTPD
import org.ehcache.Cache
import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.CacheManagerBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.StringWriter
import java.math.BigInteger
import java.net.SocketException
import java.net.URLConnection
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

class Server(private val storageService: StorageService, private val propertyService: PropertyService, private val pool: Pool, private val minerTracker: MinerTracker) : NanoHTTPD(propertyService.get(Props.serverPort)) {
    private val gson = BurstKitUtils.buildGson().create()
    private val burstCrypto = BurstCrypto.getInstance()
    private val fileCache: Cache<String, String> = CacheManagerBuilder.newCacheManagerBuilder()
            .withCache("file", CacheConfigurationBuilder.newCacheConfigurationBuilder(String::class.java, String::class.java, ResourcePoolsBuilder.heap(1024 * 1024.toLong())))
            .build(true)
            .getCache("file", String::class.java, String::class.java)
    private val currentHeight: Long
        get() {
            val miningInfo = pool.getMiningInfo() ?: return 0
            return miningInfo.height
        }

    override fun serve(session: IHTTPSession): Response {
        return try {
            val params = queryToMap(session.queryParameterString)
            session.parseBody(HashMap())
            params.putAll(session.parms)
            if (session.uri.startsWith("/burst")) {
                newFixedLengthResponse(Response.Status.OK, "application/json", handleBurstApiCall(session, params))
            } else if (session.uri.startsWith("/api")) {
                newFixedLengthResponse(Response.Status.OK, "application/json", handleApiCall(session, params))
            } else {
                handleCall(session, params)
            }
        } catch (e: SocketException) {
            logger.warn("SocketException for {}", session.remoteIpAddress)
            newFixedLengthResponse(e.message)
        } catch (e: Exception) {
            logger.warn("Error getting response", e)
            newFixedLengthResponse(e.message)
        }
    }

    private fun handleBurstApiCall(session: IHTTPSession, params: Map<String, String>): String {
        return if (session.method == Method.POST && params["requestType"] == "submitNonce") {
            try {
                val nonce = try {
                    BigInteger(params["nonce"] ?: throw SubmissionException("Missing Nonce"))
                } catch (e: NumberFormatException) {
                    throw SubmissionException("Malformed Nonce")
                }
                val accountId = BurstAddress.fromEither(params["accountId"] ?: throw SubmissionException("Missing Account ID")) ?: throw SubmissionException("Malformed Account ID")
                val submission = Submission(accountId, nonce)

                val heightString = params["blockheight"]
                if (!heightString.isNullOrBlank()) {
                    val blockHeight = try {
                        java.lang.Long.parseUnsignedLong(heightString)
                    } catch (e: NumberFormatException) {
                        throw SubmissionException("Malformed Block Height")
                    }
                    val miningInfo = pool.getMiningInfo() ?: throw SubmissionException("Cannot submit, new round starting")
                    if (blockHeight != miningInfo.height) {
                        throw SubmissionException("Given block height does not match current round height")
                    }
                }
                var userAgent = session.headers["user-agent"]
                if (userAgent == null) userAgent = ""
                gson.toJson(NonceSubmissionResponse("success", pool.checkNewSubmission(submission, userAgent)))
            } catch (e: SubmissionException) {
                gson.toJson(NonceSubmissionResponse(e.message!!, null))
            }
        } else if (params["requestType"] == "getMiningInfo") {
            val miningInfo = pool.getMiningInfo() ?: return gson.toJson(JsonNull.INSTANCE)
            // TODO remove dependency on internal burstkit4j class
            val miningInfoJson = gson.toJsonTree(MiningInfoResponse(burstCrypto.toHexString(miningInfo.generationSignature), miningInfo.baseTarget, miningInfo.height)).asJsonObject
            miningInfoJson.addProperty("targetDeadline", propertyService.get(Props.maxDeadline))
            miningInfoJson.toString()
        } else {
            "404 not found"
        }
    }

    private fun handleApiCall(session: IHTTPSession, params: Map<String, String>): String {
        return when {
            session.uri.startsWith("/api/getMiners") -> {
                val minersJson = JsonArray()
                val poolCapacity = AtomicReference(0.0)
                storageService.minersFiltered
                        .stream()
                        .sorted(Comparator.comparing(Miner::capacity).reversed())
                        .forEach { miner: Miner ->
                            poolCapacity.updateAndGet { v: Double -> v + miner.capacity }
                            minersJson.add(minerToJson(miner))
                        }
                val jsonObject = JsonObject()
                jsonObject.add("miners", minersJson)
                jsonObject.addProperty("poolCapacity", poolCapacity.get())
                jsonObject.toString()
            }
            session.uri.startsWith("/api/getMiner/") -> {
                val minerAddress = BurstAddress.fromEither(session.uri.substring(14))
                minerToJson(storageService.getMiner(minerAddress)).toString()
            }
            session.uri.startsWith("/api/getConfig") -> {
                val response = JsonObject()
                response.addProperty("version", Constants.VERSION)
                response.addProperty(Props.poolName.name, propertyService.get(Props.poolName))
                response.addProperty("poolAccount", pool.account.id)
                response.addProperty("poolAccountRS", pool.account.fullAddress)
                response.addProperty(Props.nAvg.name, propertyService.get(Props.nAvg))
                response.addProperty(Props.nMin.name, propertyService.get(Props.nMin))
                response.addProperty(Props.maxDeadline.name, propertyService.get(Props.maxDeadline))
                response.addProperty(Props.processLag.name, propertyService.get(Props.processLag))
                response.addProperty(Props.feeRecipient.name, propertyService.get(Props.feeRecipient)!!.getID())
                response.addProperty(Props.feeRecipient.name + "RS", propertyService.get(Props.feeRecipient)!!.fullAddress)
                response.addProperty(Props.poolFeePercentage.name, propertyService.get(Props.poolFeePercentage))
                response.addProperty(Props.winnerRewardPercentage.name, propertyService.get(Props.winnerRewardPercentage))
                response.addProperty(Props.defaultMinimumPayout.name, propertyService.get(Props.defaultMinimumPayout))
                response.addProperty(Props.minimumMinimumPayout.name, propertyService.get(Props.minimumMinimumPayout))
                response.addProperty(Props.minPayoutsPerTransaction.name, propertyService.get(Props.minPayoutsPerTransaction))
                response.addProperty(Props.transactionFee.name, propertyService.get(Props.transactionFee))
                response.toString()
            }
            session.uri.startsWith("/api/getCurrentRound") -> {
                pool.getCurrentRoundInfo(gson).toString()
            }
            session.uri.startsWith("/api/setMinerMinimumPayout") -> { // TODO the flow of this is horrible
                if (session.method != Method.POST) {
                    return JsonPrimitive("This endpoint requires POST").toString()
                }
                val assignment = params["assignment"]
                val publicKey = params["publicKey"]
                val signature = params["signature"]
                if (assignment == null || signature == null || publicKey == null || assignment == "" || publicKey == "" || signature == "") {
                    return JsonPrimitive("Missing parameter").toString()
                }
                val stringTokenizer = StringTokenizer(assignment, ":")
                if (stringTokenizer.countTokens() != 4) {
                    return JsonPrimitive("Incorrect assignment").toString()
                }
                val minerAddress = BurstAddress.fromEither(stringTokenizer.nextToken())
                val poolAddress = BurstAddress.fromEither(stringTokenizer.nextToken())
                val currentTime = stringTokenizer.nextToken().toLong()
                val newMinimumPayout = BurstValue.fromPlanck(stringTokenizer.nextToken())
                if (minerAddress == null || storageService.getMiner(minerAddress) == null) {
                    return JsonPrimitive("Address not found").toString()
                }
                if (poolAddress != burstCrypto.getBurstAddressFromPassphrase(propertyService.get(Props.passphrase))) {
                    return JsonPrimitive("Address does not match pool").toString()
                }
                if (Instant.now().epochSecond - currentTime > 60 * 60) { // 1 Hour
                    return JsonPrimitive("Assignment has expired").toString()
                }
                if (newMinimumPayout < BurstValue.fromBurst(propertyService.get(Props.minimumMinimumPayout).toDouble())) {
                    return JsonPrimitive("New minimum payout is below the amount allowed by the pool").toString()
                }
                val signatureBytes: ByteArray
                signatureBytes = try {
                    burstCrypto.parseHexString(signature)
                } catch (e: Exception) {
                    return JsonPrimitive("Could not parse signature").toString()
                }
                if (signatureBytes.size != 64) {
                    return JsonPrimitive("Incorrect signature length").toString()
                }
                val publicKeyBytes: ByteArray
                publicKeyBytes = try {
                    burstCrypto.parseHexString(publicKey)
                } catch (e: Exception) {
                    return JsonPrimitive("Could not parse publicKey").toString()
                }
                if (publicKeyBytes.size != 32) {
                    return JsonPrimitive("Incorrect public key length").toString()
                }
                if (burstCrypto.getBurstAddressFromPublic(publicKeyBytes) != minerAddress) { // TODO ideally would validate with node to avoid collisions
                    return JsonPrimitive("Public key did not match miner's address.").toString()
                }
                if (!burstCrypto.verify(signatureBytes, assignment.toByteArray(), publicKeyBytes, true)) {
                    return JsonPrimitive("Invalid signature").toString()
                }
                minerTracker.setMinerMinimumPayout(storageService, minerAddress, newMinimumPayout)
                JsonPrimitive("Success").toString()
            }
            session.uri.startsWith("/api/getTopMiners") -> {
                val othersShare = AtomicReference(1.0)
                val topMiners = JsonArray()
                storageService.minersFiltered.stream()
                        .sorted { m1: Miner, m2: Miner -> java.lang.Double.compare(m2.share, m1.share) } // Reverse order - highest to lowest
                        .limit(10)
                        .forEach { miner: Miner ->
                            topMiners.add(minerToJson(miner))
                            othersShare.updateAndGet { share: Double -> share - miner.share }
                        }
                val response = JsonObject()
                response.add("topMiners", topMiners)
                response.addProperty("othersShare", othersShare.get())
                response.toString()
            }
            session.uri.startsWith("/api/getSetMinimumMessage") -> {
                val currentTime = java.lang.Long.toString(Instant.now().epochSecond)
                val address = BurstAddress.fromEither(params["address"]).fullAddress
                val newPayout = BurstValue.fromBurst(params["newPayout"]).toPlanck().toString()
                val poolAddress = burstCrypto.getBurstAddressFromPassphrase(propertyService.get(Props.passphrase)).fullAddress
                val jsonObject = JsonObject()
                jsonObject.addProperty("message", "$address:$poolAddress:$currentTime:$newPayout")
                jsonObject.toString()
            }
            session.uri.startsWith("/api/getWonBlocks") -> {
                val wonBlocks = JsonArray()
                storageService.getWonBlocks(100)
                        .forEach(Consumer { wonBlock: WonBlock ->
                            val wonBlockJson = JsonObject()
                            wonBlockJson.addProperty("height", wonBlock.blockHeight)
                            wonBlockJson.addProperty("id", wonBlock.blockId.id)
                            wonBlockJson.addProperty("generator", wonBlock.generatorId.id)
                            wonBlockJson.addProperty("generatorRS", wonBlock.generatorId.fullAddress)
                            wonBlockJson.addProperty("reward", wonBlock.fullReward.toFormattedString())
                            wonBlocks.add(wonBlockJson)
                        })
                val response = JsonObject()
                response.add("wonBlocks", wonBlocks)
                response.toString()
            }
            else -> {
                "null"
            }
        }
    }

    @Throws(IOException::class)
    private fun handleCall(session: IHTTPSession, params: Map<String, String>): Response {
        if (session.uri == "" || session.uri == "/") {
            return redirect("/index.html")
        }
        var allowedFile = false
        for (extension in allowedFileExtensions) {
            if (session.uri.endsWith(extension)) allowedFile = true
        }
        if (!allowedFile || session.uri.contains("../")) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/html", "<h1>Access Forbidden</h1>")
        }
        if (fileCache.containsKey(session.uri)) {
            return newFixedLengthResponse(Response.Status.OK, URLConnection.guessContentTypeFromName(session.uri), fileCache[session.uri])
        }
        val inputStream = when {
            session.uri.contains("favicon.ico") -> {
                FileInputStream(propertyService.get(Props.siteIconIco))
            }
            session.uri == "/img/poolIcon.png" -> {
                FileInputStream(propertyService.get(Props.siteIconPng))
            }
            else -> {
                javaClass.getResourceAsStream("/html" + session.uri)
            }
        } ?: return redirect("/404.html")
        if (session.uri.contains(".png") || session.uri.contains(".ico")) {
            return newChunkedResponse(Response.Status.OK, if (session.uri.contains(".ico")) "image/x-icon" else "image/png", inputStream)
        }
        val stringWriter = StringWriter(inputStream.available())
        val buffer = ByteArray(1024 * 1024)
        var len: Int
        while (inputStream.read(buffer).also { len = it } != -1) {
            stringWriter.write(String(buffer, StandardCharsets.UTF_8), 0, len)
        }
        var response = stringWriter.toString()
        var minimize = true
        if (session.uri.contains(".png") || session.uri.contains(".ico")) minimize = false
        if (minimize) {
            response = response // Minimizing
                    .replace("    ", "")
                    .replace(" + ", "+")
                    .replace(" = ", "=")
                    .replace(" == ", "==")
                    .replace(" === ", "===")
                    .replace("\r", "")
                    .replace("\n", "") /*
                    .replace(" (", "(")
                    .replace(") ", ")")
                    .replace(", ", ",") TODO this minimization is messing up strings */
// Replace links TODO strip tags in links
                    .replace("{TITLE}", propertyService.get(Props.siteTitle))
                    .replace("{PUBLICNODE}", propertyService.get(Props.siteNodeAddress))
                    .replace("{SOFTWARE}", propertyService.get(Props.softwarePackagesAddress))
                    .replace("{DISCORD}", propertyService.get(Props.siteDiscordLink))
        }
        fileCache.put(session.uri, response)
        return newFixedLengthResponse(Response.Status.OK, URLConnection.guessContentTypeFromName(session.uri), response)
    }

    private fun redirect(redirectTo: String): Response {
        val r = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "")
        r.addHeader("Location", redirectTo)
        return r
    }

    private fun minerToJson(miner: Miner?): JsonElement {
        if (miner == null) return JsonNull.INSTANCE
        val minerJson = JsonObject()
        minerJson.addProperty("address", miner.address.id)
        minerJson.addProperty("addressRS", miner.address.fullAddress)
        minerJson.addProperty("pendingBalance", miner.pending.toFormattedString())
        minerJson.addProperty("estimatedCapacity", miner.capacity)
        minerJson.addProperty("nConf", miner.nConf)
        minerJson.addProperty("share", miner.share)
        minerJson.addProperty("minimumPayout", miner.minimumPayout.toFormattedString())
        val bestDeadline = miner.getBestDeadline(currentHeight)
        if (bestDeadline != null) {
            minerJson.addProperty("currentRoundBestDeadline", miner.getBestDeadline(currentHeight).toString())
        }
        if (miner.name != "") {
            minerJson.addProperty("name", miner.name)
        }
        if (miner.userAgent != "") {
            minerJson.addProperty("userAgent", miner.userAgent)
        }
        return minerJson
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Server::class.java)
        private val allowedFileExtensions = arrayOf(".html", ".css", ".js", ".png", ".ico")
        private fun queryToMap(query: String?): MutableMap<String, String> {
            val result: MutableMap<String, String> = HashMap()
            if (query == null) return result
            for (param in query.split("&").toTypedArray()) {
                val entry = param.split("=").toTypedArray()
                if (entry.size > 1) {
                    result[entry[0]] = entry[1]
                } else {
                    result[entry[0]] = ""
                }
            }
            return result
        }
    }
}
