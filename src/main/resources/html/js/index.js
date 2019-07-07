const noneFoundYet = "None found yet!";
const loading = "Loading...";
const minerNotFound = "Miner not found";

const genesisBaseTarget = 4398046511104 / 240;

let miners = new Array(0);
const colors = [
    "#3366CC",
    "#DC3912",
    "#FF9900",
    "#109618",
    "#990099",
    "#3B3EAC",
    "#0099C6",
    "#DD4477",
    "#66AA00",
    "#B82E2E",
    "#316395",
    "#994499",
    "#22AA99",
    "#AAAA11",
    "#6633CC",
    "#E67300",
    "#8B0707",
    "#329262",
    "#5574A6",
    "#3B3EAC"
];

const entityMap = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
    '/': '&#x2F;',
    '`': '&#x60;',
    '=': '&#x3D;'
};

function escapeHtml(string) {
    return typeof string === 'string' ? String(string).replace(/[&<>"'`=\/]/g, function (s) {
        return entityMap[s];
    }) : string;
}

let chart = null;

function formatTime(seconds) {
    const date = new Date(null);
    date.setSeconds(parseInt(seconds));
    return date.toISOString().substr(11, 8);
}

function formatBaseTarget(baseTarget) {
    return (genesisBaseTarget / baseTarget).toFixed(3) + " TB"
}

function getPoolInfo() {
    fetch("/api/getConfig").then(http => {
        return http.json();
    }).then(response => {
        document.getElementById("poolName").innerText = response.poolName;
        document.getElementById("poolAccount").innerHTML = formatMinerName(response.poolAccountRS, response.poolAccount, response.poolAccount, true);
        document.getElementById("nAvg").innerText = response.nAvg;
        document.getElementById("nMin").innerText = response.nMin;
        document.getElementById("maxDeadline").innerText = response.maxDeadline;
        document.getElementById("processLag").innerText = response.processLag + " Blocks";
        document.getElementById("feeRecipient").innerText = response.feeRecipientRS;
        document.getElementById("poolFee").innerText = (parseFloat(response.poolFeePercentage)*100).toFixed(3) + "%";
        document.getElementById("winnerReward").innerText = (parseFloat(response.winnerRewardPercentage)*100).toFixed(3) + "%";
        document.getElementById("minimumPayout").innerText = response.defaultMinimumPayout + " BURST";
        document.getElementById("minPayoutsAtOnce").innerText = response.minPayoutsPerTransaction;
        document.getElementById("payoutTxFee").innerText = response.transactionFee + " BURST";
        document.getElementById("poolVersion").innerText = response.version;
    });
}

let roundStart = 0;

function updateRoundElapsed() {
    document.getElementById("currentRoundElapsed").innerText = formatTime(parseInt((new Date().getTime() / 1000).toFixed()) - roundStart);
}

function getCurrentRound() {
    fetch("/api/getCurrentRound").then(http => {
        return http.json();
    }).then(response => {
        roundStart = response.roundStart;
        document.getElementById("blockHeight").innerText = response.miningInfo.height;
        document.getElementById("netDiff").innerText = formatBaseTarget(response.miningInfo.baseTarget);
        if (response.bestDeadline != null) {
            document.getElementById("bestDeadline").innerText = formatTime(response.bestDeadline.deadline);
            document.getElementById("bestMiner").innerText = formatMinerName(response.bestDeadline.minerRS, response.bestDeadline.miner, null, true);
            document.getElementById("bestNonce").innerText = response.bestDeadline.nonce;
        } else {
            document.getElementById("bestDeadline").innerText = noneFoundYet;
            document.getElementById("bestMiner").innerText = noneFoundYet;
            document.getElementById("bestNonce").innerText = noneFoundYet;
        }
    });
}

function getAccountExplorerLink(id) {
    return "https://explorer.burstcoin.network/?action=account&account=" + id;
}

function formatMinerName(rs, id, name, includeLink) {
    if (name == null) {
        miners.forEach(miner => {
            if (miner.address === id || miner.addressRS === rs) {
                name = miner.name;
            }
        })
    }
    name = escapeHtml(name);
    rs = escapeHtml(rs);
    if (includeLink) {
        rs = "<a href=\"" + getAccountExplorerLink(id) + "\">" + rs + "</a>";
    }
    return name == null || name === "" ? rs : rs + " (" + name + ")";
}

