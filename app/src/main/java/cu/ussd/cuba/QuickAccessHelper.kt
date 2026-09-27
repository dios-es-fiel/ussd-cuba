package cu.ussd.cuba

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

object QuickAccessHelper {

    const val CHANNEL_ID = "ussd_quick_access"
    const val NOTIF_ID = 77
    const val ACTION_DIAL = "cu.ussd.cuba.QUICK_DIAL"
    const val EXTRA_CODE = "code"
    const val EXTRA_ID = "code_id"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Accesos rápidos USSD",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Botones de códigos USSD en la barra de notificaciones"
                setShowBadge(false)
            }
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    fun show(ctx: Context, prefs: PrefsHelper) {
        if (!prefs.getShowNotifShortcuts()) {
            cancel(ctx)
            return
        }
        ensureChannel(ctx)
        val ids = prefs.getNotifShortcutIds().ifEmpty {
            listOf("c1", "c2", "p1", "c6")
        }
        val codes = ids.mapNotNull { id -> CodesRepository.allCodes.find { it.id == id } }
            .take(4)
        if (codes.isEmpty()) return

        val openApp = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle("USSD Cuba · Accesos rápidos")
            .setContentText(codes.joinToString(" · ") { shortLabel(it) })
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        codes.forEachIndexed { i, code ->
            val intent = Intent(ctx, QuickDialReceiver::class.java).apply {
                action = ACTION_DIAL
                putExtra(EXTRA_CODE, code.code)
                putExtra(EXTRA_ID, code.id)
            }
            val pi = PendingIntent.getBroadcast(
                ctx, 100 + i, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, shortLabel(code), pi)
        }

        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, builder.build())
    }

    fun cancel(ctx: Context) {
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(NOTIF_ID)
    }

    private fun shortLabel(c: UssdCode): String = when (c.id) {
        "c1" -> "Saldo"
        "c2" -> "Datos"
        "c3" -> "Bono"
        "c6" -> "Límite"
        "p1" -> "Planes"
        "p3" -> "Transfer"
        "r2" -> "Recarga"
        "l1" -> "*99"
        else -> c.title.take(8)
    }
}

class QuickDialReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != QuickAccessHelper.ACTION_DIAL) return
        val code = intent.getStringExtra(QuickAccessHelper.EXTRA_CODE) ?: return
        val id = intent.getStringExtra(QuickAccessHelper.EXTRA_ID)
        try {
            val prefs = PrefsHelper(context)
            if (id != null) prefs.addRecent(id)
            val clean = code.replace(" ", "")
            // Only dial simple codes without params from notification
            if (clean.contains("{")) return
            val dial = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${Uri.encode(clean)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dial)
        } catch (_: Exception) {}
    }
}
