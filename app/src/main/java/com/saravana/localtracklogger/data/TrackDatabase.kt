package com.saravana.localtracklogger.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val distanceMeters: Double = 0.0,
    val pointCount: Int = 0,
    val active: Boolean = true
)

@Entity(tableName = "track_points", indices = [Index(value = ["trackId", "timestamp"])])
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    /** Height above mean sea level in meters, or null if the device cannot provide it. */
    val altitude: Double?,
    val accuracy: Float?,
    val speed: Float?,
    val bearing: Float?
)

@Dao
abstract class TrackDao {
    @Query("SELECT * FROM tracks ORDER BY startedAt DESC")
    abstract fun observeTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE active = 1 LIMIT 1")
    abstract suspend fun activeTrack(): TrackEntity?

    @Query("SELECT * FROM tracks WHERE active = 1 AND startedAt < :before")
    abstract suspend fun activeTracksStartedBefore(before: Long): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id")
    abstract suspend fun track(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks ORDER BY startedAt")
    abstract suspend fun allTracks(): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks WHERE startedAt = :startedAt")
    abstract suspend fun countStartedAt(startedAt: Long): Int

    @Insert
    abstract suspend fun insertPoints(points: List<TrackPointEntity>)

    @Transaction
    open suspend fun importTrack(track: TrackEntity, points: List<TrackPointEntity>) {
        val id = insertTrack(track)
        insertPoints(points.map { it.copy(trackId = id) })
    }

    @Query("SELECT * FROM tracks WHERE id = :id")
    abstract fun observeTrack(id: Long): Flow<TrackEntity?>

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY timestamp")
    abstract fun observePoints(trackId: Long): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM track_points ORDER BY trackId, timestamp")
    abstract fun observeAllPoints(): Flow<List<TrackPointEntity>>

    @Query("DELETE FROM track_points WHERE trackId IN (SELECT id FROM tracks WHERE active = 0)")
    abstract suspend fun deleteFinishedPoints()

    @Query("DELETE FROM tracks WHERE active = 0")
    abstract suspend fun deleteFinishedTracks()

    @Transaction
    open suspend fun deleteAllFinished() {
        deleteFinishedPoints()
        deleteFinishedTracks()
    }

    @Query("DELETE FROM track_points WHERE trackId = :trackId")
    abstract suspend fun deletePoints(trackId: Long)

    @Query("DELETE FROM tracks WHERE id = :trackId")
    abstract suspend fun deleteTrackRow(trackId: Long)

    @Transaction
    open suspend fun deleteTrack(trackId: Long) {
        deletePoints(trackId)
        deleteTrackRow(trackId)
    }

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY timestamp")
    abstract suspend fun points(trackId: Long): List<TrackPointEntity>

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY timestamp DESC LIMIT 1")
    abstract suspend fun lastPoint(trackId: Long): TrackPointEntity?

    @Insert
    abstract suspend fun insertTrack(track: TrackEntity): Long

    @Insert
    abstract suspend fun insertPoint(point: TrackPointEntity)

    @Query("UPDATE tracks SET pointCount = pointCount + 1, distanceMeters = distanceMeters + :meters WHERE id = :trackId")
    abstract suspend fun bumpTrack(trackId: Long, meters: Double)

    @Query("UPDATE tracks SET active = 0, endedAt = :endedAt WHERE id = :trackId")
    abstract suspend fun finishTrack(trackId: Long, endedAt: Long)

    /** Inserts the point and updates the track totals atomically. */
    @Transaction
    open suspend fun addPoint(point: TrackPointEntity, meters: Double) {
        insertPoint(point)
        bumpTrack(point.trackId, meters)
    }
}

@Database(entities = [TrackEntity::class, TrackPointEntity::class], version = 1, exportSchema = false)
abstract class TrackDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    companion object {
        fun create(context: Context): TrackDatabase =
            Room.databaseBuilder(context, TrackDatabase::class.java, "local_tracks.db").build()
    }
}
