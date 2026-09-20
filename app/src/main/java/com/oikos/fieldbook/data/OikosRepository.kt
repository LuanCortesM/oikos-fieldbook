package com.oikos.fieldbook.data

import com.oikos.fieldbook.dwc.DarwinCoreCatalog
import kotlinx.coroutines.flow.Flow

class OikosRepository(private val db: OikosDatabase) {
    fun observeProjects(): Flow<List<ProjectEntity>> = db.projectDao().observeProjects()
    fun observeProject(id: Long): Flow<ProjectEntity?> = db.projectDao().observeProject(id)
    fun observeFields(projectId: Long): Flow<List<FieldConfigEntity>> = db.fieldConfigDao().observeForProject(projectId)
    fun observeSessions(projectId: Long): Flow<List<SessionEntity>> = db.sessionDao().observeForProject(projectId)
    fun observeSession(id: Long): Flow<SessionEntity?> = db.sessionDao().observeSession(id)
    fun observeOccurrences(sessionId: Long): Flow<List<OccurrenceEntity>> = db.occurrenceDao().observeForSession(sessionId)
    fun observeOccurrence(id: Long): Flow<OccurrenceEntity?> = db.occurrenceDao().observeOccurrence(id)
    fun observeSessionMedia(sessionId: Long): Flow<List<MediaEntity>> = db.mediaDao().observeForSession(sessionId)
    fun observeOccurrenceMedia(occurrenceId: Long): Flow<List<MediaEntity>> = db.mediaDao().observeForOccurrence(occurrenceId)
    fun observeTracks(sessionId: Long): Flow<List<GpsTrackEntity>> = db.gpsTrackDao().observeForSession(sessionId)
    fun observeTrackPoints(trackId: Long): Flow<List<GpsTrackPointEntity>> = db.gpsTrackDao().observePoints(trackId)

    suspend fun createProject(project: ProjectEntity): Long {
        val id = db.projectDao().insert(project)
        seedFieldConfig(id)
        return id
    }

    suspend fun updateProject(project: ProjectEntity) = db.projectDao().update(project.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteProject(project: ProjectEntity) = db.projectDao().delete(project)
    suspend fun getProject(id: Long): ProjectEntity? = db.projectDao().getProject(id)

    suspend fun seedFieldConfig(projectId: Long) {
        val existing = db.fieldConfigDao().getForProject(projectId)
        if (existing.isNotEmpty()) return
        db.fieldConfigDao().insertAll(
            DarwinCoreCatalog.allFields.map {
                FieldConfigEntity(
                    projectId = projectId,
                    dwcTerm = it.term,
                    labelPt = it.labelPt,
                    labelEn = it.labelEn,
                    enabled = it.enabled,
                    required = it.required,
                    fieldType = it.type,
                    groupName = it.group,
                    displayOrder = it.order
                )
            }
        )
    }

    suspend fun ensureFieldCatalog(projectId: Long) {
        val existingTerms = db.fieldConfigDao().getForProject(projectId).map { it.dwcTerm }.toSet()
        val missing = DarwinCoreCatalog.allFields.filterNot { it.term in existingTerms }
        if (missing.isNotEmpty()) {
            db.fieldConfigDao().insertAll(
                missing.map {
                    FieldConfigEntity(
                        projectId = projectId,
                        dwcTerm = it.term,
                        labelPt = it.labelPt,
                        labelEn = it.labelEn,
                        enabled = it.enabled,
                        required = it.required,
                        fieldType = it.type,
                        groupName = it.group,
                        displayOrder = it.order
                    )
                }
            )
        }
    }

    suspend fun updateField(field: FieldConfigEntity) = db.fieldConfigDao().update(field)
    suspend fun getFields(projectId: Long): List<FieldConfigEntity> = db.fieldConfigDao().getForProject(projectId)
    suspend fun replaceFieldConfig(projectId: Long, fields: List<FieldConfigEntity>) {
        db.fieldConfigDao().deleteForProject(projectId)
        db.fieldConfigDao().insertAll(fields.map { it.copy(id = 0, projectId = projectId) })
    }

    suspend fun createSession(session: SessionEntity): Long = db.sessionDao().insert(session)
    suspend fun updateSession(session: SessionEntity) = db.sessionDao().update(session.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteSession(session: SessionEntity) = db.sessionDao().delete(session)
    suspend fun getSession(id: Long): SessionEntity? = db.sessionDao().getSession(id)
    suspend fun getSessions(projectId: Long): List<SessionEntity> = db.sessionDao().getForProject(projectId)

    suspend fun createOccurrence(occurrence: OccurrenceEntity): Long = db.occurrenceDao().insert(occurrence)
    suspend fun updateOccurrence(occurrence: OccurrenceEntity) = db.occurrenceDao().update(occurrence.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteOccurrence(occurrence: OccurrenceEntity) = db.occurrenceDao().delete(occurrence)
    suspend fun getOccurrence(id: Long): OccurrenceEntity? = db.occurrenceDao().getOccurrence(id)
    suspend fun getOccurrences(projectId: Long): List<OccurrenceEntity> = db.occurrenceDao().getForProject(projectId)
    suspend fun getOccurrencesForSession(sessionId: Long): List<OccurrenceEntity> = db.occurrenceDao().getForSession(sessionId)

    suspend fun createMedia(media: MediaEntity): Long = db.mediaDao().insert(media)
    suspend fun updateMedia(media: MediaEntity) = db.mediaDao().update(media)
    suspend fun deleteMedia(media: MediaEntity) = db.mediaDao().delete(media)
    suspend fun getMedia(projectId: Long): List<MediaEntity> = db.mediaDao().getForProject(projectId)
    suspend fun getMediaForSession(sessionId: Long): List<MediaEntity> = db.mediaDao().getForSession(sessionId)
    suspend fun getMediaForOccurrence(occurrenceId: Long): List<MediaEntity> = db.mediaDao().getForOccurrence(occurrenceId)
    suspend fun getTracks(projectId: Long): List<GpsTrackEntity> = db.gpsTrackDao().getForProject(projectId)
    suspend fun getTrack(trackId: Long): GpsTrackEntity? = db.gpsTrackDao().getTrack(trackId)
    suspend fun getTrackPoints(trackId: Long): List<GpsTrackPointEntity> = db.gpsTrackDao().getPoints(trackId)
    suspend fun getLastTrackPoint(trackId: Long): GpsTrackPointEntity? = db.gpsTrackDao().getLastPoint(trackId)
    suspend fun countTrackPoints(trackId: Long): Int = db.gpsTrackDao().countPoints(trackId)
    suspend fun createTrack(track: GpsTrackEntity): Long = db.gpsTrackDao().insertTrack(track)
    suspend fun updateTrack(track: GpsTrackEntity) = db.gpsTrackDao().updateTrack(track)
    suspend fun addTrackPoint(point: GpsTrackPointEntity): Long = db.gpsTrackDao().insertPoint(point)
}