function getTop10Miners() {
    fetch("api/getTop10Miners").then(http => {
        return http.json();
    }).then(response => {
        let topTenMiners = response.topMiners;
        let topMinerNames = Array();
        let topMinerShares = Array();
        let minerColors = colors.slice(0, topTenMiners.length + 1);
        for (let i = 0; i < topTenMiners.length; i++) {
            let miner = topTenMiners[i];
            topMinerNames.push(formatMinerName(miner.addressRS, miner.address, miner.name, false));
            topMinerShares.push(miner.share);
        }
        topMinerNames.push("Other");
        topMinerShares.push(response.othersShare);
        if (chart == null) {
            chart = new Chart(document.getElementById("sharesChart"), {
                type: "pie",
                data: {
                    datasets: [{
                        data: topMinerShares,
                        backgroundColor: minerColors
                    }],
                    labels: topMinerNames
                },
                options: {
                    title: {
                        display: true,
                        text: "Pool Shares"
                    },
                    responsive: true,
                    maintainAspectRatio: false,
                }
            });
        } else {
            chart.data.datasets[0].data = topMinerShares;
            chart.data.datasets[0].backgroundColor = minerColors;
            chart.data.labels = topMinerNames;
            chart.update();
        }
    });
}

function getMiners() {
    fetch("/api/getMiners").then(http => {
        return http.json();
    }).then(response => {
        let table = document.getElementById("miners");
        table.innerHTML = "<tr><th>Miner</th><th>Pending Balance</th><th>Effective Capacity</th><th>nConf (Last (nAvg + processLag) rounds)</th><th>Share</th><th>Software</th></tr>";
        for (let i = 0; i < response.miners.length; i++) {
            let miner = response.miners[i];
            let minerAddress = formatMinerName(miner.addressRS, miner.address, miner.name, true);
            let userAgent = escapeHtml(miner.userAgent == null? "Unknown" : miner.userAgent);
            table.innerHTML += "<tr><td>"+minerAddress+"</td><td>"+miner.pendingBalance+"</td><td>"+formatCapacity(miner.estimatedCapacity)+" TB</td><td>"+miner.nConf+"</td><td>"+(parseFloat(miner.share)*100).toFixed(3)+"%</td><td>"+userAgent+"</td></tr>";
        }
        document.getElementById("minerCount").innerText = response.miners.length;
        document.getElementById("poolCapacity").innerText = formatCapacity(response.poolCapacity) + " TB";
        miners = response.miners;
    });
}

function prepareMinerInfo(address) {
    let minerAddress = escapeHtml(document.getElementById("minerAddress"));
    let minerName = escapeHtml(document.getElementById("minerName"));
    let minerPending = escapeHtml(document.getElementById("minerPending"));
    let minerMinimumPayout = escapeHtml(document.getElementById("minerMinimumPayout"));
    let minerCapacity = escapeHtml(document.getElementById("minerCapacity"));
    let minerNConf = escapeHtml(document.getElementById("minerNConf"));
    let minerShare = escapeHtml(document.getElementById("minerShare"));
    let minerSoftware = escapeHtml(document.getElementById("minerSoftware"));
    let setMinimumPayoutButton = $("#setMinimumPayoutButton");
    
    minerAddress.innerText = address;
    minerName.innerText = loading;
    minerPending.innerText = loading;
    minerMinimumPayout.innerText = loading;
    minerCapacity.innerText = loading;
    minerNConf.innerText = loading;
    minerShare.innerText = loading;
    minerSoftware.innerText = loading;

    let miner = null;
    miners.forEach(aMiner => {
        if (aMiner.addressRS === address || aMiner.address.toString() === address || aMiner.name === address) {
            miner = aMiner;
        }
    });

    if (miner == null) {
        minerName.innerText = minerNotFound;
        minerPending.innerText = minerNotFound;
        minerMinimumPayout.innerText = minerNotFound;
        minerCapacity.innerText = minerNotFound;
        minerNConf.innerText = minerNotFound;
        minerShare.innerText = minerNotFound;
        minerSoftware.innerText = minerNotFound;
        setMinimumPayoutButton.hide();
        return;
    }

    let name = escapeHtml(miner.name == null ? "Not Set" : miner.name);
    let userAgent = escapeHtml(miner.userAgent == null ? "Unknown" : miner.userAgent);

    minerAddress.innerText = miner.addressRS;
    minerName.innerText = name;
    minerPending.innerText = miner.pendingBalance;
    minerMinimumPayout.innerText = miner.minimumPayout;
    minerCapacity.innerText = formatCapacity(miner.estimatedCapacity);
    minerNConf.innerText = miner.nConf;
    minerShare.innerText = (parseFloat(miner.share)*100).toFixed(3) + "%";
    minerSoftware.innerText = userAgent;
    setMinimumPayoutButton.show();
}

