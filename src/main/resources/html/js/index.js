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
            topMinerShares.push(miner.share * 100);
        }
        topMinerNames.push("Other");
        topMinerShares.push(response.othersShare * 100);
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

function prepareMinerInfo(address) {
    setCookie("getMinerLastValue", address);
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
