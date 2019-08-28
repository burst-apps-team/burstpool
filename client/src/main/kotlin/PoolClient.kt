import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLInputElement
import kotlin.browser.document
import kotlin.browser.window
import kotlin.js.Date
import kotlin.math.roundToInt

object PoolClient {
    private var miners: Array<Miner> = emptyArray()
    private var maxSubmissions = Int.MAX_VALUE
    private var processLag = 0
    private var maxSubmissionsText = "Unknown"
    private var chart: Chart? = null
    private var roundStart: Int = 0
    private var lastSelectedMinerRS: String = ""

    private const val noneFoundYet = "None found yet!"
    private const val loadingText =  "Loading..."
    private const val minerNotFoundText = "Miner not found"

    fun init() {
        // Nothing to do until the page is loaded.
    }

    fun onPageLoaded() {
        getPoolInfo()
        WebUtil.schedule({ updateRoundElapsed() }, 1000, false)
        WebUtil.schedule({ getCurrentRound() }, 2000)
        WebUtil.schedule({ getMiners() }, 10000)
        WebUtil.schedule({ getTopMiners() }, 10000)
        document.getElementById("addressInput")?.value = WebUtil.getCookie("getMinerLastValue") ?: ""
        document.getElementById("generateSetMinimumMessageButton")?.onclick = { generateSetMinimumMessage() }
        document.getElementById("setMinimumPayoutButton")?.onclick = { setMinimumPayout() }
        document.getElementById("getWonBlocksButton")?.onclick = { getWonBlocks() }
        document.getElementById("getMinerButton")?.onclick = { prepareMinerInfo() }
        document.getElementById("openSetMinimumPayoutModalButton")?.onclick = {
            document.getElementById("setMinimumAddress")?.value = lastSelectedMinerRS
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
            this.maxSubmissions = poolConfig.nAvg
            this.processLag = poolConfig.processLag
            if (this.maxSubmissionsText == "Unknown") { // If this is the first fetch, don't leave it lingering as "Unknown", refresh the miners table
                getMiners()
            }
            this.maxSubmissionsText = poolConfig.nAvg.toString()
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
                document.getElementById("bestDeadline")?.textContent = Util.formatTime(bestDeadline.deadline)
                document.getElementById("bestMiner")?.innerHTML = formatMinerName(bestDeadline.minerRS, bestDeadline.miner, null, true)
                document.getElementById("bestNonce")?.textContent = bestDeadline.nonce
            } else {
                document.getElementById("bestDeadline")?.textContent = noneFoundYet
                document.getElementById("bestMiner")?.textContent = noneFoundYet
                document.getElementById("bestNonce")?.textContent = noneFoundYet
            }
        }
    }

    private fun updateRoundElapsed() {
        document.getElementById("currentRoundElapsed")?.textContent = Util.formatTime(((Date().getTime() / 1000) - roundStart).roundToInt())
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
            for (miner in response.miners) {
                val currentRoundDeadline = Util.formatTime(miner.currentRoundBestDeadline)
                val minerAddress = formatMinerName(miner.addressRS, miner.address, miner.name, true)
                val userAgent = miner.userAgent ?: "Unknown"
                var nConf = miner.nConf - this.processLag
                if (nConf < 0) nConf = 0
                if (nConf > this.maxSubmissions) nConf = this.maxSubmissions
                table.innerHTML += "<tr><td>"+minerAddress+"</td><td>"+currentRoundDeadline+"</td><td>"+miner.pendingBalance+"</td><td>"+Util.formatCapacity(miner.estimatedCapacity)+"</td><td>"+nConf+" / " + maxSubmissionsText + "</td><td>"+(miner.share*100).round(3).toString()+"%</td><td>"+userAgent+"</td></tr>"
            }
            document.getElementById("minerCount")?.textContent = response.miners.size.toString()
            document.getElementById("poolCapacity")?.textContent = Util.formatCapacity(response.poolCapacity)
            this.miners = response.miners
        }
    }

    private fun getTopMiners() {
        WebUtil.fetchJson<TopMiners>("/api/getTopMiners").then { response ->
            if (response == null) {
                console.error("Null top miners response")
                return@then
            }
            val topMiners = response.topMiners
            val topMinerNames = mutableListOf<String>()
            val topMinerShares = mutableListOf<Double>()
            for (miner in topMiners) {
                topMinerNames.add(formatMinerName(miner.addressRS, miner.address, miner.name, false))
                topMinerShares.add(miner.share * 100)
            }
            topMinerNames.add("Other")
            topMinerShares.add(response.othersShare * 100)
            val minerColors = Util.colors.slice(0..topMinerNames.size)
            if (this.chart == null) {
                val chartElement = document.getElementById("sharesChart")
                if (chartElement == null || chartElement !is HTMLCanvasElement) {
                    console.error("Shares chart null or not canvas element")
                    return@then
                }
                this.chart = Chart(chartElement, object: Chart.ChartConfiguration {
                    override var type = "pie"
                    override var data: Chart.ChartData? = object: Chart.ChartData {
                        override var labels: Array<dynamic>? = topMinerNames.toTypedArray()
                        override var datasets: Array<ChartDataSets>? = arrayOf(object: ChartDataSets {
                            override var data = topMinerShares.toTypedArray()
                            override var backgroundColor = minerColors.toTypedArray()
                        })
                    }
                    override var options: Chart.ChartOptions? = js("{title:{display:true,text:\"Pool Shares\"},responsive:true,maintainAspectRatio:false}").unsafeCast<Chart.ChartOptions>()
                })
            } else {
                chart!!.data.datasets!![0].data = topMinerShares.toTypedArray()
                chart!!.data.datasets!![0].backgroundColor = minerColors.toTypedArray()
                chart!!.data.labels = topMinerNames.toTypedArray()
                chart!!.update()
            }
        }
    }

    private fun prepareMinerInfo() {
        val address = document.getElementById("addressInput")?.value
        if (address == null) {
            console.error("Null address field")
            return
        }
        WebUtil.setCookie("getMinerLastValue", address)
        val minerAddress = document.getElementById("minerAddress")
        val minerPending = document.getElementById("minerPending")
        val minerMinimumPayout = document.getElementById("minerMinimumPayout")
        val minerCapacity = document.getElementById("minerCapacity")
        val minerNConf = document.getElementById("minerNConf")
        val minerShare = document.getElementById("minerShare")
        val minerSoftware = document.getElementById("minerSoftware")
        val setMinimumPayoutButtonTableRow = document.getElementById("openSetMinimumPayoutModalButtonTr")
        if (minerAddress == null || minerPending == null || minerMinimumPayout == null || minerCapacity == null || minerNConf == null || minerShare == null || minerSoftware == null || setMinimumPayoutButtonTableRow == null) {
            console.error("Prepare miner info: null controls")
            return
        }
        minerAddress.textContent = address
        minerPending.textContent = loadingText
        minerMinimumPayout.textContent = loadingText
        minerCapacity.textContent = loadingText
        minerNConf.textContent = loadingText
        minerShare.textContent = loadingText
        minerSoftware.textContent = loadingText
        setMinimumPayoutButtonTableRow.hide()
        
        var miner: Miner? = null
        for (it in this.miners) {
            if (it.addressRS == address || it.address == address || it.name?.toLowerCase() == address.toLowerCase()) {
                miner = it
            }
        }
        if (miner == null) {
            minerPending.textContent = minerNotFoundText
            minerMinimumPayout.textContent = minerNotFoundText
            minerCapacity.textContent = minerNotFoundText
            minerNConf.textContent = minerNotFoundText
            minerShare.textContent = minerNotFoundText
            minerSoftware.textContent = minerNotFoundText
            setMinimumPayoutButtonTableRow.hide()
        } else {
            lastSelectedMinerRS = miner.addressRS
            minerAddress.innerHTML = formatMinerName(miner.addressRS, miner.address, null, true)
            minerPending.textContent = miner.pendingBalance
            minerMinimumPayout.textContent = miner.minimumPayout
            minerCapacity.textContent = Util.formatCapacity(miner.estimatedCapacity)
            minerNConf.textContent = miner.nConf.toString()
            minerShare.textContent = (miner.share*100).round(3).toString() + "%"
            minerSoftware.textContent = (miner.userAgent ?: "Unknown")
            setMinimumPayoutButtonTableRow.show()
        }
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
                console.error("Null get set minimum message response")
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
            for (wonBlock in wonBlocks) {
                table.innerHTML += "<tr><td>"+wonBlock.height+"</td><td>"+wonBlock.id+"</td><td>"+ formatMinerName(wonBlock.generatorRS, wonBlock.generator, null, true)+"</td><td>"+wonBlock.reward+"</td></tr>"
            }
        }
    }

    private fun setMinimumPayout() {
        val message = document.getElementById("setMinimumMessage")?.value
        val publicKey = document.getElementById("setMinimumPublicKey")?.value
        val signature = document.getElementById("setMinimumSignature")?.value
        if (message == null || message.isEmpty()) {
            window.alert("Please generate message")
            return
        }
        if (publicKey == null || publicKey.isEmpty()) {
            window.alert("Please enter public key")
            return
        }
        if (signature == null || signature.isEmpty()) {
            window.alert("Please enter signature")
            return
        }
        WebUtil.fetchJson<String>("/api/setMinerMinimumPayout?assignment=$message&publicKey=$publicKey&signature=$signature", true).then { responseMessage ->
            if (responseMessage == null) {
                console.error("Null set minimum message response")
                return@then
            }
            document.getElementById("setMinimumResultText")?.textContent = responseMessage
            document.getElementById("setMinimumResult")?.show()
        }
    }
}