function formatCapacity(capacity) {
    return parseFloat(capacity).toFixed(3);
}

function onPageLoad() {
    $('#minerInfoModal').on('show.bs.modal', function (event) {
        prepareMinerInfo(document.getElementById("addressInput").value);
    });
    $('#setMinimumPayoutModal').on('show.bs.modal', function (event) {
        document.getElementById("setMinimumAddress").value = escapeHtml(document.getElementById("minerAddress").innerText);
        $("#setMinimumResult").hide();
    });
    document.getElementById("addressInput").addEventListener("keyup", function (event) {
        if (event.keyCode === 13) {
            event.preventDefault();
            document.getElementById("getMinerButton").click();
        }
    });
    document.getElementById("icon").onerror = function () {
        this.style.display = "none";
    }
}

function generateSetMinimumMessage() {
    let address = escapeHtml(document.getElementById("setMinimumAddress").value);
    let newPayout = escapeHtml(document.getElementById("setMinimumAmount").value);
    if (document.getElementById("setMinimumAmount").value === "") {
        alert("Please set new minimum payout");
        return;
    }
    fetch("/api/getSetMinimumMessage?address="+address+"&newPayout="+newPayout).then(http => {
        return http.json();
    }).then(response => {
        document.getElementById("setMinimumMessage").value = escapeHtml(response.message);
    });
}

function getWonBlocks() {
    fetch("/api/getWonBlocks").then(response => {
        return response.json();
    }).then(response => {
        let wonBlocks = response.wonBlocks;
        let table = document.getElementById("wonBlocksTable");
        table.innerHTML = "<tr><th>Height</th><th>ID</th><th>Winner Name</th><th>Winner Address</th><th>Reward + Fees</th></tr>";
        for (let i = 0; i < wonBlocks.length; i++) {
            let wonBlock = wonBlocks[i];
            let height = escapeHtml(wonBlock.height);
            let id = escapeHtml(wonBlock.id);
            let winner = escapeHtml(wonBlock.generator);
            let reward = escapeHtml(wonBlock.reward);
            let minerName = "";
            miners.forEach(miner => {
                if (miner.addressRS === winner) {
                    minerName = escapeHtml(miner.name);
                }
            });
            table.innerHTML += "<tr><td>"+height+"</td><td>"+id+"</td><td>"+minerName+"</td><td>"+winner+"</td><td>"+reward+"</td></tr>";
        }
    });
}

function setMinimumPayout() {
    var message = escapeHtml(document.getElementById("setMinimumMessage").value);
    var publicKey = escapeHtml(document.getElementById("setMinimumPublicKey").value);
    var signature = escapeHtml(document.getElementById("setMinimumSignature").value);
    if (message === "") {
        alert("Please generate message");
        return;
    }
    if (publicKey === "") {
        alert("Please enter public key");
        return;
    }
    if (signature === "") {
        alert("Please enter signature");
        return;
    }
    fetch("/api/setMinerMinimumPayout?assignment="+message+"&publicKey="+publicKey+"&signature="+signature, { method: "POST" }).then(http => {
        return http.json();
    }).then(response => {
        document.getElementById("setMinimumResultText").innerText = response;
        $("#setMinimumResult").show();
    });
}

getPoolInfo();
getCurrentRound();
getMiners();
getTop10Miners();

setInterval(updateRoundElapsed, 1000);
setInterval(getCurrentRound, 10000);
setInterval(getMiners, 60000); /* TODO only refresh this when we detect that we forged a block */
setInterval(getTop10Miners, 60000);
