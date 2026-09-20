package com.oikos.fieldbook.export

enum class ExportScope {
    Project,
    Session,
    MasterSheet
}

enum class ExportContent {
    MediaOnly,
    SpreadsheetsOnly,
    SpreadsheetsAndPhotos,
    SpreadsheetsAndAudios,
    SpreadsheetsAndAllMedia
}

enum class ExportFormat(val extension: String, val mimeType: String) {
    Csv("csv", "text/csv"),
    Xlsx("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    Json("json", "application/json"),
    GeoJson("geojson", "application/geo+json"),
    Zip("zip", "application/zip"),

    /** Darwin Core Archive: formato ingerido por IPT, GBIF e SiBBr. */
    DwcArchive("zip", "application/zip")
}

data class ExportResult(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val warning: String? = null
)

data class TrackExportBundle(
    val trackID: String,
    val projectID: String,
    val eventID: String,
    val startTime: Long,
    val endTime: Long?,
    val totalDistance: Double,
    val duration: Long,
    val pointsCount: Int,
    val coordinateFormat: String,
    val geodeticDatum: String,
    val points: List<TrackPointExport>
)

data class TrackPointExport(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracy: Double?,
    val timestamp: Long,
    val speed: Double?
)
