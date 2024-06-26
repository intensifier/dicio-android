package org.stypox.dicio.di

import android.content.Context
import androidx.datastore.core.DataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.input.SttInputDevice
import org.stypox.dicio.io.input.vosk.VoskInputDevice
import org.stypox.dicio.settings.datastore.InputDevice
import org.stypox.dicio.settings.datastore.InputDevice.*
import org.stypox.dicio.settings.datastore.UserSettings
import org.stypox.dicio.ui.home.SttState
import org.stypox.dicio.util.distinctUntilChangedBlockingFirst
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SttInputDeviceWrapper @Inject constructor(
    @ApplicationContext private val appContext: Context,
    dataStore: DataStore<UserSettings>,
    private val localeManager: LocaleManager,
    private val okHttpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    private var sttInputDevice: SttInputDevice? = null

    // null means that the user has not enabled any STT input device
    private val _uiState: MutableStateFlow<SttState?> = MutableStateFlow(null)
    val uiState: StateFlow<SttState?> = _uiState
    private var uiStateJob: Job? = null


    init {
        // Run blocking, because the data store is always available right away since LocaleManager
        // also initializes in a blocking way from the same data store.
        val (firstInputDevice, nextInputDeviceFlow) = dataStore.data
            .map { it.inputDevice }
            .distinctUntilChangedBlockingFirst()

        sttInputDevice = buildInputDevice(firstInputDevice)
        scope.launch {
            restartUiStateJob()
        }

        scope.launch {
            nextInputDeviceFlow.collect { setting ->
                val prevSttInputDevice = sttInputDevice
                sttInputDevice = buildInputDevice(setting)
                prevSttInputDevice?.destroy()
                restartUiStateJob()
            }
        }
    }

    private fun buildInputDevice(setting: InputDevice): SttInputDevice? {
        return when (setting) {
            UNRECOGNIZED,
            INPUT_DEVICE_UNSET,
            INPUT_DEVICE_VOSK -> VoskInputDevice(
                appContext, okHttpClient, localeManager
            )
            INPUT_DEVICE_NOTHING -> null
        }
    }

    private suspend fun restartUiStateJob() {
        uiStateJob?.cancel()
        val newSttInputDevice = sttInputDevice
        if (newSttInputDevice == null) {
            _uiState.emit(null)
        } else {
            uiStateJob = scope.launch {
                newSttInputDevice.uiState.collect { _uiState.emit(it) }
            }
        }
    }


    fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?) {
        sttInputDevice?.tryLoad(thenStartListeningEventListener)
    }

    fun onClick(eventListener: (InputEvent) -> Unit) {
        sttInputDevice?.onClick(eventListener)
    }
}
