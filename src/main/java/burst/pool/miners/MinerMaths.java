package burst.pool.miners;

import java.math.BigDecimal;
import java.math.BigInteger;

public class MinerMaths {
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

    public double estimatedEffectivePlotSize(int nConf, BigInteger hitSum) {
        if (hitSum.compareTo(BigInteger.ZERO) == 0) {
            return 0;
        }
        return alpha(nConf) * 240d * (((double)nConf)-1d) / (hitSum.divide(BigInteger.valueOf(GenesisBaseTarget)).longValue());
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
