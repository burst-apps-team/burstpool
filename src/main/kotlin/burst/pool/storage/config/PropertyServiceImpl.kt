package burst.pool.storage.config

import burst.kit.entity.BurstAddress
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import kotlin.reflect.KClass

class PropertyServiceImpl(fileName: String) : PropertyService {
    private val properties: Properties
    private val parsers: Map<KClass<*>, (String) -> Any>

    init {
        val parsers = mutableMapOf<KClass<*>, (String) -> Any>()
        parsers[String::class] = { this.getString(it) }
        parsers[Int::class] = { this.getInt(it) }
        parsers[Boolean::class] = { this.getBoolean(it) }
        parsers[List::class] = { this.getStringList(it) }
        parsers[BurstAddress::class] = { this.getBurstAddress(it) }
        this.parsers = parsers

        properties = Properties()
        try {
            properties.load(FileInputStream(fileName))
        } catch (e: IOException) {
            logger.error("Could not load properties from $fileName", e)
        }
        Props.validateProperties(this)
    }

    // TODO caching
    override operator fun <T> get(prop: Prop<T>): T {
        val value = properties.getProperty(prop.name) ?: return prop.defaultValue
        try {
            parsers.forEach { (type, parser) ->
                if (type.isInstance(prop.defaultValue)) {
                    val parsed = parser(value)
                    if (!type.isInstance(parsed)) {
                        return prop.defaultValue
                    }
                    @Suppress("UNCHECKED_CAST")
                    return parsed as T
                }
            }
        } catch (e: Exception) {
            logger.info("Failed to parse property ${prop.name}, using default value ${prop.defaultValue}")
        }

        return prop.defaultValue
    }

    private fun getBoolean(value: String): Boolean {
        return when {
            value.matches(booleanTrueRegex) -> true
            value.matches(booleanFalseRegex) -> false
            else -> throw IllegalArgumentException()
        }
    }

    private fun getInt(value: String): Int {
        return when {
            value.matches(hexRegex) -> Integer.parseInt(value.replaceFirst("0x", ""), 16)
            value.matches(binaryRegex) -> Integer.parseInt(value.replaceFirst("0b", ""), 2)
            else -> Integer.parseInt(value, 10)
        }
    }

    private fun getString(value: String): String {
        require(value.isNotEmpty()) { "String property must not be empty" }
        return value.trim()
    }

    private fun getStringList(value: String): List<String> {
        return if (value.isEmpty()) {
            emptyList()
        } else {
            value.split(";")
                    .map { element -> element.trim() }
                    .filter { it.isNotEmpty() }
        }
    }

    private fun getBurstAddress(value: String): BurstAddress {
        return BurstAddress.fromEither(value)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PropertyServiceImpl::class.java)

        private val hexRegex = Regex("(?i)^0x[0-9a-fA-F]+$")
        private val binaryRegex = Regex("(?i)^0b[01]+\$")
        private val booleanTrueRegex = Regex("(?i)^1|true|on|yes|active|enabled$")
        private val booleanFalseRegex = Regex("(?i)^0|false|off|no|inactive|disabled$")
    }
}
