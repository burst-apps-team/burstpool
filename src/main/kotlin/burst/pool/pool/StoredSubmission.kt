package burst.pool.pool

import burst.kit.entity.BurstAddress
import java.math.BigInteger

class StoredSubmission(miner: BurstAddress, nonce: BigInteger, val deadline: Long) : Submission(miner, nonce)
