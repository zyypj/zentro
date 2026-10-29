package dev.zentro.server

import dev.zentro.server.db.Store
import dev.zentro.shared.Markdown
import dev.zentro.shared.Note
import dev.zentro.shared.Notes
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.time.Instant
import java.util.Base64

/**
 * Servidor MCP (Model Context Protocol) das notas: permite que o Claude e o ChatGPT leiam suas
 * anotações quando você cita uma nota numa conversa ("faz o que está na nota Bed Wars").
 *
 * Transporte "Streamable HTTP" em modo simples: cada POST traz uma mensagem JSON-RPC e recebe a
 * resposta em JSON (sem streaming). Somente leitura — as IAs nunca alteram suas notas.
 *
 * As ferramentas `search` e `fetch` seguem o formato exigido pelo ChatGPT; o Claude usa as mesmas.
 */
class McpServer(private val store: Store, private val files: FileStore) {
    private val supportedVersions = listOf("2025-06-18", "2025-03-26", "2024-11-05")

    /** Processa uma mensagem (ou um lote). Retorna null para notificações, que não têm resposta. */
    suspend fun handle(userId: Long, body: JsonElement): JsonElement? = when (body) {
        is JsonArray -> JsonArray(body.mapNotNull { handleOne(userId, it.jsonObject) }).takeIf { it.isNotEmpty() }
        is JsonObject -> handleOne(userId, body)
        else -> error(JsonNull, -32600, "Requisição inválida")
    }

    private suspend fun handleOne(userId: Long, msg: JsonObject): JsonElement? {
        val id = msg["id"] ?: return null // notificação (ex.: notifications/initialized)
        val method = msg["method"]?.jsonPrimitive?.contentOrNull ?: return error(id, -32600, "Requisição inválida")
        val params = msg["params"] as? JsonObject ?: JsonObject(emptyMap())
        return try {
            when (method) {
                "initialize" -> result(id, initialize(params))
                "ping" -> result(id, JsonObject(emptyMap()))
                "tools/list" -> result(id, buildJsonObject { put("tools", tools) })
                "tools/call" -> result(id, callTool(userId, params))
                "resources/list" -> result(id, buildJsonObject { putJsonArray("resources") {} })
                "prompts/list" -> result(id, buildJsonObject { putJsonArray("prompts") {} })
                else -> error(id, -32601, "Método não suportado: $method")
            }
        } catch (e: IllegalArgumentException) {
            error(id, -32602, e.message ?: "Parâmetros inválidos")
        }
    }

    private fun initialize(params: JsonObject): JsonObject {
        val requested = params["protocolVersion"]?.jsonPrimitive?.contentOrNull
        return buildJsonObject {
            put("protocolVersion", requested?.takeIf { it in supportedVersions } ?: supportedVersions.first())
            putJsonObject("capabilities") { putJsonObject("tools") { put("listChanged", false) } }
            putJsonObject("serverInfo") {
                put("name", "zentro")
                put("title", "Zentro - notas")
                put("version", BuildInfo.VERSION)
            }
            put(
                "instructions",
                "Acesso somente leitura às anotações pessoais do usuário no Zentro. Quando o usuário citar uma nota " +
                    "(\"a nota X\", \"o que escrevi em X\"), use search para encontrá-la e fetch para ler o conteúdo completo, " +
                    "incluindo tabelas e imagens, e então siga o que está escrito nela. Notas podem citar outras com [[Título]].",
            )
        }
    }

