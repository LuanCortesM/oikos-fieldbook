package com.oikos.fieldbook.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.oikos.fieldbook.export.ExportResult
import org.osmdroid.config.Configuration
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer

class MediaFileService(private val context: Context) {
    init {
        ensureMapCacheConfigured()
    }

    fun mediaDir(projectName: String, sessionName: String, type: String): File {
        val root = File(context.filesDir, "Projetos")
        val dir = File(File(File(root, safe(projectName)), "Expedicoes/${safe(sessionName)}"), if (type == "audio") "Audios" else "Fotos")
        dir.mkdirs()
        return dir
    }

    fun createImageFile(projectName: String, sessionName: String, baseName: String? = null): File {
        val file = uniqueFile(mediaDir(projectName, sessionName, "photo"), safeFileBase(baseName ?: "FOTO_${System.currentTimeMillis()}"), "jpg")
        file.parentFile?.mkdirs()
        return file
    }

    fun createAudioFile(projectName: String, sessionName: String, audioFormat: String = "wav", baseName: String? = null): File {
        val extension = if (audioFormat == "mp3") "mp3" else "wav"
        val file = uniqueFile(mediaDir(projectName, sessionName, "audio"), safeFileBase(baseName ?: "AUDIO_${System.currentTimeMillis()}"), extension)
        file.parentFile?.mkdirs()
        return file
    }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun importUri(uri: Uri, projectName: String, sessionName: String, type: String, mimeType: String?, baseName: String? = null): File {
        val extension = when {
            mimeType == "audio/mpeg" -> ".mp3"
            mimeType == "audio/wav" || mimeType == "audio/x-wav" -> ".wav"
            mimeType?.contains("audio") == true -> ".wav"
            mimeType?.contains("png") == true -> ".png"
            else -> ".jpg"
        }
        val fallback = if (type == "audio") "AUDIO_${System.currentTimeMillis()}" else "FOTO_${System.currentTimeMillis()}"
        val file = uniqueFile(mediaDir(projectName, sessionName, type), safeFileBase(baseName ?: fallback), extension.removePrefix("."))
        context.contentResolver.openInputStream(uri).use { input ->
            FileOutputStream(file).use { output -> input?.copyTo(output) }
        }
        if (type == "photo") compressImage(file, "medium")
        return file
    }

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
        val oriented = correctExifOrientation(file, decoded)
        if (oriented !== decoded) decoded.recycle()
        val scaled = scaleDownIfNeeded(oriented, maxDimension)
        if (scaled !== oriented) oriented.recycle()
        val format = if (file.extension.equals("png", ignoreCase = true)) {
            Bitmap.CompressFormat.PNG
        } else {
            Bitmap.CompressFormat.JPEG
        }
        val temp = File(file.parentFile, "${file.name}.tmp")
        val compressed = FileOutputStream(temp).use { output ->
            scaled.compress(format, targetQuality, output)
        }
        scaled.recycle()
        if (compressed && temp.length() > 0L) {
            temp.copyTo(file, overwrite = true)
        }
        temp.delete()
    }

    private fun readImageBounds(file: File): BitmapFactory.Options? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return bounds.takeIf { it.outWidth > 0 && it.outHeight > 0 }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var sampleSize = 1
        var halfWidth = width / 2
        var halfHeight = height / 2
        while ((halfWidth / sampleSize) >= reqWidth || (halfHeight / sampleSize) >= reqHeight) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun correctExifOrientation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
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

    fun writeExport(result: ExportResult): File {
        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        val file = File(dir, result.fileName)
        file.writeBytes(result.bytes)
        return file
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
        val root = File(context.filesDir, "Projetos").canonicalFile
        val target = runCatching { File(path).canonicalFile }.getOrNull() ?: return
        if (target.path.startsWith(root.path) && target.exists()) {
            target.delete()
        }
    }

    fun projectFilesSizeBytes(): Long {
        val root = File(context.filesDir, "Projetos")
        return if (root.exists()) root.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
    }

    fun deleteProjectFiles(projectName: String) {
        val root = File(context.filesDir, "Projetos").canonicalFile
        val target = runCatching { File(root, safe(projectName)).canonicalFile }.getOrNull() ?: return
        if (target.path.startsWith(root.path) && target.exists()) target.deleteRecursively()
    }

    fun deleteSessionFiles(projectName: String, sessionName: String) {
        val root = File(context.filesDir, "Projetos").canonicalFile
        val target = runCatching {
            File(File(File(root, safe(projectName)), "Expedicoes"), safe(sessionName)).canonicalFile
        }.getOrNull() ?: return
        if (target.path.startsWith(root.path) && target.exists()) target.deleteRecursively()
    }

    fun databaseSizeBytes(): Long = context.getDatabasePath("oikos_fieldbook.db").length()

    private fun ensureMapCacheConfigured() {
        val root = mapCacheRoot()
        root.mkdirs()
        Configuration.getInstance().osmdroidBasePath = root
        Configuration.getInstance().osmdroidTileCache = File(root, "tiles").apply { mkdirs() }
    }

    private fun mapCacheRoot(): File = File(context.cacheDir, "osmdroid")

    private fun safe(value: String): String =
        value.lowercase().replace("[^a-z0-9_-]+".toRegex(), "_").trim('_').ifBlank { "sem_nome" }

    private fun safeFileBase(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .uppercase()
            .replace("[^A-Z0-9_-]+".toRegex(), "_")
            .trim('_')
            .ifBlank { "OIKOS_MEDIA" }

    private fun uniqueFile(dir: File, baseName: String, extension: String): File {
        dir.mkdirs()
        var candidate = File(dir, "$baseName.$extension")
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(dir, "${baseName}_${suffix.toString().padStart(2, '0')}.$extension")
            suffix += 1
        }
        return candidate
    }
}

