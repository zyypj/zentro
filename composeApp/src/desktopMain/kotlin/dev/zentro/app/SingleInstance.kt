package dev.zentro.app

import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import kotlin.concurrent.thread

/**
 * Garante um Zentro só por computador (dois abertos disparariam cada aviso duas vezes) e faz uma
 * segunda abertura — pela busca do Windows, atalho ou menu Iniciar — trazer a janela que já está
 * aberta para a frente, em vez de abrir outra.
 *
 * A trava é um lock de arquivo mantido enquanto o app roda. O pedido "aparecer" vai por um socket
 * em 127.0.0.1 (só aceita conexões do próprio computador).
 */
object SingleInstance {
    // Precisam ficar referenciados: se o coletor de lixo fechar o canal, a trava some sem aviso.
    private var channel: FileChannel? = null
    private var lock: FileLock? = null
    private var server: ServerSocket? = null

    /** Tenta ser a instância principal. Retorna false se outro Zentro já está aberto. */
    @Synchronized
    fun acquire(dir: File): Boolean {
        if (lock != null) return true
        val ch = runCatching { RandomAccessFile(File(dir, "instance.lock"), "rw").channel }.getOrNull() ?: return true
        val acquired = runCatching { ch.tryLock() }.getOrNull()
        if (acquired == null) {
            ch.close()
            return false
        }
        channel = ch
        lock = acquired
        return true
    }

    /** Fica esperando pedidos de "aparecer" vindos de uma segunda abertura. */
    fun listen(dir: File, onShow: () -> Unit) {
        val socket = runCatching { ServerSocket(0, 8, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return
        server = socket
        runCatching { File(dir, "instance.port").writeText(socket.localPort.toString()) }
        thread(isDaemon = true, name = "zentro-single-instance") {
            while (!socket.isClosed) {
                runCatching {
                    socket.accept().use { client ->
                        client.soTimeout = 2_000
                        if (client.getInputStream().bufferedReader().readLine() == SHOW) onShow()
                    }
                }
            }
        }
    }

    /** Pede ao Zentro que já está aberto para aparecer na tela. Retorna true se ele recebeu. */
    fun requestShow(dir: File): Boolean = runCatching {
        val port = File(dir, "instance.port").readText().trim().toInt()
        Socket(InetAddress.getLoopbackAddress(), port).use { s ->
            s.soTimeout = 2_000
            s.getOutputStream().apply {
                write("$SHOW\n".toByteArray())
                flush()
            }
        }
        true
    }.getOrDefault(false)

    /** Usado nos testes e ao sair. */
    @Synchronized
    fun release() {
        runCatching { server?.close() }
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        server = null
        lock = null
        channel = null
    }

    private const val SHOW = "zentro:show"
}
