package dev.zentro.app

import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Ícone na bandeja do Windows. O menu nativo (PopupMenu do AWT) não aceita estilo nenhum, então o
 * clique direito abre um menu próprio, desenhado em Compose ([onMenu] recebe a posição do clique).
 */
class AppTray(private val onOpen: () -> Unit, private val onMenu: (x: Int, y: Int) -> Unit) {
    private var icon: TrayIcon? = null

    val supported: Boolean get() = SystemTray.isSupported()

    fun install() {
        if (!supported || icon != null) return
        val trayIcon = TrayIcon(drawIcon(64), "Zentro").apply {
            isImageAutoSize = true
            addMouseListener(
                object : MouseAdapter() {
                    override fun mouseReleased(e: MouseEvent) {
                        when {
                            e.button == MouseEvent.BUTTON3 || e.isPopupTrigger -> onMenu(e.xOnScreen, e.yOnScreen)
                            e.button == MouseEvent.BUTTON1 -> onOpen()
                        }
                    }
                },
            )
        }
        runCatching { SystemTray.getSystemTray().add(trayIcon) }.onSuccess { icon = trayIcon }
    }

    fun remove() {
        icon?.let { runCatching { SystemTray.getSystemTray().remove(it) } }
        icon = null
    }

    fun notify(title: String, message: String, warning: Boolean) {
        icon?.displayMessage(title, message, if (warning) TrayIcon.MessageType.WARNING else TrayIcon.MessageType.INFO)
    }

    /** Mesmo ícone do app, desenhado pelo mesmo código da marca. */
    private fun drawIcon(size: Int): java.awt.Image =
        ZentroIcon().toAwtImage(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.geometry.Size(size.toFloat(), size.toFloat()))
}
