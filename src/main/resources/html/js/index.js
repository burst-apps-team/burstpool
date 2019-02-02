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
            table.innerHTML += "<tr><td>"+minerAddress+"</td><td>"+miner.pendingBalance+"</td><td>"+miner.estimatedCapacity+" TB</td><td>"+miner.nConf+"</td><td>"+parseFloat(miner.share)*100+"%</td><td>"+userAgent+"</td></tr>";
        }
    });
}

getCurrentRound();
getMiners();

setInterval(getCurrentRound, 500);
setInterval(getMiners, 10000);
