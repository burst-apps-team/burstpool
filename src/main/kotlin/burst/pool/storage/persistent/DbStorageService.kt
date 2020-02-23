package burst.pool.storage.persistent

import burst.kit.entity.BurstAddress
import burst.kit.entity.BurstID
import burst.kit.entity.BurstValue
import burst.kit.service.BurstNodeService
import burst.pool.db.tables.*
import burst.pool.db.tables.records.MinersRecord
import burst.pool.entity.Payout
import burst.pool.entity.WonBlock
import burst.pool.miners.Deadline
import burst.pool.miners.Miner
import burst.pool.miners.MinerMaths
import burst.pool.miners.PoolFeeRecipient
import burst.pool.pool.StoredSubmission
import burst.pool.storage.config.PropertyService
import burst.pool.storage.config.Props
import burst.pool.storage.persistent.MinerStore.FeeRecipientStore
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.ehcache.Cache
import org.ehcache.CacheManager
import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.CacheManagerBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder
import org.flywaydb.core.Flyway
import org.jooq.*
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.JDBCUtils
import org.mariadb.jdbc.MariaDbDataSource
import org.mariadb.jdbc.UrlParser
import java.math.BigInteger
import java.sql.Connection
import java.sql.SQLException
import java.util.*
import java.util.stream.Collectors

class DbStorageService(propertyService: PropertyService, minerMaths: MinerMaths, burstNodeService: BurstNodeService) : StorageService {
    private val propertyService: PropertyService
    private val minerMaths: MinerMaths
    private val burstNodeService: BurstNodeService
    private val localConnection = ThreadLocal<Connection?>()
    private val nMin: Int
    private val settings: Settings
    private val connectionPool: HikariDataSource
    private val sqlDialect: SQLDialect
    private val cacheManager: CacheManager
    private val cacheLocks: MutableMap<Table<*>, Any>
    private val dslContext: DSLContext
        get() {
            val connection = localConnection.get()
            return if (connection == null) {
                DSL.using(connectionPool, sqlDialect, settings)
            } else {
                DSL.using(connection, sqlDialect, settings)
            }
        }

    private fun <T> useDslContext(function: (DSLContext) -> T): T {
        dslContext.use { context -> return function(context) }
    }

    private fun useDslContextVoid(function: (DSLContext) -> Unit) {
        dslContext.use { context -> function(context) }
    }

    private inline fun <reified T> doOnCache(table: Table<*>, operation: (Cache<String, T>) -> Unit) {
        synchronized(cacheLocks[table]!!) {
            return operation(cacheManager.getCache(table.name, String::class.java, T::class.java))
        }
    }

    private inline fun <reified T> storeInCache(table: Table<*>, key: String, value: T) {
        doOnCache<T>(table) { cache ->
            cache.put(key, value)
        }
    }

    private inline fun <reified T> removeFromCache(table: Table<*>, key: String) {
        doOnCache<T>(table) { cache ->
            cache.remove(key)
        }
    }

    private inline fun <reified T> getFromCacheOr(table: Table<*>, key: String, lookup: () -> T): T? {
        doOnCache<T>(table) { cache ->
            if (cache.containsKey(key)) return cache.get(key)
            val newValue = lookup() ?: return null
            cache.put(key, newValue)
            return newValue
        }
        return null // TODO this is needed because we're not allowed contracts on class members
    }

    private fun minerFromRecord(record: MinersRecord): Miner {
        return Miner(minerMaths, propertyService, BurstAddress.fromId(BurstID.fromLong(record.accountId)), DbMinerStore(record.accountId))
    }

    private fun resetCache() {
        synchronized(cacheManager) {
            cacheManager.close()
            cacheManager.init()
        }
    }

    @Throws(SQLException::class)
    override fun beginTransaction(): StorageService? {
        check(localConnection.get() == null) { "Already in transaction" }
        val connection = connectionPool.connection
        connection.autoCommit = false
        localConnection.set(connection)
        return this
    }

