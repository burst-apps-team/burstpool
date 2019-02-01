package burst.pool;

import burst.kit.service.BurstNodeService;
import burst.pool.miners.MinerTracker;
import burst.pool.pool.Pool;
import burst.pool.pool.Server;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.PropertyServiceImpl;
import burst.pool.storage.config.Props;
import fi.iki.elonen.util.ServerRunner;

import java.io.IOException;

public class Launcher {
    public static void main(String[] args) {
        String propertiesFileName = "pool.properties";
        if (args.length > 0) {
            propertiesFileName = args[0];
        }
        PropertyService propertyService = new PropertyServiceImpl(propertiesFileName);
        BurstNodeService nodeService = BurstNodeService.getInstance(propertyService.getString(Props.nodeAddress));
        MinerTracker minerTracker = new MinerTracker(nodeService, propertyService);
        Pool pool = new Pool(nodeService, propertyService, minerTracker);
        Server server = new Server(propertyService, pool);
        ServerRunner.executeInstance(server);
    }
}
