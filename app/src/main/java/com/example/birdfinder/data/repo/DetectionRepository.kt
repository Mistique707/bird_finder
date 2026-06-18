package com.example.birdfinder.data.repo

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.Pager
import com.example.birdfinder.data.db.DetectionDao
import com.example.birdfinder.data.db.DetectionEntity
import java.io.File
import kotlinx.coroutines.flow.Flow

data class HistoryStats(
    val totalDetections: Int,
    val distinctSpecies: Int,
)

class DetectionRepository(private val dao: DetectionDao) {

    suspend fun insertAll(rows: List<DetectionEntity>): List<Long> =
        if (rows.isEmpty()) emptyList() else dao.insertAll(rows)

    suspend fun byId(id: Long): DetectionEntity? = dao.byId(id)

    suspend fun updateLocation(id: Long, lat: Double?, lon: Double?) {
        dao.updateLocation(id, lat, lon)
    }

    /**
     * Delete one detection. If no other rows reference the same clip file, the WAV
     * is removed from disk too.
     *
     * @return true if a row was deleted.
     */
    suspend fun delete(id: Long, filesDir: File): Boolean {
        val row = dao.byId(id) ?: return false
        val removed = dao.deleteById(id)
        if (removed == 0) return false
        if (dao.countByClipPath(row.clipPath) == 0) {
            runCatching { File(filesDir, row.clipPath).delete() }
        }
        return true
    }

    /**
     * Delete several detections at once, removing each clip file no longer referenced.
     * @return number of rows removed.
     */
    suspend fun deleteMany(ids: List<Long>, filesDir: File): Int {
        if (ids.isEmpty()) return 0
        val rows = dao.byIds(ids)
        val removed = dao.deleteByIds(ids)
        rows.map { it.clipPath }.distinct().forEach { clipPath ->
            if (dao.countByClipPath(clipPath) == 0) {
                runCatching { File(filesDir, clipPath).delete() }
            }
        }
        return removed
    }

    /** Wipe every row + every clip file from this app's filesDir. */
    suspend fun deleteAll(filesDir: File): Int {
        val clipPaths = dao.allClipPaths()
        val rows = dao.deleteAll()
        clipPaths.forEach { runCatching { File(filesDir, it).delete() } }
        // Best-effort: also clean any orphan WAVs sitting in /clips that aren't tracked.
        File(filesDir, "clips").listFiles()?.forEach { runCatching { it.delete() } }
        return rows
    }

    suspend fun stats(
        startUtc: Long? = null,
        endUtc: Long? = null,
        speciesSubstring: String = "",
    ): HistoryStats {
        val (start, end, q) = resolveFilter(startUtc, endUtc, speciesSubstring)
        return HistoryStats(
            totalDetections = dao.filteredCount(start, end, q),
            distinctSpecies = dao.filteredDistinctSpecies(start, end, q),
        )
    }

    suspend fun exportAll(): List<DetectionEntity> = dao.allOrdered()

    fun paged(
        startUtc: Long? = null,
        endUtc: Long? = null,
        speciesSubstring: String = "",
    ): Flow<PagingData<DetectionEntity>> {
        val (start, end, q) = resolveFilter(startUtc, endUtc, speciesSubstring)
        return Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = false),
            pagingSourceFactory = { dao.page(start, end, q) },
        ).flow
    }

    private fun resolveFilter(
        startUtc: Long?,
        endUtc: Long?,
        speciesSubstring: String,
    ): Triple<Long, Long, String> {
        val start = startUtc ?: Long.MIN_VALUE
        val end = endUtc ?: Long.MAX_VALUE
        val q = if (speciesSubstring.isBlank()) "%" else "%${speciesSubstring.trim()}%"
        return Triple(start, end, q)
    }
}