    @Throws(Exception::class)
    override fun commitTransaction() {
        if (localConnection.get() != null) {
            localConnection.get()!!.commit()
        } else {
            throw IllegalStateException("Not in transaction")
        }
    }

    @Throws(Exception::class)
    override fun rollbackTransaction() {
        if (localConnection.get() != null) {
            localConnection.get()!!.rollback()
            resetCache()
        } else {
            throw IllegalStateException("Not in transaction")
        }
    }

    private fun recalculateMinerCount() { // TODO increment would be faster...
        useDslContextVoid { context ->
            storeInCache(Miners.MINERS, "count", context.selectCount()
                    .from(Miners.MINERS)
                    .fetchOne(0, Int::class.javaPrimitiveType))
        }
    }

    override val minerCount: Int
        get() = getFromCacheOr(Miners.MINERS, "count") {
            useDslContext { context ->
                context.selectCount()
                        .from(Miners.MINERS)
                        .fetchOne(0, Int::class.javaPrimitiveType)
            }
        }!!

    override val miners: List<Miner>
        get() = useDslContext { context ->
            context.select(Miners.MINERS.ACCOUNT_ID)
                    .from(Miners.MINERS)
                    .fetch { id -> getMiner(id.get(Miners.MINERS.ACCOUNT_ID))!! }
        }

    override val minersFiltered: List<Miner>
        get() = useDslContext { context ->
            context.select(Miners.MINERS.ACCOUNT_ID)
                    .from(Miners.MINERS)
                    .fetch { id -> getMiner(id.get(Miners.MINERS.ACCOUNT_ID)) }
                    .filterNotNull()
                    .filter { miner -> miner.nConf >= nMin }
                    .toList()
        }

    override fun getMiner(address: BurstAddress): Miner? {
        return getMiner(address!!.burstID.signedLongId)
    }

    private fun getMiner(id: Long): Miner? {
        return try {
            getFromCacheOr(Miners.MINERS, java.lang.Long.toUnsignedString(id)) {
                useDslContext { context ->
                    context.selectFrom(Miners.MINERS)
                            .where(Miners.MINERS.ACCOUNT_ID.eq(id))
                            .fetchAny { record -> minerFromRecord(record) }
                }
            }
        } catch (e: NullPointerException) {
            null
        }
    }

    /**
     * Synchronized because...??? TODO!!
     */
    @Synchronized
    override fun getOrNewMiner(address: BurstAddress): Miner { // We do not need to add to cache as once inserted getMiner will add to cache
        return useDslContext { context ->
            if (context.selectCount()
                            .from(Miners.MINERS)
                            .where(Miners.MINERS.ACCOUNT_ID.eq(address!!.burstID.signedLongId))
                            .fetchOne(0, Int::class.javaPrimitiveType) > 0) {
                return@useDslContext getMiner(address)
            } else {
                context.insertInto(Miners.MINERS, Miners.MINERS.ACCOUNT_ID, Miners.MINERS.PENDING_BALANCE, Miners.MINERS.ESTIMATED_CAPACITY, Miners.MINERS.SHARE, Miners.MINERS.MINIMUM_PAYOUT, Miners.MINERS.NAME, Miners.MINERS.USER_AGENT)
                        .values(address.burstID.signedLongId, 0L, 0.0, 0.0, BurstValue.fromBurst(propertyService.get(Props.defaultMinimumPayout).toDouble()).toPlanck().longValueExact(), "", "")
                        .execute()
                recalculateMinerCount()
                return@useDslContext getMiner(address)
            }
        }!!
    }

    override val poolFeeRecipient: PoolFeeRecipient
        get() = PoolFeeRecipient(propertyService, DbFeeRecipientStore())

