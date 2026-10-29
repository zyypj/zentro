package dev.zentro.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import dev.zentro.app.data.UpdateManager

/** O que muda entre Android e desktop e precisa aparecer na interface comum. */
interface Platform {
    val name: String

    /** Atualização automática a partir do GitHub. */
    val updates: UpdateManager

    /** Lista de verificação "seus avisos estão garantidos?" com botões para corrigir cada item. */
    @Composable
    fun ReliabilityChecklist()

    /** Dispara um aviso de teste em poucos segundos, pelo mesmo caminho dos avisos reais. */
    fun testNotification()

    /** Abre a galeria ou o seletor de arquivos. A imagem já vem reduzida para não pesar. */
    suspend fun pickImage(): PickedImage?

    /** Imagem copiada na área de transferência (para colar com Ctrl+V), quando houver. */
    fun clipboardImage(): PickedImage? = null

    /** Checagem rápida, sem ler a imagem: decide se o Ctrl+V cola uma imagem ou texto. */
    fun hasClipboardImage(): Boolean = false

    fun decodeImage(bytes: ByteArray): ImageBitmap?
}

class PickedImage(val bytes: ByteArray, val name: String)
