package burst.pool.entity

import burst.kit.entity.BurstID
import burst.kit.entity.BurstValue

data class Payout(val transactionId: BurstID, val senderPublicKey: ByteArray, val fee: BurstValue, val deadline: Int, val attachment: ByteArray)
