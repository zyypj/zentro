package dev.zentro.app

import android.annotation.SuppressLint
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
                subtitle = if (data.settings.telegramLinked) "O servidor também te avisa pelo Telegram" else "Conecte abaixo para receber até com o celular desligado",
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
}
