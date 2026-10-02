package cu.ussd.cuba

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.regex.Pattern
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Cliente del portal cautivo Nauta (WIFI_ETECSA / Nauta Hogar).
 * Login y logout reales contra secure.etecsa.net:8443.
 */
object NautaClient {

    private const val TAG = "NautaClient"
    private const val BASE = "https://secure.etecsa.net:8443"
    private const val LOGIN = "$BASE/LoginServlet"
    private const val LOGOUT = "$BASE/LogoutServlet"
    private const val QUERY = "$BASE/EtecsaQueryServlet"
    private const val TIMEOUT_MS = 15_000

    data class AccountInfo(
        val remainingTime: String,
        val remainingSeconds: Long,
        val credit: String? = null,
        val raw: String = ""
    )

    data class Session(
        val username: String,
        val attributeUuid: String,
        val csrfHw: String,
        val wlanUserIp: String,
        val remainingTime: String? = null
    )

    data class Result(
        val ok: Boolean,
        val message: String,
        val session: Session? = null
    )

    init {
        trustAllSsl()
    }

    /** Normaliza usuario: acepta con o sin @nauta.com.cu / @nauta.co.cu */
    fun normalizeUser(raw: String): String {
        val u = raw.trim()
        if (u.isEmpty()) return u
        if (u.contains("@")) return u
        return "$u@nauta.com.cu"
    }

