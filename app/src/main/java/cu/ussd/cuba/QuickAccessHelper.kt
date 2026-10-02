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
                NotificationManager.IMPORTANCE_DEFAULT
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
            .filter { !it.code.contains("{") } // solo códigos sin parámetros
            .take(4)
        if (codes.isEmpty()) return

        val openApp = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle("USSD Cuba")
            .setContentText("Toca un botón para marcar")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                codes.joinToString("  ·  ") { shortLabel(it) }
            ))

        codes.forEachIndexed { i, code ->
            // PendingIntent hacia Activity (más fiable que Broadcast en varios OEM)
            val dialIntent = Intent(ctx, MainActivity::class.java).apply {
                action = ACTION_DIAL
                putExtra(EXTRA_CODE, code.code)
                putExtra(EXTRA_ID, code.id)
                putExtra("ussd_code", code.code)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(
                ctx, 200 + i, dialIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // Solo el nombre corto del código (sin icono vacío)
            builder.addAction(
                android.R.drawable.ic_menu_call,
                shortLabel(code),
                pi
            )
        }

        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, builder.build())
    }

    fun cancel(ctx: Context) {
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(NOTIF_ID)
    }

    /** Solo el nombre legible del código (para el botón). */
    fun shortLabel(c: UssdCode): String = when (c.id) {
        "c1" -> "Saldo"
        "c2" -> "Datos"
        "c3" -> "Bono"
        "c4" -> "Voz"
        "c5" -> "SMS"
        "c6" -> "Límite"
        "c7" -> "Amigos"
        "p1" -> "Planes"
        "p1d" -> "Datos+"
        "p2" -> "Transfer"
        "p5" -> "Adelanta"
        "r1" -> "Recarga"
        "l1" -> "*99"
        "l8" -> "Buzón"
        else -> c.title.take(10)
    }
}

/** Respaldo por si algún OEM usa broadcast. */
class QuickDialReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != QuickAccessHelper.ACTION_DIAL) return
        val code = intent.getStringExtra(QuickAccessHelper.EXTRA_CODE) ?: return
        val id = intent.getStringExtra(QuickAccessHelper.EXTRA_ID)
        try {
            val prefs = PrefsHelper(context)
            if (id != null) prefs.addRecent(id)
            val clean = code.replace(" ", "")
            if (clean.contains("{")) return
            val dial = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${Uri.encode(clean)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dial)
        } catch (_: Exception) {}
    }
}
