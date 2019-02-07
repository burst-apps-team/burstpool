package burst.pool;

import burst.kit.service.BurstNodeService;
import burst.pool.miners.MinerMaths;
import burst.pool.miners.MinerTracker;
import burst.pool.pool.Pool;
import burst.pool.pool.Server;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.PropertyServiceImpl;
import burst.pool.storage.config.Props;
import burst.pool.storage.persistent.DbStorageService;
import burst.pool.storage.persistent.MemoryStorageService;
import burst.pool.storage.persistent.StorageService;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.util.ServerRunner;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;

public class Launcher {
    public static void main(String[] args) { // todo catch exception
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
        //StorageService storageService = new MemoryStorageService(propertyService, minerMaths);
        BurstNodeService nodeService = BurstNodeService.getInstance(propertyService.getString(Props.nodeAddress));
        MinerTracker minerTracker = new MinerTracker(nodeService, storageService, propertyService);
        Pool pool = new Pool(nodeService, storageService, propertyService, minerTracker);
        Server server = new Server(storageService, propertyService, pool);
        try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        } catch (IOException e) {
            logger.error("Could not start server", e);
            System.exit(-1);
        }
    }
}
