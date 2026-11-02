package dev.zentro.shared

import kotlinx.serialization.Serializable

/** Contrato HTTP entre os apps e o servidor. */
object ApiRoutes {
    const val INFO = "/api/info"
    const val REGISTER = "/api/auth/register"
    const val LOGIN = "/api/auth/login"
    const val PASSWORD = "/api/auth/password"
    const val SYNC = "/api/sync"
    const val EVENTS = "/api/events"
    const val TRANSACTIONS = "/api/transactions"
    const val SETTINGS = "/api/settings"
    const val REVIEW = "/api/settings/review"
    const val TELEGRAM_LINK = "/api/telegram/link"
    const val TELEGRAM_TEST = "/api/telegram/test"
    const val MERCADO_PAGO = "/api/mercadopago"
    const val MERCADO_PAGO_SYNC = "/api/mercadopago/sync"
    const val LIVE = "/api/live"
    const val PEOPLE = "/api/people"
    const val NOTES = "/api/notes"
    const val CARDS = "/api/cards"
    const val UPDATE = "/api/update"
    const val FILES = "/api/files"
    const val AI_LINK = "/api/ai/link"
    const val MCP = "/mcp"

    /** Repositório oficial; o servidor pode apontar para outro com a variável GITHUB_REPO. */
    const val DEFAULT_REPO = "zyypj/zentro"
}

@Serializable
data class ServerInfo(
    val name: String,
    val version: String,
    val registrationOpen: Boolean,
    val telegramEnabled: Boolean,
    val serverTime: Long,
)

@Serializable
data class AuthRequest(val email: String, val password: String, val name: String? = null)

@Serializable
data class PasswordChange(val current: String, val new: String)

@Serializable
data class UserDto(val id: Long, val name: String, val email: String)

@Serializable
data class AuthResponse(val token: String, val user: UserDto)

/**
 * Resposta da sincronização incremental: tudo o que mudou depois de `since`,
 * incluindo itens apagados (deleted = true) para que os outros aparelhos os removam.
 */
@Serializable
data class SyncResponse(
    val serverTime: Long,
    val events: List<Event>,
    val transactions: List<Transaction>,
    val settings: UserSettings,
    val people: List<Person> = emptyList(),
    val notes: List<Note> = emptyList(),
    val cards: List<CreditCard> = emptyList(),
)

@Serializable
data class SettingsPatch(val timezone: String? = null, val reviewTimes: List<String>? = null)

@Serializable
data class ReviewRequest(val date: String)

@Serializable
data class TelegramLink(val enabled: Boolean, val linked: Boolean, val url: String? = null)

@Serializable
data class MercadoPagoConnect(val accessToken: String)

@Serializable
data class MercadoPagoStatus(
    val connected: Boolean,
    val lastSync: Long? = null,
    val imported: Int = 0,
    val message: String? = null,
    /** Caminho do webhook (somado à URL do servidor) para configurar no painel do Mercado Pago. */
    val webhookPath: String? = null,
)

@Serializable
data class ApiError(val message: String)

/** Mensagem enviada pelo WebSocket de tempo real: avisa que há dados novos para sincronizar. */
@Serializable
data class LiveMessage(val type: String, val serverTime: Long)

/** Resposta do envio de um arquivo (imagem de nota). */
@Serializable
data class UploadedFile(val id: String)

/**
 * Chave para conectar o Claude ou o ChatGPT às suas notas via MCP. [mcpPath] já inclui a chave
 * (`/mcp/<chave>`), porque o ChatGPT não deixa configurar cabeçalhos de autenticação.
 */
@Serializable
data class AiLink(val token: String, val mcpPath: String)

/** Última versão publicada no GitHub Releases, com os links de download de cada plataforma. */
@Serializable
data class UpdateInfo(
    val version: String,
    val pageUrl: String,
    val notes: String = "",
    val publishedAt: String = "",
    /** nome do arquivo → link de download */
    val assets: Map<String, String> = emptyMap(),
) {
    fun asset(suffix: String): String? = assets.entries.firstOrNull { it.key.endsWith(suffix, ignoreCase = true) }?.value
}
