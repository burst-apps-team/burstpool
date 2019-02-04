let miners = new Array(0);

let colors = new Array(11);

for (let i = 0; i < colors.length; i++) {
    colors[i] = generateColour();
}

function generateColour() {
    return "rgb(" + Math.floor(Math.random() * 255) + "," + Math.floor(Math.random() * 255) + "," + Math.floor(Math.random() * 255) + ")";
}

function htmlToElement(html) {
    let template = document.createElement('template');
    html = html.trim();
    template.innerHTML = html;
    return template.content.firstChild;
}

function getPoolInfo() {
    fetch("/api/getConfig").then(http => {
        return http.json();
    }).then(response => {
        document.getElementById("poolNameTitle").innerText = response.poolName;
        document.title = "Burst Pool (" + response.poolName + ")";
        document.getElementById("poolName").innerText = response.poolName;
        document.getElementById("poolAccount").innerText = response.poolAccountRS + " (" + response.poolAccount + ")";
        document.getElementById("nAvg").innerText = response.nAvg;
        document.getElementById("nMin").innerText = response.nMin;
        document.getElementById("maxDeadline").innerText = response.maxDeadline;
        document.getElementById("processLag").innerText = response.processLag + " Blocks";
        document.getElementById("feeRecipient").innerText = response.feeRecipientRS;
        document.getElementById("poolFee").innerText = (parseFloat(response.poolFeePercentage)*100).toFixed(3) + "%";
        document.getElementById("winnerReward").innerText = (parseFloat(response.winnerRewardPercentage)*100).toFixed(3) + "%";
        document.getElementById("minimumMinimumPayout").innerText = response.minimumPayout + " BURST";
        document.getElementById("minPayoutsAtOnce").innerText = response.minPayoutsPerTransaction;
        document.getElementById("payoutTxFee").innerText = response.transactionFee + " BURST";
        document.getElementById("poolVersion").innerText = response.version;
    });
}

function getCurrentRound() {
    fetch("/api/getCurrentRound").then(http => {
        return http.json();
    }).then(response => {
        document.getElementById("currentRoundElapsed").innerText = response.roundElapsed;
        document.getElementById("blockHeight").innerText = response.miningInfo.height;
        document.getElementById("baseTarget").innerText = response.miningInfo.baseTarget;
        if (response.bestDeadline != null) {
            document.getElementById("bestDeadline").innerText = response.bestDeadline.deadline;
            document.getElementById("bestMiner").innerText = response.bestDeadline.minerRS;
            document.getElementById("bestNonce").innerText = response.bestDeadline.nonce;
        } else {
            document.getElementById("bestDeadline").innerText = "None found yet!";
            document.getElementById("bestMiner").innerText = "None found yet!";
            document.getElementById("bestNonce").innerText = "None found yet!";
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
            let minerAddress = miner.name == null ? miner.addressRS : miner.addressRS + " (" + miner.name + ")";
            let userAgent = miner.userAgent == null? "Unknown" : miner.userAgent;
            table.innerHTML += "<tr><td>"+minerAddress+"</td><td>"+miner.pendingBalance+"</td><td>"+formatCapacity(miner.estimatedCapacity)+" TB</td><td>"+miner.nConf+"</td><td>"+(parseFloat(miner.share)*100).toFixed(3)+"%</td><td>"+userAgent+"</td></tr>";
        }
        document.getElementById("minerCount").innerText = response.miners.length;
        document.getElementById("poolCapacity").innerText = formatCapacity(response.poolCapacity) + " TB";
        let topTenMiners = response.miners.sort((a,b) => parseFloat(a.share) - parseFloat(b.share)).slice(0, 10);
        let minerShares = topTenMiners.map(miner => parseFloat(miner.share));
        let minerNames = topTenMiners.map(miner => miner.name == null ? miner.addressRS : miner.addressRS + " (" + miner.name + ")");
        let minerColors = colors.slice(0, topTenMiners.length + 1);
        let other = 1;
        minerShares.forEach(share => other -= share);
        minerShares.push(other);
        minerNames.push("Others");
        let chart = document.getElementById("sharesChart"), newChart = htmlToElement("<canvas id=\"sharesChart\" class=\"w-100 h-100\"></canvas>");
        chart.parentNode.replaceChild(newChart, chart);
        new Chart(newChart, {
            type: "pie",
            data: {
                datasets: [{
                    data: minerShares,
                    backgroundColor: minerColors
                }],
                labels: minerNames
            },
            options: {
                animation: {
                    duration: 0
                },
                title: {
                    display: true,
                    text: "Pool Shares"
                }
            }
        });
        miners = response.miners;
    });
}

function prepareMinerInfo(address) {
    let minerAddress = document.getElementById("minerAddress");
    let minerName = document.getElementById("minerName");
    let minerPending = document.getElementById("minerPending");
    let minerCapacity = document.getElementById("minerCapacity");
    let minerNConf = document.getElementById("minerNConf");
    let minerShare = document.getElementById("minerShare");
    let minerSoftware = document.getElementById("minerSoftware");
    
    minerAddress.innerText = address;
    minerName.innerText = "Loading...";
    minerPending.innerText = "Loading...";
    minerCapacity.innerText = "Loading...";
    minerNConf.innerText = "Loading...";
    minerShare.innerText = "Loading...";
    minerSoftware.innerText = "Loading...";

    let miner = null;
    miners.forEach(aMiner => {
        if (aMiner.addressRS === address || aMiner.address.toString() === address || aMiner.name === address) {
            miner = aMiner;
        }
    });

    if (miner == null) {
        minerName.innerText = "Miner not found";
        minerPending.innerText = "Miner not found";
        minerCapacity.innerText = "Miner not found";
        minerNConf.innerText = "Miner not found";
        minerShare.innerText = "Miner not found";
        minerSoftware.innerText = "Miner not found";
        return;
    }

    let name = miner.name == null ? "Not Set" : miner.name;
    let userAgent = miner.userAgent == null ? "Unknown" : miner.userAgent;

    minerAddress.innerText = miner.addressRS;
    minerName.innerText = name;
    minerPending.innerText = miner.pendingBalance;
    minerCapacity.innerText = formatCapacity(miner.estimatedCapacity);
    minerNConf.innerText = miner.nConf;
    minerShare.innerText = (parseFloat(miner.share)*100).toFixed(3) + "%";
    minerSoftware.innerText = userAgent;
}

function formatCapacity(capacity) {
    return parseFloat(capacity).toFixed(3);
}

function onPageLoad() {
    $('#minerInfoModal').on('show.bs.modal', function (event) {
        prepareMinerInfo(document.getElementById("addressInput").value);
    });
    document.getElementById("addressInput").addEventListener("keyup", function (event) {
        if (event.keyCode === 13) {
            event.preventDefault();
            document.getElementById("getMinerButton").click();
        }
    })
}

getPoolInfo();
getCurrentRound();
getMiners();

setInterval(getCurrentRound, 500);
setInterval(getMiners, 10000);
