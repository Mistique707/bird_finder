package com.example.birdfinder.ui.detail

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.content.Context
import com.example.birdfinder.BirdFinderApp
import com.example.birdfinder.BuildConfig
import com.example.birdfinder.data.db.DetectionEntity
import com.example.birdfinder.media.ReferenceCall
import com.example.birdfinder.settings.Settings
import com.example.birdfinder.settings.SettingsStore
import com.example.birdfinder.util.ShareCardRenderer
import com.example.birdfinder.util.ShareUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** State of the Xeno-canto reference-call lookup. */
sealed interface RefCallState {
    data object Idle : RefCallState
    data object Loading : RefCallState
    data class Ready(val call: ReferenceCall) : RefCallState
    data object Unavailable : RefCallState

    /** No Xeno-canto API key configured (Settings → Advanced). */
    data object NeedsKey : RefCallState
}

class DetailViewModel(private val app: BirdFinderApp) : AndroidViewModel(app) {

    private val repo = app.detections
    private val media = app.media

    private val _state = MutableStateFlow<DetectionEntity?>(null)
    val state: StateFlow<DetectionEntity?> = _state.asStateFlow()

    private val _refCall = MutableStateFlow<RefCallState>(RefCallState.Idle)
    val refCall: StateFlow<RefCallState> = _refCall.asStateFlow()

    private val _preparingShare = MutableStateFlow(false)
    val preparingShare: StateFlow<Boolean> = _preparingShare.asStateFlow()

    private val _info = MutableStateFlow<String?>(null)
    /** Short Wikipedia description of the species, or null while loading / unavailable. */
    val info: StateFlow<String?> = _info.asStateFlow()

    val settings: Flow<Settings> = app.settings.state
    val defaultSettings: Settings = SettingsStore.DEFAULT

    fun load(id: Long) {
        viewModelScope.launch {
            val row = repo.byId(id)
            _state.value = row
            if (row != null && _info.value == null) {
                _info.value = runCatching { media.info(row.speciesScientific, row.speciesCommon) }
                    .getOrNull()
            }
        }
    }

    fun loadReferenceCall(scientific: String, common: String) {
        if (_refCall.value != RefCallState.Idle) return
        _refCall.value = RefCallState.Loading
        viewModelScope.launch {
            val key = app.settings.state.first().xenoCantoApiKey
                .ifBlank { BuildConfig.XENOCANTO_API_KEY }
            if (key.isBlank()) {
                _refCall.value = RefCallState.NeedsKey
                return@launch
            }
            val call = runCatching { media.referenceCall(scientific, common, key) }.getOrNull()
            _refCall.value = if (call != null) RefCallState.Ready(call) else RefCallState.Unavailable
        }
    }

    /**
     * Render a shareable "wrapped"-style card (photo + stats) and open the share sheet
     * with the card image + audio clip together.
     */
    fun shareCard(context: Context) {
        val row = _state.value ?: return
        if (_preparingShare.value) return
        _preparingShare.value = true
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val showImages = app.settings.state.first().showBirdImages
                val url = if (showImages) {
                    runCatching { media.imageUrl(row.speciesScientific, row.speciesCommon) }.getOrNull()
                } else null
                val card = ShareCardRenderer.render(appContext, row, url)
                ShareUtil.shareDetectionCard(appContext, card, row.clipPath)
            } finally {
                _preparingShare.value = false
            }
        }
    }

    fun updateLocation(id: Long, lat: Double?, lon: Double?) {
        viewModelScope.launch {
            repo.updateLocation(id, lat, lon)
            _state.value = repo.byId(id)
        }
    }

    fun delete(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            if (repo.delete(id, app.filesDir)) {
                _state.value = null
                onDone()
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BirdFinderApp
                DetailViewModel(app)
            }
        }
    }
}
