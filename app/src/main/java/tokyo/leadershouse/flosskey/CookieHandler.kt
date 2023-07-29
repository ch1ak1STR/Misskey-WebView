package tokyo.leadershouse.flosskey
import android.content.Context
import android.webkit.CookieManager

class CookieHandler(private val context: Context) {
    private lateinit var cookieManager: CookieManager
    fun loadCookies() {
        val sharedPreferences = context.getSharedPreferences("Cookies", Context.MODE_PRIVATE)
        val savedCookies = sharedPreferences.getString("cookies", null)
        savedCookies?.let { cookieManager.setCookie(getMisskeyUrl(), it) }
    }
    fun saveCookies() {
        val cookies = cookieManager.getCookie(getMisskeyUrl())
        val sharedPreferences = context.getSharedPreferences("Cookies", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("cookies", cookies)
        editor.apply()
    }
    fun manageCookie() {
        cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
    }
}