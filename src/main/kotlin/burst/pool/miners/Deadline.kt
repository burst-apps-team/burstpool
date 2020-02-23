package burst.pool.miners

import java.math.BigInteger

data class Deadline(val deadline: BigInteger, val baseTarget: BigInteger, val height: Long) {
    fun calculateHit(): BigInteger {
        return baseTarget.multiply(deadline)
    }
}
