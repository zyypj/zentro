package dev.zentro.app

import dev.zentro.app.data.FileStorage
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Pasta de dados do app de acordo com o sistema operacional. */
fun appDirectory(): File {
    val os = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    return when {
        os.contains("win") -> File(System.getenv("APPDATA") ?: home, "Zentro")
        os.contains("mac") -> File(home, "Library/Application Support/Zentro")
        else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "zentro")
    }.apply { mkdirs() }
}

class DesktopStorage(private val dir: File) : FileStorage {
    override fun read(name: String): String? = File(dir, name).takeIf { it.exists() }?.readText()

    override fun readBytes(name: String): ByteArray? = File(dir, name).takeIf { it.exists() }?.readBytes()

    override fun writeBytes(name: String, bytes: ByteArray) {
        val target = File(dir, name)
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    override fun write(name: String, content: String) {
        val target = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(content)
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

/** Sons gerados na hora (sem arquivos de áudio), para o app continuar leve. */
object Sound {
    private val format = AudioFormat(44_100f, 16, 1, true, false)

    private fun play(notes: List<Pair<Double, Int>>) = runCatching {
        AudioSystem.getSourceDataLine(format).use { line ->
            line.open(format)
            line.start()
            for ((freq, ms) in notes) {
                val n = (format.sampleRate * ms / 1000).toInt()
                val buffer = ByteArray(n * 2)
                val fade = format.sampleRate * 0.012
                for (i in 0 until n) {
                    val envelope = min(1.0, min(i, n - i) / fade)
                    val v = if (freq <= 0) 0 else (sin(2 * PI * freq * i / format.sampleRate) * 0.4 * envelope * Short.MAX_VALUE).toInt()
                    buffer[2 * i] = v.toByte()
                    buffer[2 * i + 1] = (v shr 8).toByte()
                }
                line.write(buffer, 0, buffer.size)
            }
            line.drain()
        }
    }

    fun chime() {
        thread(isDaemon = true) { play(listOf(784.0 to 130, 0.0 to 40, 1175.0 to 220)) }
    }

    /** Toque insistente dos compromissos críticos: repete até [stop]. */
    class AlarmLoop {
        @Volatile private var running = false

        fun start() {
            if (running) return
            running = true
            thread(isDaemon = true, name = "zentro-alarm") {
                while (running) {
                    play(listOf(988.0 to 160, 0.0 to 60, 988.0 to 160, 0.0 to 60, 1319.0 to 320))
                    Thread.sleep(700)
                }
            }
        }

        fun stop() {
            running = false
        }
    }
}

/** Liga/desliga a inicialização junto com o sistema — essencial para os avisos não dependerem de abrir o app. */
object AutoStart {
    private val appPath: String? = System.getProperty("jpackage.app-path")
    private val os = System.getProperty("os.name").lowercase()
    private const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"

    /** Só funciona no app instalado (o caminho do executável vem do instalador). */
    val supported: Boolean get() = appPath != null

    private val linuxFile get() = File(System.getProperty("user.home"), ".config/autostart/zentro.desktop")
    private val macFile get() = File(System.getProperty("user.home"), "Library/LaunchAgents/dev.zentro.app.plist")

    private fun run(vararg cmd: String): Int = runCatching {
        ProcessBuilder(*cmd).redirectErrorStream(true).start().apply { inputStream.readBytes() }.waitFor()
    }.getOrDefault(-1)

    fun isEnabled(): Boolean = when {
        !supported -> false
        os.contains("win") -> run("reg", "query", RUN_KEY, "/v", "Zentro") == 0
        os.contains("mac") -> macFile.exists()
        else -> linuxFile.exists()
    }

    fun setEnabled(enabled: Boolean) {
        val path = appPath ?: return
        when {
            os.contains("win") ->
                if (enabled) run("reg", "add", RUN_KEY, "/v", "Zentro", "/t", "REG_SZ", "/d", "\"$path\" --minimized", "/f")
                else run("reg", "delete", RUN_KEY, "/v", "Zentro", "/f")
            os.contains("mac") ->
                if (enabled) {
                    macFile.parentFile.mkdirs()
                    macFile.writeText(
                        """<?xml version="1.0" encoding="UTF-8"?>
                        |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                        |<plist version="1.0"><dict>
                        |<key>Label</key><string>dev.zentro.app</string>
                        |<key>ProgramArguments</key><array><string>$path</string><string>--minimized</string></array>
                        |<key>RunAtLoad</key><true/>
                        |</dict></plist>
                        """.trimMargin(),
                    )
                } else macFile.delete()
            else ->
                if (enabled) {
                    linuxFile.parentFile.mkdirs()
                    linuxFile.writeText("[Desktop Entry]\nType=Application\nName=Zentro\nExec=\"$path\" --minimized\nX-GNOME-Autostart-enabled=true\n")
                } else linuxFile.delete()
        }
    }
}
