package cu.ussd.cuba

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * Floating overlays:
 * - Remaining session time countdown (user-set duration)
 * - Real-time network speed (download / upload)
 */
class OverlayService : Service() {

    private var wm: WindowManager? = null
    private var timeView: View? = null
    private var speedView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastTs = 0L
    private var sessionEndMs = 0L
    private lateinit var prefs: PrefsHelper

    private val tick = object : Runnable {
        override fun run() {
            updateTime()
            updateSpeed()
            val interval = prefs.getSpeedIntervalMs().coerceIn(500, 5000)
            handler.postDelayed(this, interval.toLong())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsHelper(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(NOTIF_ID, buildFgNotification())
        lastRx = TrafficStats.getTotalRxBytes()
        lastTx = TrafficStats.getTotalTxBytes()
        lastTs = System.currentTimeMillis()
        val remaining = prefs.getSessionRemainingMs()
        if (remaining > 0) sessionEndMs = System.currentTimeMillis() + remaining
        else if (prefs.getShowFloatingTime()) {
            sessionEndMs = System.currentTimeMillis() + prefs.getSessionDurationMin() * 60_000L
            prefs.setSessionEndMs(sessionEndMs)
        }
        applyOverlays()
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REFRESH -> applyOverlays()
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SET_TIME -> {
                val min = intent.getIntExtra(EXTRA_MINUTES, prefs.getSessionDurationMin())
                sessionEndMs = System.currentTimeMillis() + min * 60_000L
                prefs.setSessionDurationMin(min)
                prefs.setSessionEndMs(sessionEndMs)
                prefs.setShowFloatingTime(true)
                applyOverlays()
            }
        }
        return START_STICKY
    }

    private fun applyOverlays() {
        if (!canDrawOverlays()) {
            stopSelf()
            return
        }
        if (prefs.getShowFloatingTime()) showTimeOverlay() else removeTime()
        if (prefs.getShowSpeedMonitor()) showSpeedOverlay() else removeSpeed()
        if (!prefs.getShowFloatingTime() && !prefs.getShowSpeedMonitor()) {
            stopSelf()
        }
    }

    private fun canDrawOverlays(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this)
        else true

    private fun baseParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getOverlayX()
            y = prefs.getOverlayY()
        }
    }

    private fun showTimeOverlay() {
        if (timeView != null) return
        val tv = TextView(this).apply {
            setBackgroundColor(Color.argb(prefs.getOverlayOpacity(), 20, 20, 30))
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.getOverlayTextSizeSp().toFloat())
            typeface = Typeface.MONOSPACE
            text = "⏱ --:--"
        }
        val params = baseParams()
        params.y = prefs.getOverlayY()
        try {
            wm?.addView(tv, params)
            makeDraggable(tv, params)
            timeView = tv
        } catch (_: Exception) {}
    }

    private fun showSpeedOverlay() {
        if (speedView != null) return
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(prefs.getOverlayOpacity(), 10, 40, 30))
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val down = TextView(this).apply {
            setTextColor(Color.parseColor("#81C784"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.getOverlayTextSizeSp().toFloat())
            typeface = Typeface.MONOSPACE
            text = "↓ --"
            tag = "down"
        }
        val up = TextView(this).apply {
            setTextColor(Color.parseColor("#64B5F6"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.getOverlayTextSizeSp().toFloat())
            typeface = Typeface.MONOSPACE
            text = "↑ --"
            tag = "up"
            visibility = if (prefs.getSpeedShowUpload()) View.VISIBLE else View.GONE
        }
        ll.addView(down)
        ll.addView(up)
        val params = baseParams()
        params.y = prefs.getOverlayY() + dp(48)
        try {
            wm?.addView(ll, params)
            makeDraggable(ll, params)
            speedView = ll
        } catch (_: Exception) {}
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = e.rawX; touchY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - touchX).toInt()
                    params.y = startY + (e.rawY - touchY).toInt()
                    try { wm?.updateViewLayout(view, params) } catch (_: Exception) {}
                    prefs.setOverlayX(params.x)
                    prefs.setOverlayY(params.y)
                    true
                }
                else -> false
            }
        }
    }

    private fun updateTime() {
        val tv = timeView as? TextView ?: return
        if (sessionEndMs <= 0L) {
            val saved = prefs.getSessionEndMs()
            if (saved > System.currentTimeMillis()) sessionEndMs = saved
            else {
                tv.text = "⏱ 00:00"
                return
            }
        }
        val left = (sessionEndMs - System.currentTimeMillis()).coerceAtLeast(0)
        prefs.setSessionRemainingMs(left)
        val totalSec = (left / 1000).toInt()
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        tv.text = if (h > 0) String.format("⏱ %d:%02d:%02d", h, m, s)
        else String.format("⏱ %02d:%02d", m, s)
        if (left == 0L && prefs.getShowFloatingTime()) {
            // keep showing 00:00
        }
    }

    private fun updateSpeed() {
        val root = speedView as? LinearLayout ?: return
        val now = System.currentTimeMillis()
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (lastTs <= 0 || rx < 0) {
            lastRx = rx; lastTx = tx; lastTs = now
            return
        }
        val dt = (now - lastTs).coerceAtLeast(1) / 1000.0
        val downBps = ((rx - lastRx) / dt).coerceAtLeast(0.0)
        val upBps = ((tx - lastTx) / dt).coerceAtLeast(0.0)
        lastRx = rx; lastTx = tx; lastTs = now

        val unit = prefs.getSpeedUnit() // 0=KB/s 1=MB/s 2=auto
        fun fmt(bps: Double): String {
            val kb = bps / 1024.0
            val mb = kb / 1024.0
            return when (unit) {
                1 -> String.format("%.2f MB/s", mb)
                2 -> if (mb >= 1.0) String.format("%.2f MB/s", mb) else String.format("%.1f KB/s", kb)
                else -> String.format("%.1f KB/s", kb)
            }
        }
        (root.findViewWithTag<TextView>("down"))?.text = "↓ ${fmt(downBps)}"
        val upTv = root.findViewWithTag<TextView>("up")
        upTv?.visibility = if (prefs.getSpeedShowUpload()) View.VISIBLE else View.GONE
        upTv?.text = "↑ ${fmt(upBps)}"
    }

    private fun removeTime() {
        timeView?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        timeView = null
    }

    private fun removeSpeed() {
        speedView?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        speedView = null
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Overlays WiFi / Velocidad",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildFgNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("USSD Cuba · Overlay activo")
            .setContentText("Tiempo / velocidad en pantalla")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        removeTime()
        removeSpeed()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "ussd_overlay"
        const val NOTIF_ID = 42
        const val ACTION_REFRESH = "cu.ussd.cuba.OVERLAY_REFRESH"
        const val ACTION_STOP = "cu.ussd.cuba.OVERLAY_STOP"
        const val ACTION_SET_TIME = "cu.ussd.cuba.OVERLAY_SET_TIME"
        const val EXTRA_MINUTES = "minutes"

        fun start(ctx: Context) {
            val i = Intent(ctx, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ctx.startForegroundService(i)
            else
                ctx.startService(i)
        }

        fun refresh(ctx: Context) {
            ctx.startService(Intent(ctx, OverlayService::class.java).setAction(ACTION_REFRESH))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, OverlayService::class.java).setAction(ACTION_STOP))
        }

        fun setTime(ctx: Context, minutes: Int) {
            val i = Intent(ctx, OverlayService::class.java).setAction(ACTION_SET_TIME)
            i.putExtra(EXTRA_MINUTES, minutes)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ctx.startForegroundService(i)
            else
                ctx.startService(i)
        }
    }
}
