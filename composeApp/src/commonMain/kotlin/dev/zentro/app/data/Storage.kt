package dev.zentro.app.data

/** Leitura e escrita de arquivos pequenos na pasta privada do app (cada plataforma implementa). */
interface FileStorage {
    fun read(name: String): String?

    /** Precisa ser atômica: grava num temporário e renomeia, para nunca corromper os dados. */
    fun write(name: String, content: String)

    /** Arquivos binários (cache das imagens das notas). */
    fun readBytes(name: String): ByteArray?

    fun writeBytes(name: String, bytes: ByteArray)
}
