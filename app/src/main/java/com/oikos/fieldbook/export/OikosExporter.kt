package com.oikos.fieldbook.export

import com.oikos.fieldbook.data.FieldConfigEntity
import com.oikos.fieldbook.data.MediaEntity
import com.oikos.fieldbook.data.OccurrenceEntity
import com.oikos.fieldbook.data.ProjectEntity
import com.oikos.fieldbook.data.SessionEntity
import com.oikos.fieldbook.dwc.DarwinCoreCatalog
import com.oikos.fieldbook.util.CoordinateUtils
import com.oikos.fieldbook.util.UtmHemisphere
import com.oikos.fieldbook.util.parseFlatJsonMap
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OikosExporter {
    fun export(
        format: ExportFormat,
        project: ProjectEntity,
        sessions: List<SessionEntity>,
        occurrences: List<OccurrenceEntity>,
        media: List<MediaEntity>,
        fields: List<FieldConfigEntity>,
        tracks: List<TrackExportBundle> = emptyList(),
        content: ExportContent = ExportContent.SpreadsheetsAndAllMedia,
        fileStem: String = safeName(project.name)
    ): ExportResult {
        val packageModel = ExportPackage(project, sessions, occurrences, media, fields, tracks, content)
        val warnings = packageModel.validationWarnings()
        val bytes = when (format) {
            ExportFormat.Csv -> if (content == ExportContent.MediaOnly) {
                packageModel.mediaCsv().toByteArray(StandardCharsets.UTF_8)
            } else {
                packageModel.occurrencesCsv().toByteArray(StandardCharsets.UTF_8)
            }
            ExportFormat.Xlsx -> packageModel.xlsx()
            ExportFormat.Json -> if (content == ExportContent.MediaOnly) {
                packageModel.mediaJson().toByteArray(StandardCharsets.UTF_8)
            } else {
                packageModel.hierarchicalJson().toByteArray(StandardCharsets.UTF_8)
            }
            ExportFormat.GeoJson -> packageModel.geoJson().toByteArray(StandardCharsets.UTF_8)
            ExportFormat.Zip -> packageModel.zipBytes()
        }
        val stem = if (format == ExportFormat.Zip) "${fileStem}_${LocalDate.now()}" else fileStem
        return ExportResult("$stem.${format.extension}", format.mimeType, bytes, warnings.takeIf { it.isNotBlank() })
    }

    companion object {
        fun safeName(value: String): String {
            val normalized = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace("\\p{Mn}+".toRegex(), "")
                .replace("[^a-z0-9]+".toRegex(), "_")
                .trim('_')
            return normalized.ifBlank { "oikos_export" }
        }
    }
}

