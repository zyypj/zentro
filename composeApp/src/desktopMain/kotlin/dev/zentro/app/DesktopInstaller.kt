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
class DesktopInstaller(private val startHidden: Boolean) : Installer {
    private val os = System.getProperty("os.name").lowercase()
    private val appPath: String? = System.getProperty("jpackage.app-path")
    private val tmp = File(System.getProperty("java.io.tmpdir"))

    override val assetSuffix: String? = if (os.contains("win") && appPath != null) ".msi" else null

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

    override fun launch(version: String): Boolean {
        val msi = file(version)
        val app = appPath ?: return false
        if (!msi.exists()) return false
        runCatching { attemptFile.writeText(version) }
        val script = File(tmp, "zentro-update.cmd")
        script.writeText(
            listOf(
                "@echo off",
                "timeout /t 2 /nobreak >nul",
                "msiexec /i \"${msi.absolutePath}\" /passive /norestart",
                "start \"\" \"$app\"" + if (startHidden) " --minimized" else "",
                "del \"${msi.absolutePath}\"",
                "del \"%~f0\"",
            ).joinToString("\r\n"),
        )
        // "start" desacopla o script: ele continua rodando depois que o Zentro fechar.
        ProcessBuilder("cmd", "/c", "start", "Zentro Update", "/min", "cmd", "/c", script.absolutePath).start()
        exitProcess(0)
    }
}
