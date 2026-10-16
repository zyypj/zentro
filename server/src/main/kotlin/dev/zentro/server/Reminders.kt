package dev.zentro.server

import dev.zentro.server.db.Store
import dev.zentro.server.integrations.TelegramBot
import dev.zentro.shared.Format
import dev.zentro.shared.Schedule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import org.slf4j.LoggerFactory

/**
 * Agendador do lado do servidor: a cada 20 segundos verifica a janela de tempo desde a última
 * execução e envia pelo Telegram os avisos que caíram nela. Se o servidor ficou fora do ar,
 * recupera até 6 horas de avisos atrasados ao voltar, marcando-os como atrasados.
 */
class Reminders(private val store: Store, private val telegram: TelegramBot) {
    private val log = LoggerFactory.getLogger(Reminders::class.java)

    fun start(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                try {
                    tick()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("Erro no agendador de avisos", e)
                }
                delay(TICK)
            }
        }
    }

    suspend fun tick(now: Long = Schedule.now()) {
        val last = store.meta(LAST_RUN)?.toLongOrNull() ?: (now - TICK)
        val from = maxOf(last, now - MAX_CATCH_UP)
        if (now <= from) return

        if (telegram.enabled) {
            for (settings in store.allSettings()) {
                val chatId = settings.telegramChatId ?: continue
                val tz = Schedule.zone(settings.public.timezone)

                val events = store.activeEvents(settings.userId)
                val eventsById = events.associateBy { it.id }
                val people = store.activePeople(settings.userId).associate { it.id to it.name }
                for (trigger in Schedule.triggers(events, from, now, tz)) {
                    if (!store.markSent("tg:${settings.userId}:${trigger.key}", now)) continue
                    val start = Schedule.toLocal(trigger.occurrenceStart, tz)
                    val late = now - trigger.triggerAt > 2 * 60_000
                    val person = eventsById[trigger.eventId]?.personId?.let { people[it] }
                    val text = buildString {
                        if (person != null) append("👤 <b>").append(TelegramBot.escape(person)).append("</b> · ")
                        append(if (trigger.critical) "🚨 <b>COMPROMISSO CRÍTICO</b>\n" else "⏰ ")
                        append("<b>").append(TelegramBot.escape(trigger.title)).append("</b>\n")
                        append(when (trigger.minutesBefore) {
                            0 -> "Agora"
                            else -> "Começa às ${Format.time(start)} (${Schedule.describeOffset(trigger.minutesBefore)})"
                        })
                        append(" · ").append(Format.shortDate(start.date))
                        if (late) append("\n<i>Aviso atrasado: o servidor esteve fora do ar.</i>")
                    }
                    if (!telegram.send(chatId, text)) {
                        log.warn("Aviso ${trigger.key} não pôde ser entregue pelo Telegram")
                    }
                }

                // Cobrança diária: "você ainda não atualizou sua agenda hoje".
                val today = Schedule.today(tz, now)
                for (time in settings.public.reviewTimes.mapNotNull(Schedule::parseTime)) {
                    val at = Schedule.toMillis(LocalDateTime(today, time), tz)
                    if (at !in from until now || !Schedule.needsReview(settings.public, now)) continue
                    if (!store.markSent("review:${settings.userId}:$today:$time", now)) continue
                    telegram.send(
                        chatId,
                        "📝 <b>Você ainda não atualizou sua agenda hoje.</b>\nAbra o Zentro e confira seus compromissos — leva 10 segundos.",
                    )
                }
            }
        }

        store.setMeta(LAST_RUN, now.toString())
        if (now / DAY != last / DAY) store.purgeSent(now - 14 * DAY)
    }

    private companion object {
        const val LAST_RUN = "reminders.lastRun"
        const val TICK = 20_000L
        const val DAY = 86_400_000L
        const val MAX_CATCH_UP = 6 * 3_600_000L
    }
}
