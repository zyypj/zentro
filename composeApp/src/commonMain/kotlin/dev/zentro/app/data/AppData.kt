package dev.zentro.app.data

import dev.zentro.shared.Event
import dev.zentro.shared.Note
import dev.zentro.shared.Person
import dev.zentro.shared.SettingsPatch
import dev.zentro.shared.Transaction
import dev.zentro.shared.UserDto
import dev.zentro.shared.UserSettings
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class Session(val serverUrl: String, val token: String, val user: UserDto)

@Serializable
enum class OpKind { UPSERT_EVENT, DELETE_EVENT, UPSERT_TX, DELETE_TX, REVIEW, SETTINGS, UPSERT_PERSON, DELETE_PERSON, UPSERT_NOTE, DELETE_NOTE }

/**
 * Uma alteração feita no aparelho que ainda não chegou ao servidor.
 * Fica guardada em disco e é reenviada até dar certo — nada se perde sem internet.
 */
@Serializable
data class PendingOp(
    val kind: OpKind,
    val targetId: String = "",
    val event: Event? = null,
    val transaction: Transaction? = null,
    val person: Person? = null,
    val note: Note? = null,
    val date: String? = null,
    val patch: SettingsPatch? = null,
    val opId: String = Uuid.random().toString(),
)

/** Tudo que o app guarda localmente: funciona offline e serve de base para os alarmes. */
@Serializable
data class AppData(
    val session: Session? = null,
    val events: Map<String, Event> = emptyMap(),
    val transactions: Map<String, Transaction> = emptyMap(),
    val people: Map<String, Person> = emptyMap(),
    val notes: Map<String, Note> = emptyMap(),
    val settings: UserSettings = UserSettings(),
    val outbox: List<PendingOp> = emptyList(),
    val lastSync: Long = 0,
    val lastSyncOk: Long? = null,
) {
    val activeEvents: List<Event> get() = events.values.filter { !it.deleted }
    val activeTransactions: List<Transaction> get() = transactions.values.filter { !it.deleted }
    val activePeople: List<Person> get() = people.values.filter { !it.deleted }.sortedBy { it.name.lowercase() }
    val activeNotes: List<Note> get() = notes.values.filter { !it.deleted }

    fun personName(id: String?): String? = id?.let { people[it] }?.takeIf { !it.deleted }?.name
}

data class SyncStatus(
    val syncing: Boolean = false,
    val online: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false,
)

fun newId(): String = Uuid.random().toString()