    override var lastProcessedBlock: Int
        get() = try {
            getFromCacheOr(PoolState.POOL_STATE, POOL_STATE_LAST_PROCESSED_BLOCK) {
                useDslContext { context ->
                    context.select(PoolState.POOL_STATE.VALUE)
                            .from(PoolState.POOL_STATE)
                            .where(PoolState.POOL_STATE.KEY.eq(POOL_STATE_LAST_PROCESSED_BLOCK))
                            .fetchAny { result -> result.get(PoolState.POOL_STATE.VALUE).toInt() }
                }
            } ?: 0
        } catch (e: NullPointerException) {
            var height = burstNodeService.miningInfo.blockingFirst().height.toInt() - (propertyService.get(Props.processLag) + propertyService.get(Props.nAvg)) * 2
            if (height < 0) height = 0
            lastProcessedBlock = height
            height
        }
        private set(block) {
            useDslContextVoid { context ->
                context.mergeInto(PoolState.POOL_STATE, PoolState.POOL_STATE.KEY, PoolState.POOL_STATE.VALUE)
                        .key(PoolState.POOL_STATE.KEY)
                        .values(POOL_STATE_LAST_PROCESSED_BLOCK, Integer.toString(block))
                        .execute()
                storeInCache(PoolState.POOL_STATE, POOL_STATE_LAST_PROCESSED_BLOCK, block)
            }
        }

    override fun incrementLastProcessedBlock() {
        val block = lastProcessedBlock + 1
        lastProcessedBlock = block
    }

    // We don't need to cache as getBestSubmissionForBlock will read from cache
    override val bestSubmissions: Map<Long, List<StoredSubmission>>
        get() =// We don't need to cache as getBestSubmissionForBlock will read from cache
            useDslContext { context ->
                context.select(BestSubmissions.BEST_SUBMISSIONS.HEIGHT)
                        .from(BestSubmissions.BEST_SUBMISSIONS)
                        .fetch()
                        .stream()
                        .distinct()
                        .collect(Collectors.toMap({ height -> height.get(BestSubmissions.BEST_SUBMISSIONS.HEIGHT) }) { height -> getBestSubmissionsForBlock(height.get(BestSubmissions.BEST_SUBMISSIONS.HEIGHT)) })
            }

    override fun getBestSubmissionsForBlock(blockHeight: Long): List<StoredSubmission> {
        return try {
            getFromCacheOr(BestSubmissions.BEST_SUBMISSIONS, blockHeight.toString()) {
                useDslContext { context ->
                    context.selectFrom(BestSubmissions.BEST_SUBMISSIONS)
                            .where(BestSubmissions.BEST_SUBMISSIONS.HEIGHT.eq(blockHeight))
                            .fetch { response -> StoredSubmission(BurstAddress.fromId(BurstID.fromLong(response.accountId)), BigInteger(response.nonce), response.deadline) }
                }
            } ?: emptyList()
        } catch (e: NullPointerException) {
            emptyList()
        }
    }

    override fun addBestSubmissionForBlock(blockHeight: Long, submission: StoredSubmission) {
        val submissions = getBestSubmissionsForBlock(blockHeight).toMutableList()
        useDslContextVoid { context ->
            context.insertInto(BestSubmissions.BEST_SUBMISSIONS, BestSubmissions.BEST_SUBMISSIONS.HEIGHT, BestSubmissions.BEST_SUBMISSIONS.ACCOUNT_ID, BestSubmissions.BEST_SUBMISSIONS.NONCE, BestSubmissions.BEST_SUBMISSIONS.DEADLINE)
                    .values(blockHeight, submission!!.miner.burstID.signedLongId, submission.nonce.toString(), submission.deadline)
                    .execute()
        }
        submissions.add(submission)
        storeInCache(BestSubmissions.BEST_SUBMISSIONS, java.lang.Long.toUnsignedString(blockHeight), submissions)
    }

    override fun removeBestSubmission(blockHeight: Long) {
        useDslContextVoid { context ->
            context.deleteFrom(BestSubmissions.BEST_SUBMISSIONS)
                    .where(BestSubmissions.BEST_SUBMISSIONS.HEIGHT.eq(blockHeight))
                    .execute()
        }
        removeFromCache<StoredSubmission>(BestSubmissions.BEST_SUBMISSIONS, blockHeight.toString())
    }

