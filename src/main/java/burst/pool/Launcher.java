package burst.pool;

import burst.kit.service.BurstNodeService;
import burst.pool.miners.MinerTracker;
import burst.pool.pool.Pool;
import burst.pool.pool.Server;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.PropertyServiceImpl;
import burst.pool.storage.config.Props;
import burst.pool.storage.db.StorageService;
import fi.iki.elonen.util.ServerRunner;

public class Launcher {
    public static void main(String[] args) {
        String propertiesFileName = "pool.properties";
        if (args.length > 0) {
            propertiesFileName = args[0];
        }
        PropertyService propertyService = new PropertyServiceImpl(propertiesFileName);
        StorageService storageService = null;
        BurstNodeService nodeService = BurstNodeService.getInstance(propertyService.getString(Props.nodeAddress));
        MinerTracker minerTracker = new MinerTracker(nodeService, storageService, propertyService);
        Pool pool = new Pool(nodeService, storageService, propertyService, minerTracker);
        Server server = new Server(propertyService, pool);
        ServerRunner.executeInstance(server);
    }
}
