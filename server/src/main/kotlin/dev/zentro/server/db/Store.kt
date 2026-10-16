package dev.zentro.server.db

import dev.zentro.shared.DEFAULT_TIMEZONE
import dev.zentro.shared.Event
import dev.zentro.shared.Recurrence
import dev.zentro.shared.Transaction
import dev.zentro.shared.TxSource
import dev.zentro.shared.TxType
import dev.zentro.shared.UserDto
import dev.zentro.shared.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import java.io.File
import java.security.SecureRandom
import java.sql.Connection

data class StoredUser(val id: Long, val name: String, val email: String, val passwordHash: String) {
    fun toDto() = UserDto(id, name, email)
}

/** Dados internos de configuração que nunca saem do servidor (tokens, chat id...). */
data class PrivateSettings(
    val userId: Long,
    val public: UserSettings,
    val telegramChatId: Long?,
    val telegramLinkCode: String?,
    val mpAccessToken: String?,
    val mpUserId: Long?,
    val mpWebhookKey: String,
)

/**
 * Acesso ao banco SQLite. Um arquivo só, sem serviço externo — ideal para rodar num container
 * do Pterodactyl. Todas as operações passam por um único thread, o que elimina SQLITE_BUSY.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Store(file: File) {
    private val db = Database.connect(
        url = "jdbc:sqlite:${file.absolutePath}",
        driver = "org.sqlite.JDBC",
        setupConnection = { conn ->
            conn.createStatement().use {
                it.execute("PRAGMA journal_mode=WAL")
                it.execute("PRAGMA busy_timeout=5000")
                it.execute("PRAGMA foreign_keys=ON")
            }
        },
    )
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)

    init {
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(Users, Events, Transactions, Settings, SentNotifications, Meta)
        }
    }

    private suspend fun <T> query(block: () -> T): T = withContext(dispatcher) { transaction(db) { block() } }

    // ---------- usuários ----------

    suspend fun createUser(name: String, email: String, passwordHash: String, now: Long): StoredUser = query {
        val id = Users.insert {
            it[Users.name] = name
            it[Users.email] = email
            it[Users.passwordHash] = passwordHash
            it[createdAt] = now
        }[Users.id]
        insertDefaultSettings(id, now)
        StoredUser(id, name, email, passwordHash)
    }

    suspend fun userByEmail(email: String): StoredUser? = query {
        Users.selectAll().where { Users.email eq email }.firstOrNull()?.toUser()
    }

    suspend fun user(id: Long): StoredUser? = query {
        Users.selectAll().where { Users.id eq id }.firstOrNull()?.toUser()
    }

    suspend fun userCount(): Long = query { Users.selectAll().count() }

    // ---------- agenda ----------

    suspend fun upsertEvent(userId: Long, event: Event, now: Long): Event = query {
        val stored = event.copy(updatedAt = now)
        Events.upsert {
            it[Events.userId] = userId
            it[id] = stored.id
            it[title] = stored.title
            it[notes] = stored.notes
            it[startAt] = stored.startAt
            it[endAt] = stored.endAt
            it[allDay] = stored.allDay
            it[reminders] = stored.reminders.joinToString(",")
            it[critical] = stored.critical
            it[recurrence] = stored.recurrence.name
            it[color] = stored.color
            it[updatedAt] = now
            it[deleted] = stored.deleted
        }
        stored
    }

    suspend fun deleteEvent(userId: Long, id: String, now: Long): Boolean = query {
        Events.update({ (Events.userId eq userId) and (Events.id eq id) }) {
            it[deleted] = true
            it[updatedAt] = now
        } > 0
    }

    suspend fun eventsSince(userId: Long, since: Long): List<Event> = query {
        Events.selectAll().where { (Events.userId eq userId) and (Events.updatedAt greaterEq since) }.map { it.toEvent() }
    }

    suspend fun activeEvents(userId: Long): List<Event> = query {
        Events.selectAll().where { (Events.userId eq userId) and (Events.deleted eq false) }.map { it.toEvent() }
    }

    // ---------- finanças ----------

    suspend fun upsertTransaction(userId: Long, tx: Transaction, now: Long): Transaction = query {
        val stored = tx.copy(updatedAt = now)
        Transactions.upsert {
            it[Transactions.userId] = userId
            it[id] = stored.id
            it[type] = stored.type.name
            it[amountCents] = stored.amountCents
            it[description] = stored.description
            it[category] = stored.category
            it[date] = stored.date
            it[source] = stored.source.name
            it[externalId] = stored.externalId
            it[updatedAt] = now
            it[deleted] = stored.deleted
        }
        stored
    }

    /** Usado pela importação automática: nunca sobrescreve um lançamento existente (nem um que o usuário apagou). */
    suspend fun insertTransactionIfAbsent(userId: Long, tx: Transaction, now: Long): Boolean = query {
        Transactions.insertIgnore {
            it[Transactions.userId] = userId
            it[id] = tx.id
            it[type] = tx.type.name
            it[amountCents] = tx.amountCents
            it[description] = tx.description
            it[category] = tx.category
            it[date] = tx.date
            it[source] = tx.source.name
            it[externalId] = tx.externalId
            it[updatedAt] = now
            it[deleted] = false
        }.insertedCount > 0
    }

    suspend fun deleteTransaction(userId: Long, id: String, now: Long): Boolean = query {
        Transactions.update({ (Transactions.userId eq userId) and (Transactions.id eq id) }) {
            it[deleted] = true
            it[updatedAt] = now
        } > 0
    }

    suspend fun transactionsSince(userId: Long, since: Long): List<Transaction> = query {
        Transactions.selectAll()
            .where { (Transactions.userId eq userId) and (Transactions.updatedAt greaterEq since) }
            .map { it.toTransaction() }
    }

    // ---------- configurações ----------

    suspend fun settings(userId: Long): PrivateSettings = query { loadSettings(userId) }

    suspend fun allSettings(): List<PrivateSettings> = query {
        Settings.selectAll().map { it.toPrivateSettings() }
    }

    suspend fun updateSettings(userId: Long, now: Long, timezone: String?, reviewTimes: List<String>?): PrivateSettings = query {
        Settings.update({ Settings.userId eq userId }) {
            if (timezone != null) it[Settings.timezone] = timezone
            if (reviewTimes != null) it[Settings.reviewTimes] = reviewTimes.joinToString(",")
            it[updatedAt] = now
        }
        loadSettings(userId)
    }

    /** Marca o dia como revisado; nunca volta para uma data anterior. */
    suspend fun markReviewed(userId: Long, date: String, now: Long): PrivateSettings = query {
        val current = loadSettings(userId).public.lastReviewDate
        if (current == null || date > current) {
            Settings.update({ Settings.userId eq userId }) {
                it[lastReviewDate] = date
                it[updatedAt] = now
            }
        }
        loadSettings(userId)
    }

    suspend fun setTelegramLinkCode(userId: Long, code: String?) = query {
        Settings.update({ Settings.userId eq userId }) { it[telegramLinkCode] = code }
    }

    /** Vincula o chat do Telegram ao usuário dono do código. Retorna o usuário vinculado. */
    suspend fun linkTelegram(code: String, chatId: Long, now: Long): Long? = query {
        val row = Settings.selectAll().where { Settings.telegramLinkCode eq code }.firstOrNull() ?: return@query null
        val userId = row[Settings.userId]
        Settings.update({ Settings.userId eq userId }) {
            it[telegramChatId] = chatId
            it[telegramLinkCode] = null
            it[updatedAt] = now
        }
        userId
    }

    suspend fun unlinkTelegram(userId: Long, now: Long) = query {
        Settings.update({ Settings.userId eq userId }) {
            it[telegramChatId] = null
            it[updatedAt] = now
        }
    }

    suspend fun userByTelegramChat(chatId: Long): Long? = query {
        Settings.selectAll().where { Settings.telegramChatId eq chatId }.firstOrNull()?.get(Settings.userId)
    }

    suspend fun setMercadoPago(userId: Long, token: String?, mpUserId: Long?, now: Long) = query {
        Settings.update({ Settings.userId eq userId }) {
            it[mpAccessToken] = token
            it[Settings.mpUserId] = mpUserId
            if (token == null) it[mpLastSync] = null
            it[updatedAt] = now
        }
    }

    suspend fun setMercadoPagoSynced(userId: Long, now: Long) = query {
        Settings.update({ Settings.userId eq userId }) {
            it[mpLastSync] = now
            it[updatedAt] = now
        }
    }

    suspend fun settingsByWebhookKey(key: String): PrivateSettings? = query {
        Settings.selectAll().where { Settings.mpWebhookKey eq key }.firstOrNull()?.toPrivateSettings()
    }

    // ---------- controle de avisos ----------

    /** Registra o envio; retorna false se esse aviso já tinha sido enviado. */
    suspend fun markSent(key: String, now: Long): Boolean = query {
        SentNotifications.insertIgnore {
            it[SentNotifications.key] = key
            it[sentAt] = now
        }.insertedCount > 0
    }

    suspend fun purgeSent(olderThan: Long) = query {
        SentNotifications.deleteWhere { sentAt less olderThan }
    }

    suspend fun meta(key: String): String? = query {
        Meta.selectAll().where { Meta.key eq key }.firstOrNull()?.get(Meta.value)
    }

    suspend fun setMeta(key: String, value: String) = query {
        Meta.upsert {
            it[Meta.key] = key
            it[Meta.value] = value
        }
    }

    // ---------- mapeamentos ----------

    private fun insertDefaultSettings(userId: Long, now: Long) {
        val defaults = UserSettings()
        Settings.insert {
            it[Settings.userId] = userId
            it[timezone] = DEFAULT_TIMEZONE
            it[reviewTimes] = defaults.reviewTimes.joinToString(",")
            it[mpWebhookKey] = randomKey(24)
            it[updatedAt] = now
        }
    }

    private fun loadSettings(userId: Long): PrivateSettings =
        Settings.selectAll().where { Settings.userId eq userId }.first().toPrivateSettings()

    private fun ResultRow.toUser() = StoredUser(this[Users.id], this[Users.name], this[Users.email], this[Users.passwordHash])

    private fun ResultRow.toEvent() = Event(
        id = this[Events.id],
        title = this[Events.title],
        notes = this[Events.notes],
        startAt = this[Events.startAt],
        endAt = this[Events.endAt],
        allDay = this[Events.allDay],
        reminders = this[Events.reminders].split(",").mapNotNull { it.trim().toIntOrNull() },
        critical = this[Events.critical],
        recurrence = runCatching { Recurrence.valueOf(this[Events.recurrence]) }.getOrDefault(Recurrence.NONE),
        color = this[Events.color],
        updatedAt = this[Events.updatedAt],
        deleted = this[Events.deleted],
    )

    private fun ResultRow.toTransaction() = Transaction(
        id = this[Transactions.id],
        type = TxType.valueOf(this[Transactions.type]),
        amountCents = this[Transactions.amountCents],
        description = this[Transactions.description],
        category = this[Transactions.category],
        date = this[Transactions.date],
        source = runCatching { TxSource.valueOf(this[Transactions.source]) }.getOrDefault(TxSource.MANUAL),
        externalId = this[Transactions.externalId],
        updatedAt = this[Transactions.updatedAt],
        deleted = this[Transactions.deleted],
    )

    private fun ResultRow.toPrivateSettings() = PrivateSettings(
        userId = this[Settings.userId],
        public = UserSettings(
            timezone = this[Settings.timezone],
            reviewTimes = this[Settings.reviewTimes].split(",").map { it.trim() }.filter { it.isNotEmpty() },
            lastReviewDate = this[Settings.lastReviewDate],
            telegramLinked = this[Settings.telegramChatId] != null,
            mercadoPagoConnected = this[Settings.mpAccessToken] != null,
            mercadoPagoLastSync = this[Settings.mpLastSync],
            updatedAt = this[Settings.updatedAt],
        ),
        telegramChatId = this[Settings.telegramChatId],
        telegramLinkCode = this[Settings.telegramLinkCode],
        mpAccessToken = this[Settings.mpAccessToken],
        mpUserId = this[Settings.mpUserId],
        mpWebhookKey = this[Settings.mpWebhookKey],
    )

    companion object {
        private val random = SecureRandom()
        private const val ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun randomKey(length: Int): String = buildString { repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
    }
}