    override fun addWonBlock(wonBlock: WonBlock) { // Won blocks are not cached. TODO cache!
        useDslContextVoid { context ->
            context.insertInto(WonBlocks.WON_BLOCKS, WonBlocks.WON_BLOCKS.BLOCK_HEIGHT, WonBlocks.WON_BLOCKS.BLOCK_ID, WonBlocks.WON_BLOCKS.GENERATOR_ID, WonBlocks.WON_BLOCKS.NONCE, WonBlocks.WON_BLOCKS.FULL_REWARD)
                    .values(wonBlock!!.blockHeight.toLong(), wonBlock.blockId.signedLongId, wonBlock.generatorId.burstID.signedLongId, wonBlock.nonce.toString(), wonBlock.fullReward.toPlanck().toLong())
                    .execute()
        }
    }

    override fun getWonBlocks(limit: Int): List<WonBlock> { // TODO cache
        return useDslContext { context ->
            context.selectFrom(WonBlocks.WON_BLOCKS)
                    .orderBy(WonBlocks.WON_BLOCKS.BLOCK_HEIGHT.desc())
                    .limit(limit)
                    .fetch { record -> WonBlock(record.blockHeight.toInt(), BurstID.fromLong(record.blockId), BurstAddress.fromId(BurstID.fromLong(record.generatorId)), BigInteger(record.nonce), BurstValue.fromPlanck(record.fullReward)) }
        }
    }

    override fun addPayout(payout: Payout) { // Payouts are not cached. TODO cache them!
        useDslContextVoid { context ->
            context.insertInto(Payouts.PAYOUTS, Payouts.PAYOUTS.TRANSACTION_ID, Payouts.PAYOUTS.SENDER_PUBLIC_KEY, Payouts.PAYOUTS.FEE, Payouts.PAYOUTS.DEADLINE, Payouts.PAYOUTS.ATTACHMENT)
                    .values(payout.transactionId.signedLongId, payout.senderPublicKey, payout.fee.toPlanck().toLong(), payout.deadline.toLong(), payout.attachment)
                    .execute()
        }
    }

    override fun close() {
        if (localConnection.get() != null) {
            localConnection.get()!!.close()
            localConnection.set(null)
        } else {
            connectionPool.close()
            cacheManager.close()
        }
    }

