package com.oikos.fieldbook.services

import com.oikos.fieldbook.util.UiLanguage

import android.database.sqlite.SQLiteDatabase
import com.oikos.fieldbook.data.MediaCodes
import com.oikos.fieldbook.data.RowSnapshot
import com.oikos.fieldbook.data.OikosDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Algo apagado que ainda pode voltar. */
data class TrashItem(
    val id: String,
    /** "project", "session", "occurrence" ou "media". */
    val kind: String,
    val title: String,
    val deletedAt: Long,
    val occurrences: Int,
    val media: Int,
    val directory: File
)

/**
 * Lixeira: apagar um projeto, expedicao, ocorrencia ou midia guarda um retrato
 * das linhas do banco (coluna a coluna, sem depender do esquema) e move os
 * arquivos para files/Lixeira. Restaurar reinsere as linhas com os mesmos
 * identificadores e devolve os arquivos ao lugar. Itens com mais de
 * [RETENTION_DAYS] dias sao eliminados.
 *
 * Dado de campo nao se repete: um toque errado em "Apagar" nao pode ser
 * definitivo.
 */
class TrashService(private val database: OikosDatabase, private val filesDir: File) {

    private val root: File get() = File(filesDir, TRASH_DIR).apply { mkdirs() }

    private data class Selection(val table: String, val where: String, val args: Array<Any>)

    fun list(): List<TrashItem> =
        root.listFiles { file -> file.isDirectory }.orEmpty()
            .mapNotNull { dir -> runCatching { header(dir) }.getOrNull() }
            .sortedByDescending { it.deletedAt }

    fun trashOccurrence(id: Long, title: String): TrashItem = trash(
        kind = "occurrence",
        title = title,
        selections = listOf(
            Selection("occurrences", "id = ?", arrayOf(id)),
            Selection("media", "occurrenceId = ?", arrayOf(id))
        ),
        directories = emptyList()
    )

    fun trashSession(projectId: Long, id: Long, title: String): TrashItem = trash(
        kind = "session",
        title = title,
        selections = listOf(
            Selection("sessions", "id = ?", arrayOf(id)),
            Selection("occurrences", "sessionId = ?", arrayOf(id)),
            Selection("media", "sessionId = ?", arrayOf(id)),
            Selection("gps_tracks", "sessionId = ?", arrayOf(id)),
            Selection("gps_track_points", "trackId IN (SELECT id FROM gps_tracks WHERE sessionId = ?)", arrayOf(id))
        ),
        directories = listOf("Projetos/PROJ-$projectId/EXP-$id")
    )

    fun trashProject(id: Long, title: String): TrashItem = trash(
        kind = "project",
        title = title,
        selections = listOf(
            Selection("projects", "id = ?", arrayOf(id)),
            Selection("field_configs", "projectId = ?", arrayOf(id)),
            Selection("sessions", "projectId = ?", arrayOf(id)),
            Selection("occurrences", "projectId = ?", arrayOf(id)),
            Selection("media", "projectId = ?", arrayOf(id)),
            Selection("gps_tracks", "projectId = ?", arrayOf(id)),
            Selection("gps_track_points", "trackId IN (SELECT id FROM gps_tracks WHERE projectId = ?)", arrayOf(id))
        ),
        directories = listOf("Projetos/PROJ-$id")
    )

    fun trashMedia(id: Long, title: String): TrashItem = trash(
        kind = "media",
        title = title,
        selections = listOf(Selection("media", "id = ?", arrayOf(id))),
        directories = emptyList()
    )

