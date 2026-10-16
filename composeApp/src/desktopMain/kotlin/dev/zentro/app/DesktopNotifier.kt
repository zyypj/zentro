package dev.zentro.app

import dev.zentro.app.data.FileStorage
import dev.zentro.app.data.Repository
import dev.zentro.shared.Format
import dev.zentro.shared.ReminderTrigger
import dev.zentro.shared.Schedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime

/** Um aviso visível na tela (janela flutuante no canto). */
data class Toast(val id: Long, val title: String, val message: String, val critical: Boolean, val trigger: ReminderTrigger?)

/**
 * Agendador do desktop. Roda enquanto o app estiver aberto (inclusive minimizado na bandeja)
 * e verifica a cada 5 segundos a janela de tempo desde a última verificação — assim nenhum
 * aviso se perde mesmo se o computador "pular" alguns segundos por suspensão.
 * Ao abrir o app, recupera avisos das últimas 6 horas que caíram com ele fechado.
 */
class DesktopNotifier(
    private val repo: Repository,
    private val storage: FileStorage,
    /** Notificação do sistema (balão da bandeja do Windows). */
    private val systemNotify: (title: String, message: String, warning: Boolean) -> Unit,
) {
    private val _toasts = MutableStateFlow<List<Toast>>(emptyList())
    val toasts: StateFlow<List<Toast>> = _toasts.asStateFlow()

    private val _critical = MutableStateFlow<List<ReminderTrigger>>(emptyList())
    val critical: StateFlow<List<ReminderTrigger>> = _critical.asStateFlow()

    private val fired = LinkedHashMap<String, Long>()
    private var lastCheck: Long = 0
    private val startedAt = Schedule.now()

    init {
        storage.read(STATE)?.lines()?.forEach { line ->
            val (key, at) = line.split('|').takeIf { it.size == 2 } ?: return@forEach
            if (key == "lastCheck") lastCheck = at.toLongOrNull() ?: 0 else at.toLongOrNull()?.let { fired[key] = it }
        }
        // Primeira execução: começa de agora. Depois: recupera no máximo 6 horas de avisos perdidos.
        lastCheck = if (lastCheck == 0L) startedAt else lastCheck.coerceIn(startedAt - MAX_CATCH_UP, startedAt)
    }

    /**
     * Antes de recuperar avisos perdidos, espera a primeira sincronização (ou 20s, se estiver
     * offline) para não avisar de algo que já foi apagado ou alterado em outro aparelho.
     */
    private fun ready(now: Long): Boolean =
        (repo.data.value.lastSyncOk ?: 0) >= startedAt || now - startedAt > 20_000

    fun start(scope: CoroutineScope) = scope.launch {
        while (isActive) {
            runCatching { check(Schedule.now()) }
            delay(5_000)
        }
    }

    private fun check(now: Long) {
        if (now <= lastCheck || !ready(now)) return
        val data = repo.data.value
        if (data.session != null) {
            val tz = repo.timeZone
            for (trigger in Schedule.triggers(data.activeEvents, lastCheck, now, tz)) {
                if (fired.containsKey(trigger.key)) continue
                fired[trigger.key] = now
                show(trigger, late = now - trigger.triggerAt > 2 * 60_000)
            }

            val settings = data.settings.copy(timezone = tz.id)
            val today = Schedule.today(tz, now)
            for (time in settings.reviewTimes.mapNotNull(Schedule::parseTime)) {
                val at = Schedule.toMillis(LocalDateTime(today, time), tz)
                val key = "review:$today:$time"
                // Só horários que passam com o app aberto; ao abrir, o banner da tela Hoje já cobra.
                if (at !in maxOf(lastCheck, startedAt) until now || fired.containsKey(key) || !Schedule.needsReview(settings, now)) continue
                fired[key] = now
                notify("Atualize sua agenda", "Você ainda não revisou a agenda de hoje. Leva 10 segundos.", critical = false, trigger = null)
            }
        }
        lastCheck = now
        persist(now)
    }

    fun show(trigger: ReminderTrigger, late: Boolean = false) {
        val tz = repo.timeZone
        val start = Schedule.toLocal(trigger.occurrenceStart, tz)
        val whenText = if (trigger.minutesBefore == 0) "Agora · ${Format.time(start)}"
        else "Às ${Format.time(start)} (${Schedule.describeOffset(trigger.minutesBefore)})"
        val message = whenText + if (late) " · aviso atrasado" else ""
        if (trigger.critical) {
            _critical.update { list -> if (list.any { it.key == trigger.key }) list else list + trigger }
        }
        notify(trigger.title, message, trigger.critical, trigger)
    }

    private fun notify(title: String, message: String, critical: Boolean, trigger: ReminderTrigger?) {
        runCatching { systemNotify(title, message, critical) }
        if (!critical) Sound.chime()
        val toast = Toast(Schedule.now() + _toasts.value.size, title, message, critical, trigger)
        _toasts.update { (it + toast).takeLast(3) }
    }

    fun dismissToast(id: Long) = _toasts.update { list -> list.filterNot { it.id == id } }

    fun confirmCritical(trigger: ReminderTrigger) = _critical.update { list -> list.filterNot { it.key == trigger.key } }

    fun snoozeCritical(trigger: ReminderTrigger, scope: CoroutineScope, minutes: Int = 5) {
        confirmCritical(trigger)
        scope.launch {
            delay(minutes * 60_000L)
            _critical.update { it + trigger }
            Sound.chime()
        }
    }

    private fun persist(now: Long) {
        fired.entries.removeAll { now - it.value > 3 * 86_400_000L }
        val content = buildString {
            append("lastCheck|").append(now).append('\n')
            fired.forEach { (k, v) -> append(k).append('|').append(v).append('\n') }
        }
        runCatching { storage.write(STATE, content) }
    }

    private companion object {
        const val STATE = "notifier-state.txt"
        const val MAX_CATCH_UP = 6 * 3_600_000L
    }
}
