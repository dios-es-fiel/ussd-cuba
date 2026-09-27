package cu.ussd.cuba

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings

object WifiHelper {

    /** Captive / login portals used by WIFI_ETECSA / Nauta */
    val PORTAL_URLS = listOf(
        "http://secure.etecsa.net:8443/",
        "http://1.1.1.1/",
        "https://www.nauta.cu",
        "http://portal.nauta.cu"
    )

    fun currentSsid(ctx: Context): String? {
        return try {
            @Suppress("DEPRECATION")
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo ?: return null
            var ssid = info.ssid ?: return null
            if (ssid == "<unknown ssid>" || ssid == "0x") return null
            if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length - 1)
            }
            if (ssid.isBlank()) null else ssid
        } catch (_: Exception) {
            null
        }
    }

    fun isWifiConnected(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
        }
    }

    fun isEtecsaWifi(ctx: Context): Boolean {
        val ssid = currentSsid(ctx)?.uppercase() ?: return false
        return ssid.contains("ETECSA") || ssid.contains("WIFI_ETECSA") ||
            ssid.contains("NAUTA") || ssid.contains("CUBA")
    }

    fun openPortal(ctx: Context, index: Int = 0) {
        val url = PORTAL_URLS.getOrElse(index) { PORTAL_URLS[0] }
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (_: Exception) {}
    }

    fun openWifiSettings(ctx: Context) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (_: Exception) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (_: Exception) {}
        }
    }

    fun statusText(ctx: Context): String {
        if (!isWifiConnected(ctx)) return "Wi‑Fi desconectado"
        val ssid = currentSsid(ctx) ?: "(SSID oculto)"
        return if (isEtecsaWifi(ctx)) "Conectado a $ssid ✓ ETECSA"
        else "Conectado a $ssid"
    }
}
