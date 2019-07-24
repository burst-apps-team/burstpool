import org.w3c.dom.HTMLInputElement
import kotlin.browser.document
import kotlin.browser.window
import kotlin.js.Date
import kotlin.math.roundToLong

object PoolClient {
    private var miners: Array<Miner> = emptyArray()
    private var maxSubmissions = "Unknown"
    private var chart: Chart? = null
    private var roundStart: Int = 0

    private const val noneFoundYet = "None found yet!"
    private const val loadingText =  "Loading..."
    private const val minerNotFound = "Miner not found"

    fun init() {
        getPoolInfo()
        WebUtil.schedule({ updateRoundElapsed() }, 1000, false)
        WebUtil.schedule({ getCurrentRound() }, 10000)
        WebUtil.schedule({ getMiners() }, 60000) // TODO only refresh this when we detect that we forged a block
        WebUtil.schedule({ getTopMiners() }, 60000)
        WebUtil.addModalShowListener("minerInfoModal") { prepareMinerInfo() }
        WebUtil.addModalShowListener("setMinimumPayoutModal") {
            document.getElementById("setMinimumAddress")?.value = document.getElementById("minerAddress")?.textContent?.escapeHtml() ?: ""
            document.getElementById("setMinimumResult")?.hide()
        }
        val addressInput = document.getElementById("addressInput")
        if (addressInput is HTMLInputElement) {
            addressInput.onkeyup = { event ->
                if (event.keyCode == 13) {
                    event.preventDefault()
                    document.getElementById("getMinerButton")?.click()
                }
            }
        }
    }

    fun onPageLoaded() {
        document.getElementById("addressInput")?.value = WebUtil.getCookie("getMinerLastValue") ?: ""
    }

    private fun formatMinerName(providedRS: String, id: String, providedName: String?, includeLink: Boolean): String {
        var name: String? = null
        if (providedName == null) {
            miners.forEach { if (it.address == id || it.addressRS == providedRS) {
                name = it.name
            }
            }
        } else {
            name = providedName
        }
        name = name?.escapeHtml()
        var rs = providedRS.escapeHtml()
        if (includeLink) {
            rs = "<a href=\"" + Util.getAccountExplorerLink(id) + "\">" + rs + "</a>"
        }
        return if (name == null || name?.isEmpty() == true) rs else "$rs ($name)"
    }

    private fun getPoolInfo() {
        WebUtil.fetchJson<PoolConfig>("/api/getConfig").then { poolConfig ->
            if (poolConfig == null) {
                console.error("Null pool config")
                return@then
            }
            this.maxSubmissions = (poolConfig.nAvg + poolConfig.processLag).toString()
            document.getElementById("poolName")?.textContent = poolConfig.poolName
            document.getElementById("poolAccount")?.innerHTML = formatMinerName(poolConfig.poolAccountRS, poolConfig.poolAccount, poolConfig.poolAccount, true)
            document.getElementById("nAvg")?.textContent = poolConfig.nAvg.toString()
            document.getElementById("nMin")?.textContent = poolConfig.nMin.toString()
            document.getElementById("maxDeadline")?.textContent = poolConfig.maxDeadline.toString()
            document.getElementById("processLag")?.textContent = poolConfig.processLag.toString() + " Blocks"
            document.getElementById("feeRecipient")?.innerHTML = formatMinerName(poolConfig.feeRecipientRS, poolConfig.feeRecipient, null, true)
            document.getElementById("poolFee")?.textContent = (poolConfig.poolFeePercentage * 100).round(3).toString()
            document.getElementById("winnerReward")?.textContent = (poolConfig.winnerRewardPercentage * 100).round(3).toString()
            document.getElementById("minimumPayout")?.textContent = poolConfig.defaultMinimumPayout + " BURST"
            document.getElementById("minPayoutsAtOnce")?.textContent = poolConfig.minPayoutsPerTransaction.toString()
            document.getElementById("payoutTxFee")?.textContent = poolConfig.transactionFee.round(3).toString() + " BURST"
            document.getElementById("poolVersion")?.textContent = poolConfig.version
        }
    }