private class ExportPackage(
    private val project: ProjectEntity,
    private val sessions: List<SessionEntity>,
    private val occurrences: List<OccurrenceEntity>,
    media: List<MediaEntity>,
    private val fields: List<FieldConfigEntity>,
    private val tracks: List<TrackExportBundle>,
    private val content: ExportContent
) {
    private val sessionById = sessions.associateBy { it.id }
    private val occurrenceById = occurrences.associateBy { it.id }
    private val includedMedia = media.filter {
        when (content) {
            ExportContent.MediaOnly -> true
            ExportContent.SpreadsheetsOnly -> false
            ExportContent.SpreadsheetsAndPhotos -> it.mediaType == "photo"
            ExportContent.SpreadsheetsAndAudios -> it.mediaType == "audio"
            ExportContent.SpreadsheetsAndAllMedia -> true
        }
    }
    private val mediaLinks = buildMediaLinks(includedMedia)
    private val linksByOccurrence = mediaLinks.filter { it.occurrenceInternalId != null }.groupBy { it.occurrenceInternalId }

    fun occurrencesCsv(): String = csv(occurrenceHeaders(), occurrences.map { occurrence ->
        val session = sessionById[occurrence.sessionId]
        occurrenceMap(session, occurrence, linksByOccurrence[occurrence.id].orEmpty())
    })

    fun sessionsCsv(): String = csv(sessionHeaders, sessions.map { sessionMap(it) })

    fun mediaCsv(): String = csv(mediaHeaders, mediaLinks.map { it.csvMap() })

    fun measurementsCsv(): String = csv(measurementHeaders, measurementRows())

    fun tracksCsv(): String = csv(trackPointHeaders, tracks.flatMap { track ->
        track.points.mapIndexed { index, point ->
            linkedMapOf(
                "trackID" to track.trackID,
                "projectID" to track.projectID,
                "eventID" to track.eventID,
                "pointIndex" to (index + 1).toString(),
                "decimalLatitude" to decimal(point.latitude),
                "decimalLongitude" to decimal(point.longitude),
                "altitude" to decimal(point.altitude),
                "accuracy" to decimal(point.accuracy),
                "timestamp" to millis(point.timestamp),
                "speed" to decimal(point.speed),
                "geodeticDatum" to track.geodeticDatum
            ).withUtm(point.latitude, point.longitude, track.geodeticDatum)
        }
    })

    fun hierarchicalJson(): String {
        val root = JSONObject()
        root.put("project", JSONObject(projectMap()))
        root.put("sessions", JSONArray(sessions.map { session ->
            val sessionObject = JSONObject(sessionMap(session))
            val sessionOccurrences = occurrences.filter { it.sessionId == session.id }.map { occurrence ->
                val occurrenceObject = JSONObject(occurrenceMap(session, occurrence, linksByOccurrence[occurrence.id].orEmpty()))
                occurrenceObject.put("media", JSONArray(mediaLinks.filter { it.occurrenceInternalId == occurrence.id }.map { JSONObject(it.csvMap()) }))
                occurrenceObject
            }
            sessionObject.put("occurrences", JSONArray(sessionOccurrences))
            sessionObject.put("sessionMedia", JSONArray(mediaLinks.filter { it.sessionInternalId == session.id && it.occurrenceInternalId == null }.map { JSONObject(it.csvMap()) }))
            sessionObject
        }))
        root.put("fieldConfig", JSONArray(fields.map { JSONObject(fieldMap(it)) }))
        root.put("gpsTracks", JSONArray(tracks.map { JSONObject(trackMap(it)) }))
        return root.toString(2)
    }

    fun mediaJson(): String = JSONObject()
        .put("project", JSONObject(projectMap()))
        .put("exportedAt", exportedAt())
        .put("media", JSONArray(mediaLinks.map { JSONObject(it.csvMap()) }))
        .toString(2)

    fun geoJson(): String {
        val features = JSONArray()
        occurrences.filter { it.latitude != null && it.longitude != null }.forEach { occurrence ->
            val session = sessionById[occurrence.sessionId]
            val links = linksByOccurrence[occurrence.id].orEmpty()
            val properties = JSONObject()
                .put("occurrenceID", occurrence.occurrenceId)
                .put("eventID", session?.eventId.orEmpty())
                .put("scientificName", occurrence.scientificName)
                .put("eventDate", session?.eventDate.orEmpty())
                .put("associatedMedia", associatedMedia(links))
                .put("mediaCount", links.size)
                .put("photoCount", links.count { it.type == "photo" })
                .put("audioCount", links.count { it.type == "audio" })
            linkedMapOf<String, String>().withUtm(occurrence.latitude, occurrence.longitude, occurrence.geodeticDatum)
                .forEach { (key, value) -> properties.put(key, value) }
            val geometry = JSONObject()
                .put("type", "Point")
                .put("coordinates", JSONArray(listOf(occurrence.longitude, occurrence.latitude)))
            features.put(JSONObject().put("type", "Feature").put("geometry", geometry).put("properties", properties))
        }
        tracks.filter { it.points.size >= 2 }.forEach { track ->
            val coordinates = JSONArray(track.points.map { JSONArray(listOf(it.longitude, it.latitude)) })
            val properties = JSONObject(trackMap(track)).put("featureType", "expeditionTrack")
            features.put(
                JSONObject()
                    .put("type", "Feature")
                    .put("geometry", JSONObject().put("type", "LineString").put("coordinates", coordinates))
                    .put("properties", properties)
            )
        }
        return JSONObject().put("type", "FeatureCollection").put("features", features).toString(2)
    }

    fun xlsx(): ByteArray {
        if (content == ExportContent.MediaOnly) {
            return XlsxWriter.write(
                listOf(
                    "Mídias" to table(mediaLinks.map { it.csvMap() }),
                    "Resumo_Exportação" to listOf(
                        listOf("item", "valor"),
                        listOf("projectName", project.name),
                        listOf("exportedAt", exportedAt()),
                        listOf("photos", mediaLinks.count { it.type == "photo" }.toString()),
                        listOf("audios", mediaLinks.count { it.type == "audio" }.toString()),
                        listOf("warnings", validationWarnings())
                    )
                )
            )
        }
        val occurrenceHeaders = occurrenceHeaders()
        val sheets = listOf(
            "Projeto" to listOf(projectMap().keys.toList(), projectMap().values.toList()),
            "Expedições" to table(sessions.map { sessionMap(it) }),
            "Ocorrências_DarwinCore" to listOf(occurrenceHeaders) + occurrences.map {
                val row = occurrenceMap(sessionById[it.sessionId], it, linksByOccurrence[it.id].orEmpty())
                occurrenceHeaders.map { header -> row[header].orEmpty() }
            },
            "Mídias" to table(mediaLinks.map { it.csvMap() }),
            "Trilha_GPS" to table(trackPointRows()),
            "MeasurementOrFact" to table(measurementRows()),
            "Campos_Usados" to table(fields.map { fieldMap(it) }),
            "Legenda_PT_EN" to listOf(listOf("dwcTerm", "Português", "English")) + fields.map { listOf(it.dwcTerm, it.labelPt, it.labelEn) },
            "Resumo_Exportação" to listOf(
                listOf("item", "valor"),
                listOf("projectName", project.name),
                listOf("exportedAt", exportedAt()),
                listOf("sessions", sessions.size.toString()),
                listOf("occurrences", occurrences.size.toString()),
                listOf("photos", mediaLinks.count { it.type == "photo" }.toString()),
                listOf("audios", mediaLinks.count { it.type == "audio" }.toString()),
                listOf("tracks", tracks.size.toString()),
                listOf("trackPoints", tracks.sumOf { it.points.size }.toString()),
                listOf("coordinateFormat", project.coordinateFormat),
                listOf("coordinateStorage", "Graus Decimais"),
                listOf("utmColumns", if (project.coordinateFormat == "UTM") "Incluídas nas planilhas e properties do GeoJSON" else "Não aplicável"),
                listOf("geodeticDatum", project.defaultDatum),
                listOf("warnings", validationWarnings())
            )
        )
        return XlsxWriter.write(sheets)
    }

    fun zipBytes(): ByteArray {
        val root = "Projeto_${OikosExporter.safeName(project.name)}"
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.put("$root/README.txt", readme())
            zip.put("$root/manifest.json", manifestJson())
            if (content != ExportContent.MediaOnly) {
                zip.put("$root/01_planilha_principal/ocorrencias_darwin_core.csv", occurrencesCsv())
                zip.put("$root/01_planilha_principal/oikos_fieldbook_exportacao_completa.xlsx", xlsx())
                zip.put("$root/01_planilha_principal/projeto_completo.json", hierarchicalJson())
                zip.put("$root/03_sig_qgis/ocorrencias_e_trilhas.geojson", geoJson())
                zip.put("$root/02_tabelas_de_apoio/expedicoes.csv", sessionsCsv())
                zip.put("$root/02_tabelas_de_apoio/trilha_gps_pontos.csv", tracksCsv())
                zip.put("$root/02_tabelas_de_apoio/measurement_or_fact.csv", measurementsCsv())
            }
            zip.put("$root/02_tabelas_de_apoio/midias.csv", mediaCsv())
            mediaLinks.forEach { link ->
                val source = File(link.sourcePath)
                if (source.exists()) zip.putBytes("$root/${link.relativePath}", source.readBytes())
            }
        }
        return output.toByteArray()
    }

    fun validationWarnings(): String {
        val warnings = mutableListOf<String>()
        if (occurrences.isEmpty()) warnings += "Nenhuma ocorrência encontrada para exportar."
        val withoutId = occurrences.count { it.occurrenceId.isBlank() }
        if (withoutId > 0) warnings += "$withoutId ocorrência(s) sem occurrenceID."
        val withoutCoordinates = occurrences.count { it.latitude == null || it.longitude == null }
        if (withoutCoordinates > 0) warnings += "$withoutCoordinates ocorrência(s) sem coordenadas; elas não aparecem no GeoJSON."
        val emptyTracks = tracks.count { it.points.size < 2 }
        if (emptyTracks > 0) warnings += "$emptyTracks trilha(s) com menos de 2 pontos; elas aparecem no CSV, mas não formam LineString no GeoJSON."
        val missingMedia = mediaLinks.count { !File(it.sourcePath).exists() }
        if (missingMedia > 0) warnings += "$missingMedia arquivo(s) de mídia não foram encontrados no armazenamento local."
        val mediaWithoutEvent = mediaLinks.count { it.eventID.isBlank() }
        if (mediaWithoutEvent > 0) warnings += "$mediaWithoutEvent mídia(s) sem eventID de expedição."
        val mediaWithoutOccurrence = mediaLinks.count { it.occurrenceID.isBlank() }
        if (mediaWithoutOccurrence > 0) warnings += "$mediaWithoutOccurrence mídia(s) ligadas apenas à expedição, sem occurrenceID."
        val duplicatedNames = mediaLinks.groupBy { it.relativePath }.count { it.value.size > 1 }
        if (duplicatedNames > 0) warnings += "$duplicatedNames nome(s) de mídia duplicado(s) na exportação."
        return warnings.joinToString("\n")
    }

    private fun manifestJson(): String {
        val files = mutableListOf(
            "README.txt",
            "manifest.json",
            "02_tabelas_de_apoio/midias.csv"
        )
        if (content != ExportContent.MediaOnly) {
            files += listOf(
                "01_planilha_principal/ocorrencias_darwin_core.csv",
                "01_planilha_principal/oikos_fieldbook_exportacao_completa.xlsx",
                "01_planilha_principal/projeto_completo.json",
                "03_sig_qgis/ocorrencias_e_trilhas.geojson",
                "02_tabelas_de_apoio/expedicoes.csv",
                "02_tabelas_de_apoio/trilha_gps_pontos.csv",
                "02_tabelas_de_apoio/measurement_or_fact.csv"
            )
        }
        files += mediaLinks.map { it.relativePath }
        return JSONObject()
            .put("projectID", "PROJ-${project.id.toString().padStart(3, '0')}")
            .put("projectName", project.name)
            .put("exportedAt", exportedAt())
            .put("totalSessions", sessions.size)
            .put("totalOccurrences", occurrences.size)
            .put("totalPhotos", mediaLinks.count { it.type == "photo" })
            .put("totalAudios", mediaLinks.count { it.type == "audio" })
            .put("totalTracks", tracks.size)
            .put("totalTrackPoints", tracks.sumOf { it.points.size })
            .put("coordinateFormat", project.coordinateFormat)
            .put("coordinateStorage", "decimalDegrees")
            .put("utmColumnsIncluded", project.coordinateFormat == "UTM")
            .put("geodeticDatum", project.defaultDatum)
            .put("files", JSONArray(files))
            .put("mediaLinks", JSONArray(mediaLinks.map { JSONObject(it.manifestMap()) }))
            .put("gpsTracks", JSONArray(tracks.map { JSONObject(trackMap(it)) }))
            .put("warnings", JSONArray(validationWarnings().lines().filter { it.isNotBlank() }))
            .toString(2)
    }

    private fun readme(): String = """
        Oikos FieldBook - Exportação completa

        Projeto: ${project.name}
        Exportado em: ${exportedAt()}
        Datum: ${project.defaultDatum}
        Coordenadas internas/Darwin Core: Graus Decimais (decimalLatitude, decimalLongitude)
        Formato configurado no projeto: ${project.coordinateFormat}
        ${if (project.coordinateFormat == "UTM") "Coordenadas UTM também aparecem em verbatimCoordinates, verbatimCoordinateSystem, verbatimSRS, utmZone, utmHemisphere, utmEasting e utmNorthing." else ""}

        Estrutura:
        - 01_planilha_principal/: arquivos principais para análise. O CSV Darwin Core é a tabela principal de ocorrências; o XLSX reúne projeto, expedições, ocorrências, mídias, trilhas e campos usados em abas.
        - 02_tabelas_de_apoio/: tabelas auxiliares, como expedições, mídias, pontos da trilha GPS e MeasurementOrFact.
        - 03_sig_qgis/: GeoJSON para abrir no QGIS, com ocorrências como Point e trilhas como LineString.
        - 04_midias/fotos/: fotografias exportadas com nomes rastreáveis.
        - 04_midias/audios/: áudios exportados com nomes rastreáveis.
        - manifest.json: resumo da exportação e relação completa entre mídias e registros.

        Cada foto ou áudio está ligado a um registro por occurrenceID, ou a uma expedição por eventID.
        Consulte a coluna associatedMedia em 01_planilha_principal/ocorrencias_darwin_core.csv ou a tabela 02_tabelas_de_apoio/midias.csv.
        Quando houver mais de uma mídia, associatedMedia usa caminhos relativos separados por ponto e vírgula.

        Para QGIS:
        Abra 03_sig_qgis/ocorrencias_e_trilhas.geojson. A geometria usa Point em ordem longitude, latitude.
        Se o projeto estiver em UTM, os valores UTM ficam nas propriedades da camada; a geometria GeoJSON continua em longitude/latitude por padrão do formato.
        Registros sem coordenadas permanecem nas planilhas, mas não aparecem no GeoJSON.
    """.trimIndent()

    private fun occurrenceHeaders(): List<String> {
        val configured = fields.filter { it.enabled }.map { it.dwcTerm }
        val utmHeaders = if (project.coordinateFormat == "UTM") utmExportHeaders else emptyList()
        return (DarwinCoreCatalog.coreOccurrenceTerms + configured + utmHeaders + "associatedMedia").distinct()
    }

    private fun occurrenceMap(session: SessionEntity?, occurrence: OccurrenceEntity, links: List<MediaLink>): Map<String, String> {
        val dynamic = parseFlatJsonMap(occurrence.dynamicFieldsJson)
        val latitude = occurrence.latitude ?: session?.latitude
        val longitude = occurrence.longitude ?: session?.longitude
        val datum = occurrence.geodeticDatum.ifBlank { session?.geodeticDatum ?: project.defaultDatum }
        val base = linkedMapOf(
            "occurrenceID" to occurrence.occurrenceId,
            "eventID" to session?.eventId.orEmpty(),
            "recordNumber" to occurrence.recordNumber,
            "basisOfRecord" to occurrence.basisOfRecord,
            "occurrenceStatus" to occurrence.occurrenceStatus,
            "scientificName" to occurrence.scientificName,
            "vernacularName" to occurrence.vernacularName,
            "individualCount" to occurrence.individualCount?.toString().orEmpty(),
            "sex" to occurrence.sex,
            "lifeStage" to occurrence.lifeStage,
            "behavior" to occurrence.behavior,
            "reproductiveCondition" to occurrence.reproductiveCondition,
            "recordedBy" to occurrence.recordedBy.ifBlank { session?.recordedBy.orEmpty() },
            "identifiedBy" to occurrence.identifiedBy,
            "dateIdentified" to occurrence.dateIdentified,
            "eventDate" to dynamic["eventDate"].orEmpty().ifBlank { session?.eventDate.orEmpty() },
            "eventTime" to dynamic["eventTime"].orEmpty().ifBlank { session?.startTime.orEmpty() },
            "samplingProtocol" to session?.samplingProtocol.orEmpty(),
            "samplingEffort" to session?.samplingEffort.orEmpty(),
            "habitat" to session?.habitat.orEmpty(),
            "locality" to session?.locality.orEmpty(),
            "municipality" to session?.municipality.orEmpty(),
            "stateProvince" to session?.stateProvince.orEmpty(),
            "country" to session?.country.orEmpty(),
            "decimalLatitude" to decimal(latitude),
            "decimalLongitude" to decimal(longitude),
            "coordinateUncertaintyInMeters" to decimal(occurrence.coordinateUncertaintyInMeters ?: session?.coordinateUncertaintyInMeters),
            "geodeticDatum" to datum,
            "minimumElevationInMeters" to decimal(occurrence.altitude ?: session?.altitude),
            "occurrenceRemarks" to occurrence.occurrenceRemarks,
            "fieldNotes" to occurrence.fieldNotes,
            "associatedMedia" to associatedMedia(links)
        )
        if (project.coordinateFormat == "UTM") base.withUtm(latitude, longitude, datum)
        dynamic.forEach { (key, value) -> base[key] = value }
        fields.filter { it.enabled && !base.containsKey(it.dwcTerm) }.forEach { base[it.dwcTerm] = "" }
        return base
    }

    private fun sessionMap(session: SessionEntity): Map<String, String> =
        linkedMapOf(
            "projectID" to "PROJ-${project.id.toString().padStart(3, '0')}",
            "eventID" to session.eventId,
            "eventDate" to session.eventDate,
            "eventTime" to session.startTime,
            "locality" to session.locality,
            "habitat" to session.habitat,
            "samplingProtocol" to session.samplingProtocol,
            "samplingEffort" to session.samplingEffort,
            "recordedBy" to session.recordedBy,
            "decimalLatitude" to decimal(session.latitude),
            "decimalLongitude" to decimal(session.longitude),
            "coordinateUncertaintyInMeters" to decimal(session.coordinateUncertaintyInMeters),
            "geodeticDatum" to session.geodeticDatum,
            "eventRemarks" to session.notes
        ).withUtm(session.latitude, session.longitude, session.geodeticDatum)

    private fun projectMap(): Map<String, String> = mapOf(
        "projectID" to "PROJ-${project.id.toString().padStart(3, '0')}",
        "projectName" to project.name,
        "description" to project.description,
        "institution" to project.institution,
        "principalInvestigator" to project.principalInvestigator,
        "language" to project.language,
        "defaultDatum" to project.defaultDatum,
        "coordinateFormat" to project.coordinateFormat,
        "photoQuality" to project.photoQuality,
        "audioFormat" to project.audioFormat
    )

    private fun fieldMap(field: FieldConfigEntity): Map<String, String> = mapOf(
        "dwcTerm" to field.dwcTerm,
        "labelPt" to field.labelPt,
        "labelEn" to field.labelEn,
        "enabled" to field.enabled.toString(),
        "required" to field.required.toString(),
        "fieldType" to field.fieldType,
        "groupName" to field.groupName
    )

    private fun trackMap(track: TrackExportBundle): Map<String, String> = mapOf(
        "trackID" to track.trackID,
        "projectID" to track.projectID,
        "eventID" to track.eventID,
        "startTime" to millis(track.startTime),
        "endTime" to track.endTime?.let { millis(it) }.orEmpty(),
        "totalDistance" to decimal(track.totalDistance),
        "duration" to track.duration.toString(),
        "pointsCount" to track.pointsCount.toString(),
        "coordinateFormat" to track.coordinateFormat,
        "geodeticDatum" to track.geodeticDatum
    )

    private fun trackPointRows(): List<Map<String, String>> = tracks.flatMap { track ->
        track.points.mapIndexed { index, point ->
            linkedMapOf(
                "trackID" to track.trackID,
                "projectID" to track.projectID,
                "eventID" to track.eventID,
                "pointIndex" to (index + 1).toString(),
                "decimalLatitude" to decimal(point.latitude),
                "decimalLongitude" to decimal(point.longitude),
                "altitude" to decimal(point.altitude),
                "accuracy" to decimal(point.accuracy),
                "timestamp" to millis(point.timestamp),
                "speed" to decimal(point.speed),
                "geodeticDatum" to track.geodeticDatum
            ).withUtm(point.latitude, point.longitude, track.geodeticDatum)
        }
    }

    private fun MutableMap<String, String>.withUtm(latitude: Double?, longitude: Double?, datum: String): MutableMap<String, String> {
        if (project.coordinateFormat != "UTM") return this
        val utm = CoordinateUtils.toUtm(latitude, longitude)
        this["verbatimCoordinateSystem"] = "UTM"
        this["verbatimSRS"] = datum
        if (utm == null) {
            this["verbatimCoordinates"] = ""
            this["utmZone"] = ""
            this["utmHemisphere"] = ""
            this["utmEasting"] = ""
            this["utmNorthing"] = ""
            return this
        }
        val hemisphere = if (utm.hemisphere == UtmHemisphere.South) "S" else "N"
        this["verbatimCoordinates"] = "${utm.zone}${hemisphere} ${decimal(utm.easting)} ${decimal(utm.northing)}"
        this["utmZone"] = utm.zone.toString()
        this["utmHemisphere"] = hemisphere
        this["utmEasting"] = decimal(utm.easting)
        this["utmNorthing"] = decimal(utm.northing)
        return this
    }

    private fun measurementRows(): List<Map<String, String>> {
        val directTerms = occurrenceHeaders().toSet()
        return occurrences.flatMap { occurrence ->
            parseFlatJsonMap(occurrence.dynamicFieldsJson)
                .filter { (key, value) -> value.isNotBlank() && key !in directTerms }
                .map { (key, value) ->
                    mapOf(
                        "occurrenceID" to occurrence.occurrenceId,
                        "eventID" to sessionById[occurrence.sessionId]?.eventId.orEmpty(),
                        "measurementType" to key,
                        "measurementValue" to value,
                        "measurementUnit" to unitForMeasurement(key),
                        "measurementRemarks" to "Campo dinamico opcional do Oikos FieldBook"
                    )
                }
        }
    }

    private fun unitForMeasurement(key: String): String = when {
        key.contains("temperature", ignoreCase = true) -> "Celsius"
        key.contains("humidity", ignoreCase = true) -> "percent"
        key.contains("height", ignoreCase = true) -> "meter"
        key.contains("distance", ignoreCase = true) -> "meter"
        else -> ""
    }

    private fun buildMediaLinks(media: List<MediaEntity>): List<MediaLink> {
        val counters = mutableMapOf<String, Int>()
        return media.map { item ->
            val occurrence = item.occurrenceId?.let { occurrenceById[it] }
            val session = sessionById[item.sessionId]
            val extension = extensionFor(item)
            val folder = if (item.mediaType == "audio") "audios" else "fotos"
            val fileName = uniqueMediaFileName(item, occurrence, session, extension, counters)
            val relativePath = "04_midias/$folder/$fileName"
            MediaLink(
                mediaID = "MED-${item.id.toString().padStart(6, '0')}",
                projectID = "PROJ-${item.projectId.toString().padStart(3, '0')}",
                eventID = session?.eventId.orEmpty(),
                sessionInternalId = item.sessionId,
                occurrenceID = occurrence?.occurrenceId.orEmpty(),
                occurrenceInternalId = occurrence?.id,
                type = item.mediaType,
                fileName = fileName,
                relativePath = relativePath,
                mimeType = item.mimeType,
                capturedAt = millis(item.createdAt),
                decimalLatitude = decimal(item.latitude),
                decimalLongitude = decimal(item.longitude),
                coordinateUncertaintyInMeters = decimal(item.coordinateUncertaintyInMeters),
                caption = item.caption,
                mediaSubject = item.mediaSubject,
                bodyPartOrPlantPart = item.bodyPartOrPlantPart,
                behaviorContext = item.behaviorContext,
                notes = item.notes,
                sourcePath = item.filePath
            )
        }
    }

    private fun uniqueMediaFileName(
        media: MediaEntity,
        occurrence: OccurrenceEntity?,
        session: SessionEntity?,
        extension: String,
        counters: MutableMap<String, Int>
    ): String {
        val base = exportMediaBaseName(media, occurrence, session)
        val safeBase = safeToken(base)
        val key = "$safeBase.$extension"
        val number = (counters[key] ?: 0) + 1
        counters[key] = number
        return if (number == 1) {
            "$safeBase.$extension"
        } else {
            "$safeBase-${number.toString().padStart(2, '0')}.$extension"
        }
    }

    private fun exportMediaBaseName(media: MediaEntity, occurrence: OccurrenceEntity?, session: SessionEntity?): String {
        val date = session?.eventDate?.takeIf { it.isNotBlank() } ?: millis(media.createdAt).take(10)
        val time = DateTimeFormatter.ofPattern("HHmm", Locale.US)
            .format(java.time.Instant.ofEpochMilli(media.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalTime())
        val area = projectAcronym(project.name)
        val expedition = shortCode(session?.name.orEmpty()).ifBlank { "EXPEDICAO" }
        val owner = if (occurrence != null) {
            occurrence.recordNumber.ifBlank { occurrence.occurrenceId.takeLast(8) }
        } else {
            session?.eventId?.takeLast(8).orEmpty().ifBlank { "EXPEDICAO" }
        }
        val species = shortCode(occurrence?.scientificName?.ifBlank { occurrence.vernacularName }.orEmpty())
        val type = if (media.mediaType == "audio") "AUDIO" else "FOTO"
        return listOf(date, time, area, expedition, owner, species, type)
            .filter { it.isNotBlank() }
            .joinToString("_")
    }

    private fun extensionFor(media: MediaEntity): String = when {
        media.mediaType == "audio" && media.mimeType.contains("mpeg") -> "mp3"
        media.mediaType == "audio" && media.fileName.endsWith(".mp3", true) -> "mp3"
        media.mediaType == "audio" && media.fileName.endsWith(".wav", true) -> "wav"
        media.mediaType == "audio" -> "wav"
        media.fileName.endsWith(".png", true) -> "png"
        else -> "jpg"
    }

    private fun associatedMedia(links: List<MediaLink>): String = links.joinToString("; ") { it.relativePath }

    private fun csv(headers: List<String>, rows: List<Map<String, String>>): String = buildString {
        appendLine(headers.joinToString(","))
        rows.forEach { row -> appendLine(headers.joinToString(",") { csvCell(row[it].orEmpty()) }) }
    }

    private fun table(rows: List<Map<String, String>>): List<List<String>> {
        if (rows.isEmpty()) return listOf(emptyList())
        val headers = rows.flatMap { it.keys }.distinct()
        return listOf(headers) + rows.map { row -> headers.map { row[it].orEmpty() } }
    }

    private fun exportedAt(): String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    private fun millis(value: Long): String = java.time.Instant.ofEpochMilli(value).toString()
    private fun decimal(value: Double?): String = value?.let { String.format(Locale.US, "%.8f", it).trimEnd('0').trimEnd('.') }.orEmpty()
    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"$escaped\"" else escaped
    }

    private fun safeToken(value: String): String = value.uppercase(Locale.ROOT)
        .replace("[^A-Z0-9_-]+".toRegex(), "-")
        .trim('-')
        .ifBlank { "SEM-ID" }

    private fun projectAcronym(value: String): String {
        val clean = asciiWords(value)
        val compact = clean.filter { it.length > 2 }.joinToString("") { it.first().toString() }
        return compact.ifBlank { "OIKOS" }.take(8)
    }

    private fun shortCode(value: String): String =
        asciiWords(value).joinToString("_").ifBlank { "" }.take(28).trim('_')

    private fun asciiWords(value: String): List<String> =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .uppercase(Locale.US)
            .replace("[^A-Z0-9]+".toRegex(), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in setOf("DE", "DA", "DO", "DAS", "DOS", "E", "A", "O") }

    companion object {
        private val utmExportHeaders = listOf("verbatimCoordinates", "verbatimCoordinateSystem", "verbatimSRS", "utmZone", "utmHemisphere", "utmEasting", "utmNorthing")
        private val sessionHeaders = listOf("projectID", "eventID", "eventDate", "eventTime", "locality", "habitat", "samplingProtocol", "samplingEffort", "recordedBy", "decimalLatitude", "decimalLongitude", "coordinateUncertaintyInMeters", "geodeticDatum") + utmExportHeaders + "eventRemarks"
        private val mediaHeaders = listOf("mediaID", "projectID", "eventID", "occurrenceID", "mediaType", "fileName", "relativePath", "mimeType", "capturedAt", "decimalLatitude", "decimalLongitude", "coordinateUncertaintyInMeters", "caption", "mediaSubject", "bodyPartOrPlantPart", "behaviorContext", "notes", "associatedMedia")
        private val trackPointHeaders = listOf("trackID", "projectID", "eventID", "pointIndex", "decimalLatitude", "decimalLongitude", "altitude", "accuracy", "timestamp", "speed", "geodeticDatum") + utmExportHeaders
        private val measurementHeaders = listOf("occurrenceID", "eventID", "measurementType", "measurementValue", "measurementUnit", "measurementRemarks")
    }
}

