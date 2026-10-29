package dev.zentro.server

import dev.zentro.server.db.Store
import dev.zentro.shared.AiLink
import dev.zentro.shared.ApiRoutes
import dev.zentro.shared.AuthRequest
import dev.zentro.shared.AuthResponse
import dev.zentro.shared.Note
import dev.zentro.shared.UploadedFile
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

class McpTest {
    // PNG mínimo válido (1x1)
    private val png = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 0x1F, 0x15, 0xC4.toByte(), 0x89.toByte(),
    )

    private fun ApplicationTestBuilder.setup(): HttpClient {
        val dir = Files.createTempDirectory("zentro-mcp").toFile()
        application { zentro(Config(0, dir, "test-secret", true, telegramToken = null), Store(dir.resolve("t.db"))) }
        return createClient { install(ClientContentNegotiation) { json(ZentroJson) } }
    }

    private suspend fun HttpClient.rpc(path: String, body: String): JsonObject =
        Json.parseToJsonElement(post(path) { contentType(ContentType.Application.Json); setBody(body) }.bodyAsText()).jsonObject

    @Test
    fun claudeAndChatGptCanReadNotes() = testApplication {
        val client = setup()
        val auth = client.post(ApiRoutes.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(AuthRequest("eu@zentro.dev", "segredo123", "Eu"))
        }.body<AuthResponse>()

        // imagem enviada pelo app
        val upload = client.post(ApiRoutes.FILES) {
            bearerAuth(auth.token)
            header("X-File-Name", "menu.png")
            setBody(png)
        }.body<UploadedFile>()
        val download = client.get("${ApiRoutes.FILES}/${upload.id}") { bearerAuth(auth.token) }
        assertContentEquals(png, download.readRawBytes())

        client.put("${ApiRoutes.NOTES}/n1") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(Note(id = "n1", title = "Bed Wars", content = "| Slot | Item |\n|---|---|\n| 5 | Menu |\n\n![menu](zentro:${upload.id})", folder = "Projetos/Minecraft"))
        }
        val link = client.get(ApiRoutes.AI_LINK) { bearerAuth(auth.token) }.body<AiLink>()
        assertEquals("/mcp/${link.token}", link.mcpPath)

        val init = client.rpc(link.mcpPath, """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"teste","version":"1"}}}""")
        assertEquals("2025-06-18", init["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content)

        // notificação não tem resposta
        val notified = client.post(link.mcpPath) { contentType(ContentType.Application.Json); setBody("""{"jsonrpc":"2.0","method":"notifications/initialized"}""") }
        assertEquals(HttpStatusCode.Accepted, notified.status)

        val tools = client.rpc(link.mcpPath, """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")["result"]!!.jsonObject["tools"]!!.jsonArray
        assertEquals(listOf("search", "fetch", "list_notes"), tools.map { it.jsonObject["name"]!!.jsonPrimitive.content })

        val search = client.rpc(link.mcpPath, """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"search","arguments":{"query":"bed wars"}}}""")
        val searchText = search["result"]!!.jsonObject["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertEquals("n1", Json.parseToJsonElement(searchText).jsonObject["results"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content)

        // o Claude usa o cabeçalho Authorization em vez da chave no caminho
        val fetch = Json.parseToJsonElement(
            client.post(ApiRoutes.MCP) {
                header("Authorization", "Bearer ${link.token}")
                contentType(ContentType.Application.Json)
                setBody("""{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"fetch","arguments":{"id":"Bed Wars"}}}""")
            }.bodyAsText(),
        ).jsonObject
        val content = fetch["result"]!!.jsonObject["content"]!!.jsonArray
        val doc = Json.parseToJsonElement(content[0].jsonObject["text"]!!.jsonPrimitive.content).jsonObject
        assertTrue(doc["text"]!!.jsonPrimitive.content.contains("| 5 | Menu |"))
        assertEquals("image", content[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("image/png", content[1].jsonObject["mimeType"]!!.jsonPrimitive.content)
    }

    @Test
    fun rejectsWrongKeyAndNonImages() = testApplication {
        val client = setup()
        val auth = client.post(ApiRoutes.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(AuthRequest("eu@zentro.dev", "segredo123", "Eu"))
        }.body<AuthResponse>()
        assertEquals(HttpStatusCode.Unauthorized, client.post("/mcp/chave-errada") { setBody("{}") }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post(ApiRoutes.FILES) { bearerAuth(auth.token); setBody("não é imagem".toByteArray()) }.status)
        val first = client.get(ApiRoutes.AI_LINK) { bearerAuth(auth.token) }.body<AiLink>()
        val reset = client.post("${ApiRoutes.AI_LINK}/reset") { bearerAuth(auth.token) }.body<AiLink>()
        assertTrue(first.token != reset.token)
        assertEquals(HttpStatusCode.Unauthorized, client.post(first.mcpPath) { setBody("{}") }.status)
    }
}