    private inner class DbMinerStore(private val accountId: Long) : MinerStore {
        private val accountIdStr = java.lang.Long.toUnsignedString(accountId)

        private fun recalculateCacheDeadlineCount() { // TODO increase / decrease would be faster...
            useDslContextVoid { context ->
                storeInCache(MinerDeadlines.MINER_DEADLINES, accountIdStr + "dlcount", context.selectCount()
                        .from(MinerDeadlines.MINER_DEADLINES)
                        .where(MinerDeadlines.MINER_DEADLINES.ACCOUNT_ID.eq(accountId))
                        .fetchAny(0, Int::class.javaPrimitiveType))
            }
        }

        override var pendingBalance: BurstValue
            get() = getFromCacheOr(Miners.MINERS, accountIdStr + "pending") {
                useDslContext { context ->
                    context.select(Miners.MINERS.PENDING_BALANCE)
                            .from(Miners.MINERS)
                            .where(Miners.MINERS.ACCOUNT_ID.eq(accountId))
                            .fetchAny { record -> BurstValue.fromPlanck(record.get(Miners.MINERS.PENDING_BALANCE)) }
                }
            } ?: BurstValue.ZERO
            set(pendingBalance) {
                useDslContextVoid { context ->
                    context.update(Miners.MINERS)
                            .set(Miners.MINERS.PENDING_BALANCE, pendingBalance.toPlanck().longValueExact())
                            .where(Miners.MINERS.ACCOUNT_ID.eq(accountId))
                            .execute()
                }
                storeInCache(Miners.MINERS, accountIdStr + "pending", pendingBalance)
            }

        override var estimatedCapacity: Double
            get() = getFromCacheOr(Miners.MINERS, accountIdStr + "estimated") {
                useDslContext { context ->
                    context.select(Miners.MINERS.ESTIMATED_CAPACITY)
                            .from(Miners.MINERS)
                            .where(Miners.MINERS.ACCOUNT_ID.eq(accountId))
                            .fetchAny()
                            .get(Miners.MINERS.ESTIMATED_CAPACITY)
                }
            } ?: 0.0
            set(estimatedCapacity) {
                useDslContextVoid { context ->
                    context.update(Miners.MINERS)
                            .set(Miners.MINERS.ESTIMATED_CAPACITY, estimatedCapacity)
                            .where(Miners.MINERS.ACCOUNT_ID.eq(accountId))
                            .execute()
                }
                storeInCache(Miners.MINERS, accountIdStr + "estimated", estimatedCapacity)
            }

        override var share: Double
            get() = getFromCacheOr(Miners.MINERS, accountIdStr + "share") {
                useDslContext { context ->
                    context.select(Miners.MINERS.SHARE)
                            .from(Miners.MINERS)
                            .where(Miners.MINERS.ACCOUNT_ID.eq(accountId))
                            .fetchAny()
                            .get(Miners.MINERS.SHARE)
                }
            } ?: 0.0
            set(share) {
                useDslContextVoid { context ->
                    context.update(Miners.MINERS)
                            .set(Miners.MINERS.SHARE, share)
                            .where(Miners.MINERS.ACCOUNT_ID.eq(accountId))
                            .execute()
                }
                storeInCache(Miners.MINERS, accountIdStr + "share", share)
            }

        override var minimumPayout: BurstValue
            get() = getFromCacheOr(Miners.MINERS, accountIdStr + "minpayout") {
                useDslContext { context ->
                    context.select(Miners.MINERS.MINIMUM_PAYOUT)
                            .from(Miners.MINERS)
                            .where(Miners.MINERS.ACCOUNT_ID.eq(accountId))
                            .fetchAny { record -> BurstValue.fromPlanck(record.get(Miners.MINERS.MINIMUM_PAYOUT)) }
                }
            } ?: BurstValue.fromBurst(propertyService.get(Props.defaultMinimumPayout).toDouble())
            set(minimumPayout) {
                useDslContextVoid { context ->
                    context.update(Miners.MINERS)
                            .set(Miners.MINERS.MINIMUM_PAYOUT, minimumPayout.toPlanck().longValueExact())
                            .where(Miners.MINERS.ACCOUNT_ID.eq(accountId))
                            .execute()
                }
                storeInCache(Miners.MINERS, accountIdStr + "minpayout", minimumPayout)
            }

        override var name: String?
            get() = getFromCacheOr(Miners.MINERS, accountIdStr + "name") {
                useDslContext { context ->
                    context.select(Miners.MINERS.NAME)
                            .from(Miners.MINERS)
                            .where(Miners.MINERS.ACCOUNT_ID.eq(accountId))
                            .fetchAny()
                            .get(Miners.MINERS.NAME)
                }
            }
            set(name) {
                useDslContextVoid { context ->
                    context.update(Miners.MINERS)
                            .set(Miners.MINERS.NAME, name)
                            .where(Miners.MINERS.ACCOUNT_ID.eq(accountId))
                            .execute()
                }
                storeInCache(Miners.MINERS, accountIdStr + "name", name)
            }

        override var userAgent: String?
            get() = getFromCacheOr(Miners.MINERS, accountIdStr + "userAgent") {
                useDslContext { context ->
                    context.select(Miners.MINERS.USER_AGENT)
                            .from(Miners.MINERS)
                            .where(Miners.MINERS.ACCOUNT_ID.eq(accountId))
                            .fetchAny()
                            .get(Miners.MINERS.USER_AGENT)
                }
            }
            set(userAgent) {
                useDslContextVoid { context ->
                    context.update(Miners.MINERS)
                            .set(Miners.MINERS.USER_AGENT, userAgent)
                            .where(Miners.MINERS.ACCOUNT_ID.eq(accountId))
                            .execute()
                }
                storeInCache(Miners.MINERS, accountIdStr + "userAgent", userAgent)
            }

        // TODO cache
        override val deadlines: List<Deadline>
            get() =// TODO cache
                useDslContext { context ->
                    context.select(MinerDeadlines.MINER_DEADLINES.BASE_TARGET, MinerDeadlines.MINER_DEADLINES.HEIGHT, MinerDeadlines.MINER_DEADLINES.DEADLINE)
                            .from(MinerDeadlines.MINER_DEADLINES)
                            .where(MinerDeadlines.MINER_DEADLINES.ACCOUNT_ID.eq(accountId))
                            .fetch()
                            .map { record -> Deadline(BigInteger.valueOf(record.get(MinerDeadlines.MINER_DEADLINES.DEADLINE)), BigInteger.valueOf(record.get(MinerDeadlines.MINER_DEADLINES.BASE_TARGET)), record.get(MinerDeadlines.MINER_DEADLINES.HEIGHT)) }
                }

        override val deadlineCount: Int
            get() = getFromCacheOr(MinerDeadlines.MINER_DEADLINES, accountIdStr + "dlcount") {
                useDslContext { context ->
                    context.selectCount()
                            .from(MinerDeadlines.MINER_DEADLINES)
                            .where(MinerDeadlines.MINER_DEADLINES.ACCOUNT_ID.eq(accountId))
                            .fetchAny(0, Int::class.javaPrimitiveType)
                }
            } ?: 0

        override fun removeDeadline(height: Long) {
            useDslContextVoid { context ->
                context.delete(MinerDeadlines.MINER_DEADLINES)
                        .where(MinerDeadlines.MINER_DEADLINES.ACCOUNT_ID.eq(accountId), MinerDeadlines.MINER_DEADLINES.HEIGHT.eq(height))
                        .execute()
            }
            removeFromCache<Deadline>(MinerDeadlines.MINER_DEADLINES, accountIdStr + "deadline" + height.toString())
            recalculateCacheDeadlineCount()
        }

        override fun getDeadline(height: Long): Deadline? {
            return try {
                getFromCacheOr(MinerDeadlines.MINER_DEADLINES, accountIdStr + "deadline" + height.toString()) {
                    useDslContext { context ->
                        context.select(MinerDeadlines.MINER_DEADLINES.BASE_TARGET, MinerDeadlines.MINER_DEADLINES.HEIGHT, MinerDeadlines.MINER_DEADLINES.DEADLINE)
                                .from(MinerDeadlines.MINER_DEADLINES)
                                .where(MinerDeadlines.MINER_DEADLINES.ACCOUNT_ID.eq(accountId), MinerDeadlines.MINER_DEADLINES.HEIGHT.eq(height))
                                .fetchAny()
                                .map { record -> Deadline(BigInteger.valueOf(record.get(MinerDeadlines.MINER_DEADLINES.DEADLINE)), BigInteger.valueOf(record.get(MinerDeadlines.MINER_DEADLINES.BASE_TARGET)), height) }
                    }
                }
            } catch (e: NullPointerException) {
                null
            }
        }

        override fun setOrUpdateDeadline(height: Long, deadline: Deadline) {
            useDslContextVoid { context ->
                context.mergeInto(MinerDeadlines.MINER_DEADLINES, MinerDeadlines.MINER_DEADLINES.ACCOUNT_ID, MinerDeadlines.MINER_DEADLINES.HEIGHT, MinerDeadlines.MINER_DEADLINES.DEADLINE, MinerDeadlines.MINER_DEADLINES.BASE_TARGET)
                        .key(MinerDeadlines.MINER_DEADLINES.ACCOUNT_ID, MinerDeadlines.MINER_DEADLINES.HEIGHT)
                        .values(accountId, height, deadline.deadline.toLong(), deadline.baseTarget.toLong())
                        .execute()
            }
            storeInCache(MinerDeadlines.MINER_DEADLINES, accountIdStr + "deadline" + height.toString(), deadline)
            recalculateCacheDeadlineCount()
        }

    }

