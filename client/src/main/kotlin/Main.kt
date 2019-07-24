import kotlin.browser.window

fun main() {
    window.onload = { PoolClient.onPageLoaded() }
    PoolClient.init()
}
