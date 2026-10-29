package dev.zentro.app

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import dev.zentro.app.data.Repository
import dev.zentro.app.data.UpdateManager
import dev.zentro.app.ui.ChecklistRow
import dev.zentro.shared.ReminderTrigger
import dev.zentro.shared.Schedule
import java.awt.SystemTray

class DesktopPlatform(
    private val repo: Repository,
    private val notifier: DesktopNotifier,
    override val updates: UpdateManager,
) : Platform {
    override val name: String = "Desktop · " + System.getProperty("os.name")

    @Composable
    override fun ReliabilityChecklist() {
        val data by repo.data.collectAsState()
        var autoStart by remember { mutableStateOf(AutoStart.isEnabled()) }
        Column {
            ChecklistRow(
                ok = autoStart,
                title = "Abrir junto com o computador",
                subtitle = when {
                    !AutoStart.supported -> "Disponível no app instalado"
                    autoStart -> "O Zentro liga sozinho e fica na bandeja"
                    else -> "Sem isso, os avisos só tocam depois que você abrir o app"
                },
                actionLabel = if (AutoStart.supported) "Ativar" else null,
                onAction = {
                    AutoStart.setEnabled(true)
                    autoStart = AutoStart.isEnabled()
                },
            )
            ChecklistRow(
                ok = SystemTray.isSupported(),
                title = "Ícone na bandeja",
                subtitle = if (SystemTray.isSupported()) "Fechar a janela não desliga os avisos" else "Seu sistema não tem bandeja; use a janela flutuante",
            )
            ChecklistRow(
                ok = data.settings.telegramLinked,
                title = "Telegram como reserva",
                subtitle = if (data.settings.telegramLinked) "Você também recebe os avisos no Telegram" else "Conecte abaixo para receber mesmo com o PC desligado",
            )
        }
    }

    override suspend fun pickImage(): PickedImage? {
        val file = withContext(Dispatchers.Swing) { DesktopImages.chooseFile() } ?: return null
        return withContext(Dispatchers.IO) { PickedImage(DesktopImages.shrink(file.readBytes()), file.name) }
    }

    override fun hasClipboardImage(): Boolean = DesktopImages.clipboardHasImage()

    override fun clipboardImage(): PickedImage? =
        DesktopImages.fromClipboard().firstOrNull()?.let { (bytes, name) -> PickedImage(bytes, name) }

    override fun decodeImage(bytes: ByteArray): ImageBitmap? =
        runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

    override fun testNotification() {
        val now = Schedule.now()
        Thread {
            Thread.sleep(5_000)
            notifier.show(ReminderTrigger("teste", "Aviso de teste do Zentro", now + 5_000, 0, now + 5_000, critical = false))
        }.apply { isDaemon = true }.start()
    }
}
