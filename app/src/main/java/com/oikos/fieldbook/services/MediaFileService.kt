package com.oikos.fieldbook.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import org.osmdroid.config.Configuration
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale

/** Metadados lidos do EXIF da foto original, antes de qualquer recompressao. */
data class PhotoMetadata(
    val capturedAt: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Fabricante e modelo da camera, do EXIF. */
    val captureDevice: String = ""
)

class MediaFileService(private val context: Context) {
    init {
        ensureMapCacheConfigured()
    }

    private fun projectsRoot(): File = File(context.filesDir, "Projetos")

    /**
     * Pasta da midia pelo identificador interno do projeto e da expedicao.
     *
     * Ate a v0.2.0 a pasta vinha do NOME: duas expedicoes sem nome no mesmo dia
     * ("Expedicao 2026-10-17") dividiam a pasta, e apagar uma apagava a pasta
     * inteira com as midias da outra. O identificador do Room nunca se repete
     * (AUTOINCREMENT), entao cada expedicao tem a sua.
     */
    fun mediaDir(projectId: Long, sessionId: Long, type: String): File {
        val dir = File(sessionDir(projectId, sessionId), if (type == "audio") "Audios" else "Fotos")
        dir.mkdirs()
        return dir
    }

    private fun projectDir(projectId: Long): File = File(projectsRoot(), "PROJ-$projectId")
    private fun sessionDir(projectId: Long, sessionId: Long): File = File(projectDir(projectId), "EXP-$sessionId")

    fun createImageFile(projectId: Long, sessionId: Long, baseName: String? = null): File =
        uniqueFile(mediaDir(projectId, sessionId, "photo"), safeFileBase(baseName ?: "FOTO_${System.currentTimeMillis()}"), "jpg")

    /** Gravacao do app: sempre WAV PCM, sem compressao (o que a bioacustica usa). */
    fun createAudioFile(projectId: Long, sessionId: Long, baseName: String? = null): File {
        return uniqueFile(mediaDir(projectId, sessionId, "audio"), safeFileBase(baseName ?: "AUDIO_${System.currentTimeMillis()}"), "wav")
    }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * Copia um arquivo escolhido pelo usuario. Chamar fora da main thread.
     *
     * A extensao vem do conteudo do arquivo, nao do tipo informado pelo outro
     * app: antes todo audio que nao fosse MP3 virava ".wav" (um M4A ou OGG com
     * extensao errada nao abre em programas de analise).
     */
    fun importUri(uri: Uri, projectId: Long, sessionId: Long, type: String, mimeType: String?, baseName: String? = null): ImportedFile {
        val fallback = if (type == "audio") "AUDIO_${System.currentTimeMillis()}" else "FOTO_${System.currentTimeMillis()}"
        val base = safeFileBase(baseName ?: fallback)
        val dir = mediaDir(projectId, sessionId, type)
        val staging = uniqueFile(dir, base, "importando")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("o outro app não entregou o arquivo")
            input.use { source -> FileOutputStream(staging).use { output -> source.copyTo(output) } }
            if (staging.length() == 0L) throw java.io.IOException("o arquivo escolhido está vazio")
            val detected = MediaKind.detect(staging)
            val mime = detected?.mime ?: mimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
                ?: if (type == "audio") "audio/*" else "image/*"
            val extension = detected?.extension
                ?: mimeType?.let { android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                ?: if (type == "audio") "audio" else "img"
            val file = uniqueFile(dir, base, extension)
            if (!staging.renameTo(file)) {
                staging.copyTo(file, overwrite = true)
                staging.delete()
            }
            return ImportedFile(file, mime)
        } catch (error: Throwable) {
            // Copia pela metade (disco cheio, arquivo na nuvem sem sinal) nao fica para tras.
            staging.delete()
            throw error
        }
    }

    /**
     * Ajusta a extensao ao conteudo: foto HEIC ou WebP sai da reducao como
     * JPEG, e o nome precisa acompanhar.
     */
    fun matchExtensionToContent(file: File, fallbackMime: String): ImportedFile {
        val kind = MediaKind.detect(file) ?: return ImportedFile(file, fallbackMime)
        if (file.extension.equals(kind.extension, ignoreCase = true)) return ImportedFile(file, kind.mime)
        val target = uniqueFile(file.parentFile ?: return ImportedFile(file, kind.mime), file.nameWithoutExtension, kind.extension)
        if (!file.renameTo(target)) {
            file.copyTo(target, overwrite = true)
            file.delete()
        }
        return ImportedFile(target, kind.mime)
    }