    /**
     * Inicia sesión Nauta en la red actual (WIFI_ETECSA o Nauta Hogar).
     * Debe haber Wi‑Fi con portal cautivo alcanzable.
     */
    fun login(usernameRaw: String, password: String): Result {
        val username = normalizeUser(usernameRaw)
        if (username.isBlank() || password.isBlank()) {
            return Result(false, "Usuario y contraseña requeridos")
        }
        return try {
            val home = get(BASE)
            if (home.code !in 200..399 && home.body.isBlank()) {
                return Result(false, "No se alcanza el portal. Conéctate a WIFI_ETECSA o Nauta Hogar.")
            }
            val csrf = extractHidden(home.body, "CSRFHW") ?: extractParam(home.body, "CSRFHW") ?: ""
            val wlan = extractHidden(home.body, "wlanuserip") ?: extractParam(home.body, "wlanuserip") ?: ""

            val params = linkedMapOf(
                "username" to username,
                "password" to password,
                "wlanuserip" to wlan,
                "CSRFHW" to csrf,
                "lang" to "es_ES"
            )
            val post = postForm(LOGIN, params, home.cookies)
            val body = post.body
            val lower = body.lowercase()

            when {
                lower.contains("ya est") || lower.contains("already connected") ||
                    lower.contains("en uso") ->
                    Result(false, "La cuenta ya está en uso. Desconecta la otra sesión o llama al 118.")
                lower.contains("no tiene saldo") || lower.contains("sin saldo") ||
                    lower.contains("no time") ->
                    Result(false, "Sin saldo / tiempo en la cuenta Nauta.")
                lower.contains("correctos") || lower.contains("incorrect") ||
                    lower.contains("error de autenticación") || lower.contains("credenciales") ->
                    Result(false, "Usuario o contraseña incorrectos.")
                else -> {
                    val uuid = extractUuid(body)
                        ?: extractParam(body, "ATTRIBUTE_UUID")
                        ?: extractUuid(post.url)
                    if (uuid.isNullOrBlank()) {
                        // A veces el login simple sin CSRF también funciona
                        val simple = postForm(LOGIN, mapOf("username" to username, "password" to password), "")
                        val uuid2 = extractUuid(simple.body) ?: extractParam(simple.body, "ATTRIBUTE_UUID")
                        if (uuid2.isNullOrBlank()) {
                            val alert = extractAlert(body) ?: extractAlert(simple.body)
                            Result(false, alert ?: "No se pudo iniciar sesión. ¿Estás en Wi‑Fi Nauta?")
                        } else {
                            val time = tryGetLeftTime(username, uuid2, csrf, wlan)
                            Result(
                                true,
                                "Conectado" + (time?.let { " · $it" } ?: ""),
                                Session(username, uuid2, csrf, wlan, time)
                            )
                        }
                    } else {
                        val time = tryGetLeftTime(username, uuid, csrf, wlan)
                        Result(
                            true,
                            "Conectado" + (time?.let { " · $it" } ?: ""),
                            Session(username, uuid, csrf, wlan, time)
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "login: ${e.message}")
            Result(false, "Error de red: ${e.message ?: "desconocido"}")
        }
    }

    fun logout(
        usernameRaw: String,
        attributeUuid: String,
        csrfHw: String = "",
        wlanUserIp: String = ""
    ): Result {
        val username = normalizeUser(usernameRaw)
        if (username.isBlank() || attributeUuid.isBlank()) {
            return Result(false, "No hay sesión activa guardada")
        }
        return try {
            // Preferir GET como stickNAUTA
            val q = buildString {
                append("?username=").append(enc(username))
                append("&ATTRIBUTE_UUID=").append(enc(attributeUuid))
                if (wlanUserIp.isNotBlank()) append("&wlanuserip=").append(enc(wlanUserIp))
                if (csrfHw.isNotBlank()) append("&CSRFHW=").append(enc(csrfHw))
            }
            val getRes = get("$LOGOUT$q")
            if (getRes.body.contains("SUCCESS", ignoreCase = true) ||
                getRes.code in 200..399
            ) {
                // También intentar POST por compatibilidad
                if (!getRes.body.contains("SUCCESS", ignoreCase = true)) {
                    val postRes = postForm(
                        LOGOUT,
                        mapOf(
                            "username" to username,
                            "ATTRIBUTE_UUID" to attributeUuid,
                            "wlanuserip" to wlanUserIp,
                            "CSRFHW" to csrfHw
                        ),
                        getRes.cookies
                    )
                    if (postRes.body.contains("SUCCESS", ignoreCase = true) ||
                        postRes.code in 200..399
                    ) {
                        return Result(true, "Desconectado")
                    }
                }
                return Result(true, "Desconectado")
            }
            Result(false, "No se pudo desconectar. Prueba de nuevo.")
        } catch (e: Exception) {
            Log.w(TAG, "logout: ${e.message}")
            Result(false, "Error al desconectar: ${e.message ?: "desconocido"}")
        }
    }

    fun fetchRemainingWithCredentials(username: String, password: String): AccountInfo? {
        if (username.isBlank() || password.isBlank()) return null
        return try {
            val home = get(BASE)
            val csrf = extractHidden(home.body, "CSRFHW") ?: ""
            val wlan = extractHidden(home.body, "wlanuserip") ?: ""
            val params = mapOf(
                "username" to normalizeUser(username),
                "password" to password,
                "wlanuserip" to wlan,
                "CSRFHW" to csrf,
                "lang" to "es_ES"
            )
            val post = postForm(QUERY, params, home.cookies)
            parseAccountPage(post.body)
        } catch (e: Exception) {
            Log.w(TAG, "fetchRemainingWithCredentials: ${e.message}")
            null
        }
    }

    fun fetchLeftTimeSession(username: String, attributeUuid: String): AccountInfo? {
        if (username.isBlank() || attributeUuid.isBlank()) return null
        return try {
            val url = "$QUERY?op=getLeftTime&username=${enc(normalizeUser(username))}&ATTRIBUTE_UUID=${enc(attributeUuid)}"
            val text = get(url).body.trim()
            val secs = parseTimeToSeconds(text)
            if (secs >= 0 && text.matches(Regex("[0-9:]+"))) AccountInfo(text, secs, raw = text)
            else null
        } catch (e: Exception) {
            Log.w(TAG, "fetchLeftTimeSession: ${e.message}")
            null
        }
    }

    fun fetchBest(username: String, password: String, attributeUuid: String?): AccountInfo? {
        if (!attributeUuid.isNullOrBlank()) {
            fetchLeftTimeSession(username, attributeUuid)?.let { return it }
        }
        return fetchRemainingWithCredentials(username, password)
    }

    private fun tryGetLeftTime(user: String, uuid: String, csrf: String, wlan: String): String? {
        return try {
            val post = postForm(
                QUERY,
                mapOf(
                    "op" to "getLeftTime",
                    "username" to user,
                    "ATTRIBUTE_UUID" to uuid,
                    "wlanuserip" to wlan,
                    "CSRFHW" to csrf
                ),
                ""
            )
            val t = post.body.trim()
            if (t.matches(Regex("[0-9:]+"))) t else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseAccountPage(html: String): AccountInfo? {
        val timePatterns = listOf(
            Pattern.compile(
                """(?i)(?:tiempo\\s*(?:disponible|restante)|available\\s*time)[^0-9]{0,40}([0-9]{1,3}:[0-9]{2}:[0-9]{2})"""
            ),
            Pattern.compile("""(?i)([0-9]{1,3}:[0-9]{2}:[0-9]{2})""")
        )
        var timeStr: String? = null
        for (p in timePatterns) {
            val m = p.matcher(html)
            if (m.find()) {
                timeStr = m.group(1)
                break
            }
        }
        if (timeStr == null) return null
        val secs = parseTimeToSeconds(timeStr)
        if (secs < 0) return null
        var credit: String? = null
        val creditPat = Pattern.compile(
            """(?i)(?:saldo|credit|crédito)[^0-9.]{0,30}([0-9]+(?:[.,][0-9]+)?)"""
        )
        val cm = creditPat.matcher(html)
        if (cm.find()) credit = cm.group(1)
        return AccountInfo(timeStr, secs, credit, html.take(200))
    }

    fun parseTimeToSeconds(t: String): Long {
        val parts = t.trim().split(":")
        return try {
            when (parts.size) {
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
                2 -> parts[0].toLong() * 60 + parts[1].toLong()
                1 -> parts[0].toLong()
                else -> -1L
            }
        } catch (_: Exception) {
            -1L
        }
    }

    fun formatSeconds(total: Long): String {
        val s = total.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec)
        else String.format("%02d:%02d", m, sec)
    }

    private fun extractUuid(text: String): String? {
        val patterns = listOf(
            Pattern.compile("ATTRIBUTE_UUID=([A-Za-z0-9]+)"),
            Pattern.compile("""ATTRIBUTE_UUID\\s*=\\s*\"([A-Za-z0-9]+)\"""),
            Pattern.compile("""var\\s+urlParam[^\"]*ATTRIBUTE_UUID=([A-Za-z0-9]+)""")
        )
        for (p in patterns) {
            val m = p.matcher(text)
            if (m.find()) return m.group(1)
        }
        return null
    }

    private fun extractAlert(html: String): String? {
        val m = Pattern.compile("""alert\\(\"([^\"]+)\"\\)""").matcher(html)
        return if (m.find()) m.group(1) else null
    }

    private fun extractHidden(html: String, name: String): String? {
        val p = Pattern.compile(
            """(?i)<input[^>]*name=[\"']?$name[\"']?[^>]*value=[\"']([^\"']*)[\"']|<input[^>]*value=[\"']([^\"']*)[\"'][^>]*name=[\"']?$name[\"']?"""
        )
        val m = p.matcher(html)
        return if (m.find()) m.group(1) ?: m.group(2) else null
    }

    private fun extractParam(text: String, name: String): String? {
        val m = Pattern.compile("(?i)$name=([^&\"'\\s]+)").matcher(text)
        return if (m.find()) m.group(1) else null
    }

    private data class HttpResult(val code: Int, val body: String, val cookies: String, val url: String)

    private fun get(urlStr: String): HttpResult {
        val conn = open(urlStr)
        conn.requestMethod = "GET"
        val code = try { conn.responseCode } catch (_: Exception) { -1 }
        val body = readBody(conn)
        val cookies = conn.headerFields["Set-Cookie"]?.joinToString("; ") { it.split(";")[0] } ?: ""
        val url = conn.url?.toString() ?: urlStr
        conn.disconnect()
        return HttpResult(code, body, cookies, url)
    }

    private fun postForm(urlStr: String, params: Map<String, String>, cookies: String): HttpResult {
        val bodyStr = params.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
        val conn = open(urlStr)
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        if (cookies.isNotEmpty()) conn.setRequestProperty("Cookie", cookies)
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(bodyStr); it.flush() }
        val code = try { conn.responseCode } catch (_: Exception) { -1 }
        val body = readBody(conn)
        val newCookies = conn.headerFields["Set-Cookie"]?.joinToString("; ") { it.split(";")[0] }
            ?: cookies
        val url = conn.url?.toString() ?: urlStr
        conn.disconnect()
        return HttpResult(code, body, newCookies, url)
    }

    private fun open(urlStr: String): HttpURLConnection {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.instanceFollowRedirects = true
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
        )
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*")
        return conn
    }

    private fun readBody(conn: HttpURLConnection): String {
        val stream = try {
            if (conn.responseCode in 200..399) conn.inputStream else conn.errorStream
        } catch (_: Exception) {
            try { conn.inputStream } catch (_: Exception) { null }
        } ?: return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun trustAllSsl() {
        try {
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sc = SSLContext.getInstance("TLS")
            sc.init(null, trustAll, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier(HostnameVerifier { _, _ -> true })
        } catch (e: Exception) {
            Log.w(TAG, "trustAllSsl failed: ${e.message}")
        }
    }
}
