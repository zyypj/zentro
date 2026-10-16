package dev.zentro.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.zentro.app.data.Repository
import dev.zentro.app.ui.ZColors
import dev.zentro.app.ui.ZentroLogo
import dev.zentro.app.ui.ZentroTheme
import dev.zentro.shared.Format
import dev.zentro.shared.Schedule
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import java.awt.GraphicsEnvironment
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener

private const val MENU_WIDTH = 280
private const val MENU_HEIGHT = 262

/** Menu da bandeja no visual do app, aberto onde você clicou com o botão direito. */
@Composable
fun ApplicationScope.TrayMenu(at: IntOffset, repo: Repository, onOpen: () -> Unit, onExit: () -> Unit, onDismiss: () -> Unit) {
    val data by repo.data.collectAsState()
    val status by repo.status.collectAsState()
    val bounds = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
    // A barra de tarefas costuma ficar embaixo: o menu abre para cima e nunca sai da tela.
    val x = (at.x - MENU_WIDTH + 16).coerceIn(bounds.x + 8, bounds.x + bounds.width - MENU_WIDTH - 8)
    val y = (if (at.y - MENU_HEIGHT > bounds.y) at.y - MENU_HEIGHT - 8 else at.y + 8)
        .coerceIn(bounds.y + 8, bounds.y + bounds.height - MENU_HEIGHT - 8)
    var autoStart by remember { mutableStateOf(AutoStart.isEnabled()) }

    val tz = repo.timeZone
    val next = remember(data.events) {
        val now = Schedule.now()
        Schedule.agendaFor(data.activeEvents, Schedule.today(tz), tz).plus(Schedule.agendaFor(data.activeEvents, Schedule.today(tz).plus(1, DateTimeUnit.DAY), tz))
            .firstOrNull { it.second >= now }
    }

    Window(
        onCloseRequest = onDismiss,
        title = "Zentro",
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        resizable = false,
        state = rememberWindowState(position = WindowPosition(x.dp, y.dp), size = DpSize(MENU_WIDTH.dp, MENU_HEIGHT.dp)),
        onKeyEvent = {
            if (it.key == Key.Escape) onDismiss()
            false
        },
    ) {
        // Fecha ao clicar fora (a janela perde o foco), como um menu de verdade.
        DisposableEffect(Unit) {
            val listener = object : WindowFocusListener {
                override fun windowGainedFocus(e: WindowEvent) = Unit
                override fun windowLostFocus(e: WindowEvent) = onDismiss()
            }
            window.addWindowFocusListener(listener)
            onDispose { window.removeWindowFocusListener(listener) }
        }
        LaunchedEffect(Unit) {
            window.toFront()
            window.requestFocus()
        }
        ZentroTheme {
            val colors = MaterialTheme.colorScheme
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainer)
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp)),
            ) {
                Column(Modifier.padding(6.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ZentroLogo(34)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text("Zentro", style = MaterialTheme.typography.titleSmall)
                            Text(
                                next?.let { (event, start) ->
                                    val local = Schedule.toLocal(start, tz)
                                    val day = if (local.date == Schedule.today(tz)) "" else "amanhã "
                                    "Próximo: ${event.title} · $day${Format.time(local)}"
                                } ?: if (status.error == null) "Nada marcado por enquanto" else "Sem conexão agora",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colors.outlineVariant)
                    MenuItem(Icons.Rounded.Home, "Abrir o Zentro") {
                        onDismiss()
                        onOpen()
                    }
                    if (AutoStart.supported) {
                        MenuItem(if (autoStart) Icons.Rounded.Check else null, "Iniciar com o Windows") {
                            AutoStart.setEnabled(!autoStart)
                            autoStart = AutoStart.isEnabled()
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colors.outlineVariant)
                    MenuItem(Icons.AutoMirrored.Rounded.ExitToApp, "Fechar o Zentro", "Os avisos deste PC param até você abrir de novo", ZColors.Expense) {
                        onDismiss()
                        onExit()
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector?, label: String, hint: String? = null, color: Color = Color.Unspecified, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color)
            if (hint != null) {
                Spacer(Modifier.height(1.dp))
                Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