    private inner class DbFeeRecipientStore : FeeRecipientStore {
        override var pendingBalance: BurstValue
            get() = try {
                val pending = getFromCacheOr(PoolState.POOL_STATE, POOL_STATE_FEE_RECIPIENT_BALANCE) {
                    useDslContext { context ->
                        context.select(PoolState.POOL_STATE.VALUE)
                                .from(PoolState.POOL_STATE)
                                .where(PoolState.POOL_STATE.KEY.eq(POOL_STATE_FEE_RECIPIENT_BALANCE))
                                .fetchAny { record -> BurstValue.fromPlanck(record.get(PoolState.POOL_STATE.VALUE)) }
                    }
                }
                pending ?: BurstValue.ZERO
            } catch (e: NullPointerException) {
                BurstValue.fromPlanck(0)
            }
            set(pending) {
                useDslContextVoid { context ->
                    context.mergeInto(PoolState.POOL_STATE, PoolState.POOL_STATE.KEY, PoolState.POOL_STATE.VALUE)
                            .key(PoolState.POOL_STATE.KEY)
                            .values(POOL_STATE_FEE_RECIPIENT_BALANCE, pending.toPlanck().toString())
                            .execute()
                }
                storeInCache(PoolState.POOL_STATE, POOL_STATE_FEE_RECIPIENT_BALANCE, pending)
            }

    }

