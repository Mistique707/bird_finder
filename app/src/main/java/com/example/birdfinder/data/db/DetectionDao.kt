package com.example.birdfinder.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DetectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<DetectionEntity>): List<Long>

    @Query("SELECT * FROM detections WHERE id = :id")
    suspend fun byId(id: Long): DetectionEntity?

    /**
     * @param startUtc inclusive epoch millis; pass [Long.MIN_VALUE] to ignore.
     * @param endUtc exclusive epoch millis; pass [Long.MAX_VALUE] to ignore.
     * @param speciesQuery matched against both common & scientific name with LIKE;
     *   pass `%` to ignore.
     */
    @Query(
        """
        SELECT * FROM detections
        WHERE timestampUtc >= :startUtc
          AND timestampUtc < :endUtc
          AND (speciesCommon LIKE :speciesQuery OR speciesScientific LIKE :speciesQuery)
        ORDER BY timestampUtc DESC
        """,
    )
    fun page(
        startUtc: Long,
        endUtc: Long,
        speciesQuery: String,
    ): PagingSource<Int, DetectionEntity>

    @Query("SELECT COUNT(*) FROM detections")
    suspend fun count(): Int

    @Query("UPDATE detections SET latitude = :lat, longitude = :lon WHERE id = :id")
    suspend fun updateLocation(id: Long, lat: Double?, lon: Double?)

    @Query("DELETE FROM detections WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM detections WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<DetectionEntity>

    @Query("DELETE FROM detections WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    @Query("DELETE FROM detections")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM detections WHERE clipPath = :clipPath")
    suspend fun countByClipPath(clipPath: String): Int

    @Query("SELECT DISTINCT clipPath FROM detections")
    suspend fun allClipPaths(): List<String>

    @Query(
        """
        SELECT COUNT(*) FROM detections
        WHERE timestampUtc >= :startUtc AND timestampUtc < :endUtc
          AND (speciesCommon LIKE :speciesQuery OR speciesScientific LIKE :speciesQuery)
        """,
    )
    suspend fun filteredCount(startUtc: Long, endUtc: Long, speciesQuery: String): Int

    @Query(
        """
        SELECT COUNT(DISTINCT speciesScientific) FROM detections
        WHERE timestampUtc >= :startUtc AND timestampUtc < :endUtc
          AND (speciesCommon LIKE :speciesQuery OR speciesScientific LIKE :speciesQuery)
        """,
    )
    suspend fun filteredDistinctSpecies(startUtc: Long, endUtc: Long, speciesQuery: String): Int

    @Query("SELECT * FROM detections ORDER BY timestampUtc")
    suspend fun allOrdered(): List<DetectionEntity>
}
