package dev.zentro.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.zentro.app.MainActivity
import dev.zentro.app.data.AppData
import dev.zentro.shared.ReminderTrigger
import dev.zentro.shared.Schedule
import kotlinx.datetime.TimeZone
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Agenda os avisos no AlarmManager do Android.
 *
 * Os lembretes usam `setAlarmClock`, o mesmo mecanismo do app Relógio: é o único tipo de alarme
 * que o Android garante disparar na hora exata mesmo em modo soneca (Doze) e economia de bateria.
 * Agendamos os próximos 7 dias de uma vez; a lista é refeita sempre que a agenda muda, quando um
 * alarme dispara, a cada sincronização em segundo plano, ao ligar o celular e ao mudar o relógio.
 */
object AlarmScheduler {
    const val ACTION_FIRE = "dev.zentro.app.ALARM"
    const val EXTRA_TYPE = "type"
    const val EXTRA_KEY = "key"
    const val EXTRA_EVENT = "event"
    const val EXTRA_TITLE = "title"
    const val EXTRA_START = "start"
    const val EXTRA_BEFORE = "before"
    const val EXTRA_CRITICAL = "critical"
    const val EXTRA_AMOUNT = "amount"

    const val TYPE_REMINDER = "reminder"
    const val TYPE_REVIEW = "review"
    const val TYPE_HEARTBEAT = "heartbeat"

    private const val PREFS = "zentro-alarms"
    private const val KEY_COUNT = "count"
    private const val BASE_CODE = 10_000
    private const val SNOOZE_BASE = 500_000
    private const val HEARTBEAT_CODE = 9_999
    private const val HORIZON = 7 * 86_400_000L
    private const val MAX_ALARMS = 300

    @Synchronized
    fun reschedule(context: Context, data: AppData) {
        val am = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getInt(KEY_COUNT, 0)
        val now = Schedule.now()
        val tz = TimeZone.currentSystemDefault()

        val intents = mutableListOf<Pair<Long, Intent>>()
        if (data.session != null) {
            Schedule.triggers(data.activeEvents, now, now + HORIZON, tz).take(MAX_ALARMS).forEach { t ->
                intents += t.triggerAt to reminderIntent(context, t)
            }
            Schedule.nextReviewChecks(data.settings.copy(timezone = tz.id), now, days = 2).forEach { at ->
                intents += at to baseIntent(context).putExtra(EXTRA_TYPE, TYPE_REVIEW).putExtra(EXTRA_KEY, "review@$at")
            }
        }

        intents.forEachIndexed { index, (at, intent) ->
            val pi = PendingIntent.getBroadcast(context, BASE_CODE + index, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if (intent.getStringExtra(EXTRA_TYPE) == TYPE_REMINDER) setAlarmClock(context, am, at, pi) else setExact(am, at, pi)
        }
        for (index in intents.size until previous) {
            PendingIntent.getBroadcast(context, BASE_CODE + index, baseIntent(context), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                ?.let {
                    am.cancel(it)
                    it.cancel()
                }
        }
        prefs.edit().putInt(KEY_COUNT, intents.size).apply()

        // Batimento diário às 04:00: sincroniza e refaz a lista mesmo se nada mais acontecer.
        val heartbeat = ZonedDateTime.now().with(LocalTime.of(4, 0)).let { if (it.isBefore(ZonedDateTime.now())) it.plusDays(1) else it }
        val hbIntent = baseIntent(context).putExtra(EXTRA_TYPE, TYPE_HEARTBEAT)
        setExact(am, heartbeat.toInstant().toEpochMilli(), PendingIntent.getBroadcast(context, HEARTBEAT_CODE, hbIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    /** Adia um aviso: agenda uma cópia dele para daqui a [minutes] minutos. */
    fun snooze(context: Context, source: Intent, minutes: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        val at = Schedule.now() + minutes * 60_000L
        val key = (source.getStringExtra(EXTRA_KEY) ?: "snooze") + "+snooze@$at"
        val intent = baseIntent(context).putExtras(source).putExtra(EXTRA_TYPE, TYPE_REMINDER).putExtra(EXTRA_KEY, key)
        val code = SNOOZE_BASE + (key.hashCode() and 0xFFFF)
        setAlarmClock(context, am, at, PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    /** Aviso de teste em 5 segundos, pelo mesmo caminho dos avisos reais. */
    fun scheduleTest(context: Context) {
        val now = Schedule.now()
        val trigger = ReminderTrigger("test", "Aviso de teste do Zentro", now + 5_000, 0, now + 5_000, critical = false)
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(context, SNOOZE_BASE - 1, reminderIntent(context, trigger), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        setAlarmClock(context, am, trigger.triggerAt, pi)
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun baseIntent(context: Context) = Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE)

    private fun reminderIntent(context: Context, t: ReminderTrigger) = baseIntent(context)
        .putExtra(EXTRA_TYPE, TYPE_REMINDER)
        .putExtra(EXTRA_KEY, t.key)
        .putExtra(EXTRA_EVENT, t.eventId)
        .putExtra(EXTRA_TITLE, t.title)
        .putExtra(EXTRA_START, t.occurrenceStart)
        .putExtra(EXTRA_BEFORE, t.minutesBefore)
        .putExtra(EXTRA_CRITICAL, t.critical)
        .putExtra(EXTRA_AMOUNT, t.amountCents ?: -1L)

    private fun setAlarmClock(context: Context, am: AlarmManager, at: Long, pi: PendingIntent) {
        if (canScheduleExact(context)) {
            val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } else {
            // Sem permissão de alarme exato (o app avisa nos Ajustes): ainda dispara, com possível atraso.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun setExact(am: AlarmManager, at: Long, pi: PendingIntent) {
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exactAllowed) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
}