    /**
     * Copia as linhas, apaga do banco (filhos antes dos pais, sem depender da
     * cascata) e so entao move os arquivos. Se algo falhar antes de apagar,
     * nada muda.
     */
    private fun trash(kind: String, title: String, selections: List<Selection>, directories: List<String>): TrashItem {
        val deletedAt = System.currentTimeMillis()
        // Pasta nova sempre: duas exclusoes no mesmo milissegundo dividiam a
        // pasta e o segundo item.json apagava o primeiro.
        var dir = File(root, "$deletedAt-$kind")
        var suffix = 2
        while (!dir.mkdir()) {
            if (!root.isDirectory || suffix > 1000) throw IOException(UiLanguage.t("não foi possível criar a pasta da lixeira", "could not create the trash folder"))
            dir = File(root, "$deletedAt-$kind-$suffix")
            suffix++
        }
        var removedFromDatabase = false
        try {
            val db = database.openHelper.writableDatabase
            val paths = linkedSetOf<String>()
            // Retrato e exclusao na mesma transacao: antes o retrato era tirado
            // antes, e um ponto de trilha ou uma midia gravados no intervalo eram
            // apagados sem ir para a lixeira.
            database.runInTransaction {
                val tables = JSONObject()
                selections.forEach { selection -> tables.put(selection.table, RowSnapshot.rows(db, selection.table, selection.where, selection.args)) }
                val mediaRows = tables.optJSONArray("media") ?: JSONArray()
                for (i in 0 until mediaRows.length()) {
                    relative(mediaRows.getJSONObject(i).optString("filePath"))?.let(paths::add)
                }
                directories.forEach { relativeDir ->
                    File(filesDir, relativeDir).takeIf { it.isDirectory }?.walkTopDown()?.filter { it.isFile }?.forEach { file ->
                        relative(file.path)?.let(paths::add)
                    }
                }
                val header = JSONObject()
                    .put("kind", kind)
                    .put("title", title)
                    .put("deletedAt", deletedAt)
                    .put("occurrences", tables.optJSONArray("occurrences")?.length() ?: 0)
                    .put("media", mediaRows.length())
                    .put("tables", tables)
                    .put("files", JSONArray(paths.toList()))
                    .put("parents", parentsOf(db, tables))
                File(dir, ITEM_FILE).writeText(header.toString())
                selections.reversed().forEach { selection -> db.delete(selection.table, selection.where, selection.args) }
            }
            removedFromDatabase = true
            // Arquivo que nao puder ser movido continua onde estava: nada se perde.
            paths.forEach { path -> move(File(filesDir, path), File(dir, "files/$path")) }
            return header(dir)
        } catch (error: Throwable) {
            // Antes de apagar do banco nada mudou: basta descartar o item. Depois,
            // o item fica, para que o que ja saiu do banco possa voltar.
            if (!removedFromDatabase) dir.deleteRecursively()
            throw error
        }
    }

    /**
     * Reinsere as linhas (pais antes dos filhos) e devolve os arquivos.
     * Devolve quantos arquivos nao puderam voltar (normalmente zero).
     */
    fun restore(item: TrashItem): Int {
        val json = JSONObject(File(item.directory, ITEM_FILE).readText())
        val tables = json.getJSONObject("tables")
        val db = database.openHelper.writableDatabase
        // Destino de cada arquivo: se ja existe outro no mesmo caminho (foto nova
        // tirada no mesmo minuto), volta com outro nome. Antes o arquivo novo era
        // sobrescrito e perdido.
        val files = json.optJSONArray("files") ?: JSONArray()
        val reserved = mutableSetOf<String>()
        val destinations = (0 until files.length()).associate { i ->
            val path = files.getString(i)
            path to freeRelativePath(path, reserved).also { reserved += it }
        }
        try {
            database.runInTransaction {
                checkParents(db, json.optJSONArray("parents") ?: JSONArray(), item.kind)
                INSERT_ORDER.forEach { table ->
                    val rows = tables.optJSONArray(table) ?: return@forEach
                    val columns = RowSnapshot.columns(db, table)
                    for (i in 0 until rows.length()) {
                        val row = rows.getJSONObject(i)
                        val overrides = when (table) {
                            "gps_tracks" -> closedTrack(row, tables)
                            "media" -> relative(row.optString("filePath"))?.let { original ->
                                destinations[original]?.takeIf { it != original }?.let { mapOf("filePath" to File(filesDir, it).path) }
                            }.orEmpty() + mapOf(
                                // Item apagado antes do numero fixo volta com o numero de antes.
                                "mediaCode" to MediaCodes.free(db, row.optString("mediaCode").ifBlank { MediaCodes.legacy(row.optLong("id")) })
                            )
                            else -> emptyMap()
                        }
                        val values = RowSnapshot.contentValues(row, columns, overrides = overrides)
                        db.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
                    }
                }
            }
        } catch (error: android.database.sqlite.SQLiteConstraintException) {
            val text = error.message.orEmpty()
            throw IOException(
                when {
                    "UNIQUE" in text || "PRIMARY KEY" in text ->
                        UiLanguage.t("já existe no aparelho um registro com o mesmo identificador (por exemplo, depois de restaurar um backup). Nada foi alterado.", "a record with the same identifier already exists on this device (for example, after restoring a backup). Nothing was changed.")
                    item.kind == "occurrence" || item.kind == "media" -> UiLanguage.t("a expedição deste item não existe mais. Restaure a expedição (ou o projeto) primeiro.", "this item's expedition no longer exists. Restore the expedition (or the project) first.")
                    item.kind == "session" -> UiLanguage.t("o projeto desta expedição não existe mais. Restaure o projeto primeiro.", "this expedition's project no longer exists. Restore the project first.")
                    else -> UiLanguage.t("o banco recusou a restauração (${error.message}). Nada foi alterado.", "the database refused the restore (${error.message}). Nothing was changed.")
                },
                error
            )
        }
        // As linhas ja voltaram: um arquivo que nao puder voltar nao desfaz a
        // restauracao (antes a excecao deixava o item na lixeira, e tentar de
        // novo dava conflito de identificador).
        val failed = destinations.count { (original, destination) ->
            runCatching { move(File(item.directory, "files/$original"), File(filesDir, destination)) }.isFailure
        }
        if (failed == 0) {
            item.directory.deleteRecursively()
        } else {
            // O item nao pode ser restaurado de novo; os arquivos que ficaram
            // saem junto com a lixeira antiga.
            File(item.directory, ITEM_FILE).delete()
        }
        return failed
    }

