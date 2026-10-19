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
import com.oikos.fieldbook.util.UiLanguage
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
    // Mensagens no idioma da tela (Configuracoes > Idioma).
    private fun t(pt: String, en: String, es: String) = UiLanguage.t(pt, en, es)
    private fun qty(n: Number, ptOne: String, ptMany: String, enOne: String, enMany: String, esOne: String, esMany: String) = UiLanguage.count(n, ptOne, ptMany, enOne, enMany, esOne, esMany)
    private fun why(error: Throwable) = error.message ?: error.javaClass.simpleName

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
            RestoreOutcome.Restored -> _message.value = t("Backup restaurado. Confira projetos, expedições e mídias.", "Backup restored. Check projects, expeditions and media.", "Copia de seguridad restaurada. Revisa proyectos, salidas de campo y archivos multimedia.")
            is RestoreOutcome.Failed -> _message.value = t("A restauração do backup não foi concluída: ${outcome.reason}", "The backup restore did not finish: ${outcome.reason}", "La restauración de la copia de seguridad no terminó: ${outcome.reason}")
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

    val checklists: Flow<List<ChecklistEntity>> = (application as OikosApplication).checklistDatabase.checklistDao().observeChecklists()

    /** Andamento por fonte: "flora_brasil" -> ("Baixando 12 MB", 0.6). */
    private val _checklistProgress = MutableStateFlow<Map<String, BackupProgress>>(emptyMap())
    val checklistProgress: StateFlow<Map<String, BackupProgress>> = _checklistProgress

    /** Versao mais nova publicada por fonte, quando conferida com internet. */
    private val _latestVersions = MutableStateFlow<Map<String, RemoteVersion>>(emptyMap())
    val latestVersions: StateFlow<Map<String, RemoteVersion>> = _latestVersions

    init {
        // Aqui, depois de checklistService e do andamento existirem: o init do topo roda antes.
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { checklistService.removeInterruptedImports() } }
            // As listas oficiais vem no app: na primeira abertura (ou se a instalada
            // e anterior a v3), sao instaladas em segundo plano, sem internet. A que
            // o pesquisador removeu fica de fora ate ele pedir de novo.
            ChecklistSources.all.forEach { source ->
                val removed = settings.getBoolean(KEY_LIST_REMOVED + source.key, false)
                if (!removed && runCatching { checklistService.needsEmbeddedInstall(source) }.getOrDefault(false)) {
                    installEmbeddedChecklist(source, announce = false).join()
                }
            }
        }
    }

    /** Instala a lista que vem no app (sem internet). [announce]: avisa ao terminar. */
    fun installEmbeddedChecklist(source: ChecklistSource, announce: Boolean = true): kotlinx.coroutines.Job {
        if (_checklistProgress.value.containsKey(source.key)) return kotlinx.coroutines.Job().apply { complete() }
        settings.edit().remove(KEY_LIST_REMOVED + source.key).apply()
        _checklistProgress.value = _checklistProgress.value + (source.key to BackupProgress(t("Preparando a lista...", "Preparing the list...", "Preparando la lista..."), null))
        return viewModelScope.launch {
            runCatching {
                checklistService.installEmbedded(source) { stage, fraction ->
                    _checklistProgress.value = _checklistProgress.value + (source.key to BackupProgress(stage, fraction))
                }
            }.onSuccess {
                if (announce) _message.value = t("${it.title}: ${it.taxonCount} nomes prontos, sem internet.", "${it.title}: ${it.taxonCount} names ready, no internet needed.", "${it.title}: ${it.taxonCount} nombres listos, sin necesidad de internet.")
            }.onFailure {
                _message.value = t("Não foi possível instalar ${source.title}: ${why(it)}.", "Could not install ${source.title}: ${why(it)}.", "No se pudo instalar ${source.title}: ${why(it)}.")
            }
            _checklistProgress.value = _checklistProgress.value - source.key
        }
    }

    fun downloadChecklist(source: ChecklistSource) {
        if (_checklistProgress.value.containsKey(source.key)) return
        settings.edit().remove(KEY_LIST_REMOVED + source.key).apply()
        _checklistProgress.value = _checklistProgress.value + (source.key to BackupProgress(t("Conectando...", "Connecting...", "Conectando..."), null))
        viewModelScope.launch {
            runCatching {
                checklistService.download(source) { stage, fraction ->
                    _checklistProgress.value = _checklistProgress.value + (source.key to BackupProgress(stage, fraction))
                }
            }.onSuccess {
                _latestVersions.value = _latestVersions.value + (source.key to RemoteVersion(it.version, it.pubDate))
                _message.value = t("${it.title}: ${it.taxonCount} nomes instalados (versão ${it.version}).", "${it.title}: ${it.taxonCount} names installed (version ${it.version}).", "${it.title}: ${it.taxonCount} nombres instalados (versión ${it.version}).")
            }.onFailure {
                _message.value = t("Não foi possível baixar ${source.title}: ${why(it)}.", "Could not download ${source.title}: ${why(it)}.", "No se pudo descargar ${source.title}: ${why(it)}.")
            }
            _checklistProgress.value = _checklistProgress.value - source.key
        }
    }

    fun importChecklistFile(uri: Uri) {
        val key = "arquivo"
        if (_checklistProgress.value.containsKey(key)) return
        _checklistProgress.value = _checklistProgress.value + (key to BackupProgress(t("Lendo o arquivo...", "Reading the file...", "Leyendo el archivo..."), null))
        viewModelScope.launch {
            runCatching {
                checklistService.importFile(uri) { stage, fraction ->
                    _checklistProgress.value = _checklistProgress.value + (key to BackupProgress(stage, fraction))
                }
            }.onSuccess { _message.value = t("${it.title}: ${it.taxonCount} nomes instalados.", "${it.title}: ${it.taxonCount} names installed.", "${it.title}: ${it.taxonCount} nombres instalados.") }
                .onFailure { _message.value = t("Não foi possível importar a lista: ${why(it)}.", "Could not import the list: ${why(it)}.", "No se pudo importar la lista: ${why(it)}.") }
            _checklistProgress.value = _checklistProgress.value - key
        }
    }

    fun removeChecklist(checklist: ChecklistEntity) {
        viewModelScope.launch {
            // Lista oficial removida pelo pesquisador nao volta sozinha na proxima abertura.
            if (ChecklistSources.byKey(checklist.sourceKey) != null) settings.edit().putBoolean(KEY_LIST_REMOVED + checklist.sourceKey, true).apply()
            checklistService.remove(checklist)
            _message.value = t("Lista removida: ${checklist.title}.", "List removed: ${checklist.title}.", "Lista eliminada: ${checklist.title}.")
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

    /** [groups]: grupos escolhidos no projeto ([com.oikos.fieldbook.checklist.NameGroup]); vazio = todos. */
    suspend fun searchNames(query: String, groups: Set<String> = emptySet()): List<TaxonSuggestion> =
        runCatching { checklistService.search(query, groups) }.getOrDefault(emptyList())

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
        _backupProgress.value = BackupProgress(t("Criando backup...", "Creating backup...", "Creando copia de seguridad..."), 0f)
        viewModelScope.launch {
            runCatching {
                backupService.create(target) { done, total ->
                    _backupProgress.value = BackupProgress(t("Criando backup...", "Creating backup...", "Creando copia de seguridad..."), if (total > 0) done.toFloat() / total else null)
                }
            }.onSuccess { manifest ->
                val now = System.currentTimeMillis()
                settings.edit().putLong(KEY_LAST_BACKUP, now).apply()
                _lastBackupAt.value = now
                _message.value = t("Backup salvo: ", "Backup saved: ", "Copia de seguridad guardada:") + listOf(
                    qty(manifest.projects, "projeto", "projetos", "project", "projects", "proyecto", "proyectos"),
                    qty(manifest.occurrences, "ocorrência", "ocorrências", "occurrence", "occurrences", "registro", "registros"),
                    qty(manifest.mediaFiles, "mídia", "mídias", "media file", "media files", "archivo multimedia", "archivos multimedia")
                ).joinToString(", ") + "."
            }.onFailure {
                _message.value = t("O backup falhou: ${why(it)}. Confira o espaço no destino.", "The backup failed: ${why(it)}. Check the free space at the destination.", "La copia de seguridad falló: ${why(it)}. Revisa el espacio libre en el destino.")
            }
            _backupProgress.value = null
        }
    }

    fun inspectBackup(source: Uri, onResult: (BackupManifest?) -> Unit) {
        viewModelScope.launch {
            val manifest = backupService.inspect(source)
            if (manifest == null) _message.value = t("Este arquivo não é um backup do Oikos FieldBook.", "This file is not an Oikos FieldBook backup.", "Este archivo no es una copia de seguridad de Oikos FieldBook.")
            onResult(manifest)
        }
    }

    /**
     * Prepara a restauracao e pede o reinicio do app, que conclui a troca antes
     * de abrir o banco. Trilha e gravacao em andamento sao encerradas antes.
     */
    fun restoreBackup(source: Uri, onReadyToRestart: () -> Unit) {
        if (_backupProgress.value != null) return
        _backupProgress.value = BackupProgress(t("Preparando a restauração...", "Preparing the restore...", "Preparando la restauración..."), null)
        viewModelScope.launch {
            stopRecording()
            stopTrackService()
            runCatching { backupService.stageRestore(source) }
                .onSuccess {
                    backupService.checkpointBeforeRestart()
                    onReadyToRestart()
                }
                .onFailure {
                    _message.value = t("Não foi possível restaurar: ${why(it)}. Seus dados atuais não foram alterados.", "Could not restore: ${why(it)}. Your current data was not changed.", "No se pudo restaurar: ${why(it)}. Tus datos actuales no se modificaron.")
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
        is SQLiteFullException -> t("Sem espaço no aparelho: nada foi salvo. Libere espaço e toque em Salvar de novo; o que você digitou continua na tela.", "No space left on the device: nothing was saved. Free up space and tap Save again; what you typed is still on screen.", "No queda espacio en el dispositivo: no se guardó nada. Libera espacio y toca Guardar de nuevo; lo que escribiste sigue en la pantalla.")
        else -> t("Nada foi salvo: ${why(error)}. O que você digitou continua na tela.", "Nothing was saved: ${why(error)}. What you typed is still on screen.", "No se guardó nada: ${why(error)}. Lo que escribiste sigue en la pantalla.")
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
                _message.value = saveFailureMessage(error, t("Já existe um projeto igual.", "An identical project already exists.", "Ya existe un proyecto idéntico."))
                onFailed()
            }
        }
    }

    fun deleteProject(project: ProjectEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            stopTrackServiceIf { it.projectId == project.id }
            finishRecordingFor { it.projectId == project.id }
            moveToTrash(t("Projeto", "Project", "Proyecto"), onDone) { trash.trashProject(project.id, project.name) }
        }
    }

    fun updateField(field: FieldConfigEntity) {
        viewModelScope.launch { repository.updateField(field) }
    }

    fun saveFieldConfiguration(projectId: Long, fields: List<FieldConfigEntity>) {
        viewModelScope.launch {
            try {
                repository.replaceFieldConfig(projectId, fields)
                _message.value = t("Configuração de campos salva.", "Field settings saved.", "Configuración de campos guardada.")
            } catch (error: SQLiteException) {
                _message.value = t("A configuração de campos não foi salva: ${why(error)}.", "Field settings were not saved: ${why(error)}.", "La configuración de campos no se guardó: ${why(error)}.")
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
        if (session.eventDate.isBlank()) _message.value = t("Aviso: expedição salva sem data de coleta.", "Warning: expedition saved without a collection date.", "Aviso: salida de campo guardada sin fecha de colecta.")
        viewModelScope.launch {
            try {
                val id = if (session.id == 0L) repository.createSession(session) else {
                    repository.updateSession(session)
                    session.id
                }
                onSaved(id)
            } catch (error: SQLiteException) {
                _message.value = saveFailureMessage(error, t("eventID já existe. Use um identificador único.", "eventID already exists. Use a unique identifier.", "El eventID ya existe. Usa un identificador único."))
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
            moveToTrash(t("Expedição", "Expedition", "Salida de campo"), onDone) { trash.trashSession(session.projectId, session.id, session.name.ifBlank { session.eventId }) }
        }
    }

    // ---------------------------------------------------------------- lixeira

    /** Apaga indo para a lixeira; [action] roda em IO e devolve o item criado. */
    private suspend fun moveToTrash(what: String, onDone: () -> Unit, action: () -> TrashItem) {
        runCatching { withContext(Dispatchers.IO) { action() } }
            .onSuccess {
                _message.value = t("$what na lixeira por ${TrashService.RETENTION_DAYS} dias (Configurações > Lixeira).", "$what moved to the trash for ${TrashService.RETENTION_DAYS} days (Settings > Trash).", "$what en la papelera por ${TrashService.RETENTION_DAYS} días (Configuración > Papelera).")
                refreshTrash()
                onDone()
            }
            .onFailure { _message.value = t("Não foi possível apagar: ${why(it)}.", "Could not delete: ${why(it)}.", "No se pudo eliminar: ${why(it)}.") }
    }

    fun refreshTrash() {
        viewModelScope.launch { _trashItems.value = withContext(Dispatchers.IO) { trash.list() } }
    }

    fun restoreFromTrash(item: TrashItem) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { trash.restore(item) } }
                .onSuccess { failed ->
                    _message.value = when {
                        failed == 0 -> t("Restaurado: ${item.title}.", "Restored: ${item.title}.", "Restaurado: ${item.title}.")
                        failed == 1 -> t("Restaurado: ${item.title}, mas 1 arquivo não pôde voltar (falta de espaço?).", "Restored: ${item.title}, but 1 file could not be brought back (out of space?).", "Restaurado: ${item.title}, pero 1 archivo no se pudo recuperar (¿falta de espacio?).")
                        else -> t("Restaurado: ${item.title}, mas $failed arquivos não puderam voltar (falta de espaço?).", "Restored: ${item.title}, but $failed files could not be brought back (out of space?).", "Restaurado: ${item.title}, pero $failed archivos no se pudieron recuperar (¿falta de espacio?).")
                    }
                }
                .onFailure { _message.value = t("Não foi possível restaurar: ${why(it)}.", "Could not restore: ${why(it)}.", "No se pudo restaurar: ${why(it)}.") }
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
            _message.value = t("Lixeira esvaziada.", "Trash emptied.", "Papelera vaciada.")
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
                    occurrence.latitude == null || occurrence.longitude == null -> _message.value = t("Aviso: registro salvo sem coordenadas.", "Warning: record saved without coordinates.", "Aviso: registro guardado sin coordenadas.")
                    occurrence.scientificName.isBlank() -> _message.value = t("Aviso: registro salvo sem o nome da espécie.", "Warning: record saved without the species name.", "Aviso: registro guardado sin el nombre de la especie.")
                }
                onSaved(id)
            } catch (error: SQLiteException) {
                _message.value = saveFailureMessage(error, t("occurrenceID já existe. Use um identificador único.", "occurrenceID already exists. Use a unique identifier.", "El occurrenceID ya existe. Usa un identificador único."))
                onFailed()
            }
        }
    }

    fun deleteOccurrence(occurrence: OccurrenceEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            finishRecordingFor { it.occurrenceId == occurrence.id }
            // Antes as linhas das midias ficavam no banco, apontando para
            // arquivos apagados; agora saem junto com a ocorrencia.
            moveToTrash(t("Ocorrência", "Occurrence", "Registro"), onDone) { trash.trashOccurrence(occurrence.id, occurrence.scientificName.ifBlank { occurrence.occurrenceId }) }
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
            _message.value = t("Mídia atualizada.", "Media updated.", "Archivo multimedia actualizado.")
        }
    }

    fun deleteMedia(media: MediaEntity) {
        viewModelScope.launch {
            moveToTrash(t("Mídia", "Media file", "Archivo multimedia"), {}) { trash.trashMedia(media.id, media.fileName) }
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
                val reason = if (error is SQLiteFullException) t("sem espaço no aparelho", "no space left on the device", "no queda espacio en el dispositivo") else why(error)
                _message.value = t("A mídia ${media.fileName} não pôde ser registrada ($reason). Libere espaço e registre de novo.", "The media file ${media.fileName} could not be recorded ($reason). Free up space and record it again.", "El archivo multimedia ${media.fileName} no se pudo registrar ($reason). Libera espacio y regístralo de nuevo.")
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
        _message.value = t("Pouco espaço livre (${free / (1024 * 1024)} MB). Libere espaço no aparelho antes de fotografar ou importar.", "Low free space (${free / (1024 * 1024)} MB). Free up space before taking photos or importing.", "Poco espacio libre (${free / (1024 * 1024)} MB). Libera espacio en el dispositivo antes de fotografiar o importar.")
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
                    _message.value = t("A câmera não entregou a foto. Tente de novo.", "The camera did not return the photo. Try again.", "La cámara no entregó la foto. Inténtalo de nuevo.")
                    file.delete()
                    return@launch
                }
                val (metadata, compressError) = withContext(Dispatchers.IO) {
                    val meta = mediaFiles.readPhotoMetadata(file)
                    meta to runCatching { mediaFiles.compressImage(file, quality) }.exceptionOrNull()
                }
                if (compressError != null) _message.value = t("Foto guardada sem reduzir o tamanho (${why(compressError)}).", "Photo kept without reducing its size (${why(compressError)}).", "Foto guardada sin reducir su tamaño (${why(compressError)}).")
                onMetadata(metadata)
                deliver(target, mediaEntity(target, "photo", file, "image/jpeg", subject, description, metadata))
            } catch (error: Exception) {
                _message.value = t("Não foi possível registrar a foto: ${why(error)}.", "Could not record the photo: ${why(error)}.", "No se pudo registrar la foto: ${why(error)}.")
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
                if (compressError != null) _message.value = t("Foto guardada sem reduzir o tamanho (${why(compressError)}).", "Photo kept without reducing its size (${why(compressError)}).", "Foto guardada sin reducir su tamaño (${why(compressError)}).")
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
                _message.value = t("Não foi possível importar o arquivo: ${it.message ?: "erro de leitura"}.", "Could not import the file: ${it.message ?: "read error"}.", "No se pudo importar el archivo: ${it.message ?: "error de lectura"}.")
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
        _offlineMapProgress.value = BackupProgress(t("Copiando o mapa...", "Copying the map...", "Copiando el mapa..."), null)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    offlineMapService.import(uri) { copied, total ->
                        _offlineMapProgress.value = BackupProgress(
                            t("Copiando o mapa: ${copied / (1024 * 1024)} MB", "Copying the map: ${copied / (1024 * 1024)} MB", "Copiando el mapa: ${copied / (1024 * 1024)} MB"),
                            if (total > 0) copied.toFloat() / total else null
                        )
                    }
                }
            }.onSuccess { map ->
                setActiveOfflineMap(map)
                _message.value = t("Mapa instalado: ${map.name} (zoom ${map.minZoom} a ${map.maxZoom}).", "Map installed: ${map.name} (zoom ${map.minZoom} to ${map.maxZoom}).", "Mapa instalado: ${map.name} (zoom ${map.minZoom} a ${map.maxZoom}).")
            }.onFailure {
                _message.value = t("Não foi possível instalar o mapa: ${why(it)}.", "Could not install the map: ${why(it)}.", "No se pudo instalar el mapa: ${why(it)}.")
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
            _message.value = t("Mapa removido: ${map.name}.", "Map removed: ${map.name}.", "Mapa eliminado: ${map.name}.")
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
                _message.value = t("Contagem salva: ", "Count saved: ", "Conteo guardado:") +
                    qty(occurrences.size, "registro", "registros", "record", "records", "registro", "registros") + ", " +
                    qty(individuals, "indivíduo", "indivíduos", "individual", "individuals", "individuo", "individuos") + "."
                onSaved()
            } catch (error: SQLiteException) {
                _message.value = saveFailureMessage(error, t("occurrenceID já existe. Use um identificador único.", "occurrenceID already exists. Use a unique identifier.", "El occurrenceID ya existe. Usa un identificador único."))
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
            _message.value = t("Já existe uma gravação em andamento.", "A recording is already in progress.", "Ya hay una grabación en curso.")
            return
        }
        // Gravacao anterior ainda sendo fechada: comecar outra agora disputaria
        // o microfone com ela.
        if (finishingRecordings.get() > 0) {
            _message.value = t("Aguarde: a gravação anterior ainda está sendo salva.", "Please wait: the previous recording is still being saved.", "Espera: la grabación anterior todavía se está guardando.")
            return
        }
        val app = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _message.value = t("Permissão de áudio negada.", "Audio permission denied.", "Permiso de audio denegado.")
            return
        }
        val free = allocatableBytes(app, app.filesDir)
        if (free < MIN_RECORDING_FREE_BYTES) {
            _message.value = t("Pouco espaço livre (${free / (1024 * 1024)} MB). Libere espaço antes de gravar: o WAV ocupa cerca de 5 MB por minuto.", "Low free space (${free / (1024 * 1024)} MB). Free up space before recording: WAV takes about 5 MB per minute.", "Poco espacio libre (${free / (1024 * 1024)} MB). Libera espacio antes de grabar: el WAV ocupa unos 5 MB por minuto.")
            return
        }
        val file = mediaFiles.createAudioFile(target.projectId, target.sessionId, baseName)
        val sources = preferredAudioSources()
        val started = runCatching {
            wavRecorder.start(file, sources)
        }
        started.onFailure {
            file.delete()
            _message.value = it.message ?: t("Não foi possível iniciar a gravação de áudio.", "Could not start the audio recording.", "No se pudo iniciar la grabación de audio.")
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
                _message.value = t("A gravação não produziu áudio", "The recording produced no audio", "La grabación no produjo audio") + (error?.message?.let { ": $it" } ?: ".")
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
            _message.value = if (error != null) t("Áudio salvo, mas a gravação foi interrompida: ${why(error)}.", "Audio saved, but the recording was interrupted: ${why(error)}.", "Audio guardado, pero la grabación se interrumpió: ${why(error)}.") else reason ?: t("Áudio salvo.", "Audio saved.", "Audio guardado.")
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
        // Nem fica anunciando ou procurando celulares depois que o app fecha.
        nearby.close()
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
            _message.value = if (recorded != null) t("Ponto da trilha salvo.", "Track point saved.", "Punto del recorrido guardado.") else t("Ponto ignorado: precisão baixa ou deslocamento insuficiente.", "Point skipped: low accuracy or too little movement.", "Punto descartado: precisión baja o desplazamiento insuficiente.")
        }
        return recorded != null
    }

    fun startTrack(session: SessionEntity, onStarted: (Long) -> Unit = {}) {
        if (!hasLocationPermission()) {
            _message.value = t("Permita o acesso à localização para gravar a trilha.", "Allow location access to record the track.", "Permite el acceso a la ubicación para grabar el recorrido.")
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
            _message.value = t("Trilha GPS iniciada. A linha será atualizada em tempo real.", "GPS track started. The line will update in real time.", "Recorrido GPS iniciado. La línea se actualiza en tiempo real.")
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
            _message.value = t("Trilha GPS encerrada.", "GPS track stopped.", "Recorrido GPS finalizado.")
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
                _message.value = t("Uma trilha GPS que estava aberta foi encerrada no último ponto gravado.", "A GPS track that was left open was closed at its last recorded point.", "Un recorrido GPS que había quedado abierto se cerró en su último punto grabado.")
                return@launch
            }
            startTrackService(current.id)
            _message.value = t("Trilha GPS retomada após o app ter sido fechado.", "GPS track resumed after the app was closed.", "Recorrido GPS reanudado después de que se cerró la app.")
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
                        ?: throw java.io.IOException(t("não foi possível ler o arquivo", "could not read the file", "no se pudo leer el archivo"))
                }
            }.getOrElse {
                _message.value = t("Não foi possível importar a trilha: ${why(it)}.", "Could not import the track: ${why(it)}.", "No se pudo importar el recorrido: ${why(it)}.")
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
                _message.value = t("Não foi possível importar: a trilha precisa ter pelo menos 2 pontos válidos.", "Could not import: the track needs at least 2 valid points.", "No se pudo importar: el recorrido necesita al menos 2 puntos válidos.")
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
                    append(t("Trilha importada com ${points.size} pontos.", "Track imported with ${points.size} points.", "Recorrido importado con ${points.size} puntos."))
                    if (!timed) append(t(" O arquivo não traz horário para todos os pontos: a trilha ficou sem horário e sem duração.", " The file does not have a time for every point: the track was left without times and duration.", " El archivo no trae la hora de todos los puntos: el recorrido quedó sin horas y sin duración."))
                    val skipped = parsed.size - valid.size
                    if (skipped == 1) append(t(" 1 ponto com coordenada inválida ficou de fora.", " 1 point with an invalid coordinate was left out.", " 1 punto con coordenada no válida quedó fuera."))
                    if (skipped > 1) append(t(" $skipped pontos com coordenada inválida ficaram de fora.", " $skipped points with invalid coordinates were left out.", " $skipped puntos con coordenadas no válidas quedaron fuera."))
                }
            }.onFailure {
                _message.value = t("Não foi possível importar a trilha: ${why(it)}.", "Could not import the track: ${why(it)}.", "No se pudo importar el recorrido: ${why(it)}.")
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
            .onFailure { _message.value = t("Não foi possível iniciar a trilha em segundo plano.", "Could not start the track in the background.", "No se pudo iniciar el recorrido en segundo plano.") }
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
            if (location == null) _message.value = t("Sem posição de GPS agora. Tente em local mais aberto ou digite a coordenada.", "No GPS position right now. Try a more open spot or type the coordinate.", "No hay posición de GPS ahora. Prueba en un lugar más abierto o escribe la coordenada.")
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
                com.oikos.fieldbook.util.inProjectLanguage(project.language, "expedicao_", "expedition_", "salida_") +
                    OikosExporter.safeName(session.name.ifBlank { session.eventId }),
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
                _message.value = t("Nenhum espécime neste escopo. As etiquetas saem para registros com natureza do registro PreservedSpecimen, MaterialSample ou LivingSpecimen.", "No specimens in this scope. Labels are made for records whose basis of record is PreservedSpecimen, MaterialSample or LivingSpecimen.", "No hay especímenes en este alcance. Las etiquetas se generan para registros cuya base del registro es PreservedSpecimen, MaterialSample o LivingSpecimen.")
                return@runExport null
            }
            val sessionsById = sessions.associateBy { it.id }
            val labels = specimens.map { SpecimenLabels.content(project, sessionsById[it.sessionId], it) }
            val scopeName = sessionId?.let { id -> sessionsById[id]?.let { it.name.ifBlank { it.eventId } } } ?: project.name
            val file = mediaFiles.newExportFile(com.oikos.fieldbook.util.inProjectLanguage(project.language, "etiquetas_", "labels_", "etiquetas_") + "${OikosExporter.safeName(scopeName)}.pdf")
            val shortened = SpecimenLabelPdf.write(labels, file)
            val note = when {
                shortened == 1 -> t(" Atenção: em 1 etiqueta o texto não coube e a descrição foi encurtada (…); confira antes de imprimir.", " Note: on 1 label the text did not fit and the description was shortened (…); check it before printing.", " Atención: en 1 etiqueta el texto no cupo y la descripción se acortó (…); revísala antes de imprimir.")
                shortened > 1 -> t(" Atenção: em $shortened etiquetas o texto não coube e a descrição foi encurtada (…); confira antes de imprimir.", " Note: on $shortened labels the text did not fit and the description was shortened (…); check them before printing.", " Atención: en $shortened etiquetas el texto no cupo y la descripción se acortó (…); revísalas antes de imprimir.")
                else -> ""
            }
            val sheets = (labels.size + 7) / 8
            ExportResult(
                file,
                "application/pdf",
                qty(labels.size, "etiqueta", "etiquetas", "label", "labels", "etiqueta", "etiquetas") + t(" em ", " on ", " en") +
                    qty(sheets, "folha A4", "folhas A4", "A4 sheet", "A4 sheets", "hoja A4", "hojas A4") +
                    t(". Imprima em tamanho real (100%), sem ajustar à página.", ". Print at actual size (100%), without fitting to the page.", ". Imprime en tamaño real (100%), sin ajustar a la página.") + note
            )
        }
    }

    // ------------------------------------------------- troca entre aparelhos

    private val transfer = ProjectTransfer((application as OikosApplication).database, application.filesDir)

    /** Pacote do projeto (ou de uma expedicao) para juntar em outro celular. */
    fun exportProjectPackage(projectId: Long, sessionId: Long?, onReady: (Intent, String?) -> Unit) {
        runExport(onReady) {
            val project = repository.getProject(projectId) ?: return@runExport null
            val scopeName = sessionId?.let { id -> repository.getSession(id)?.let { it.name.ifBlank { it.eventId } } } ?: project.name
            val file = mediaFiles.newExportFile(com.oikos.fieldbook.util.inProjectLanguage(project.language, "pacote_", "package_", "paquete_") + "${OikosExporter.safeName(scopeName)}_${LocalDate.now()}.zip")
            file.outputStream().use { transfer.export(projectId, sessionId, it, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") }
            ExportResult(file, "application/zip", t("Envie o pacote ao outro celular (WhatsApp, e-mail, Drive ou cabo) e, lá, toque em Importar na tela inicial e escolha Importar de um arquivo.", "Send the package to the other phone (WhatsApp, email, Drive or cable) and, there, tap Import on the home screen and choose Import from a file.", "Envía el paquete al otro celular (WhatsApp, correo, Drive o cable) y, allí, toca Importar en la pantalla de inicio y elige Importar de un archivo."))
        }
    }

    // ------------------------------------------- envio direto para celular proximo

    val nearby = com.oikos.fieldbook.services.NearbyTransfer(application)

    /** Monta o pacote e abre a busca por celulares com o "Receber" aberto. */
    fun startNearbySend(projectId: Long, sessionId: Long?) {
        viewModelScope.launch {
            val prepared = runCatching {
                withContext(Dispatchers.IO) {
                    val project = repository.getProject(projectId) ?: throw java.io.IOException(t("projeto não encontrado", "project not found", "proyecto no encontrado"))
                    val sessions = if (sessionId == null) repository.getSessions(projectId) else listOfNotNull(repository.getSession(sessionId))
                    val occurrences = if (sessionId == null) repository.getOccurrences(projectId) else repository.getOccurrencesForSession(sessionId)
                    val folder = File(getApplication<Application>().cacheDir, "envios").apply { mkdirs() }
                    folder.listFiles().orEmpty().forEach { it.delete() }
                    val file = File(folder, "pacote-${System.currentTimeMillis()}.zip")
                    file.outputStream().use { transfer.export(projectId, sessionId, it, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") }
                    val sender = ProjectTransfer.senderName(sessions.map { it.recordedBy }, occurrences.map { it.recordedBy })
                    Triple(file, sender, project.name)
                }
            }
            prepared.onSuccess { (file, sender, projectName) -> nearby.startSending(file, sender, projectName) }
                .onFailure { _message.value = t("Não foi possível montar o pacote: ${why(it)}", "Could not build the package: ${why(it)}", "No se pudo armar el paquete: ${why(it)}") }
        }
    }

    suspend fun inspectProjectPackage(uri: Uri): Result<TransferManifest> = withContext(Dispatchers.IO) {
        runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { transfer.readManifest(it) }
                ?: throw java.io.IOException(t("não foi possível ler o arquivo", "could not read the file", "no se pudo leer el archivo"))
        }
    }

    private var importing = false

    /** [takeIncoming]: chaves dos registros em conflito em que fica a versao que chegou. */
    fun importProjectPackage(uri: Uri, targetProjectId: Long?, takeIncoming: Set<String>, cleanup: File? = null, onDone: (Long) -> Unit) {
        // Dois toques seguidos importavam o mesmo pacote duas vezes ao mesmo tempo.
        if (importing) return
        importing = true
        viewModelScope.launch {
            _message.value = t("Importando o pacote...", "Importing the package...", "Importando el paquete...")
            runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { transfer.import(it, targetProjectId, takeIncoming) }
                        ?: throw java.io.IOException(t("não foi possível ler o arquivo", "could not read the file", "no se pudo leer el archivo"))
                }
            }.onSuccess { result ->
                _message.value = importSummary(result)
                onDone(result.projectId)
            }.onFailure {
                _message.value = t("Não foi possível importar: ${why(it)}. Nada foi alterado.", "Could not import: ${why(it)}. Nothing was changed.", "No se pudo importar: ${why(it)}. No se modificó nada.")
            }
            // Pacote recebido de celular proximo fica no cache so ate ser importado.
            cleanup?.delete()
            importing = false
        }
    }

    private fun importSummary(result: com.oikos.fieldbook.services.TransferSummary): String = buildString {
        append(t("Importado: ", "Imported: ", "Importado:"))
        append(qty(result.occurrencesAdded, "ocorrência", "ocorrências", "occurrence", "occurrences", "registro", "registros")).append(", ")
        append(qty(result.mediaAdded, "mídia", "mídias", "media file", "media files", "archivo multimedia", "archivos multimedia")).append(", ")
        append(qty(result.sessionsAdded, "expedição nova", "expedições novas", "new expedition", "new expeditions", "salida de campo nueva", "salidas de campo nuevas"))
        if (result.tracksAdded > 0) append(", ").append(qty(result.tracksAdded, "trilha", "trilhas", "track", "tracks", "recorrido", "recorridos"))
        append(".")
        val merged = result.sessionsMerged
        if (merged > 0) {
            append(
                if (merged == 1) t(" 1 expedição já existia e recebeu as novidades", " 1 expedition already existed and received the new data", " 1 salida de campo ya existía y recibió los datos nuevos")
                else t(" $merged expedições já existiam e receberam as novidades", " $merged expeditions already existed and received the new data", " $merged salidas de campo ya existían y recibieron los datos nuevos")
            )
            val elsewhere = result.sessionsInOtherProject
            if (elsewhere > 0) {
                append(
                    if (elsewhere == 1) t(" (1 em outro projeto, onde ficou)", " (1 in another project, where it stayed)", " (1 en otro proyecto, donde se quedó)")
                    else t(" ($elsewhere em outro projeto, onde ficaram)", " ($elsewhere in another project, where they stayed)", " ($elsewhere en otro proyecto, donde se quedaron)")
                )
            }
            append(".")
        }
        val skipped = result.occurrencesSkipped
        if (skipped > 0) {
            append(
                if (skipped == 1) t(" 1 ocorrência já existia e não foi duplicada", " 1 occurrence already existed and was not duplicated", " 1 registro ya existía y no se duplicó")
                else t(" $skipped ocorrências já existiam e não foram duplicadas", " $skipped occurrences already existed and were not duplicated", " $skipped registros ya existían y no se duplicaron")
            )
            append(".")
        }
        // Registros editados nos dois celulares em que o pesquisador escolheu a versao que chegou.
        val replaced = result.sessionsReplaced + result.occurrencesReplaced
        if (replaced > 0) {
            append(" ").append(qty(replaced, "registro ficou", "registros ficaram", "record now has", "records now have", "registro quedó", "registros quedaron"))
            append(t(" com a versão do outro celular, como você escolheu.", " the other phone's version, as you chose.", " con la versión del otro celular, como elegiste."))
        }
        if (result.tracksExtended > 0) {
            append(" ").append(qty(result.tracksExtended, "trilha recebeu", "trilhas receberam", "track received", "tracks received", "recorrido recibió", "recorridos recibieron"))
            append(" ").append(qty(result.trackPointsAdded, "ponto que faltava", "pontos que faltavam", "missing point", "missing points", "punto que faltaba", "puntos que faltaban")).append(".")
        }
        val withoutFile = result.mediaWithoutFile
        if (withoutFile == 1) append(t(" Atenção: 1 mídia ficou sem o arquivo (faltava no pacote ou não houve espaço para copiá-lo).", " Note: 1 media file is missing (it was not in the package or there was no space to copy it).", " Atención: 1 archivo multimedia quedó sin su archivo (faltaba en el paquete o no hubo espacio para copiarlo)."))
        if (withoutFile > 1) append(t(" Atenção: $withoutFile mídias ficaram sem o arquivo (faltava no pacote ou não houve espaço para copiá-los).", " Note: $withoutFile media files are missing (they were not in the package or there was no space to copy them).", " Atención: $withoutFile archivos multimedia quedaron sin su archivo (faltaban en el paquete o no hubo espacio para copiarlos)."))
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
                    _message.value = t("A exportação falhou: ${why(it)}. Libere espaço e tente de novo.", "The export failed: ${why(it)}. Free up space and try again.", "La exportación falló: ${why(it)}. Libera espacio e inténtalo de nuevo.")
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
        !CoordinateUtils.validLatitude(latitude) -> t("Latitude deve estar entre -90 e 90.", "Latitude must be between -90 and 90.", "La latitud debe estar entre -90 y 90.")
        !CoordinateUtils.validLongitude(longitude) -> t("Longitude deve estar entre -180 e 180.", "Longitude must be between -180 and 180.", "La longitud debe estar entre -180 y 180.")
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
        private const val KEY_LIST_REMOVED = "lista_removida_"
        private const val KEY_LAST_MICROPHONE = "last_microphone"
        private const val KEY_REPEAT_FIELDS = "repeat_fields:"
        private const val KEY_OFFLINE_MAP = "offline_map"
        private const val RESUME_LIMIT_MILLIS = 12 * 60 * 60 * 1000L

        fun newEventId(): String = "event-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}-${UUID.randomUUID().toString().take(8)}"
    }
}

