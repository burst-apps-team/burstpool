package burst.pool.storage.persistent;

import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstID;
import burst.pool.db.burstpool.tables.records.MinersRecord;
import burst.pool.miners.Deadline;
import burst.pool.miners.Miner;
import burst.pool.miners.MinerMaths;
import burst.pool.miners.PoolFeeRecipient;
import burst.pool.pool.Submission;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.Props;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static burst.pool.db.burstpool.tables.Bestsubmissions.BESTSUBMISSIONS;
import static burst.pool.db.burstpool.tables.Minerdeadlines.MINERDEADLINES;
import static burst.pool.db.burstpool.tables.Miners.MINERS;
import static burst.pool.db.burstpool.tables.Poolstate.POOLSTATE;

public class DbStorageService implements StorageService {

    private static final String POOLSTATE_FEE_RECIPIENT_BALANCE = "feeRecipientBalance";
    private static final String POOLSTATE_LAST_PROCESSED_BLOCK = "lastProcessedBlock";

    private final PropertyService propertyService;
    private final MinerMaths minerMaths;

    private final Connection conn;
    private final DSLContext context;

    public DbStorageService(PropertyService propertyService, MinerMaths minerMaths, String url, String username, String password) throws SQLException {
        this.propertyService = propertyService;
        this.minerMaths = minerMaths;
        Flyway flyway = Flyway.configure().dataSource(url, username, password).load();
        flyway.migrate();
        conn = DriverManager.getConnection(url, username, password);
        context = DSL.using(conn, SQLDialect.H2); // todo close, abstract
    }

    private Miner minerFromRecord(MinersRecord record) {
        return new Miner(minerMaths, propertyService, BurstAddress.fromId(new BurstID(record.getAccountId())), new DbMinerStore(record.getAccountId()));
    }

    @Override
    public int getMinerCount() {
        return context.selectCount()
                .from(MINERS)
                .fetchOne(0, int.class);
    }

    @Override
    public List<Miner> getMiners() {
        return context.selectFrom(MINERS)
                .fetch(this::minerFromRecord);
    }

    @Override
    public Miner getMiner(BurstAddress address) {
        try {
            return context.selectFrom(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(address.getBurstID().getSignedLongId()))
                    .fetchOne(this::minerFromRecord);
        } catch (NullPointerException e) {
            return null;
        }
    }

    @Override
    public Miner newMiner(BurstAddress address) {
        context.insertInto(MINERS, MINERS.ACCOUNT_ID, MINERS.PENDING_BALANCE, MINERS.ESTIMATED_CAPACITY, MINERS.SHARE, MINERS.HITSUM, MINERS.MINIMUM_PAYOUT, MINERS.NAME, MINERS.USER_AGENT)
                .values(address.getBurstID().getSignedLongId(), 0d, 0d, 0d, 0d, (double) propertyService.getFloat(Props.defaultMinimumPayout), "", "")
                .execute();
        return getMiner(address);
    }

    @Override
    public PoolFeeRecipient getPoolFeeRecipient() {
        return new PoolFeeRecipient(propertyService, new DbFeeRecipientStore());
    }

    @Override
    public int getLastProcessedBlock() {
        try {
            return context.select(POOLSTATE.VALUE)
                    .from(POOLSTATE)
                    .where(POOLSTATE.KEY.eq(POOLSTATE_LAST_PROCESSED_BLOCK))
                    .fetchOne(result -> Integer.parseInt(result.get(POOLSTATE.VALUE)));
        } catch (NullPointerException e) {
            return 0;
        }
    }

    @Override
    public void incrementLastProcessedBlock() {
        int lastProcessedBlock = getLastProcessedBlock();
        try {
            context.insertInto(POOLSTATE, POOLSTATE.KEY, POOLSTATE.VALUE)
                    .values(POOLSTATE_LAST_PROCESSED_BLOCK, Integer.toString(lastProcessedBlock + 1))
                    .execute();
        } catch (DataAccessException e) { // TODO there's gotta be a better way to do this...
            context.update(POOLSTATE)
                    .set(POOLSTATE.VALUE, Integer.toString(lastProcessedBlock + 1))
                    .where(POOLSTATE.KEY.eq(POOLSTATE_LAST_PROCESSED_BLOCK))
                    .execute();
        }
    }

    @Override
    public Submission getBestSubmissionForBlock(long blockHeight) {
        try {
            return context.selectFrom(BESTSUBMISSIONS)
                    .where(BESTSUBMISSIONS.HEIGHT.eq(blockHeight))
                    .fetchOne(response -> new Submission(BurstAddress.fromId(new BurstID(response.getAccountid())), response.getNonce()));
        } catch (NullPointerException e) {
            return null;
        }
    }

    @Override
    public void removeBestSubmissionForBlock(long blockHeight) {
        context.deleteFrom(BESTSUBMISSIONS)
                .where(BESTSUBMISSIONS.HEIGHT.eq(blockHeight))
                .execute();
    }

    @Override
    public void setBestSubmissionForBlock(long blockHeight, Submission submission) {
        context.insertInto(BESTSUBMISSIONS, BESTSUBMISSIONS.HEIGHT, BESTSUBMISSIONS.ACCOUNTID, BESTSUBMISSIONS.NONCE)
                .values(blockHeight, submission.getMiner().getBurstID().getSignedLongId(), submission.getNonce())
                .execute();
    }

    private class DbMinerStore implements MinerStore {
        private final long accountId;

        private DbMinerStore(long accountId) {
            this.accountId = accountId;
        }

        @Override
        public double getPendingBalance() {
            return context.select(MINERS.PENDING_BALANCE)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchOne()
                    .get(MINERS.PENDING_BALANCE);
        }

