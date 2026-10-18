package com.oikos.fieldbook

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import com.oikos.fieldbook.services.AudioInfo
import com.oikos.fieldbook.services.AudioInfoReader
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.oikos.fieldbook.backup.BackupManifest
import com.oikos.fieldbook.backup.RestoreOutcome
import com.oikos.fieldbook.backup.BackupService
import com.oikos.fieldbook.checklist.ChecklistService
import com.oikos.fieldbook.checklist.ChecklistSource
import com.oikos.fieldbook.checklist.ChecklistSources
import com.oikos.fieldbook.checklist.RemoteVersion
import com.oikos.fieldbook.data.ChecklistEntity
import com.oikos.fieldbook.geo.CoordinateQuality
import com.oikos.fieldbook.geo.MunicipalityLocator
import com.oikos.fieldbook.services.OfflineMap
import com.oikos.fieldbook.services.TrackFileParser
import com.oikos.fieldbook.services.OfflineMapService
import com.oikos.fieldbook.services.TrashItem
import com.oikos.fieldbook.services.TrashService
import com.oikos.fieldbook.services.ProjectTransfer
import com.oikos.fieldbook.services.TransferManifest
import com.oikos.fieldbook.services.TrackProfile
import com.oikos.fieldbook.data.TaxonNameEntity
import com.oikos.fieldbook.data.TaxonSuggestion
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
import com.oikos.fieldbook.export.ExportResult
import com.oikos.fieldbook.export.OikosExporter
import com.oikos.fieldbook.export.SpecimenLabelPdf
import com.oikos.fieldbook.export.SpecimenLabels
import com.oikos.fieldbook.export.TrackExportBundle
import com.oikos.fieldbook.export.TrackPointExport
import com.oikos.fieldbook.services.CapturedLocation
import com.oikos.fieldbook.services.LocationService
import com.oikos.fieldbook.services.MediaFileService
import com.oikos.fieldbook.services.PhotoMetadata
import com.oikos.fieldbook.services.TrackPointRules
import com.oikos.fieldbook.services.TrackRecordingService
import com.oikos.fieldbook.services.WavAudioRecorder
import com.oikos.fieldbook.util.CoordinateUtils
import com.oikos.fieldbook.util.allocatableBytes
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Destino de uma midia recem-capturada: [draftKey] nulo grava direto no banco
 * (midia da expedicao ou de ocorrencia ja salva); preenchido, a midia fica
 * pendente no formulario da ocorrencia nova ate ela ser salva.
 */
data class MediaTarget(
    val projectId: Long,
    val sessionId: Long,
    val occurrenceId: Long?,
    val draftKey: String?
)

/** Condicoes anotadas pelo pesquisador para uma gravacao (Gannon et al. 2022). */
data class RecordingConditions(
    val microphone: String = "",
    val airTemperature: Double? = null,
    val relativeHumidity: Double? = null,
    val distanceToSourceMeters: Double? = null
)

