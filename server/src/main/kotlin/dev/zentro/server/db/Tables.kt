package dev.zentro.server.db

import org.jetbrains.exposed.sql.Table

object Users : Table("users") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 100)
    val email = varchar("email", 200).uniqueIndex()
    val passwordHash = varchar("password_hash", 100)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

/** Os ids são gerados pelos apps; a chave composta impede que um usuário sobrescreva dados de outro. */
object Events : Table("events") {
    val userId = long("user_id").references(Users.id)
    val id = varchar("id", 64)
    val title = varchar("title", 300)
    val notes = text("notes")
    val startAt = long("start_at")
    val endAt = long("end_at").nullable()
    val allDay = bool("all_day")
    val reminders = varchar("reminders", 200)
    val critical = bool("critical")
    val recurrence = varchar("recurrence", 16)
    val color = integer("color")
    val personId = varchar("person_id", 64).nullable()
    // Colunas novas precisam de valor padrão para o SQLite aceitar o ALTER TABLE em bancos existentes.
    val billAmountCents = long("bill_amount_cents").nullable()
    val billCategory = varchar("bill_category", 60).default("Contas")
    val paid = text("paid").default("")
    val updatedAt = long("updated_at").index()
    val deleted = bool("deleted")
    override val primaryKey = PrimaryKey(userId, id)
}

object People : Table("people") {
    val userId = long("user_id").references(Users.id)
    val id = varchar("id", 64)
    val name = varchar("name", 80)
    val color = integer("color")
    val updatedAt = long("updated_at").index()
    val deleted = bool("deleted")
    override val primaryKey = PrimaryKey(userId, id)
}

object NotesTable : Table("notes") {
    val userId = long("user_id").references(Users.id)
    val id = varchar("id", 64)
    val title = varchar("title", 300)
    val content = text("content")
    val folder = varchar("folder", 200)
    val icon = varchar("icon", 16)
    val pinned = bool("pinned")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at").index()
    val deleted = bool("deleted")
    override val primaryKey = PrimaryKey(userId, id)
}

object Transactions : Table("transactions") {
    val userId = long("user_id").references(Users.id)
    val id = varchar("id", 64)
    val type = varchar("type", 16)
    val amountCents = long("amount_cents")
    val description = varchar("description", 300)
    val category = varchar("category", 60)
    val date = long("date").index()
    val origin = varchar("source", 20)
    val externalId = varchar("external_id", 100).nullable()
    val updatedAt = long("updated_at").index()
    val deleted = bool("deleted")
    override val primaryKey = PrimaryKey(userId, id)
}

object Settings : Table("settings") {
    val userId = long("user_id").references(Users.id)
    val timezone = varchar("timezone", 60)
    val reviewTimes = varchar("review_times", 200)
    val lastReviewDate = varchar("last_review_date", 10).nullable()
    val telegramChatId = long("telegram_chat_id").nullable()
    val telegramLinkCode = varchar("telegram_link_code", 40).nullable()
    val mpAccessToken = text("mp_access_token").nullable()
    val mpUserId = long("mp_user_id").nullable()
    val mpLastSync = long("mp_last_sync").nullable()
    val mpWebhookKey = varchar("mp_webhook_key", 40)
    /** Chave de acesso das IAs (MCP) às notas; null até o usuário gerar uma. */
    val aiToken = varchar("ai_token", 64).nullable()
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

/** Imagens das notas. O conteúdo fica em data/files/<usuário>/<id>; aqui só os dados do arquivo. */
object Files : Table("files") {
    val userId = long("user_id").references(Users.id)
    val id = varchar("id", 40)
    val name = varchar("name", 200)
    val mime = varchar("mime", 80)
    val size = long("size")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(userId, id)
}

/** Avisos já enviados pelo servidor (Telegram), para nunca mandar o mesmo duas vezes. */
object SentNotifications : Table("sent_notifications") {
    val key = varchar("key", 200)
    val sentAt = long("sent_at").index()
    override val primaryKey = PrimaryKey(key)
}

object Meta : Table("meta") {
    val key = varchar("key", 100)
    val value = text("value")
    override val primaryKey = PrimaryKey(key)
}
