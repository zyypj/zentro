package dev.zentro.shared

import kotlinx.serialization.Serializable

/** Contrato HTTP entre os apps e o servidor. */
object ApiRoutes {
    const val INFO = "/api/info"
    const val REGISTER = "/api/auth/register"
    const val LOGIN = "/api/auth/login"
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
)

@Serializable
data class ApiError(val message: String)

/** Mensagem enviada pelo WebSocket de tempo real: avisa que há dados novos para sincronizar. */
@Serializable
data class LiveMessage(val type: String, val serverTime: Long)
