package dev.zentro.app

import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingleInstanceTest {
    private val dir = Files.createTempDirectory("zentro-instance").toFile()

    @AfterTest
    fun cleanup() = SingleInstance.release()

    @Test
    fun lockSurvivesGarbageCollection() {
        assertTrue(SingleInstance.acquire(dir))
        // O bug antigo: o canal ficava sem referência e o GC liberava a trava.
        repeat(3) {
            System.gc()
            Thread.sleep(100)
        }
        RandomAccessFile(dir.resolve("instance.lock"), "rw").channel.use { other ->
            val stolen = runCatching { other.tryLock() }.getOrNull()
            assertTrue(stolen == null, "outra abertura não pode conseguir a trava")
        }
    }

    @Test
    fun secondLaunchAsksTheFirstToShowItself() {
        assertTrue(SingleInstance.acquire(dir))
        val shown = CountDownLatch(1)
        SingleInstance.listen(dir) { shown.countDown() }
        assertTrue(SingleInstance.requestShow(dir))
        assertTrue(shown.await(3, TimeUnit.SECONDS), "a janela existente deveria receber o pedido para aparecer")
    }

    @Test
    fun requestShowFailsGracefullyWhenNothingIsListening() {
        assertFalse(SingleInstance.requestShow(dir))
    }
}
