package dev.zentro.app.ui.notes

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Tudo sobre imagens nas notas: carregar para exibir, escolher da galeria/arquivos e colar da
 * área de transferência. [pick] e [paste] devolvem a linha Markdown pronta (`![nome](zentro:id)`),
 * null se nada foi escolhido, ou falha (ex.: sem internet para enviar).
 */
class NoteMedia(
    val load: suspend (src: String) -> ImageBitmap?,
    val pick: suspend () -> Result<String>?,
    val hasClipboardImage: () -> Boolean,
    val paste: suspend () -> Result<String>?,
)
