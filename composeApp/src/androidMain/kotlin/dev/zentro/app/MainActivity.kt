package dev.zentro.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import dev.zentro.app.ui.App
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission()
        }
        val platform = AndroidPlatform(this)
        setContent { App(zentro.repo, platform) }
        // Ao abrir: se houver versão nova, já baixa e abre o instalador. Com o app aberto, só avisa.
        zentro.updates.start()
    }

    override fun onStart() {
        super.onStart()
        val app = zentro
        app.repo.startLive()
        app.scope.launch { app.repo.sync() }
    }

    override fun onStop() {
        super.onStop()
        zentro.repo.stopLive()
    }
}
