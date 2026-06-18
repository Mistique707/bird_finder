package com.example.birdfinder.ui.listen

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.birdfinder.BirdFinderApp
import com.example.birdfinder.service.ListeningService
import com.example.birdfinder.settings.Settings
import com.example.birdfinder.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class ListenViewModel(app: BirdFinderApp) : AndroidViewModel(app) {

    private val pipeline = app.pipeline
    val state: StateFlow<com.example.birdfinder.pipeline.ListenState> get() = pipeline.state
    val settings: Flow<Settings> = app.settings.state
    val defaultSettings: Settings = SettingsStore.DEFAULT

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        ListeningService.start(getApplication())
    }

    fun stop() {
        ListeningService.stop(getApplication())
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BirdFinderApp
                ListenViewModel(app)
            }
        }
    }
}
