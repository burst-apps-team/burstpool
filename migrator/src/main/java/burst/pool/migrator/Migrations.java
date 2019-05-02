package burst.pool.migrator;

import burst.kit.entity.BurstAddress;
import burst.pool.migrator.entity.MinerWithCapacity;
import burst.pool.migrator.nogroddb.tables.records.BlockRecord;
import burst.pool.migrator.nogroddb.tables.records.MinerRecord;
import burst.pool.migrator.nogroddb.tables.records.NonceSubmissionRecord;
import org.jooq.DSLContext;
import org.jooq.types.ULong;

import java.util.List;
import java.util.Map;

import static burst.pool.migrator.db.tables.Minerdeadlines.MINERDEADLINES;
import static burst.pool.migrator.db.tables.Miners.MINERS;
import static burst.pool.migrator.db.tables.Poolstate.POOLSTATE;
import static burst.pool.migrator.db.tables.Wonblocks.WONBLOCKS;
import static burst.pool.migrator.nogroddb.tables.Account.ACCOUNT;
import static burst.pool.migrator.nogroddb.tables.Block.BLOCK;
import static burst.pool.migrator.nogroddb.tables.Miner.MINER;
import static burst.pool.migrator.nogroddb.tables.NonceSubmission.NONCE_SUBMISSION;

public class Migrations implements Runnable {
    private final DSLContext source;
    private final DSLContext target;
    private final Map<Long, Long> baseTargetsAtHeight;

    public Migrations(DSLContext source, DSLContext target) {
        this.source = source;
        this.target = target;
        this.baseTargetsAtHeight = null /*BurstNodeService.getInstance("https://wallet1.burst-team.us:2083")
                .getBlocks(0, 10000)
                .map(blocks -> Arrays.stream(blocks.getBlocks()).collect(Collectors.toMap(BlockResponse::getHeight, BlockResponse::getBaseTarget)))
                .blockingGet();
        System.out.println(baseTargetsAtHeight);
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/; // TODO
    }

    @Override
    public void run() {
        System.err.println("Migrating miners...");
        migrateMiners();
        System.err.println("Migrating won blocks...");
        migrateWonBlocks();
        System.err.println("Migrating deadlines...");
        migrateMinerDeadlines();
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
                        ((double) (miner.getAccount().getMinPayoutValue() == null ? 100 : miner.getAccount().getMinPayoutValue())), // TODO This needs to go from long to double??
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
                            block.getWinnerId() == null ? 0 : block.getWinnerId().longValue(),
                            block.getBestNonceSubmissionId() == null ? "0" : getNonce(block.getBestNonceSubmissionId()),
                            block.getReward()) // TODO check this is in planck
                    .execute();
        }
        target.insertInto(POOLSTATE, POOLSTATE.KEY, POOLSTATE.VALUE)
                .values("lastProcessedBlock", Integer.toString(highestHeight))
                .execute();
    }

    private void migrateMinerDeadlines() {
        List<NonceSubmissionRecord> nonceSubmissions = source.selectFrom(NONCE_SUBMISSION)
                .fetch();
        nonceSubmissions.forEach(nonceSubmission -> target.insertInto(MINERDEADLINES, MINERDEADLINES.ACCOUNT_ID, MINERDEADLINES.HEIGHT, MINERDEADLINES.DEADLINE, MINERDEADLINES.BASETARGET)
                // TODO Lack of baseTarget is a SERIOUS PROBLEM for calculating hitSum and therefore capacity
                        .values(getMinerAccountIdFromTableId(nonceSubmission.getMinerId()), nonceSubmission.getBlockHeight().longValue(), nonceSubmission.getDeadline().longValue(), 0L /* TODO Nogrod does not store basetarget? */)
                .execute());
    }

    private long getAccountCapacity(ULong minerId) {
        MinerRecord miner = source.selectFrom(MINER)
                .where(MINER.ID.eq(minerId))
                .fetchAny();
        return miner == null ? 0 : miner.getCapacity();
    }

    private String getNonce(long id) {
        return source.selectFrom(NONCE_SUBMISSION)
                .where(NONCE_SUBMISSION.ID.eq(id))
                .fetchAny()
                .getNonce()
                .toString();
    }

    private long getMinerAccountIdFromTableId(ULong tableId) {
        String address = source.selectFrom(ACCOUNT)
                .where(ACCOUNT.ID.eq(tableId))
                .fetchAny()
                .getAddress();
        return BurstAddress.fromRs(address).getBurstID().getSignedLongId();
    }
}
