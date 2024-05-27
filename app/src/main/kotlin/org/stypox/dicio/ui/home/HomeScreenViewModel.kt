package org.stypox.dicio.ui.home

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.dicio.skill.context.SkillContext
import org.stypox.dicio.di.SpeechOutputDeviceWrapper
import org.stypox.dicio.di.SttInputDeviceWrapper
import org.stypox.dicio.eval.SkillEvaluator2
import org.stypox.dicio.eval.SkillHandler2
import org.stypox.dicio.io.input.InputEventsModule
import org.stypox.dicio.io.input.SttInputDevice
import org.stypox.dicio.io.speech.SnackbarSpeechDevice
import javax.inject.Inject

@HiltViewModel
class HomeScreenViewModel @Inject constructor(
    application: Application,
    val skillContext: SkillContext,
    val skillHandler: SkillHandler2,
    val inputEventsModule: InputEventsModule,
    val sttInputDevice: SttInputDeviceWrapper,
    val speechOutputDevice: SpeechOutputDeviceWrapper,
    // this is always instantiated, but will do nothing if
    // it is not the speech device chosen by the user
    snackbarSpeechDevice: SnackbarSpeechDevice,
) : AndroidViewModel(application) {

    val skillEvaluator = SkillEvaluator2(
        scope = viewModelScope,
        skillContext = skillContext,
        skillHandler = skillHandler,
        inputEventsModule = inputEventsModule,
        sttInputDevice = sttInputDevice,
    )

    private var showSnackbarJob: Job? = null
    val snackbarHostState = SnackbarHostState()

    init {
        // show snackbars generated by the SnackbarSpeechDevice
        viewModelScope.launch {
            snackbarSpeechDevice.events.collect {
                if (it == null) {
                    // "stop speaking", i.e. remove the current snackbar
                    showSnackbarJob?.cancel()
                } else {
                    // replace the current snackbar
                    showSnackbarJob?.cancel()
                    showSnackbarJob = launch {
                        snackbarHostState.showSnackbar(it)
                    }
                }
            }
        }

        // stop speaking when the STT device starts listening
        viewModelScope.launch {
            sttInputDevice.uiState
                .filter { it == SttState.Listening }
                .collect { speechOutputDevice.stopSpeaking() }
        }
    }
}