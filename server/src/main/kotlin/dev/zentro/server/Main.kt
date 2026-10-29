package dev.zentro.server

import dev.zentro.server.db.Store
import dev.zentro.server.integrations.MercadoPago
import dev.zentro.server.integrations.TelegramBot
import dev.zentro.server.integrations.GitHubReleases
import dev.zentro.shared.ApiError
import dev.zentro.shared.BuildVersion
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

object BuildInfo {
    const val VERSION: String = BuildVersion.CURRENT
}

/** Permite que partes do servidor peçam para desligar com um código de saída (ex.: atualização). */
object ServerControl {
    @Volatile var stop: ((Int) -> Unit)? = null
    @Volatile var backup: (() -> Unit)? = null

    fun exit(code: Int) {
        thread(name = "zentro-exit") { stop?.invoke(code) ?: exitProcess(code) }
    }
}

val ZentroJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

private val log = LoggerFactory.getLogger("dev.zentro.server")

fun main() {
    val config = Config.fromEnv()
    val server = embeddedServer(CIO, port = config.port, host = "0.0.0.0") { zentro(config) }
    ServerControl.stop = { code ->
        server.stop(1_000, 5_000)
        exitProcess(code)
    }

    // O Pterodactyl envia o comando de parada pelo console (stdin).
    thread(isDaemon = true, name = "console") {
        val reader = System.`in`.bufferedReader()
        while (true) {
            val line = runCatching { reader.readLine() }.getOrNull() ?: break
            when (line.trim().lowercase()) {
                "stop", "end", "exit", "quit" -> {
                    log.info("Desligando...")
                    ServerControl.exit(0)
                }
                "status" -> log.info("Online · versão ${BuildInfo.VERSION} · porta ${config.port}")
                "backup" -> ServerControl.backup?.invoke()
                "" -> Unit
                else -> log.info("Comandos: status, backup, stop")
            }
        }
    }

    server.start(wait = true)
}

fun Application.zentro(config: Config, store: Store = Store(File(config.dataDir, "zentro.db"))) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http = HttpClient(ClientCIO) {
        install(ClientContentNegotiation) { json(ZentroJson) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
        }
        expectSuccess = false
    }
    val hub = LiveHub()
    val auth = Auth(config.jwtSecret)
    val telegram = TelegramBot(config.telegramToken, http, store, onLinked = { hub.notify(it) })
    val mercadoPago = MercadoPago(http, store, onChanged = { hub.notify(it) })
    val releases = GitHubReleases(http, config.githubRepo)
    val backups = Backups(store, File(config.dataDir, "backups"))
    ServerControl.backup = { scope.launch { backups.backup("manual") } }
    val files = FileStore(File(config.dataDir, "files"))
    val ctx = AppContext(config, store, auth, hub, telegram, mercadoPago, releases, files, McpServer(store, files), scope)

    install(ContentNegotiation) { json(ZentroJson) }
    install(WebSockets) {
        pingPeriodMillis = 25_000
        timeoutMillis = 60_000
    }
    install(Authentication) {
        jwt("jwt") {
            realm = "zentro"
            verifier(auth.verifier)
            validate { credential -> credential.payload.getClaim(Auth.CLAIM_USER).asLong()?.let(::UserPrincipal) }
            challenge { _, _ -> call.respond(HttpStatusCode.Unauthorized, ApiError("Sessão expirada. Entre novamente.")) }
        }
    }
    install(StatusPages) {
        exception<ApiException> { call, e -> call.respond(e.status, ApiError(e.message)) }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("Requisição inválida")) }
        exception<Throwable> { call, e ->
            // Conexão encerrada pelo cliente (ex.: app fechado com o WebSocket aberto) não é erro.
            if (e is kotlinx.coroutines.CancellationException) return@exception
            log.error("Erro inesperado em ${call.request.local.uri}", e)
            call.respond(HttpStatusCode.InternalServerError, ApiError("Erro interno no servidor"))
        }
    }

    routing { apiRoutes(ctx) }

    monitor.subscribe(ApplicationStarted) {
        telegram.start(scope)
        mercadoPago.start(scope)
        Reminders(store, telegram).start(scope)
        backups.start(scope)
        SelfUpdater(config, releases, http, restart = {
            scope.launch {
                backups.backup("antes-da-atualizacao")
                ServerControl.exit(EXIT_UPDATE)
            }
        }).start(scope)
        scope.launch {
            val stats = store.stats()
            log.info("Dados em ${store.file.absolutePath}: ${stats.users} conta(s), ${stats.events} compromisso(s), ${stats.notes} nota(s), ${stats.transactions} movimentação(ões)")
        }
        // Esta linha é usada pelo egg do Pterodactyl para saber que o servidor terminou de iniciar.
        log.info("Zentro server started on port ${config.port} (v${BuildInfo.VERSION})")
    }
    monitor.subscribe(ApplicationStopping) {
        scope.cancel()
        http.close()
    }
}