    /**
     * Pais que ficaram fora do item (a expedicao de uma ocorrencia, a
     * ocorrencia de uma midia), com a chave que os identifica de verdade.
     * Depois de restaurar um backup, o mesmo id pode ser de outra expedicao:
     * sem essa conferencia, a ocorrencia voltava presa a expedicao errada.
     */
    private fun parentsOf(db: androidx.sqlite.db.SupportSQLiteDatabase, tables: JSONObject): JSONArray {
        val result = JSONArray()
        val seen = mutableSetOf<String>()
        PARENT_LINKS.forEach { link ->
            val rows = tables.optJSONArray(link.child) ?: return@forEach
            val inside = (tables.optJSONArray(link.parent) ?: JSONArray()).let { parents ->
                (0 until parents.length()).map { parents.getJSONObject(it).optLong("id") }.toSet()
            }
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                if (row.isNull(link.column)) continue
                val id = row.optLong(link.column)
                if (id <= 0L || id in inside || !seen.add("${link.parent}:$id")) continue
                val value = db.query("SELECT `${link.key}` FROM `${link.parent}` WHERE id = ?", arrayOf<Any>(id)).use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
                } ?: continue
                result.put(JSONObject().put("table", link.parent).put("id", id).put("key", link.key).put("value", value))
            }
        }
        return result
    }

    private fun checkParents(db: androidx.sqlite.db.SupportSQLiteDatabase, parents: JSONArray, kind: String) {
        for (i in 0 until parents.length()) {
            val parent = parents.getJSONObject(i)
            val table = parent.getString("table")
            val key = parent.getString("key")
            val current = db.query("SELECT `$key` FROM `$table` WHERE id = ?", arrayOf<Any>(parent.getLong("id"))).use { cursor ->
                if (cursor.moveToFirst()) (if (cursor.isNull(0)) "" else cursor.getString(0)) else null
            }
            if (current == null) {
                throw IOException(
                    when (table) {
                        "projects" -> UiLanguage.t("o projeto deste item não existe mais. Restaure o projeto primeiro.", "this item's project no longer exists. Restore the project first.")
                        "sessions" -> UiLanguage.t("a expedição deste item não existe mais. Restaure a expedição (ou o projeto) primeiro.", "this item's expedition no longer exists. Restore the expedition (or the project) first.")
                        else -> UiLanguage.t("a ocorrência desta mídia não existe mais. Restaure a ocorrência primeiro.", "this media file's occurrence no longer exists. Restore the occurrence first.")
                    }
                )
            }
            if (current != parent.getString("value")) {
                val what = when (table) {
                    "projects" -> UiLanguage.t("o projeto deste item agora é outro", "this item's project is now a different one")
                    "sessions" -> UiLanguage.t("a expedição deste item agora é outra", "this item's expedition is now a different one")
                    else -> UiLanguage.t("a ocorrência desta mídia agora é outra", "this media file's occurrence is now a different one")
                }
                throw IOException(UiLanguage.t("$what (por exemplo, depois de restaurar um backup); restaurar prenderia o item ao registro errado. Nada foi alterado.", "$what (for example, after restoring a backup); restoring would attach the item to the wrong record. Nothing was changed."))
            }
        }
    }

    private fun freeRelativePath(path: String, reserved: Set<String>): String {
        if (!File(filesDir, path).exists() && path !in reserved) return path
        val name = path.substringAfterLast('/')
        val folder = path.substringBeforeLast('/', "")
        val stem = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var index = 2
        while (true) {
            val candidate = listOf(folder, "$stem-restaurado-$index$extension").filter { it.isNotEmpty() }.joinToString("/")
            if (!File(filesDir, candidate).exists() && candidate !in reserved) return candidate
            index++
        }
    }

    /**
     * Trilha que estava gravando quando foi apagada volta encerrada no ultimo
     * ponto. Voltando "ativa", o app retomava a gravacao nela e colava pontos
     * de hoje numa trilha de dias atras.
     */
    private fun closedTrack(row: JSONObject, tables: JSONObject): Map<String, Any?> {
        if (row.optLong("active") == 0L) return emptyMap()
        val trackId = row.optLong("id")
        val points = tables.optJSONArray("gps_track_points") ?: JSONArray()
        var last: Long? = null
        for (i in 0 until points.length()) {
            val point = points.getJSONObject(i)
            if (point.optLong("trackId") == trackId) last = maxOf(last ?: Long.MIN_VALUE, point.optLong("timestamp"))
        }
        val end = if (row.isNull("endTime")) last ?: row.optLong("startTime") else row.optLong("endTime")
        return mapOf("active" to 0L, "endTime" to end)
    }

    fun delete(item: TrashItem) {
        item.directory.deleteRecursively()
    }

    fun purgeOlderThan(days: Int = RETENTION_DAYS, now: Long = System.currentTimeMillis()) {
        val limit = now - days * 24L * 60 * 60 * 1000
        list().filter { it.deletedAt < limit }.forEach(::delete)
        // Sobras sem item.json (restauracao com arquivo que nao voltou, ou
        // exclusao interrompida antes de gravar o item).
        root.listFiles { file -> file.isDirectory && !File(file, ITEM_FILE).exists() && file.lastModified() < limit }
            .orEmpty().forEach { it.deleteRecursively() }
    }

    private fun header(dir: File): TrashItem {
        val json = JSONObject(File(dir, ITEM_FILE).readText())
        return TrashItem(
            id = dir.name,
            kind = json.optString("kind"),
            title = json.optString("title"),
            deletedAt = json.optLong("deletedAt"),
            occurrences = json.optInt("occurrences"),
            media = json.optInt("media"),
            directory = dir
        )
    }

    private fun relative(path: String): String? {
        if (path.isBlank()) return null
        val base = filesDir.canonicalFile
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        if (!file.path.startsWith(base.path + File.separator)) return null
        if (file.path.startsWith(File(base, TRASH_DIR).path + File.separator)) return null
        return file.path.removePrefix(base.path + File.separator).replace(File.separatorChar, '/')
    }

    private fun move(from: File, to: File) {
        if (!from.isFile) return
        to.parentFile?.mkdirs()
        if (!from.renameTo(to)) {
            from.copyTo(to, overwrite = true)
            from.delete()
        }
    }

    companion object {
        const val TRASH_DIR = "Lixeira"
        const val RETENTION_DAYS = 30
        private const val ITEM_FILE = "item.json"
        private val INSERT_ORDER = listOf("projects", "field_configs", "sessions", "occurrences", "media", "gps_tracks", "gps_track_points")

        private data class ParentLink(val child: String, val column: String, val parent: String, val key: String)

        /** Chave estavel de cada pai: eventID, occurrenceID ou a data de criacao do projeto. */
        private val PARENT_LINKS = listOf(
            ParentLink("sessions", "projectId", "projects", "createdAt"),
            ParentLink("occurrences", "projectId", "projects", "createdAt"),
            ParentLink("occurrences", "sessionId", "sessions", "eventId"),
            ParentLink("media", "projectId", "projects", "createdAt"),
            ParentLink("media", "sessionId", "sessions", "eventId"),
            ParentLink("media", "occurrenceId", "occurrences", "occurrenceId"),
            ParentLink("gps_tracks", "sessionId", "sessions", "eventId")
        )
    }
}
