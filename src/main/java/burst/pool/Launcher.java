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
import fi.iki.elonen.util.ServerRunner;

import java.sql.SQLException;

public class Launcher {
    public static void main(String[] args) throws SQLException { // todo catch exception
        String propertiesFileName = "pool.properties";
        if (args.length > 0) {
            propertiesFileName = args[0];
        }
        PropertyService propertyService = new PropertyServiceImpl(propertiesFileName);
        MinerMaths minerMaths = new MinerMaths(propertyService.getInt(Props.nAvg), propertyService.getInt(Props.nMin));
        StorageService storageService = new DbStorageService(propertyService, minerMaths);
        BurstNodeService nodeService = BurstNodeService.getInstance(propertyService.getString(Props.nodeAddress));
        MinerTracker minerTracker = new MinerTracker(nodeService, storageService, propertyService);
        Pool pool = new Pool(nodeService, storageService, propertyService, minerTracker);
        Server server = new Server(storageService, propertyService, pool);
        ServerRunner.executeInstance(server);
    }
}
