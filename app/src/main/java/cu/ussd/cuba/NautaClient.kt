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
 * Cliente para el portal cautivo Nauta (secure.etecsa.net:8443).
 * Obtiene tiempo restante real de la cuenta.
 *
 * Flujo típico:
 * 1. Con credenciales → POST EtecsaQueryServlet → parsear tiempo disponible
 * 2. Con sesión activa (ATTRIBUTE_UUID) → GET getLeftTime
 */
object NautaClient {

    private const val TAG = "NautaClient"
    private const val BASE = "https://secure.etecsa.net:8443"
    private const val QUERY = "$BASE/EtecsaQueryServlet"
    private const val TIMEOUT_MS = 12_000

    data class AccountInfo(
        val remainingTime: String,   // "HH:MM:SS" o "MM:SS"
        val remainingSeconds: Long,
        val credit: String? = null,
        val raw: String = ""
    )

    init {
        trustAllSsl()
    }

    /** Consulta tiempo restante con usuario+contraseña (funciona sin estar logueado en WiFi). */
    fun fetchRemainingWithCredentials(username: String, password: String): AccountInfo? {
        if (username.isBlank() || password.isBlank()) return null
        return try {
            // 1) GET página principal para cookies / CSRF
            val getConn = open("$BASE/")
            getConn.requestMethod = "GET"
            val getBody = readBody(getConn)
            val cookies = getConn.headerFields["Set-Cookie"]?.joinToString("; ") { it.split(";")[0] } ?: ""

            val csrf = extractHidden(getBody, "CSRFHW") ?: ""
            val wlan = extractHidden(getBody, "wlanuserip") ?: ""

            // 2) POST a EtecsaQueryServlet (información de usuario)
            val params = buildString {
                append("username=").append(enc(username))
                append("&password=").append(enc(password))
                if (csrf.isNotEmpty()) append("&CSRFHW=").append(enc(csrf))
                if (wlan.isNotEmpty()) append("&wlanuserip=").append(enc(wlan))
            }
            val post = open(QUERY)
            post.requestMethod = "POST"
            post.doOutput = true
            post.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            if (cookies.isNotEmpty()) post.setRequestProperty("Cookie", cookies)
            OutputStreamWriter(post.outputStream).use { it.write(params); it.flush() }
            val body = readBody(post)

            parseAccountPage(body)
        } catch (e: Exception) {
            Log.w(TAG, "fetchRemainingWithCredentials: ${e.message}")
            null
        }
    }

    /**
     * Tiempo restante mientras hay sesión activa en WIFI_ETECSA.
     * Requiere ATTRIBUTE_UUID obtenido al loguearse.
     */
    fun fetchLeftTimeSession(username: String, attributeUuid: String): AccountInfo? {
        if (username.isBlank() || attributeUuid.isBlank()) return null
        return try {
            val url = "$QUERY?op=getLeftTime&username=${enc(username)}&ATTRIBUTE_UUID=${enc(attributeUuid)}"
            val conn = open(url)
            conn.requestMethod = "GET"
            val text = readBody(conn).trim()
            // Respuesta típica: "02:14:24" o similar
            val secs = parseTimeToSeconds(text)
            if (secs >= 0 && text.matches(Regex("[0-9:]+"))) {
                AccountInfo(text, secs, raw = text)
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "fetchLeftTimeSession: ${e.message}")
            null
        }
    }

    /** Variante antigua: op=getLeftTime&op1=username (sin UUID). */
    fun fetchLeftTimeSimple(username: String): AccountInfo? {
        if (username.isBlank()) return null
        return try {
            val url = "$QUERY?op=getLeftTime&op1=${enc(username)}"
            val conn = open(url)
            conn.requestMethod = "GET"
            val text = readBody(conn).trim()
            val secs = parseTimeToSeconds(text)
            if (secs >= 0 && text.matches(Regex("[0-9:]+"))) {
                AccountInfo(text, secs, raw = text)
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "fetchLeftTimeSimple: ${e.message}")
            null
        }
    }

    /**
     * Estrategia combinada:
     * 1. Si hay UUID de sesión → getLeftTime con UUID
     * 2. Si no → consulta con usuario/clave (página de info)
     * 3. Fallback op1 simple
     */
    fun fetchBest(
        username: String,
        password: String,
        attributeUuid: String?
    ): AccountInfo? {
        if (!attributeUuid.isNullOrBlank()) {
            fetchLeftTimeSession(username, attributeUuid)?.let { return it }
        }
        fetchRemainingWithCredentials(username, password)?.let { return it }
        return fetchLeftTimeSimple(username)
    }

    private fun parseAccountPage(html: String): AccountInfo? {
        // Buscar patrones de tiempo: HH:MM:SS o H:MM:SS cerca de "tiempo" / "disponible"
        val timePatterns = listOf(
            Pattern.compile("(?i)(?:tiempo\s*(?:disponible|restante)|available\s*time)[^0-9]{0,40}([0-9]{1,3}:[0-9]{2}:[0-9]{2})"),
            Pattern.compile("(?i)([0-9]{1,3}:[0-9]{2}:[0-9]{2})")
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
        val creditPat = Pattern.compile("(?i)(?:saldo|credit|crédito)[^0-9\\.]{0,30}([0-9]+(?:[.,][0-9]+)?)")
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

    private fun extractHidden(html: String, name: String): String? {
        val p = Pattern.compile(
            "(?i)<input[^>]*name=[\"']?$name[\"']?[^>]*value=[\"']([^\"']*)[\"']" +
                "|<input[^>]*value=[\"']([^\"']*)[\"'][^>]*name=[\"']?$name[\"']?"
        )
        val m = p.matcher(html)
        return if (m.find()) m.group(1) ?: m.group(2) else null
    }

    private fun open(urlStr: String): HttpURLConnection {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
        return conn
    }

    private fun readBody(conn: HttpURLConnection): String {
        val stream = try {
            if (conn.responseCode in 200..399) conn.inputStream else conn.errorStream
        } catch (_: Exception) {
            conn.inputStream
        } ?: return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** ETECSA usa certificados que a menudo fallan la validación estándar. */
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
