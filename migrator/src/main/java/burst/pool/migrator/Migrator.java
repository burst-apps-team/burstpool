package burst.pool.migrator;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.JDBCUtils;
import org.mariadb.jdbc.MariaDbDataSource;
import org.mariadb.jdbc.MariaDbPoolDataSource;
import org.mariadb.jdbc.UrlParser;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

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
            FluentConfiguration flywayConfig = Flyway.configure()
                    .locations("classpath:/")
                    .baselineOnMigrate(true);
            Flyway flyway = JDBCUtils.dialect(args[1]) == SQLDialect.MARIADB ? flywayHack(flywayConfig, args[1], args[2], args[3]).load() : flywayConfig.dataSource(args[1], args[2], args[3]).load();
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
