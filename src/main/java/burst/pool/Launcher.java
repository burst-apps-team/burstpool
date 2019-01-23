package burst.pool;

import burst.pool.miners.MinerTracker;
import burst.pool.pool.Pool;
import burst.pool.pool.Server;
import fi.iki.elonen.util.ServerRunner;

public class Launcher {
    public static void main(String[] args) {
        ServerRunner.executeInstance(new Server(new Pool(new MinerTracker())));
    }
}
