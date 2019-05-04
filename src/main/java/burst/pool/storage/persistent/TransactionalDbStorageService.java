package burst.pool.storage.persistent;

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
    private final Connection connection;
    private final DSLContext dslContext;

    private TransactionalDbStorageService(PropertyService propertyService, MinerMaths minerMaths, Settings settings, HikariDataSource connectionPool, Connection connection, DSLContext context, SQLDialect sqlDialect, CacheManager cacheManager, Map<Table<?>, Semaphore> cacheLocks) throws SQLException {
        super(propertyService, minerMaths, settings, connectionPool, context, sqlDialect, cacheManager, cacheLocks);
        this.connection = connection;
        this.dslContext = context;
    }

    protected static TransactionalDbStorageService create(PropertyService propertyService, MinerMaths minerMaths, Settings settings, HikariDataSource connectionPool, SQLDialect sqlDialect, CacheManager cacheManager, Map<Table<?>, Semaphore> cacheLocks) throws SQLException {
        Connection connection = connectionPool.getConnection();
        connection.setAutoCommit(false);
        return new TransactionalDbStorageService(propertyService, minerMaths, settings, connectionPool, connection, DSL.using(connection, sqlDialect, settings), sqlDialect, cacheManager, cacheLocks);
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
    public void close() throws Exception {
        dslContext.close();
        connection.close();
    }
}