/** Gravacao de audio em andamento. Vive no ViewModel para sobreviver a rotacao. */
data class RecordingState(
    val filePath: String,
    val startedAt: Long,
    val target: MediaTarget,
    val subject: String,
    val description: String,
    val conditions: RecordingConditions = RecordingConditions()
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: OikosRepository = (application as OikosApplication).repository
    private val exporter = OikosExporter()
    private val locationService = LocationService(application)
    val mediaFiles = MediaFileService(application)
    private val settings = application.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)

    val projects: Flow<List<ProjectEntity>> = repository.observeProjects()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting

    private val _recording = MutableStateFlow<RecordingState?>(null)
    val recording: StateFlow<RecordingState?> = _recording
    private val wavRecorder = WavAudioRecorder()
    /** Gravacoes paradas cujo arquivo ainda esta sendo fechado. */
    private val finishingRecordings = java.util.concurrent.atomic.AtomicInteger(0)

    /** Midias capturadas para ocorrencias ainda nao salvas, por chave de rascunho. */
    private val _pendingMedia = MutableStateFlow<Map<String, List<MediaEntity>>>(emptyMap())
    val pendingMedia: StateFlow<Map<String, List<MediaEntity>>> = _pendingMedia

    private val backupService = BackupService(application as OikosApplication)

    /** Andamento de backup/restauracao: rotulo e fracao (0..1) ou null se indeterminado. */
    data class BackupProgress(val label: String, val fraction: Float?)

    private val _backupProgress = MutableStateFlow<BackupProgress?>(null)
    val backupProgress: StateFlow<BackupProgress?> = _backupProgress

    private val _lastBackupAt = MutableStateFlow(settings.getLong(KEY_LAST_BACKUP, 0L))
    val lastBackupAt: StateFlow<Long> = _lastBackupAt

    // Antes do init: ele ja le os mapas instalados.
    private val offlineMapService = OfflineMapService(application)
    private val _offlineMaps = MutableStateFlow<List<OfflineMap>>(emptyList())
    val offlineMaps: StateFlow<List<OfflineMap>> = _offlineMaps

    /** Mapa offline em uso; nulo = OpenStreetMap pela internet. */
    private val _activeOfflineMap = MutableStateFlow<OfflineMap?>(null)
    val activeOfflineMap: StateFlow<OfflineMap?> = _activeOfflineMap

    private val _offlineMapProgress = MutableStateFlow<BackupProgress?>(null)
    val offlineMapProgress: StateFlow<BackupProgress?> = _offlineMapProgress

    private val trash = TrashService((application as OikosApplication).database, application.filesDir)
    private val _trashItems = MutableStateFlow<List<TrashItem>>(emptyList())
    val trashItems: StateFlow<List<TrashItem>> = _trashItems

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { trash.purgeOlderThan() }
            runCatching { mediaFiles.cleanupInterruptedImports() }
            _trashItems.value = runCatching { trash.list() }.getOrDefault(emptyList())
        }
        refreshOfflineMaps()
        resumeInterruptedTracks()
        when (val outcome = (application as OikosApplication).restoreOutcome) {
            RestoreOutcome.Restored -> _message.value = "Backup restaurado. Confira projetos, expedições e mídias."
            is RestoreOutcome.Failed -> _message.value = "A restauração do backup não foi concluída: ${outcome.reason}"
            RestoreOutcome.None -> Unit
        }
    }

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

    // ---------------------------------------------------------------- ajustes

    /** Geocodificacao reversa: envia a coordenada ao servico do Google. Desligada por padrao. */
    var reverseGeocodingEnabled: Boolean
        get() = settings.getBoolean(KEY_REVERSE_GEOCODING, false)
        set(value) {
            settings.edit().putBoolean(KEY_REVERSE_GEOCODING, value).apply()
        }

    // ---------------------------------------------------------------- listas de especies

    private val checklistService = ChecklistService(application, (application as OikosApplication).checklistDatabase)

    init {
        // Aqui, depois de checklistService existir: o init do topo roda antes.
        viewModelScope.launch(Dispatchers.IO) { runCatching { checklistService.removeInterruptedImports() } }
    }
    val checklists: Flow<List<ChecklistEntity>> = (application as OikosApplication).checklistDatabase.checklistDao().observeChecklists()

    /** Andamento por fonte: "flora_brasil" -> ("Baixando 12 MB", 0.6). */
    private val _checklistProgress = MutableStateFlow<Map<String, BackupProgress>>(emptyMap())
    val checklistProgress: StateFlow<Map<String, BackupProgress>> = _checklistProgress

    /** Versao mais nova publicada por fonte, quando conferida com internet. */
    private val _latestVersions = MutableStateFlow<Map<String, RemoteVersion>>(emptyMap())
    val latestVersions: StateFlow<Map<String, RemoteVersion>> = _latestVersions

    fun downloadChecklist(source: ChecklistSource) {
        if (_checklistProgress.value.containsKey(source.key)) return
        _checklistProgress.value = _checklistProgress.value + (source.key to BackupProgress("Conectando...", null))
        viewModelScope.launch {
            runCatching {
                checklistService.download(source) { stage, fraction ->
                    _checklistProgress.value = _checklistProgress.value + (source.key to BackupProgress(stage, fraction))
                }
            }.onSuccess {
                _latestVersions.value = _latestVersions.value + (source.key to RemoteVersion(it.version, it.pubDate))
                _message.value = "${it.title}: ${it.taxonCount} nomes instalados (versão ${it.version})."
            }.onFailure {
                _message.value = "Não foi possível baixar ${source.title}: ${it.message ?: it.javaClass.simpleName}."
            }
            _checklistProgress.value = _checklistProgress.value - source.key
        }
    }

    fun importChecklistFile(uri: Uri) {
        val key = "arquivo"
        if (_checklistProgress.value.containsKey(key)) return
        _checklistProgress.value = _checklistProgress.value + (key to BackupProgress("Lendo o arquivo...", null))
        viewModelScope.launch {
            runCatching {
                checklistService.importFile(uri) { stage, fraction ->
                    _checklistProgress.value = _checklistProgress.value + (key to BackupProgress(stage, fraction))
                }
            }.onSuccess { _message.value = "${it.title}: ${it.taxonCount} nomes instalados." }
                .onFailure { _message.value = "Não foi possível importar a lista: ${it.message ?: it.javaClass.simpleName}." }
            _checklistProgress.value = _checklistProgress.value - key
        }
    }

    fun removeChecklist(checklist: ChecklistEntity) {
        viewModelScope.launch {
            checklistService.remove(checklist)
            _message.value = "Lista removida: ${checklist.title}."
        }
    }

    /** Confere, com internet, se as fontes oficiais publicaram versao nova. */
    fun checkChecklistUpdates() {
        viewModelScope.launch {
            ChecklistSources.all.forEach { source ->
                checklistService.latestVersion(source)?.let { version ->
                    _latestVersions.value = _latestVersions.value + (source.key to version)
                }
            }
        }
    }

    suspend fun searchNames(query: String): List<TaxonSuggestion> =
        runCatching { checklistService.search(query) }.getOrDefault(emptyList())

    suspend fun acceptedFor(suggestion: TaxonSuggestion): TaxonNameEntity? = checklistService.accepted(suggestion)

    suspend fun vernacularFor(taxon: TaxonNameEntity): String? = checklistService.vernacularFor(taxon)

    /** Icone de cada registro no mapa (so leitura das listas). */
    suspend fun mapIconGroups(occurrences: List<OccurrenceEntity>): Map<Long, com.oikos.fieldbook.checklist.MapIconGroup> =
        occurrences.associate { it.id to (runCatching { checklistService.mapIconGroup(it) }.getOrNull() ?: com.oikos.fieldbook.checklist.MapIconGroup.OTHER) }

    suspend fun checkName(fullName: String, authorship: String): com.oikos.fieldbook.checklist.NameCheck? =
        runCatching { checklistService.check(fullName, authorship) }.getOrNull()

    // ---------------------------------------------------------------- backup

    fun createBackup(target: Uri) {
        if (_backupProgress.value != null) return
        _backupProgress.value = BackupProgress("Criando backup...", 0f)
        viewModelScope.launch {
            runCatching {
                backupService.create(target) { done, total ->
                    _backupProgress.value = BackupProgress("Criando backup...", if (total > 0) done.toFloat() / total else null)
                }
            }.onSuccess { manifest ->
                val now = System.currentTimeMillis()
                settings.edit().putLong(KEY_LAST_BACKUP, now).apply()
                _lastBackupAt.value = now
                _message.value = "Backup salvo: ${manifest.projects} projeto(s), ${manifest.occurrences} ocorrência(s), ${manifest.mediaFiles} mídia(s)."
            }.onFailure {
                _message.value = "O backup falhou: ${it.message ?: it.javaClass.simpleName}. Confira o espaço no destino."
            }
            _backupProgress.value = null
        }
    }

    fun inspectBackup(source: Uri, onResult: (BackupManifest?) -> Unit) {
        viewModelScope.launch {
            val manifest = backupService.inspect(source)
            if (manifest == null) _message.value = "Este arquivo não é um backup do Oikos FieldBook."
            onResult(manifest)
        }
    }

    /**
     * Prepara a restauracao e pede o reinicio do app, que conclui a troca antes
     * de abrir o banco. Trilha e gravacao em andamento sao encerradas antes.
     */
    fun restoreBackup(source: Uri, onReadyToRestart: () -> Unit) {
        if (_backupProgress.value != null) return
        _backupProgress.value = BackupProgress("Preparando a restauração...", null)
        viewModelScope.launch {
            stopRecording()
            stopTrackService()
            runCatching { backupService.stageRestore(source) }
                .onSuccess {
                    backupService.checkpointBeforeRestart()
                    onReadyToRestart()
                }
                .onFailure {
                    _message.value = "Não foi possível restaurar: ${it.message ?: it.javaClass.simpleName}. Seus dados atuais não foram alterados."
                }
            _backupProgress.value = null
        }
    }

    // ---------------------------------------------------------------- projetos

    /**
     * Erro do banco vira mensagem; nunca derruba o app com o formulario
     * preenchido (antes, disco cheio fechava o app e o registro digitado sumia).
     */
    private fun saveFailureMessage(error: Throwable, duplicate: String): String = when (error) {
        is SQLiteConstraintException -> duplicate
        is SQLiteFullException -> "Sem espaço no aparelho: nada foi salvo. Libere espaço e toque em Salvar de novo; o que você digitou continua na tela."
        else -> "Nada foi salvo: ${error.message ?: error.javaClass.simpleName}. O que você digitou continua na tela."
    }

    fun saveProject(project: ProjectEntity, onFailed: () -> Unit = {}, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                val id = if (project.id == 0L) repository.createProject(project) else {
                    repository.updateProject(project)
                    project.id
                }
                onSaved(id)
            } catch (error: SQLiteException) {
                _message.value = saveFailureMessage(error, "Já existe um projeto igual.")
                onFailed()
            }
        }
    }

    fun deleteProject(project: ProjectEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            stopTrackServiceIf { it.projectId == project.id }
            finishRecordingFor { it.projectId == project.id }
            moveToTrash("Projeto", onDone) { trash.trashProject(project.id, project.name) }
        }
    }

    fun updateField(field: FieldConfigEntity) {
        viewModelScope.launch { repository.updateField(field) }
    }

    fun saveFieldConfiguration(projectId: Long, fields: List<FieldConfigEntity>) {
        viewModelScope.launch {
            try {
                repository.replaceFieldConfig(projectId, fields)
                _message.value = "Configuração de campos salva."
            } catch (error: SQLiteException) {
                _message.value = "A configuração de campos não foi salva: ${error.message ?: error.javaClass.simpleName}."
            }
        }
    }

    fun ensureFieldCatalog(projectId: Long) {
        viewModelScope.launch { repository.ensureFieldCatalog(projectId) }
    }

    // ---------------------------------------------------------------- expedicoes

    fun saveSession(session: SessionEntity, onFailed: () -> Unit = {}, onSaved: (Long) -> Unit) {
        val error = validateCoordinates(session.latitude, session.longitude)
        if (error != null) {
            _message.value = error
            onFailed()
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
            } catch (error: SQLiteException) {
                _message.value = saveFailureMessage(error, "eventID já existe. Use um identificador único.")
                onFailed()
            }
        }
    }

    fun deleteSession(session: SessionEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            // So para a trilha se ela for DESTA expedicao. Antes, apagar
            // qualquer expedicao parava a trilha ativa de outra, que ficava
            // marcada como ativa sem gravar mais nada.
            stopTrackServiceIf { it.sessionId == session.id }
            finishRecordingFor { it.sessionId == session.id }
            moveToTrash("Expedição", onDone) { trash.trashSession(session.projectId, session.id, session.name.ifBlank { session.eventId }) }
        }
    }

    // ---------------------------------------------------------------- lixeira

    /** Apaga indo para a lixeira; [action] roda em IO e devolve o item criado. */
    private suspend fun moveToTrash(what: String, onDone: () -> Unit, action: () -> TrashItem) {
        runCatching { withContext(Dispatchers.IO) { action() } }
            .onSuccess {
                _message.value = "$what na lixeira por ${TrashService.RETENTION_DAYS} dias (Configurações > Lixeira)."
                refreshTrash()
                onDone()
            }
            .onFailure { _message.value = "Não foi possível apagar: ${it.message ?: it.javaClass.simpleName}." }
    }

    fun refreshTrash() {
        viewModelScope.launch { _trashItems.value = withContext(Dispatchers.IO) { trash.list() } }
    }

    fun restoreFromTrash(item: TrashItem) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { trash.restore(item) } }
                .onSuccess { failed ->
                    _message.value = if (failed == 0) "Restaurado: ${item.title}."
                    else "Restaurado: ${item.title}, mas $failed arquivo(s) não puderam voltar (falta de espaço?)."
                }
                .onFailure { _message.value = "Não foi possível restaurar: ${it.message ?: it.javaClass.simpleName}" }
            refreshTrash()
        }
    }

    fun deleteForever(item: TrashItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { trash.delete(item) }
            refreshTrash()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { trash.list().forEach(trash::delete) }
            _message.value = "Lixeira esvaziada."
            refreshTrash()
        }
    }

    // ---------------------------------------------------------------- ocorrencias

    fun saveOccurrenceWithMedia(occurrence: OccurrenceEntity, pendingMedia: List<MediaEntity>, onFailed: () -> Unit = {}, onSaved: (Long) -> Unit) {
        val error = validateCoordinates(occurrence.latitude, occurrence.longitude)
        if (error != null) {
            _message.value = error
            onFailed()
            return
        }
        viewModelScope.launch {
            try {
                // Registro e midias juntos: antes, uma falha no meio deixava o
                // registro salvo com so parte das fotos.
                val id = (getApplication<OikosApplication>()).database.withTransaction {
                    val savedId = if (occurrence.id == 0L) repository.createOccurrence(occurrence) else {
                        repository.updateOccurrence(occurrence)
                        occurrence.id
                    }
                    pendingMedia.forEach { media -> repository.createMedia(media.copy(id = 0L, occurrenceId = savedId)) }
                    savedId
                }
                when {
                    occurrence.latitude == null || occurrence.longitude == null -> _message.value = "Aviso: registro salvo sem coordenadas."
                    occurrence.scientificName.isBlank() -> _message.value = "Aviso: registro salvo sem o nome da espécie."
                }
                onSaved(id)
            } catch (error: SQLiteException) {
                _message.value = saveFailureMessage(error, "occurrenceID já existe. Use um identificador único.")
                onFailed()
            }
        }
    }

    fun deleteOccurrence(occurrence: OccurrenceEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            finishRecordingFor { it.occurrenceId == occurrence.id }
            // Antes as linhas das midias ficavam no banco, apontando para
            // arquivos apagados; agora saem junto com a ocorrencia.
            moveToTrash("Ocorrência", onDone) { trash.trashOccurrence(occurrence.id, occurrence.scientificName.ifBlank { occurrence.occurrenceId }) }
        }
    }

    /** Descarta as midias de um rascunho abandonado, para nao deixar arquivo orfao. */
    fun discardPendingMedia(media: List<MediaEntity>) {
        viewModelScope.launch(Dispatchers.IO) { media.forEach { mediaFiles.deleteIfManaged(it.filePath) } }
    }

    // ---------------------------------------------------------------- midias

    fun updateMedia(media: MediaEntity) {
        viewModelScope.launch {
            repository.updateMedia(media)
            _message.value = "Mídia atualizada."
        }
    }

    fun deleteMedia(media: MediaEntity) {
        viewModelScope.launch {
            moveToTrash("Mídia", {}) { trash.trashMedia(media.id, media.fileName) }
        }
    }

    /** Midias pendentes que chegaram para um rascunho; o formulario as recolhe. */
    fun takePendingMedia(draftKey: String): List<MediaEntity> {
        val current = _pendingMedia.value[draftKey].orEmpty()
        if (current.isNotEmpty()) _pendingMedia.value = _pendingMedia.value - draftKey
        return current
    }

    /**
     * Midias ainda em processamento por rascunho (foto comprimindo, gravacao
     * fechando). O formulario nao salva nem sai sem aviso enquanto houver uma:
     * antes, uma foto que terminava depois de "Salvar e novo" caia no registro
     * seguinte.
     */
    private val _busyDrafts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val busyDrafts: StateFlow<Map<String, Int>> = _busyDrafts

    /** Rascunhos descartados: midia que chegar depois e apagada, nao anexada. */
    private val discardedDrafts: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    private fun markBusy(target: MediaTarget, delta: Int) {
        val key = target.draftKey ?: return
        _busyDrafts.update { map ->
            val count = (map[key] ?: 0) + delta
            if (count <= 0) map - key else map + (key to count)
        }
    }

    /** Falso se a midia nao pode ser registrada; a mensagem ja foi mostrada. */
    private suspend fun deliver(target: MediaTarget, media: MediaEntity): Boolean {
        val key = target.draftKey
        when {
            key == null -> try {
                repository.createMedia(media)
            } catch (error: SQLiteException) {
                // Antes o erro escapava da corrotina e fechava o app. O arquivo
                // fica no aparelho; so o registro nao foi feito.
                val reason = if (error is SQLiteFullException) "sem espaço no aparelho" else error.message ?: error.javaClass.simpleName
                _message.value = "A mídia ${media.fileName} não pôde ser registrada ($reason). Libere espaço e registre de novo."
                return false
            }
            key in discardedDrafts -> withContext(Dispatchers.IO) { mediaFiles.deleteIfManaged(media.filePath) }
            else -> _pendingMedia.update { map -> map + (key to (map[key].orEmpty() + media)) }
        }
        return true
    }

    /**
     * Espaco para mais uma midia e o registro dela no banco. Conferido antes
     * de abrir a camera ou copiar um arquivo, para nao chegar ao disco cheio
     * com a foto tirada e sem onde registra-la.
     */
    fun hasRoomForMedia(): Boolean {
        val app = getApplication<Application>()
        val free = allocatableBytes(app, app.filesDir)
        if (free >= MIN_MEDIA_FREE_BYTES) return true
        _message.value = "Pouco espaço livre (${free / (1024 * 1024)} MB). Libere espaço no aparelho antes de fotografar ou importar."
        return false
    }

    /** Rascunho abandonado: gravacao cancelada e midias apagadas, inclusive as que ainda vao chegar. */
    fun discardDraft(draftKey: String, pending: List<MediaEntity>) {
        discardedDrafts += draftKey
        discardRecording(draftKey)
        discardPendingMedia(pending + takePendingMedia(draftKey))
    }

    /**
     * Foto tirada pela camera. O EXIF e lido ANTES da recompressao: antes era
     * lido depois, quando data, GPS e camera ja tinham sido descartados.
     * Roda fora da main thread: uma foto de 12 MP travava a tela. Se a
     * compressao falhar (memoria, disco cheio), a foto original e mantida.
     */
    fun processCapturedPhoto(file: File, target: MediaTarget, quality: String, subject: String, description: String, onMetadata: (PhotoMetadata) -> Unit = {}) {
        markBusy(target, +1)
        viewModelScope.launch {
            try {
                if (!file.isFile || file.length() == 0L) {
                    _message.value = "A câmera não entregou a foto. Tente de novo."
                    file.delete()
                    return@launch
                }
                val (metadata, compressError) = withContext(Dispatchers.IO) {
                    val meta = mediaFiles.readPhotoMetadata(file)
                    meta to runCatching { mediaFiles.compressImage(file, quality) }.exceptionOrNull()
                }
                if (compressError != null) _message.value = "Foto guardada sem reduzir o tamanho (${compressError.message ?: compressError.javaClass.simpleName})."
                onMetadata(metadata)
                deliver(target, mediaEntity(target, "photo", file, "image/jpeg", subject, description, metadata))
            } catch (error: Exception) {
                _message.value = "Não foi possível registrar a foto: ${error.message ?: error.javaClass.simpleName}."
            } finally {
                markBusy(target, -1)
            }
        }
    }

    fun importMedia(uri: Uri, type: String, target: MediaTarget, baseName: String, quality: String, subject: String, description: String, onMetadata: (PhotoMetadata) -> Unit = {}) {
        markBusy(target, +1)
        viewModelScope.launch {
            try {
                importMediaNow(uri, type, target, baseName, quality, subject, description, onMetadata)
            } finally {
                markBusy(target, -1)
            }
        }
    }

    private suspend fun importMediaNow(uri: Uri, type: String, target: MediaTarget, baseName: String, quality: String, subject: String, description: String, onMetadata: (PhotoMetadata) -> Unit) {
        run {
            val app = getApplication<Application>()
            if (!hasRoomForMedia()) return@run
            val mime = app.contentResolver.getType(uri)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val imported = mediaFiles.importUri(uri, target.projectId, target.sessionId, type, mime, baseName)
                    if (type != "photo") return@runCatching Triple(imported, null, null)
                    // EXIF lido antes da reducao; reducao que falha mantem o original.
                    val metadata = mediaFiles.readPhotoMetadata(imported.file, fileTimeFallback = false)
                    val compressError = runCatching { mediaFiles.compressImage(imported.file, quality) }.exceptionOrNull()
                    Triple(mediaFiles.matchExtensionToContent(imported.file, imported.mimeType), metadata, compressError)
                }
            }
            result.onSuccess { (imported, metadata, compressError) ->
                if (compressError != null) _message.value = "Foto guardada sem reduzir o tamanho (${compressError.message ?: compressError.javaClass.simpleName})."
                metadata?.let(onMetadata)
                val file = imported.file
                // Data de captura so a gravada no proprio arquivo (EXIF da foto); sem
                // ela, a data fica marcada como desconhecida.
                var entity = mediaEntity(target, type, file, imported.mimeType, subject, description, metadata)
                    .copy(captureTimeKnown = metadata?.capturedAt != null)
                if (type == "audio") {
                    val info = withContext(Dispatchers.IO) { importedAudioInfo(file) }
                    // Data gravada no proprio arquivo (M4A/MP4 de gravador),
                    // quando existe; senao fica o momento da importacao.
                    val recordedAt = withContext(Dispatchers.IO) { embeddedAudioDate(file) }
                    entity = entity.copy(
                        durationMillis = info?.durationMillis,
                        sampleRateHz = info?.sampleRateHz,
                        bitsPerSample = info?.bitsPerSample,
                        channels = info?.channels,
                        createdAt = recordedAt ?: entity.createdAt,
                        captureTimeKnown = recordedAt != null
                    )
                }
                deliver(target, entity)
            }.onFailure {
                _message.value = "Não foi possível importar o arquivo: ${it.message ?: "erro de leitura"}."
            }
        }
    }

    // ---------------------------------------------------------------- gravacao

    // ---------------------------------------------------------- mapas offline

    private fun refreshOfflineMaps() {
        viewModelScope.launch {
            val maps = withContext(Dispatchers.IO) { offlineMapService.installed() }
            _offlineMaps.value = maps
            val active = settings.getString(KEY_OFFLINE_MAP, null)
            _activeOfflineMap.value = maps.firstOrNull { it.file.name == active }
        }
    }

    fun importOfflineMap(uri: Uri) {
        if (_offlineMapProgress.value != null) return
        _offlineMapProgress.value = BackupProgress("Copiando o mapa...", null)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    offlineMapService.import(uri) { copied, total ->
                        _offlineMapProgress.value = BackupProgress(
                            "Copiando o mapa: ${copied / (1024 * 1024)} MB",
                            if (total > 0) copied.toFloat() / total else null
                        )
                    }
                }
            }.onSuccess { map ->
                setActiveOfflineMap(map)
                _message.value = "Mapa instalado: ${map.name} (zoom ${map.minZoom} a ${map.maxZoom})."
            }.onFailure {
                _message.value = "Não foi possível instalar o mapa: ${it.message ?: it.javaClass.simpleName}."
            }
            _offlineMapProgress.value = null
            refreshOfflineMaps()
        }
    }

    fun setActiveOfflineMap(map: OfflineMap?) {
        settings.edit().putString(KEY_OFFLINE_MAP, map?.file?.name).apply()
        _activeOfflineMap.value = map
    }

    fun removeOfflineMap(map: OfflineMap) {
        viewModelScope.launch {
            if (_activeOfflineMap.value?.file == map.file) setActiveOfflineMap(null)
            withContext(Dispatchers.IO) { offlineMapService.remove(map) }
            _message.value = "Mapa removido: ${map.name}."
            refreshOfflineMaps()
        }
    }

    // ---------------------------------------------------------- malha do IBGE

    /** 5,6 MB lidos so na primeira conferencia de coordenada. */
    private val municipalityLocator: Deferred<MunicipalityLocator?> = viewModelScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
        runCatching { MunicipalityLocator(getApplication<Application>().assets.open("municipios.bin").use { it.readBytes() }) }.getOrNull()
    }

    /** Municipio do ponto e avisos (sinal trocado, divisa, estado diferente do da expedicao). */
    suspend fun coordinateReport(
        latitude: Double,
        longitude: Double,
        declaredUf: String = "",
        declaredMunicipality: String = ""
    ): CoordinateQuality.Report? {
        val locator = municipalityLocator.await() ?: return null
        return withContext(Dispatchers.Default) {
            CoordinateQuality.check(latitude, longitude, locator.check(latitude, longitude), locator::locate, declaredUf, declaredMunicipality)
        }
    }

    // --------------------------------------------------------- entrada rapida

    /**
     * Campos que o pesquisador escolheu repetir do registro anterior, por
     * projeto (coletor, habitat, ponto...). Em transecto ou parcela, so a
     * especie muda de um registro para o outro.
     */
    fun repeatFields(projectId: Long): Set<String> =
        settings.getString("$KEY_REPEAT_FIELDS$projectId", "").orEmpty().split('|').filter { it.isNotBlank() }.toSet()

    fun setRepeatFields(projectId: Long, keys: Set<String>) {
        settings.edit().putString("$KEY_REPEAT_FIELDS$projectId", keys.joinToString("|")).apply()
    }

    /** Contagem rapida: varios registros de uma vez, todos ou nenhum. */
    fun saveOccurrences(occurrences: List<OccurrenceEntity>, onFailed: () -> Unit = {}, onSaved: () -> Unit) {
        if (occurrences.isEmpty()) {
            onFailed()
            return
        }
        viewModelScope.launch {
            try {
                (getApplication<OikosApplication>()).database.withTransaction {
                    occurrences.forEach { repository.createOccurrence(it) }
                }
                val individuals = occurrences.sumOf { it.individualCount ?: 0 }
                _message.value = "Contagem salva: ${occurrences.size} registro(s), $individuals indivíduo(s)."
                onSaved()
            } catch (error: SQLiteException) {
                _message.value = saveFailureMessage(error, "occurrenceID já existe. Use um identificador único.")
                onFailed()
            }
        }
    }

    /** Registros da expedicao, do mais recente para o mais antigo. */
    suspend fun occurrencesInSession(sessionId: Long): List<OccurrenceEntity> = repository.getOccurrencesForSession(sessionId)

    /** Densidade da trilha GPS; vale a partir da proxima trilha iniciada. */
    var trackProfile: TrackProfile
        get() = TrackProfile.parse(settings.getString(TrackProfile.PREFERENCE_KEY, null))
        set(value) {
            settings.edit().putString(TrackProfile.PREFERENCE_KEY, value.code).apply()
        }

    /** Ultimo microfone/gravador informado: em campo quase sempre e o mesmo. */
    var lastMicrophone: String
        get() = settings.getString(KEY_LAST_MICROPHONE, "").orEmpty()
        set(value) {
            settings.edit().putString(KEY_LAST_MICROPHONE, value).apply()
        }

    fun startRecording(
        target: MediaTarget,
        baseName: String,
        subject: String,
        description: String,
        conditions: RecordingConditions = RecordingConditions()
    ) {
        if (_recording.value != null) {
            _message.value = "Já existe uma gravação em andamento."
            return
        }
        // Gravacao anterior ainda sendo fechada: comecar outra agora disputaria
        // o microfone com ela.
        if (finishingRecordings.get() > 0) {
            _message.value = "Aguarde: a gravação anterior ainda está sendo salva."
            return
        }
        val app = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _message.value = "Permissão de áudio negada."
            return
        }
        val free = allocatableBytes(app, app.filesDir)
        if (free < MIN_RECORDING_FREE_BYTES) {
            _message.value = "Pouco espaço livre (${free / (1024 * 1024)} MB). Libere espaço antes de gravar: o WAV ocupa cerca de 5 MB por minuto."
            return
        }
        val file = mediaFiles.createAudioFile(target.projectId, target.sessionId, baseName)
        val sources = preferredAudioSources()
        val started = runCatching {
            wavRecorder.start(file, sources)
        }
        started.onFailure {
            file.delete()
            _message.value = it.message ?: "Não foi possível iniciar a gravação de áudio."
            return
        }
        if (conditions.microphone.isNotBlank()) lastMicrophone = conditions.microphone
        val state = RecordingState(file.absolutePath, System.currentTimeMillis(), target, subject, description, conditions)
        _recording.value = state
        // Gravacao que parou sozinha (microfone perdido, disco cheio) e
        // encerrada e registrada na hora, com o motivo. Antes a tela seguia
        // mostrando "gravando" sem gravar nada.
        viewModelScope.launch {
            while (_recording.value === state) {
                delay(1_000)
                if (_recording.value === state && !wavRecorder.isCapturing) stopRecording()
            }
        }
    }

    /**
     * Fonte de audio para bioacustica. A fonte MIC, usada ate a v0.2.0, aplica
     * controle automatico de ganho e reducao de ruido, que alteram amplitude e
     * espectro do sinal. UNPROCESSED entrega o sinal cru quando o aparelho
     * declara suporte; senao VOICE_RECOGNITION, que o Android exige sem ganho
     * automatico e sem reducao de ruido. MIC fica so como ultimo recurso.
     */
    private fun preferredAudioSources(): List<Int> {
        val audioManager = getApplication<Application>().getSystemService(AudioManager::class.java)
        val unprocessed = audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return if (unprocessed) {
            listOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.VOICE_RECOGNITION)
        } else {
            listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        }
    }

    fun recordingLevel(): Float = wavRecorder.level

    /**
     * Encerra e registra a gravacao. Chamado pelo botao Parar, ao sair da tela
     * ou quando o app vai para segundo plano (onde o Android 9+ entregaria
     * silencio no lugar do som).
     */
    fun stopRecording(reason: String? = null): Job? {
        val state = _recording.value ?: return null
        // Ocupado ate o audio ser entregue: fechar o arquivo leva ate meio
        // segundo, e salvar nesse intervalo deixava o audio de fora.
        markBusy(state.target, +1)
        finishingRecordings.incrementAndGet()
        _recording.value = null
        return viewModelScope.launch(NonCancellable) {
            try {
                finishRecording(state, reason)
            } finally {
                finishingRecordings.decrementAndGet()
                markBusy(state.target, -1)
            }
        }
    }

    /**
     * Gravacao destinada ao que vai ser apagado: termina e e registrada antes,
     * para ir junto para a lixeira. Antes o audio era descartado (expedicao,
     * projeto) ou registrado depois, preso a uma ocorrencia ja apagada.
     */
    private suspend fun finishRecordingFor(matches: (MediaTarget) -> Boolean) {
        val state = _recording.value ?: return
        if (!matches(state.target)) return
        // Rascunho nunca salvo nao tem para onde ir: o audio sai com ele.
        if (state.target.draftKey != null) cancelRecording() else stopRecording()?.join()
    }

    private suspend fun finishRecording(state: RecordingState, reason: String?) {
        run {
            val error = withContext(Dispatchers.IO) {
                wavRecorder.stop()
                wavRecorder.lastError
            }
            val file = File(state.filePath)
            if (!file.exists() || file.length() <= 44L) {
                _message.value = "A gravação não produziu áudio${error?.message?.let { ": $it" } ?: "."}"
                file.delete()
                return@run
            }
            val mime = "audio/wav"
            val info = withContext(Dispatchers.IO) { AudioInfoReader.readWav(file) }
            val entity = mediaEntity(state.target, "audio", file, mime, state.subject, state.description, null).copy(
                durationMillis = info?.durationMillis,
                sampleRateHz = info?.sampleRateHz,
                bitsPerSample = info?.bitsPerSample,
                channels = info?.channels,
                audioSource = wavRecorder.sourceName,
                captureDevice = deviceName(),
                microphone = state.conditions.microphone,
                airTemperature = state.conditions.airTemperature,
                relativeHumidity = state.conditions.relativeHumidity,
                distanceToSourceMeters = state.conditions.distanceToSourceMeters,
                // Momento da captura e o inicio da gravacao, nao o toque em Parar.
                createdAt = state.startedAt
            )
            if (!deliver(state.target, entity)) return@run
            // Falha no meio (disco cheio, microfone tomado) com parte gravada:
            // o audio fica, mas o pesquisador precisa saber que foi cortado.
            _message.value = if (error != null) "Áudio salvo, mas a gravação foi interrompida: ${error.message ?: error.javaClass.simpleName}." else reason ?: "Áudio salvo."
        }
    }

    /** Rascunho descartado: a gravacao dele e cancelada e o arquivo apagado. */
    fun discardRecording(draftKey: String) {
        if (_recording.value?.target?.draftKey == draftKey) cancelRecording()
    }

    private fun cancelRecording() {
        val state = _recording.value ?: return
        _recording.value = null
        finishingRecordings.incrementAndGet()
        viewModelScope.launch(NonCancellable + Dispatchers.IO) {
            try {
                wavRecorder.stop()
                File(state.filePath).delete()
            } finally {
                finishingRecordings.decrementAndGet()
            }
        }
    }

    override fun onCleared() {
        // Ultima garantia: nunca deixar o microfone aberto.
        if (_recording.value != null) {
            wavRecorder.stop()
        }
        super.onCleared()
    }

    // ---------------------------------------------------------------- trilha

    fun addTrackPoint(trackId: Long, location: CapturedLocation) {
        viewModelScope.launch {
            val track = repository.getTrack(trackId) ?: return@launch
            recordTrackPoint(track, location, showMessage = true)
        }
    }

    private suspend fun recordTrackPoint(track: GpsTrackEntity, location: CapturedLocation, showMessage: Boolean): Boolean {
        val recorded = repository.recordTrackPoint(
            trackId = track.id,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            accuracy = location.accuracyMeters,
            timestamp = location.capturedAt,
            speed = null
        )
        if (showMessage) {
            _message.value = if (recorded != null) "Ponto da trilha salvo." else "Ponto ignorado: precisão baixa ou deslocamento insuficiente."
        }
        return recorded != null
    }

    fun startTrack(session: SessionEntity, onStarted: (Long) -> Unit = {}) {
        if (!hasLocationPermission()) {
            _message.value = "Permita o acesso à localização para gravar a trilha."
            return
        }
        viewModelScope.launch {
            val active = repository.getActiveTracks()
            val existingActive = active.firstOrNull { it.sessionId == session.id }
            // So uma trilha grava por vez: a de outra expedicao e encerrada no
            // ultimo ponto. Antes ficava "ativa" sem gravar nada.
            active.filter { it.id != existingActive?.id }.forEach { other -> repository.closeTrack(other.id) }
            if (existingActive != null) {
                if (TrackRecordingService.runningTrackId != existingActive.id) startTrackService(existingActive.id)
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
            // Encerramento so pelos campos de fim: regravar a trilha inteira
            // apagava pontos e distancia somados enquanto isto rodava.
            repository.closeTrack(track.id, System.currentTimeMillis())
            if (TrackRecordingService.runningTrackId == track.id || TrackRecordingService.runningTrackId == null) stopTrackService()
            _message.value = "Trilha GPS encerrada."
        }
    }

    /**
     * Trilha marcada como ativa no banco sem servico gravando (app fechado a
     * forca, aparelho reiniciado): retoma a gravacao em vez de deixar a tela
     * dizendo "ativa" enquanto nada e registrado.
     */
    private fun resumeInterruptedTracks() {
        viewModelScope.launch {
            val active = repository.getActiveTracks()
            val current = active.firstOrNull() ?: return@launch
            // Mais de uma trilha ativa e estado invalido: so a mais recente segue,
            // e as outras terminam no proprio ultimo ponto (nao "agora").
            active.drop(1).forEach { stale -> repository.closeTrack(stale.id) }
            if (TrackRecordingService.runningTrackId == current.id) return@launch
            val lastActivity = repository.lastTrackPointTime(current.id) ?: current.startTime
            // Parada ha mais de 12 h (app fechado a forca e so reaberto dias
            // depois): retomar colaria pontos de hoje numa trilha antiga.
            if (System.currentTimeMillis() - lastActivity > RESUME_LIMIT_MILLIS || !hasLocationPermission()) {
                repository.closeTrack(current.id)
                _message.value = "Uma trilha GPS que estava aberta foi encerrada no último ponto gravado."
                return@launch
            }
            startTrackService(current.id)
            _message.value = "Trilha GPS retomada após o app ter sido fechado."
        }
    }

    /**
     * Trilha de arquivo (GPX, KML, GeoJSON, CSV), lida fora da main thread:
     * antes o arquivo inteiro era lido e analisado na thread da tela, e um GPX
     * grande travava o app.
     *
     * Horario so quando o arquivo traz um para cada ponto. Sem isso, os pontos
     * ficam sem horario (0, exportado em branco) na ordem do arquivo, em vez de
     * receber o instante da importacao como se fosse o da coleta.
     */
    fun importTrackFile(session: SessionEntity, uri: Uri) {
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { TrackFileParser.parse(it.readText()) }
                        ?: throw java.io.IOException("não foi possível ler o arquivo")
                }
            }.getOrElse {
                _message.value = "Não foi possível importar a trilha: ${it.message ?: it.javaClass.simpleName}."
                return@launch
            }
            val valid = parsed.filter { validateCoordinates(it.latitude, it.longitude) == null }
            val timed = valid.isNotEmpty() && valid.all { it.timestamp != null }
            val points = if (timed) {
                valid.sortedBy { it.timestamp }.distinctBy { Triple(it.latitude, it.longitude, it.timestamp) }
            } else {
                // Sem horario, so repeticoes seguidas saem: passar duas vezes
                // pelo mesmo ponto e parte da trilha.
                valid.filterIndexed { index, point -> index == 0 || valid[index - 1].latitude != point.latitude || valid[index - 1].longitude != point.longitude }
            }
            if (points.size < 2) {
                _message.value = "Não foi possível importar: a trilha precisa ter pelo menos 2 pontos válidos."
                return@launch
            }
            val start = if (timed) points.first().timestamp!! else 0L
            val end = if (timed) points.last().timestamp!! else null
            val distance = points.zipWithNext().sumOf { (a, b) -> haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude) }
            runCatching {
                (getApplication<OikosApplication>()).database.withTransaction {
                    val trackId = repository.createTrack(
                        GpsTrackEntity(
                            projectId = session.projectId,
                            sessionId = session.id,
                            eventId = session.eventId,
                            startTime = start,
                            endTime = end,
                            totalDistance = distance,
                            duration = if (end != null) (end - start).coerceAtLeast(0L) else 0L,
                            pointsCount = points.size,
                            coordinateFormat = "DD",
                            geodeticDatum = session.geodeticDatum,
                            active = false
                        )
                    )
                    points.forEach { point ->
                        repository.addTrackPoint(
                            GpsTrackPointEntity(
                                trackId = trackId,
                                latitude = point.latitude,
                                longitude = point.longitude,
                                altitude = point.altitude,
                                timestamp = point.timestamp ?: 0L
                            )
                        )
                    }
                }
            }.onSuccess {
                _message.value = buildString {
                    append("Trilha importada com ${points.size} pontos.")
                    if (!timed) append(" O arquivo não traz horário para todos os pontos: a trilha ficou sem horário e sem duração.")
                    if (valid.size < parsed.size) append(" ${parsed.size - valid.size} ponto(s) com coordenada inválida ficaram de fora.")
                }
            }.onFailure {
                _message.value = "Não foi possível importar a trilha: ${it.message ?: it.javaClass.simpleName}."
            }
        }
    }

    private suspend fun stopTrackServiceIf(predicate: (GpsTrackEntity) -> Boolean) {
        val affected = repository.getActiveTracks().filter(predicate)
        if (affected.any { it.id == TrackRecordingService.runningTrackId }) stopTrackService()
    }

    private fun startTrackService(trackId: Long) {
        val context = getApplication<Application>()
        val intent = Intent(context, TrackRecordingService::class.java)
            .setAction(TrackRecordingService.ACTION_START)
            .putExtra(TrackRecordingService.EXTRA_TRACK_ID, trackId)
        runCatching { ContextCompat.startForegroundService(context, intent) }
            .onFailure { _message.value = "Não foi possível iniciar a trilha em segundo plano." }
    }

    private fun stopTrackService() {
        val context = getApplication<Application>()
        runCatching {
            context.startService(Intent(context, TrackRecordingService::class.java).setAction(TrackRecordingService.ACTION_STOP))
        }
    }

    private fun hasLocationPermission(): Boolean {
        val app = getApplication<Application>()
        return ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

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

    // ---------------------------------------------------------------- localizacao

    fun captureLocation(onResult: (CapturedLocation?) -> Unit) {
        viewModelScope.launch {
            val location = locationService.currentLocation(withAddress = reverseGeocodingEnabled)
            if (location == null) _message.value = "Sem posição de GPS agora. Tente em local mais aberto ou digite a coordenada."
            onResult(location)
        }
    }

    /** Posicao continua para o mapa; vazio se nao houver permissao. */
    fun locationUpdates(intervalMillis: Long = 3_000L): Flow<CapturedLocation> =
        if (hasLocationPermission()) locationService.locationUpdates(intervalMillis).catch { } else emptyFlow()

    // ---------------------------------------------------------------- exportacao

    fun exportProject(projectId: Long, format: ExportFormat, content: ExportContent, onReady: (Intent, String?) -> Unit) {
        runExport(onReady) {
            val project = repository.getProject(projectId) ?: return@runExport null
            exporter.export(
                format, project, repository.getSessions(projectId), repository.getOccurrences(projectId),
                repository.getMedia(projectId), repository.getFields(projectId), exportTracks(projectId), content,
                destination = mediaFiles::newExportFile
            )
        }
    }

    fun exportSession(projectId: Long, sessionId: Long, format: ExportFormat, content: ExportContent, onReady: (Intent, String?) -> Unit) {
        runExport(onReady) {
            val project = repository.getProject(projectId) ?: return@runExport null
            val session = repository.getSession(sessionId) ?: return@runExport null
            exporter.export(
                format,
                project,
                listOf(session),
                repository.getOccurrencesForSession(sessionId),
                repository.getMediaForSession(sessionId),
                repository.getFields(projectId),
                exportTracks(projectId, session.id),
                content,
                "expedicao_${OikosExporter.safeName(session.name.ifBlank { session.eventId })}",
                destination = mediaFiles::newExportFile
            )
        }
    }

    /**
     * Etiquetas de herbario/colecao em PDF para os especimes do escopo.
     */
    fun exportLabels(projectId: Long, sessionId: Long?, onReady: (Intent, String?) -> Unit) {
        runExport(onReady) {
            val project = repository.getProject(projectId) ?: return@runExport null
            val sessions = if (sessionId == null) repository.getSessions(projectId) else listOfNotNull(repository.getSession(sessionId))
            val occurrences = if (sessionId == null) repository.getOccurrences(projectId) else repository.getOccurrencesForSession(sessionId)
            val specimens = occurrences.filter { it.basisOfRecord in SpecimenLabels.specimenBases }.sortedBy { it.createdAt }
            if (specimens.isEmpty()) {
                _message.value = "Nenhum espécime neste escopo. As etiquetas saem para registros com natureza do registro PreservedSpecimen, MaterialSample ou LivingSpecimen."
                return@runExport null
            }
            val sessionsById = sessions.associateBy { it.id }
            val labels = specimens.map { SpecimenLabels.content(project, sessionsById[it.sessionId], it) }
            val scopeName = sessionId?.let { id -> sessionsById[id]?.let { it.name.ifBlank { it.eventId } } } ?: project.name
            val file = mediaFiles.newExportFile("etiquetas_${OikosExporter.safeName(scopeName)}.pdf")
            val shortened = SpecimenLabelPdf.write(labels, file)
            val note = if (shortened > 0) " Atenção: em $shortened etiqueta(s) o texto não coube e a descrição foi encurtada (…); confira antes de imprimir." else ""
            ExportResult(file, "application/pdf", "${labels.size} etiqueta(s) em ${(labels.size + 7) / 8} folha(s) A4. Imprima em tamanho real (100%), sem ajustar à página.$note")
        }
    }

    // ------------------------------------------------- troca entre aparelhos

    private val transfer = ProjectTransfer((application as OikosApplication).database, application.filesDir)

    /** Pacote do projeto (ou de uma expedicao) para juntar em outro celular. */
    fun exportProjectPackage(projectId: Long, sessionId: Long?, onReady: (Intent, String?) -> Unit) {
        runExport(onReady) {
            val project = repository.getProject(projectId) ?: return@runExport null
            val scopeName = sessionId?.let { id -> repository.getSession(id)?.let { it.name.ifBlank { it.eventId } } } ?: project.name
            val file = mediaFiles.newExportFile("pacote_${OikosExporter.safeName(scopeName)}_${LocalDate.now()}.zip")
            file.outputStream().use { transfer.export(projectId, sessionId, it, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") }
            ExportResult(file, "application/zip", "Envie o pacote ao outro celular (WhatsApp, e-mail, Drive ou cabo) e, lá, toque em Importar de outro celular na tela inicial.")
        }
    }

    suspend fun inspectProjectPackage(uri: Uri): Result<TransferManifest> = withContext(Dispatchers.IO) {
        runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { transfer.readManifest(it) }
                ?: throw java.io.IOException("não foi possível ler o arquivo")
        }
    }

    private var importing = false

    fun importProjectPackage(uri: Uri, targetProjectId: Long?, onDone: (Long) -> Unit) {
        // Dois toques seguidos importavam o mesmo pacote duas vezes ao mesmo tempo.
        if (importing) return
        importing = true
        viewModelScope.launch {
            _message.value = "Importando o pacote..."
            runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { transfer.import(it, targetProjectId) }
                        ?: throw java.io.IOException("não foi possível ler o arquivo")
                }
            }.onSuccess { result ->
                _message.value = importSummary(result)
                onDone(result.projectId)
            }.onFailure {
                _message.value = "Não foi possível importar: ${it.message ?: it.javaClass.simpleName}. Nada foi alterado."
            }
            importing = false
        }
    }

    private fun importSummary(result: com.oikos.fieldbook.services.TransferSummary): String = buildString {
        append("Importado: ${result.occurrencesAdded} ocorrência(s), ${result.mediaAdded} mídia(s), ")
        append("${result.sessionsAdded} expedição(ões) nova(s)")
        if (result.tracksAdded > 0) append(", ${result.tracksAdded} trilha(s)")
        append(".")
        if (result.sessionsMerged > 0) {
            append(" ${result.sessionsMerged} expedição(ões) já existiam e receberam as novidades")
            if (result.sessionsInOtherProject > 0) append(" (${result.sessionsInOtherProject} delas em outro projeto, onde ficaram)")
            append(".")
        }
        if (result.occurrencesSkipped > 0) {
            append(" ${result.occurrencesSkipped} ocorrência(s) já existiam e não foram duplicadas")
            if (result.occurrencesNewerInPackage > 0) {
                append("; ${result.occurrencesNewerInPackage} delas foram editadas depois no outro celular, mas a versão deste aparelho foi mantida")
            }
            append(".")
        }
        if (result.tracksExtended > 0) append(" ${result.tracksExtended} trilha(s) receberam ${result.trackPointsAdded} ponto(s) que faltavam.")
        if (result.mediaWithoutFile > 0) append(" Atenção: ${result.mediaWithoutFile} mídia(s) ficaram sem o arquivo (faltava no pacote ou não houve espaço para copiá-lo).")
    }

    /** Exportacao fora da main thread, com estado de andamento e sem execucao dupla. */
    private fun runExport(onReady: (Intent, String?) -> Unit, block: suspend () -> com.oikos.fieldbook.export.ExportResult?) {
        if (_exporting.value) return
        _exporting.value = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { runCatching { block() } }
                result.onSuccess { exported ->
                    if (exported != null) onReady(shareIntent(exported.file, exported.mimeType), exported.warning)
                }.onFailure {
                    _message.value = "A exportação falhou: ${it.message ?: it.javaClass.simpleName}. Libere espaço e tente de novo."
                }
            } finally {
                _exporting.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun showMessage(message: String) {
        _message.value = message
    }

    private fun shareIntent(file: File, mimeType: String): Intent {
        val uri = mediaFiles.uriFor(file)
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

    /**
     * Trilhas do projeto (ou so de uma expedicao), com o eventID ATUAL da
     * expedicao. Antes o filtro e o eventID usavam a copia gravada na trilha ao
     * cria-la: editar o eventID da expedicao tirava a trilha da exportacao.
     */
    private suspend fun exportTracks(projectId: Long, sessionId: Long? = null): List<TrackExportBundle> {
        val eventIds = repository.getSessions(projectId).associate { it.id to it.eventId }
        return repository.getTracks(projectId).filter { sessionId == null || it.sessionId == sessionId }.map { track ->
            TrackExportBundle(
                trackID = "TRK-${track.id.toString().padStart(6, '0')}",
                projectID = "PROJ-${track.projectId.toString().padStart(3, '0')}",
                eventID = eventIds[track.sessionId] ?: track.eventId,
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
    }

    /**
     * Data de gravacao guardada no arquivo ("20261017T083000.000Z"), em UTC.
     * Sem fuso declarado, ou com a data zero do formato MP4 (1904), nao vale.
     */
    private fun embeddedAudioDate(file: File): Long? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE).orEmpty().trim()
            val match = Regex("^(\\d{8})T(\\d{6})(?:\\.\\d+)?Z$").find(raw) ?: return@runCatching null
            val instant = java.time.LocalDateTime.parse(
                match.groupValues[1] + match.groupValues[2],
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            ).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            instant.takeIf { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).year >= 1990 && it <= System.currentTimeMillis() + 86_400_000L }
        } finally {
            retriever.release()
        }
    }.getOrNull()

    /** WAV pelo cabecalho; outros formatos pela duracao que o Android informa. */
    private fun importedAudioInfo(file: File): AudioInfo? {
        val wav = AudioInfoReader.readWav(file)
        if (wav?.durationMillis != null) return wav
        // WAV com o bloco de dados depois dos primeiros 64 KB (metadados longos
        // de gravador) vinha sem duracao: completa pelo leitor do Android.
        val other = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                AudioInfo(
                    sampleRateHz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
                    } else null,
                    bitsPerSample = null,
                    channels = null,
                    durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                )
            } finally {
                retriever.release()
            }
        }.getOrNull()
        if (wav == null) return other
        return wav.copy(durationMillis = other?.durationMillis, sampleRateHz = wav.sampleRateHz ?: other?.sampleRateHz)
    }

    private fun deviceName(): String =
        listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.distinct().joinToString(" ")

    private fun mediaEntity(
        target: MediaTarget,
        type: String,
        file: File,
        mimeType: String,
        subject: String,
        description: String,
        metadata: PhotoMetadata?
    ): MediaEntity = MediaEntity(
        projectId = target.projectId,
        sessionId = target.sessionId,
        occurrenceId = target.occurrenceId,
        mediaType = type,
        filePath = file.absolutePath,
        fileName = file.name,
        mimeType = mimeType,
        createdAt = metadata?.capturedAt ?: System.currentTimeMillis(),
        latitude = metadata?.latitude,
        longitude = metadata?.longitude,
        caption = description,
        mediaSubject = subject,
        notes = description,
        captureDevice = metadata?.captureDevice.orEmpty()
    )

    companion object {
        const val SETTINGS = "oikos_app_settings"
        /** Espaco minimo para comecar a gravar: cerca de 5 min de WAV, mais folga para o banco. */
        private const val MIN_RECORDING_FREE_BYTES = 40L * 1024 * 1024
        /** Uma foto em qualidade alta (ate ~10 MB antes da reducao), mais folga para o banco. */
        private const val MIN_MEDIA_FREE_BYTES = 25L * 1024 * 1024
        private const val KEY_REVERSE_GEOCODING = "reverse_geocoding"
        private const val KEY_LAST_BACKUP = "last_backup_at"
        private const val KEY_LAST_MICROPHONE = "last_microphone"
        private const val KEY_REPEAT_FIELDS = "repeat_fields:"
        private const val KEY_OFFLINE_MAP = "offline_map"
        private const val RESUME_LIMIT_MILLIS = 12 * 60 * 60 * 1000L

        fun newEventId(): String = "event-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}-${UUID.randomUUID().toString().take(8)}"
    }
}

