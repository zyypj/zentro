package dev.zentro.server

import dev.zentro.server.db.Store
import dev.zentro.shared.ApiRoutes
import dev.zentro.shared.AuthRequest
import dev.zentro.shared.AuthResponse
import dev.zentro.shared.Event
import dev.zentro.shared.Note
import dev.zentro.shared.Person
import dev.zentro.shared.Schedule
import dev.zentro.shared.SettingsPatch
import dev.zentro.shared.SyncResponse
import dev.zentro.shared.Transaction
import dev.zentro.shared.TxType
import dev.zentro.shared.UserSettings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

class ApiTest {
    private fun ApplicationTestBuilder.setup(allowRegistration: Boolean = true): HttpClient {
        val dir = Files.createTempDirectory("zentro-test").toFile()
        val config = Config(0, dir, "test-secret", allowRegistration, telegramToken = null)
        application { zentro(config, Store(dir.resolve("test.db"))) }
        return createClient { install(ClientContentNegotiation) { json(ZentroJson) } }
    }

    private suspend fun HttpClient.register(email: String = "eu@zentro.dev"): AuthResponse =
        post(ApiRoutes.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(AuthRequest(email, "segredo123", "Eu"))
        }.body()

    @Test
    fun fullAgendaAndFinanceFlow() = testApplication {
        val client = setup()
        val auth = client.register()

        val login = client.post(ApiRoutes.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(AuthRequest("EU@zentro.dev ", "segredo123"))
        }
        assertEquals(HttpStatusCode.OK, login.status)

        val event = Event(id = "evt-1", title = "  Dentista  ", startAt = Schedule.now() + 3_600_000, reminders = listOf(60, 0, 60))
        val saved = client.put("${ApiRoutes.EVENTS}/evt-1") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(event)
        }.body<Event>()
        assertEquals("Dentista", saved.title)
        assertEquals(listOf(60, 0), saved.reminders)

        val tx = Transaction(id = "tx-1", type = TxType.EXPENSE, amountCents = 4590, description = "Almoço", date = Schedule.now())
        assertEquals(HttpStatusCode.OK, client.put("${ApiRoutes.TRANSACTIONS}/tx-1") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(tx)
        }.status)

        val sync = client.get("${ApiRoutes.SYNC}?since=0") { bearerAuth(auth.token) }.body<SyncResponse>()
        assertEquals(listOf("evt-1"), sync.events.map { it.id })
        assertEquals(4590, sync.transactions.single().amountCents)
        // Criar um compromisso conta como "agenda atualizada hoje".
        assertEquals(Schedule.today(Schedule.zone(sync.settings.timezone)).toString(), sync.settings.lastReviewDate)

        client.delete("${ApiRoutes.EVENTS}/evt-1") { bearerAuth(auth.token) }
        val incremental = client.get("${ApiRoutes.SYNC}?since=${sync.serverTime}") { bearerAuth(auth.token) }.body<SyncResponse>()
        assertTrue(incremental.events.single().deleted)
    }

    @Test
    fun usersAreIsolated() = testApplication {
        val client = setup()
        val a = client.register("a@zentro.dev")
        val b = client.register("b@zentro.dev")
        client.put("${ApiRoutes.EVENTS}/same-id") {
            bearerAuth(a.token)
            contentType(ContentType.Application.Json)
            setBody(Event(id = "same-id", title = "Do A", startAt = 0))
        }
        val syncB = client.get("${ApiRoutes.SYNC}?since=0") { bearerAuth(b.token) }.body<SyncResponse>()
        assertTrue(syncB.events.isEmpty())
    }

    @Test
    fun rejectsInvalidInputAndMissingAuth() = testApplication {
        val client = setup()
        val auth = client.register()
        assertEquals(HttpStatusCode.Unauthorized, client.get("${ApiRoutes.SYNC}?since=0").status)
        assertEquals(HttpStatusCode.BadRequest, client.put("${ApiRoutes.EVENTS}/x") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(Event(id = "x", title = "   ", startAt = 0))
        }.status)
        assertEquals(HttpStatusCode.BadRequest, client.patch(ApiRoutes.SETTINGS) {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(SettingsPatch(reviewTimes = listOf("25:99")))
        }.status)
        val ok = client.patch(ApiRoutes.SETTINGS) {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(SettingsPatch(reviewTimes = listOf("21:00", "08:30")))
        }.body<UserSettings>()
        assertEquals(listOf("08:30", "21:00"), ok.reviewTimes)
    }

    @Test
    fun registrationCanBeClosedAfterFirstUser() = testApplication {
        val client = setup(allowRegistration = false)
        client.register("dono@zentro.dev")
        val second = client.post(ApiRoutes.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(AuthRequest("outro@zentro.dev", "segredo123"))
        }
        assertEquals(HttpStatusCode.Forbidden, second.status)
    }

    @Test
    fun peopleAndNotesSync() = testApplication {
        val client = setup()
        val auth = client.register()
        client.put("${ApiRoutes.PEOPLE}/p1") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(Person(id = "p1", name = "  Ana  "))
        }
        client.put("${ApiRoutes.EVENTS}/e1") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(Event(id = "e1", title = "Prova de cálculo", startAt = Schedule.now(), personId = "p1"))
        }
        val note = client.put("${ApiRoutes.NOTES}/n1") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(Note(id = "n1", title = "Reunião", content = "- [ ] enviar proposta #trabalho", folder = "/Trabalho/"))
        }.body<Note>()
        assertEquals("Trabalho", note.folder)
        assertTrue(note.createdAt > 0)

        val sync = client.get("${ApiRoutes.SYNC}?since=0") { bearerAuth(auth.token) }.body<SyncResponse>()
        assertEquals("Ana", sync.people.single().name)
        assertEquals("p1", sync.events.single().personId)
        assertEquals("Reunião", sync.notes.single().title)

        // Editar mantém a data de criação original.
        val edited = client.put("${ApiRoutes.NOTES}/n1") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(note.copy(content = "- [x] enviar proposta", createdAt = 0))
        }.body<Note>()
        assertEquals(note.createdAt, edited.createdAt)

        client.delete("${ApiRoutes.NOTES}/n1") { bearerAuth(auth.token) }
        val after = client.get("${ApiRoutes.SYNC}?since=${sync.serverTime}") { bearerAuth(auth.token) }.body<SyncResponse>()
        assertTrue(after.notes.single().deleted)
    }
}
