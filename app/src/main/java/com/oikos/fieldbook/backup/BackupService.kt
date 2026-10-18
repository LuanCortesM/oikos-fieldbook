package com.oikos.fieldbook.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.oikos.fieldbook.BuildConfig
import com.oikos.fieldbook.OikosApplication
import com.oikos.fieldbook.data.OikosDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Cria e restaura backups ([BackupArchive]) no aparelho.
 *
 * A restauracao acontece em duas fases para nunca deixar os dados pela metade:
 * 1. [stageRestore] extrai o arquivo para uma area de preparo, confere o
 *    manifesto e corrige os caminhos das midias, sem tocar nos dados atuais;
 * 2. o app reinicia e [applyPendingRestore], chamado no Application.onCreate
 *    antes de o banco abrir, troca banco e midias de uma vez.
 * O banco anterior fica guardado em restore-safety/ ate a proxima restauracao.
 */
class BackupService(private val app: OikosApplication) {

    suspend fun create(target: Uri, onProgress: (done: Long, total: Long) -> Unit): BackupManifest = withContext(Dispatchers.IO) {
        val database = app.database.openHelper.writableDatabase
        // Leva o conteudo do WAL para o arquivo principal antes de copiar.
        database.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val counts = database.query(
            "SELECT (SELECT COUNT(*) FROM projects), (SELECT COUNT(*) FROM occurrences), (SELECT COUNT(*) FROM media)"
        ).use { cursor ->
            cursor.moveToFirst()
            Triple(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2))
        }
        val snapshot = File(app.cacheDir, "backup-snapshot.db")
        DATABASE_SUFFIXES.forEach { File(snapshot.path + it).delete() }
        // Banco e WAL copiados com a escrita bloqueada. Antes so o arquivo
        // principal era copiado: se o checkpoint acima nao tinha terminado (o
        // resultado nem era lido), as gravacoes mais recentes ficavam de fora.
        app.database.runInTransaction {
            val source = app.getDatabasePath(DATABASE_NAME)
            source.copyTo(snapshot, overwrite = true)
            File(source.path + "-wal").takeIf { it.isFile && it.length() > 0 }?.copyTo(File(snapshot.path + "-wal"), overwrite = true)
        }
        makeSelfContained(snapshot)
        val mediaRoot = mediaRoot(app)
        val mediaFiles = if (mediaRoot.exists()) mediaRoot.walkTopDown().filter { it.isFile }.toList() else emptyList()
        val manifest = BackupManifest(
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            databaseVersion = OikosDatabase.VERSION,
            createdAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            filesDir = app.filesDir.absolutePath,
            projects = counts.first,
            occurrences = counts.second,
            mediaFiles = mediaFiles.size,
            mediaBytes = mediaFiles.sumOf { it.length() }
        )
        try {
            val output = app.contentResolver.openOutputStream(target, "wt")
                ?: throw IOException("Não foi possível abrir o destino do backup.")
            output.use { BackupArchive.write(it, manifest, snapshot, mediaRoot, onProgress) }
        } finally {
            snapshot.delete()
        }
        manifest
    }

    /**
     * Copia sem WAL: o arquivo dentro do ZIP abre sozinho, sem -wal ao lado
     * (abrir a copia aplica o WAL copiado; mudar o journal grava tudo no
     * arquivo principal). E o banco e conferido antes de ir para o backup.
     */
    private fun makeSelfContained(snapshot: File) {
        SQLiteDatabase.openDatabase(snapshot.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
            checkIntegrity(db, "a cópia do banco para o backup")
        }
        File(snapshot.path + "-wal").delete()
        File(snapshot.path + "-shm").delete()
    }

    private fun checkIntegrity(db: SQLiteDatabase, what: String) {
        val result = db.rawQuery("PRAGMA integrity_check", null).use { if (it.moveToFirst()) it.getString(0) else "" }
        if (result != "ok") throw IOException("$what está corrompida ($result).")
    }

    suspend fun inspect(source: Uri): BackupManifest? = withContext(Dispatchers.IO) {
        runCatching { app.contentResolver.openInputStream(source)?.use { BackupArchive.readManifest(it) } }.getOrNull()
    }

    /** Fase 1: prepara a restauracao sem mexer nos dados atuais. */
    suspend fun stageRestore(source: Uri): BackupManifest = withContext(Dispatchers.IO) {
        val staging = File(app.filesDir, STAGING_DIR)
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            val stagedDatabase = File(staging, DATABASE_NAME)
            val stagedMedia = File(staging, MEDIA_DIR)
            val input = app.contentResolver.openInputStream(source) ?: throw IOException("Não foi possível ler o arquivo escolhido.")
            val manifest = input.use { BackupArchive.extract(it, stagedDatabase, stagedMedia) }
            if (manifest.databaseVersion > OikosDatabase.VERSION) {
                throw IOException("Este backup foi feito numa versão mais nova do Oikos. Atualize o app antes de restaurar.")
            }
            prepareStagedDatabase(stagedDatabase)
            rewriteMediaPaths(stagedDatabase, manifest.filesDir, app.filesDir.absolutePath)
            File(staging, PENDING_MARKER).writeText(BackupArchive.manifestJson(manifest))
            manifest
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    /**
     * Confere o banco do backup antes de trocar qualquer coisa: integridade e
     * versao lidas do proprio arquivo (nao so do manifesto). Trilha que estava
     * gravando quando o backup foi feito volta encerrada no ultimo ponto; ativa,
     * o app retomaria a gravacao nela.
     */
    private fun prepareStagedDatabase(databaseFile: File) {
        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            checkIntegrity(db, "O banco dentro do backup")
            if (db.version > OikosDatabase.VERSION) {
                throw IOException("Este backup foi feito numa versão mais nova do Oikos. Atualize o app antes de restaurar.")
            }
            db.execSQL(
                "UPDATE gps_tracks SET endTime = COALESCE(endTime, (SELECT MAX(timestamp) FROM gps_track_points p WHERE p.trackId = gps_tracks.id), startTime), " +
                    "active = 0 WHERE active = 1"
            )
        }
    }

    /**
     * Chamado antes de reiniciar o app para restaurar: passa o WAL para o
     * arquivo principal, para que a copia de seguranca leve tudo.
     */
    fun checkpointBeforeRestart() {
        runCatching { app.database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() } }
    }

    private fun rewriteMediaPaths(databaseFile: File, oldFilesDir: String, newFilesDir: String) {
        if (oldFilesDir.trimEnd('/') == newFilesDir.trimEnd('/')) return
        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val paths = mutableListOf<Pair<Long, String>>()
            db.rawQuery("SELECT id, filePath FROM media", null).use { cursor ->
                while (cursor.moveToNext()) paths += cursor.getLong(0) to cursor.getString(1)
            }
            db.beginTransaction()
            try {
                paths.forEach { (id, path) ->
                    val rewritten = BackupArchive.rewritePath(path, oldFilesDir, newFilesDir)
                    if (rewritten != path) db.execSQL("UPDATE media SET filePath = ? WHERE id = ?", arrayOf<Any>(rewritten, id))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        }
    }

    companion object {
        const val DATABASE_NAME = "oikos_fieldbook.db"
        private const val MEDIA_DIR = "Projetos"
        private const val STAGING_DIR = "restore-staging"
        private const val SAFETY_DIR = "restore-safety"
        private const val PENDING_MARKER = "pending.json"

        fun mediaRoot(context: Context): File = File(context.filesDir, MEDIA_DIR)

        private val DATABASE_SUFFIXES = listOf("", "-wal", "-shm", "-journal")

        /**
         * Fase 2, no inicio do processo e antes de qualquer acesso ao banco:
         * troca banco e midias pelos preparados.
         *
         * A copia de seguranca leva banco E WAL (antes so o arquivo principal, e
         * as gravacoes ainda no WAL se perdiam num retorno), e e feita numa pasta
         * nova: so substitui a anterior se terminar inteira. Copia parcial (disco
         * cheio) cancela a restauracao sem tocar no banco atual.
         */
        fun applyPendingRestore(context: Context): RestoreOutcome {
            val staging = File(context.filesDir, STAGING_DIR)
            val marker = File(staging, PENDING_MARKER)
            if (!marker.exists()) return RestoreOutcome.None
            val stagedDatabase = File(staging, DATABASE_NAME)
            val stagedMedia = File(staging, MEDIA_DIR)
            val databaseFile = context.getDatabasePath(DATABASE_NAME)
            val databaseFiles = DATABASE_SUFFIXES.map { File(databaseFile.path + it) }
            val safety = File(context.filesDir, SAFETY_DIR)
            val mediaRoot = mediaRoot(context)
            val previousMedia = File(context.filesDir, "$MEDIA_DIR.antes-da-restauracao")

            if (databaseFile.exists()) {
                val newSafety = File(context.filesDir, "$SAFETY_DIR.nova").apply { deleteRecursively(); mkdirs() }
                try {
                    databaseFiles.filter { it.isFile }.forEach { it.copyTo(File(newSafety, it.name), overwrite = true) }
                } catch (error: Throwable) {
                    newSafety.deleteRecursively()
                    staging.deleteRecursively()
                    return RestoreOutcome.Failed("não foi possível guardar uma cópia do banco atual (${error.message ?: error.javaClass.simpleName}). Nada foi alterado.")
                }
                safety.deleteRecursively()
                if (!newSafety.renameTo(safety)) {
                    newSafety.deleteRecursively()
                    staging.deleteRecursively()
                    return RestoreOutcome.Failed("não foi possível guardar uma cópia do banco atual. Nada foi alterado.")
                }
            }
            // Sem banco atual (nova tentativa depois de uma interrupcao): a copia
            // de seguranca da tentativa anterior e mantida.
            return try {
                databaseFiles.forEach { it.delete() }
                databaseFile.parentFile?.mkdirs()
                stagedDatabase.copyTo(databaseFile, overwrite = true)
                previousMedia.deleteRecursively()
                if (mediaRoot.exists() && !mediaRoot.renameTo(previousMedia)) throw IOException("não foi possível mover as mídias atuais")
                if (stagedMedia.exists()) {
                    if (!stagedMedia.renameTo(mediaRoot)) stagedMedia.copyRecursively(mediaRoot, overwrite = true)
                } else {
                    mediaRoot.mkdirs()
                }
                previousMedia.deleteRecursively()
                staging.deleteRecursively()
                RestoreOutcome.Restored
            } catch (error: Throwable) {
                // Volta ao estado anterior: banco e WAL guardados, midias movidas.
                runCatching {
                    databaseFiles.forEach { it.delete() }
                    safety.listFiles()?.forEach { saved -> saved.copyTo(File(databaseFile.parentFile, saved.name), overwrite = true) }
                }
                runCatching {
                    if (previousMedia.exists()) {
                        mediaRoot.deleteRecursively()
                        previousMedia.renameTo(mediaRoot)
                    }
                }
                staging.deleteRecursively()
                RestoreOutcome.Failed("${error.message ?: error.javaClass.simpleName}. Os dados anteriores foram mantidos.")
            }
        }
    }
}

/** Resultado da restauracao aplicada ao abrir o app. */
sealed interface RestoreOutcome {
    data object None : RestoreOutcome
    data object Restored : RestoreOutcome
    data class Failed(val reason: String) : RestoreOutcome
}
