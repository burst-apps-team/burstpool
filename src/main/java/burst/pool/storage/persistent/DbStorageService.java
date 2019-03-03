package burst.pool.storage.persistent;

import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstID;
import burst.pool.db.burstpool.tables.records.BestsubmissionsRecord;
import burst.pool.db.burstpool.tables.records.MinersRecord;
import burst.pool.miners.Deadline;
import burst.pool.miners.Miner;
import burst.pool.miners.MinerMaths;
import burst.pool.miners.PoolFeeRecipient;
import burst.pool.pool.StoredSubmission;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.Props;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.JDBCUtils;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static burst.pool.db.burstpool.tables.Bestsubmissions.BESTSUBMISSIONS;
import static burst.pool.db.burstpool.tables.Minerdeadlines.MINERDEADLINES;
import static burst.pool.db.burstpool.tables.Miners.MINERS;
import static burst.pool.db.burstpool.tables.Poolstate.POOLSTATE;

public class DbStorageService implements StorageService {

    private static final String POOLSTATE_FEE_RECIPIENT_BALANCE = "feeRecipientBalance";
    private static final String POOLSTATE_LAST_PROCESSED_BLOCK = "lastProcessedBlock";

    private final PropertyService propertyService;
    private final MinerMaths minerMaths;

    private final HikariDataSource connectionPool;
    private final DSLContext defaultContext;
    private final SQLDialect sqlDialect;

    private final Object newMinerLock = new Object();

    public DbStorageService(PropertyService propertyService, MinerMaths minerMaths) throws SQLException, FlywayException {
        String url = propertyService.getString(Props.dbUrl);
        String username = propertyService.getString(Props.dbUsername);
        String password = propertyService.getString(Props.dbPassword);
        this.propertyService = propertyService;
        this.minerMaths = minerMaths;

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
    }

    DbStorageService(PropertyService propertyService, MinerMaths minerMaths, HikariDataSource connectionPool, DSLContext defaultContext, SQLDialect sqlDialect) {
        this.propertyService = propertyService;
        this.minerMaths = minerMaths;
        this.connectionPool = connectionPool;
        this.defaultContext = defaultContext;
        this.sqlDialect = sqlDialect;
    }

    private Miner minerFromRecord(MinersRecord record) {
        return new Miner(minerMaths, propertyService, BurstAddress.fromId(new BurstID(record.getAccountId())), new DbMinerStore(record.getAccountId()));
    }