private data class MediaLink(
    val mediaID: String,
    val projectID: String,
    val eventID: String,
    val sessionInternalId: Long,
    val occurrenceID: String,
    val occurrenceInternalId: Long?,
    val type: String,
    val fileName: String,
    val relativePath: String,
    val mimeType: String,
    val capturedAt: String,
    val decimalLatitude: String,
    val decimalLongitude: String,
    val coordinateUncertaintyInMeters: String,
    val caption: String,
    val mediaSubject: String,
    val bodyPartOrPlantPart: String,
    val behaviorContext: String,
    val notes: String,
    val sourcePath: String
) {
    fun csvMap(): Map<String, String> = mapOf(
        "mediaID" to mediaID,
        "projectID" to projectID,
        "eventID" to eventID,
        "occurrenceID" to occurrenceID,
        "mediaType" to type,
        "fileName" to fileName,
        "relativePath" to relativePath,
        "mimeType" to mimeType,
        "capturedAt" to capturedAt,
        "decimalLatitude" to decimalLatitude,
        "decimalLongitude" to decimalLongitude,
        "coordinateUncertaintyInMeters" to coordinateUncertaintyInMeters,
        "caption" to caption,
        "mediaSubject" to mediaSubject,
        "bodyPartOrPlantPart" to bodyPartOrPlantPart,
        "behaviorContext" to behaviorContext,
        "notes" to notes,
        "associatedMedia" to relativePath
    )

    fun manifestMap(): Map<String, String> = mapOf(
        "mediaID" to mediaID,
        "occurrenceID" to occurrenceID,
        "eventID" to eventID,
        "type" to type,
        "path" to relativePath
    )
}

