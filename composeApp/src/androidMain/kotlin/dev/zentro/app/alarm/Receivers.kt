package dev.zentro.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import dev.zentro.app.SyncWorker
import dev.zentro.app.zentro
import dev.zentro.shared.Schedule
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone

/** Recebe os alarmes do sistema e mostra os avisos. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Segura a CPU acordada enquanto mostramos o aviso e reagendamos os próximos.
        val wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zentro:alarm").apply { acquire(30_000) }
        val app = context.zentro
        val data = app.repo.data.value

        when (intent.getStringExtra(AlarmScheduler.EXTRA_TYPE)) {
            AlarmScheduler.TYPE_REMINDER -> {
                val key = intent.getStringExtra(AlarmScheduler.EXTRA_KEY)
                val eventId = intent.getStringExtra(AlarmScheduler.EXTRA_EVENT)
                // Usa o título atual (pode ter sido editado); se o compromisso foi apagado, não avisa.
                val occurrence = intent.getLongExtra(AlarmScheduler.EXTRA_START, 0)
                // Conta já paga neste mês: não avisa (o alarme pode ter sido agendado antes do pagamento).
                val event = data.events[eventId]?.takeIf { occurrence !in it.paid }
                val isTest = eventId == "test"
                if (key != null && (event != null || isTest) && FiredStore.markFired(context, key)) {
                    val title = event?.title ?: intent.getStringExtra(AlarmScheduler.EXTRA_TITLE).orEmpty()
                    Notifications.showReminder(context, intent, title, late = false)
                }
            }
            AlarmScheduler.TYPE_REVIEW -> {
                val tz = TimeZone.currentSystemDefault()
                if (data.session != null && Schedule.needsReview(data.settings.copy(timezone = tz.id))) {
                    Notifications.showReview(context)
                }
            }
        }

        val pending = goAsync()
        app.scope.launch {
            try {
                withTimeoutOrNull(8_000) { app.repo.sync() }
                AlarmScheduler.reschedule(context, app.repo.data.value)
            } finally {
                pending.finish()
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }
}

/** Botões da notificação: adiar e confirmar. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(AlarmScheduler.EXTRA_KEY) ?: return
        Notifications.cancel(context, Notifications.notificationId(key))
        if (intent.action == ACTION_SNOOZE) {
            val critical = intent.getBooleanExtra(AlarmScheduler.EXTRA_CRITICAL, false)
            AlarmScheduler.snooze(context, intent, if (critical) 5 else 10)
        }
    }

    companion object {
        const val ACTION_SNOOZE = "dev.zentro.app.SNOOZE"
        const val ACTION_DISMISS = "dev.zentro.app.DISMISS"
    }
}

/**
 * Ligou o celular, atualizou o app ou mudou o relógio/fuso: o Android apaga ou desalinha alarmes
 * nesses casos, então refazemos tudo — e mostramos os avisos perdidos enquanto estava desligado.
 */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.zentro
        val data = app.repo.data.value
        AlarmScheduler.reschedule(context, data)
        SyncWorker.enqueue(context)

        val isBootOrUpdate = intent.action != Intent.ACTION_TIME_CHANGED && intent.action != Intent.ACTION_TIMEZONE_CHANGED &&
            intent.action != "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        if (data.session != null && isBootOrUpdate) {
            val now = Schedule.now()
            val tz = TimeZone.currentSystemDefault()
            Schedule.triggers(data.activeEvents, now - 6 * 3_600_000L, now, tz)
                .filter { !FiredStore.wasFired(context, it.key) }
                // Só o que já existia na hora do aviso (não "avisa atrasado" algo criado depois).
                .filter { t -> data.events[t.eventId]?.let { it.updatedAt in 1 until t.triggerAt } == true }
                .takeLast(5)
                .forEach { t ->
                    if (!FiredStore.markFired(context, t.key)) return@forEach
                    val alarm = Intent()
                        .putExtra(AlarmScheduler.EXTRA_KEY, t.key)
                        .putExtra(AlarmScheduler.EXTRA_EVENT, t.eventId)
                        .putExtra(AlarmScheduler.EXTRA_TITLE, t.title)
                        .putExtra(AlarmScheduler.EXTRA_START, t.occurrenceStart)
                        .putExtra(AlarmScheduler.EXTRA_BEFORE, t.minutesBefore)
                        .putExtra(AlarmScheduler.EXTRA_CRITICAL, t.critical)
                    Notifications.showReminder(context, alarm, t.title, late = true)
                }
        }

        val pending = goAsync()
        app.scope.launch {
            try {
                withTimeoutOrNull(8_000) { app.repo.sync() }
                AlarmScheduler.reschedule(context, app.repo.data.value)
            } finally {
                pending.finish()
            }
        }
    }
}
