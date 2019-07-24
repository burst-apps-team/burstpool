import kotlin.browser.window

fun main() {
    window.onload = { PoolClient.onPageLoaded() }
    WebUtil.initModalShowListeners()
    PoolClient.init()
}
