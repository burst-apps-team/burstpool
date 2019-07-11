package burst.pool.storage.persistent;

import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstID;
import burst.kit.entity.BurstValue;
import burst.kit.service.BurstNodeService;
import burst.pool.db.tables.records.MinersRecord;
import burst.pool.entity.Payout;
import burst.pool.entity.WonBlock;
import burst.pool.miners.Deadline;
import burst.pool.miners.Miner;
import burst.pool.miners.MinerMaths;
import burst.pool.miners.PoolFeeRecipient;
import burst.pool.pool.StoredSubmission;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.Props;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.JDBCUtils;
import org.mariadb.jdbc.MariaDbDataSource;
import org.mariadb.jdbc.UrlParser;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static burst.pool.db.tables.BestSubmissions.BEST_SUBMISSIONS;
import static burst.pool.db.tables.MinerDeadlines.MINER_DEADLINES;
import static burst.pool.db.tables.Miners.MINERS;
import static burst.pool.db.tables.Payouts.PAYOUTS;
import static burst.pool.db.tables.PoolState.POOL_STATE;
import static burst.pool.db.tables.WonBlocks.WON_BLOCKS;

public class DbStorageService implements StorageService {

    private static final String POOL_STATE_FEE_RECIPIENT_BALANCE = "feeRecipientBalance";
    private static final String POOL_STATE_LAST_PROCESSED_BLOCK = "lastProcessedBlock";

    private final PropertyService propertyService;
    private final MinerMaths minerMaths;
    private final BurstNodeService burstNodeService;

    private final ThreadLocal<Connection> localConnection = new ThreadLocal<>();

    private final int nMin;

    private final Settings settings;
    private final HikariDataSource connectionPool;
    private final SQLDialect sqlDialect;

    private final CacheManager cacheManager;
    private final Map<Table<?>, Semaphore> cacheLocks;

    public DbStorageService(PropertyService propertyService, MinerMaths minerMaths, BurstNodeService burstNodeService) throws SQLException, FlywayException {
        String url = propertyService.getString(Props.dbUrl);
        String username = propertyService.getString(Props.dbUsername);
        String password = propertyService.getString(Props.dbPassword);
        this.propertyService = propertyService;
        this.minerMaths = minerMaths;
        this.burstNodeService = burstNodeService;

        nMin = propertyService.getInt(Props.nMin);

        String driverClass = JDBCUtils.driver(url);
        sqlDialect = JDBCUtils.dialect(url);
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            throw new SQLException("Could not find SQL driver: " + driverClass + ". If you want to use this Database type, please check if it is supported by JDBC and jOOQ, and then add the driver to the classpath.");
        }

        Flyway flyway = sqlDialect == SQLDialect.MARIADB ? flywayHack(Flyway.configure(), url, username, password).load() : Flyway.configure().dataSource(url, username, password).load();
        flyway.migrate();
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(url);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(32);
        hikariConfig.setAutoCommit(true);

        settings = new Settings();
        settings.setRenderSchema(false);
        connectionPool = new HikariDataSource(hikariConfig);

