package dev.zentro.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.zentro.app.MainActivity
import dev.zentro.app.R
import dev.zentro.shared.Format
import dev.zentro.shared.Schedule
import kotlinx.datetime.TimeZone

object Notifications {
    const val CH_REMINDERS = "reminders"
    const val CH_CRITICAL = "critical"
    const val CH_REVIEW = "review"
    private const val REVIEW_ID = 42

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val reminders = NotificationChannel(CH_REMINDERS, "Lembretes de compromissos", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisos antes e na hora dos seus compromissos"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build(),
            )
        }
        val critical = NotificationChannel(CH_CRITICAL, "Compromissos críticos", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Toca como despertador até você confirmar"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
        }
        val review = NotificationChannel(CH_REVIEW, "Atualizar a agenda", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Lembra você de revisar a agenda do dia"
        }
        nm.createNotificationChannels(listOf(reminders, critical, review))
    }

    fun notificationId(key: String): Int = key.substringBefore("+snooze").hashCode()

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(context: Context, source: Intent, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code,
        Intent(context, ActionReceiver::class.java).setAction(action).putExtras(source),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun showReminder(context: Context, alarm: Intent, title: String, late: Boolean) {
        val key = alarm.getStringExtra(AlarmScheduler.EXTRA_KEY) ?: return
        val start = alarm.getLongExtra(AlarmScheduler.EXTRA_START, Schedule.now())
        val before = alarm.getIntExtra(AlarmScheduler.EXTRA_BEFORE, 0)
        val critical = alarm.getBooleanExtra(AlarmScheduler.EXTRA_CRITICAL, false)
        val local = Schedule.toLocal(start, TimeZone.currentSystemDefault())
        val text = (if (before == 0) "Agora · ${Format.time(local)}" else "Às ${Format.time(local)} · ${Schedule.describeOffset(before)}") +
            (if (late) " · aviso atrasado" else "")
        val id = notificationId(key)

        val builder = NotificationCompat.Builder(context, if (critical) CH_CRITICAL else CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF6C5CE7.toInt())
            .setContentTitle(if (critical) "⚠ $title" else title)
            .setContentText(text)
            .setWhen(start)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(if (critical) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(context))
            .setAutoCancel(!critical)
            .setOngoing(critical)
            .addAction(0, if (critical) "Adiar 5 min" else "Adiar 10 min", action(context, alarm, ActionReceiver.ACTION_SNOOZE, id))
            .addAction(0, if (critical) "Estou ciente" else "Ok", action(context, alarm, ActionReceiver.ACTION_DISMISS, id + 1))

        if (critical) {
            val fullScreen = PendingIntent.getActivity(
                context, id,
                Intent(context, AlarmActivity::class.java).putExtras(alarm).putExtra(AlarmScheduler.EXTRA_TITLE, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setFullScreenIntent(fullScreen, true)
        }

        val notification = builder.build()
        // Crítico: o som se repete até você interagir com a notificação.
        if (critical) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        post(context, id, notification)
    }

    fun showReview(context: Context) {
        val notification = NotificationCompat.Builder(context, CH_REVIEW)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF6C5CE7.toInt())
            .setContentTitle("Atualize sua agenda")
            .setContentText("Você ainda não revisou a agenda de hoje. Leva 10 segundos.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        post(context, REVIEW_ID, notification)
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(id)
    }

    private fun post(context: Context, id: Int, notification: Notification) {
        try {
            context.getSystemService(NotificationManager::class.java).notify(id, notification)
        } catch (_: SecurityException) {
            // Sem permissão de notificação: o app mostra o problema em Ajustes › Avisos garantidos.
        }
    }

    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
}

/** Memória dos avisos já mostrados, para nunca repetir o mesmo aviso duas vezes. */
object FiredStore {
    private const val PREFS = "zentro-fired"

    @Synchronized
    fun markFired(context: Context, key: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(key)) return false
        val now = Schedule.now()
        val editor = prefs.edit().putLong(key, now)
        prefs.all.forEach { (k, v) -> if (v is Long && now - v > 3 * 86_400_000L) editor.remove(k) }
        editor.commit()
        return true
    }

    fun wasFired(context: Context, key: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(key)
}
