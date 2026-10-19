package com.oikos.fieldbook.data

import com.oikos.fieldbook.dwc.DarwinCoreCatalog
import androidx.room.withTransaction
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

    /**
     * Acrescenta ao projeto os termos novos do catalogo (desligados conforme o
     * catalogo) e atualiza rotulo, tipo e grupo dos que ja existem, preservando
     * o que o pesquisador escolheu: ligado e obrigatorio. Sem a atualizacao, um
     * rotulo corrigido no catalogo (por exemplo "DAP (cm)") nunca chegava aos
     * projetos ja criados.
     */
    suspend fun ensureFieldCatalog(projectId: Long) {
        val existing = db.fieldConfigDao().getForProject(projectId)
        val refreshed = existing.mapNotNull { field ->
            val definition = DarwinCoreCatalog.byTerm[field.dwcTerm] ?: return@mapNotNull null
            val updated = field.copy(
                labelPt = definition.labelPt,
                labelEn = definition.labelEn,
                fieldType = definition.type,
                groupName = definition.group,
                displayOrder = definition.order
            )
            updated.takeIf { it != field }
        }
        refreshed.forEach { db.fieldConfigDao().update(it) }
        val existingTerms = existing.map { it.dwcTerm }.toSet()
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
    /** Troca a configuracao de uma vez: uma falha no meio nao deixa o projeto sem campos. */
    suspend fun replaceFieldConfig(projectId: Long, fields: List<FieldConfigEntity>) = db.withTransaction {
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

    /** Grava a midia com o numero fixo dela (iniciais do coletor + sequencia deste aparelho). */
    suspend fun createMedia(media: MediaEntity): Long = db.withTransaction {
        val code = media.mediaCode.ifBlank { MediaCodes.next(db.openHelper.writableDatabase, media.sessionId, media.occurrenceId) }
        db.mediaDao().insert(media.copy(mediaCode = code))
    }
    suspend fun updateMedia(media: MediaEntity) = db.mediaDao().update(media)
    suspend fun deleteMedia(media: MediaEntity) = db.mediaDao().delete(media)
    suspend fun getMedia(projectId: Long): List<MediaEntity> = db.mediaDao().getForProject(projectId)
    suspend fun getMediaForSession(sessionId: Long): List<MediaEntity> = db.mediaDao().getForSession(sessionId)
    suspend fun getMediaForOccurrence(occurrenceId: Long): List<MediaEntity> = db.mediaDao().getForOccurrence(occurrenceId)
    suspend fun getTracks(projectId: Long): List<GpsTrackEntity> = db.gpsTrackDao().getForProject(projectId)
    suspend fun getTrack(trackId: Long): GpsTrackEntity? = db.gpsTrackDao().getTrack(trackId)
    suspend fun getActiveTracks(): List<GpsTrackEntity> = db.gpsTrackDao().getActiveTracks()
    suspend fun getTrackPoints(trackId: Long): List<GpsTrackPointEntity> = db.gpsTrackDao().getPoints(trackId)
    suspend fun getLastTrackPoint(trackId: Long): GpsTrackPointEntity? = db.gpsTrackDao().getLastPoint(trackId)
    suspend fun countTrackPoints(trackId: Long): Int = db.gpsTrackDao().countPoints(trackId)
    suspend fun createTrack(track: GpsTrackEntity): Long = db.gpsTrackDao().insertTrack(track)
    suspend fun updateTrack(track: GpsTrackEntity) = db.gpsTrackDao().updateTrack(track)
    suspend fun addTrackPoint(point: GpsTrackPointEntity): Long = db.gpsTrackDao().insertPoint(point)

    /**
     * Ponto de trilha gravado de uma vez: confere que a trilha segue ativa, le o
     * ponto anterior, decide, grava e soma a distancia na mesma transacao. Antes
     * cada posicao rodava em paralelo, lia o mesmo "anterior" e sobrescrevia a
     * distancia das outras. Devolve a contagem de pontos, ou null se o ponto
     * foi recusado ou a trilha ja estava encerrada.
     */
    suspend fun recordTrackPoint(
        trackId: Long,
        latitude: Double,
        longitude: Double,
        altitude: Double?,
        accuracy: Double?,
        timestamp: Long,
        speed: Double?
    ): RecordedPoint? = db.withTransaction {
        val dao = db.gpsTrackDao()
        val track = dao.getTrack(trackId)
        if (track == null || !track.active) return@withTransaction null
        val previous = dao.getLastPoint(trackId)
        val decision = com.oikos.fieldbook.services.TrackPointRules.decide(previous, latitude, longitude, accuracy, timestamp)
        if (!decision.accepted) return@withTransaction null
        dao.insertPoint(
            GpsTrackPointEntity(
                trackId = trackId,
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
                accuracy = accuracy,
                timestamp = timestamp,
                speed = speed
            )
        )
        dao.addProgress(trackId, decision.distanceFromPrevious, System.currentTimeMillis())
        RecordedPoint(track.pointsCount + 1, track.totalDistance + decision.distanceFromPrevious)
    }

    /** Encerra no horario do ultimo ponto (ou no informado), sem regravar a trilha inteira. */
    suspend fun closeTrack(trackId: Long, endTime: Long? = null) {
        val dao = db.gpsTrackDao()
        val end = endTime ?: dao.lastPointTime(trackId) ?: dao.getTrack(trackId)?.startTime ?: System.currentTimeMillis()
        dao.close(trackId, end)
    }

    suspend fun lastTrackPointTime(trackId: Long): Long? = db.gpsTrackDao().lastPointTime(trackId)
}

/** Resultado de um ponto de trilha aceito. */
data class RecordedPoint(val pointsCount: Int, val totalDistance: Double)
