package burst.pool.migrator;

import burst.kit.entity.BurstAddress;
import burst.pool.migrator.db.burstpool.tables.Wonblocks;
import burst.pool.migrator.entity.MinerWithCapacity;
import burst.pool.migrator.nogroddb.nogrod.tables.records.BlockRecord;
import org.jooq.DSLContext;
import org.jooq.types.ULong;

import java.util.List;

import static burst.pool.migrator.db.burstpool.tables.Miners.MINERS;
import static burst.pool.migrator.db.burstpool.tables.Poolstate.POOLSTATE;
import static burst.pool.migrator.db.burstpool.tables.Wonblocks.WONBLOCKS;
import static burst.pool.migrator.nogroddb.nogrod.Tables.BLOCK;
import static burst.pool.migrator.nogroddb.nogrod.Tables.NONCE_SUBMISSION;
import static burst.pool.migrator.nogroddb.nogrod.tables.Account.ACCOUNT;
import static burst.pool.migrator.nogroddb.nogrod.tables.Miner.MINER;

public class Migrations implements Runnable {
    private final DSLContext source;
    private final DSLContext target;

    public Migrations(DSLContext source, DSLContext target) {
        this.source = source;
        this.target = target;
    }

    @Override
    public void run() {
        migrateMiners();
        migrateWonBlocks();
    }

    private void migrateMiners() {
        List<MinerWithCapacity> sourceMiners = source.selectFrom(ACCOUNT)
                .fetch()
                .map(account -> new MinerWithCapacity(account, getAccountCapacity(account.getId())));
        sourceMiners.forEach(miner -> target.insertInto(MINERS, MINERS.ACCOUNT_ID, MINERS.PENDING_BALANCE, MINERS.ESTIMATED_CAPACITY, MINERS.SHARE, MINERS.MINIMUM_PAYOUT, MINERS.NAME, MINERS.USER_AGENT)
                .values(BurstAddress.fromRs(miner.getAccount().getAddress()).getBurstID().getSignedLongId(),
                        ((double) miner.getAccount().getPending()), // TODO This needs to go from long to double??
                        ((double) miner.getCapacity()), // TODO This needs to go from long to double??
                        0d, // TODO Nogrod does not store share?
                        ((double) miner.getAccount().getMinPayoutValue()), // TODO This needs to go from long to double??
                        miner.getAccount().getName(),
                        "") // TODO Nogrod does not store user agent?
                .execute());
    }

    private void migrateWonBlocks() {
        List<BlockRecord> blocks = source.selectFrom(BLOCK)
                .fetch();
        int highestHeight = 0;
        for (BlockRecord block : blocks) {
            if (highestHeight <= block.getHeight().longValue()) {
                highestHeight = Math.toIntExact(block.getHeight().longValue());
            }
        target.insertInto(WONBLOCKS, WONBLOCKS.BLOCKHEIGHT, WONBLOCKS.BLOCKID, WONBLOCKS.GENERATORID, WONBLOCKS.NONCE, WONBLOCKS.FULLREWARD)
                .values(block.getHeight().longValue(),
                        0L, // TODO Nogrod does not record block ID?
                        block.getWinnerId().longValue(),
                        getNonce(block.getBestNonceSubmissionId()),
                        block.getReward()) // TODO check this is in planck
                .execute();
        }
        target.insertInto(POOLSTATE, POOLSTATE.KEY, POOLSTATE.VALUE)
                .values("lastProcessedBlock", Integer.toString(highestHeight))
                .execute();
    }

    private long getAccountCapacity(ULong id) {
        return source.selectFrom(MINER)
                .where(MINER.ID.eq(id))
                .fetchAny()
                .getCapacity();
    }

    private String getNonce(long id) {
        return source.selectFrom(NONCE_SUBMISSION)
                .where(NONCE_SUBMISSION.ID.eq(id))
                .fetchAny()
                .getNonce()
                .toString();
    }
}
