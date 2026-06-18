package com.example.birdfinder.ui.history

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.lifecycle.viewModelScope
import com.example.birdfinder.BirdFinderApp
import com.example.birdfinder.data.db.DetectionEntity
import com.example.birdfinder.data.repo.HistoryStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryFilter(
    val startUtc: Long? = null,
    val endUtc: Long? = null,
    val speciesQuery: String = "",
)

class HistoryViewModel(private val app: BirdFinderApp) : AndroidViewModel(app) {

    private val repo = app.detections

    private val _filter = MutableStateFlow(HistoryFilter())
    val filter = _filter.asStateFlow()

    private val refreshTick = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val paged: Flow<PagingData<DetectionEntity>> = _filter
        .flatMapLatest { f ->
            repo.paged(
                startUtc = f.startUtc,
                endUtc = f.endUtc?.let { it + ONE_DAY_MILLIS },
                speciesSubstring = f.speciesQuery,
            )
        }
        .cachedIn(viewModelScope)

    val stats: StateFlow<HistoryStats> =
        combine(_filter, refreshTick) { f, _ -> f }
            .map { f ->
                repo.stats(
                    startUtc = f.startUtc,
                    endUtc = f.endUtc?.let { it + ONE_DAY_MILLIS },
                    speciesSubstring = f.speciesQuery,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryStats(0, 0))

    val showImages: StateFlow<Boolean> = app.settings.state
        .map { it.showBirdImages }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun updateSpeciesQuery(q: String) { _filter.value = _filter.value.copy(speciesQuery = q) }
    fun updateStartUtc(epochMillis: Long?) { _filter.value = _filter.value.copy(startUtc = epochMillis) }
    fun updateEndUtc(epochMillis: Long?) { _filter.value = _filter.value.copy(endUtc = epochMillis) }
    fun clearDates() { _filter.value = _filter.value.copy(startUtc = null, endUtc = null) }

    fun delete(id: Long) {
        viewModelScope.launch {
            repo.delete(id, app.filesDir)
            refreshTick.value = refreshTick.value + 1
            // Room's PagingSource auto-invalidates on table change; no manual refresh needed.
        }
    }

    // ---- Multi-select ----
    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    /** Selected row ids; non-empty means selection mode is active. */
    val selected = _selected.asStateFlow()

    fun toggleSelected(id: Long) {
        _selected.value = _selected.value.let { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun deleteSelected() {
        val ids = _selected.value.toList()
        if (ids.isEmpty()) return
        _selected.value = emptySet()
        viewModelScope.launch {
            repo.deleteMany(ids, app.filesDir)
            refreshTick.value = refreshTick.value + 1
        }
    }

    companion object {
        private const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BirdFinderApp
                HistoryViewModel(app)
            }
        }
    }
}
