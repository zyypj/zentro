package dev.zentro.server

import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** Código de saída que o servidor usa para pedir "aplique a atualização e me reinicie". */
const val EXIT_UPDATE = 3

/** O .jar em execução, ou null quando rodando fora de um jar (ex.: `gradle run`). */
fun currentJar(): File? = runCatching {
    File(Launcher::class.java.protectionDomain.codeSource.location.toURI())
}.getOrNull()?.takeIf { it.isFile && it.name.endsWith(".jar") }

/**
 * Supervisor do servidor. A imagem Java do Pterodactyl executa o comando de inicialização sem
 * shell, então quem cuida do ciclo de vida é o próprio jar:
 *
 * - roda o servidor num processo filho (que lê o console normalmente, inclusive o `stop`);
 * - quando o servidor baixa uma versão nova e sai com [EXIT_UPDATE], troca o jar e reinicia;
 * - se o servidor cair, reinicia sozinho (com espera crescente);
 * - se uma versão recém-instalada cair logo ao subir, volta para a versão anterior.
 */
object Launcher {
    @Volatile private var child: Process? = null
    @Volatile private var shuttingDown = false

    private fun say(message: String) = println("[Zentro] $message")

    @JvmStatic
    fun main(args: Array<String>) {
        val jar = currentJar()
        if (jar == null || System.getenv("ZENTRO_LAUNCHER") == "off") {
            main()
            return
        }

        val java = ProcessHandle.current().info().command().orElse("java")
        // O filho fica com um pouco menos de memória para sobrar espaço para este supervisor.
        val jvmArgs = ManagementFactory.getRuntimeMXBean().inputArguments
            .map { if (it.startsWith("-XX:MaxRAMPercentage")) "-XX:MaxRAMPercentage=75.0" else it }

        Runtime.getRuntime().addShutdownHook(
            Thread {
                shuttingDown = true
                child?.let {
                    it.destroy()
                    it.waitFor(15, TimeUnit.SECONDS)
                }
            },
        )

        var failures = 0
        var updatedAt = 0L
        while (true) {
            if (applyPendingUpdate(jar)) updatedAt = System.currentTimeMillis()
            val startedAt = System.currentTimeMillis()
            val command = listOf(java) + jvmArgs + listOf("-Dzentro.launcher=true", "-cp", jar.absolutePath, "dev.zentro.server.MainKt") + args
            val process = ProcessBuilder(command).inheritIO().start()
            child = process
            val code = process.waitFor()
            if (shuttingDown) return

            when (code) {
                0 -> exitProcess(0)
                EXIT_UPDATE -> {
                    failures = 0
                    say("Reiniciando com a nova versão...")
                }
                else -> {
                    val ranFor = System.currentTimeMillis() - startedAt
                    if (updatedAt > 0 && ranFor < 60_000 && rollback(jar)) {
                        say("A versão nova falhou ao iniciar. Voltei para a versão anterior.")
                        updatedAt = 0
                    }
                    failures = if (ranFor > 5 * 60_000) 1 else failures + 1
                    val wait = (1L shl failures.coerceAtMost(5)).coerceAtMost(30)
                    say("O servidor parou (código $code). Reiniciando em ${wait}s...")
                    Thread.sleep(wait * 1000)
                }
            }
        }
    }

    /** Se houver um `<jar>.new` baixado, guarda o atual como `.bak` e coloca o novo no lugar. */
    private fun applyPendingUpdate(jar: File): Boolean {
        val update = File(jar.path + ".new")
        if (!update.exists()) return false
        return runCatching {
            Files.copy(jar.toPath(), File(jar.path + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.move(update.toPath(), jar.toPath(), StandardCopyOption.REPLACE_EXISTING)
            say("Atualização aplicada.")
            true
        }.getOrElse {
            say("Não consegui aplicar a atualização: ${it.message}")
            update.delete()
            false
        }
    }

    private fun rollback(jar: File): Boolean {
        val backup = File(jar.path + ".bak")
        if (!backup.exists()) return false
        return runCatching {
            Files.move(backup.toPath(), jar.toPath(), StandardCopyOption.REPLACE_EXISTING)
            true
        }.getOrDefault(false)
    }
}
