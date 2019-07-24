external interface PoolConfig {
    val version: String
    val poolName: String
    val poolAccount: String
    val poolAccountRS: String
    val nAvg: Int
    val nMin: Int
    val maxDeadline: Int
    val processLag: Int
    val feeRecipient: String
    val feeRecipientRS: String
    val poolFeePercentage: Double
    val winnerRewardPercentage: Double
    val defaultMinimumPayout: String
    val minPayoutsPerTransaction: Int
    val transactionFee: Double
}

external interface BestDeadline {
    val miner: String
    val minerRS: String
    val nonce: String
    val deadline: Int
}

external interface MiningInfo {
    val generationSignature: String
    val baseTarget: Int
    val height: Int
}

external interface Round {
    val roundStart: Int
    val bestDeadline: BestDeadline?
    val miningInfo: MiningInfo?
}

external interface Miners {
    val miners: Array<Miner>
    val poolCapacity: Double
}

external interface SetMinimumMessage {
    val message: String
}

external interface WonBlock {
    val height: Int
    val id: String
    val generator: String
    val generatorRS: String
    val reward: String
}

external interface WonBlocks {
    val wonBlocks: Array<WonBlock>
}

external interface TopMiners {
    val topMiners: Array<Miner>
    val othersShare: Double
}

external interface Miner {
    val address: String
    val addressRS: String
    val pendingBalance: String
    val estimatedCapacity: Double
    val nConf: Int
    val share: Double
    val minimumPayout: String
    val currentRoundBestDeadline: Int?
    val name: String?
    val userAgent: String?
}
