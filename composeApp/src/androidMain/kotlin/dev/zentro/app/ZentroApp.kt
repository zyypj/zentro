package dev.zentro.app

import android.app.Application
import android.content.Context
import dev.zentro.app.alarm.AlarmScheduler
import dev.zentro.app.alarm.Notifications
import dev.zentro.app.data.FileStorage
import dev.zentro.app.data.Repository
import dev.zentro.app.data.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class AndroidStorage(private val dir: File) : FileStorage {
    override fun read(name: String): String? = File(dir, name).takeIf { it.exists() }?.readText()

    override fun write(name: String, content: String) {
        val target = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }
}

class ZentroApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var repo: Repository
        private set
    val updates: UpdateManager by lazy { UpdateManager(repo, AndroidInstaller(this), scope) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        repo = Repository(AndroidStorage(filesDir), scope)
        // Toda mudança na agenda (feita aqui ou vinda de outro aparelho) reagenda os alarmes na hora.
        repo.onScheduleChanged = { AlarmScheduler.reschedule(this, it) }
        AlarmScheduler.reschedule(this, repo.data.value)
        SyncWorker.enqueue(this)
    }
}

val Context.zentro: ZentroApp get() = applicationContext as ZentroApp