    companion object {
        private const val POOL_STATE_FEE_RECIPIENT_BALANCE = "feeRecipientBalance"
        private const val POOL_STATE_LAST_PROCESSED_BLOCK = "lastProcessedBlock"
    }

    init {
        val url = propertyService.get(Props.dbUrl)
        val username = propertyService.get(Props.dbUsername)
        val password = propertyService.get(Props.dbPassword)
        this.propertyService = propertyService
        this.minerMaths = minerMaths
        this.burstNodeService = burstNodeService
        nMin = propertyService.get(Props.nMin)
        val driverClass = JDBCUtils.driver(url)
        sqlDialect = JDBCUtils.dialect(url)
        try {
            Class.forName(driverClass)
        } catch (e: ClassNotFoundException) {
            throw SQLException("Could not find SQL driver: $driverClass. If you want to use this Database type, please check if it is supported by JDBC and jOOQ, and then add the driver to the classpath.")
        }
        val flywayBuilder = Flyway.configure()
        if (sqlDialect == SQLDialect.MARIADB) {
            val flywayDataSource = object : MariaDbDataSource(url) {
                override fun initialize() {
                    super.initialize()
                    val props = Properties()
                    props.setProperty("user", username)
                    props.setProperty("password", password)
                    props.setProperty("useMysqlMetadata", "true")
                    val f = MariaDbDataSource::class.java.getDeclaredField("urlParser")
                    f.isAccessible = true
                    f.set(this, UrlParser.parse(url, props))
                }
            }
            flywayBuilder.dataSource(flywayDataSource) // TODO Remove this hack once we can use Flyway 6
        } else {
            flywayBuilder.dataSource(url, username, password)
        }
        val flyway = flywayBuilder.load()
        flyway.migrate()
        val hikariConfig = HikariConfig()
        hikariConfig.jdbcUrl = url
        hikariConfig.username = username
        hikariConfig.password = password
        hikariConfig.maximumPoolSize = 32
        hikariConfig.isAutoCommit = true
        settings = Settings()
        settings.isRenderSchema = false
        connectionPool = HikariDataSource(hikariConfig)
        val tables = arrayOf<Table<*>>(BestSubmissions.BEST_SUBMISSIONS, MinerDeadlines.MINER_DEADLINES, Miners.MINERS, PoolState.POOL_STATE)
        cacheLocks = HashMap()
        var cacheManagerBuilder: CacheManagerBuilder<*> = CacheManagerBuilder.newCacheManagerBuilder()
        val cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder(String::class.java, Any::class.java, ResourcePoolsBuilder.heap(1024 * 1024.toLong()).build()).build()
        for (table in tables) {
            cacheManagerBuilder = cacheManagerBuilder.withCache(table.name, cacheConfiguration)
            cacheLocks[table] = Any()
        }
        cacheManager = cacheManagerBuilder.build(true)
    }
}
