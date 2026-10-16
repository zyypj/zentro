package dev.zentro.app.data

import dev.zentro.shared.ApiError
import dev.zentro.shared.ApiRoutes
import dev.zentro.shared.AuthRequest
import dev.zentro.shared.AuthResponse
import dev.zentro.shared.BuildVersion
import dev.zentro.shared.Event
import dev.zentro.shared.GitHub
import dev.zentro.shared.MercadoPagoConnect
import dev.zentro.shared.MercadoPagoStatus
import dev.zentro.shared.Note
import dev.zentro.shared.Person
import dev.zentro.shared.ReviewRequest
import dev.zentro.shared.ServerInfo
import dev.zentro.shared.SettingsPatch
import dev.zentro.shared.SyncResponse
import dev.zentro.shared.TelegramLink
import dev.zentro.shared.Transaction
import dev.zentro.shared.UpdateInfo
import dev.zentro.shared.UserSettings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import io.ktor.websocket.Frame
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class ApiFailure(message: String, val status: Int? = null, val network: Boolean = false) : Exception(message)

/** Cliente HTTP do servidor Zentro. */
class ApiClient(serverUrl: String, private val token: String?) {
    private val base = normalizeUrl(serverUrl)

    private fun HttpRequestBuilder.prepare() {
        token?.let { bearerAuth(it) }
        contentType(ContentType.Application.Json)
    }

    private suspend fun exec(block: suspend () -> HttpResponse): HttpResponse {
        val response = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiFailure("Sem conexão", network = true)
        }
        if (!response.status.isSuccess()) {
            val message = runCatching { response.body<ApiError>().message }.getOrNull() ?: "Algo deu errado. Tente de novo."
            throw ApiFailure(message, response.status.value)
        }
        return response
    }

    suspend fun info(): ServerInfo = exec { http.get("$base${ApiRoutes.INFO}") }.body()

    suspend fun login(email: String, password: String): AuthResponse =
        exec { http.post("$base${ApiRoutes.LOGIN}") { prepare(); setBody(AuthRequest(email, password)) } }.body()

    suspend fun register(name: String, email: String, password: String): AuthResponse =
        exec { http.post("$base${ApiRoutes.REGISTER}") { prepare(); setBody(AuthRequest(email, password, name)) } }.body()

    suspend fun sync(since: Long): SyncResponse = exec { http.get("$base${ApiRoutes.SYNC}?since=$since") { prepare() } }.body()

    suspend fun putEvent(event: Event): Event =
        exec { http.put("$base${ApiRoutes.EVENTS}/${event.id}") { prepare(); setBody(event) } }.body()

    suspend fun deleteEvent(id: String) {
        exec { http.delete("$base${ApiRoutes.EVENTS}/$id") { prepare() } }
    }

    suspend fun putTransaction(tx: Transaction): Transaction =
        exec { http.put("$base${ApiRoutes.TRANSACTIONS}/${tx.id}") { prepare(); setBody(tx) } }.body()

    suspend fun deleteTransaction(id: String) {
        exec { http.delete("$base${ApiRoutes.TRANSACTIONS}/$id") { prepare() } }
    }

    suspend fun putPerson(person: Person): Person =
        exec { http.put("$base${ApiRoutes.PEOPLE}/${person.id}") { prepare(); setBody(person) } }.body()

    suspend fun deletePerson(id: String) {
        exec { http.delete("$base${ApiRoutes.PEOPLE}/$id") { prepare() } }
    }

    suspend fun putNote(note: Note): Note =
        exec { http.put("$base${ApiRoutes.NOTES}/${note.id}") { prepare(); setBody(note) } }.body()

    suspend fun deleteNote(id: String) {
        exec { http.delete("$base${ApiRoutes.NOTES}/$id") { prepare() } }
    }

    suspend fun latestRelease(): UpdateInfo = exec { http.get("$base${ApiRoutes.UPDATE}") }.body()

    suspend fun review(date: String): UserSettings =
        exec { http.post("$base${ApiRoutes.REVIEW}") { prepare(); setBody(ReviewRequest(date)) } }.body()

    suspend fun patchSettings(patch: SettingsPatch): UserSettings =
        exec { http.patch("$base${ApiRoutes.SETTINGS}") { prepare(); setBody(patch) } }.body()

    suspend fun telegramLink(): TelegramLink = exec { http.get("$base${ApiRoutes.TELEGRAM_LINK}") { prepare() } }.body()

    suspend fun telegramUnlink() {
        exec { http.delete("$base${ApiRoutes.TELEGRAM_LINK}") { prepare() } }
    }

    suspend fun telegramTest() {
        exec { http.post("$base${ApiRoutes.TELEGRAM_TEST}") { prepare() } }
    }

    suspend fun mercadoPago(): MercadoPagoStatus = exec { http.get("$base${ApiRoutes.MERCADO_PAGO}") { prepare() } }.body()

    suspend fun mercadoPagoConnect(token: String): MercadoPagoStatus =
        exec { http.post("$base${ApiRoutes.MERCADO_PAGO}") { prepare(); setBody(MercadoPagoConnect(token)) } }.body()

    suspend fun mercadoPagoDisconnect() {
        exec { http.delete("$base${ApiRoutes.MERCADO_PAGO}") { prepare() } }
    }

    suspend fun mercadoPagoSync(): MercadoPagoStatus =
        exec { http.post("$base${ApiRoutes.MERCADO_PAGO_SYNC}") { prepare() } }.body()

    /** Mantém o WebSocket aberto e chama [onMessage] a cada aviso de mudança. Retorna quando a conexão cai. */
    suspend fun live(onConnected: suspend () -> Unit, onMessage: suspend () -> Unit) {
        val wsUrl = base.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + ApiRoutes.LIVE
        wsHttp.webSocket(urlString = wsUrl, request = { token?.let { bearerAuth(it) } }) {
            onConnected()
            for (frame in incoming) {
                if (frame is Frame.Text) onMessage()
            }
        }
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

        private val http = HttpClient {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 30_000
            }
            expectSuccess = false
        }

        /** Última release direto do GitHub — reserva para quando o servidor não está acessível. */
        suspend fun githubLatest(repo: String = ApiRoutes.DEFAULT_REPO): UpdateInfo? = runCatching {
            val response = http.get(GitHub.latestReleaseUrl(repo)) {
                header(HttpHeaders.UserAgent, "Zentro/${BuildVersion.CURRENT}")
                header(HttpHeaders.Accept, "application/vnd.github+json")
            }
            if (response.status.isSuccess()) GitHub.parseRelease(response.body<JsonObject>(), repo) else null
        }.getOrNull()

        /** Baixa um arquivo grande (instalador) em pedaços, sem limite de tempo, informando o progresso. */
        suspend fun download(url: String, write: (ByteArray, Int) -> Unit, onProgress: (Float) -> Unit) {
            wsHttp.prepareGet(url).execute { response ->
                if (!response.status.isSuccess()) throw ApiFailure("Download falhou (HTTP ${response.status.value})", response.status.value)
                val total = response.contentLength() ?: -1L
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read < 0) break
                    if (read == 0) continue
                    write(buffer, read)
                    done += read
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        }

        // Cliente separado, sem timeout de requisição, para a conexão de tempo real de longa duração.
        private val wsHttp = HttpClient {
            install(WebSockets) { pingIntervalMillis = 20_000 }
        }

        fun normalizeUrl(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
        }
    }
}