        @Override
        public void setPendingBalance(double pendingBalance) {
            context.update(MINERS)
                    .set(MINERS.PENDING_BALANCE, pendingBalance)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute();
        }

        @Override
        public double getEstimatedCapacity() {
            return context.select(MINERS.ESTIMATED_CAPACITY)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchOne()
                    .get(MINERS.ESTIMATED_CAPACITY);
        }

        @Override
        public void setEstimatedCapacity(double estimatedCapacity) {
            context.update(MINERS)
                    .set(MINERS.ESTIMATED_CAPACITY, estimatedCapacity)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute();
        }

        @Override
        public double getShare() {
            return context.select(MINERS.SHARE)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchOne()
                    .get(MINERS.SHARE);
        }

        @Override
        public void setShare(double share) {
            context.update(MINERS)
                    .set(MINERS.SHARE, share)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute();
        }

        @Override
        public double getHitSum() {
            return context.select(MINERS.HITSUM)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchOne()
                    .get(MINERS.HITSUM);
        }

        @Override
        public void setHitSum(double hitSum) {
            context.update(MINERS)
                    .set(MINERS.HITSUM, hitSum)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute();
        }

        @Override
        public double getMinimumPayout() {
            return context.select(MINERS.MINIMUM_PAYOUT)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchOne()
                    .get(MINERS.MINIMUM_PAYOUT);
        }

        @Override
        public void setMinimumPayout(double minimumPayout) {
            context.update(MINERS)
                    .set(MINERS.MINIMUM_PAYOUT, minimumPayout)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute();
        }

        @Override
        public String getName() {
            return context.select(MINERS.NAME)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchOne()
                    .get(MINERS.NAME);
        }

        @Override
        public void setName(String name) {
            context.update(MINERS)
                    .set(MINERS.NAME, name)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute();
        }

        @Override
        public String getUserAgent() {
            return context.select(MINERS.USER_AGENT)
                    .from(MINERS)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .fetchOne()
                    .get(MINERS.USER_AGENT);
        }

        @Override
        public void setUserAgent(String userAgent) {
            context.update(MINERS)
                    .set(MINERS.USER_AGENT, userAgent)
                    .where(MINERS.ACCOUNT_ID.eq(accountId))
                    .execute();
        }

        @Override
        public List<Deadline> getDeadlines() {
            return context.select(MINERDEADLINES.BASETARGET, MINERDEADLINES.HEIGHT, MINERDEADLINES.DEADLINE)
                    .from(MINERDEADLINES)
                    .where(MINERDEADLINES.ACCOUNT_ID.eq(accountId))
                    .fetch()
                    .map(record -> new Deadline(BigInteger.valueOf(record.get(MINERDEADLINES.DEADLINE)), BigInteger.valueOf(record.get(MINERDEADLINES.BASETARGET)), record.get(MINERDEADLINES.HEIGHT)));
        }

        @Override
        public int getDeadlineCount() {
            return context.selectCount()
                    .from(MINERDEADLINES)
                    .where(MINERDEADLINES.ACCOUNT_ID.eq(accountId))
                    .fetchOne(0, int.class);
        }

        @Override
        public void removeDeadline(long height) {
            context.delete(MINERDEADLINES)
                    .where(MINERDEADLINES.ACCOUNT_ID.eq(accountId), MINERDEADLINES.HEIGHT.eq(height))
                    .execute();
        }

        @Override
        public Deadline getDeadline(long height) {
            try {
                return context.select(MINERDEADLINES.BASETARGET, MINERDEADLINES.HEIGHT, MINERDEADLINES.DEADLINE)
                        .from(MINERDEADLINES)
                        .where(MINERDEADLINES.ACCOUNT_ID.eq(accountId), MINERDEADLINES.HEIGHT.eq(height))
                        .fetchOne()
                        .map(record -> new Deadline(BigInteger.valueOf(record.get(MINERDEADLINES.DEADLINE)), BigInteger.valueOf(record.get(MINERDEADLINES.BASETARGET)), height));
            } catch (NullPointerException e) {
                return null;
            }
        }

        @Override
        public void setDeadline(long height, Deadline deadline) {
            context.insertInto(MINERDEADLINES, MINERDEADLINES.ACCOUNT_ID, MINERDEADLINES.HEIGHT, MINERDEADLINES.DEADLINE, MINERDEADLINES.BASETARGET)
                    .values(accountId, height, deadline.getDeadline().longValue(), deadline.getBaseTarget().longValue())
                    .execute();
        }
    }

    private final class DbFeeRecipientStore implements MinerStore.FeeRecipientStore {

        @Override
        public double getPendingBalance() {
            try {
                return context.select(POOLSTATE.VALUE)
                        .from(POOLSTATE)
                        .where(POOLSTATE.KEY.eq(POOLSTATE_FEE_RECIPIENT_BALANCE))
                        .fetchOne(record -> Double.parseDouble(record.get(POOLSTATE.VALUE)));
            } catch (NullPointerException e) {
                return 0;
            }
        }

        @Override
        public void setPendingBalance(double pending) {
            try {
                context.insertInto(POOLSTATE, POOLSTATE.KEY, POOLSTATE.VALUE)
                        .values(POOLSTATE_FEE_RECIPIENT_BALANCE, Double.toString(pending))
                        .execute();
            } catch (DataAccessException e) { // TODO there's gotta be a better way to do this...
                context.update(POOLSTATE)
                        .set(POOLSTATE.VALUE, Double.toString(pending))
                        .where(POOLSTATE.KEY.eq(POOLSTATE_FEE_RECIPIENT_BALANCE))
                        .execute();
            }
        }
    }
}
