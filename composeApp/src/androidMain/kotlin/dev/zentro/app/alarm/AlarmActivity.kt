package dev.zentro.app.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.zentro.app.ui.ZColors
import dev.zentro.app.ui.ZentroTheme
import dev.zentro.shared.Format
import dev.zentro.shared.Schedule
import kotlinx.datetime.TimeZone

/** Tela cheia de compromisso crítico: acende a tela e aparece por cima do bloqueio. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val key = intent.getStringExtra(AlarmScheduler.EXTRA_KEY).orEmpty()
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE).orEmpty()
        val start = intent.getLongExtra(AlarmScheduler.EXTRA_START, Schedule.now())
        val before = intent.getIntExtra(AlarmScheduler.EXTRA_BEFORE, 0)
        val local = Schedule.toLocal(start, TimeZone.currentSystemDefault())

        setContent {
            ZentroTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.safeDrawingPadding().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Rounded.Warning, null, tint = ZColors.Expense, modifier = Modifier.size(72.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("COMPROMISSO CRÍTICO", style = MaterialTheme.typography.labelLarge, color = ZColors.Expense)
                        Spacer(Modifier.height(8.dp))
                        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (before == 0) "Agora · ${Format.time(local)}" else "Às ${Format.time(local)} · ${Schedule.describeOffset(before)}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = {
                                Notifications.cancel(this@AlarmActivity, Notifications.notificationId(key))
                                finish()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ZColors.Expense),
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                        ) { Text("Estou ciente", style = MaterialTheme.typography.titleMedium) }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                Notifications.cancel(this@AlarmActivity, Notifications.notificationId(key))
                                AlarmScheduler.snooze(this@AlarmActivity, intent, 5)
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) { Text("Adiar 5 minutos") }
                    }
                }
            }
        }
    }
}
