import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.MouseEvent
import org.w3c.fetch.RequestInit
import kotlin.browser.document
import kotlin.browser.window
import kotlin.js.Promise

external fun decodeURIComponent(encodedURI: String): String

object WebUtil {

    fun setCookie(name: String, value: String) {
        document.cookie = "$name=$value;"
    }

    fun getCookie(nameStr: String): String? {
        val name = "$nameStr="
        val decodedCookie = decodeURIComponent(document.cookie)
        val ca = decodedCookie.split(';')
        for (i in 0 until ca.size) {
            var c = ca[i]
            while (c[0] == ' ') {
                c = c.substring(1)
            }
            if (c.indexOf(name) == 0) {
                return c.substring(name.length, c.length)
            }
        }
        return null
    }

    fun <T: Any> fetchJson(address: String, post: Boolean = false): Promise<T?> {
        return window.fetch(address, object: RequestInit {
            override var method: String? = if (post) "POST" else "GET"
        })
                .then { it.json() }
                .then { it.unsafeCast<T>() }
    }

    fun schedule(handler: () -> Unit, repeatEveryMs: Int, runImmediately: Boolean = true) {
        window.setInterval(handler, repeatEveryMs)
        if (runImmediately) handler()
    }
}

fun Element.show() {
    if (this !is HTMLElement) return
    this.style.display = "block"
}

fun Element.hide() {
    if (this !is HTMLElement) return
    this.style.display = "none"
}

var Element.value: String
    get() {
        return if (this !is HTMLInputElement) "" else this.value
    }
    set(value) {
        if (this !is HTMLInputElement) return
        this.value = value
    }

fun Element.click() {
    if (this !is HTMLElement) return
    this.click()
}

var Element.onclick: ((MouseEvent) -> dynamic)?
    get() {
        return if (this !is HTMLElement) null else this.onclick
    }
    set(value) {
        if (this !is HTMLElement) return
        this.onclick = value
    }
