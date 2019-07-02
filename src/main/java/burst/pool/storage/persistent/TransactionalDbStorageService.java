package burst.pool.storage.persistent;

import burst.kit.service.BurstNodeService;
import burst.pool.miners.MinerMaths;
import burst.pool.storage.config.PropertyService;
import com.zaxxer.hikari.HikariDataSource;
import org.ehcache.CacheManager;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class TransactionalDbStorageService extends DbStorageService {
    private final HikariDataSource connectionPool;
    private final Connection connection;
    private final Settings settings;
    private final SQLDialect sqlDialect;
    private boolean isClosed = false;

    private TransactionalDbStorageService(PropertyService propertyService, MinerMaths minerMaths, BurstNodeService burstNodeService, Settings settings, HikariDataSource connectionPool, Connection connection, SQLDialect sqlDialect, CacheManager cacheManager, Map<Table<?>, Semaphore> cacheLocks) throws SQLException {
        super(propertyService, minerMaths, burstNodeService, settings, connectionPool, sqlDialect, cacheManager, cacheLocks);
        this.connectionPool = connectionPool;
        this.connection = connection;
        this.settings = settings;
        this.sqlDialect = sqlDialect;
    }

    protected static TransactionalDbStorageService create(PropertyService propertyService, MinerMaths minerMaths, BurstNodeService burstNodeService, Settings settings, HikariDataSource connectionPool, SQLDialect sqlDialect, CacheManager cacheManager, Map<Table<?>, Semaphore> cacheLocks) throws SQLException {
        Connection connection = connectionPool.getConnection();
        connection.setAutoCommit(false);
        return new TransactionalDbStorageService(propertyService, minerMaths, burstNodeService, settings, connectionPool, connection, sqlDialect, cacheManager, cacheLocks);
    }

    @Override
    protected DSLContext getDslContext() {
        return DSL.using(connection, sqlDialect, settings);
    }

    @Override
    public void commitTransaction() throws Exception {
        connection.commit();
    }

    @Override
    public void rollbackTransaction() throws Exception {
        connection.rollback();
    }

    @Override
    public synchronized void close() {
        if (!isClosed) {
            connectionPool.evictConnection(connection);
        }
        isClosed = true;
    }
}
