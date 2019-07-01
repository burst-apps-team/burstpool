package burst.pool;

import burst.kit.service.BurstNodeService;
import burst.kit.service.impl.DefaultSchedulerAssigner;
import burst.kit.service.impl.GrpcBurstNodeService;
import burst.pool.miners.MinerMaths;
import burst.pool.miners.MinerTracker;
import burst.pool.pool.Pool;
import burst.pool.pool.Server;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.PropertyServiceImpl;
import burst.pool.storage.config.Props;
import burst.pool.storage.persistent.DbStorageService;
import burst.pool.storage.persistent.StorageService;
import fi.iki.elonen.NanoHTTPD;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;

public class Launcher {
    public static void main(String[] args) { // todo catch exception
        if (System.getProperty("log4j.configurationFile") == null) {
            System.setProperty("log4j.configurationFile", "burstPoolLoggingConfig.xml");
        }
        Logger logger = LoggerFactory.getLogger(Launcher.class);
        String propertiesFileName = "pool.properties";
        if (args.length > 0) {
            propertiesFileName = args[0];
        }
        PropertyService propertyService = new PropertyServiceImpl(propertiesFileName);
        MinerMaths minerMaths = new MinerMaths(propertyService.getInt(Props.nAvg), propertyService.getInt(Props.nMin));
        StorageService storageService = null;
        try {
            storageService = new DbStorageService(propertyService, minerMaths);
        } catch (SQLException | FlywayException e) {
            logger.error("Could not open database connection", e);
            System.exit(-1);
        }
        BurstNodeService nodeService;
        if (propertyService.getBoolean(Props.useGrpcApi)) {
            nodeService = new GrpcBurstNodeService(propertyService.getString(Props.nodeAddress), new DefaultSchedulerAssigner());
        } else {
            nodeService = BurstNodeService.getInstance(propertyService.getString(Props.nodeAddress), Constants.USER_AGENT);
        }
        MinerTracker minerTracker = new MinerTracker(nodeService, propertyService);
        Pool pool = new Pool(nodeService, storageService, propertyService, minerTracker);
        Server server = new Server(storageService, propertyService, pool, minerTracker);
        try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        } catch (IOException e) {
            logger.error("Could not start server", e);
            System.exit(-1);
        }
    }
}
