package dev.zentro.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import dev.zentro.app.ui.App
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private var pickResult: CompletableDeferred<Uri?>? = null
    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> pickResult?.complete(uri) }

    /** Abre o seletor de fotos do Android e espera a escolha (null se cancelar). */
    suspend fun pickImageUri(): Uri? {
        val result = CompletableDeferred<Uri?>()
        pickResult = result
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        return result.await()
    }

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
