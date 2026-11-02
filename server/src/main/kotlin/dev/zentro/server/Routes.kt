package dev.zentro.server

import dev.zentro.server.db.Store
import dev.zentro.server.integrations.GitHubReleases
import dev.zentro.server.integrations.MercadoPago
import dev.zentro.server.integrations.TelegramBot
import dev.zentro.shared.AiLink
import dev.zentro.shared.ApiError
import dev.zentro.shared.ApiRoutes
import dev.zentro.shared.AuthRequest
import dev.zentro.shared.AuthResponse
import dev.zentro.shared.Event
import dev.zentro.shared.MercadoPagoConnect
import dev.zentro.shared.Note
import dev.zentro.shared.Notes
import dev.zentro.shared.PasswordChange
import dev.zentro.shared.Person
import dev.zentro.shared.CreditCard
import dev.zentro.shared.Cards
import dev.zentro.shared.ReviewRequest
import dev.zentro.shared.Schedule
import dev.zentro.shared.ServerInfo
import dev.zentro.shared.SettingsPatch
import dev.zentro.shared.SyncResponse
import dev.zentro.shared.TelegramLink
import dev.zentro.shared.UploadedFile
import dev.zentro.shared.Transaction
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json

class ApiException(val status: HttpStatusCode, override val message: String) : RuntimeException(message)

private fun bad(message: String): Nothing = throw ApiException(HttpStatusCode.BadRequest, message)

/** Tudo que as rotas precisam, num lugar só. */
class AppContext(
    val config: Config,
    val store: Store,
    val auth: Auth,
    val hub: LiveHub,
    val telegram: TelegramBot,
    val mercadoPago: MercadoPago,
    val releases: GitHubReleases,
    val files: FileStore,
    val mcp: McpServer,
    val scope: CoroutineScope,
) {
    /** Qualquer alteração na agenda conta como "agenda atualizada hoje". */
    suspend fun touchAgenda(userId: Long, now: Long) {
        val tz = Schedule.zone(store.settings(userId).public.timezone)
        store.markReviewed(userId, Schedule.today(tz, now).toString(), now)
    }
}

private const val MAX_FILE_BYTES = 10L * 1024 * 1024
private const val PARSE_ERROR = "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"JSON inválido\"}}"

private val idPattern = Regex("^[A-Za-z0-9_-]{1,64}$")

private fun ApplicationCall.pathId(): String =
    parameters["id"]?.takeIf { idPattern.matches(it) } ?: bad("Id inválido")

private fun Event.validated(pathId: String): Event {
    if (id != pathId) bad("Id do corpo diferente da URL")
    val cleanTitle = title.trim()
    if (cleanTitle.isEmpty()) bad("Dê um nome ao compromisso")
    if (cleanTitle.length > 300) bad("Nome muito longo")
    if ((endAt ?: startAt) < startAt) bad("O fim não pode ser antes do início")
    if (billAmountCents != null && billAmountCents !in 1..100_000_000_000L) bad("Valor da conta inválido")
    return copy(
        title = cleanTitle,
        notes = notes.take(5000),
        billCategory = billCategory.trim().take(60).ifEmpty { "Contas" },
        category = category.trim().take(40),
        paid = paid.distinct().takeLast(500),
        reminders = reminders.filter { it in 0..43_200 }.distinct().sortedDescending().take(10),
    )
}

private fun Transaction.validated(pathId: String): Transaction {
    if (id != pathId) bad("Id do corpo diferente da URL")
    if (amountCents <= 0) bad("Informe um valor maior que zero")
    if (amountCents > 100_000_000_000L) bad("Valor alto demais")
    return copy(
        description = description.trim().take(300).ifEmpty { category },
        category = category.trim().take(60),
        cardId = cardId?.take(64),
        installments = if (cardId == null) 1 else installments.coerceIn(1, Cards.MAX_INSTALLMENTS),
        invoiceKey = invoiceKey?.take(100),
    )
}

private fun CreditCard.validated(pathId: String): CreditCard {
    if (id != pathId) bad("Id do corpo diferente da URL")
    val clean = name.trim()
    if (clean.isEmpty()) bad("Informe o nome do cartão")
    if (closingDay !in 1..31 || dueDay !in 1..31) bad("Dia de fechamento e vencimento vão de 1 a 31")
    if ((limitCents ?: 0) < 0) bad("Limite inválido")
    return copy(name = clean.take(80))
}

private fun Person.validated(pathId: String): Person {
    if (id != pathId) bad("Id do corpo diferente da URL")
    val clean = name.trim()
    if (clean.isEmpty()) bad("Informe o nome da pessoa")
    return copy(name = clean.take(80))
}