    private val tools = buildJsonArray {
        addJsonObject {
            put("name", "search")
            put("title", "Buscar notas")
            put("description", "Busca nas anotações do usuário por título, conteúdo ou #tag. Retorna as notas mais relevantes (id e título).")
            putJsonObject("inputSchema") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Texto a procurar, por exemplo o nome da nota que o usuário citou")
                    }
                }
                putJsonArray("required") { add("query") }
            }
        }
        addJsonObject {
            put("name", "fetch")
            put("title", "Ler nota")
            put("description", "Lê o conteúdo completo de uma nota (Markdown, com tabelas e imagens). Aceita o id retornado por search ou o título exato.")
            putJsonObject("inputSchema") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("id") {
                        put("type", "string")
                        put("description", "Id da nota (de search) ou o título exato")
                    }
                }
                putJsonArray("required") { add("id") }
            }
        }
        addJsonObject {
            put("name", "list_notes")
            put("title", "Listar notas")
            put("description", "Lista as notas do usuário (título, pasta e data), opcionalmente só de uma pasta.")
            putJsonObject("inputSchema") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("folder") {
                        put("type", "string")
                        put("description", "Pasta, por exemplo Trabalho ou Projetos/Minecraft (opcional)")
                    }
                }
            }
        }
    }

    private suspend fun callTool(userId: Long, params: JsonObject): JsonObject {
        val name = params["name"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Informe o nome da ferramenta")
        val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        fun arg(key: String) = args[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val notes = store.activeNotes(userId)

        return when (name) {
            "search" -> {
                val query = arg("query")
                val hits = if (query.isEmpty()) emptyList() else Notes.search(notes, query).take(10).map { it.note }
                textResult(
                    buildJsonObject {
                        putJsonArray("results") {
                            hits.forEach { note ->
                                addJsonObject {
                                    put("id", note.id)
                                    put("title", note.title.ifBlank { "Sem título" })
                                    put("url", noteUrl(note))
                                }
                            }
                        }
                    }.toString(),
                )
            }
            "fetch" -> {
                val key = arg("id")
                val note = notes.firstOrNull { it.id == key } ?: Notes.findByTitle(notes, key)
                    ?: return textResult("Nota não encontrada: \"$key\". Use search para procurar pelo nome.", isError = true)
                fetchResult(userId, note, notes)
            }
            "list_notes" -> {
                val folder = arg("folder")
                val list = notes.filter { folder.isEmpty() || Notes.inFolder(it, folder) }.sortedByDescending { it.updatedAt }
                textResult(
                    if (list.isEmpty()) "Nenhuma nota encontrada."
                    else list.joinToString("\n") { "- ${it.title.ifBlank { "Sem título" }} (pasta: ${it.folder}, id: ${it.id}, atualizada em ${Instant.ofEpochMilli(it.updatedAt)})" },
                )
            }
            else -> textResult("Ferramenta desconhecida: $name", isError = true)
        }
    }

    /** Conteúdo da nota no formato do `fetch` + as imagens dela, para a IA "ver" os prints. */
    private fun fetchResult(userId: Long, note: Note, all: List<Note>): JsonObject {
        val linked = Notes.links(note.content).mapNotNull { Notes.findByTitle(all, it.target)?.title }.distinct()
        val document = buildJsonObject {
            put("id", note.id)
            put("title", note.title.ifBlank { "Sem título" })
            put("text", note.content)
            put("url", noteUrl(note))
            putJsonObject("metadata") {
                put("folder", note.folder)
                put("tags", Notes.tags(note.content).joinToString(", "))
                put("updated_at", Instant.ofEpochMilli(note.updatedAt).toString())
                if (linked.isNotEmpty()) put("linked_notes", linked.joinToString(", "))
            }
        }
        val images = Markdown.images(note.content).mapNotNull { img ->
            val fileId = Markdown.fileId(img.src) ?: return@mapNotNull null
            files.read(userId, fileId)?.takeIf { it.second.size <= MAX_IMAGE_BYTES }
        }.take(MAX_IMAGES)
        return buildJsonObject {
            putJsonArray("content") {
                addJsonObject {
                    put("type", "text")
                    put("text", document.toString())
                }
                images.forEach { (mime, bytes) ->
                    addJsonObject {
                        put("type", "image")
                        put("data", Base64.getEncoder().encodeToString(bytes))
                        put("mimeType", mime)
                    }
                }
            }
            put("isError", false)
        }
    }

    private fun noteUrl(note: Note) = "zentro://nota/${note.id}"

    private fun textResult(text: String, isError: Boolean = false) = buildJsonObject {
        putJsonArray("content") {
            addJsonObject {
                put("type", "text")
                put("text", text)
            }
        }
        put("isError", isError)
    }

    private fun result(id: JsonElement, result: JsonObject) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }

    private fun error(id: JsonElement, code: Int, message: String) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
        }
    }

    private companion object {
        const val MAX_IMAGES = 5
        const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    }
}

/** Guarda as imagens das notas em data/files/<usuário>/<id>. */
class FileStore(private val root: File) {
    fun dir(userId: Long) = File(root, userId.toString()).apply { mkdirs() }

    fun write(userId: Long, id: String, bytes: ByteArray) = File(dir(userId), id).writeBytes(bytes)

    /** (tipo, conteúdo) — o tipo vem do próprio arquivo para não depender do banco. */
    fun read(userId: Long, id: String): Pair<String, ByteArray>? {
        if (!id.matches(Regex("^[A-Za-z0-9_-]{1,40}$"))) return null
        val file = File(dir(userId), id).takeIf { it.isFile } ?: return null
        val bytes = file.readBytes()
        return mimeOf(bytes) to bytes
    }

    companion object {
        /** Detecta o tipo pela assinatura do arquivo; só aceitamos imagens comuns. */
        fun mimeOf(bytes: ByteArray): String = when {
            bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "image/png"
            bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
            bytes.size > 12 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WEBP" -> "image/webp"
            bytes.size > 6 && String(bytes, 0, 3) == "GIF" -> "image/gif"
            else -> "application/octet-stream"
        }
    }
}
