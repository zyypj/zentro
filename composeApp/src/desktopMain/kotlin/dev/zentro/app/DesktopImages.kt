package dev.zentro.app

import java.awt.FileDialog
import java.awt.Frame
import java.awt.Image
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/** Escolher, colar e reduzir imagens no desktop (Java2D/ImageIO, sem bibliotecas extras). */
object DesktopImages {
    private const val MAX_SIDE = 1920
    private const val KEEP_IF_SMALLER = 1_500_000
    private val extensions = listOf("png", "jpg", "jpeg", "gif", "webp")

    /** Seletor de arquivos nativo do sistema (precisa rodar na thread da interface). */
    fun chooseFile(): File? {
        val dialog = FileDialog(null as Frame?, "Escolher imagem", FileDialog.LOAD).apply {
            setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in extensions }
            file = extensions.joinToString(";") { "*.$it" }
            isVisible = true
        }
        val name = dialog.file ?: return null
        return File(dialog.directory, name).takeIf { it.isFile }
    }

    fun isImageFile(file: File) = file.isFile && file.extension.lowercase() in extensions

    /** Lê e reduz arquivos de imagem (arrastados ou copiados no Explorer); ignora o que não for imagem. */
    fun fromFiles(files: List<File>): List<Pair<ByteArray, String>> =
        files.filter(::isImageFile).take(10).mapNotNull { f -> runCatching { shrink(f.readBytes()) to f.name }.getOrNull() }

    /** Tem imagem na área de transferência? Checagem rápida, sem ler a imagem. */
    fun clipboardHasImage(): Boolean = runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor) ||
            (clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor) && clipboardFiles().any(::isImageFile))
    }.getOrDefault(false)

    @Suppress("UNCHECKED_CAST")
    private fun clipboardFiles(): List<File> =
        Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.javaFileListFlavor) as? List<File> ?: emptyList()

    /**
     * Imagens da área de transferência: um print (Win+Shift+S), "copiar imagem" do navegador ou
     * arquivos de imagem copiados no Explorer (Ctrl+C num arquivo).
     */
    fun fromClipboard(): List<Pair<ByteArray, String>> = runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        when {
            clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor) -> {
                val image = clipboard.getData(DataFlavor.imageFlavor) as? Image ?: return emptyList()
                listOf(encode(toBuffered(image), forcePng = true) to "print.png")
            }
            clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor) -> fromFiles(clipboardFiles())
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    /** Reduz fotos grandes (lado maior até 1920 px) para a nota abrir rápido no celular e no PC. */
    fun shrink(bytes: ByteArray): ByteArray {
        val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return bytes // WEBP etc.: envia como está
        val isGif = bytes.size > 3 && String(bytes, 0, 3) == "GIF"
        if (isGif || (bytes.size <= KEEP_IF_SMALLER && maxOf(image.width, image.height) <= MAX_SIDE)) return bytes
        return encode(scale(image), forcePng = false)
    }

    private fun toBuffered(image: Image): BufferedImage {
        if (image is BufferedImage) return image
        val buffered = BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB)
        buffered.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
        return buffered
    }

    private fun scale(image: BufferedImage): BufferedImage {
        val side = maxOf(image.width, image.height)
        if (side <= MAX_SIDE) return image
        val ratio = MAX_SIDE.toDouble() / side
        val w = (image.width * ratio).toInt().coerceAtLeast(1)
        val h = (image.height * ratio).toInt().coerceAtLeast(1)
        val type = if (image.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        return BufferedImage(w, h, type).also { out ->
            out.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                drawImage(image, 0, 0, w, h, null)
                dispose()
            }
        }
    }

    /** PNG quando há transparência (ou for print), JPEG 85% para fotos. */
    private fun encode(image: BufferedImage, forcePng: Boolean): ByteArray {
        val scaled = scale(image)
        val out = ByteArrayOutputStream()
        if (forcePng || scaled.colorModel.hasAlpha()) {
            ImageIO.write(scaled, "png", out)
        } else {
            val writer = ImageIO.getImageWritersByFormatName("jpg").next()
            val params = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = 0.85f
            }
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                writer.write(null, IIOImage(scaled, null, null), params)
            }
            writer.dispose()
        }
        return out.toByteArray()
    }
}
