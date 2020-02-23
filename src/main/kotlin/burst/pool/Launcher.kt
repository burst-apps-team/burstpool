package burst.pool

import burst.kit.service.BurstNodeService
import burst.pool.miners.MinerMaths
import burst.pool.miners.MinerTracker
import burst.pool.payout.BurstPayoutService
import burst.pool.payout.PayoutService
import burst.pool.pool.Pool
import burst.pool.pool.Server
import burst.pool.storage.config.PropertyService
import burst.pool.storage.config.PropertyServiceImpl
import burst.pool.storage.config.Props
import burst.pool.storage.persistent.DbStorageService
import burst.pool.storage.persistent.StorageService
import fi.iki.elonen.NanoHTTPD
import org.flywaydb.core.api.FlywayException
import org.slf4j.LoggerFactory
import java.io.IOException
import java.sql.SQLException
import kotlin.system.exitProcess

object Launcher {
    @JvmStatic
    fun main(args: Array<String>) { // todo catch exception
        if (System.getProperty("log4j.configurationFile") == null) {
            System.setProperty("log4j.configurationFile", "burstPoolLoggingConfig.xml")
        }
        val logger = LoggerFactory.getLogger(Launcher::class.java)
        var propertiesFileName = "pool.properties"
        if (args.isNotEmpty()) {
            propertiesFileName = args[0]
        }
        val propertyService: PropertyService = PropertyServiceImpl(propertiesFileName)
        val minerMaths = MinerMaths(propertyService.get(Props.nAvg), propertyService.get(Props.nMin))
        val nodeService = BurstNodeService.getCompositeInstanceWithUserAgent(Constants.USER_AGENT, *propertyService.get(Props.nodeAddresses).toTypedArray())
        val storageService: StorageService = try {
            DbStorageService(propertyService, minerMaths, nodeService)
        } catch (e: SQLException) {
            logger.error("Could not open database connection", e)
            exitProcess(-1)
        } catch (e: FlywayException) {
            logger.error("Could not open database connection", e)
            exitProcess(-1)
        }
        val minerTracker = MinerTracker(nodeService, propertyService)
        val payoutService: PayoutService = BurstPayoutService(nodeService, propertyService, minerTracker)
        val pool = Pool(nodeService, storageService, propertyService, minerTracker, payoutService)
        val server = Server(storageService, propertyService, pool, minerTracker)
        try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        } catch (e: IOException) {
            logger.error("Could not start server", e)
            exitProcess(-1)
        }
    }
}
