package dev.zentro.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.TrayState
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.zentro.app.data.Repository
import dev.zentro.app.data.UpdateManager
import dev.zentro.app.ui.App
import dev.zentro.app.ui.ZColors
import dev.zentro.app.ui.ZentroTheme
import dev.zentro.shared.Format
import dev.zentro.shared.ReminderTrigger
import dev.zentro.shared.Schedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.RandomAccessFile
import javax.swing.JOptionPane
import kotlin.system.exitProcess

/** Ícone desenhado em código (quadrado roxo com "Z"), usado na janela e na bandeja. */
class ZentroIcon : Painter() {
    override val intrinsicSize = Size(256f, 256f)

    override fun DrawScope.onDraw() {
        val s = size.minDimension
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFF8B7CF6), Color(0xFF5B4BDB)), Offset.Zero, Offset(s, s)),
            size = Size(s, s),
            cornerRadius = CornerRadius(s * 0.28f),
        )
        val stroke = Stroke(width = s * 0.12f)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.30f, s * 0.30f)
            lineTo(s * 0.70f, s * 0.30f)
            lineTo(s * 0.30f, s * 0.70f)
            lineTo(s * 0.70f, s * 0.70f)
        }
        drawPath(path, Color.White, style = stroke)
    }
}

/** Garante uma única instância (duas rodando dispararia cada aviso duas vezes). */
private fun acquireSingleInstance(dir: File): Boolean = runCatching {
    val channel = RandomAccessFile(File(dir, "instance.lock"), "rw").channel
    channel.tryLock() != null
}.getOrDefault(true)

fun main(args: Array<String>) {
    val dir = appDirectory()
    if (!acquireSingleInstance(dir)) {
        JOptionPane.showMessageDialog(null, "O Zentro já está aberto. Procure o ícone na bandeja do sistema.", "Zentro", JOptionPane.INFORMATION_MESSAGE)
        exitProcess(0)
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val storage = DesktopStorage(dir)
    val repo = Repository(storage, scope)
    val trayState = TrayState()
    val notifier = DesktopNotifier(repo, storage, trayState)
    val startHidden = "--minimized" in args
    val updates = UpdateManager(repo, DesktopInstaller(startHidden), scope)
    val platform = DesktopPlatform(repo, notifier, updates)
    val alarm = Sound.AlarmLoop()

    // Na primeira execução do app instalado, já liga a inicialização automática.
    if (AutoStart.supported && storage.read("autostart.done") == null) {
        AutoStart.setEnabled(true)
        storage.write("autostart.done", "1")
    }

    notifier.start(scope)
    repo.startLive()
    // Ao abrir: se houver versão nova, baixa e instala sozinho. Com o app aberto, só avisa.
    updates.start()
    scope.launch {
        // Reserva para quando o tempo real estiver indisponível.
        while (true) {
            repo.sync()
            delay(5 * 60_000L)
        }
    }

    application {
        var visible by remember { mutableStateOf(!startHidden) }
        val icon = remember { ZentroIcon() }

        Tray(
            icon = icon,
            state = trayState,
            tooltip = "Zentro",
            onAction = { visible = true },
            menu = {
                Item("Abrir Zentro", onClick = { visible = true })
                Item("Sincronizar agora", onClick = { scope.launch { repo.sync() } })
                Separator()
                Item("Sair (desliga os avisos no PC)", onClick = { exitApplication() })
            },
        )

        val windowState = rememberWindowState(width = 1120.dp, height = 760.dp, position = WindowPosition(Alignment.Center))
        Window(
            onCloseRequest = { visible = false },
            visible = visible,
            title = "Zentro",
            icon = icon,
            state = windowState,
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(420, 640) }
            val dark = isSystemInDarkTheme()
            LaunchedEffect(dark, visible) { TitleBar.apply(window, dark) }
            LaunchedEffect(visible) {
                if (visible) {
                    if (windowState.placement == WindowPlacement.Floating) window.toFront()
                    repo.sync()
                }
            }
            App(repo, platform)
        }

        Toasts(notifier, onOpen = { visible = true })
        CriticalAlerts(notifier, alarm, scope, onOpen = { visible = true })
    }
    exitProcess(0)
}

@Composable
private fun ApplicationScope.Toasts(notifier: DesktopNotifier, onOpen: () -> Unit) {
    val toasts by notifier.toasts.collectAsState()
    val toast = toasts.lastOrNull { !it.critical } ?: return
    val bounds = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
    val width = 380
    val height = 120

    LaunchedEffect(toast.id) {
        delay(25_000)
        notifier.dismissToast(toast.id)
    }

    Window(
        onCloseRequest = { notifier.dismissToast(toast.id) },
        title = "Zentro",
        undecorated = true,
        alwaysOnTop = true,
        focusable = false,
        resizable = false,
        state = rememberWindowState(
            position = WindowPosition((bounds.x + bounds.width - width - 16).dp, (bounds.y + bounds.height - height - 16).dp),
            size = DpSize(width.dp, height.dp),
        ),
    ) {
        ZentroTheme {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxSize().clickable {
                    notifier.dismissToast(toast.id)
                    onOpen()
                },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(5.dp).height(64.dp).background(ZColors.Violet))
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text("Zentro", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(toast.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(toast.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                    Text(
                        "✕",
                        modifier = Modifier.clickable { notifier.dismissToast(toast.id) }.padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ApplicationScope.CriticalAlerts(notifier: DesktopNotifier, alarm: Sound.AlarmLoop, scope: CoroutineScope, onOpen: () -> Unit) {
    val alerts by notifier.critical.collectAsState()
    LaunchedEffect(alerts.isEmpty()) { if (alerts.isEmpty()) alarm.stop() else alarm.start() }
    val trigger: ReminderTrigger = alerts.firstOrNull() ?: return

    Window(
        onCloseRequest = { notifier.snoozeCritical(trigger, scope) },
        title = "Compromisso crítico",
        alwaysOnTop = true,
        resizable = false,
        state = rememberWindowState(position = WindowPosition(Alignment.Center), size = DpSize(520.dp, 340.dp)),
        icon = remember { ZentroIcon() },
    ) {
        val dark = isSystemInDarkTheme()
        LaunchedEffect(trigger.key) {
            TitleBar.apply(window, dark)
            window.toFront()
            window.requestFocus()
        }
        ZentroTheme {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Warning, null, tint = ZColors.Expense, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(trigger.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2)
                    val start = Schedule.toLocal(trigger.occurrenceStart, kotlinx.datetime.TimeZone.currentSystemDefault())
                    Text(
                        if (trigger.minutesBefore == 0) "Agora · ${Format.time(start)}"
                        else "Às ${Format.time(start)} · ${Schedule.describeOffset(trigger.minutesBefore)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { notifier.snoozeCritical(trigger, scope) }, modifier = Modifier.weight(1f).height(52.dp)) {
                            Text("Adiar 5 min")
                        }
                        Button(
                            onClick = {
                                notifier.confirmCritical(trigger)
                                onOpen()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ZColors.Expense),
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Text("Estou ciente") }
                    }
                }
            }
        }
    }
}
