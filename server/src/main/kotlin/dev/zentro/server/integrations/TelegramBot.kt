package dev.zentro.server.integrations

import dev.zentro.server.db.Store
import dev.zentro.shared.Format
import dev.zentro.shared.Schedule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.longOrNull
import org.slf4j.LoggerFactory

/**
 * Canal reserva de avisos. Mesmo que o celular esteja desligado, sem bateria ou com o app
 * morto pelo sistema, o servidor manda o lembrete pelo Telegram — que chega no celular,
 * no relógio e no computador.
 */
class TelegramBot(
    private val token: String?,
    private val http: HttpClient,
    private val store: Store,
    private val onLinked: suspend (Long) -> Unit,
) {
    private val log = LoggerFactory.getLogger(TelegramBot::class.java)
    private val api = "https://api.telegram.org/bot$token"
    private var username: String? = null

    val enabled: Boolean get() = token != null && username != null

    fun deepLink(code: String): String = "https://t.me/$username?start=$code"

    fun start(scope: CoroutineScope) {
        if (token == null) {
            log.info("Telegram desativado (defina TELEGRAM_BOT_TOKEN para ativar o canal reserva de avisos)")
            return
        }
        scope.launch {
            while (isActive && username == null) {
                username = runCatching {
                    http.get("$api/getMe").body<JsonObject>()["result"]!!.jsonObject["username"]!!.jsonPrimitive.content
                }.onFailure { log.warn("Telegram indisponível: ${it.message}. Tentando de novo em 30s") }.getOrNull()
                if (username == null) delay(30_000)
            }
            log.info("Telegram conectado como @$username")
            pollUpdates()
        }
    }

    suspend fun send(chatId: Long, html: String): Boolean {
        if (token == null) return false
        repeat(3) { attempt ->
            val ok = runCatching {
                http.post("$api/sendMessage") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        buildJsonObject {
                            put("chat_id", chatId)
                            put("text", html)
                            put("parse_mode", "HTML")
                        },
                    )
                }.status.isSuccess()
            }.getOrDefault(false)
            if (ok) return true
            delay(2_000L * (attempt + 1))
        }
        log.warn("Falha ao enviar mensagem para o chat $chatId")
        return false
    }

    private suspend fun CoroutineScope.pollUpdates() {
        var offset = 0L
        while (isActive) {
            try {
                val response = http.get("$api/getUpdates") {
                    parameter("offset", offset)
                    parameter("timeout", 50)
                    parameter("allowed_updates", "[\"message\"]")
                    timeout {
                        requestTimeoutMillis = 65_000
                        socketTimeoutMillis = 65_000
                    }
                }.body<JsonObject>()
                for (update in response["result"]?.jsonArray.orEmpty()) {
                    val obj = update.jsonObject
                    offset = obj["update_id"]!!.jsonPrimitive.long + 1
                    val message = obj["message"]?.jsonObject ?: continue
                    val chatId = message["chat"]?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull ?: continue
                    val text = message["text"]?.jsonPrimitive?.content ?: continue
                    handle(chatId, text.trim())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Erro lendo o Telegram: ${e.message}")
                delay(10_000)
            }
        }
    }

    private suspend fun handle(chatId: Long, text: String) {
        val command = text.substringBefore(' ').substringBefore('@').lowercase()
        when (command) {
            "/start" -> {
                val code = text.substringAfter(' ', "").trim()
                val userId = if (code.isNotEmpty()) store.linkTelegram(code, chatId, Schedule.now()) else null
                if (userId != null) {
                    send(chatId, "🎉 <b>Telegram conectado ao Zentro!</b>\nVou te avisar de todos os compromissos por aqui também.\n\nComandos: /hoje /amanha")
                    onLinked(userId)
                } else {
                    send(chatId, "Olá! Para conectar, abra o Zentro › Ajustes › <b>Conectar Telegram</b>.")
                }
            }
            "/hoje", "/amanha" -> {
                val userId = store.userByTelegramChat(chatId)
                    ?: return run { send(chatId, "Este chat não está conectado a nenhuma conta Zentro.") }
                send(chatId, agendaText(userId, if (command == "/hoje") 0 else 1))
            }
            else -> send(chatId, "Comandos: /hoje para ver a agenda de hoje, /amanha para amanhã.")
        }
    }

    private suspend fun agendaText(userId: Long, plusDays: Int): String {
        val tz = Schedule.zone(store.settings(userId).public.timezone)
        val date = Schedule.toLocal(Schedule.now() + plusDays * 86_400_000L, tz).date
        val items = Schedule.agendaFor(store.activeEvents(userId), date, tz)
        val people = store.activePeople(userId).associate { it.id to it.name }
        val header = "📅 <b>${Format.longDate(date).replaceFirstChar { it.uppercase() }}</b>"
        if (items.isEmpty()) return "$header\nNada marcado. Aproveite!"
        return header + "\n" + items.joinToString("\n") { (event, start) ->
            val time = if (event.allDay) "dia todo" else Format.time(Schedule.toLocal(start, tz))
            val person = event.personId?.let { people[it] }?.let { " <i>(${escape(it)})</i>" }.orEmpty()
            "${if (event.critical) "🚨" else "•"} <b>$time</b> ${escape(event.title)}$person"
        }
    }

    companion object {
        fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}
