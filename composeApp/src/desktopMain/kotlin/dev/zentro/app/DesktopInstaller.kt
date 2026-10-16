package dev.zentro.app

import dev.zentro.app.data.ApiClient
import dev.zentro.app.data.Installer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.exitProcess

/**
 * No Windows (app instalado pelo .msi): baixa o instalador novo, fecha o Zentro, instala em modo
 * silencioso e abre o app de novo. Em Linux/macOS ou rodando pelo Gradle, só avisa e abre a página da release.
 */
/**
 * [isHidden]: a janela está fechada (só na bandeja)? [isIdle]: dá para reiniciar agora sem
 * atrapalhar (janela escondida ou minimizada e nenhum alarme crítico tocando)?
 */
class DesktopInstaller(private val isHidden: () -> Boolean, private val isIdle: () -> Boolean) : Installer {
    private val os = System.getProperty("os.name").lowercase()
    private val appPath: String? = System.getProperty("jpackage.app-path")
    private val tmp = File(System.getProperty("java.io.tmpdir"))

    override val assetSuffix: String? = if (os.contains("win") && appPath != null) ".msi" else null
    override val installInBackground: Boolean get() = assetSuffix != null
    @Volatile private var waiting = false

    private fun file(version: String) = File(tmp, "Zentro-$version.msi")
    private val attemptFile = File(appDirectory(), "update-attempt.txt")

    override fun autoInstallAllowed(version: String): Boolean =
        runCatching { attemptFile.readText().trim() }.getOrNull() != version

    override suspend fun download(url: String, version: String, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val target = file(version)
        val part = File(target.path + ".part")
        part.outputStream().buffered().use { out ->
            ApiClient.download(url, { bytes, count -> out.write(bytes, 0, count) }, onProgress)
        }
        if (part.length() < 1_000_000) {
            part.delete()
            return@withContext false
        }
        target.delete()
        part.renameTo(target)
    }

    override fun launch(version: String, userRequested: Boolean): Boolean {
        val msi = file(version)
        if (appPath == null || !msi.exists()) return false
        if (!userRequested && !isIdle()) {
            // Atualização pronta com você usando o app: espera minimizar ou fechar a janela.
            if (!waiting) {
                waiting = true
                Thread {
                    while (!isIdle()) Thread.sleep(10_000)
                    install(version, msi)
                }.apply { isDaemon = true }.start()
            }
            return true
        }
        return install(version, msi)
    }

    private fun install(version: String, msi: File): Boolean {
        val app = appPath ?: return false
        runCatching { attemptFile.writeText(version) }
        val script = File(tmp, "zentro-update.cmd")
        script.writeText(
            listOf(
                "@echo off",
                "timeout /t 2 /nobreak >nul",
                "msiexec /i \"${msi.absolutePath}\" /passive /norestart",
                "start \"\" \"$app\"" + if (isHidden()) " --minimized" else "",
                "del \"${msi.absolutePath}\"",
                "del \"%~f0\"",
            ).joinToString("\r\n"),
        )
        // "start" desacopla o script: ele continua rodando depois que o Zentro fechar.
        ProcessBuilder("cmd", "/c", "start", "Zentro Update", "/min", "cmd", "/c", script.absolutePath).start()
        exitProcess(0)
    }
}
