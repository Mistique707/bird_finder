package com.example.birdfinder.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.birdfinder.data.db.DetectionEntity
import com.example.birdfinder.data.repo.HistoryStats
import com.example.birdfinder.ui.common.BirdThumbnail
import com.example.birdfinder.ui.common.GlassCard
import com.example.birdfinder.ui.common.rememberLocalName
import com.example.birdfinder.ui.theme.Brand
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenDetail: (Long) -> Unit,
    vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val showImages by vm.showImages.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val items = vm.paged.collectAsLazyPagingItems()

    val selectionActive = selected.isNotEmpty()
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var confirmBatchDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.size(8.dp))
        if (selectionActive) {
            SelectionBar(
                count = selected.size,
                onClose = { vm.clearSelection() },
                onDelete = { confirmBatchDelete = true },
            )
        } else {
            Text(
                "History",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            StatsRow(stats)
        }

        OutlinedTextField(
            value = filter.speciesQuery,
            onValueChange = vm::updateSpeciesQuery,
            label = { Text("Search species") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showStartPicker = true }) {
                Text("From: " + formatDateOrAll(filter.startUtc))
            }
            OutlinedButton(onClick = { showEndPicker = true }) {
                Text("To: " + formatDateOrAll(filter.endUtc))
            }
            TextButton(onClick = vm::clearDates) { Text("Clear") }
        }

        if (items.itemCount == 0) {
            Text(
                "No detections match. Swipe a row to delete, tap to open, long-press to multi-select.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 110.dp),
        ) {
            for (i in 0 until items.itemCount) {
                val entity = items[i] ?: continue
                item(key = entity.id) {
                    SwipeRow(
                        entity = entity,
                        showImages = showImages,
                        selectionActive = selectionActive,
                        isSelected = entity.id in selected,
                        modifier = Modifier.animateItem(),
                        onClick = {
                            if (selectionActive) vm.toggleSelected(entity.id) else onOpenDetail(entity.id)
                        },
                        onLongClick = { vm.toggleSelected(entity.id) },
                        onDelete = { vm.delete(entity.id) },
                    )
                }
            }
        }
    }

    if (confirmBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text("Delete ${selected.size} detections?") },
            text = { Text("Removes the selected rows and their saved clips. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmBatchDelete = false
                        vm.deleteSelected()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmBatchDelete = false }) { Text("Cancel") } },
        )
    }

    if (showStartPicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateStartUtc(pickerState.selectedDateMillis)
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
    if (showEndPicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateEndUtc(pickerState.selectedDateMillis)
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
            }
            Text(
                "$count selected",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete selected",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun StatsRow(stats: HistoryStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatChip("${stats.totalDetections}", "detections")
        StatChip("${stats.distinctSpecies}", "species")
    }
}

@Composable
private fun StatChip(value: String, label: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(
    entity: DetectionEntity,
    showImages: Boolean,
    selectionActive: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // In selection mode swipe-to-delete is disabled so taps drive selection cleanly.
    if (selectionActive) {
        HistoryRow(
            entity = entity,
            showImages = showImages,
            isSelected = isSelected,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier,
        )
        return
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        },
        positionalThreshold = { distance -> distance * 0.45f },
    )

    LaunchedEffect(entity.id) { dismissState.reset() }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val swiping = dismissState.dismissDirection != SwipeToDismissBoxValue.Settled
            if (swiping) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(20.dp))
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        },
        content = {
            HistoryRow(
                entity = entity,
                showImages = showImages,
                isSelected = false,
                onClick = onClick,
                onLongClick = onLongClick,
            )
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    entity: DetectionEntity,
    showImages: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val regionalName = rememberLocalName(entity.speciesScientific, entity.speciesCommon)
    val rowModifier = modifier
        .fillMaxWidth()
        .then(
            if (isSelected) Modifier.border(2.dp, Brand.SkyBlue, RoundedCornerShape(20.dp)) else Modifier,
        )
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)

    GlassCard(modifier = rowModifier) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Brand.SkyBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = androidx.compose.ui.graphics.Color.White)
                }
            } else {
                BirdThumbnail(
                    scientific = entity.speciesScientific,
                    common = entity.speciesCommon,
                    enabled = showImages,
                    size = 60,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entity.speciesCommon, style = MaterialTheme.typography.titleMedium)
                if (regionalName != null) {
                    Text(
                        regionalName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    entity.speciesScientific,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatInstant(entity.timestampUtc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "%.0f%%".format(entity.confidence * 100f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())

private fun formatInstant(epochMillis: Long): String =
    dateTimeFormatter.format(Instant.ofEpochMilli(epochMillis))

private fun formatDateOrAll(epochMillis: Long?): String =
    epochMillis?.let { dateFormatter.format(Instant.ofEpochMilli(it)) } ?: "all"
