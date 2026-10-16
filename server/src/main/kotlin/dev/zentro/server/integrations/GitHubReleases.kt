package dev.zentro.server.integrations

import dev.zentro.shared.BuildVersion
import dev.zentro.shared.UpdateInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory

/**
 * Consulta a última release no GitHub. O resultado fica em cache por 30 minutos e é servido
 * aos apps em /api/update — assim eles não esbarram no limite de requisições do GitHub.
 */
class GitHubReleases(private val http: HttpClient, private val repo: String) {
    private val log = LoggerFactory.getLogger(GitHubReleases::class.java)
    private val lock = Mutex()
    private var cached: UpdateInfo? = null
    private var cachedAt = 0L

    suspend fun latest(force: Boolean = false): UpdateInfo? = lock.withLock {
        val now = System.currentTimeMillis()
        if (!force && cached != null && now - cachedAt < CACHE_MS) return cached
        val fresh = runCatching { fetch() }
            .onFailure { log.warn("Não consegui consultar as releases do GitHub ($repo): ${it.message}") }
            .getOrNull()
        if (fresh != null) {
            cached = fresh
            cachedAt = now
        }
        cached
    }

    private suspend fun fetch(): UpdateInfo? {
        val response = http.get("https://api.github.com/repos/$repo/releases/latest") {
            header(HttpHeaders.UserAgent, "Zentro/${BuildVersion.CURRENT}")
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }
        if (!response.status.isSuccess()) return null
        val body = response.body<JsonObject>()
        fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val tag = body.str("tag_name") ?: return null
        val assets = body["assets"]?.jsonArray.orEmpty().mapNotNull { element ->
            val asset = element.jsonObject
            val name = asset.str("name") ?: return@mapNotNull null
            val url = asset.str("browser_download_url") ?: return@mapNotNull null
            name to url
        }.toMap()
        return UpdateInfo(
            version = tag.removePrefix("v"),
            pageUrl = body.str("html_url") ?: "https://github.com/$repo/releases",
            notes = body.str("body").orEmpty().take(4000),
            publishedAt = body.str("published_at").orEmpty(),
            assets = assets,
        )
    }

    private companion object {
        const val CACHE_MS = 30 * 60_000L
    }
}
