package burst.pool.migrator;

import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstID;
import burst.kit.entity.response.Block;
import burst.kit.service.BurstNodeService;
import burst.pool.migrator.entity.MinerWithCapacity;
import burst.pool.migrator.nogroddb.tables.records.BlockRecord;
import burst.pool.migrator.nogroddb.tables.records.MinerRecord;
import burst.pool.migrator.nogroddb.tables.records.NonceSubmissionRecord;
import org.jooq.DSLContext;
import org.jooq.types.ULong;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static burst.pool.migrator.db.tables.MinerDeadlines.MINER_DEADLINES;
import static burst.pool.migrator.db.tables.Miners.MINERS;
import static burst.pool.migrator.db.tables.PoolState.POOL_STATE;
import static burst.pool.migrator.db.tables.WonBlocks.WON_BLOCKS;
import static burst.pool.migrator.nogroddb.tables.Account.ACCOUNT;
import static burst.pool.migrator.nogroddb.tables.Block.BLOCK;
import static burst.pool.migrator.nogroddb.tables.Miner.MINER;
import static burst.pool.migrator.nogroddb.tables.NonceSubmission.NONCE_SUBMISSION;

public class Migrations implements Runnable {
    private final BurstNodeService burstNodeService;
    private final DSLContext source;
    private final DSLContext target;
    private final Map<Integer, Long> baseTargetsAtHeight;

    public Migrations(DSLContext source, DSLContext target) {
        this.source = source;
        this.target = target;
        burstNodeService = BurstNodeService.getInstance("https://wallet1.burst-team.us:2083");
        baseTargetsAtHeight = burstNodeService
                .getBlocks(0, 10000)
                .map(blocks -> Arrays.stream(blocks).collect(Collectors.toMap(Block::getHeight, Block::getBaseTarget)))
                .blockingGet();
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
                        (miner.getAccount().getPending()),
                        (((double) miner.getCapacity()) / 1000.0),
                        0d, // TODO Nogrod does not store share?
                        (miner.getAccount().getMinPayoutValue() == null ? 10000000000L : miner.getAccount().getMinPayoutValue()),
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
            target.insertInto(WON_BLOCKS, WON_BLOCKS.BLOCK_HEIGHT, WON_BLOCKS.BLOCK_ID, WON_BLOCKS.GENERATOR_ID, WON_BLOCKS.NONCE, WON_BLOCKS.FULL_REWARD)
                    .values(block.getHeight().longValue(),
                            getBlockId(block.getHeight().intValue()),
                            block.getWinnerId() == null ? 0 : block.getWinnerId().longValue(),
                            block.getBestNonceSubmissionId() == null ? "0" : getNonce(block.getBestNonceSubmissionId()),
                            block.getReward())
                    .execute();
        }
        target.insertInto(POOL_STATE, POOL_STATE.KEY, POOL_STATE.VALUE)
                .values("lastProcessedBlock", Integer.toString(highestHeight))
                .execute();
    }

    private void migrateMinerDeadlines() {
        List<NonceSubmissionRecord> nonceSubmissions = source.selectFrom(NONCE_SUBMISSION)
                .fetch();
        nonceSubmissions.forEach(nonceSubmission -> target.insertInto(MINER_DEADLINES, MINER_DEADLINES.ACCOUNT_ID, MINER_DEADLINES.HEIGHT, MINER_DEADLINES.DEADLINE, MINER_DEADLINES.BASE_TARGET)
                        .values(getMinerAccountIdFromTableId(nonceSubmission.getMinerId()), nonceSubmission.getBlockHeight().longValue(), nonceSubmission.getDeadline().longValue(), baseTargetsAtHeight.get(nonceSubmission.getBlockHeight().intValue()))
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

    private long getBlockId(int height) {
        return burstNodeService
                .getBlockId(height)
                .map(BurstID::getSignedLongId)
                .blockingGet();
    }
}
