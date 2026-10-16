package dev.zentro.server

import dev.zentro.server.db.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Backups automáticos do banco em `data/backups`: ao iniciar, todo dia às 03:30 e antes de cada
 * atualização. Mantém os 20 mais recentes (cerca de duas semanas).
 *
 * Para restaurar: pare o servidor, copie o backup desejado por cima de `data/zentro.db`
 * (apague `zentro.db-wal` e `zentro.db-shm`, se existirem) e inicie de novo.
 */
class Backups(private val store: Store, private val dir: File) {
    private val log = LoggerFactory.getLogger(Backups::class.java)
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    fun start(scope: CoroutineScope) {
        scope.launch {
            backup("inicio")
            while (isActive) {
                val now = LocalDateTime.now()
                var next = now.toLocalDate().atTime(LocalTime.of(3, 30))
                if (!next.isAfter(now)) next = next.plusDays(1)
                delay(java.time.Duration.between(now, next).toMillis().coerceAtLeast(60_000))
                backup("diario")
            }
        }
    }

    suspend fun backup(reason: String): File? = try {
        val file = File(dir, "zentro_${LocalDateTime.now().format(stamp)}_$reason.db")
        store.backupTo(file)
        prune()
        log.info("Backup salvo: backups/${file.name}")
        file
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.error("Falha ao criar backup: ${e.message}")
        null
    }

    fun list(): List<File> = dir.listFiles { f -> f.name.endsWith(".db") }.orEmpty().sortedByDescending { it.lastModified() }

    private fun prune() {
        list().drop(KEEP).forEach { it.delete() }
    }

    private companion object {
        const val KEEP = 20
    }
}
