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

    /** Mesmo ícone do app (quadrado roxo com "Z"), desenhado em código para não depender de arquivos. */
    private fun drawIcon(size: Int): BufferedImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        val s = size.toFloat()
        g.paint = GradientPaint(0f, 0f, Color(0x8B7CF6), s, s, Color(0x5B4BDB))
        g.fill(RoundRectangle2D.Float(0f, 0f, s, s, s * 0.56f, s * 0.56f))
        g.color = Color.WHITE
        g.stroke = BasicStroke(s * 0.13f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(
            Path2D.Float().apply {
                moveTo(s * 0.30f, s * 0.30f)
                lineTo(s * 0.70f, s * 0.30f)
                lineTo(s * 0.30f, s * 0.70f)
                lineTo(s * 0.70f, s * 0.70f)
            },
        )
        g.dispose()
        return image
    }
}
