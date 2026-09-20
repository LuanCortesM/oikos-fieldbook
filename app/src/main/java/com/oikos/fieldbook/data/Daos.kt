package com.oikos.fieldbook.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeProject(id: Long): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProject(id: Long): ProjectEntity?

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)
}

@Dao
interface FieldConfigDao {
    @Query("SELECT * FROM field_configs WHERE projectId = :projectId ORDER BY groupName, displayOrder")
    fun observeForProject(projectId: Long): Flow<List<FieldConfigEntity>>

    @Query("SELECT * FROM field_configs WHERE projectId = :projectId ORDER BY groupName, displayOrder")
    suspend fun getForProject(projectId: Long): List<FieldConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(fields: List<FieldConfigEntity>)

    @Update
    suspend fun update(field: FieldConfigEntity)

    @Query("DELETE FROM field_configs WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: Long)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE projectId = :projectId ORDER BY eventDate DESC, createdAt DESC")
    fun observeForProject(projectId: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE projectId = :projectId")
    suspend fun getForProject(projectId: Long): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: Long): SessionEntity?

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Delete
    suspend fun delete(session: SessionEntity)
}

@Dao
interface OccurrenceDao {
    @Query("SELECT * FROM occurrences WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun observeForSession(sessionId: Long): Flow<List<OccurrenceEntity>>

    @Query("SELECT * FROM occurrences WHERE projectId = :projectId ORDER BY createdAt DESC")
    suspend fun getForProject(projectId: Long): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrences WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    suspend fun getForSession(sessionId: Long): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrences WHERE id = :id")
    fun observeOccurrence(id: Long): Flow<OccurrenceEntity?>

    @Query("SELECT * FROM occurrences WHERE id = :id")
    suspend fun getOccurrence(id: Long): OccurrenceEntity?

    @Query(
        """
        SELECT * FROM occurrences
        WHERE projectId = :projectId
          AND (:species = '' OR scientificName LIKE '%' || :species || '%' OR vernacularName LIKE '%' || :species || '%')
          AND (:collector = '' OR recordedBy LIKE '%' || :collector || '%')
        ORDER BY createdAt DESC
        """
    )
    suspend fun search(projectId: Long, species: String, collector: String): List<OccurrenceEntity>

    @Insert
    suspend fun insert(occurrence: OccurrenceEntity): Long

    @Update
    suspend fun update(occurrence: OccurrenceEntity)

    @Delete
    suspend fun delete(occurrence: OccurrenceEntity)
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun observeForSession(sessionId: Long): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE occurrenceId = :occurrenceId ORDER BY createdAt DESC")
    fun observeForOccurrence(occurrenceId: Long): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE projectId = :projectId ORDER BY createdAt DESC")
    suspend fun getForProject(projectId: Long): List<MediaEntity>

    @Query("SELECT * FROM media WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    suspend fun getForSession(sessionId: Long): List<MediaEntity>

    @Query("SELECT * FROM media WHERE occurrenceId = :occurrenceId ORDER BY createdAt DESC")
    suspend fun getForOccurrence(occurrenceId: Long): List<MediaEntity>

    @Insert
    suspend fun insert(media: MediaEntity): Long

    @Update
    suspend fun update(media: MediaEntity)

    @Delete
    suspend fun delete(media: MediaEntity)
}

@Dao
interface GpsTrackDao {
    @Query("SELECT * FROM gps_tracks WHERE sessionId = :sessionId ORDER BY startTime DESC")
    fun observeForSession(sessionId: Long): Flow<List<GpsTrackEntity>>

    @Query("SELECT * FROM gps_track_points WHERE trackId = :trackId ORDER BY timestamp")
    fun observePoints(trackId: Long): Flow<List<GpsTrackPointEntity>>

    @Query("SELECT * FROM gps_tracks WHERE projectId = :projectId ORDER BY startTime DESC")
    suspend fun getForProject(projectId: Long): List<GpsTrackEntity>

    @Query("SELECT * FROM gps_tracks WHERE id = :trackId")
    suspend fun getTrack(trackId: Long): GpsTrackEntity?

    @Query("SELECT * FROM gps_track_points WHERE trackId = :trackId ORDER BY timestamp")
    suspend fun getPoints(trackId: Long): List<GpsTrackPointEntity>

    @Query("SELECT * FROM gps_track_points WHERE trackId = :trackId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastPoint(trackId: Long): GpsTrackPointEntity?

    @Query("SELECT COUNT(*) FROM gps_track_points WHERE trackId = :trackId")
    suspend fun countPoints(trackId: Long): Int

    @Insert
    suspend fun insertTrack(track: GpsTrackEntity): Long

    @Update
    suspend fun updateTrack(track: GpsTrackEntity)

    @Insert
    suspend fun insertPoint(point: GpsTrackPointEntity): Long
}
