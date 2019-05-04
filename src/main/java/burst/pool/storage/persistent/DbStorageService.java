package burst.pool.storage.persistent;

import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstID;
import burst.pool.db.tables.records.BestsubmissionsRecord;
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
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.JDBCUtils;

import java.math.BigInteger;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static burst.pool.db.tables.Bestsubmissions.BESTSUBMISSIONS;
import static burst.pool.db.tables.Minerdeadlines.MINERDEADLINES;
import static burst.pool.db.tables.Miners.MINERS;
import static burst.pool.db.tables.Payouts.PAYOUTS;
import static burst.pool.db.tables.Poolstate.POOLSTATE;
import static burst.pool.db.tables.Wonblocks.WONBLOCKS;

public class DbStorageService implements StorageService {

    private static final String POOLSTATE_FEE_RECIPIENT_BALANCE = "feeRecipientBalance";
    private static final String POOLSTATE_LAST_PROCESSED_BLOCK = "lastProcessedBlock";

    private final PropertyService propertyService;
    private final MinerMaths minerMaths;

    private final int nMin;

    private final HikariDataSource connectionPool;
    private final DSLContext defaultContext;
    private final SQLDialect sqlDialect;

    private final CacheManager cacheManager;

    private final Object newMinerLock = new Object();

    public DbStorageService(PropertyService propertyService, MinerMaths minerMaths) throws SQLException, FlywayException {
        String url = propertyService.getString(Props.dbUrl);
        String username = propertyService.getString(Props.dbUsername);
        String password = propertyService.getString(Props.dbPassword);
        this.propertyService = propertyService;
        this.minerMaths = minerMaths;

        nMin = propertyService.getInt(Props.nMin);

        String driverClass = JDBCUtils.driver(url);
        sqlDialect = JDBCUtils.dialect(url);
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            throw new SQLException("Could not find SQL driver: " + driverClass + ". If you want to use this Database type, please check if it is supported by JDBC and jOOQ, and then add the driver to the classpath.");
        }

        Flyway flyway = Flyway.configure().dataSource(url, username, password).load();
        flyway.migrate();
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(url);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(32);
        hikariConfig.setAutoCommit(true);

        connectionPool = new HikariDataSource(hikariConfig);
        defaultContext = DSL.using(connectionPool.getConnection(), sqlDialect);

