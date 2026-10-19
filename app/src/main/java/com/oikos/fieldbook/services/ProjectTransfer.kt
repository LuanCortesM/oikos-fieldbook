package com.oikos.fieldbook.services

import com.oikos.fieldbook.util.UiLanguage

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.oikos.fieldbook.data.MediaCodes
import com.oikos.fieldbook.data.OikosDatabase
import com.oikos.fieldbook.data.RowSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** O que um pacote traz, lido antes de importar. */
data class TransferManifest(
    val projectName: String,
    val appVersion: String,
    val databaseVersion: Int,
    val createdAt: Long,
    val sessions: Int,
    val occurrences: Int,
    val media: Int,
    /** Coletor de quem enviou (recordedBy mais frequente nas expedicoes do pacote). */
    val senderName: String = "",
    /** Registros que existem nos dois aparelhos com conteudo diferente. */
    val conflicts: List<TransferConflict> = emptyList(),
    /** Projeto deste aparelho que ja tem as expedicoes do pacote (o destino provavel). */
    val existingProjectId: Long? = null
) {
    /** Nome do projeto criado ao importar como novo: "Projeto (Maria Souza)". */
    val newProjectName: String get() = ProjectTransfer.newProjectName(projectName, senderName)
}

/** Campo que difere entre o registro deste aparelho e o que chegou. */
data class FieldDifference(val column: String, val local: String, val incoming: String)

/**
 * Expedicao ou ocorrencia editada nos dois celulares: o pesquisador escolhe
 * qual versao fica. [key] identifica o registro em import (evt:<eventID> ou
 * occ:<occurrenceID>).
 */
data class TransferConflict(
    val key: String,
    val isOccurrence: Boolean,
    val title: String,
    val subtitle: String,
    val localUpdatedAt: Long,
    val incomingUpdatedAt: Long,
    val differences: List<FieldDifference>
) {
    val incomingIsNewer: Boolean get() = incomingUpdatedAt > localUpdatedAt
}

/** O que a importacao fez. */
data class TransferSummary(
    val projectId: Long,
    val sessionsAdded: Int,
    /** Expedicoes do pacote que ja existiam no aparelho e receberam as novidades. */
    val sessionsMerged: Int,
    /** Dessas, quantas estao em outro projeto (as novidades foram para la). */
    val sessionsInOtherProject: Int,
    val occurrencesAdded: Int,
    /** Ocorrencias que ja existiam: a versao deste aparelho foi mantida. */
    val occurrencesSkipped: Int,
    val mediaAdded: Int,
    /** Midias registradas no pacote cujo arquivo nao veio junto. */
    val mediaWithoutFile: Int,
    val tracksAdded: Int,
    /** Trilhas que ja existiam e receberam pontos que faltavam. */
    val tracksExtended: Int,
    val trackPointsAdded: Int,
    /** Expedicoes e ocorrencias em que o pesquisador escolheu a versao que chegou. */
    val sessionsReplaced: Int = 0,
    val occurrencesReplaced: Int = 0
)

/**
 * Troca de projeto entre aparelhos, sem servidor: cada pessoa da equipe coleta
 * no proprio celular, gera um pacote e o coordenador junta tudo no dele.
 *
 * O pacote (.zip) tem projeto.json (as linhas do banco, coluna a coluna) e as
 * midias em files/. Na importacao todos os ids sao renumerados; expedicao com
 * o mesmo eventID e juntada a que ja existe e ocorrencia com o mesmo
 * occurrenceID nao entra de novo, entao importar o mesmo pacote duas vezes, ou
 * um pacote atualizado, nao duplica registros. Se a expedicao ou a ocorrencia
 * estiver diferente nos dois aparelhos, o pesquisador escolhe qual versao fica.
 */
class ProjectTransfer(private val database: OikosDatabase, private val filesDir: File) {

