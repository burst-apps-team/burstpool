package burst.pool.miners;

class MinerMaths {
    private static final double GenesisBaseTarget = 18325193796d;
    private final double[] alphas;

    MinerMaths(int nAvg, int nMin) {
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

    double estimatedEffectivePlotSize(int nConf, double hitSum) {
        if (hitSum == 0) {
            return 0;
        }
        return alpha(nConf) * 240d * (((double)nConf)-1d) / (hitSum / GenesisBaseTarget);
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
