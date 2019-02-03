let poolInfo = null;

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
        poolInfo = response;
        document.getElementById("poolName").innerText = response.poolName;
        document.title = "Burst Pool (" + response.poolName + ")";
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
            table.innerHTML += "<tr><td>"+minerAddress+"</td><td>"+miner.pendingBalance+"</td><td>"+formatCapacity(miner.estimatedCapacity)+" TB</td><td>"+miner.nConf+"</td><td>"+parseFloat(miner.share)*100+"%</td><td>"+userAgent+"</td></tr>";
        }
        document.getElementById("poolCapacity").innerText = formatCapacity(response.poolCapacity) + " TB";
        let topTenMiners = response.miners.sort((a,b) => parseFloat(a.share) - parseFloat(b.share)).slice(0, 10);
        let minerShares = topTenMiners.map(miner => parseFloat(miner.share));
        let minerNames = topTenMiners.map(miner => miner.name == null ? miner.addressRS : miner.addressRS + " (" + miner.name + ")");
        let minerColors = colors.slice(0, topTenMiners.length + 1);
        let other = 1;
        minerShares.forEach(share => other -= share);
        minerShares.push(other);
        minerNames.push("Others");
        console.log(minerShares);
        console.log(minerNames);
        console.log(minerColors);
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
    });
}

function formatCapacity(capacity) {
    return parseFloat(capacity).toFixed(3);
}

getPoolInfo();
getCurrentRound();
getMiners();

setInterval(getCurrentRound, 500);
setInterval(getMiners, 10000);