    fun export(projectId: Long, sessionId: Long?, output: OutputStream, appVersion: String) {
        val db = database.openHelper.writableDatabase
        db.query("PRAGMA wal_checkpoint(PASSIVE)").use { it.moveToFirst() }
        val sessionFilter = if (sessionId == null) "projectId = ?" else "id = ?"
        val sessionArg: Array<Any> = arrayOf(sessionId ?: projectId)
        // Tudo lido na mesma transacao: uma foto ou um ponto de trilha gravados
        // durante a exportacao nao podem entrar numa tabela e faltar na outra.
        val tables = JSONObject()
        database.runInTransaction {
            tables
                .put("projects", RowSnapshot.rows(db, "projects", "id = ?", arrayOf(projectId)))
                .put("field_configs", RowSnapshot.rows(db, "field_configs", "projectId = ?", arrayOf(projectId)))
                .put("sessions", RowSnapshot.rows(db, "sessions", sessionFilter, sessionArg))
                .put("occurrences", RowSnapshot.rows(db, "occurrences", "sessionId IN (SELECT id FROM sessions WHERE $sessionFilter)", sessionArg))
                .put("media", RowSnapshot.rows(db, "media", "sessionId IN (SELECT id FROM sessions WHERE $sessionFilter)", sessionArg))
                .put("gps_tracks", RowSnapshot.rows(db, "gps_tracks", "sessionId IN (SELECT id FROM sessions WHERE $sessionFilter)", sessionArg))
                .put(
                    "gps_track_points",
                    RowSnapshot.rows(db, "gps_track_points", "trackId IN (SELECT id FROM gps_tracks WHERE sessionId IN (SELECT id FROM sessions WHERE $sessionFilter)) ORDER BY trackId, timestamp, id", sessionArg)
                )
        }
        val project = tables.getJSONArray("projects").optJSONObject(0) ?: throw IOException(UiLanguage.t("projeto não encontrado", "project not found"))
        // Caminho relativo de cada midia: no outro aparelho a pasta do app e outra.
        val media = tables.getJSONArray("media")
        val files = mutableListOf<Pair<String, File>>()
        for (i in 0 until media.length()) {
            val row = media.getJSONObject(i)
            val file = File(row.optString("filePath"))
            if (file.isFile) {
                val relative = "m${row.getLong("id")}/${file.name}"
                row.put(RELATIVE_KEY, relative)
                files += relative to file
            }
        }
        val document = JSONObject()
            .put("format", FORMAT)
            .put("appVersion", appVersion)
            .put("databaseVersion", OikosDatabase.VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("projectName", project.optString("name"))
            .put("tables", tables)
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(DATA_ENTRY))
            zip.write(document.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            files.forEach { (relative, file) ->
                zip.putNextEntry(ZipEntry("files/$relative"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Le o pacote e compara com este aparelho (so leitura): nada e gravado. */
    fun readManifest(input: InputStream): TransferManifest {
        val document = readDocument(ZipInputStream(input.buffered()))
        val tables = document.getJSONObject("tables")
        return TransferManifest(
            projectName = document.optString("projectName"),
            appVersion = document.optString("appVersion"),
            databaseVersion = document.optInt("databaseVersion"),
            createdAt = document.optLong("createdAt"),
            sessions = tables.optJSONArray("sessions")?.length() ?: 0,
            occurrences = tables.optJSONArray("occurrences")?.length() ?: 0,
            media = tables.optJSONArray("media")?.length() ?: 0,
            senderName = senderName(tables),
            conflicts = conflicts(tables),
            existingProjectId = existingProject(tables)
        )
    }

    /** Projeto deste aparelho com mais expedicoes do pacote (mesmo eventID), se houver. */
    private fun existingProject(tables: JSONObject): Long? {
        val db = database.openHelper.readableDatabase
        val sessions = tables.optJSONArray("sessions") ?: return null
        return (0 until sessions.length())
            .mapNotNull { existingSession(db, sessions.getJSONObject(it).optString("eventId"))?.projectId }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    /**
     * Expedicoes e ocorrencias do pacote que ja existem aqui (mesmo eventID ou
     * occurrenceID) com algum campo diferente. Iguais nao entram: nao ha o que
     * escolher.
     */
    private fun conflicts(tables: JSONObject): List<TransferConflict> {
        val db = database.openHelper.readableDatabase
        val result = mutableListOf<TransferConflict>()
        (tables.optJSONArray("sessions") ?: JSONArray()).forEachObject { row ->
            val place = existingSession(db, row.optString("eventId")) ?: return@forEachObject
            val local = RowSnapshot.rows(db, "sessions", "id = ?", arrayOf(place.sessionId)).optJSONObject(0) ?: return@forEachObject
            val differences = differences(local, row)
            if (differences.isEmpty()) return@forEachObject
            result += TransferConflict(
                key = SESSION_KEY + row.optString("eventId"),
                isOccurrence = false,
                title = row.optString("name").ifBlank { row.optString("eventId") },
                subtitle = listOf(row.optString("eventDate"), row.optString("recordedBy")).filter { it.isNotBlank() }.joinToString(" · "),
                localUpdatedAt = local.optLong("updatedAt"),
                incomingUpdatedAt = row.optLong("updatedAt"),
                differences = differences
            )
        }
        (tables.optJSONArray("occurrences") ?: JSONArray()).forEachObject { row ->
            val existing = existingOccurrence(db, row.optString("occurrenceId")) ?: return@forEachObject
            val local = RowSnapshot.rows(db, "occurrences", "id = ?", arrayOf(existing.id)).optJSONObject(0) ?: return@forEachObject
            val differences = differences(local, row)
            if (differences.isEmpty()) return@forEachObject
            result += TransferConflict(
                key = OCCURRENCE_KEY + row.optString("occurrenceId"),
                isOccurrence = true,
                title = row.optString("scientificName").ifBlank { row.optString("vernacularName") }
                    .ifBlank { local.optString("scientificName") }.ifBlank { local.optString("vernacularName") },
                subtitle = listOf(row.optString("recordNumber"), row.optString("recordedBy")).filter { it.isNotBlank() }.joinToString(" · "),
                localUpdatedAt = local.optLong("updatedAt"),
                incomingUpdatedAt = row.optLong("updatedAt"),
                differences = differences
            )
        }
        return result
    }

    /**
     * Campos com valor diferente. Vazio e nulo contam como iguais, numeros sao
     * comparados como numeros, e os campos personalizados (dynamicFieldsJson)
     * campo a campo, como "dyn:<termo>".
     */
    private fun differences(local: JSONObject, incoming: JSONObject): List<FieldDifference> {
        val result = mutableListOf<FieldDifference>()
        incoming.keys().forEach { column ->
            if (column in NOT_COMPARED || column.startsWith("_") || !local.has(column)) return@forEach
            val mine = local.opt(column)
            val theirs = incoming.opt(column)
            if (column == DYNAMIC_FIELDS) {
                val a = runCatching { JSONObject(text(mine).ifBlank { "{}" }) }.getOrElse { JSONObject() }
                val b = runCatching { JSONObject(text(theirs).ifBlank { "{}" }) }.getOrElse { JSONObject() }
                (a.keys().asSequence() + b.keys().asSequence()).toSortedSet().forEach { key ->
                    if (!same(a.opt(key), b.opt(key))) result += FieldDifference("dyn:$key", text(a.opt(key)), text(b.opt(key)))
                }
            } else if (!same(mine, theirs)) {
                result += FieldDifference(column, text(mine), text(theirs))
            }
        }
        return result
    }

    /**
     * [targetProjectId] nulo cria um projeto novo; senao junta ao projeto indicado.
     * [takeIncoming] tem as chaves (TransferConflict.key) dos registros em que o
     * pesquisador escolheu a versao que chegou; nos outros fica a deste aparelho.
     * Nada e gravado se qualquer linha for recusada pelo banco.
     */
    fun import(input: InputStream, targetProjectId: Long?, takeIncoming: Set<String> = emptySet()): TransferSummary {
        val stagingRoot = File(filesDir, STAGING_DIR).apply { mkdirs() }
        // Sobra de importacao interrompida (app fechado a forca) ocupa espaco.
        stagingRoot.listFiles().orEmpty()
            .filter { it.lastModified() < System.currentTimeMillis() - STALE_STAGING_MILLIS }
            .forEach { it.deleteRecursively() }
        // Pasta propria para cada importacao: duas importacoes seguidas nao
        // apagam os arquivos uma da outra.
        val staging = File(stagingRoot, "${System.currentTimeMillis()}-${System.nanoTime()}").apply { mkdirs() }
        try {
            val zip = ZipInputStream(input.buffered())
            val document = readDocument(zip)
            if (document.optInt("databaseVersion") > OikosDatabase.VERSION) {
                throw IOException(UiLanguage.t("o pacote foi feito numa versão mais nova do Oikos; atualize o app antes de importar", "the package was made with a newer version of Oikos; update the app before importing"))
            }
            // As midias vao para uma area de preparo; so mudam de lugar depois
            // que o banco aceitar as linhas.
            val stagingCanonical = staging.canonicalFile
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !entry.name.startsWith("files/")) continue
                val target = File(staging, entry.name.removePrefix("files/")).canonicalFile
                if (!target.path.startsWith(stagingCanonical.path + File.separator)) throw IOException(UiLanguage.t("caminho inválido no pacote: ${entry.name}", "invalid path in the package: ${entry.name}"))
                target.parentFile?.mkdirs()
                target.outputStream().use { zip.copyTo(it) }
            }
            val tables = document.getJSONObject("tables")
            val moves = mutableListOf<Pair<File, File>>()
            val summary = insert(tables, targetProjectId, takeIncoming, staging, moves)
            // O banco ja aceitou as linhas: um arquivo que nao puder ser movido
            // (disco cheio) e contado no resumo, em vez de virar um erro que
            // diria "nada foi alterado".
            val failed = moves.count { (from, to) ->
                runCatching {
                    to.parentFile?.mkdirs()
                    if (!from.renameTo(to)) {
                        from.copyTo(to, overwrite = false)
                        from.delete()
                    }
                }.isFailure
            }
            return summary.copy(mediaWithoutFile = summary.mediaWithoutFile + failed)
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Expedicao do aparelho que recebe as linhas de uma expedicao do pacote. */
    private data class Place(val projectId: Long, val sessionId: Long)

    private fun insert(tables: JSONObject, targetProjectId: Long?, takeIncoming: Set<String>, staging: File, moves: MutableList<Pair<File, File>>): TransferSummary {
        val db = database.openHelper.writableDatabase
        val projectsRoot = File(filesDir, "Projetos").canonicalFile
        var summary: TransferSummary? = null
        database.runInTransaction {
            val projectId: Long
            if (targetProjectId == null) {
                val sessions = tables.optJSONArray("sessions") ?: JSONArray()
                val alreadyThere = (0 until sessions.length()).map { sessions.getJSONObject(it).optString("eventId") }
                    .filter { it.isNotBlank() }
                    .mapNotNull { eventId -> existingSession(db, eventId) }
                if (sessions.length() > 0 && alreadyThere.size == sessions.length()) {
                    val name = projectName(db, alreadyThere.first().projectId)
                    throw IOException(UiLanguage.t("este pacote já foi importado no projeto \"$name\". Para trazer novidades, escolha juntar a esse projeto", "this package was already imported into the project \"$name\". To bring in new data, choose to merge into that project"))
                }
                val projectRow = tables.getJSONArray("projects").optJSONObject(0) ?: throw IOException(UiLanguage.t("o pacote não traz o projeto", "the package does not contain the project"))
                // Projeto novo leva o nome de quem enviou: "Projeto (Maria Souza)".
                val name = uniqueProjectName(db, newProjectName(projectRow.optString("name").ifBlank { "Projeto importado" }, senderName(tables)))
                projectId = db.insert("projects", SQLiteDatabase.CONFLICT_ABORT, RowSnapshot.contentValues(
                    projectRow, RowSnapshot.columns(db, "projects"), skip = setOf("id"),
                    overrides = mapOf("name" to name, "updatedAt" to System.currentTimeMillis())
                ))
                val fieldColumns = RowSnapshot.columns(db, "field_configs")
                (tables.optJSONArray("field_configs") ?: JSONArray()).forEachObject { row ->
                    db.insert("field_configs", SQLiteDatabase.CONFLICT_IGNORE, RowSnapshot.contentValues(row, fieldColumns, skip = setOf("id"), overrides = mapOf("projectId" to projectId)))
                }
            } else {
                // Projeto apagado enquanto o pacote era escolhido: antes o banco
                // recusava com uma mensagem tecnica.
                if (!existsId(db, "projects", targetProjectId)) throw IOException(UiLanguage.t("o projeto escolhido não existe mais", "the chosen project no longer exists"))
                projectId = targetProjectId
            }

            // Expedicao com o mesmo eventID ja existe: as novidades vao para
            // ela, mesmo que esteja em outro projeto. Antes, nesse caso, as
            // ocorrencias, midias e trilhas dela eram descartadas sem aviso.
            val places = mutableMapOf<Long, Place>()
            var sessionsAdded = 0
            var sessionsMerged = 0
            var sessionsInOtherProject = 0
            var sessionsReplaced = 0
            val sessionColumns = RowSnapshot.columns(db, "sessions")
            (tables.optJSONArray("sessions") ?: JSONArray()).forEachObject { row ->
                val existing = existingSession(db, row.optString("eventId"))
                if (existing != null) {
                    places[row.getLong("id")] = existing
                    sessionsMerged++
                    if (existing.projectId != projectId) sessionsInOtherProject++
                    // Editada nos dois aparelhos e o pesquisador escolheu a que chegou.
                    if (SESSION_KEY + row.optString("eventId") in takeIncoming) {
                        replaceWithIncoming(db, "sessions", existing.sessionId, row, sessionColumns)
                        sessionsReplaced++
                    }
                } else {
                    val id = db.insert("sessions", SQLiteDatabase.CONFLICT_ABORT, RowSnapshot.contentValues(
                        row, sessionColumns, skip = setOf("id"), overrides = mapOf("projectId" to projectId)
                    ))
                    places[row.getLong("id")] = Place(projectId, id)
                    sessionsAdded++
                }
            }

            // Ocorrencia que ja existe (mesmo occurrenceID) nao e duplicada; so e
            // sobrescrita se o pesquisador escolheu a versao que chegou. Continua
            // recebendo as midias novas.
            val occurrences = mutableMapOf<Long, Pair<Place, Long>>()
            var occurrencesAdded = 0
            var occurrencesSkipped = 0
            var occurrencesReplaced = 0
            val occurrenceColumns = RowSnapshot.columns(db, "occurrences")
            (tables.optJSONArray("occurrences") ?: JSONArray()).forEachObject { row ->
                val local = existingOccurrence(db, row.optString("occurrenceId"))
                if (local != null) {
                    occurrences[row.getLong("id")] = local.place to local.id
                    occurrencesSkipped++
                    if (OCCURRENCE_KEY + row.optString("occurrenceId") in takeIncoming) {
                        replaceWithIncoming(db, "occurrences", local.id, row, occurrenceColumns)
                        occurrencesReplaced++
                    }
                    return@forEachObject
                }
                val place = places[row.getLong("sessionId")] ?: throw IOException(UiLanguage.t("pacote incompleto: ocorrência sem a expedição dela", "incomplete package: occurrence without its expedition"))
                occurrences[row.getLong("id")] = place to db.insert("occurrences", SQLiteDatabase.CONFLICT_ABORT, RowSnapshot.contentValues(
                    row, occurrenceColumns, skip = setOf("id"), overrides = mapOf("projectId" to place.projectId, "sessionId" to place.sessionId)
                ))
                occurrencesAdded++
            }

            var mediaAdded = 0
            var mediaWithoutFile = 0
            val reserved = mutableSetOf<String>()
            val mediaColumns = RowSnapshot.columns(db, "media")
            (tables.optJSONArray("media") ?: JSONArray()).forEachObject { row ->
                val oldOccurrence = if (row.isNull("occurrenceId")) null else row.getLong("occurrenceId")
                val place: Place
                val occurrenceId: Long?
                if (oldOccurrence != null) {
                    val known = occurrences[oldOccurrence] ?: throw IOException(UiLanguage.t("pacote incompleto: mídia sem a ocorrência dela", "incomplete package: media file without its occurrence"))
                    place = known.first
                    occurrenceId = known.second
                } else {
                    place = places[row.getLong("sessionId")] ?: throw IOException(UiLanguage.t("pacote incompleto: mídia sem a expedição dela", "incomplete package: media file without its expedition"))
                    occurrenceId = null
                }
                if (mediaExists(db, place.sessionId, occurrenceId, row.optString("fileName"), row.optLong("createdAt"))) return@forEachObject
                val staged = row.optString(RELATIVE_KEY).takeIf { it.isNotBlank() }?.let { File(staging, it) }?.takeIf { it.isFile }
                val subfolder = if (row.optString("mediaType") == "audio") "Audios" else "Fotos"
                // So o nome, sem pastas: um nome como "../../banco" escreveria
                // fora da pasta do projeto.
                val name = safeFileName(row.optString("fileName")).ifBlank { staged?.name?.let(::safeFileName).orEmpty() }.ifBlank { "midia" }
                val target = uniqueFile(File(filesDir, "Projetos/PROJ-${place.projectId}/EXP-${place.sessionId}/$subfolder/$name"), reserved)
                if (!target.canonicalFile.path.startsWith(projectsRoot.path + File.separator)) throw IOException(UiLanguage.t("nome de arquivo inválido no pacote: ${row.optString("fileName")}", "invalid file name in the package: ${row.optString("fileName")}"))
                reserved += target.path
                db.insert("media", SQLiteDatabase.CONFLICT_ABORT, RowSnapshot.contentValues(
                    row, mediaColumns, skip = setOf("id", RELATIVE_KEY),
                    overrides = mapOf(
                        "projectId" to place.projectId, "sessionId" to place.sessionId, "occurrenceId" to occurrenceId,
                        "filePath" to target.path,
                        // O numero fixo viaja com a midia; pacote de versao anterior traz o
                        // numero que o outro aparelho exportava (MED + id dele).
                        "mediaCode" to MediaCodes.free(db, row.optString("mediaCode").ifBlank { MediaCodes.legacy(row.getLong("id")) })
                    )
                ))
                if (staged != null) moves += staged to target else mediaWithoutFile++
                mediaAdded++
            }

            // Trilha ja importada antes (pacote feito com ela ainda gravando)
            // recebe os pontos que faltam, em vez de ser ignorada.
            var tracksAdded = 0
            var tracksExtended = 0
            var pointsAdded = 0
            val packagePoints = (tables.optJSONArray("gps_track_points") ?: JSONArray()).let { points ->
                (0 until points.length()).map { points.getJSONObject(it) }.groupBy { it.getLong("trackId") }
            }
            val trackColumns = RowSnapshot.columns(db, "gps_tracks")
            val pointColumns = RowSnapshot.columns(db, "gps_track_points")
            (tables.optJSONArray("gps_tracks") ?: JSONArray()).forEachObject { row ->
                val place = places[row.getLong("sessionId")] ?: throw IOException(UiLanguage.t("pacote incompleto: trilha sem a expedição dela", "incomplete package: track without its expedition"))
                val points = packagePoints[row.getLong("id")].orEmpty()
                val local = existingTrack(db, place.sessionId, row)
                if (local == null) {
                    val id = db.insert("gps_tracks", SQLiteDatabase.CONFLICT_ABORT, RowSnapshot.contentValues(
                        // Trilha que estava gravando no outro aparelho chega encerrada.
                        row, trackColumns, skip = setOf("id"),
                        overrides = mapOf("projectId" to place.projectId, "sessionId" to place.sessionId, "active" to 0L) + closedEnd(row, points)
                    ))
                    points.forEach { point ->
                        db.insert("gps_track_points", SQLiteDatabase.CONFLICT_ABORT, RowSnapshot.contentValues(point, pointColumns, skip = setOf("id"), overrides = mapOf("trackId" to id)))
                    }
                    tracksAdded++
                    pointsAdded += points.size
                    return@forEachObject
                }
                // A trilha que esta gravando neste aparelho nao e mexida.
                if (local.active) return@forEachObject
                val known = pointTimes(db, local.id)
                val missing = points.filter { it.optLong("timestamp") !in known }
                if (missing.isEmpty()) return@forEachObject
                missing.forEach { point ->
                    db.insert("gps_track_points", SQLiteDatabase.CONFLICT_ABORT, RowSnapshot.contentValues(point, pointColumns, skip = setOf("id"), overrides = mapOf("trackId" to local.id)))
                }
                // Distancia e duracao vem da versao do pacote quando ela tem
                // mais pontos (foi exportada depois); a contagem e a real.
                val lastPoint = (known + missing.map { it.optLong("timestamp") }).max()
                val values = ContentValues().apply {
                    put("pointsCount", known.size + missing.size)
                    if (row.optInt("pointsCount") > local.pointsCount) {
                        put("totalDistance", row.optDouble("totalDistance", 0.0))
                        put("duration", row.optLong("duration"))
                    }
                    put("endTime", maxOf(lastPoint, if (row.isNull("endTime")) 0L else row.optLong("endTime"), local.endTime ?: 0L))
                }
                db.update("gps_tracks", SQLiteDatabase.CONFLICT_ABORT, values, "id = ?", arrayOf(local.id))
                tracksExtended++
                pointsAdded += missing.size
            }
            summary = TransferSummary(
                projectId = projectId,
                sessionsAdded = sessionsAdded,
                sessionsMerged = sessionsMerged,
                sessionsInOtherProject = sessionsInOtherProject,
                occurrencesAdded = occurrencesAdded,
                occurrencesSkipped = occurrencesSkipped,
                mediaAdded = mediaAdded,
                mediaWithoutFile = mediaWithoutFile,
                tracksAdded = tracksAdded,
                tracksExtended = tracksExtended,
                trackPointsAdded = pointsAdded,
                sessionsReplaced = sessionsReplaced,
                occurrencesReplaced = occurrencesReplaced
            )
        }
        return summary!!
    }

    /** Trilha ativa no pacote chega encerrada no ultimo ponto. */
    private fun closedEnd(row: JSONObject, points: List<JSONObject>): Map<String, Any?> {
        if (row.optLong("active") == 0L || !row.isNull("endTime")) return emptyMap()
        val last = points.maxOfOrNull { it.optLong("timestamp") } ?: row.optLong("startTime")
        return mapOf("endTime" to last)
    }

    private fun readDocument(zip: ZipInputStream): JSONObject {
        val entry = zip.nextEntry ?: throw IOException(UiLanguage.t("arquivo vazio", "empty file"))
        if (entry.name != DATA_ENTRY) throw IOException(UiLanguage.t("este arquivo não é um pacote de projeto do Oikos (se for um backup, use Restaurar em Configurações)", "this file is not an Oikos project package (if it is a backup, use Restore in Settings)"))
        val document = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
        if (document.optString("format") != FORMAT) throw IOException(UiLanguage.t("formato de pacote desconhecido", "unknown package format"))
        return document
    }

    /** Expedicao do aparelho com este eventID, se ja existir. */
    private fun existingSession(db: SupportSQLiteDatabase, eventId: String): Place? {
        if (eventId.isBlank()) return null
        return db.query("SELECT projectId, id FROM sessions WHERE eventId = ?", arrayOf(eventId)).use { cursor ->
            if (cursor.moveToFirst()) Place(cursor.getLong(0), cursor.getLong(1)) else null
        }
    }

    private data class LocalOccurrence(val id: Long, val place: Place, val updatedAt: Long)

    private fun existingOccurrence(db: SupportSQLiteDatabase, occurrenceId: String): LocalOccurrence? {
        if (occurrenceId.isBlank()) return null
        return db.query("SELECT id, projectId, sessionId, updatedAt FROM occurrences WHERE occurrenceId = ?", arrayOf(occurrenceId)).use { cursor ->
            if (cursor.moveToFirst()) LocalOccurrence(cursor.getLong(0), Place(cursor.getLong(1), cursor.getLong(2)), cursor.getLong(3)) else null
        }
    }

    private data class LocalTrack(val id: Long, val active: Boolean, val pointsCount: Int, val endTime: Long?)

    /**
     * Mesma trilha: mesma expedicao e mesmo inicio. Trilha importada sem
     * horario (inicio 0) so e a mesma se tambem tiver os mesmos pontos e a
     * mesma distancia; senao duas trilhas sem horario seriam fundidas.
     */
    private fun existingTrack(db: SupportSQLiteDatabase, sessionId: Long, row: JSONObject): LocalTrack? {
        val startTime = row.optLong("startTime")
        val sql = if (startTime > 0L) "SELECT id, active, pointsCount, endTime FROM gps_tracks WHERE sessionId = ? AND startTime = ? LIMIT 1"
        else "SELECT id, active, pointsCount, endTime FROM gps_tracks WHERE sessionId = ? AND startTime = ? AND pointsCount = ? AND abs(totalDistance - ?) < 0.01 LIMIT 1"
        val args: Array<Any> = if (startTime > 0L) arrayOf(sessionId, startTime) else arrayOf(sessionId, startTime, row.optInt("pointsCount"), row.optDouble("totalDistance", 0.0))
        return db.query(sql, args).use { cursor ->
            if (cursor.moveToFirst()) LocalTrack(cursor.getLong(0), cursor.getInt(1) != 0, cursor.getInt(2), if (cursor.isNull(3)) null else cursor.getLong(3)) else null
        }
    }

    private fun pointTimes(db: SupportSQLiteDatabase, trackId: Long): Set<Long> =
        db.query("SELECT timestamp FROM gps_track_points WHERE trackId = ?", arrayOf(trackId)).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }

    private fun existsId(db: SupportSQLiteDatabase, table: String, id: Long): Boolean =
        db.query("SELECT 1 FROM $table WHERE id = ? LIMIT 1", arrayOf(id)).use { it.moveToFirst() }

    private fun projectName(db: SupportSQLiteDatabase, projectId: Long): String =
        db.query("SELECT name FROM projects WHERE id = ?", arrayOf(projectId)).use { if (it.moveToFirst()) it.getString(0) else "?" }

    private fun uniqueProjectName(db: SupportSQLiteDatabase, name: String): String {
        if (!exists(db, "projects", "name", name)) return name
        var index = 2
        while (exists(db, "projects", "name", "$name ($index)")) index++
        return "$name ($index)"
    }

    private fun exists(db: SupportSQLiteDatabase, table: String, column: String, value: String): Boolean =
        value.isNotBlank() && db.query("SELECT 1 FROM $table WHERE $column = ? LIMIT 1", arrayOf(value)).use { it.moveToFirst() }

    private fun mediaExists(db: SupportSQLiteDatabase, sessionId: Long, occurrenceId: Long?, fileName: String, createdAt: Long): Boolean =
        if (occurrenceId == null) {
            db.query(
                "SELECT 1 FROM media WHERE sessionId = ? AND occurrenceId IS NULL AND fileName = ? AND createdAt = ? LIMIT 1",
                arrayOf(sessionId, fileName, createdAt)
            ).use { it.moveToFirst() }
        } else {
            db.query(
                "SELECT 1 FROM media WHERE occurrenceId = ? AND fileName = ? AND createdAt = ? LIMIT 1",
                arrayOf(occurrenceId, fileName, createdAt)
            ).use { it.moveToFirst() }
        }

    /** Nome sem pastas e sem caracteres que o Android recusa. */
    private fun safeFileName(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\x00-\\x1F:*?\"<>|]"), "_")
            .trim().trimStart('.')
            .take(120)

    /** Caminho livre, contando tambem os ja reservados por esta importacao. */
    private fun uniqueFile(file: File, reserved: Set<String>): File {
        if (!file.exists() && file.path !in reserved) return file
        val extension = file.extension.let { if (it.isEmpty()) "" else ".$it" }
        var index = 2
        while (true) {
            val candidate = File(file.parentFile, "${file.nameWithoutExtension}-$index$extension")
            if (!candidate.exists() && candidate.path !in reserved) return candidate
            index++
        }
    }

    /**
     * Troca os campos do registro deste aparelho pelos que chegaram, so os que
     * o pacote traz: uma coluna que um pacote de versao anterior nao tem fica
     * como esta (contentValues a preencheria com vazio).
     */
    private fun replaceWithIncoming(db: SupportSQLiteDatabase, table: String, localId: Long, row: JSONObject, columns: Map<String, RowSnapshot.Column>) {
        val values = RowSnapshot.contentValues(row, columns, skip = NOT_COMPARED - "updatedAt")
        val present = row.keys().asSequence().toSet()
        values.keySet().filter { it !in present }.forEach(values::remove)
        if (values.size() > 0) db.update(table, SQLiteDatabase.CONFLICT_ABORT, values, "id = ?", arrayOf(localId))
    }

    private inline fun JSONArray.forEachObject(action: (JSONObject) -> Unit) {
        for (i in 0 until length()) action(getJSONObject(i))
    }

    companion object {
        const val FORMAT = "oikos-projeto-1"
        const val SESSION_KEY = "evt:"
        const val OCCURRENCE_KEY = "occ:"
        private const val DATA_ENTRY = "projeto.json"
        private const val RELATIVE_KEY = "_arquivoNoPacote"
        private const val STAGING_DIR = "import-staging"
        private const val STALE_STAGING_MILLIS = 6 * 60 * 60 * 1000L
        private const val DYNAMIC_FIELDS = "dynamicFieldsJson"

        /** Colunas que mudam de um aparelho para o outro sem ser edicao do registro. */
        private val NOT_COMPARED = setOf("id", "projectId", "sessionId", "createdAt", "updatedAt")

        /**
         * "Projeto (Maria Souza)"; sem coletor no pacote, so o nome do projeto. Um
         * pacote que volta (o projeto ja tem o nome de quem enviou) nao ganha o
         * nome duas vezes.
         */
        fun newProjectName(projectName: String, senderName: String): String =
            if (senderName.isBlank() || projectName.endsWith(" ($senderName)")) projectName else "$projectName ($senderName)"

        /**
         * Coletor de quem enviou: o recordedBy mais frequente nas expedicoes do
         * pacote (o das ocorrencias, se as expedicoes nao tiverem). Com varios
         * coletores ("A | B"), vale o primeiro.
         */
        fun senderName(tables: JSONObject): String {
            fun recordedBy(table: String): List<String> {
                val rows = tables.optJSONArray(table) ?: return emptyList()
                return (0 until rows.length()).map { i -> rows.optJSONObject(i)?.optString("recordedBy").orEmpty() }
            }
            return senderName(recordedBy("sessions"), recordedBy("occurrences"))
        }

        /** O mesmo, a partir dos recordedBy das expedicoes e das ocorrencias. */
        fun senderName(sessionCollectors: List<String>, occurrenceCollectors: List<String>): String {
            fun firsts(values: List<String>) = values.mapNotNull { value ->
                value.split('|', ';').map { it.trim() }.firstOrNull { it.isNotEmpty() }
            }
            val names = firsts(sessionCollectors).ifEmpty { firsts(occurrenceCollectors) }
            // eachCount guarda a ordem de chegada: no empate, vence o primeiro.
            return names.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
        }

        /** Mesmo valor: vazio e nulo iguais, numeros comparados como numeros. */
        internal fun same(a: Any?, b: Any?): Boolean {
            val x = normalized(a)
            val y = normalized(b)
            if (x is Double && y is Double) return kotlin.math.abs(x - y) <= 1e-9 * maxOf(1.0, kotlin.math.abs(x), kotlin.math.abs(y))
            return x == y
        }

        private fun normalized(value: Any?): Any? = when (value) {
            null, JSONObject.NULL -> null
            is Number -> value.toDouble()
            else -> value.toString().ifBlank { null }
        }

        /** Valor para mostrar ao pesquisador: 12 em vez de 12.0, sem notacao cientifica. */
        internal fun text(value: Any?): String = when (value) {
            null, JSONObject.NULL -> ""
            is Double -> java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
            is Float -> java.math.BigDecimal.valueOf(value.toDouble()).stripTrailingZeros().toPlainString()
            else -> value.toString()
        }
    }
}
