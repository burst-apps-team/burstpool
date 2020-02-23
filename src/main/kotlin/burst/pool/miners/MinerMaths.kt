package burst.pool.miners

import org.slf4j.LoggerFactory
import java.math.BigInteger

class MinerMaths(nAvg: Int, nMin: Int) {
    private val logger = LoggerFactory.getLogger(MinerMaths::class.java)
    private val alphas: DoubleArray
    fun estimatedEffectivePlotSize(originalNConf: Int, nConf: Int, hitSum: BigInteger): Double {
        if (hitSum.compareTo(BigInteger.ZERO) == 0) {
            throw ArithmeticException()
        }
        val plotSize: Double = alpha(originalNConf) * 240.0 * (nConf.toDouble() - 1.0) / hitSum.divide(BigInteger.valueOf(GenesisBaseTarget)).toLong()
        if (java.lang.Double.isInfinite(plotSize) || java.lang.Double.isNaN(plotSize)) {
            logger.debug("Calculated impossible plot size. originalNConf: $originalNConf, nConf: $nConf, hitSum: $hitSum")
            throw ArithmeticException()
        }
        return plotSize
    }

    private fun alpha(nConf: Int): Double {
        if (nConf == 0) {
            return 0.0
        }
        return if (alphas.size < nConf) {
            1.0
        } else alphas[nConf - 1]
    }

    companion object {
        private const val GenesisBaseTarget = 18325193796L
    }

    init {
        alphas = DoubleArray(nAvg)
        for (i in 0 until nAvg) {
            if (i < nMin - 1) {
                alphas[i] = 0.0
            } else {
                val nConf = i + 1.toDouble()
                alphas[i] = 1.0 - (nAvg.toDouble() - nConf) / nConf * Math.log(nAvg / (nAvg.toDouble() - nConf))
            }
        }
        alphas[nAvg - 1] = 1.0
    }
}
