package burst.pool.storage.persistent;

import burst.pool.miners.MinerMaths;
import burst.pool.storage.config.PropertyService;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.SQLException;

public class TransactionalDbStorageService extends DbStorageService {
    private final Connection connection;
    private final DSLContext dslContext;

    private TransactionalDbStorageService(PropertyService propertyService, MinerMaths minerMaths, HikariDataSource connectionPool, Connection connection, DSLContext context, SQLDialect sqlDialect) throws SQLException {
        super(propertyService, minerMaths, connectionPool, context, sqlDialect);
        this.connection = connection;
        this.dslContext = context;
    }

    protected static TransactionalDbStorageService create(PropertyService propertyService, MinerMaths minerMaths, HikariDataSource connectionPool, SQLDialect sqlDialect) throws SQLException {
        Connection connection = connectionPool.getConnection();
        connection.setAutoCommit(false);
        return new TransactionalDbStorageService(propertyService, minerMaths, connectionPool, connection, DSL.using(connection, sqlDialect), sqlDialect);
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
