package burst.pool.miners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

public class MinerMaths {
    private final Logger logger = LoggerFactory.getLogger(MinerMaths.class);

    private static final long GenesisBaseTarget = 18325193796L;
    private final double[] alphas;

    public MinerMaths(int nAvg, int nMin) {
        alphas = new double[nAvg];
        for (int i = 0; i < nAvg; i++) {
            if (i < nMin-1) {
                alphas[i] = 0d;
            } else {
                double nConf = i + 1;
                alphas[i] = 1d - ((double)nAvg-nConf)/ nConf *Math.log(nAvg/((double)nAvg-nConf));
            }
        }
        alphas[nAvg-1] = 1d;
    }

    public double estimatedEffectivePlotSize(int originalNConf, int nConf, BigInteger hitSum) {
        if (hitSum.compareTo(BigInteger.ZERO) == 0) {
            throw new ArithmeticException();
        }
        double plotSize =  alpha(originalNConf) * 240d * (((double)nConf)-1d) / (hitSum.divide(BigInteger.valueOf(GenesisBaseTarget)).longValue());
        if (Double.isInfinite(plotSize) || Double.isNaN(plotSize)) {
            logger.debug("Calculated impossible plot size. originalNConf: " + originalNConf + ", nConf: " + nConf + ", hitSum: " + hitSum);
            throw new ArithmeticException();
        }
        return plotSize;
    }

    private double alpha(int nConf) {
        if (nConf == 0) {
            return 0d;
        }
        if (alphas.length < nConf) {
            return 1d;
        }
        return alphas[nConf-1];
    }
}
