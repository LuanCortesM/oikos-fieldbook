package com.oikos.fieldbook.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Resumo gravado dentro do backup, lido antes de restaurar. */
data class BackupManifest(
    val format: Int = BackupArchive.FORMAT_VERSION,
    val appVersion: String,
    val databaseVersion: Int,
    val createdAt: String,
    /** filesDir do aparelho de origem: os caminhos das midias no banco comecam por ele. */
    val filesDir: String,
    val projects: Int,
    val occurrences: Int,
    val mediaFiles: Int,
    val mediaBytes: Long
)

/**
 * Backup completo do Oikos em um unico arquivo ZIP: banco, midias e manifesto.
 *
 * Perda de dados foi a queixa mais espalhada entre usuarios de apps de campo
 * (23 relatos em 7 fontes; docs/pesquisa-apps-semelhantes.md), muitas vezes por
 * troca, perda ou reinstalacao do aparelho. Sem servidor, o caminho e o
 * pesquisador guardar este arquivo fora do celular.
 *
 * Estrutura:
 *   manifest.json
 *   database/oikos_fieldbook.db
 *   media/<caminho relativo dentro de Projetos/>
 *
 * Nada aqui depende de Android, para poder ser testado na JVM.
 */
object BackupArchive {
    const val FORMAT_VERSION = 1
    const val MANIFEST = "manifest.json"
    const val DATABASE = "database/oikos_fieldbook.db"
    const val MEDIA_PREFIX = "media/"

    fun write(
        output: OutputStream,
        manifest: BackupManifest,
        databaseFile: File,
        mediaRoot: File,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }
    ) {
        val mediaFiles = if (mediaRoot.exists()) mediaRoot.walkTopDown().filter { it.isFile }.toList() else emptyList()
        val total = databaseFile.length() + mediaFiles.sumOf { it.length() }
        var done = 0L
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifestJson(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(DATABASE))
            databaseFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            done += databaseFile.length()
            onProgress(done, total)

            mediaFiles.forEach { file ->
                val relative = file.relativeTo(mediaRoot).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(MEDIA_PREFIX + relative))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                done += file.length()
                onProgress(done, total)
            }
        }
    }

    /** Le so o manifesto, para mostrar o resumo antes de restaurar. */
    fun readManifest(input: InputStream): BackupManifest? {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return null
                if (entry.name == MANIFEST) return parseManifest(zip.readBytes().toString(Charsets.UTF_8))
            }
        }
    }

    /**
     * Extrai o banco para [databaseTarget] e as midias para dentro de
     * [mediaRoot]. Entradas que tentam escapar da pasta ("../") sao recusadas.
     * Devolve o manifesto lido; lanca IOException se o arquivo estiver incompleto.
     */
    fun extract(input: InputStream, databaseTarget: File, mediaRoot: File): BackupManifest {
        var manifest: BackupManifest? = null
        var databaseFound = false
        val root = mediaRoot.canonicalFile
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.isDirectory -> Unit
                    entry.name == MANIFEST -> manifest = parseManifest(zip.readBytes().toString(Charsets.UTF_8))
                    entry.name == DATABASE -> {
                        databaseTarget.parentFile?.mkdirs()
                        FileOutputStream(databaseTarget).use { zip.copyTo(it) }
                        databaseFound = true
                    }
                    entry.name.startsWith(MEDIA_PREFIX) -> {
                        val target = File(root, entry.name.removePrefix(MEDIA_PREFIX)).canonicalFile
                        if (!target.path.startsWith(root.path + File.separator)) {
                            throw IOException("Entrada inválida no backup: ${entry.name}")
                        }
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { zip.copyTo(it) }
                    }
                }
            }
        }
        if (!databaseFound) throw IOException("O arquivo não contém o banco de dados do Oikos.")
        return manifest ?: throw IOException("O arquivo não contém o manifesto do backup.")
    }

    /**
     * O banco guarda o caminho absoluto de cada midia. Em outro aparelho (ou
     * outro usuario Android) o filesDir muda; sem reescrever, as midias
     * restauradas apareceriam como "arquivo nao encontrado".
     */
    fun rewritePath(path: String, oldFilesDir: String, newFilesDir: String): String {
        val oldRoot = oldFilesDir.trimEnd('/')
        val newRoot = newFilesDir.trimEnd('/')
        return if (oldRoot.isNotEmpty() && (path == oldRoot || path.startsWith("$oldRoot/"))) newRoot + path.substring(oldRoot.length) else path
    }

    fun manifestJson(m: BackupManifest): String = buildJsonObject {
        put("app", "Oikos FieldBook")
        put("format", m.format)
        put("appVersion", m.appVersion)
        put("databaseVersion", m.databaseVersion)
        put("createdAt", m.createdAt)
        put("filesDir", m.filesDir)
        put("projects", m.projects)
        put("occurrences", m.occurrences)
        put("mediaFiles", m.mediaFiles)
        put("mediaBytes", m.mediaBytes)
    }.toString()

    fun parseManifest(text: String): BackupManifest? = runCatching {
        val o = Json.parseToJsonElement(text).jsonObject
        BackupManifest(
            format = o.getValue("format").jsonPrimitive.int,
            appVersion = o.getValue("appVersion").jsonPrimitive.content,
            databaseVersion = o.getValue("databaseVersion").jsonPrimitive.int,
            createdAt = o.getValue("createdAt").jsonPrimitive.content,
            filesDir = o.getValue("filesDir").jsonPrimitive.content,
            projects = o.getValue("projects").jsonPrimitive.int,
            occurrences = o.getValue("occurrences").jsonPrimitive.int,
            mediaFiles = o.getValue("mediaFiles").jsonPrimitive.int,
            mediaBytes = o.getValue("mediaBytes").jsonPrimitive.long
        )
    }.getOrNull()
}
