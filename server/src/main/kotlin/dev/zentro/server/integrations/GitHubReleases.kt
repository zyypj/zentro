package dev.zentro.server.integrations

import dev.zentro.shared.BuildVersion
import dev.zentro.shared.GitHub
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
        val response = http.get(GitHub.latestReleaseUrl(repo)) {
            header(HttpHeaders.UserAgent, "Zentro/${BuildVersion.CURRENT}")
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }
        if (!response.status.isSuccess()) return null
        return GitHub.parseRelease(response.body<JsonObject>(), repo)
    }

    private companion object {
        const val CACHE_MS = 30 * 60_000L
    }
}
