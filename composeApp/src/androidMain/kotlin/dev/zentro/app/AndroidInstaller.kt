package dev.zentro.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dev.zentro.app.data.ApiClient
import dev.zentro.app.data.Installer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Baixa o APK novo e abre o instalador do Android. Por segurança o sistema sempre pede um toque
 * para confirmar; na primeira vez também pede para permitir "instalar apps desta fonte".
 */
class AndroidInstaller(private val context: Context) : Installer {
    override val assetSuffix: String = ".apk"

    private fun file(version: String): File =
        File(File(context.cacheDir, "updates").apply { mkdirs() }, "Zentro-$version.apk")

    override suspend fun download(url: String, version: String, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val target = file(version)
        val part = File(target.path + ".part")
        part.outputStream().buffered().use { out ->
            ApiClient.download(url, { bytes, count -> out.write(bytes, 0, count) }, onProgress)
        }
        if (part.length() < 100_000) {
            part.delete()
            return@withContext false
        }
        // Apaga instaladores antigos para não ocupar espaço.
        target.parentFile?.listFiles()?.filter { it.name != part.name }?.forEach { it.delete() }
        part.renameTo(target)
    }

    override fun launch(version: String): Boolean {
        val apk = file(version)
        if (!apk.exists()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }
}
