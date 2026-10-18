package com.oikos.fieldbook.export

import com.oikos.fieldbook.data.FieldConfigEntity
import com.oikos.fieldbook.data.MediaEntity
import com.oikos.fieldbook.data.OccurrenceEntity
import com.oikos.fieldbook.data.ProjectEntity
import com.oikos.fieldbook.data.SessionEntity
import com.oikos.fieldbook.dwc.DarwinCoreCatalog
import com.oikos.fieldbook.services.MediaKind
import com.oikos.fieldbook.util.CoordinateUtils
import com.oikos.fieldbook.util.UtmHemisphere
import com.oikos.fieldbook.util.parseFlatJsonMap
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** [appVersion] entra na citacao do aplicativo (COMO_CITAR.txt, eml.xml, resumo da planilha). */
class OikosExporter(private val appVersion: String = com.oikos.fieldbook.BuildConfig.VERSION_NAME) {
    /**
     * Gera a exportacao direto no arquivo devolvido por [destination].
     *
     * Ate a v0.2.0 o ZIP inteiro, com todas as fotos e audios, era montado num
     * ByteArray na memoria e na thread principal: com audio WAV (~5 MB/min) um
     * projeto real estourava a memoria de aparelhos modestos. Agora as midias
     * sao copiadas em fluxo para o arquivo, e o chamador roda fora da main thread.
     */
    fun export(
        format: ExportFormat,
        project: ProjectEntity,
        sessions: List<SessionEntity>,
        occurrences: List<OccurrenceEntity>,
        media: List<MediaEntity>,
        fields: List<FieldConfigEntity>,
        tracks: List<TrackExportBundle> = emptyList(),
        content: ExportContent = ExportContent.SpreadsheetsAndAllMedia,
        fileStem: String = safeName(project.name),
        destination: (String) -> File
    ): ExportResult {
        val packageModel = ExportPackage(project, sessions, occurrences, media, fields, tracks, content, appVersion)
        val warnings = packageModel.validationWarnings(format)
        val stem = when (format) {
            ExportFormat.Zip -> "${fileStem}_${LocalDate.now()}"
            ExportFormat.DwcArchive -> "${fileStem}_dwca_${LocalDate.now()}"
            else -> fileStem
        }
        val file = destination("$stem.${format.extension}")
        when (format) {
            ExportFormat.Csv -> file.writeText(
                if (content == ExportContent.MediaOnly) packageModel.mediaCsv() else packageModel.occurrencesCsv(),
                StandardCharsets.UTF_8
            )
            ExportFormat.Xlsx -> file.writeBytes(packageModel.xlsx())
            ExportFormat.Json -> file.writeText(
                if (content == ExportContent.MediaOnly) packageModel.mediaJson() else packageModel.hierarchicalJson(),
                StandardCharsets.UTF_8
            )
            ExportFormat.GeoJson -> file.writeText(packageModel.geoJson(), StandardCharsets.UTF_8)
            ExportFormat.Zip -> FileOutputStream(file).buffered().use { packageModel.zipTo(it) }
            ExportFormat.DwcArchive -> file.writeBytes(packageModel.dwcArchiveBytes())
        }
        return ExportResult(file, format.mimeType, warnings.takeIf { it.isNotBlank() })
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
    private val content: ExportContent,
    private val appVersion: String
) {
    private val sessionById = sessions.associateBy { it.id }
    private val occurrenceById = occurrences.associateBy { it.id }
    // Ordem fixa (mais antiga primeiro): antes vinha da mais nova, e uma foto
    // nova mudava o numero -01/-02 das anteriores a cada exportacao.
    private val allMedia = media.sortedBy { it.id }
    private val includedMedia = allMedia.filter {
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

    /**
     * O Darwin Core Archive descreve todas as midias, qualquer que seja o
     * "Conteudo" escolhido: antes, "Apenas planilhas" tirava do archive as
     * condicoes de gravacao (temperatura, umidade, distancia) de cada audio.
     */
    private val archiveMediaLinks = if (includedMedia.size == allMedia.size) mediaLinks else buildMediaLinks(allMedia)

    /** Termos numericos (catalogo ou campo configurado como numero). */
    private val numericTerms: Set<String> =
        DarwinCoreCatalog.byTerm.values.filter { it.type == "decimal" || it.type == "integer" }.map { it.term }.toSet() +
            fields.filter { it.fieldType == "decimal" || it.fieldType == "integer" }.map { it.dwcTerm } +
            setOf("measurementValue", "measurementAccuracy")

    /** Colunas em que o CSV brasileiro troca o ponto decimal por virgula. */
    private val numericColumns: Set<String> = numericTerms + setOf(
        "decimalLatitude", "decimalLongitude", "coordinateUncertaintyInMeters", "minimumElevationInMeters",
        "individualCount", "altitude", "accuracy", "speed", "totalDistance", "durationSeconds",
        "airTemperature", "relativeHumidity", "distanceToSourceMeters", "sampleSizeValue", "utmEasting", "utmNorthing"
    )

    /**
     * "12,4" digitado num campo numerico sai "12.4". O formulario guarda o
     * texto como foi digitado, e o GBIF nao le virgula decimal.
     */
    private fun normalized(term: String, value: String): String {
        val trimmed = value.trim()
        return if (term in numericTerms && COMMA_DECIMAL.matches(trimmed)) trimmed.replace(',', '.') else value
    }

    /** Coordenada do registro e o que so vale junto com ela. */
    private data class RecordPlace(val latitude: Double?, val longitude: Double?, val uncertainty: Double?, val altitude: Double?, val datum: String)

    /**
     * Sem coordenada propria, o registro usa o ponto da expedicao com a
     * incerteza e o datum DELE. Antes cada campo herdava sozinho: um ponto
     * digitado de um GPS de mao, sem precisao informada, saia com a precisao e
     * a altitude do acampamento.
     */
    private fun placeOf(occurrence: OccurrenceEntity, session: SessionEntity?): RecordPlace = when {
        occurrence.latitude != null && occurrence.longitude != null -> RecordPlace(
            occurrence.latitude, occurrence.longitude, occurrence.coordinateUncertaintyInMeters, occurrence.altitude,
            occurrence.geodeticDatum.ifBlank { project.defaultDatum }
        )
        session?.latitude != null && session.longitude != null -> RecordPlace(
            session.latitude, session.longitude, session.coordinateUncertaintyInMeters, occurrence.altitude ?: session.altitude,
            session.geodeticDatum.ifBlank { project.defaultDatum }
        )
        else -> RecordPlace(null, null, null, occurrence.altitude, occurrence.geodeticDatum.ifBlank { project.defaultDatum })
    }

    /** Data do registro; sem data propria, a da expedicao. */
    private fun eventDateOf(occurrence: OccurrenceEntity, session: SessionEntity?, dynamic: Map<String, String> = parseFlatJsonMap(occurrence.dynamicFieldsJson)): String =
        dynamic["eventDate"].orEmpty().ifBlank { session?.eventDate.orEmpty() }

    /**
     * Termos Darwin Core com valor em algum registro, mesmo com o campo
     * desligado no formulario: antes viravam MeasurementOrFact no archive
     * (por exemplo "Infraspecific epithet" de toda subespecie).
     */
    private val standardTermsWithData: List<String> by lazy {
        occurrences.flatMap { occurrence -> parseFlatJsonMap(occurrence.dynamicFieldsJson).filterValues { it.isNotBlank() }.keys }
            .distinct()
            .filter { it in DarwinCoreCatalog.byTerm && it !in DarwinCoreCatalog.nonCoreTerms }
    }

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
                "timestamp" to knownTime(point.timestamp),
                "speed" to decimal(point.speed),
                "geodeticDatum" to track.geodeticDatum
            ).withVerbatim(point.latitude, point.longitude, track.geodeticDatum)
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
        root.put("softwareCitation", AppCitation.apa(appVersion))
        return root.toString(2)
    }