    @Override
    public StorageService beginTransaction() throws SQLException {
        return TransactionalDbStorageService.create(propertyService, minerMaths, connectionPool, sqlDialect);
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
    public int getMinerCount() {
        return defaultContext.selectCount()
                .from(MINERS)
                .fetchOne(0, int.class);
    }

    @Override
    public List<Miner> getMiners() {
        return defaultContext.selectFrom(MINERS)
                .fetch(this::minerFromRecord);
    }

    @Override
    public Miner getMiner(BurstAddress address) {
        try {
            return defaultContext.selectFrom(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(address.getBurstID().getSignedLongId()))
                    .fetchAny(this::minerFromRecord);
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
            return defaultContext.select(POOLSTATE.VALUE)
                    .from(POOLSTATE)
                    .where(POOLSTATE.KEY.eq(POOLSTATE_LAST_PROCESSED_BLOCK))
                    .fetchAny(result -> Integer.parseInt(result.get(POOLSTATE.VALUE)));
        } catch (NullPointerException e) {
            return 0;
        }
    }

    @Override
    public void incrementLastProcessedBlock() {
        int lastProcessedBlock = getLastProcessedBlock();
        try {
            defaultContext.insertInto(POOLSTATE, POOLSTATE.KEY, POOLSTATE.VALUE)
                    .values(POOLSTATE_LAST_PROCESSED_BLOCK, Integer.toString(lastProcessedBlock + 1))
                    .execute();
        } catch (DataAccessException e) { // TODO there's gotta be a better way to do this...
            defaultContext.update(POOLSTATE)
                    .set(POOLSTATE.VALUE, Integer.toString(lastProcessedBlock + 1))
                    .where(POOLSTATE.KEY.eq(POOLSTATE_LAST_PROCESSED_BLOCK))
                    .execute();
        }
    }

    @Override
    public Map<Long, StoredSubmission> getBestSubmissions() {
        return defaultContext.selectFrom(BESTSUBMISSIONS)
                .fetch()
                .intoMap(BestsubmissionsRecord::getHeight, record -> new StoredSubmission(BurstAddress.fromId(new BurstID(record.getAccountid())), record.getNonce(), record.getDeadline()));
    }

    @Override
    public StoredSubmission getBestSubmissionForBlock(long blockHeight) {
        try {
            return defaultContext.selectFrom(BESTSUBMISSIONS)
                    .where(BESTSUBMISSIONS.HEIGHT.eq(blockHeight))
                    .fetchAny(response -> new StoredSubmission(BurstAddress.fromId(new BurstID(response.getAccountid())), response.getNonce(), response.getDeadline()));
        } catch (NullPointerException e) {
            return null;
        }
    }

    @Override
    public void setOrUpdateBestSubmissionForBlock(long blockHeight, StoredSubmission submission) {
        if (defaultContext.selectCount()
                .from(BESTSUBMISSIONS)
                .where(BESTSUBMISSIONS.HEIGHT.eq(blockHeight))
                .fetchOne(0, int.class) > 0) {
            defaultContext.update(BESTSUBMISSIONS)
                    .set(BESTSUBMISSIONS.ACCOUNTID, submission.getMiner().getBurstID().getSignedLongId())
                    .set(BESTSUBMISSIONS.NONCE, submission.getNonce())
                    .set(BESTSUBMISSIONS.DEADLINE, submission.getDeadline())
                    .where(BESTSUBMISSIONS.HEIGHT.eq(blockHeight))
                    .execute();
        } else {
            defaultContext.insertInto(BESTSUBMISSIONS, BESTSUBMISSIONS.HEIGHT, BESTSUBMISSIONS.ACCOUNTID, BESTSUBMISSIONS.NONCE, BESTSUBMISSIONS.DEADLINE)
                    .values(blockHeight, submission.getMiner().getBurstID().getSignedLongId(), submission.getNonce(), submission.getDeadline())
                    .execute();
        }
    }

    @Override
    public void removeBestSubmission(long blockHeight) {
        defaultContext.deleteFrom(BESTSUBMISSIONS)
                .where(BESTSUBMISSIONS.HEIGHT.eq(blockHeight))
                .execute();
    }

    @Override
    public void close() throws Exception {
        defaultContext.close();
        connectionPool.close();
    }

    private class DbMinerStore implements MinerStore {
        private final long accountId;

        private DbMinerStore(long accountId) {
            this.accountId = accountId;
        }

        @Override
        public double getPendingBalance() {
            return defaultContext.select(MINERS.PENDING_BALANCE)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchAny()
                    .get(MINERS.PENDING_BALANCE);
        }

        @Override
        public void setPendingBalance(double pendingBalance) {
            defaultContext.update(MINERS)
                    .set(MINERS.PENDING_BALANCE, pendingBalance)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute();
        }

        @Override
        public double getEstimatedCapacity() {
            return defaultContext.select(MINERS.ESTIMATED_CAPACITY)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchAny()
                    .get(MINERS.ESTIMATED_CAPACITY);
        }

        @Override
        public void setEstimatedCapacity(double estimatedCapacity) {
            defaultContext.update(MINERS)
                    .set(MINERS.ESTIMATED_CAPACITY, estimatedCapacity)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute();
        }

        @Override
        public double getShare() {
            return defaultContext.select(MINERS.SHARE)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchAny()
                    .get(MINERS.SHARE);
        }

        @Override
        public void setShare(double share) {
            defaultContext.update(MINERS)
                    .set(MINERS.SHARE, share)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute();
        }

        @Override
        public double getMinimumPayout() {
            return defaultContext.select(MINERS.MINIMUM_PAYOUT)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchAny()
                    .get(MINERS.MINIMUM_PAYOUT);
        }

        @Override
        public void setMinimumPayout(double minimumPayout) {
            defaultContext.update(MINERS)
                    .set(MINERS.MINIMUM_PAYOUT, minimumPayout)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute();
        }

        @Override
        public String getName() {
            return defaultContext.select(MINERS.NAME)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchAny()
                    .get(MINERS.NAME);
        }

        @Override
        public void setName(String name) {
            defaultContext.update(MINERS)
                    .set(MINERS.NAME, name)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute();
        }

        @Override
        public String getUserAgent() {
            return defaultContext.select(MINERS.USER_AGENT)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchAny()
                    .get(MINERS.USER_AGENT);
        }

        @Override
        public void setUserAgent(String userAgent) {
            defaultContext.update(MINERS)
                    .set(MINERS.USER_AGENT, userAgent)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute();
        }

        @Override
        public List<Deadline> getDeadlines() {
            return defaultContext.select(MINERDEADLINES.BASETARGET, MINERDEADLINES.HEIGHT, MINERDEADLINES.DEADLINE)
                    .from(MINERDEADLINES)
                    .where(MINERDEADLINES.ACCOUNT_ID.eq(accountId))
                    .fetch()
                    .map(record -> new Deadline(BigInteger.valueOf(record.get(MINERDEADLINES.DEADLINE)), BigInteger.valueOf(record.get(MINERDEADLINES.BASETARGET)), record.get(MINERDEADLINES.HEIGHT)));
        }

        @Override
        public int getDeadlineCount() {
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
        }

        @Override
        public Deadline getDeadline(long height) {
            try {
                return defaultContext.select(MINERDEADLINES.BASETARGET, MINERDEADLINES.HEIGHT, MINERDEADLINES.DEADLINE)
                        .from(MINERDEADLINES)
                        .where(MINERDEADLINES.ACCOUNT_ID.eq(accountId), MINERDEADLINES.HEIGHT.eq(height))
                        .fetchAny()
                        .map(record -> new Deadline(BigInteger.valueOf(record.get(MINERDEADLINES.DEADLINE)), BigInteger.valueOf(record.get(MINERDEADLINES.BASETARGET)), height));
            } catch (NullPointerException e) {
                return null;
            }
        }

        @Override
        public void setOrUpdateDeadline(long height, Deadline deadline) {
            if (defaultContext.selectCount()
                    .from(MINERDEADLINES)
                    .where(MINERDEADLINES.ACCOUNT_ID.eq(accountId), MINERDEADLINES.HEIGHT.eq(height))
                    .fetchOne(0, int.class) > 0) {
                defaultContext.update(MINERDEADLINES)
                        .set(MINERDEADLINES.DEADLINE, deadline.getDeadline().longValue())
                        .set(MINERDEADLINES.BASETARGET, deadline.getBaseTarget().longValue())
                        .where(MINERDEADLINES.ACCOUNT_ID.eq(accountId), MINERDEADLINES.HEIGHT.eq(height))
                        .execute();
            } else {
                defaultContext.insertInto(MINERDEADLINES, MINERDEADLINES.ACCOUNT_ID, MINERDEADLINES.HEIGHT, MINERDEADLINES.DEADLINE, MINERDEADLINES.BASETARGET)
                        .values(accountId, height, deadline.getDeadline().longValue(), deadline.getBaseTarget().longValue())
                        .execute();
            }
        }
    }

    private final class DbFeeRecipientStore implements MinerStore.FeeRecipientStore {

        @Override
        public double getPendingBalance() {
            try {
                return defaultContext.select(POOLSTATE.VALUE)
                        .from(POOLSTATE)
                        .where(POOLSTATE.KEY.eq(POOLSTATE_FEE_RECIPIENT_BALANCE))
                        .fetchAny(record -> Double.parseDouble(record.get(POOLSTATE.VALUE)));
            } catch (NullPointerException e) {
                return 0;
            }
        }

        @Override
        public void setPendingBalance(double pending) {
            try {
                defaultContext.insertInto(POOLSTATE, POOLSTATE.KEY, POOLSTATE.VALUE)
                        .values(POOLSTATE_FEE_RECIPIENT_BALANCE, Double.toString(pending))
                        .execute();
            } catch (DataAccessException e) { // TODO there's gotta be a better way to do this...
                defaultContext.update(POOLSTATE)
                        .set(POOLSTATE.VALUE, Double.toString(pending))
                        .where(POOLSTATE.KEY.eq(POOLSTATE_FEE_RECIPIENT_BALANCE))
                        .execute();
            }
        }
    }
}
