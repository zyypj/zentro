package dev.zentro.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import dev.zentro.app.alarm.AlarmScheduler
import dev.zentro.app.alarm.Notifications
import dev.zentro.app.ui.ChecklistRow
import kotlinx.coroutines.delay

class AndroidPlatform(private val activity: MainActivity) : Platform {
    override val name: String = "Android ${Build.VERSION.RELEASE}"
    override val updates = activity.zentro.updates

    private fun open(intent: Intent) {
        runCatching { activity.startActivity(intent) }.onFailure {
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))
        }
    }

    @SuppressLint("BatteryLife")
    @Composable
    override fun ReliabilityChecklist() {
        val data by activity.zentro.repo.data.collectAsState()
        // As permissões podem mudar fora do app; reavalia a cada segundo enquanto a tela está aberta.
        var tick by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000)
                tick++
            }
        }

        val pkg = activity.packageName
        val notificationsOk = remember(tick) { NotificationManagerCompat.from(activity).areNotificationsEnabled() }
        val exactOk = remember(tick) { AlarmScheduler.canScheduleExact(activity) }
        val batteryOk = remember(tick) { activity.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(pkg) }
        val fullScreenOk = remember(tick) { Notifications.canUseFullScreen(activity) }

        Column {
            ChecklistRow(
                ok = notificationsOk,
                title = "Notificações permitidas",
                subtitle = if (notificationsOk) "O Zentro pode te avisar" else "Sem isso nenhum aviso aparece",
                actionLabel = "Permitir",
                onAction = {
                    if (Build.VERSION.SDK_INT >= 33) activity.requestNotificationPermission()
                    open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
                },
            )
            ChecklistRow(
                ok = exactOk,
                title = "Alarmes na hora exata",
                subtitle = if (exactOk) "Avisos tocam no minuto certo, mesmo em economia de bateria" else "Os avisos podem atrasar",
                actionLabel = "Permitir",
                onAction = {
                    if (Build.VERSION.SDK_INT >= 31) open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg")))
                },
            )
            ChecklistRow(
                ok = batteryOk,
                title = "Sem restrição de bateria",
                subtitle = if (batteryOk) "O sistema não vai “matar” o Zentro" else "Alguns celulares bloqueiam apps em segundo plano",
                actionLabel = "Liberar",
                onAction = { open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg"))) },
            )
            ChecklistRow(
                ok = fullScreenOk,
                title = "Tela cheia para críticos",
                subtitle = if (fullScreenOk) "Compromissos críticos acendem a tela" else "Críticos aparecem só como notificação",
                actionLabel = "Permitir",
                onAction = {
                    if (Build.VERSION.SDK_INT >= 34) open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg")))
                },
            )
            ChecklistRow(
                ok = data.settings.telegramLinked,
                title = "Telegram como reserva",
                subtitle = if (data.settings.telegramLinked) "Você também recebe os avisos no Telegram" else "Conecte abaixo para receber até com o celular desligado",
            )
            Text(
                "Xiaomi, Samsung, Motorola e outros: em Configurações › Apps › Zentro, ative “Início automático” e deixe a bateria em “Sem restrições”.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    override fun testNotification() = AlarmScheduler.scheduleTest(activity)

    override suspend fun pickImage(): PickedImage? {
        val uri = activity.pickImageUri() ?: return null
        return withContext(Dispatchers.IO) {
            val bytes = activity.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            PickedImage(shrink(bytes), uri.lastPathSegment?.substringAfterLast('/') ?: "foto.jpg")
        }
    }

    override fun decodeImage(bytes: ByteArray): ImageBitmap? =
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()

    /** Fotos do celular costumam ter 4000 px e 5 MB: reduz para 1920 px antes de enviar. */
    private fun shrink(bytes: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val side = maxOf(bounds.outWidth, bounds.outHeight)
        if (side <= 0 || (side <= 1920 && bytes.size <= 1_500_000) || bounds.outMimeType == "image/gif") return bytes
        var sample = 1
        while (side / (sample * 2) >= 1920) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return bytes
        val ratio = minOf(1f, 1920f / maxOf(decoded.width, decoded.height))
        val scaled = if (ratio < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * ratio).toInt(), (decoded.height * ratio).toInt(), true) else decoded
        val out = ByteArrayOutputStream()
        scaled.compress(if (scaled.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray()
    }
}
