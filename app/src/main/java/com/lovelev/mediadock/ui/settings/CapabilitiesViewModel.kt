package com.lovelev.mediadock.ui.settings

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lovelev.mediadock.core.model.DeviceCapabilities
import com.lovelev.mediadock.di.AppContainer
import com.lovelev.mediadock.player.diagnostics.PlaybackDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CapabilitiesViewModel(private val container: AppContainer) : ViewModel() {

    private val _device = MutableStateFlow<DeviceCapabilities?>(null)
    val device: StateFlow<DeviceCapabilities?> = _device.asStateFlow()

    val playback: StateFlow<PlaybackDiagnostics> = container.playbackDiagnostics.state

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val displayManager = container.appContext.getSystemService(DisplayManager::class.java)
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) refresh()
        }
    }

    init {
        displayManager?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun refresh() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            runCatching { container.deviceCapabilities.probe(force = true) }
                .onSuccess { _device.value = it }
            _loading.value = false
        }
    }

    fun clearPlayback() {
        container.playbackDiagnostics.clear()
    }

    override fun onCleared() {
        displayManager?.unregisterDisplayListener(displayListener)
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { CapabilitiesViewModel(container) }
        }
    }
}
