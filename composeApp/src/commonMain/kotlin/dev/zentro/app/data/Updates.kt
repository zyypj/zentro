package dev.zentro.app.data

import dev.zentro.shared.BuildVersion
import dev.zentro.shared.UpdateInfo
import dev.zentro.shared.Version
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Como cada plataforma baixa e instala uma versão nova. */
interface Installer {
    /** Final do nome do arquivo da release usado aqui (".apk", ".msi"...), ou null se não instala sozinho. */
    val assetSuffix: String?

    /** Baixa o instalador; retorna true se o arquivo ficou pronto. */
    suspend fun download(url: String, version: String, onProgress: (Float) -> Unit): Boolean

    /** Abre o instalador. No desktop isso fecha o app e o reabre já atualizado. */
    fun launch(version: String): Boolean
}

sealed interface UpdateState {
    data object None : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo) : UpdateState
    data class Failed(val info: UpdateInfo, val message: String) : UpdateState
}

/**
 * Atualização automática dos apps, a partir das releases do GitHub (consultadas pelo servidor).
 *
 * - Ao abrir o app: se houver versão nova, já baixa e instala (no Android o sistema pede um toque
 *   para confirmar a instalação).
 * - Com o app aberto: verifica a cada 3 horas e, achando novidade, só avisa — você atualiza quando quiser.
 */
class UpdateManager(private val repo: Repository, private val installer: Installer, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.None)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var loop: Job? = null

    val current: String get() = BuildVersion.CURRENT
    val canInstall: Boolean get() = installer.assetSuffix != null

    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            delay(4_000)
            check()?.let { if (canInstall) install(it) }
            while (isActive) {
                delay(3 * 3_600_000L)
                if (_state.value is UpdateState.None) check()
            }
        }
    }

    /** Consulta a última versão; retorna a info se houver algo mais novo que o app instalado. */
    suspend fun check(): UpdateInfo? {
        val info = repo.latestRelease().getOrNull() ?: return null
        if (!Version.isNewer(info.version, current)) return null
        if (_state.value is UpdateState.None || _state.value is UpdateState.Failed) _state.value = UpdateState.Available(info)
        return info
    }

    fun installNow() {
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Failed -> s.info
            is UpdateState.Ready -> {
                installer.launch(s.info.version)
                return
            }
            else -> return
        }
        scope.launch { install(info) }
    }

    private suspend fun install(info: UpdateInfo) {
        val suffix = installer.assetSuffix ?: return
        val url = info.asset(suffix) ?: run {
            _state.value = UpdateState.Failed(info, "Esta versão não tem instalador para este sistema")
            return
        }
        _state.value = UpdateState.Downloading(info, 0f)
        val ok = runCatching {
            installer.download(url, info.version) { _state.value = UpdateState.Downloading(info, it.coerceIn(0f, 1f)) }
        }.getOrDefault(false)
        if (!ok) {
            _state.value = UpdateState.Failed(info, "Não consegui baixar a atualização")
            return
        }
        _state.value = UpdateState.Ready(info)
        installer.launch(info.version)
    }
}