    private fun getCurrentRound() {
        WebUtil.fetchJson<Round>("/api/getCurrentRound").then { currentRound ->
            if (currentRound == null) {
                console.error("Null current round")
                return@then
            }
            this.roundStart = currentRound.roundStart
            val miningInfo = currentRound.miningInfo
            if (miningInfo != null) {
                document.getElementById("blockHeight")?.textContent = miningInfo.height.toString()
                document.getElementById("netDiff")?.textContent = Util.formatBaseTarget(miningInfo.baseTarget)
            }
            val bestDeadline = currentRound.bestDeadline
            if (bestDeadline != null) {
                document.getElementById("bestDeadline")?.textContent = Util.formatTime(bestDeadline.deadline.toLong())
                document.getElementById("bestMiner")?.textContent = formatMinerName(bestDeadline.minerRS, bestDeadline.miner, null, true)
                document.getElementById("bestNonce")?.textContent = bestDeadline.nonce
            } else {
                document.getElementById("bestDeadline")?.textContent = noneFoundYet
                document.getElementById("bestMiner")?.textContent = noneFoundYet
                document.getElementById("bestNonce")?.textContent = noneFoundYet
            }
        }
    }

    private fun updateRoundElapsed() {
        document.getElementById("currentRoundElapsed")?.textContent = Util.formatTime((Date().getTime() / 1000).roundToLong())
    }

    private fun getMiners() {
        WebUtil.fetchJson<Miners>("/api/getMiners").then { response ->
            if (response == null) {
                console.error("Null miners")
                return@then
            }
            val table = document.getElementById("miners")
            if (table == null) {
                console.error("Could not find table")
                return@then
            }
            table.innerHTML = "<tr><th>Miner</th><th>Current Deadline</th><th>Pending Balance</th><th>Effective Capacity</th><th>Confirmed Deadlines</th><th>Share</th><th>Software</th></tr>"
            for (i in 0 until response.miners.size) {
                val miner = response.miners[i]
                val currentRoundDeadline = Util.formatTime(miner.currentRoundBestDeadline)
                val minerAddress = formatMinerName(miner.addressRS, miner.address, miner.name, true)
                val userAgent = (miner.userAgent ?: "Unknown").escapeHtml()
                table.innerHTML += "<tr><td>"+minerAddress+"</td><td>"+currentRoundDeadline+"</td><td>"+miner.pendingBalance+"</td><td>"+Util.formatCapacity(miner.estimatedCapacity)+" TB</td><td>"+miner.nConf+" / " + maxSubmissions + "</td><td>"+(miner.share*100).round(3).toString()+"%</td><td>"+userAgent+"</td></tr>"
            }
            document.getElementById("minerCount")?.textContent = response.miners.size.toString()
            document.getElementById("poolCapacity")?.textContent = Util.formatCapacity(response.poolCapacity)
            this.miners = response.miners
        }
    }

    private fun getTopMiners() {

    }

    private fun prepareMinerInfo() {

    }

    private fun generateSetMinimumMessage() {
        val address = document.getElementById("setMinimumAddress")?.value?.escapeHtml()
        val newPayout = document.getElementById("setMinimumAmount")?.value?.escapeHtml()
        if (newPayout == null || address == null || newPayout.isEmpty() || address.isEmpty()) {
            window.alert("Please set new minimum payout and address")
            return
        }
        WebUtil.fetchJson<SetMinimumMessage>("/api/getSetMinimumMessage?address=$address&newPayout=$newPayout").then { response ->
            if (response == null) {
                console.error("Null set minimum message response")
                return@then
            }
            document.getElementById("setMinimumMessage")?.value = response.message.escapeHtml()
        }
    }

    private fun getWonBlocks() {
        WebUtil.fetchJson<WonBlocks>("/api/getWonBlocks").then { response ->
            if (response == null) {
                console.error("Null won blocks response")
                return@then
            }
            val wonBlocks = response.wonBlocks
            val table = document.getElementById("wonBlocksTable")
            if (table == null) {
                console.error("Null won blocks table")
                return@then
            }
            table.innerHTML = "<tr><th>Height</th><th>ID</th><th>Winner</th><th>Reward + Fees</th></tr>"
            for (i in 0 until wonBlocks.size) {
                val wonBlock = wonBlocks[i]
                table.innerHTML += "<tr><td>"+wonBlock.height+"</td><td>"+wonBlock.id+"</td><td>"+ formatMinerName(wonBlock.generatorRS, wonBlock.generator, null, true)+"</td><td>"+wonBlock.reward+"</td></tr>"
            }
        }
    }

    private fun setMinimumPayout() {

    }
}
