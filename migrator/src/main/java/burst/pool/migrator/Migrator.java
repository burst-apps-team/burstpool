package burst.pool.migrator;

import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.JDBCUtils;
import org.mariadb.jdbc.MariaDbDataSource;
import org.mariadb.jdbc.MariaDbPoolDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Migrator {
    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Not enough arguments");
            System.out.println("Usage: java -jar migrator.jar (source url) (target url) (username) (password)");
            return;
        }
        DSLContext source, target;
        try {
            System.err.println("Connecting to source...");
            source = openSourceConnection(args);
            System.err.println("Migrating target schema...");
            Flyway flyway = Flyway.configure()
                    .locations("classpath:/")
                    .dataSource(args[1], args[2], args[3])
                    .baselineOnMigrate(true).load();
            flyway.migrate();
            System.err.println("Connecting to target...");
            target = openTargetConnection(args);
        } catch (SQLException e) {
            System.err.println("Error opening database connection: ");
            e.printStackTrace();
            return;
        }
        new Migrations(source, target).run();
    }

    private static DSLContext openSourceConnection(String[] args) throws SQLException {
        String sourceUrl = args[0];
        String sourceUsername = args[2];
        String sourcePassword = args[3];
        return connect(sourceUrl, sourceUsername, sourcePassword);
    }

    private static DSLContext openTargetConnection(String[] args) throws SQLException {
        String targetUrl = args[1];
        String targetUsername = args[2];
        String targetPassword = args[3];
        return connect(targetUrl, targetUsername, targetPassword);
    }

    private static DSLContext connect(String url, String username, String password) throws SQLException {
        String driverClass = JDBCUtils.driver(url);
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            throw new SQLException("Could not find SQL driver: " + driverClass + ". If you want to use this Database type, please check if it is supported by JDBC and jOOQ, and then add the driver to the classpath.");
        }
        SQLDialect dialect = JDBCUtils.dialect(url);
        Connection connection = DriverManager.getConnection(url, username, password);
        return DSL.using(connection, dialect);
    }
}
