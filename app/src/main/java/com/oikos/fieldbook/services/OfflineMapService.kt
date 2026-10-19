package com.oikos.fieldbook.services

import com.oikos.fieldbook.util.UiLanguage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import com.oikos.fieldbook.util.allocatableBytes
import java.io.File
import java.io.IOException

/** Mapa offline instalado (MBTiles raster). */
data class OfflineMap(
    val file: File,
    val name: String,
    val format: String,
    val minZoom: Int,
    val maxZoom: Int,
    /** oeste, sul, leste, norte em graus; nulo se o arquivo nao informa. */
    val bounds: List<Double>?,
    val attribution: String
) {
    val sizeBytes: Long get() = file.length()
    val centerLatitude: Double? get() = bounds?.let { (it[1] + it[3]) / 2 }
    val centerLongitude: Double? get() = bounds?.let { (it[0] + it[2]) / 2 }
}

/**
 * Mapas offline em MBTiles, o formato que o QGIS gera a partir de qualquer
 * camada (imagem de satelite, carta topografica, mapa de vegetacao) pela
 * ferramenta "Gerar tiles XYZ (MBTiles)". Sem servidor: o pesquisador prepara
 * o mapa da area de estudo no computador e copia o arquivo para o celular.
 * So mapas raster (PNG, JPG, WebP): tiles vetoriais (pbf) o osmdroid nao desenha.
 */
class OfflineMapService(private val context: Context) {

    private val directory: File get() = File(context.filesDir, "mapas").apply { mkdirs() }

    fun installed(): List<OfflineMap> =
        directory.listFiles { file -> file.extension.equals("mbtiles", ignoreCase = true) }
            .orEmpty()
            .mapNotNull { runCatching { read(it) }.getOrNull() }
            .sortedBy { it.name.lowercase() }

    fun byFileName(fileName: String): OfflineMap? =
        File(directory, fileName).takeIf { it.exists() }?.let { runCatching { read(it) }.getOrNull() }

    /** Copia o arquivo escolhido, confere se e um MBTiles raster e devolve o mapa instalado. */
    fun import(uri: Uri, onProgress: (copied: Long, total: Long) -> Unit): OfflineMap {
        val displayName = displayName(uri) ?: "mapa.mbtiles"
        val baseName = displayName.substringBeforeLast('.').replace("[^A-Za-z0-9._-]+".toRegex(), "_").ifBlank { "mapa" }
        val total = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: -1L
        val free = allocatableBytes(context, context.filesDir)
        if (total > 0 && total + 50L * 1024 * 1024 > free) {
            throw IOException(UiLanguage.t("espaço insuficiente: o mapa tem ${total / (1024 * 1024)} MB e há ${free / (1024 * 1024)} MB livres", "not enough space: the map is ${total / (1024 * 1024)} MB and only ${free / (1024 * 1024)} MB are free", "espacio insuficiente: el mapa ocupa ${total / (1024 * 1024)} MB y hay ${free / (1024 * 1024)} MB libres"))
        }
        val temporary = File(directory, "$baseName.importando")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException(UiLanguage.t("não foi possível ler o arquivo escolhido", "could not read the chosen file", "no se pudo leer el archivo elegido"))
            input.use { source ->
                temporary.outputStream().use { target ->
                    val buffer = ByteArray(256 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        target.write(buffer, 0, read)
                        copied += read
                        onProgress(copied, total)
                    }
                }
            }
            val map = read(temporary)
            if (map.format.equals("pbf", ignoreCase = true) || map.format.equals("mvt", ignoreCase = true)) {
                throw IOException(UiLanguage.t("o arquivo tem tiles vetoriais (pbf); use um MBTiles raster (PNG ou JPG), como o que o QGIS gera", "the file has vector tiles (pbf); use a raster MBTiles (PNG or JPG), like the one QGIS generates", "el archivo tiene teselas vectoriales (pbf); usa un MBTiles ráster (PNG o JPG), como el que genera QGIS"))
            }
            var target = File(directory, "$baseName.mbtiles")
            var suffix = 2
            while (target.exists()) target = File(directory, "$baseName-${suffix++}.mbtiles")
            if (!temporary.renameTo(target)) throw IOException(UiLanguage.t("não foi possível guardar o mapa", "could not save the map", "no se pudo guardar el mapa"))
            return read(target)
        } finally {
            temporary.delete()
        }
    }

    fun remove(map: OfflineMap) {
        map.file.delete()
        File(map.file.path + "-journal").delete()
    }

    private fun read(file: File): OfflineMap {
        val db = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        } catch (error: Exception) {
            throw IOException(UiLanguage.t("o arquivo não é um MBTiles (banco SQLite)", "the file is not an MBTiles (SQLite database)", "el archivo no es un MBTiles (base de datos SQLite)"), error)
        }
        return db.use {
            val metadata = mutableMapOf<String, String>()
            runCatching {
                it.rawQuery("SELECT name, value FROM metadata", null).use { cursor ->
                    while (cursor.moveToNext()) metadata[cursor.getString(0).lowercase()] = cursor.getString(1).orEmpty()
                }
            }
            val zooms = runCatching {
                it.rawQuery("SELECT MIN(zoom_level), MAX(zoom_level), COUNT(*) FROM tiles", null).use { cursor ->
                    cursor.moveToFirst()
                    Triple(cursor.getInt(0), cursor.getInt(1), cursor.getLong(2))
                }
            }.getOrElse { throw IOException(UiLanguage.t("o arquivo não tem a tabela de tiles de um MBTiles", "the file does not have the tiles table of an MBTiles", "el archivo no tiene la tabla de teselas de un MBTiles")) }
            if (zooms.third == 0L) throw IOException(UiLanguage.t("o MBTiles não tem nenhum tile", "the MBTiles has no tiles", "el MBTiles no tiene ninguna tesela"))
            val bounds = metadata["bounds"]?.split(',')?.mapNotNull { value -> value.trim().toDoubleOrNull() }?.takeIf { b -> b.size == 4 }
            OfflineMap(
                file = file,
                name = metadata["name"]?.takeIf { name -> name.isNotBlank() } ?: file.nameWithoutExtension,
                format = metadata["format"].orEmpty().ifBlank { "png" },
                minZoom = metadata["minzoom"]?.toIntOrNull() ?: zooms.first,
                maxZoom = metadata["maxzoom"]?.toIntOrNull() ?: zooms.second,
                bounds = bounds,
                attribution = metadata["attribution"].orEmpty().replace("<[^>]*>".toRegex(), "").trim()
            )
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
}
