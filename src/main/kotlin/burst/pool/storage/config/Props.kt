package burst.pool.storage.config

import burst.kit.entity.BurstAddress
import kotlin.math.pow

object Props {
    val serverPort = Prop("serverPort", 80) // Must be > 0, < 2^16
    val nodeAddresses = Prop("nodeAddresses", emptyList<String>()) // Must be non-empty
    val poolName = Prop("poolName", "")
    val passphrase = Prop("passphrase", "") // Must be non-empty
    val dbUrl = Prop("dbUrl", "")
    val dbUsername = Prop("dbUsername", "")
    val dbPassword = Prop("dbPassword", "")
    val nAvg = Prop("nAvg", 360) // Must be ?
    val nMin = Prop("nMin", 1) // Must be ?
    val tMin = Prop("tMin", 20) // Must be ?
    val maxDeadline = Prop("maxDeadline", Long.MAX_VALUE) // Must be > 0
    val processLag = Prop("processLag", 10) // Must be > 0
    val feeRecipient = Prop<BurstAddress?>("feeRecipient", null) // Must be non null
    val poolFeePercentage = Prop("poolFeePercentage", 0f) // Must be 0-1
    val winnerRewardPercentage = Prop("winnerRewardPercentage", 0f) // Must be 0-1
    val defaultMinimumPayout = Prop("defaultMinimumPayout", 100f) // Must be > 0
    val minimumMinimumPayout = Prop("minimumMinimumPayout", 100f) // Must be > 0
    val minPayoutsPerTransaction = Prop("minPayoutsPerTransaction", 10) // Must be 2-64
    val transactionFee = Prop("transactionFee", 1f) // Must be > 0.00735
    val payoutRetryCount = Prop("payoutRetryCount", 3)
    val submitNonceRetryCount = Prop("submitNonceRetryCount", 3)
    val siteTitle = Prop("site.title", "Burst Pool")
    val siteIconIco = Prop("site.icon.ico", "icon.ico")
    val siteIconPng = Prop("site.icon.png", "icon.png")
    val siteNodeAddress = Prop("site.nodeAddress", "https://wallet.burst-alliance.org:8125/")
    val softwarePackagesAddress = Prop("site.softwarePackagesAddress", "https://github.com/burst-apps-team")
    val siteDiscordLink = Prop("site.discord", "https://discord.gg/ms6eagX")
    fun validateProperties(propertyService: PropertyService) {
        val serverPort = propertyService.get(serverPort)
        require(!(serverPort <= 0 || serverPort >= 2.0.pow(16.0))) { "Illegal server port: $serverPort (Must be 0-2^16 exclusive)" }
        val nodeAddresses = propertyService.get(nodeAddresses)
        require(nodeAddresses.isNotEmpty()) { "Illegal node addresses (empty)" }
        val poolName = propertyService.get(poolName)
        require(poolName.isNotBlank()) { "Illegal pool name (empty)" }
        val passphrase = propertyService.get(passphrase)
        require(passphrase.isNotBlank()) { "Illegal passphrase (empty)" }
        val nAvg = propertyService.get(nAvg)
        // Todo
        val nMin = propertyService.get(nMin)
        // Todo
        val tMin = propertyService.get(tMin)
        // Todo
        val maxDeadline = propertyService.get(maxDeadline)
        require(maxDeadline > 0) { "Illegal maxDeadline: $maxDeadline (Must be > 0)" }
        val processLag = propertyService.get(processLag)
        require(processLag >= 0) { "Illegal processLag: $processLag (Must be > 0)" }
        val feeRecipient = propertyService.get(feeRecipient) ?: throw IllegalArgumentException("Illegal feeRecipient (not set)")
        val poolFeePercentage = propertyService.get(poolFeePercentage)
        require(!(poolFeePercentage < 0f || poolFeePercentage > 1f)) { "Illegal poolFeePercentage: $poolFeePercentage (Must be 0-1)" }
        val winnerRewardPercentage = propertyService.get(winnerRewardPercentage)
        require(!(winnerRewardPercentage < 0f || winnerRewardPercentage > 1f)) { "Illegal winnerRewardPercentage: $winnerRewardPercentage (Must be 0-1)" }
        val minimumMinimumPayout = propertyService.get(minimumMinimumPayout)
        require(minimumMinimumPayout > 0) { "Illegal minimumMinimumPayout: $processLag (Must be > 0)" }
        val defaultMinimumPayout = propertyService.get(defaultMinimumPayout)
        require(defaultMinimumPayout >= minimumMinimumPayout) { "Illegal defaultMinimumPayout: $processLag (Must be > minimumMinimumPayout)" }
        val minPayoutsPerTransaction = propertyService.get(minPayoutsPerTransaction)
        require(!(minPayoutsPerTransaction < 2 || minPayoutsPerTransaction > 64)) { "Illegal minPayoutsPerTransaction: $minPayoutsPerTransaction (Must be 2-64)" }
        val transactionFee = propertyService.get(transactionFee)
        require(transactionFee >= 0.00735f) { "Illegal minPayoutsPerTransaction: $minPayoutsPerTransaction (Must be > 0.00735)" }
    }
}
