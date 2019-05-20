package burst.pool.migrator;

import burst.kit.entity.BurstAddress;
import burst.kit.entity.response.Block;
import burst.kit.service.BurstNodeService;
import burst.pool.migrator.db.tables.records.MinerDeadlinesRecord;
import burst.pool.migrator.entity.MinerWithCapacity;
import burst.pool.migrator.nogroddb.tables.records.BlockRecord;
import burst.pool.migrator.nogroddb.tables.records.MinerRecord;
import burst.pool.migrator.nogroddb.tables.records.NonceSubmissionRecord;
import org.jooq.BatchBindStep;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.types.ULong;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final Map<Integer, Block> blocksAtHeights = new ConcurrentHashMap<>();
    private final Map<ULong, Long> tableIdToAccountId = new ConcurrentHashMap<>();

    public Migrations(DSLContext source, DSLContext target) {
        this.source = source;
        this.target = target;
        burstNodeService = BurstNodeService.getInstance("https://wallet1.burst-team.us:2083");
        System.err.println("Finding last 10000 blocks for cache");
        List<Integer> firstIndexes = new ArrayList<>();
        for (int i = 0; i < 10000; i += 100) {
            firstIndexes.add(i);
        }
        firstIndexes.parallelStream().forEach(i -> {
            boolean success = false;
            while (!success) {
                try {
                    blocksAtHeights.putAll(Arrays.stream(burstNodeService.getBlocks(i, i + 100)
                            .blockingGet())
                            .collect(Collectors.toMap(Block::getHeight, block -> block)));
                    System.err.println("Fetched " + blocksAtHeights.size() + " blocks.");
                    success = true;
                } catch (Exception ignored) {
                }
            }
        });
    }

    @Override
    public void run() {
        int nConf = 360;
        long start = System.currentTimeMillis();
        System.err.println("Migrating...");
        int highestHeight = migrateWonBlocks();
        migrateMiners();
        migrateMinerDeadlines(highestHeight - nConf);
        System.err.println("Total time: " + (System.currentTimeMillis() - start) + "ms");
    }

    private void migrateMiners() {
        List<MinerWithCapacity> sourceMiners = source.selectFrom(ACCOUNT)
                .fetch()
                .map(account -> new MinerWithCapacity(account, getAccountCapacity(account.getId())));
        BatchBindStep batch = target.batch(target.insertInto(MINERS, MINERS.ACCOUNT_ID, MINERS.PENDING_BALANCE, MINERS.ESTIMATED_CAPACITY, MINERS.SHARE, MINERS.MINIMUM_PAYOUT, MINERS.NAME, MINERS.USER_AGENT).values((Long) null, null, null, null, null, null, null));
        sourceMiners.forEach(miner -> {
            batch.bind(BurstAddress.fromRs(miner.getAccount().getAddress()).getBurstID().getSignedLongId(),
                            (miner.getAccount().getPending()),
                            (((double) miner.getCapacity()) / 1000.0),
                            0d, // TODO Nogrod does not store share?
                            (miner.getAccount().getMinPayoutValue() == null ? 10000000000L : miner.getAccount().getMinPayoutValue()),
                            miner.getAccount().getName(),
                            ""); // TODO Nogrod does not store user agent?
            tableIdToAccountId.put(miner.getAccount().getId(), BurstAddress.fromRs(miner.getAccount().getAddress()).getBurstID().getSignedLongId());
        });
        batch.execute();
        System.err.println("Migrating miners done.");
    }

    private int migrateWonBlocks() {
        List<BlockRecord> blocks = source.selectFrom(BLOCK)
                .where(BLOCK.WINNER_ID.isNotNull())
                .fetch();
        AtomicInteger highestHeight = new AtomicInteger();
        BatchBindStep batch = target.batch(target.insertInto(WON_BLOCKS, WON_BLOCKS.BLOCK_HEIGHT, WON_BLOCKS.BLOCK_ID, WON_BLOCKS.GENERATOR_ID, WON_BLOCKS.NONCE, WON_BLOCKS.FULL_REWARD)
                .values((Long) null, null, null, null, null));
        blocks.forEach(block -> {
            batch.bind(block.getHeight().longValue(),
                            getBlockId(block.getHeight().intValue()),
                            block.getWinnerId() == null ? 0 : block.getWinnerId().longValue(),
                            block.getBestNonceSubmissionId() == null ? "0" : getNonce(block.getBestNonceSubmissionId()),
                            block.getReward());
            synchronized (highestHeight) {
                if (highestHeight.get() <= block.getHeight().longValue()) {
                    highestHeight.set(Math.toIntExact(block.getHeight().longValue()));
                }
            }
        });
        batch.execute();
        target.insertInto(POOL_STATE, POOL_STATE.KEY, POOL_STATE.VALUE)
                .values("lastProcessedBlock", Integer.toString(highestHeight.get()))
                .execute();
        System.err.println("Migrating won blocks done.");
        return highestHeight.get();
    }

    private void migrateMinerDeadlines(int minHeight) {
        List<NonceSubmissionRecord> nonceSubmissions = source.selectFrom(NONCE_SUBMISSION)
                .where(NONCE_SUBMISSION.BLOCK_HEIGHT.ge(ULong.valueOf(minHeight)))
                .fetch();
        BatchBindStep batch = target.batch(target.insertInto(MINER_DEADLINES, MINER_DEADLINES.ACCOUNT_ID, MINER_DEADLINES.HEIGHT, MINER_DEADLINES.DEADLINE, MINER_DEADLINES.BASE_TARGET).values((Long) null, null, null, null));
        System.err.println("Compiling deadlines...");
        nonceSubmissions.forEach(nonceSubmission -> batch.bind(getMinerAccountIdFromTableId(nonceSubmission.getMinerId()), nonceSubmission.getBlockHeight().longValue(), nonceSubmission.getDeadline().longValue(), getBaseTarget(nonceSubmission.getBlockHeight().intValue())));
        System.err.println("Inserting deadlines...");
        batch.execute();
        System.err.println("Migrating deadlines done.");
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
        return tableIdToAccountId.get(tableId);
    }

    private long getBlockId(int height) {
        if (blocksAtHeights.containsKey(height)) {
            return blocksAtHeights.get(height).getId().getSignedLongId();
        }
        Block block = burstNodeService
                .getBlock(height)
                .blockingGet();
        blocksAtHeights.put(height, block);
        return block.getId().getSignedLongId();
    }

    private long getBaseTarget(int height) {
        if (blocksAtHeights.containsKey(height)) {
            return blocksAtHeights.get(height).getBaseTarget();
        }
        Block block = burstNodeService
                .getBlock(height)
                .blockingGet();
        blocksAtHeights.put(height, block);
        return block.getBaseTarget();
    }
}
