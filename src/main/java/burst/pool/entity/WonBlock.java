package burst.pool.entity;

import burst.kit.entity.BurstID;
import burst.kit.entity.BurstValue;

import java.math.BigInteger;

public class WonBlock {
    private final int blockHeight;
    private final BurstID blockId;
    private final BurstID generatorId;
    private final BigInteger nonce;
    private final BurstValue fullReward;

    public WonBlock(int blockHeight, BurstID blockId, BurstID generatorId, BigInteger nonce, BurstValue fullReward) {
        this.blockHeight = blockHeight;
        this.blockId = blockId;
        this.generatorId = generatorId;
        this.nonce = nonce;
        this.fullReward = fullReward;
    }

    public int getBlockHeight() {
        return blockHeight;
    }

    public BurstID getBlockId() {
        return blockId;
    }

    public BurstID getGeneratorId() {
        return generatorId;
    }

    public BigInteger getNonce() {
        return nonce;
    }

    public BurstValue getFullReward() {
        return fullReward;
    }
}
