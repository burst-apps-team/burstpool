package burst.pool.entity

import burst.kit.entity.BurstAddress
import burst.kit.entity.BurstID
import burst.kit.entity.BurstValue
import java.math.BigInteger

data class WonBlock(val blockHeight: Int, val blockId: BurstID, val generatorId: BurstAddress, val nonce: BigInteger, val fullReward: BurstValue)