        Table<?>[] tables = new Table[]{BEST_SUBMISSIONS, MINER_DEADLINES, MINERS, POOL_STATE};
        cacheLocks = new HashMap<>();
        CacheManagerBuilder cacheManagerBuilder = CacheManagerBuilder.newCacheManagerBuilder();
        CacheConfiguration<String, Object> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, Object.class, ResourcePoolsBuilder.heap(1024 * 1024).build()).build();
        for (Table<?> table : tables) {
            cacheManagerBuilder = cacheManagerBuilder.withCache(table.getName(), cacheConfiguration);
            cacheLocks.put(table, new Semaphore(1));
        }
        this.cacheManager = cacheManagerBuilder.build(true);
    }

    protected DSLContext getDslContext() {
        Connection connection = localConnection.get();
        if (connection == null) {
            return DSL.using(connectionPool, sqlDialect, settings);
        } else {
            return DSL.using(connection, sqlDialect, settings);
        }
    }

    private <T> T useDslContext(Function<DSLContext, T> function) {
        try (DSLContext context = getDslContext()) {
            return function.apply(context);
        }
    }

    private void useDslContextVoid(Consumer<DSLContext> function) {
        try (DSLContext context = getDslContext()) {
            function.accept(context);
        }
    }

    private static FluentConfiguration flywayHack(FluentConfiguration flywayBuilder, String dbUrl, String dbUsername, String dbPassword) {
        MariaDbDataSource flywayDataSource = new MariaDbDataSource(dbUrl) {
            @Override
            protected synchronized void initialize() throws SQLException {
                super.initialize();
                Properties props = new Properties();
                props.setProperty("user", dbUsername);
                props.setProperty("password", dbPassword);
                props.setProperty("useMysqlMetadata", "true");
                try {
                    Field f = MariaDbDataSource.class.getDeclaredField("urlParser");
                    f.setAccessible(true);
                    f.set(this, UrlParser.parse(dbUrl, props));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        flywayBuilder.dataSource(flywayDataSource); // TODO Remove this hack once a stable version of Flyway has this bug fixed
        return flywayBuilder;
    }

    private <T> T doOnCache(Table<?> table, Function<Cache<String, Object>, T> operation) {
        Semaphore semaphore = cacheLocks.get(table);
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            return null;
        }
        try {
            return operation.apply(cacheManager.getCache(table.getName(), String.class, Object.class));
        } finally {
            semaphore.release();
        }
    }

    private void storeInCache(Table<?> table, String key, Object value) {
        doOnCache(table, cache -> {
            cache.put(key, value);
            return null;
        });
    }

    private void removeFromCache(Table<?> table, String key) {
        doOnCache(table, cache -> {
            cache.remove(key);
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T getFromCacheOr(Table<?> table, String key, Supplier<T> supplier) {
        return doOnCache(table, cache -> (T) Optional.ofNullable(cache.get(key)).orElseGet(() -> {
            T object = supplier.get();
            if (object != null) cache.put(key, object);
            return object;
        }));
    }

    private Miner minerFromRecord(MinersRecord record) {
        return new Miner(minerMaths, propertyService, BurstAddress.fromId(BurstID.fromLong(record.getAccountId())), new DbMinerStore(record.getAccountId()));
    }

    private void resetCache() {
        synchronized (cacheManager) {
            cacheManager.close();
            cacheManager.init();
        }
    }

    @Override
    public StorageService beginTransaction() throws SQLException {
        if (localConnection.get() != null) {
            throw new IllegalStateException("Already in transaction");
        }

        Connection connection = connectionPool.getConnection();
        connection.setAutoCommit(false);
        localConnection.set(connection);

        return this;
    }

    @Override
    public void commitTransaction() throws Exception {
        if (localConnection.get() != null) {
            localConnection.get().commit();
        } else {
            throw new IllegalStateException("Not in transaction");
        }
    }

    @Override
    public void rollbackTransaction() throws Exception {
        if (localConnection.get() != null) {
            localConnection.get().rollback();
            resetCache();
        } else {
            throw new IllegalStateException("Not in transaction");
        }
    }

    private void recalculateMinerCount() { // TODO increment would be faster...
        useDslContextVoid(context -> storeInCache(MINERS, "count", context.selectCount()
                .from(MINERS)
                .fetchOne(0, int.class)));
    }

    @Override
    public int getMinerCount() {
        return getFromCacheOr(MINERS, "count", () -> useDslContext(context -> context.selectCount()
                .from(MINERS)
                .fetchOne(0, int.class)));
    }

    @Override
    public List<Miner> getMiners() {
        return useDslContext(context -> context.select(MINERS.ACCOUNT_ID)
                .from(MINERS)
                .fetch(id -> getMiner(id.get(MINERS.ACCOUNT_ID))));
    }

    @Override
    public List<Miner> getMinersFiltered() {
        return useDslContext(context -> context.select(MINERS.ACCOUNT_ID)
                .from(MINERS)
                .fetch(id -> getMiner(id.get(MINERS.ACCOUNT_ID)))
                .stream()
                .filter(miner -> miner.getNConf() >= nMin)
                .collect(Collectors.toList()));
    }

    @Override
    public Miner getMiner(BurstAddress address) {
        return getMiner(address.getBurstID().getSignedLongId());
    }

    private Miner getMiner(long id) {
        try {
            return getFromCacheOr(MINERS, Long.toUnsignedString(id), () -> useDslContext(context -> context.selectFrom(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(id))
                    .fetchAny(this::minerFromRecord)));
        } catch (NullPointerException e) {
            return null;
        }
    }

    /**
     * Synchronized because...??? TODO!!
     */
    @Override
    public synchronized Miner newMiner(BurstAddress address) {
        // We do not need to add to cache as once inserted getMiner will add to cache
        return useDslContext(context -> {
            if (context.selectCount()
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(address.getBurstID().getSignedLongId()))
                    .fetchOne(0, int.class) > 0) {
                return getMiner(address);
            } else {
                context.insertInto(MINERS, MINERS.ACCOUNT_ID, MINERS.PENDING_BALANCE, MINERS.ESTIMATED_CAPACITY, MINERS.SHARE, MINERS.MINIMUM_PAYOUT, MINERS.NAME, MINERS.USER_AGENT)
                        .values(address.getBurstID().getSignedLongId(), 0L, 0d, 0d, BurstValue.fromBurst(propertyService.getFloat(Props.defaultMinimumPayout)).toPlanck().longValueExact(), "", "")
                        .execute();
                recalculateMinerCount();
                return getMiner(address);
            }
        });
    }

    @Override
    public PoolFeeRecipient getPoolFeeRecipient() {
        return new PoolFeeRecipient(propertyService, new DbFeeRecipientStore());
    }

    private void setLastProcessedBlock(int block) {
        useDslContextVoid(context -> {
            context.mergeInto(POOL_STATE, POOL_STATE.KEY, POOL_STATE.VALUE)
                    .key(POOL_STATE.KEY)
                    .values(POOL_STATE_LAST_PROCESSED_BLOCK, Integer.toString(block))
                    .execute();
            storeInCache(POOL_STATE, POOL_STATE_LAST_PROCESSED_BLOCK, block);
        });
    }

    @Override
    public int getLastProcessedBlock() {
        try {
            return getFromCacheOr(POOL_STATE, POOL_STATE_LAST_PROCESSED_BLOCK, () -> useDslContext(context -> context.select(POOL_STATE.VALUE)
                    .from(POOL_STATE)
                    .where(POOL_STATE.KEY.eq(POOL_STATE_LAST_PROCESSED_BLOCK))
                    .fetchAny(result -> Integer.parseInt(result.get(POOL_STATE.VALUE)))));
        } catch (NullPointerException e) {
            int height = (int) burstNodeService.getMiningInfo().blockingFirst().getHeight() - ((propertyService.getInt(Props.processLag) + propertyService.getInt(Props.nAvg)) * 2);
            if (height < 0) height = 0;
            setLastProcessedBlock(height);
            return height;
        }
    }

    @Override
    public void incrementLastProcessedBlock() {
        int block = getLastProcessedBlock() + 1;
        setLastProcessedBlock(block);
    }

    @Override
    public Map<Long, List<StoredSubmission>> getBestSubmissions() {
        // We don't need to cache as getBestSubmissionForBlock will read from cache
        return useDslContext(context -> context.select(BEST_SUBMISSIONS.HEIGHT)
                .from(BEST_SUBMISSIONS)
                .fetch()
                .stream()
                .distinct()
                .collect(Collectors.toMap(height -> height.get(BEST_SUBMISSIONS.HEIGHT), height -> getBestSubmissionsForBlock(height.get(BEST_SUBMISSIONS.HEIGHT)))));
    }

    @Override
    public List<StoredSubmission> getBestSubmissionsForBlock(long blockHeight) {
        try {
            return getFromCacheOr(BEST_SUBMISSIONS, Long.toString(blockHeight), () -> useDslContext(context -> context.selectFrom(BEST_SUBMISSIONS)
                    .where(BEST_SUBMISSIONS.HEIGHT.eq(blockHeight))
                    .fetch(response -> new StoredSubmission(BurstAddress.fromId(BurstID.fromLong(response.getAccountId())), new BigInteger(response.getNonce()), response.getDeadline()))));
        } catch (NullPointerException e) {
            return null;
        }
    }

    @Override
    public void addBestSubmissionForBlock(long blockHeight, StoredSubmission submission) {
        List<StoredSubmission> submissions = getBestSubmissionsForBlock(blockHeight);
        if (submissions == null) submissions = new ArrayList<>();
        useDslContextVoid(context -> context.insertInto(BEST_SUBMISSIONS, BEST_SUBMISSIONS.HEIGHT, BEST_SUBMISSIONS.ACCOUNT_ID, BEST_SUBMISSIONS.NONCE, BEST_SUBMISSIONS.DEADLINE)
                .values(blockHeight, submission.getMiner().getBurstID().getSignedLongId(), submission.getNonce().toString(), submission.getDeadline())
                .execute());
        submissions.add(submission);
        storeInCache(BEST_SUBMISSIONS, Long.toUnsignedString(blockHeight), submissions);
    }

    @Override
    public void removeBestSubmission(long blockHeight) {
        useDslContextVoid(context -> context.deleteFrom(BEST_SUBMISSIONS)
                .where(BEST_SUBMISSIONS.HEIGHT.eq(blockHeight))
                .execute());
        removeFromCache(BEST_SUBMISSIONS, Long.toString(blockHeight));
    }

    @Override
    public void addWonBlock(WonBlock wonBlock) {
        // Won blocks are not cached. TODO cache!
        useDslContextVoid(context -> context.insertInto(WON_BLOCKS, WON_BLOCKS.BLOCK_HEIGHT, WON_BLOCKS.BLOCK_ID, WON_BLOCKS.GENERATOR_ID, WON_BLOCKS.NONCE, WON_BLOCKS.FULL_REWARD)
            .values((long) wonBlock.getBlockHeight(), wonBlock.getBlockId().getSignedLongId(), wonBlock.getGeneratorId().getBurstID().getSignedLongId(), wonBlock.getNonce().toString(), wonBlock.getFullReward().toPlanck().longValue())
            .execute());
    }

    @Override
    public List<WonBlock> getWonBlocks(int limit) { // TODO cache
        return useDslContext(context -> context.selectFrom(WON_BLOCKS)
                .orderBy(WON_BLOCKS.BLOCK_HEIGHT.desc())
                .limit(limit)
                .fetch(record -> new WonBlock(record.getBlockHeight().intValue(), BurstID.fromLong(record.getBlockId()), BurstAddress.fromId(BurstID.fromLong(record.getGeneratorId())), new BigInteger(record.getNonce()), BurstValue.fromPlanck(record.getFullReward()))));
    }

    @Override
    public void addPayout(Payout payout) {
        // Payouts are not cached. TODO cache them!
        useDslContextVoid(context -> context.insertInto(PAYOUTS, PAYOUTS.TRANSACTION_ID, PAYOUTS.SENDER_PUBLIC_KEY, PAYOUTS.FEE, PAYOUTS.DEADLINE, PAYOUTS.ATTACHMENT)
                .values(payout.getTransactionId().getSignedLongId(), payout.getSenderPublicKey(), payout.getFee().toPlanck().longValue(), (long) payout.getDeadline(), payout.getAttachment())
                .execute());
    }

    @Override
    public void close() throws Exception {
        if (localConnection.get() != null) {
            localConnection.get().close();
            localConnection.set(null);
        } else {
            connectionPool.close();
            cacheManager.close();
        }
    }

    private class DbMinerStore implements MinerStore {
        private final long accountId;
        private final String accountIdStr;

        private DbMinerStore(long accountId) {
            this.accountId = accountId;
            this.accountIdStr = Long.toUnsignedString(accountId);
        }

        private void recalculateCacheDeadlineCount() { // TODO increase / decrease would be faster...
            useDslContextVoid(context -> storeInCache(MINER_DEADLINES, accountIdStr + "dlcount", context.selectCount()
                    .from(MINER_DEADLINES)
                    .where(MINER_DEADLINES.ACCOUNT_ID.eq(accountId))
                    .fetchAny(0, int.class)));
        }

        @Override
        public BurstValue getPendingBalance() {
            return getFromCacheOr(MINERS, accountIdStr + "pending", () -> useDslContext(context -> context.select(MINERS.PENDING_BALANCE)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchAny(record -> BurstValue.fromPlanck(record.get(MINERS.PENDING_BALANCE)))));
        }

        @Override
        public void setPendingBalance(BurstValue pendingBalance) {
            useDslContextVoid(context -> context.update(MINERS)
                    .set(MINERS.PENDING_BALANCE, pendingBalance.toPlanck().longValueExact())
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute());
            storeInCache(MINERS, accountIdStr + "pending", pendingBalance);
        }

        @Override
        public double getEstimatedCapacity() {
            return getFromCacheOr(MINERS, accountIdStr + "estimated", () -> useDslContext(context -> context.select(MINERS.ESTIMATED_CAPACITY)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchAny()
                    .get(MINERS.ESTIMATED_CAPACITY)));
        }

        @Override
        public void setEstimatedCapacity(double estimatedCapacity) {
            useDslContextVoid(context -> context.update(MINERS)
                    .set(MINERS.ESTIMATED_CAPACITY, estimatedCapacity)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute());
            storeInCache(MINERS, accountIdStr + "estimated", estimatedCapacity);
        }

        @Override
        public double getShare() {
            return getFromCacheOr(MINERS, accountIdStr + "share", () -> useDslContext(context -> context.select(MINERS.SHARE)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchAny()
                    .get(MINERS.SHARE)));
        }

        @Override
        public void setShare(double share) {
            useDslContextVoid(context -> context.update(MINERS)
                    .set(MINERS.SHARE, share)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute());
            storeInCache(MINERS, accountIdStr + "share", share);
        }

        @Override
        public BurstValue getMinimumPayout() {
            return getFromCacheOr(MINERS, accountIdStr + "minpayout", () -> useDslContext(context -> context.select(MINERS.MINIMUM_PAYOUT)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchAny(record -> BurstValue.fromPlanck(record.get(MINERS.MINIMUM_PAYOUT)))));
        }

        @Override
        public void setMinimumPayout(BurstValue minimumPayout) {
            useDslContextVoid(context -> context.update(MINERS)
                    .set(MINERS.MINIMUM_PAYOUT, minimumPayout.toPlanck().longValueExact())
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute());
            storeInCache(MINERS, accountIdStr + "minpayout", minimumPayout);
        }

        @Override
        public String getName() {
            return getFromCacheOr(MINERS, accountIdStr + "name", () -> useDslContext(context -> context.select(MINERS.NAME)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchAny()
                    .get(MINERS.NAME)));
        }

        @Override
        public void setName(String name) {
            useDslContextVoid(context -> context.update(MINERS)
                    .set(MINERS.NAME, name)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute());
            storeInCache(MINERS, accountIdStr + "name", name);
        }

        @Override
        public String getUserAgent() {
            return getFromCacheOr(MINERS, accountIdStr + "userAgent", () -> useDslContext(context -> context.select(MINERS.USER_AGENT)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchAny()
                    .get(MINERS.USER_AGENT)));
        }

        @Override
        public void setUserAgent(String userAgent) {
            useDslContextVoid(context -> context.update(MINERS)
                    .set(MINERS.USER_AGENT, userAgent)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute());
            storeInCache(MINERS, accountIdStr + "userAgent", userAgent);
        }

        @Override
        public List<Deadline> getDeadlines() { // TODO cache
            return useDslContext(context -> context.select(MINER_DEADLINES.BASE_TARGET, MINER_DEADLINES.HEIGHT, MINER_DEADLINES.DEADLINE)
                    .from(MINER_DEADLINES)
                    .where(MINER_DEADLINES.ACCOUNT_ID.eq(accountId))
                    .fetch()
                    .map(record -> new Deadline(BigInteger.valueOf(record.get(MINER_DEADLINES.DEADLINE)), BigInteger.valueOf(record.get(MINER_DEADLINES.BASE_TARGET)), record.get(MINER_DEADLINES.HEIGHT))));
        }

        @Override
        public int getDeadlineCount() {
            return getFromCacheOr(MINER_DEADLINES, accountIdStr + "dlcount", () -> useDslContext(context -> context.selectCount()
                    .from(MINER_DEADLINES)
                    .where(MINER_DEADLINES.ACCOUNT_ID.eq(accountId))
                    .fetchAny(0, int.class)));
        }

        @Override
        public void removeDeadline(long height) {
            useDslContextVoid(context -> context.delete(MINER_DEADLINES)
                    .where(MINER_DEADLINES.ACCOUNT_ID.eq(accountId), MINER_DEADLINES.HEIGHT.eq(height))
                    .execute());
            removeFromCache(MINER_DEADLINES, accountIdStr + "deadline" + Long.toString(height));
            recalculateCacheDeadlineCount();
        }

        @Override
        public Deadline getDeadline(long height) {
            try {
                return getFromCacheOr(MINER_DEADLINES, accountIdStr + "deadline" + Long.toString(height), () -> useDslContext(context -> context.select(MINER_DEADLINES.BASE_TARGET, MINER_DEADLINES.HEIGHT, MINER_DEADLINES.DEADLINE)
                        .from(MINER_DEADLINES)
                        .where(MINER_DEADLINES.ACCOUNT_ID.eq(accountId), MINER_DEADLINES.HEIGHT.eq(height))
                        .fetchAny()
                        .map(record -> new Deadline(BigInteger.valueOf(record.get(MINER_DEADLINES.DEADLINE)), BigInteger.valueOf(record.get(MINER_DEADLINES.BASE_TARGET)), height))));
            } catch (NullPointerException e) {
                return null;
            }
        }

        @Override
        public void setOrUpdateDeadline(long height, Deadline deadline) {
            useDslContextVoid(context -> context.mergeInto(MINER_DEADLINES, MINER_DEADLINES.ACCOUNT_ID, MINER_DEADLINES.HEIGHT, MINER_DEADLINES.DEADLINE, MINER_DEADLINES.BASE_TARGET)
                    .key(MINER_DEADLINES.ACCOUNT_ID, MINER_DEADLINES.HEIGHT)
                    .values(accountId, height, deadline.getDeadline().longValue(), deadline.getBaseTarget().longValue())
                    .execute());
            storeInCache(MINER_DEADLINES, accountIdStr + "deadline" + Long.toString(height), deadline);
            recalculateCacheDeadlineCount();
        }
    }

    private final class DbFeeRecipientStore implements MinerStore.FeeRecipientStore {

        @Override
        public BurstValue getPendingBalance() {
            try {
                BurstValue pending = getFromCacheOr(POOL_STATE, POOL_STATE_FEE_RECIPIENT_BALANCE, () -> useDslContext(context -> context.select(POOL_STATE.VALUE)
                        .from(POOL_STATE)
                        .where(POOL_STATE.KEY.eq(POOL_STATE_FEE_RECIPIENT_BALANCE))
                        .fetchAny(record -> BurstValue.fromPlanck(record.get(POOL_STATE.VALUE)))));
                return pending == null ? BurstValue.ZERO : pending;
            } catch (NullPointerException e) {
                return BurstValue.fromPlanck(0);
            }
        }

        @Override
        public void setPendingBalance(BurstValue pending) {
            useDslContextVoid(context -> context.mergeInto(POOL_STATE, POOL_STATE.KEY, POOL_STATE.VALUE)
                    .key(POOL_STATE.KEY)
                    .values(POOL_STATE_FEE_RECIPIENT_BALANCE, pending.toPlanck().toString())
                    .execute());
            storeInCache(POOL_STATE, POOL_STATE_FEE_RECIPIENT_BALANCE, pending);
        }
    }
}
