package dev.zentro.server

import dev.zentro.server.integrations.GitHubReleases
import dev.zentro.shared.BuildVersion
import dev.zentro.shared.Version
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.util.zip.ZipFile

/**
 * Atualização automática do servidor: confere o GitHub ao iniciar e a cada 6 horas. Achando uma
 * versão nova, baixa o jar, confere se ele está íntegro e pede ao [Launcher] para trocar e reiniciar.
 * Os avisos que caírem durante os segundos de reinício são enviados assim que o servidor volta.
 */
class SelfUpdater(
    private val config: Config,
    private val releases: GitHubReleases,
    private val http: HttpClient,
    private val restart: () -> Unit,
) {
    private val log = LoggerFactory.getLogger(SelfUpdater::class.java)

    fun start(scope: CoroutineScope) {
        val jar = currentJar()
        when {
            !config.autoUpdate -> log.info("Atualização automática desligada (ZENTRO_AUTO_UPDATE=true para ligar)")
            System.getProperty("zentro.launcher") != "true" || jar == null -> log.info("Atualização automática indisponível fora do jar")
            else -> {
                log.info("Atualização automática ligada (${config.githubRepo})")
                scope.launch {
                    delay(30_000)
                    while (isActive) {
                        runCatching { check(jar) }.onFailure { log.warn("Falha ao verificar atualização: ${it.message}") }
                        delay(6 * 3_600_000L)
                    }
                }
            }
        }
    }

    private suspend fun check(jar: File) {
        val info = releases.latest(force = true) ?: return
        if (!Version.isNewer(info.version, BuildVersion.CURRENT)) return
        val url = info.asset("zentro-server.jar") ?: return
        log.info("Nova versão ${info.version} disponível (atual ${BuildVersion.CURRENT}). Baixando...")

        val download = File(jar.parentFile, jar.name + ".download")
        http.prepareGet(url) {
            timeout { requestTimeoutMillis = 15 * 60_000L }
        }.execute { response ->
            check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
            withContext(Dispatchers.IO) { download.outputStream().use { response.bodyAsChannel().copyTo(it) } }
        }

        val valid = withContext(Dispatchers.IO) {
            runCatching { ZipFile(download).use { it.getEntry("dev/zentro/server/MainKt.class") != null } }.getOrDefault(false)
        }
        if (!valid) {
            download.delete()
            log.warn("O arquivo baixado não é um servidor Zentro válido. Ignorando.")
            return
        }
        val ready = File(jar.path + ".new")
        if (!download.renameTo(ready)) {
            ready.delete()
            download.renameTo(ready)
        }
        log.info("Versão ${info.version} baixada. Reiniciando para atualizar...")
        restart()
    }
}
