package dev.zentro.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.zentro.app.alarm.AlarmScheduler
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Sincroniza em segundo plano a cada 15 minutos, mesmo com o app fechado. É o que garante que
 * um compromisso criado no computador ganhe alarme no celular sem você precisar abrir o app.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.zentro
        withTimeoutOrNull(60_000) { app.repo.sync() }
        AlarmScheduler.reschedule(applicationContext, app.repo.data.value)
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("zentro-sync", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
