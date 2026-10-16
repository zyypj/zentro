package dev.zentro.app

import androidx.compose.ui.awt.ComposeWindow
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference

/**
 * Pinta a barra de título nativa do Windows com as cores do app. Usamos a própria barra do
 * sistema (via DWM) em vez de desenhar uma falsa: arrastar, encaixar nas bordas (Snap), maximizar
 * com duplo clique e redimensionar continuam funcionando exatamente como em qualquer app do Windows.
 *
 * Windows 11: cor de fundo, do texto e da borda. Windows 10: só o modo escuro. Outros sistemas: nada muda.
 */
object TitleBar {
    private interface Dwm : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int
    }

    private const val USE_IMMERSIVE_DARK_MODE = 20
    private const val USE_IMMERSIVE_DARK_MODE_OLD = 19
    private const val BORDER_COLOR = 34
    private const val CAPTION_COLOR = 35
    private const val TEXT_COLOR = 36

    private val dwm: Dwm? by lazy {
        if (!System.getProperty("os.name").lowercase().contains("win")) null
        else runCatching { Native.load("dwmapi", Dwm::class.java) }.getOrNull()
    }

    /** Converte 0xRRGGBB para o formato COLORREF do Windows (0x00BBGGRR). */
    private fun colorRef(rgb: Int): Int = ((rgb and 0xFF) shl 16) or (rgb and 0xFF00) or ((rgb shr 16) and 0xFF)

    fun apply(window: ComposeWindow, dark: Boolean) {
        val api = dwm ?: return
        runCatching {
            val hwnd = Pointer(window.windowHandle)
            fun set(attribute: Int, value: Int) = api.DwmSetWindowAttribute(hwnd, attribute, IntByReference(value), 4)

            if (set(USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0) != 0) set(USE_IMMERSIVE_DARK_MODE_OLD, if (dark) 1 else 0)
            // Mesma cor da barra lateral do app, para a janela parecer uma peça só.
            val caption = if (dark) 0x12151A else 0xFAFAFD
            set(CAPTION_COLOR, colorRef(caption))
            set(BORDER_COLOR, colorRef(caption))
            set(TEXT_COLOR, colorRef(if (dark) 0xE9EAEE else 0x16181D))
        }
    }
}