        CacheManagerBuilder cacheManager = CacheManagerBuilder.newCacheManagerBuilder();
        CacheConfiguration<String, Object> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, Object.class, ResourcePoolsBuilder.heap(1024 * 1024).build()).build();
        cacheManager = cacheManager.withCache(BESTSUBMISSIONS.getName(), cacheConfiguration);
        cacheManager = cacheManager.withCache(MINERDEADLINES.getName(), cacheConfiguration);
        cacheManager = cacheManager.withCache(MINERS.getName(), cacheConfiguration);
        cacheManager = cacheManager.withCache(POOLSTATE.getName(), cacheConfiguration);
        this.cacheManager = cacheManager.build(true);
    }

    DbStorageService(PropertyService propertyService, MinerMaths minerMaths, HikariDataSource connectionPool, DSLContext defaultContext, SQLDialect sqlDialect, CacheManager cacheManager) {
        this.propertyService = propertyService;
        this.minerMaths = minerMaths;
        this.connectionPool = connectionPool;
        this.defaultContext = defaultContext;
        this.sqlDialect = sqlDialect;
        this.cacheManager = cacheManager;
        nMin = propertyService.getInt(Props.nMin);
    }

    private Cache<String, Object> getCache(Table<?> table) {
        return cacheManager.getCache(table.getName(), String.class, Object.class);
    }

    private void storeInCache(Table<?> table, String key, Object value) {
        getCache(table).put(key, value);
    }

    private void removeFromCache(Table<?> table, String key) {
        getCache(table).remove(key);
    }

    @SuppressWarnings("unchecked")
    private <T> T getFromCacheOr(Table<?> table, String key, Supplier<T> supplier) {
        return (T) Optional.ofNullable(getCache(table).get(key)).orElseGet(() -> {
            T object = supplier.get();
            getCache(table).put(key, object);
            return object;
        });
    }

    private Miner minerFromRecord(MinersRecord record) {
        return new Miner(minerMaths, propertyService, BurstAddress.fromId(new BurstID(record.getAccountId())), new DbMinerStore(record.getAccountId()));
    }

    @Override
    public StorageService beginTransaction() throws SQLException {
        return TransactionalDbStorageService.create(propertyService, minerMaths, connectionPool, sqlDialect, cacheManager);
    }

    @Override
    public void commitTransaction() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void rollbackTransaction() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMinerCount() { // TODO cache
        return defaultContext.selectCount()
                .from(MINERS)
                .fetchOne(0, int.class);
    }

    @Override
    public List<Miner> getMiners() { // TODO cache
        return defaultContext.selectFrom(MINERS)
                .fetch(this::minerFromRecord)
                .stream()
                .filter(miner -> miner.getNConf() >= nMin)
                .collect(Collectors.toList());
    }

    @Override
    public Miner getMiner(BurstAddress address) {
        try {
            return getFromCacheOr(MINERS, address.getBurstID().getID(), () -> defaultContext.selectFrom(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(address.getBurstID().getSignedLongId()))
                    .fetchAny(this::minerFromRecord));
        } catch (NullPointerException e) {
            return null;
        }
    }

    @Override
    public Miner newMiner(BurstAddress address) {
        synchronized (newMinerLock) {
            if (defaultContext.selectCount()
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(address.getBurstID().getSignedLongId()))
                    .fetchOne(0, int.class) > 0) {
                return getMiner(address);
            } else {
                defaultContext.insertInto(MINERS, MINERS.ACCOUNT_ID, MINERS.PENDING_BALANCE, MINERS.ESTIMATED_CAPACITY, MINERS.SHARE, MINERS.MINIMUM_PAYOUT, MINERS.NAME, MINERS.USER_AGENT)
                        .values(address.getBurstID().getSignedLongId(), 0d, 0d, 0d, (double) propertyService.getFloat(Props.defaultMinimumPayout), "", "")
                        .execute();
                return getMiner(address);
            }
        }
    }

    @Override
    public PoolFeeRecipient getPoolFeeRecipient() {
        return new PoolFeeRecipient(propertyService, new DbFeeRecipientStore());
    }

    @Override
    public int getLastProcessedBlock() {
        try {
            return getFromCacheOr(POOLSTATE, POOLSTATE_LAST_PROCESSED_BLOCK, () -> defaultContext.select(POOLSTATE.VALUE)
                    .from(POOLSTATE)
                    .where(POOLSTATE.KEY.eq(POOLSTATE_LAST_PROCESSED_BLOCK))
                    .fetchAny(result -> Integer.parseInt(result.get(POOLSTATE.VALUE))));
        } catch (NullPointerException e) {
            return 0;
        }
    }

    @Override
    public void incrementLastProcessedBlock() { // TODO cache
        int lastProcessedBlock = getLastProcessedBlock();
        defaultContext.mergeInto(POOLSTATE, POOLSTATE.KEY, POOLSTATE.VALUE)
                .key(POOLSTATE.KEY)
                .values(POOLSTATE_LAST_PROCESSED_BLOCK, Integer.toString(lastProcessedBlock + 1))
                .execute();
    }

    @Override
    public Map<Long, StoredSubmission> getBestSubmissions() { // TODO cache
        return defaultContext.selectFrom(BESTSUBMISSIONS)
                .fetch()
                .intoMap(BestsubmissionsRecord::getHeight, record -> new StoredSubmission(BurstAddress.fromId(new BurstID(record.getAccountid())), record.getNonce(), record.getDeadline()));
    }

    @Override
    public StoredSubmission getBestSubmissionForBlock(long blockHeight) {
        try {
            return getFromCacheOr(BESTSUBMISSIONS, Long.toString(blockHeight), () -> defaultContext.selectFrom(BESTSUBMISSIONS)
                    .where(BESTSUBMISSIONS.HEIGHT.eq(blockHeight))
                    .fetchAny(response -> new StoredSubmission(BurstAddress.fromId(new BurstID(response.getAccountid())), response.getNonce(), response.getDeadline())));
        } catch (NullPointerException e) {
            return null;
        }
    }

    @Override
    public void setOrUpdateBestSubmissionForBlock(long blockHeight, StoredSubmission submission) { // TODO cache
        defaultContext.mergeInto(BESTSUBMISSIONS, BESTSUBMISSIONS.HEIGHT, BESTSUBMISSIONS.ACCOUNTID, BESTSUBMISSIONS.NONCE, BESTSUBMISSIONS.DEADLINE)
                .key(BESTSUBMISSIONS.HEIGHT)
                .values(blockHeight, submission.getMiner().getBurstID().getSignedLongId(), submission.getNonce(), submission.getDeadline())
                .execute();
    }

    @Override
    public void removeBestSubmission(long blockHeight) {
        defaultContext.deleteFrom(BESTSUBMISSIONS)
                .where(BESTSUBMISSIONS.HEIGHT.eq(blockHeight))
                .execute();
        removeFromCache(BESTSUBMISSIONS, Long.toString(blockHeight));
    }

    @Override
    public void addWonBlock(WonBlock wonBlock) {
        // Won blocks are not cached.
        defaultContext.insertInto(WONBLOCKS, WONBLOCKS.BLOCKHEIGHT, WONBLOCKS.BLOCKID, WONBLOCKS.GENERATORID, WONBLOCKS.NONCE, WONBLOCKS.FULLREWARD)
            .values((long) wonBlock.getBlockHeight(), wonBlock.getBlockId().getSignedLongId(), wonBlock.getGeneratorId().getSignedLongId(), wonBlock.getNonce(), Long.parseUnsignedLong(wonBlock.getFullReward().toPlanck()))
            .execute();
    }

    @Override
    public void addPayout(Payout payout) {
        // Payouts are not cached.
        defaultContext.insertInto(PAYOUTS, PAYOUTS.TRANSACTIONID, PAYOUTS.SENDERPUBLICKEY, PAYOUTS.FEE, PAYOUTS.DEADLINE, PAYOUTS.ATTACHMENT)
                .values(payout.getTransactionId().getSignedLongId(), payout.getSenderPublicKey(), Long.parseUnsignedLong(payout.getFee().toPlanck()), (long) payout.getDeadline(), payout.getAttachment())
                .execute();
    }

    @Override
    public void close() throws Exception {
        defaultContext.close();
        connectionPool.close();
        cacheManager.close();
    }

    private class DbMinerStore implements MinerStore {
        private final long accountId;
        private final String accountIdStr;

        private DbMinerStore(long accountId) {
            this.accountId = accountId;
            this.accountIdStr = Long.toUnsignedString(accountId);
        }

        @Override
        public double getPendingBalance() {
            return getFromCacheOr(MINERS, accountIdStr + "pending", () -> defaultContext.select(MINERS.PENDING_BALANCE)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchAny()
                    .get(MINERS.PENDING_BALANCE));
        }

        @Override
        public void setPendingBalance(double pendingBalance) { // TODO cache
            defaultContext.update(MINERS)
                    .set(MINERS.PENDING_BALANCE, pendingBalance)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute();
        }

        @Override
        public double getEstimatedCapacity() {
            return getFromCacheOr(MINERS, accountIdStr + "estimated", () -> defaultContext.select(MINERS.ESTIMATED_CAPACITY)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchAny()
                    .get(MINERS.ESTIMATED_CAPACITY));
        }

        @Override
        public void setEstimatedCapacity(double estimatedCapacity) { // TODO cache
            defaultContext.update(MINERS)
                    .set(MINERS.ESTIMATED_CAPACITY, estimatedCapacity)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute();
        }

        @Override
        public double getShare() {
            return getFromCacheOr(MINERS, accountIdStr + "share", () -> defaultContext.select(MINERS.SHARE)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchAny()
                    .get(MINERS.SHARE));
        }

        @Override
        public void setShare(double share) { // TODO cache
            defaultContext.update(MINERS)
                    .set(MINERS.SHARE, share)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute();
        }

        @Override
        public double getMinimumPayout() {
            return getFromCacheOr(MINERS, accountIdStr + "minpayout", () -> defaultContext.select(MINERS.MINIMUM_PAYOUT)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchAny()
                    .get(MINERS.MINIMUM_PAYOUT));
        }

        @Override
        public void setMinimumPayout(double minimumPayout) { // TODO Cache
            defaultContext.update(MINERS)
                    .set(MINERS.MINIMUM_PAYOUT, minimumPayout)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute();
        }

        @Override
        public String getName() {
            return getFromCacheOr(MINERS, accountIdStr + "name", () -> defaultContext.select(MINERS.NAME)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchAny()
                    .get(MINERS.NAME));
        }

        @Override
        public void setName(String name) { // TODO cache
            defaultContext.update(MINERS)
                    .set(MINERS.NAME, name)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute();
        }

        @Override
        public String getUserAgent() {
            return getFromCacheOr(MINERS, accountIdStr + "userAgent", () -> defaultContext.select(MINERS.USER_AGENT)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchAny()
                    .get(MINERS.USER_AGENT));
        }

        @Override
        public void setUserAgent(String userAgent) { // TODO cache
            defaultContext.update(MINERS)
                    .set(MINERS.USER_AGENT, userAgent)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute();
        }

        @Override
        public List<Deadline> getDeadlines() { // TODO cache
            return defaultContext.select(MINERDEADLINES.BASETARGET, MINERDEADLINES.HEIGHT, MINERDEADLINES.DEADLINE)
                    .from(MINERDEADLINES)
                    .where(MINERDEADLINES.ACCOUNT_ID.eq(accountId))
                    .fetch()
                    .map(record -> new Deadline(BigInteger.valueOf(record.get(MINERDEADLINES.DEADLINE)), BigInteger.valueOf(record.get(MINERDEADLINES.BASETARGET)), record.get(MINERDEADLINES.HEIGHT)));
        }

        @Override
        public int getDeadlineCount() { // TODO cache
            return defaultContext.selectCount()
                    .from(MINERDEADLINES)
                    .where(MINERDEADLINES.ACCOUNT_ID.eq(accountId))
                    .fetchAny(0, int.class);
        }

        @Override
        public void removeDeadline(long height) {
            defaultContext.delete(MINERDEADLINES)
                    .where(MINERDEADLINES.ACCOUNT_ID.eq(accountId), MINERDEADLINES.HEIGHT.eq(height))
                    .execute();
            removeFromCache(MINERDEADLINES, accountIdStr + "deadline" + Long.toString(height));
        }

        @Override
        public Deadline getDeadline(long height) {
            try {
                return getFromCacheOr(MINERDEADLINES, accountIdStr + "deadline" + Long.toString(height), () -> defaultContext.select(MINERDEADLINES.BASETARGET, MINERDEADLINES.HEIGHT, MINERDEADLINES.DEADLINE)
                        .from(MINERDEADLINES)
                        .where(MINERDEADLINES.ACCOUNT_ID.eq(accountId), MINERDEADLINES.HEIGHT.eq(height))
                        .fetchAny()
                        .map(record -> new Deadline(BigInteger.valueOf(record.get(MINERDEADLINES.DEADLINE)), BigInteger.valueOf(record.get(MINERDEADLINES.BASETARGET)), height)));
            } catch (NullPointerException e) {
                return null;
            }
        }

        @Override
        public void setOrUpdateDeadline(long height, Deadline deadline) { // TODO cache
            defaultContext.mergeInto(MINERDEADLINES, MINERDEADLINES.ACCOUNT_ID, MINERDEADLINES.HEIGHT, MINERDEADLINES.DEADLINE, MINERDEADLINES.BASETARGET)
                    .key(MINERDEADLINES.ACCOUNT_ID, MINERDEADLINES.HEIGHT)
                    .values(accountId, height, deadline.getDeadline().longValue(), deadline.getBaseTarget().longValue())
                    .execute();
        }
    }

    private final class DbFeeRecipientStore implements MinerStore.FeeRecipientStore {

        @Override
        public double getPendingBalance() {
            try {
                return getFromCacheOr(POOLSTATE, POOLSTATE_FEE_RECIPIENT_BALANCE, () -> defaultContext.select(POOLSTATE.VALUE)
                        .from(POOLSTATE)
                        .where(POOLSTATE.KEY.eq(POOLSTATE_FEE_RECIPIENT_BALANCE))
                        .fetchAny(record -> Double.parseDouble(record.get(POOLSTATE.VALUE))));
            } catch (NullPointerException e) {
                return 0;
            }
        }

        @Override
        public void setPendingBalance(double pending) { // TODO cache
            defaultContext.mergeInto(POOLSTATE, POOLSTATE.KEY, POOLSTATE.VALUE)
                    .key(POOLSTATE.KEY)
                    .values(POOLSTATE_FEE_RECIPIENT_BALANCE, Double.toString(pending))
                    .execute();
        }
    }
}