    /** Apaga copias de importacao interrompidas (app fechado no meio). */
    fun cleanupInterruptedImports(olderThanMillis: Long = 24 * 60 * 60 * 1000L) {
        val limit = System.currentTimeMillis() - olderThanMillis
        projectsRoot().takeIf { it.isDirectory }?.walkTopDown()
            ?.filter { it.isFile && it.extension == "importando" && it.lastModified() < limit }
            ?.forEach { it.delete() }
    }

    /** Le data/hora e GPS do EXIF. Deve ser chamada ANTES de [compressImage]. */
    /**
     * [fileTimeFallback]: sem data no EXIF, usar a hora do arquivo. Vale para a
     * foto que a camera acabou de gravar; numa copia importada, a hora do
     * arquivo e a da importacao, e a data fica desconhecida.
     */
    fun readPhotoMetadata(file: File, fileTimeFallback: Boolean = true): PhotoMetadata {
        if (!file.exists()) return PhotoMetadata()
        return runCatching {
            val exif = ExifInterface(file.absolutePath)
            val latLong = exif.latLong
            val capturedAt = listOf(
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_DATETIME
            ).firstNotNullOfOrNull { tag ->
                exif.getAttribute(tag)?.let { value ->
                    runCatching { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(value)?.time }.getOrNull()
                }
            }
            val device = listOfNotNull(exif.getAttribute(ExifInterface.TAG_MAKE), exif.getAttribute(ExifInterface.TAG_MODEL))
                .map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" ")
            PhotoMetadata(
                capturedAt = capturedAt ?: file.lastModified().takeIf { it > 0L && fileTimeFallback },
                latitude = latLong?.getOrNull(0),
                longitude = latLong?.getOrNull(1),
                captureDevice = device
            )
        }.getOrDefault(PhotoMetadata(capturedAt = file.lastModified().takeIf { it > 0L && fileTimeFallback }))
    }

    /**
     * Reduz a foto ao tamanho da qualidade escolhida. Chamar fora da main thread.
     *
     * A recompressao pelo Bitmap descarta o EXIF; os campos que importam para a
     * procedencia da foto (data/hora, GPS, camera, exposicao) sao copiados do
     * original para o arquivo novo. A orientacao vai como normal porque os
     * pixels ja foram girados.
     */
    fun compressImage(file: File, quality: String) {
        val targetQuality = when (quality) {
            "low" -> 55
            "high" -> 90
            else -> 75
        }
        val maxDimension = when (quality) {
            "low" -> 1600
            "high" -> 4096
            else -> 2560
        }
        val bounds = readImageBounds(file) ?: return
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension, maxDimension)
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return
        val originalExif = runCatching { ExifInterface(file.absolutePath) }.getOrNull()
        val oriented = correctExifOrientation(originalExif, decoded)
        if (oriented !== decoded) decoded.recycle()
        val scaled = scaleDownIfNeeded(oriented, maxDimension)
        if (scaled !== oriented) oriented.recycle()
        val isPng = file.extension.equals("png", ignoreCase = true)
        val format = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            val compressed = FileOutputStream(temp).use { output -> scaled.compress(format, targetQuality, output) }
            if (compressed && temp.length() > 0L) {
                if (!isPng && originalExif != null) copyExif(originalExif, temp)
                // rename troca o arquivo de uma vez. copyTo(overwrite) apagava o
                // original antes de copiar: uma falha no meio deixava a foto cortada.
                if (!temp.renameTo(file)) temp.copyTo(file, overwrite = true)
            }
        } finally {
            scaled.recycle()
            temp.delete()
        }
    }

    private fun copyExif(source: ExifInterface, target: File) {
        runCatching {
            val destination = ExifInterface(target.absolutePath)
            PRESERVED_EXIF_TAGS.forEach { tag -> source.getAttribute(tag)?.let { destination.setAttribute(tag, it) } }
            destination.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            destination.saveAttributes()
        }
    }

    private fun readImageBounds(file: File): BitmapFactory.Options? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return bounds.takeIf { it.outWidth > 0 && it.outHeight > 0 }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var sampleSize = 1
        val halfWidth = width / 2
        val halfHeight = height / 2
        while ((halfWidth / sampleSize) >= reqWidth || (halfHeight / sampleSize) >= reqHeight) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun correctExifOrientation(exif: ExifInterface?, bitmap: Bitmap): Bitmap {
        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longestSide = maxOf(bitmap.width, bitmap.height)
        if (longestSide <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longestSide.toFloat()
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    /**
     * Arquivo de destino de uma exportacao. As exportacoes anteriores sao
     * apagadas: ja foram entregues ao app escolhido e so ocupariam espaco.
     */
    /**
     * Arquivo novo de exportacao. Antes cada exportacao apagava as anteriores:
     * um CSV anexado a um e-mail ainda nao enviado sumia quando o XLSX era
     * gerado em seguida. Agora so saem as com mais de um dia, e o mesmo nome
     * no mesmo dia ganha numero.
     */
    fun newExportFile(fileName: String): File {
        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        val limit = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        dir.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.deleteRecursively() }
        val stem = fileName.substringBeforeLast('.')
        val extension = fileName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var candidate = File(dir, fileName)
        var index = 2
        while (!candidate.createNewFile()) {
            candidate = File(dir, "$stem-$index$extension")
            index++
        }
        return candidate
    }

    fun cacheSizeBytes(): Long = context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun clearCache(): Long {
        val before = cacheSizeBytes()
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.exists()) file.deleteRecursively()
        }
        return before - cacheSizeBytes()
    }

    fun mapCacheSizeBytes(): Long {
        ensureMapCacheConfigured()
        val root = mapCacheRoot()
        return if (root.exists()) root.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
    }

    fun clearMapCache(): Long {
        ensureMapCacheConfigured()
        val before = mapCacheSizeBytes()
        val root = mapCacheRoot()
        root.listFiles()?.forEach { file ->
            if (file.exists()) file.deleteRecursively()
        }
        root.mkdirs()
        return before - mapCacheSizeBytes()
    }

    fun deleteIfManaged(path: String) {
        val root = projectsRoot().canonicalFile
        val target = runCatching { File(path).canonicalFile }.getOrNull() ?: return
        if (target.path.startsWith(root.path + File.separator) && target.isFile) {
            target.delete()
        }
    }

    fun projectFilesSizeBytes(): Long {
        val root = projectsRoot()
        return if (root.exists()) root.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
    }

    /**
     * Remove a pasta do projeto (pelo identificador, que e exclusivo dele) e
     * depois as pastas que ficaram vazias. Pastas antigas, nomeadas pelo nome
     * do projeto, nunca sao apagadas em bloco: os arquivos delas sao removidos
     * um a um pelo chamador, a partir da lista de midias do banco.
     */
    fun deleteProjectFiles(projectId: Long) {
        deleteInsideRoot(projectDir(projectId))
        pruneEmptyDirs()
    }

    fun deleteSessionFiles(projectId: Long, sessionId: Long) {
        deleteInsideRoot(sessionDir(projectId, sessionId))
        pruneEmptyDirs()
    }

    private fun deleteInsideRoot(dir: File) {
        val root = projectsRoot().canonicalFile
        val target = runCatching { dir.canonicalFile }.getOrNull() ?: return
        if (target.path.startsWith(root.path + File.separator) && target.exists()) target.deleteRecursively()
    }

    /** Apaga diretorios vazios dentro de Projetos/, de baixo para cima. */
    fun pruneEmptyDirs() {
        val root = projectsRoot()
        if (!root.exists()) return
        root.walkBottomUp()
            .filter { it.isDirectory && it != root }
            .forEach { dir -> if (dir.listFiles().isNullOrEmpty()) dir.delete() }
    }

    fun databaseSizeBytes(): Long = context.getDatabasePath("oikos_fieldbook.db").length()

    /** Listas de especies (banco proprio) e mapas offline: grandes, mas baixaveis de novo. */
    fun checklistsSizeBytes(): Long = listOf("", "-wal").sumOf { context.getDatabasePath("oikos_checklists.db$it").length() }

    fun offlineMapsSizeBytes(): Long = File(context.filesDir, "mapas").walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun ensureMapCacheConfigured() {
        val root = mapCacheRoot()
        root.mkdirs()
        Configuration.getInstance().osmdroidBasePath = root
        Configuration.getInstance().osmdroidTileCache = File(root, "tiles").apply { mkdirs() }
    }

    private fun mapCacheRoot(): File = File(context.cacheDir, "osmdroid")

    private fun safeFileBase(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .uppercase()
            .replace("[^A-Z0-9_-]+".toRegex(), "_")
            .trim('_')
            .ifBlank { "OIKOS_MEDIA" }

    /**
     * Nome livre, ja reservado no disco (arquivo vazio criado de forma
     * atomica). Antes o nome era so conferido: uma foto importada e outra
     * tirada ao mesmo tempo, com o mesmo nome base, recebiam o mesmo caminho
     * e uma sobrescrevia a outra.
     */
    private fun uniqueFile(dir: File, baseName: String, extension: String): File {
        dir.mkdirs()
        var candidate = File(dir, "$baseName.$extension")
        var suffix = 2
        while (!candidate.createNewFile()) {
            candidate = File(dir, "${baseName}_${suffix.toString().padStart(2, '0')}.$extension")
            suffix += 1
        }
        return candidate
    }

    companion object {
        private val PRESERVED_EXIF_TAGS = listOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_USER_COMMENT
        )
    }
}
