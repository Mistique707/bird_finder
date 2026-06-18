package com.example.birdfinder.ui.settings

import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.birdfinder.BirdFinderApp
import com.example.birdfinder.settings.Settings
import com.example.birdfinder.util.ShareUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class SettingsViewModel(private val app: BirdFinderApp) : AndroidViewModel(app) {

    private val store = app.settings
    private val repo = app.detections

    val state: Flow<Settings> = store.state

    fun update(transform: Settings.() -> Settings) {
        viewModelScope.launch { store.update(transform) }
    }

    /** Deletes every detection row and every WAV clip. Calls [onDone] with row count removed. */
    fun clearAll(onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val n = repo.deleteAll(app.filesDir)
            onDone(n)
        }
    }

    /** Writes a CSV of all detections to filesDir/exports/ and fires the share sheet. */
    fun exportCsv(context: Context, onDone: (rows: Int) -> Unit) {
        viewModelScope.launch {
            val rows = repo.exportAll()
            ShareUtil.exportCsvAndShare(context.applicationContext, rows)
            onDone(rows.size)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BirdFinderApp
                SettingsViewModel(app)
            }
        }
    }
}