private fun Note.validated(pathId: String): Note {
    if (id != pathId) bad("Id do corpo diferente da URL")
    if (content.length > 500_000) bad("Anotação grande demais")
    return copy(
        title = title.trim().take(300),
        folder = folder.trim().trim('/').take(200).ifEmpty { Notes.DEFAULT_FOLDER },
        icon = icon.take(16),
    )
}

fun Route.apiRoutes(ctx: AppContext) {
    val store = ctx.store

    get("/") {
        call.respondText(
            "<html><body style=\"font-family:sans-serif;background:#0f1115;color:#e8e8ea;display:grid;place-items:center;height:100vh;margin:0\">" +
                "<div style=\"text-align:center\"><h1>Zentro</h1><p>Servidor online · v${BuildInfo.VERSION}</p></div></body></html>",
            ContentType.Text.Html,
        )
    }

    get(ApiRoutes.INFO) {
        call.respond(
            ServerInfo(
                name = "Zentro",
                version = BuildInfo.VERSION,
                registrationOpen = ctx.config.allowRegistration || store.userCount() == 0L,
                telegramEnabled = ctx.telegram.enabled,
                serverTime = Schedule.now(),
            ),
        )
    }

    // ---------- MCP: Claude e ChatGPT lendo as notas ----------
    // A chave vai no caminho (/mcp/<chave>, para o ChatGPT) ou no cabeçalho Authorization (Claude).
    suspend fun ApplicationCall.handleMcp(token: String?) {
        val userId = token?.let { store.userByAiToken(it) }
            ?: return respond(HttpStatusCode.Unauthorized, ApiError("Chave inválida. Gere uma nova em Ajustes › Conectar ao Claude/ChatGPT."))
        val body = runCatching { Json.parseToJsonElement(receiveText()) }.getOrNull()
            ?: return respondText(PARSE_ERROR, ContentType.Application.Json, HttpStatusCode.BadRequest)
        val answer = ctx.mcp.handle(userId, body)
        if (answer == null) respond(HttpStatusCode.Accepted, "") else respondText(answer.toString(), ContentType.Application.Json)
    }
    post(ApiRoutes.MCP) { call.handleMcp(call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()) }
    post("${ApiRoutes.MCP}/{token}") { call.handleMcp(call.parameters["token"]) }
    // Sem streaming por SSE: o GET não é usado neste servidor (a especificação permite responder 405).
    get(ApiRoutes.MCP) { call.respond(HttpStatusCode.MethodNotAllowed, "") }
    get("${ApiRoutes.MCP}/{token}") { call.respond(HttpStatusCode.MethodNotAllowed, "") }

    get(ApiRoutes.UPDATE) {
        val info = ctx.releases.latest() ?: throw ApiException(HttpStatusCode.ServiceUnavailable, "Não consegui consultar o GitHub agora")
        call.respond(info)
    }

    post(ApiRoutes.REGISTER) {
        val req = call.receive<AuthRequest>()
        if (!ctx.config.allowRegistration && store.userCount() > 0) {
            throw ApiException(HttpStatusCode.Forbidden, "Novos cadastros estão desativados neste servidor")
        }
        val email = req.email.trim().lowercase()
        val name = req.name?.trim().orEmpty().ifEmpty { email.substringBefore('@') }
        if (!email.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) bad("E-mail inválido")
        if (req.password.length < 6) bad("A senha precisa ter pelo menos 6 caracteres")
        if (store.userByEmail(email) != null) throw ApiException(HttpStatusCode.Conflict, "Este e-mail já tem conta. Faça login.")
        val user = store.createUser(name.take(100), email, ctx.auth.hash(req.password), Schedule.now())
        call.respond(AuthResponse(ctx.auth.issue(user.id), user.toDto()))
    }

    post(ApiRoutes.LOGIN) {
        val req = call.receive<AuthRequest>()
        val user = store.userByEmail(req.email.trim().lowercase())
        if (user == null || !ctx.auth.matches(req.password, user.passwordHash)) {
            delay(600) // freia tentativas de força bruta
            throw ApiException(HttpStatusCode.Unauthorized, "E-mail ou senha incorretos")
        }
        call.respond(AuthResponse(ctx.auth.issue(user.id), user.toDto()))
    }

    authenticate("jwt") {
        post(ApiRoutes.PASSWORD) {
            val userId = call.userId
            val req = call.receive<PasswordChange>()
            val user = store.user(userId) ?: throw ApiException(HttpStatusCode.Unauthorized, "Sessão inválida")
            if (!ctx.auth.matches(req.current, user.passwordHash)) {
                delay(600)
                bad("Senha atual incorreta")
            }
            if (req.new.length < 6) bad("A nova senha precisa ter pelo menos 6 caracteres")
            store.setPassword(userId, ctx.auth.hash(req.new))
            call.respond(HttpStatusCode.NoContent)
        }
    }

    post("/api/webhooks/mercadopago/{key}") {
        // O webhook só dispara uma sincronização: os dados vêm sempre da API oficial,
        // então uma notificação forjada não consegue inserir nada.
        val settings = call.parameters["key"]?.let { store.settingsByWebhookKey(it) }
        if (settings != null && settings.mpAccessToken != null) {
            ctx.scope.launch { ctx.mercadoPago.sync(settings.userId) }
        }
        call.respond(HttpStatusCode.OK)
    }

    authenticate("jwt") {
        get(ApiRoutes.SYNC) {
            val userId = call.userId
            val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
            val serverTime = Schedule.now()
            call.respond(
                SyncResponse(
                    serverTime = serverTime,
                    events = store.eventsSince(userId, since),
                    transactions = store.transactionsSince(userId, since),
                    settings = store.settings(userId).public,
                    people = store.peopleSince(userId, since),
                    notes = store.notesSince(userId, since),
                    cards = store.cardsSince(userId, since),
                ),
            )
        }

        post(ApiRoutes.FILES) {
            val userId = call.userId
            val declared = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0
            if (declared > MAX_FILE_BYTES) bad("Imagem grande demais (máximo 10 MB)")
            val bytes = call.receive<ByteArray>()
            if (bytes.isEmpty() || bytes.size > MAX_FILE_BYTES) bad("Imagem grande demais (máximo 10 MB)")
            val mime = FileStore.mimeOf(bytes)
            if (!mime.startsWith("image/")) bad("Envie uma imagem PNG, JPG, WEBP ou GIF")
            val id = Store.randomKey(24)
            ctx.files.write(userId, id, bytes)
            val name = call.request.headers["X-File-Name"]?.take(200) ?: "imagem"
            store.addFile(userId, id, name, mime, bytes.size.toLong(), Schedule.now())
            call.respond(UploadedFile(id))
        }

        get("${ApiRoutes.FILES}/{id}") {
            val file = ctx.files.read(call.userId, call.parameters["id"].orEmpty())
                ?: throw ApiException(HttpStatusCode.NotFound, "Imagem não encontrada")
            // O conteúdo de um id nunca muda: o app pode guardar para sempre.
            call.response.headers.append(HttpHeaders.CacheControl, "private, max-age=31536000, immutable")
            call.respondBytes(file.second, ContentType.parse(file.first))
        }

        get(ApiRoutes.AI_LINK) {
            val token = store.aiToken(call.userId, regenerate = false)
            call.respond(AiLink(token, "${ApiRoutes.MCP}/$token"))
        }

        post("${ApiRoutes.AI_LINK}/reset") {
            val token = store.aiToken(call.userId, regenerate = true)
            call.respond(AiLink(token, "${ApiRoutes.MCP}/$token"))
        }

        put("${ApiRoutes.PEOPLE}/{id}") {
            val userId = call.userId
            val saved = store.upsertPerson(userId, call.receive<Person>().validated(call.pathId()), Schedule.now())
            ctx.hub.notify(userId)
            call.respond(saved)
        }

        delete("${ApiRoutes.PEOPLE}/{id}") {
            val userId = call.userId
            store.deletePerson(userId, call.pathId(), Schedule.now())
            ctx.hub.notify(userId)
            call.respond(HttpStatusCode.NoContent)
        }

        put("${ApiRoutes.CARDS}/{id}") {
            val userId = call.userId
            val saved = store.upsertCard(userId, call.receive<CreditCard>().validated(call.pathId()), Schedule.now())
            ctx.hub.notify(userId)
            call.respond(saved)
        }

        delete("${ApiRoutes.CARDS}/{id}") {
            val userId = call.userId
            store.deleteCard(userId, call.pathId(), Schedule.now())
            ctx.hub.notify(userId)
            call.respond(HttpStatusCode.NoContent)
        }

        put("${ApiRoutes.NOTES}/{id}") {
            val userId = call.userId
            val saved = store.upsertNote(userId, call.receive<Note>().validated(call.pathId()), Schedule.now())
            ctx.hub.notify(userId)
            call.respond(saved)
        }

        delete("${ApiRoutes.NOTES}/{id}") {
            val userId = call.userId
            store.deleteNote(userId, call.pathId(), Schedule.now())
            ctx.hub.notify(userId)
            call.respond(HttpStatusCode.NoContent)
        }

        put("${ApiRoutes.EVENTS}/{id}") {
            val userId = call.userId
            val event = call.receive<Event>().validated(call.pathId())
            val now = Schedule.now()
            val saved = store.upsertEvent(userId, event, now)
            ctx.touchAgenda(userId, now)
            ctx.hub.notify(userId)
            call.respond(saved)
        }

        delete("${ApiRoutes.EVENTS}/{id}") {
            val userId = call.userId
            val now = Schedule.now()
            store.deleteEvent(userId, call.pathId(), now)
            ctx.touchAgenda(userId, now)
            ctx.hub.notify(userId)
            call.respond(HttpStatusCode.NoContent)
        }

        put("${ApiRoutes.TRANSACTIONS}/{id}") {
            val userId = call.userId
            val tx = call.receive<Transaction>().validated(call.pathId())
            val saved = store.upsertTransaction(userId, tx, Schedule.now())
            ctx.hub.notify(userId)
            call.respond(saved)
        }

        delete("${ApiRoutes.TRANSACTIONS}/{id}") {
            val userId = call.userId
            store.deleteTransaction(userId, call.pathId(), Schedule.now())
            ctx.hub.notify(userId)
            call.respond(HttpStatusCode.NoContent)
        }

        patch(ApiRoutes.SETTINGS) {
            val userId = call.userId
            val patch = call.receive<SettingsPatch>()
            patch.timezone?.let { runCatching { TimeZone.of(it) }.getOrElse { bad("Fuso horário inválido") } }
            val times = patch.reviewTimes?.map { it.trim() }?.distinct()?.sorted()
            if (times != null) {
                if (times.size > 6) bad("Use no máximo 6 horários")
                if (times.any { Schedule.parseTime(it) == null }) bad("Horário inválido, use HH:mm")
            }
            val saved = store.updateSettings(userId, Schedule.now(), patch.timezone, times)
            ctx.hub.notify(userId)
            call.respond(saved.public)
        }

        post(ApiRoutes.REVIEW) {
            val userId = call.userId
            val date = call.receive<ReviewRequest>().date
            runCatching { LocalDate.parse(date) }.getOrElse { bad("Data inválida") }
            val saved = store.markReviewed(userId, date, Schedule.now())
            ctx.hub.notify(userId)
            call.respond(saved.public)
        }

        get(ApiRoutes.TELEGRAM_LINK) {
            val userId = call.userId
            if (!ctx.telegram.enabled) return@get call.respond(TelegramLink(enabled = false, linked = false))
            val settings = store.settings(userId)
            if (settings.telegramChatId != null) return@get call.respond(TelegramLink(enabled = true, linked = true))
            val code = Store.randomKey(20)
            store.setTelegramLinkCode(userId, code)
            call.respond(TelegramLink(enabled = true, linked = false, url = ctx.telegram.deepLink(code)))
        }

        delete(ApiRoutes.TELEGRAM_LINK) {
            val userId = call.userId
            store.unlinkTelegram(userId, Schedule.now())
            ctx.hub.notify(userId)
            call.respond(HttpStatusCode.NoContent)
        }

        post(ApiRoutes.TELEGRAM_TEST) {
            val chatId = store.settings(call.userId).telegramChatId ?: bad("Telegram ainda não conectado")
            val ok = ctx.telegram.send(chatId, "✅ <b>Tudo certo!</b>\nO Zentro vai te avisar por aqui também.")
            if (!ok) throw ApiException(HttpStatusCode.BadGateway, "Não consegui falar com o Telegram agora")
            call.respond(HttpStatusCode.NoContent)
        }

        get(ApiRoutes.MERCADO_PAGO) {
            call.respond(ctx.mercadoPago.status(call.userId))
        }

        post(ApiRoutes.MERCADO_PAGO) {
            val userId = call.userId
            val token = call.receive<MercadoPagoConnect>().accessToken.trim()
            if (token.length < 20) bad("Cole o Access Token completo (começa com APP_USR-)")
            call.respond(ctx.mercadoPago.connect(userId, token))
        }

        delete(ApiRoutes.MERCADO_PAGO) {
            val userId = call.userId
            store.setMercadoPago(userId, null, null, Schedule.now())
            ctx.hub.notify(userId)
            call.respond(HttpStatusCode.NoContent)
        }

        post(ApiRoutes.MERCADO_PAGO_SYNC) {
            call.respond(ctx.mercadoPago.sync(call.userId))
        }

        webSocket(ApiRoutes.LIVE) {
            val userId = call.userId
            ctx.hub.join(userId, this)
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text && frame.readText() == "ping") send(Frame.Text("pong"))
                }
            } finally {
                ctx.hub.leave(userId, this)
            }
        }
    }
}
