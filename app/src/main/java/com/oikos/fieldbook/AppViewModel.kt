package com.oikos.fieldbook

import android.app.Application
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oikos.fieldbook.data.FieldConfigEntity
import com.oikos.fieldbook.data.GpsTrackEntity
import com.oikos.fieldbook.data.GpsTrackPointEntity
import com.oikos.fieldbook.data.MediaEntity
import com.oikos.fieldbook.data.OccurrenceEntity
import com.oikos.fieldbook.data.OikosRepository
import com.oikos.fieldbook.data.ProjectEntity
import com.oikos.fieldbook.data.SessionEntity
import com.oikos.fieldbook.export.ExportContent
import com.oikos.fieldbook.export.ExportFormat
import com.oikos.fieldbook.export.TrackExportBundle
import com.oikos.fieldbook.export.TrackPointExport
import com.oikos.fieldbook.export.OikosExporter
import com.oikos.fieldbook.services.CapturedLocation
import com.oikos.fieldbook.services.LocationService
import com.oikos.fieldbook.services.MediaFileService
import com.oikos.fieldbook.services.TrackRecordingService
import com.oikos.fieldbook.services.TrackPointRules
import com.oikos.fieldbook.util.CoordinateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: OikosRepository = (application as OikosApplication).repository
    private val exporter = OikosExporter()
    private val locationService = LocationService(application)
    val mediaFiles = MediaFileService(application)

    val projects: Flow<List<ProjectEntity>> = repository.observeProjects()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun observeProject(projectId: Long) = repository.observeProject(projectId)
    fun observeFields(projectId: Long) = repository.observeFields(projectId)
    fun observeSessions(projectId: Long) = repository.observeSessions(projectId)
    fun observeSession(sessionId: Long) = repository.observeSession(sessionId)
    fun observeOccurrences(sessionId: Long) = repository.observeOccurrences(sessionId)
    fun observeOccurrence(occurrenceId: Long) = repository.observeOccurrence(occurrenceId)
    fun observeSessionMedia(sessionId: Long) = repository.observeSessionMedia(sessionId)
    fun observeOccurrenceMedia(occurrenceId: Long) = repository.observeOccurrenceMedia(occurrenceId)
    fun observeTracks(sessionId: Long) = repository.observeTracks(sessionId)
    fun observeTrackPoints(trackId: Long) = repository.observeTrackPoints(trackId)

    fun saveProject(project: ProjectEntity, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val id = if (project.id == 0L) repository.createProject(project) else {
                repository.updateProject(project)
                project.id
            }
            onSaved(id)
        }
    }

    fun deleteProject(project: ProjectEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            stopTrackService()
            repository.getMedia(project.id).forEach { mediaFiles.deleteIfManaged(it.filePath) }
            repository.deleteProject(project)
            mediaFiles.deleteProjectFiles(project.name)
            _message.value = "Projeto apagado."
            onDone()
        }
    }

    fun updateField(field: FieldConfigEntity) {
        viewModelScope.launch { repository.updateField(field) }
    }

    fun saveFieldConfiguration(projectId: Long, fields: List<FieldConfigEntity>) {
        viewModelScope.launch {
            repository.replaceFieldConfig(projectId, fields)
            _message.value = "Configuração de campos salva."
        }
    }

    fun ensureFieldCatalog(projectId: Long) {
        viewModelScope.launch { repository.ensureFieldCatalog(projectId) }
    }

    fun saveSession(session: SessionEntity, onSaved: (Long) -> Unit) {
        val error = validateCoordinates(session.latitude, session.longitude)
        if (error != null) {
            _message.value = error
            return
        }
        if (session.eventDate.isBlank()) _message.value = "Aviso: expedição salva sem data de coleta."
        viewModelScope.launch {
            try {
                val id = if (session.id == 0L) repository.createSession(session) else {
                    repository.updateSession(session)
                    session.id
                }
                onSaved(id)
            } catch (_: SQLiteConstraintException) {
                _message.value = "eventID já existe. Use um identificador único."
            }
        }
    }

    fun deleteSession(session: SessionEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            stopTrackService()
            val project = repository.getProject(session.projectId)
            repository.getMediaForSession(session.id).forEach { mediaFiles.deleteIfManaged(it.filePath) }
            repository.deleteSession(session)
            project?.let { mediaFiles.deleteSessionFiles(it.name, session.name) }
            _message.value = "Expedição apagada."
            onDone()
        }
    }

    fun saveOccurrence(occurrence: OccurrenceEntity, onSaved: (Long) -> Unit) {
        val error = validateCoordinates(occurrence.latitude, occurrence.longitude)
        if (error != null) {
            _message.value = error
            return
        }
        if (occurrence.latitude == null || occurrence.longitude == null) {
            _message.value = "Aviso: registro salvo sem coordenadas."
        } else if (occurrence.scientificName.isBlank()) {
            _message.value = "Aviso: nome da espécie é recomendado, mas o registro será salvo."
        }
        viewModelScope.launch {
            try {
                val id = if (occurrence.id == 0L) repository.createOccurrence(occurrence) else {
                    repository.updateOccurrence(occurrence)
                    occurrence.id
                }
                onSaved(id)
            } catch (_: SQLiteConstraintException) {
                _message.value = "occurrenceID já existe. Use um identificador único."
            }
        }
    }

    fun saveOccurrenceWithMedia(occurrence: OccurrenceEntity, pendingMedia: List<MediaEntity>, onSaved: (Long) -> Unit) {
        val error = validateCoordinates(occurrence.latitude, occurrence.longitude)
        if (error != null) {
            _message.value = error
            return
        }
        if (occurrence.latitude == null || occurrence.longitude == null) {
            _message.value = "Aviso: registro salvo sem coordenadas."
        } else if (occurrence.scientificName.isBlank()) {
            _message.value = "Aviso: nome da espécie é recomendado, mas o registro será salvo."
        }
        viewModelScope.launch {
            try {
                val id = if (occurrence.id == 0L) repository.createOccurrence(occurrence) else {
                    repository.updateOccurrence(occurrence)
                    occurrence.id
                }
                pendingMedia.forEach { media ->
                    repository.createMedia(media.copy(occurrenceId = id))
                }
                onSaved(id)
            } catch (_: SQLiteConstraintException) {
                _message.value = "occurrenceID já existe. Use um identificador único."
            }
        }
    }

    fun deleteOccurrence(occurrence: OccurrenceEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.getMediaForOccurrence(occurrence.id).forEach { mediaFiles.deleteIfManaged(it.filePath) }
            repository.deleteOccurrence(occurrence)
            _message.value = "Ocorrência apagada."
            onDone()
        }
    }

    fun addMedia(media: MediaEntity) {
        viewModelScope.launch { repository.createMedia(media) }
    }

    fun updateMedia(media: MediaEntity) {
        viewModelScope.launch {
            repository.updateMedia(media)
            _message.value = "Mídia atualizada."
        }
    }

    fun deleteMedia(media: MediaEntity) {
        viewModelScope.launch {
            repository.deleteMedia(media)
            mediaFiles.deleteIfManaged(media.filePath)
            _message.value = "Mídia removida."
        }
    }

    fun addTrackPoint(trackId: Long, location: CapturedLocation) {
        viewModelScope.launch {
            val track = repository.getTrack(trackId) ?: return@launch
            recordTrackPoint(track, location, showMessage = true)
        }
    }

    private suspend fun recordTrackPoint(track: GpsTrackEntity, location: CapturedLocation, showMessage: Boolean): Boolean {
        if (!track.active) return false
        val previous = repository.getLastTrackPoint(track.id)
        val decision = TrackPointRules.decide(previous, location.latitude, location.longitude, location.accuracyMeters, location.capturedAt)
        if (!decision.accepted) {
            if (showMessage) _message.value = "Ponto ignorado: precisão baixa ou deslocamento insuficiente."
            return false
        }
        repository.addTrackPoint(
            GpsTrackPointEntity(
                trackId = track.id,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                accuracy = location.accuracyMeters,
                timestamp = location.capturedAt
            )
        )
        val latest = repository.getTrack(track.id) ?: track
        repository.updateTrack(
            latest.copy(
                totalDistance = latest.totalDistance + decision.distanceFromPrevious,
                duration = System.currentTimeMillis() - latest.startTime,
                pointsCount = repository.countTrackPoints(track.id)
            )
        )
        if (showMessage) _message.value = "Ponto da trilha salvo."
        return true
    }

    fun startTrack(session: SessionEntity, onStarted: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val existingActive = repository.getTracks(session.projectId).firstOrNull { it.sessionId == session.id && it.active }
            if (existingActive != null) {
                onStarted(existingActive.id)
                return@launch
            }
            val id = repository.createTrack(
                GpsTrackEntity(
                    projectId = session.projectId,
                    sessionId = session.id,
                    eventId = session.eventId,
                    coordinateFormat = "DD",
                    geodeticDatum = session.geodeticDatum,
                    active = true
                )
            )
            startTrackService(id)
            _message.value = "Trilha GPS iniciada. A linha será atualizada em tempo real."
            onStarted(id)
        }
    }

    fun stopTrack(track: GpsTrackEntity, finalLocation: CapturedLocation? = null) {
        viewModelScope.launch {
            if (finalLocation != null) recordTrackPoint(track, finalLocation, showMessage = false)
            val latest = repository.getTrack(track.id) ?: track
            val now = System.currentTimeMillis()
            repository.updateTrack(
                latest.copy(
                    endTime = now,
                    duration = now - latest.startTime,
                    active = false
                )
            )
            stopTrackService()
            _message.value = "Trilha GPS encerrada."
        }
    }

    fun importTrack(session: SessionEntity, points: List<ImportedTrackPoint>) {
        viewModelScope.launch {
            val cleanPoints = points
                .filter { validateCoordinates(it.latitude, it.longitude) == null }
                .distinctBy { "${it.latitude},${it.longitude},${it.timestamp}" }
            if (cleanPoints.size < 2) {
                _message.value = "Não foi possível importar: a trilha precisa ter pelo menos 2 pontos válidos."
                return@launch
            }
            val trackId = repository.createTrack(
                GpsTrackEntity(
                    projectId = session.projectId,
                    sessionId = session.id,
                    eventId = session.eventId,
                    startTime = cleanPoints.first().timestamp,
                    endTime = cleanPoints.last().timestamp,
                    totalDistance = importedTrackDistance(cleanPoints),
                    duration = (cleanPoints.last().timestamp - cleanPoints.first().timestamp).coerceAtLeast(0L),
                    pointsCount = cleanPoints.size,
                    coordinateFormat = "DD",
                    geodeticDatum = session.geodeticDatum,
                    active = false
                )
            )
            cleanPoints.forEach { point ->
                repository.addTrackPoint(
                    GpsTrackPointEntity(
                        trackId = trackId,
                        latitude = point.latitude,
                        longitude = point.longitude,
                        altitude = point.altitude,
                        accuracy = point.accuracy,
                        timestamp = point.timestamp
                    )
                )
            }
            _message.value = "Trilha importada com ${cleanPoints.size} pontos."
        }
    }

    private fun startTrackService(trackId: Long) {
        val context = getApplication<Application>()
        val intent = Intent(context, TrackRecordingService::class.java)
            .setAction(TrackRecordingService.ACTION_START)
            .putExtra(TrackRecordingService.EXTRA_TRACK_ID, trackId)
        ContextCompat.startForegroundService(context, intent)
    }

    private fun stopTrackService() {
        val context = getApplication<Application>()
        context.startService(Intent(context, TrackRecordingService::class.java).setAction(TrackRecordingService.ACTION_STOP))
    }

    private fun importedTrackDistance(points: List<ImportedTrackPoint>): Double =
        points.zipWithNext().sumOf { (a, b) -> haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude) }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    fun captureLocation(onResult: (CapturedLocation?) -> Unit) {
        viewModelScope.launch {
            val location = locationService.currentLocation()
            if (location == null) _message.value = "Não foi possível capturar a localização agora."
            onResult(location)
        }
    }

    fun exportProject(projectId: Long, format: ExportFormat, content: ExportContent, onReady: (Intent, String?) -> Unit) {
        viewModelScope.launch {
            val project = repository.getProject(projectId) ?: return@launch
            val sessions = repository.getSessions(projectId)
            val occurrences = repository.getOccurrences(projectId)
            val media = repository.getMedia(projectId)
            val fields = repository.getFields(projectId)
            val result = exporter.export(format, project, sessions, occurrences, media, fields, exportTracks(projectId), content)
            val file = mediaFiles.writeExport(result)
            onReady(shareIntent(file, result.mimeType), result.warning)
        }
    }

    fun exportSession(projectId: Long, sessionId: Long, format: ExportFormat, content: ExportContent, onReady: (Intent, String?) -> Unit) {
        viewModelScope.launch {
            val project = repository.getProject(projectId) ?: return@launch
            val session = repository.getSession(sessionId) ?: return@launch
            val occurrences = repository.getOccurrencesForSession(sessionId)
            val media = repository.getMediaForSession(sessionId)
            val fields = repository.getFields(projectId)
            val result = exporter.export(
                format,
                project,
                listOf(session),
                occurrences,
                media,
                fields,
                exportTracks(projectId).filter { it.eventID == session.eventId },
                content,
                "expedicao_${OikosExporter.safeName(session.name.ifBlank { session.eventId })}"
            )
            val file = mediaFiles.writeExport(result)
            onReady(shareIntent(file, result.mimeType), result.warning)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun showMessage(message: String) {
        _message.value = message
    }

    private fun shareIntent(file: File, mimeType: String): Intent {
        val context = getApplication<Application>()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, file.name)
            putExtra(Intent.EXTRA_SUBJECT, "Exportacao Oikos FieldBook")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun validateCoordinates(latitude: Double?, longitude: Double?): String? = when {
        !CoordinateUtils.validLatitude(latitude) -> "Latitude deve estar entre -90 e 90."
        !CoordinateUtils.validLongitude(longitude) -> "Longitude deve estar entre -180 e 180."
        else -> null
    }

    private suspend fun exportTracks(projectId: Long): List<TrackExportBundle> =
        repository.getTracks(projectId).map { track ->
            TrackExportBundle(
                trackID = "TRK-${track.id.toString().padStart(6, '0')}",
                projectID = "PROJ-${track.projectId.toString().padStart(3, '0')}",
                eventID = track.eventId,
                startTime = track.startTime,
                endTime = track.endTime,
                totalDistance = track.totalDistance,
                duration = track.duration,
                pointsCount = track.pointsCount,
                coordinateFormat = track.coordinateFormat,
                geodeticDatum = track.geodeticDatum,
                points = repository.getTrackPoints(track.id).map {
                    TrackPointExport(it.latitude, it.longitude, it.altitude, it.accuracy, it.timestamp, it.speed)
                }
            )
        }

    companion object {
        fun newEventId(): String = "event-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}-${UUID.randomUUID().toString().take(8)}"
    }
}

data class ImportedTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)

