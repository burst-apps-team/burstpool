package burst.pool.entity;

import burst.kit.entity.BurstID;
import burst.kit.entity.BurstValue;

public class Payout {
    private final BurstID transactionId;
    private final byte[] senderPublicKey;
    private final BurstValue fee;
    private final int deadline;
    private final byte[] attachment;

    public Payout(BurstID transactionId, byte[] senderPublicKey, BurstValue fee, int deadline, byte[] attachment) {
        this.transactionId = transactionId;
        this.senderPublicKey = senderPublicKey;
        this.fee = fee;
        this.deadline = deadline;
        this.attachment = attachment;
    }

    public BurstID getTransactionId() {
        return transactionId;
    }

    public byte[] getSenderPublicKey() {
        return senderPublicKey;
    }

    public BurstValue getFee() {
        return fee;
    }

    public int getDeadline() {
        return deadline;
    }

    public byte[] getAttachment() {
        return attachment;
    }
}