    fun mediaJson(): String = JSONObject()
        .put("project", JSONObject(projectMap()))
        .put("exportedAt", exportedAt())
        .put("media", JSONArray(mediaLinks.map { JSONObject(it.csvMap()) }))
        .toString(2)

    fun geoJson(): String {
        val features = JSONArray()
        // Mesma coordenada das planilhas: a propria ou, sem ela, a da expedicao.
        occurrences.forEach { occurrence ->
            val session = sessionById[occurrence.sessionId]
            val place = placeOf(occurrence, session)
            if (place.latitude == null || place.longitude == null) return@forEach
            val links = linksByOccurrence[occurrence.id].orEmpty()
            val properties = JSONObject()
                .put("occurrenceID", occurrence.occurrenceId)
                .put("eventID", session?.eventId.orEmpty())
                .put("scientificName", occurrence.scientificName)
                // Data do registro, nao a da expedicao (antes era sempre a da expedicao).
                .put("eventDate", eventDateOf(occurrence, session))
                .put("coordinateUncertaintyInMeters", decimal(place.uncertainty))
                .put("geodeticDatum", place.datum)
                .put("associatedMedia", associatedMedia(links))
                .put("mediaCount", links.size)
                .put("photoCount", links.count { it.type == "photo" })
                .put("audioCount", links.count { it.type == "audio" })
            linkedMapOf<String, String>().withVerbatim(place.latitude, place.longitude, place.datum)
                .forEach { (key, value) -> properties.put(key, value) }
            val geometry = JSONObject()
                .put("type", "Point")
                .put("coordinates", JSONArray(listOf(place.longitude, place.latitude)))
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
                        listOf("warnings", validationWarnings()),
                        listOf("comoCitarOApp", AppCitation.apa(appVersion))
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
                listOf("warnings", validationWarnings()),
                listOf("comoCitarOApp", AppCitation.apa(appVersion))
            )
        )
        return XlsxWriter.write(sheets)
    }

    fun zipTo(output: OutputStream) {
        val root = "Projeto_${OikosExporter.safeName(project.name)}"
        ZipOutputStream(output).use { zip ->
            zip.put("$root/README.txt", readme())
            zip.put("$root/COMO_CITAR.txt", AppCitation.readme(appVersion))
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
            if (content != ExportContent.MediaOnly) {
                // Pacote pronto para IPT/GBIF/SiBBr, dentro do ZIP completo.
                zip.put("$root/04_darwin_core_archive/dwca.zip", dwcArchiveBytes())
            }
            // Copia em fluxo: nenhum arquivo de midia passa inteiro pela memoria.
            mediaLinks.forEach { link ->
                val source = File(link.sourcePath)
                if (source.exists()) {
                    zip.putNextEntry(ZipEntry("$root/${link.relativePath}"))
                    source.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /**
     * Darwin Core Archive pronto para IPT, GBIF e SiBBr.
     *
     * O core leva apenas termos com URI na TDWG. Campos proprios do Oikos e
     * medidas vao para a extensao MeasurementOrFact, e as midias para a Simple
     * Multimedia do GBIF. As colunas auxiliares de UTM ficam de fora do archive
     * (permanecem nas planilhas), porque nao sao termos do padrao.
     */
    fun dwcArchiveBytes(): ByteArray {
        val auxiliares = setOf("utmZone", "utmHemisphere", "utmEasting", "utmNorthing")
        val cabecalhoCore = occurrenceHeaders()
            .filterNot { it in DarwinCoreCatalog.nonCoreTerms }
            .filterNot { it in auxiliares }
        val baseUrl = project.mediaBaseUrl.trim().trimEnd('/')
        val archiveLinksByOccurrence = archiveMediaLinks.filter { it.occurrenceInternalId != null }.groupBy { it.occurrenceInternalId }
        // associatedMedia no archive leva os mesmos links publicos da extensao
        // de multimidia. Antes levava caminhos do ZIP completo ("05_midias/..."),
        // que nem existem dentro do dwca.zip e o GBIF tentava ler como URL.
        val linhasCore = occurrences.map { occurrence ->
            occurrenceMap(sessionById[occurrence.sessionId], occurrence, emptyList()).toMutableMap().apply {
                this["associatedMedia"] = if (baseUrl.isBlank()) "" else archiveLinksByOccurrence[occurrence.id].orEmpty().joinToString(" | ") { publicUrl(baseUrl, it) }
            }
        }

        // Tudo que ficou de fora do core e tem valor vira MeasurementOrFact.
        // Os campos measurement* preenchidos na ocorrencia formam uma linha propria.
        val linhasMof = occurrences.flatMap { occurrence ->
            val dinamicos = parseFlatJsonMap(occurrence.dynamicFieldsJson)
            val atributos = dinamicos
                .filter { (termo, valor) ->
                    valor.isNotBlank() && termo !in cabecalhoCore && termo !in DarwinCoreCatalog.measurementTerms
                }
                .map { (termo, valor) ->
                    mapOf(
                        "occurrenceID" to occurrence.occurrenceId,
                        "measurementType" to (DarwinCoreCatalog.byTerm[termo]?.labelEn ?: termo),
                        "measurementValue" to normalized(termo, valor),
                        "measurementUnit" to unitForMeasurement(termo),
                        "measurementRemarks" to "Oikos FieldBook: $termo"
                    )
                }
            val medidaDeclarada = dinamicos["measurementType"].orEmpty()
            val valorDeclarado = normalized("measurementValue", dinamicos["measurementValue"].orEmpty())
            val declarada = if (medidaDeclarada.isNotBlank() && valorDeclarado.isNotBlank()) {
                listOf(
                    mapOf(
                        "occurrenceID" to occurrence.occurrenceId,
                        "measurementType" to medidaDeclarada,
                        "measurementValue" to valorDeclarado,
                        "measurementUnit" to dinamicos["measurementUnit"].orEmpty(),
                        "measurementMethod" to dinamicos["measurementMethod"].orEmpty(),
                        "measurementDeterminedBy" to dinamicos["measurementDeterminedBy"].orEmpty(),
                        "measurementDeterminedDate" to dinamicos["measurementDeterminedDate"].orEmpty(),
                        "measurementAccuracy" to normalized("measurementAccuracy", dinamicos["measurementAccuracy"].orEmpty()),
                        "measurementRemarks" to ""
                    )
                )
            } else emptyList()
            atributos + declarada
        } + archiveLinksByOccurrence.values.flatten().flatMap { link ->
            // Condicoes de cada gravacao viram medidas ligadas a ocorrencia:
            // temperatura e umidade alteram parametros de vocalizacao.
            listOfNotNull(
                link.airTemperature.takeIf { it.isNotBlank() }?.let { Triple("air temperature during audio recording", it, "°C") },
                link.relativeHumidity.takeIf { it.isNotBlank() }?.let { Triple("relative humidity during audio recording", it, "%") },
                link.distanceToSourceMeters.takeIf { it.isNotBlank() }?.let { Triple("distance to sound source", it, "m") }
            ).map { (type, value, unit) ->
                mapOf(
                    "occurrenceID" to link.occurrenceID,
                    "measurementType" to type,
                    "measurementValue" to value,
                    "measurementUnit" to unit,
                    "measurementRemarks" to "Oikos FieldBook: gravação ${link.fileName}"
                )
            }
        }

        // A extensao de multimidia exige URL publica (dc:identifier); caminho
        // dentro do ZIP nao resolve em lugar nenhum. Sem URL base no projeto,
        // a extensao fica de fora e a exportacao avisa.
        val linhasMidia = if (baseUrl.isBlank()) emptyList() else archiveMediaLinks
            .filter { it.occurrenceID.isNotBlank() }
            .map { link ->
                mapOf(
                    "occurrenceID" to link.occurrenceID,
                    "type" to if (link.type == "audio") "Sound" else "StillImage",
                    "format" to link.mimeType,
                    "identifier" to publicUrl(baseUrl, link),
                    "title" to link.mediaSubject,
                    "description" to listOf(link.caption, link.technicalSummary()).filter { it.isNotBlank() }.joinToString(" | "),
                    "created" to link.capturedAt
                )
            }

        // Retangulo com as mesmas coordenadas do core (inclusive as herdadas da
        // expedicao) e datas dos registros, nao so das expedicoes.
        val pontos = occurrences.map { placeOf(it, sessionById[it.sessionId]) }.filter { it.latitude != null && it.longitude != null }
        val datas = (sessions.map { it.eventDate } + occurrences.map { eventDateOf(it, sessionById[it.sessionId]) })
            .flatMap { it.split('/') }
            .map { it.trim().take(10) }
            .filter { DATE_ONLY.matches(it) }
            .sorted()

        val metadata = ArchiveMetadata(
            datasetTitle = project.name,
            creatorName = project.principalInvestigator.trim(),
            organization = project.institution.trim(),
            abstractText = project.description.ifBlank {
                "Conjunto de dados de ocorrencia coletado com o Oikos FieldBook."
            },
            licenseCode = project.license,
            methods = sessions.mapNotNull { it.samplingProtocol.ifBlank { null } }
                .distinct()
                .joinToString("; ")
                .ifBlank { "Protocolo de amostragem nao informado." },
            packageId = "oikos-fieldbook/PROJ-${project.id.toString().padStart(3, '0')}",
            pubDate = LocalDate.now().toString(),
            geographicDescription = sessions.mapNotNull { it.locality.ifBlank { null } }
                .distinct()
                .take(10)
                .joinToString("; ")
                .ifBlank { "Localidade nao informada." },
            language = if (project.language.startsWith("en")) "eng" else "por",
            westBound = pontos.minOfOrNull { it.longitude ?: 0.0 },
            eastBound = pontos.maxOfOrNull { it.longitude ?: 0.0 },
            southBound = pontos.minOfOrNull { it.latitude ?: 0.0 },
            northBound = pontos.maxOfOrNull { it.latitude ?: 0.0 },
            beginDate = datas.firstOrNull().orEmpty(),
            endDate = datas.lastOrNull().orEmpty(),
            taxa = occurrences.mapNotNull { it.scientificName.ifBlank { null } }.distinct().sorted(),
            softwareCitation = "Dados registrados com o aplicativo Oikos FieldBook, versão $appVersion. " + AppCitation.apa(appVersion)
        )

        return DarwinCoreArchive.build(
            occurrenceHeaders = cabecalhoCore,
            occurrenceRows = linhasCore,
            measurementHeaders = archiveMeasurementHeaders,
            measurementRows = linhasMof,
            multimediaHeaders = archiveMultimediaHeaders,
            multimediaRows = linhasMidia,
            metadata = metadata
        )
    }

    fun validationWarnings(format: ExportFormat? = null): String {
        val warnings = mutableListOf<String>()
        if (format == ExportFormat.DwcArchive || format == ExportFormat.Zip) {
            if (project.license.isBlank()) {
                warnings += "Licença do conjunto não definida: o GBIF exige CC0, CC BY ou CC BY-NC (defina em Editar projeto)."
            }
            if (project.mediaBaseUrl.isBlank() && mediaLinks.any { it.occurrenceID.isNotBlank() }) {
                warnings += "Mídias fora do Darwin Core Archive: o padrão exige URL pública. Informe a URL das mídias em Editar projeto."
            }
        }
        if (occurrences.isEmpty()) warnings += "Nenhuma ocorrência encontrada para exportar."
        val withoutId = occurrences.count { it.occurrenceId.isBlank() }
        if (withoutId > 0) warnings += "$withoutId ocorrência(s) sem occurrenceID."
        val withoutCoordinates = occurrences.count { placeOf(it, sessionById[it.sessionId]).latitude == null }
        if (withoutCoordinates > 0) warnings += "$withoutCoordinates ocorrência(s) sem coordenadas (nem própria, nem da expedição); elas não aparecem no GeoJSON."
        val inherited = occurrences.count { (it.latitude == null || it.longitude == null) && placeOf(it, sessionById[it.sessionId]).latitude != null }
        if (inherited > 0) warnings += "$inherited ocorrência(s) sem coordenada própria saem com o ponto da expedição."
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
            "COMO_CITAR.txt",
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
                "02_tabelas_de_apoio/measurement_or_fact.csv",
                "04_darwin_core_archive/dwca.zip"
            )
        }
        files += mediaLinks.filter { File(it.sourcePath).exists() }.map { it.relativePath }
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
            .put("warnings", JSONArray(validationWarnings(ExportFormat.Zip).lines().filter { it.isNotBlank() }))
            .toString(2)
    }

    private fun readme(): String = """
        Oikos FieldBook - Exportação completa

        Projeto: ${project.name}
        Exportado em: ${exportedAt()}
        Datum: ${project.defaultDatum}
        Coordenadas internas/Darwin Core: Graus Decimais (decimalLatitude, decimalLongitude)
        Altitude: metros sobre o nível do mar. O GPS mede a altura sobre o elipsoide WGS84; o app a converte pelo geoide EGM2008, e esses registros trazem verticalDatum = EGM2008.
        Formato configurado no projeto: ${project.coordinateFormat}
        ${if (project.coordinateFormat == "UTM") "Coordenadas UTM também aparecem em verbatimCoordinates, verbatimCoordinateSystem, verbatimSRS, utmZone, utmHemisphere, utmEasting e utmNorthing." else ""}

        Estrutura:
        - 01_planilha_principal/: arquivos principais para análise. O CSV Darwin Core é a tabela principal de ocorrências; o XLSX reúne projeto, expedições, ocorrências, mídias, trilhas e campos usados em abas.
        - 02_tabelas_de_apoio/: tabelas auxiliares, como expedições, mídias, pontos da trilha GPS e MeasurementOrFact.
        - 03_sig_qgis/: GeoJSON para abrir no QGIS, com ocorrências como Point e trilhas como LineString.
        - 04_darwin_core_archive/dwca.zip: pacote Darwin Core Archive (meta.xml, eml.xml, occurrence.txt e extensões) para IPT, GBIF e SiBBr.
        - 05_midias/fotos/: fotografias exportadas com nomes rastreáveis.
        - 05_midias/audios/: áudios exportados com nomes rastreáveis.
        - manifest.json: resumo da exportação e relação completa entre mídias e registros.

        Cada foto ou áudio está ligado a um registro por occurrenceID, ou a uma expedição por eventID.
        Consulte a coluna associatedMedia em 01_planilha_principal/ocorrencias_darwin_core.csv ou a tabela 02_tabelas_de_apoio/midias.csv.
        Como citar o aplicativo: veja COMO_CITAR.txt.
        Quando houver mais de uma mídia, associatedMedia usa caminhos relativos separados por ponto e vírgula.
        No Darwin Core Archive, associatedMedia e a extensão de multimídia usam a URL pública do projeto seguida do nome do arquivo: publique as fotos e os áudios de 05_midias/fotos e 05_midias/audios juntos, sem subpastas, nesse endereço (como num registro do Zenodo).

        Para QGIS:
        Abra 03_sig_qgis/ocorrencias_e_trilhas.geojson. A geometria usa Point em ordem longitude, latitude.
        Se o projeto estiver em UTM, os valores UTM ficam nas propriedades da camada; a geometria GeoJSON continua em longitude/latitude por padrão do formato.
        Registros sem coordenadas permanecem nas planilhas, mas não aparecem no GeoJSON.
    """.trimIndent()

    private fun occurrenceHeaders(): List<String> = occurrenceHeaderList

    private val occurrenceHeaderList: List<String> by lazy {
        val configured = fields.filter { it.enabled }.map { it.dwcTerm }
        val utmHeaders = when (project.coordinateFormat) {
            "UTM" -> utmExportHeaders
            "DMS", "DMM" -> verbatimExportHeaders
            else -> emptyList()
        }
        (DarwinCoreCatalog.coreOccurrenceTerms + configured + standardTermsWithData + utmHeaders + "associatedMedia").distinct()
    }

    private fun occurrenceMap(session: SessionEntity?, occurrence: OccurrenceEntity, links: List<MediaLink>): Map<String, String> {
        val dynamic = parseFlatJsonMap(occurrence.dynamicFieldsJson)
        val place = placeOf(occurrence, session)
        val latitude = place.latitude
        val longitude = place.longitude
        val datum = place.datum
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
            "eventDate" to eventDateOf(occurrence, session, dynamic),
            "eventTime" to dynamic["eventTime"].orEmpty().ifBlank { session?.let { timeRange(it) }.orEmpty() },
            "samplingProtocol" to session?.samplingProtocol.orEmpty(),
            "samplingEffort" to session?.samplingEffort.orEmpty(),
            "habitat" to session?.habitat.orEmpty(),
            "locality" to session?.locality.orEmpty(),
            "municipality" to session?.municipality.orEmpty(),
            "stateProvince" to session?.stateProvince.orEmpty(),
            "country" to session?.country.orEmpty(),
            "decimalLatitude" to decimal(latitude),
            "decimalLongitude" to decimal(longitude),
            "coordinateUncertaintyInMeters" to decimal(place.uncertainty),
            "geodeticDatum" to datum,
            "minimumElevationInMeters" to decimal(place.altitude),
            "occurrenceRemarks" to occurrence.occurrenceRemarks,
            "fieldNotes" to occurrence.fieldNotes,
            "associatedMedia" to associatedMedia(links)
        )
        base.withVerbatim(latitude, longitude, datum)
        dynamic.forEach { (key, value) -> base[key] = normalized(key, value) }
        fields.filter { it.enabled && !base.containsKey(it.dwcTerm) }.forEach { base[it.dwcTerm] = "" }
        return base
    }

    private fun sessionMap(session: SessionEntity): Map<String, String> =
        linkedMapOf(
            "projectID" to "PROJ-${project.id.toString().padStart(3, '0')}",
            "eventID" to session.eventId,
            "eventDate" to session.eventDate,
            "eventTime" to timeRange(session),
            "locality" to session.locality,
            "habitat" to session.habitat,
            "parentEventID" to session.parentEventId,
            "samplingProtocol" to session.samplingProtocol,
            "samplingEffort" to session.samplingEffort,
            "sampleSizeValue" to decimal(session.sampleSizeValue),
            "sampleSizeUnit" to session.sampleSizeUnit,
            "verbatimEventDate" to session.verbatimEventDate,
            "recordedBy" to session.recordedBy,
            "decimalLatitude" to decimal(session.latitude),
            "decimalLongitude" to decimal(session.longitude),
            "coordinateUncertaintyInMeters" to decimal(session.coordinateUncertaintyInMeters),
            "geodeticDatum" to session.geodeticDatum,
            "eventRemarks" to session.notes
        ).withVerbatim(session.latitude, session.longitude, session.geodeticDatum)

    private fun projectMap(): Map<String, String> = mapOf(
        "projectID" to "PROJ-${project.id.toString().padStart(3, '0')}",
        "projectName" to project.name,
        "description" to project.description,
        "institution" to project.institution,
        "principalInvestigator" to project.principalInvestigator,
        "language" to project.language,
        "defaultDatum" to project.defaultDatum,
        "coordinateFormat" to project.coordinateFormat,
        "photoQuality" to project.photoQuality
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
        "startTime" to knownTime(track.startTime),
        "endTime" to track.endTime?.let { millis(it) }.orEmpty(),
        "totalDistance" to decimal(track.totalDistance),
        "duration" to if (track.startTime > 0L) track.duration.toString() else "",
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
                "timestamp" to knownTime(point.timestamp),
                "speed" to decimal(point.speed),
                "geodeticDatum" to track.geodeticDatum
            ).withVerbatim(point.latitude, point.longitude, track.geodeticDatum)
        }
    }

    /**
     * Coordenada no formato escolhido pelo projeto, em dwc:verbatimCoordinates.
     * O valor oficial continua em graus decimais (decimalLatitude/Longitude);
     * UTM, GMS e GMD sao a forma "como lida", que e o que o padrao preve.
     * Ate a v0.2.0 so UTM era exportado; GMS/GMD podiam ser escolhidos e nao
     * apareciam em lugar nenhum.
     */
    private fun MutableMap<String, String>.withVerbatim(latitude: Double?, longitude: Double?, datum: String): MutableMap<String, String> {
        if (project.coordinateFormat == "DMS" || project.coordinateFormat == "DMM") {
            val verbatim = CoordinateUtils.verbatim(latitude, longitude, project.coordinateFormat)
            this["verbatimCoordinates"] = verbatim?.first.orEmpty()
            this["verbatimCoordinateSystem"] = verbatim?.second.orEmpty()
            this["verbatimSRS"] = if (verbatim != null) datum else ""
            return this
        }
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
        // Decimetro: o metro com 8 casas sugeria uma precisao de micrometro.
        val easting = String.format(Locale.US, "%.1f", utm.easting)
        val northing = String.format(Locale.US, "%.1f", utm.northing)
        // Zona com a letra da faixa de latitude, como no exemplo do Darwin Core
        // ("17T 630000 4833400"). Antes ia "23S" querendo dizer "sul", mas S e
        // tambem a faixa de 32 a 40 graus NORTE; no archive, onde a coluna
        // utmHemisphere nao entra, nada desfazia a ambiguidade.
        this["verbatimCoordinates"] = "${utm.zone}${CoordinateUtils.utmBand(latitude ?: 0.0)} $easting $northing"
        this["utmZone"] = utm.zone.toString()
        this["utmHemisphere"] = hemisphere
        this["utmEasting"] = easting
        this["utmNorthing"] = northing
        return this
    }

    /** Hora da expedicao como intervalo ISO 8601 ("08:00/10:30") quando ha termino. */
    private fun timeRange(session: SessionEntity): String =
        if (session.endTime.isNotBlank() && session.startTime.isNotBlank()) "${session.startTime}/${session.endTime}"
        else session.startTime

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
                        "measurementValue" to normalized(key, value),
                        "measurementUnit" to unitForMeasurement(key),
                        "measurementRemarks" to "Campo dinamico opcional do Oikos FieldBook"
                    )
                }
        }
    }

    /**
     * Unidade da medida. Vem do catalogo, onde o termo a declara; o fallback por
     * substring do nome ficou como ultimo recurso para campos livres antigos.
     */
    private fun unitForMeasurement(key: String): String {
        val declarada = DarwinCoreCatalog.byTerm[key]?.unit.orEmpty()
        return declarada.ifBlank { unitGuessedFromName(key) }
    }

    private fun unitGuessedFromName(key: String): String = when {
        key.contains("temperature", ignoreCase = true) -> "Celsius"
        key.contains("humidity", ignoreCase = true) -> "percent"
        key.contains("height", ignoreCase = true) -> "meter"
        key.contains("distance", ignoreCase = true) -> "meter"
        else -> ""
    }

    private fun buildMediaLinks(media: List<MediaEntity>): List<MediaLink> {
        return media.map { item ->
            val occurrence = item.occurrenceId?.let { occurrenceById[it] }
            val session = sessionById[item.sessionId]
            val kind = kindFor(item)
            val extension = extensionFor(item, kind)
            val folder = if (item.mediaType == "audio") "audios" else "fotos"
            val fileName = uniqueMediaFileName(item, occurrence, session, extension)
            val relativePath = "05_midias/$folder/$fileName"
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
                mimeType = kind?.mime ?: item.mimeType,
                // Em branco quando a data de captura nao e conhecida (arquivo importado sem data).
                capturedAt = if (item.captureTimeKnown) millis(item.createdAt) else "",
                decimalLatitude = decimal(item.latitude),
                decimalLongitude = decimal(item.longitude),
                coordinateUncertaintyInMeters = decimal(item.coordinateUncertaintyInMeters),
                caption = item.caption,
                mediaSubject = item.mediaSubject,
                bodyPartOrPlantPart = item.bodyPartOrPlantPart,
                behaviorContext = item.behaviorContext,
                notes = item.notes,
                sourcePath = item.filePath,
                durationSeconds = item.durationMillis?.let { decimal(it / 1000.0) }.orEmpty(),
                sampleRateHz = item.sampleRateHz?.toString().orEmpty(),
                bitsPerSample = item.bitsPerSample?.toString().orEmpty(),
                channels = item.channels?.toString().orEmpty(),
                audioSource = item.audioSource,
                captureDevice = item.captureDevice,
                microphone = item.microphone,
                airTemperature = decimal(item.airTemperature),
                relativeHumidity = decimal(item.relativeHumidity),
                distanceToSourceMeters = decimal(item.distanceToSourceMeters)
            )
        }
    }

    private fun uniqueMediaFileName(
        media: MediaEntity,
        occurrence: OccurrenceEntity?,
        session: SessionEntity?,
        extension: String
    ): String {
        // A especie e a de AGORA: corrigir a identificacao antes de exportar
        // renomeia o arquivo exportado. O numero da midia no fim (MED000123)
        // nunca muda e torna o nome unico; antes era um contador -01/-02 que
        // dependia da ordem das midias.
        val base = safeToken(exportMediaBaseName(media, occurrence, session))
        return "${base}_MED${media.id.toString().padStart(6, '0')}.$extension"
    }

    private fun exportMediaBaseName(media: MediaEntity, occurrence: OccurrenceEntity?, session: SessionEntity?): String {
        // Data e hora da captura, no mesmo fuso (o do aparelho). Antes a data era
        // a da expedicao (ou UTC) e a hora local: um registro noturno ganhava um
        // instante que nao existiu.
        val local = java.time.Instant.ofEpochMilli(media.createdAt).atZone(java.time.ZoneId.systemDefault())
        val date = if (media.captureTimeKnown) local.toLocalDate().toString() else "SEM-DATA"
        val time = if (media.captureTimeKnown) DateTimeFormatter.ofPattern("HHmm", Locale.US).format(local.toLocalTime()) else ""
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

    /**
     * Extensao e tipo pelo conteudo do arquivo. Antes todo audio que nao fosse
     * MP3 saia ".wav": um M4A importado ia com extensao errada e o Raven e
     * programas parecidos recusavam o arquivo.
     */
    private fun kindFor(media: MediaEntity): MediaKind? = MediaKind.detect(File(media.filePath))

    private fun extensionFor(media: MediaEntity, kind: MediaKind?): String {
        kind?.let { return it.extension }
        val own = media.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (own in KNOWN_MEDIA_EXTENSIONS) return if (own == "jpeg") "jpg" else own
        return when (media.mimeType.lowercase(Locale.ROOT)) {
            "audio/mpeg" -> "mp3"
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a"
            "audio/aac" -> "aac"
            "audio/ogg" -> "ogg"
            "audio/flac" -> "flac"
            "audio/amr" -> "amr"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic" -> "heic"
            else -> if (media.mediaType == "audio") "wav" else "jpg"
        }
    }

    private fun publicUrl(baseUrl: String, link: MediaLink): String =
        "$baseUrl/${java.net.URLEncoder.encode(link.fileName, "UTF-8").replace("+", "%20")}"

    private fun associatedMedia(links: List<MediaLink>): String = links.joinToString("; ") { it.relativePath }

    /**
     * CSV para planilha, em um de dois padroes coerentes, escolhido no projeto:
     *
     * - "br" (padrao): BOM de UTF-8, separador ";" e VIRGULA decimal. E o que o
     *   Excel em portugues abre por duplo clique sem quebrar acento nem numero,
     *   e o que o read.csv2() do R le direto.
     * - "intl": sem BOM, separador "," e ponto decimal, para R/Python/QGIS.
     *
     * Ate a v0.2.0 o arquivo misturava ";" com ponto decimal: no Excel pt-BR,
     * "12.345" virava 12345 (ponto lido como milhar). Os arquivos do Darwin Core
     * Archive nao passam por aqui: sao TSV com ponto decimal, como o padrao exige.
     */
    private fun csv(headers: List<String>, rows: List<Map<String, String>>): String = buildString {
        val brazilian = project.csvStyle != "intl"
        val separator = if (brazilian) ";" else ","
        if (brazilian) append(UTF8_BOM)
        appendLine(headers.joinToString(separator) { csvCell(it, separator) })
        rows.forEach { row ->
            appendLine(headers.joinToString(separator) { header ->
                val value = row[header].orEmpty()
                // So colunas numericas: antes um fieldNumber "3.2" virava "3,2".
                csvCell(if (brazilian && header in numericColumns) brazilianDecimal(value) else value, separator)
            })
        }
    }

    private fun table(rows: List<Map<String, String>>): List<List<String>> {
        if (rows.isEmpty()) return listOf(emptyList())
        val headers = rows.flatMap { it.keys }.distinct()
        return listOf(headers) + rows.map { row -> headers.map { row[it].orEmpty() } }
    }

    private fun exportedAt(): String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    private fun millis(value: Long): String = java.time.Instant.ofEpochMilli(value).toString()

    /** Horario de trilha; 0 = trilha importada de arquivo sem horario, sai em branco. */
    private fun knownTime(value: Long): String = if (value > 0L) millis(value) else ""
    private fun decimal(value: Double?): String = value?.let { String.format(Locale.US, "%.8f", it).trimEnd('0').trimEnd('.') }.orEmpty()
    private fun csvCell(value: String, separator: String): String {
        val escaped = value.replace("\"", "\"\"")
        val needsQuotes = escaped.contains(separator) || escaped.any { it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"$escaped\"" else escaped
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
        private val verbatimExportHeaders = listOf("verbatimCoordinates", "verbatimCoordinateSystem", "verbatimSRS")
        private val utmExportHeaders = verbatimExportHeaders + listOf("utmZone", "utmHemisphere", "utmEasting", "utmNorthing")
        private val sessionHeaders = listOf("projectID", "eventID", "parentEventID", "eventDate", "verbatimEventDate", "eventTime", "locality", "habitat", "samplingProtocol", "samplingEffort", "sampleSizeValue", "sampleSizeUnit", "recordedBy", "decimalLatitude", "decimalLongitude", "coordinateUncertaintyInMeters", "geodeticDatum") + utmExportHeaders + "eventRemarks"
        // bodyPartOrPlantPart, behaviorContext e notes saiam repetindo
        // literalmente mediaSubject e caption; a repeticao afirmava sobre o dado
        // algo que nao era verdade e foi removida da exportacao.
        private val mediaHeaders = listOf("mediaID", "projectID", "eventID", "occurrenceID", "mediaType", "fileName", "relativePath", "mimeType", "capturedAt", "decimalLatitude", "decimalLongitude", "coordinateUncertaintyInMeters", "caption", "mediaSubject", "associatedMedia", "durationSeconds", "sampleRateHz", "bitsPerSample", "channels", "audioSource", "captureDevice", "microphone", "airTemperature", "relativeHumidity", "distanceToSourceMeters")
        private val trackPointHeaders = listOf("trackID", "projectID", "eventID", "pointIndex", "decimalLatitude", "decimalLongitude", "altitude", "accuracy", "timestamp", "speed", "geodeticDatum") + utmExportHeaders
        private val measurementHeaders = listOf("occurrenceID", "eventID", "measurementType", "measurementValue", "measurementUnit", "measurementRemarks")

        /** BOM de UTF-8: faz o Excel pt-BR reconhecer a codificacao. */
        private const val UTF8_BOM = "\uFEFF"

        /** Numero com ponto decimal, como o app grava internamente. */
        private val DECIMAL_NUMBER = Regex("^-?\\d+\\.\\d+$")

        /** Numero com virgula decimal, como se digita em portugues. */
        private val COMMA_DECIMAL = Regex("^-?\\d+,\\d+$")

        private val DATE_ONLY = Regex("^\\d{4}-\\d{2}-\\d{2}$")

        private val KNOWN_MEDIA_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "wav", "mp3", "m4a", "aac", "ogg", "flac", "amr")

        /** "12.5" vira "12,5" no CSV brasileiro; texto e inteiro ficam como estao. */
        fun brazilianDecimal(value: String): String =
            if (DECIMAL_NUMBER.matches(value)) value.replace('.', ',') else value

        /** MeasurementOrFact do archive: coreid na coluna 0. */
        private val archiveMeasurementHeaders = listOf(
            "occurrenceID", "measurementType", "measurementValue", "measurementUnit",
            "measurementMethod", "measurementDeterminedBy", "measurementDeterminedDate",
            "measurementAccuracy", "measurementRemarks"
        )

        /** Simple Multimedia do GBIF: coreid na coluna 0. */
        private val archiveMultimediaHeaders = listOf(
            "occurrenceID", "type", "format", "identifier", "title", "description", "created"
        )
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
    val sourcePath: String,
    val durationSeconds: String = "",
    val sampleRateHz: String = "",
    val bitsPerSample: String = "",
    val channels: String = "",
    val audioSource: String = "",
    val captureDevice: String = "",
    val microphone: String = "",
    val airTemperature: String = "",
    val relativeHumidity: String = "",
    val distanceToSourceMeters: String = ""
) {
    /** Parametros tecnicos em texto, para a descricao da midia no DwC-A. */
    fun technicalSummary(): String = listOfNotNull(
        durationSeconds.takeIf { it.isNotBlank() }?.let { "$it s" },
        sampleRateHz.takeIf { it.isNotBlank() }?.let { "$it Hz" },
        bitsPerSample.takeIf { it.isNotBlank() }?.let { "$it bit" },
        channels.takeIf { it.isNotBlank() }?.let { "$it canal(is)" },
        audioSource.takeIf { it.isNotBlank() }?.let { "fonte $it" },
        captureDevice.takeIf { it.isNotBlank() },
        microphone.takeIf { it.isNotBlank() }?.let { "microfone: $it" }
    ).joinToString("; ")

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
        "associatedMedia" to relativePath,
        "durationSeconds" to durationSeconds,
        "sampleRateHz" to sampleRateHz,
        "bitsPerSample" to bitsPerSample,
        "channels" to channels,
        "audioSource" to audioSource,
        "captureDevice" to captureDevice,
        "microphone" to microphone,
        "airTemperature" to airTemperature,
        "relativeHumidity" to relativeHumidity,
        "distanceToSourceMeters" to distanceToSourceMeters
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
                val ref = cellName(columnIndex, rowIndex)
                // Cabecalho sempre como texto; demais linhas viram numero quando
                // o valor e numerico, para que latitude, longitude, DAP e
                // contagens cheguem ao Excel calculaveis em vez de texto.
                if (rowIndex > 0 && isNumeric(value)) {
                    append("""<c r="$ref" t="n"><v>$value</v></c>""")
                } else {
                    append("""<c r="$ref" t="inlineStr"><is><t>${xml(value)}</t></is></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    /**
     * Numero em formato aceito pelo OpenXML: ponto decimal, sem separador de
     * milhar e sem notacao cientifica ambigua. Strings como "01" ou "2026-09-19"
     * continuam texto de proposito, para nao perder zeros a esquerda nem virar data.
     */
    private fun isNumeric(value: String): Boolean = STRICT_NUMBER.matches(value)

    /**
     * Antes valia qualquer coisa que o Kotlin aceitasse como Double: "5d",
     * "12F", "NaN" e "Infinity" entravam como numero e o Excel recusava o
     * arquivo. Inteiro com mais de 15 digitos (codigo de barras, tombo) fica
     * texto, porque o Excel arredonda o resto.
     */
    private val STRICT_NUMBER = Regex("^-?(0|[1-9]\\d{0,14})(\\.\\d+)?$")

    private fun cellName(columnIndex: Int, rowIndex: Int): String {
        var n = columnIndex
        val col = StringBuilder()
        do {
            col.insert(0, ('A'.code + (n % 26)).toChar())
            n = n / 26 - 1
        } while (n >= 0)
        return "$col${rowIndex + 1}"
    }

    private fun xml(value: String): String = xmlSafe(value)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

/**
 * Remove caracteres que o XML 1.0 nao admite (controles de U+0000 a U+001F,
 * exceto tab, LF e CR; U+FFFE e U+FFFF; surrogates soltos). Um deles, colado
 * de outro app, deixava a planilha corrompida ou o eml.xml invalido.
 */
internal fun xmlSafe(value: String): String {
    if (value.none { it < ' ' || it in '\uD800'..'\uDFFF' || it == '￾' || it == '￿' }) return value
    val out = StringBuilder(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        when {
            c == '\t' || c == '\n' || c == '\r' -> out.append(c)
            c < ' ' || c == '￾' || c == '￿' -> Unit
            Character.isHighSurrogate(c) && i + 1 < value.length && Character.isLowSurrogate(value[i + 1]) -> {
                out.append(c).append(value[i + 1])
                i++
            }
            Character.isSurrogate(c) -> Unit
            else -> out.append(c)
        }
        i++
    }
    return out.toString()
}

