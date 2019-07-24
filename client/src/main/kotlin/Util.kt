import kotlin.math.floor
import kotlin.math.round

object Util {
    private const val genesisBaseTarget = 4398046511104 / 240

    val colors = arrayOf(
            "#3366CC",
            "#DC3912",
            "#FF9900",
            "#109618",
            "#990099",
            "#3B3EAC",
            "#0099C6",
            "#DD4477",
            "#66AA00",
            "#B82E2E",
            "#316395",
            "#994499",
            "#22AA99",
            "#AAAA11",
            "#6633CC",
            "#E67300",
            "#8B0707",
            "#329262",
            "#5574A6",
            "#3B3EAC"
    )

    internal val entityMap = mapOf(
            "&" to "amp;",
            "<" to "lt;",
            ">" to "gt;",
            "\"" to "quot;",
            "'" to "#39;",
            "/" to "#x2F;",
            "`" to "#x60;",
            "=" to "#x3D;"
    )

    private fun filterTimePart(part: Double, suffix: String): String? {
        return if (part == 0.0) {
            null
        } else {
            part.toString() + suffix
        }
    }

    fun formatTime(secsInt: Int?): String {
        if (secsInt == null || secsInt < 0) return ""
        if (secsInt == 0) return "0s"
        val secs = secsInt.toDouble()
        val years = filterTimePart(floor(secs / 3600 / 24 / 365), "y")
        val days = filterTimePart(floor((secs / 3600 / 24) % 365), "d")
        val hours = filterTimePart(floor((secs / 3600) % 24), "h")
        val minutes = filterTimePart(floor((secs / 60) % 60), "m")
        val seconds = filterTimePart(floor(secs % 60), "s")

        val result = StringBuilder()
        if (years != null) result.append(' ').append(years)
        if (days != null) result.append(' ').append(days)
        if (hours != null) result.append(' ').append(hours)
        if (minutes != null) result.append(' ').append(minutes)
        if (seconds != null) result.append(' ').append(seconds)
        return result.substring(1)
    }

    fun formatBaseTarget(baseTarget: Int): String {
        return formatCapacity(genesisBaseTarget / baseTarget.toDouble())
    }

    fun formatCapacity(capacity: Double): String {
        return capacity.round(3).toString() + " TB"
    }

    fun getAccountExplorerLink(id: String): String {
        return "https://explorer.burstcoin.network/?action=account&account=$id"
    }
}

fun String.escapeHtml(): String {
    return this.replace(Regex("[&<>\"'`=\\\\/]")) { Util.entityMap[it.value] ?: it.value }
}

fun Double.round(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return round(this * multiplier) / multiplier
}