private fun ZipOutputStream.put(name: String, content: String) {
    putNextEntry(ZipEntry(name))
    write(content.toByteArray(StandardCharsets.UTF_8))
    closeEntry()
}

private fun ZipOutputStream.put(name: String, content: ByteArray) {
    putNextEntry(ZipEntry(name))
    write(content)
    closeEntry()
}

private fun ZipOutputStream.putBytes(name: String, content: ByteArray) = put(name, content)

private object XlsxWriter {
    fun write(sheets: List<Pair<String, List<List<String>>>>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.put("[Content_Types].xml", contentTypes(sheets.size))
            zip.put("_rels/.rels", rels())
            zip.put("xl/workbook.xml", workbook(sheets))
            zip.put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            sheets.forEachIndexed { index, (_, rows) -> zip.put("xl/worksheets/sheet${index + 1}.xml", sheet(rows)) }
        }
        return output.toByteArray()
    }

    private fun contentTypes(count: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        repeat(count) { append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""") }
        append("</Types>")
    }

    private fun rels(): String =
        """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private fun workbook(sheets: List<Pair<String, List<List<String>>>>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { index, (name, _) -> append("""<sheet name="${xml(name.take(31))}" sheetId="${index + 1}" r:id="rId${index + 1}"/>""") }
        append("</sheets></workbook>")
    }

    private fun workbookRels(count: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        repeat(count) { append("""<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""") }
        append("</Relationships>")
    }

    private fun sheet(rows: List<List<String>>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rowIndex, row ->
            append("""<row r="${rowIndex + 1}">""")
            row.forEachIndexed { columnIndex, value ->
                append("""<c r="${cellName(columnIndex, rowIndex)}" t="inlineStr"><is><t>${xml(value)}</t></is></c>""")
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun cellName(columnIndex: Int, rowIndex: Int): String {
        var n = columnIndex
        val col = StringBuilder()
        do {
            col.insert(0, ('A'.code + (n % 26)).toChar())
            n = n / 26 - 1
        } while (n >= 0)
        return "$col${rowIndex + 1}"
    }

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

