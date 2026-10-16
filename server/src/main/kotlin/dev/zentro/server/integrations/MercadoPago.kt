package dev.zentro.server.integrations

import dev.zentro.server.ApiException
import dev.zentro.server.db.Store
import dev.zentro.shared.MercadoPagoStatus
import dev.zentro.shared.Schedule
import dev.zentro.shared.Transaction
import dev.zentro.shared.TxSource
import dev.zentro.shared.TxType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong

/**
 * Importa automaticamente as movimentações da conta Mercado Pago usando a API oficial
 * (`/v1/payments/search`) com o Access Token de produção do próprio usuário.
 *
 * - Pagamentos aprovados em que você é o recebedor viram ENTRADAS (valor líquido).
 * - Pagamentos aprovados em que você é o pagador viram SAÍDAS.
 * Cada pagamento vira um lançamento com id fixo `mp-<id>`, então importar de novo nunca duplica.
 */
class MercadoPago(
    private val http: HttpClient,
    private val store: Store,
    private val onChanged: suspend (Long) -> Unit,
) {
    private val log = LoggerFactory.getLogger(MercadoPago::class.java)
    private val locks = ConcurrentHashMap<Long, Mutex>()
    private val base = "https://api.mercadopago.com"

    fun start(scope: CoroutineScope) {
        scope.launch {
            delay(30_000)
            while (isActive) {
                for (settings in store.allSettings()) {
                    if (settings.mpAccessToken != null) runCatching { sync(settings.userId) }
                }
                delay(15 * 60_000L)
            }
        }
    }

    suspend fun status(userId: Long, imported: Int = 0, message: String? = null): MercadoPagoStatus {
        val s = store.settings(userId)
        return MercadoPagoStatus(
            connected = s.mpAccessToken != null,
            lastSync = s.public.mercadoPagoLastSync,
            imported = imported,
            message = message,
            webhookPath = "/api/webhooks/mercadopago/${s.mpWebhookKey}",
        )
    }

    suspend fun connect(userId: Long, token: String): MercadoPagoStatus {
        val response = http.get("$base/users/me") { bearerAuth(token) }
        if (!response.status.isSuccess()) {
            throw ApiException(HttpStatusCode.BadRequest, "O Mercado Pago recusou esse token. Confira se é o Access Token de produção.")
        }
        val me = response.body<JsonObject>()["id"]?.jsonPrimitive?.longOrNull
            ?: throw ApiException(HttpStatusCode.BadGateway, "Resposta inesperada do Mercado Pago")
        store.setMercadoPago(userId, token, me, Schedule.now())
        return sync(userId)
    }

    suspend fun sync(userId: Long): MercadoPagoStatus = locks.computeIfAbsent(userId) { Mutex() }.withLock {
        val settings = store.settings(userId)
        val token = settings.mpAccessToken ?: return status(userId, message = "Mercado Pago não conectado")
        val me = settings.mpUserId ?: return status(userId, message = "Reconecte o Mercado Pago")
        val now = Schedule.now()

        // Primeira importação: últimos 90 dias. Depois, só a diferença (com 2 dias de folga).
        val days = settings.public.mercadoPagoLastSync
            ?.let { ((now - it) / 86_400_000L + 2).coerceIn(2, 90) } ?: 90

        var imported = 0
        try {
            val seen = HashSet<Long>()
            for (filter in listOf("collector.id", "payer.id")) {
                for (payment in fetch(token, filter, me, days)) {
                    val tx = payment.toTransaction(me) ?: continue
                    if (!seen.add(tx.externalId!!.toLong())) continue
                    if (store.insertTransactionIfAbsent(userId, tx, Schedule.now())) imported++
                }
            }
        } catch (e: ApiException) {
            return status(userId, message = e.message)
        } catch (e: Exception) {
            log.warn("Falha ao sincronizar Mercado Pago do usuário $userId: ${e.message}")
            return status(userId, message = "Não consegui falar com o Mercado Pago agora. Tento de novo em 15 min.")
        }

        store.setMercadoPagoSynced(userId, now)
        if (imported > 0) log.info("Mercado Pago: $imported novas movimentações para o usuário $userId")
        onChanged(userId)
        status(userId, imported, if (imported == 0) "Tudo em dia" else "$imported movimentações importadas")
    }

    private suspend fun fetch(token: String, filter: String, me: Long, days: Long): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        var offset = 0
        while (offset < 2_000) {
            val response = http.get("$base/v1/payments/search") {
                bearerAuth(token)
                parameter("sort", "date_created")
                parameter("criteria", "desc")
                parameter("range", "date_created")
                parameter("begin_date", "NOW-${days}DAYS")
                parameter("end_date", "NOW")
                parameter(filter, me)
                parameter("limit", 100)
                parameter("offset", offset)
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                throw ApiException(HttpStatusCode.Unauthorized, "Token do Mercado Pago expirou ou foi revogado. Conecte de novo.")
            }
            // A busca por pagador pode não estar liberada para todas as contas; nesse caso seguimos só com as entradas.
            if (!response.status.isSuccess()) {
                if (filter == "payer.id") return result
                throw IllegalStateException("HTTP ${response.status.value}")
            }
            val body = response.body<JsonObject>()
            val page = body["results"]?.jsonArray?.map { it.jsonObject }.orEmpty()
            result += page
            val total = body["paging"]?.jsonObject?.get("total")?.jsonPrimitive?.longOrNull ?: 0
            offset += page.size
            if (page.isEmpty() || offset >= total) break
        }
        return result
    }

    private fun JsonObject.toTransaction(me: Long): Transaction? {
        if (str("status") != "approved") return null
        val id = this["id"]?.jsonPrimitive?.longOrNull ?: return null
        val collector = this["collector_id"]?.jsonPrimitive?.longOrNull
        val payer = this["payer"]?.takeIf { it !is JsonNull }?.jsonObject?.get("id")?.primitiveLong()
        val details = this["transaction_details"]?.takeIf { it !is JsonNull }?.jsonObject
        val amount = num("transaction_amount") ?: return null
        val method = str("payment_method_id")
        val date = (str("date_approved") ?: str("date_created"))
            ?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
            ?: return null

        val type: TxType
        val value: Double
        val category: String
        when (me) {
            collector -> {
                type = TxType.INCOME
                value = details?.num("net_received_amount")?.takeIf { it > 0 } ?: amount
                category = if (method == "pix") "Pix recebido" else "Vendas"
            }
            payer -> {
                type = TxType.EXPENSE
                value = details?.num("total_paid_amount")?.takeIf { it > 0 } ?: amount
                category = "Compras"
            }
            else -> return null
        }
        val label = str("description")?.takeIf { it.isNotBlank() }
            ?: if (type == TxType.INCOME) "Recebimento Mercado Pago" else "Pagamento Mercado Pago"
        return Transaction(
            id = "mp-$id",
            type = type,
            amountCents = (value * 100).roundToLong(),
            description = label.take(300),
            category = category,
            date = date,
            source = TxSource.MERCADO_PAGO,
            externalId = id.toString(),
        )
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun JsonElement.primitiveLong(): Long? =
        (this as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toLongOrNull() }
}
