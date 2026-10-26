package com.oikos.fieldbook.export

import com.oikos.fieldbook.util.AppLanguage
import com.oikos.fieldbook.util.UiLanguage
import com.oikos.fieldbook.util.inProjectLanguage

import com.oikos.fieldbook.data.FieldConfigEntity
import com.oikos.fieldbook.data.MediaCodes
import com.oikos.fieldbook.data.MediaEntity
import com.oikos.fieldbook.data.OccurrenceEntity
import com.oikos.fieldbook.data.ProjectEntity
import com.oikos.fieldbook.data.SessionEntity
import com.oikos.fieldbook.dwc.DarwinCoreCatalog
import com.oikos.fieldbook.dwc.spanishFieldLabels
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

/** Pastas e arquivos do ZIP completo, no idioma do projeto (portugues por padrao). */
internal class ExportPaths private constructor(
    val root: String,
    val howToCite: String,
    val main: String,
    val support: String,
    val gis: String,
    val dwca: String,
    val media: String,
    val photos: String,
    val audios: String,
    val occurrencesCsv: String,
    val workbook: String,
    val projectJson: String,
    val geojson: String,
    val sessionsCsv: String,
    val trackCsv: String,
    val mofCsv: String,
    val mediaCsv: String
) {
    companion object {
        val PT = ExportPaths(
            "Projeto_", "COMO_CITAR.txt", "01_planilha_principal", "02_tabelas_de_apoio", "03_sig_qgis", "04_darwin_core_archive",
            "05_midias", "fotos", "audios", "ocorrencias_darwin_core.csv", "oikos_fieldbook_exportacao_completa.xlsx",
            "projeto_completo.json", "ocorrencias_e_trilhas.geojson", "expedicoes.csv", "trilha_gps_pontos.csv",
            "measurement_or_fact.csv", "midias.csv"
        )
        val EN = ExportPaths(
            "Project_", "HOW_TO_CITE.txt", "01_main_spreadsheet", "02_supporting_tables", "03_gis_qgis", "04_darwin_core_archive",
            "05_media", "photos", "audios", "occurrences_darwin_core.csv", "oikos_fieldbook_full_export.xlsx",
            "full_project.json", "occurrences_and_tracks.geojson", "expeditions.csv", "gps_track_points.csv",
            "measurement_or_fact.csv", "media.csv"
        )
        // Sem acentos nos nomes de pasta e arquivo, como no portugues.
        val ES = ExportPaths(
            "Proyecto_", "COMO_CITAR.txt", "01_planilla_principal", "02_tablas_de_apoyo", "03_sig_qgis", "04_darwin_core_archive",
            "05_multimedia", "fotos", "audios", "registros_darwin_core.csv", "oikos_fieldbook_exportacion_completa.xlsx",
            "proyecto_completo.json", "registros_y_recorridos.geojson", "salidas_de_campo.csv", "recorrido_gps_puntos.csv",
            "measurement_or_fact.csv", "multimedia.csv"
        )

        fun of(projectLanguage: String): ExportPaths = when (AppLanguage.ofProject(projectLanguage)) {
            AppLanguage.PT -> PT
            AppLanguage.EN -> EN
            AppLanguage.ES -> ES
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
    // Antes de mediaLinks: o caminho das midias depende do idioma.
    private val language = AppLanguage.ofProject(project.language)
    private fun say(pt: String, en: String, es: String) = when (language) {
        AppLanguage.PT -> pt
        AppLanguage.EN -> en
        AppLanguage.ES -> es
    }
    private val paths = ExportPaths.of(project.language)
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
                "geodeticDatum" to DatumCodes.forExport(track.geodeticDatum)
            ).withVerbatim(point.latitude, point.longitude, DatumCodes.forExport(track.geodeticDatum))
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
                .put("geodeticDatum", DatumCodes.forExport(place.datum))
                .put("associatedMedia", associatedMedia(links))
                .put("mediaCount", links.size)
                .put("photoCount", links.count { it.type == "photo" })
                .put("audioCount", links.count { it.type == "audio" })
            linkedMapOf<String, String>().withVerbatim(place.latitude, place.longitude, DatumCodes.forExport(place.datum))
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
                    sheet("Mídias", "Media", "Multimedia") to table(mediaLinks.map { it.csvMap() }),
                    sheet("Resumo_Exportação", "Export_Summary", "Resumen_Exportación") to listOf(
                        listOf("item", say("valor", "value", "valor")),
                        listOf("projectName", project.name),
                        listOf("exportedAt", exportedAt()),
                        listOf("photos", mediaLinks.count { it.type == "photo" }.toString()),
                        listOf("audios", mediaLinks.count { it.type == "audio" }.toString()),
                        listOf("warnings", validationWarnings(language = language)),
                        listOf(say("comoCitarOApp", "howToCiteTheApp", "comoCitarLaApp"), AppCitation.apa(appVersion))
                    )
                ).map { (name, rows) -> XlsxSheet(name, rows) }
            )
        }
        val occurrenceHeaders = occurrenceHeaders()
        val sheets = listOf(
            sheet("Projeto", "Project", "Proyecto") to listOf(projectMap().keys.toList(), projectMap().values.toList()),
            sheet("Expedições", "Expeditions", "Salidas_de_campo") to table(sessions.map { sessionMap(it) }),
            sheet("Ocorrências_DarwinCore", "Occurrences_DarwinCore", "Registros_DarwinCore") to listOf(occurrenceHeaders) + occurrences.map {
                val row = occurrenceMap(sessionById[it.sessionId], it, linksByOccurrence[it.id].orEmpty())
                occurrenceHeaders.map { header -> row[header].orEmpty() }
            },
            sheet("Mídias", "Media", "Multimedia") to table(mediaLinks.map { it.csvMap() }),
            sheet("Trilha_GPS", "GPS_Track", "Recorrido_GPS") to table(trackPointRows()),
            "MeasurementOrFact" to table(measurementRows()),
            sheet("Campos_Usados", "Fields_Used", "Campos_Usados") to table(fields.map { fieldMap(it) }),
            if (language == AppLanguage.ES) {
                "Leyenda_ES_EN" to listOf(listOf("dwcTerm", "Español", "English")) +
                    fields.map { listOf(it.dwcTerm, spanishFieldLabels[it.dwcTerm] ?: it.labelPt, it.labelEn) }
            } else {
                sheet("Legenda_PT_EN", "Legend_PT_EN", "") to listOf(listOf("dwcTerm", "Português", "English")) + fields.map { listOf(it.dwcTerm, it.labelPt, it.labelEn) }
            },
            sheet("Resumo_Exportação", "Export_Summary", "Resumen_Exportación") to listOf(
                listOf("item", say("valor", "value", "valor")),
                listOf("projectName", project.name),
                listOf("exportedAt", exportedAt()),
                listOf("sessions", sessions.size.toString()),
                listOf("occurrences", occurrences.size.toString()),
                listOf("photos", mediaLinks.count { it.type == "photo" }.toString()),
                listOf("audios", mediaLinks.count { it.type == "audio" }.toString()),
                listOf("tracks", tracks.size.toString()),
                listOf("trackPoints", tracks.sumOf { it.points.size }.toString()),
                listOf("coordinateFormat", project.coordinateFormat),
                listOf("coordinateStorage", say("Graus Decimais", "Decimal degrees", "Grados decimales")),
                listOf(
                    "utmColumns",
                    when {
                        project.coordinateFormat != "UTM" -> say("Não aplicável", "Not applicable", "No aplica")
                        else -> say(
                            "Incluídas nas planilhas e properties do GeoJSON",
                            "Included in the spreadsheets and GeoJSON properties",
                            "Incluidas en las planillas y en las properties del GeoJSON"
                        )
                    }
                ),
                listOf("geodeticDatum", DatumCodes.forExport(project.defaultDatum)),
                listOf("warnings", validationWarnings(language = language)),
                listOf(say("comoCitarOApp", "howToCiteTheApp", "comoCitarLaApp"), AppCitation.apa(appVersion))
            )
        )
        // No fim, para nao mudar a posicao das abas de dados de quem ja le a planilha por indice.
        return XlsxWriter.write(sheets.map { (name, rows) -> XlsxSheet(name, rows) } + analysisSheets())
    }

    private val analysis by lazy { TaxonAnalysis(sessions, occurrences) }

    /**
     * Tres abas de analise no fim da planilha, como as abas do app: taxons
     * (matriz e familias), expedicoes (indices, curva do coletor e
     * similaridade) e tempo (meses e horas). Sem CSV a parte: a pessoa acha a
     * analise num lugar so, e os dados brutos seguem nas abas e CSVs de sempre.
     */
    private fun analysisSheets(): List<XlsxSheet> = listOf(
        XlsxSheet(
            sheet("Análise_Táxons", "Analysis_Taxa", "Análisis_Taxones"),
            listOfNotNull(taxaTable(), familiesTable()),
            report = true,
            notes = listOf(howToRead()) + taxaNotes() + familiesNotes()
        ),
        XlsxSheet(
            sheet("Análise_Expedições", "Analysis_Expeditions", "Análisis_Salidas"),
            listOfNotNull(expeditionsTable(), similarityTable()),
            report = true,
            notes = listOf(howToRead()) + expeditionsNotes() + similarityNotes(),
            chart = collectorCurve()
        ),
        XlsxSheet(
            sheet("Análise_Tempo", "Analysis_Time", "Análisis_Tiempo"),
            listOf(monthsTable(), hoursTable()),
            report = true,
            notes = listOf(howToRead()) + timeNotes()
        )
    )

    private fun howToRead() = say("Como ler", "How to read", "Cómo leer")

    /** Expedicao como aparece nas colunas e nos pares: nome e data; o eventID so desempata nomes repetidos. */
    private fun expeditionLabels(): List<String> {
        val labels = analysis.expeditions.map { expedition ->
            val session = expedition.session
            val name = session.name.trim().ifBlank { session.eventId }
            if (session.eventDate.isBlank()) name else "$name (${session.eventDate})"
        }
        return labels.mapIndexed { index, label ->
            if (labels.count { it == label } > 1) "$label [${analysis.expeditions[index].session.eventId}]" else label
        }
    }

    /** Matriz taxon x expedicao em individuos, com frequencia, constancia, abundancia e o perfil de cada taxon. */
    private fun taxaTable(): XlsxTable {
        val taxa = analysis.taxa
        val withFamily = taxa.any { it.family.isNotBlank() }
        val withVernacular = taxa.any { it.vernacularName.isNotBlank() }
        val withAltitude = taxa.any { it.minimumAltitude != null }
        val withSex = taxa.any { it.sexes.isNotEmpty() }
        val withStage = taxa.any { it.lifeStages.isNotEmpty() }
        val scientific = say("Nome científico", "Scientific name", "Nombre científico")
        val header = buildList {
            if (withFamily) add(say("Família", "Family", "Familia"))
            add(scientific)
            if (withVernacular) add(say("Nome popular", "Common name", "Nombre común"))
            addAll(expeditionLabels())
            add(say("Total de indivíduos", "Total individuals", "Total de individuos"))
            add(say("Registros", "Records", "Registros"))
            add(say("Expedições com o táxon", "Expeditions with the taxon", "Salidas con el taxón"))
            add(say("Frequência de ocorrência (%)", "Frequency of occurrence (%)", "Frecuencia de ocurrencia (%)"))
            add(say("Constância (Dajoz)", "Constancy (Dajoz)", "Constancia (Dajoz)"))
            add(say("Abundância relativa (%)", "Relative abundance (%)", "Abundancia relativa (%)"))
            if (withAltitude) {
                add(say("Altitude mínima (m)", "Minimum altitude (m)", "Altitud mínima (m)"))
                add(say("Altitude máxima (m)", "Maximum altitude (m)", "Altitud máxima (m)"))
            }
            if (withSex) add(say("Sexo", "Sex", "Sexo"))
            if (withStage) add(say("Fase de vida", "Life stage", "Etapa de vida"))
        }
        val rows = taxa.map { taxon ->
            buildList {
                if (withFamily) add(taxon.family)
                add(taxon.scientificName)
                if (withVernacular) add(taxon.vernacularName)
                addAll(taxon.individualsByExpedition.map { it.toString() })
                add(taxon.individuals.toString())
                add(taxon.records.toString())
                add(taxon.expeditionsWithTaxon.toString())
                add(percent(analysis.frequencyPercent(taxon)))
                add(constancyLabel(analysis.constancy(taxon)))
                add(percent(analysis.relativeAbundancePercent(taxon)))
                if (withAltitude) {
                    add(decimal(taxon.minimumAltitude))
                    add(decimal(taxon.maximumAltitude))
                }
                if (withSex) add(tallyText(taxon.sexes))
                if (withStage) add(tallyText(taxon.lifeStages))
            }
        }
        return XlsxTable(
            listOf(header) + rows,
            title = say("Táxons por expedição", "Taxa per expedition", "Taxones por salida de campo"),
            nameColumns = setOf(header.indexOf(scientific))
        )
    }

    private fun taxaNotes(): List<String> = buildList {
        val count = analysis.expeditions.size
        add(say(
            "Nas colunas de cada expedição: número de indivíduos. Registro sem contagem vale 1.",
            "In each expedition column: number of individuals. A record with no count is taken as 1.",
            "En cada columna de salida: número de individuos. Un registro sin conteo vale 1."
        ))
        add(say(
            "Frequência de ocorrência = expedições com o táxon ÷ $count expedições × 100.",
            "Frequency of occurrence = expeditions with the taxon ÷ $count expeditions × 100.",
            "Frecuencia de ocurrencia = salidas con el taxón ÷ $count salidas × 100."
        ))
        add(say(
            "Constância (Dajoz): constante em mais de 50% das expedições, acessória de 25% a 50%, acidental abaixo de 25%.",
            "Constancy (Dajoz): constant in more than 50% of the expeditions, accessory from 25% to 50%, accidental below 25%.",
            "Constancia (Dajoz): constante en más del 50% de las salidas, accesoria del 25% al 50%, accidental por debajo del 25%."
        ))
        add(say(
            "Abundância relativa = indivíduos do táxon ÷ total de indivíduos × 100.",
            "Relative abundance = individuals of the taxon ÷ total individuals × 100.",
            "Abundancia relativa = individuos del taxón ÷ total de individuos × 100."
        ))
        if (analysis.taxa.any { it.minimumAltitude != null }) add(say(
            "Altitude: a do registro; sem coordenada própria, a da expedição.",
            "Altitude: the record's own; without its own coordinates, the expedition's.",
            "Altitud: la del registro; sin coordenada propia, la de la salida de campo."
        ))
        add(say(
            "Cada nome científico é um táxon, com ou sem o autor; morfoespécies (sp. 1, sp. 2) contam separadas.",
            "Each scientific name is one taxon, with or without the author; morphospecies (sp. 1, sp. 2) count separately.",
            "Cada nombre científico es un taxón, con o sin el autor; las morfoespecies (sp. 1, sp. 2) cuentan por separado."
        ))
        leftOut(
            analysis.absenceRecords to Triple("de ausência", "marked absent", "de ausencia"),
            analysis.recordsWithoutName to Triple("sem nome científico", "with no scientific name", "sin nombre científico")
        )?.let { add(it) }
        when (val grouping = analysis.groupingExpeditions) {
            0 -> Unit
            1 -> add(say(
                "Fora das colunas: 1 expedição que só agrupa outras (parentEventID), sem registro próprio.",
                "Not a column: 1 expedition that only groups others (parentEventID), with no records of its own.",
                "Fuera de las columnas: 1 salida de campo que solo agrupa otras (parentEventID), sin registros propios."
            ))
            else -> add(say(
                "Fora das colunas: $grouping expedições que só agrupam outras (parentEventID), sem registro próprio.",
                "Not a column: $grouping expeditions that only group others (parentEventID), with no records of their own.",
                "Fuera de las columnas: $grouping salidas de campo que solo agrupan otras (parentEventID), sin registros propios."
            ))
        }
    }

    /** Riqueza e abundancia por familia, da mais rica para a menos; sem familia em nenhum taxon, nao ha tabela. */
    private fun familiesTable(): XlsxTable? {
        if (analysis.families.isEmpty()) return null
        val taxa = analysis.taxa.size
        val individuals = analysis.totalIndividuals
        val header = listOf(
            say("Família", "Family", "Familia"),
            say("Táxons", "Taxa", "Taxones"),
            say("Indivíduos", "Individuals", "Individuos"),
            say("Registros", "Records", "Registros"),
            say("% dos táxons", "% of taxa", "% de los taxones"),
            say("% dos indivíduos", "% of individuals", "% de los individuos")
        )
        val rows = analysis.families.map {
            listOf(
                it.name, it.taxa.toString(), it.individuals.toString(), it.records.toString(),
                percent(if (taxa == 0) 0.0 else it.taxa * 100.0 / taxa),
                percent(if (individuals == 0) 0.0 else it.individuals * 100.0 / individuals)
            )
        }
        return XlsxTable(listOf(header) + rows, title = say("Táxons por família", "Taxa per family", "Taxones por familia"))
    }

    private fun familiesNotes(): List<String> = buildList {
        if (analysis.families.isEmpty()) {
            add(say(
                "Nenhum registro tem família; ela vem junto quando o nome sai do checklist.",
                "No record has a family; it comes with the name when picked from the checklist.",
                "Ningún registro tiene familia; viene incluida al elegir el nombre en la lista de especies."
            ))
            return@buildList
        }
        add(say(
            "Família: a do registro; vem junto quando o nome sai do checklist.",
            "Family: the record's; it comes with the name when picked from the checklist.",
            "Familia: la del registro; viene incluida al elegir el nombre en la lista de especies."
        ))
        when (val withoutFamily = analysis.taxaWithoutFamily) {
            0 -> Unit
            1 -> add(say(
                "1 táxon sem família fica fora da tabela por família. As porcentagens são do total do projeto.",
                "1 taxon with no family is left out of the family table. The percentages are of the project's total.",
                "1 taxón sin familia queda fuera de la tabla por familia. Los porcentajes son del total del proyecto."
            ))
            else -> add(say(
                "$withoutFamily táxons sem família ficam fora da tabela por família. As porcentagens são do total do projeto.",
                "$withoutFamily taxa with no family are left out of the family table. The percentages are of the project's total.",
                "$withoutFamily taxones sin familia quedan fuera de la tabla por familia. Los porcentajes son del total del proyecto."
            ))
        }
    }

    /** Uma linha por expedicao, em ordem de data, com os indices de diversidade; o total traz os do projeto. */
    private fun expeditionsTable(): XlsxTable {
        val header = listOf(
            "eventID",
            say("Expedição", "Expedition", "Salida de campo"),
            say("Data", "Date", "Fecha"),
            say("Registros", "Records", "Registros"),
            say("Indivíduos", "Individuals", "Individuos"),
            say("Riqueza (táxons)", "Richness (taxa)", "Riqueza (taxones)"),
            say("Táxons novos", "New taxa", "Taxones nuevos"),
            say("Riqueza acumulada", "Cumulative richness", "Riqueza acumulada"),
            "Shannon (H')", "Simpson (1-D)", "Pielou (J')", "Berger-Parker (d)",
            say("Nº efetivo (exp H')", "Effective number (exp H')", "Nº efectivo (exp H')"),
            say("Nº efetivo (1/D)", "Effective number (1/D)", "Nº efectivo (1/D)")
        )
        val rows = analysis.expeditions.map {
            listOf(
                it.session.eventId, it.session.name, it.session.eventDate, it.records.toString(), it.individuals.toString(),
                it.richness.toString(), it.newTaxa.toString(), it.cumulativeRichness.toString()
            ) + diversityCells(it.diversity)
        }
        val total = listOf(
            "", "Total", "", analysis.recordsUsed.toString(), analysis.totalIndividuals.toString(),
            analysis.taxa.size.toString(), analysis.taxa.size.toString(), ""
        ) + diversityCells(analysis.diversity)
        return XlsxTable(
            listOf(header) + rows + listOf(total),
            title = say("Por expedição", "By expedition", "Por salida de campo"),
            totalRow = true
        )
    }

    /** Curva do coletor ao lado da tabela por expedicao: data no eixo quando toda expedicao tem; senao o nome. */
    private fun collectorCurve(): XlsxLineChart {
        val dated = analysis.expeditions.all { it.session.eventDate.isNotBlank() }
        return XlsxLineChart(
            title = say("Curva do coletor", "Collector's curve", "Curva del colector"),
            categoryColumn = if (dated) 2 else 1,
            valueColumn = 7,
            lastRow = analysis.expeditions.size,
            categoryTitle = say("Expedições em ordem de data", "Expeditions in date order", "Salidas de campo en orden de fecha"),
            valueTitle = say("Riqueza acumulada (táxons)", "Cumulative richness (taxa)", "Riqueza acumulada (taxones)")
        )
    }

    private fun expeditionsNotes(): List<String> = buildList {
        add(say("Mesmos critérios da aba Análise_Táxons.", "Same criteria as the Analysis_Taxa sheet.", "Mismos criterios que la hoja Análisis_Taxones."))
        add(say(
            "Índices com logaritmo natural, como no pacote vegan do R: Simpson = 1 − Σp²; Pielou = H' ÷ ln S; Berger-Parker = indivíduos do táxon mais abundante ÷ total.",
            "Indices use the natural logarithm, as in the R package vegan: Simpson = 1 − Σp²; Pielou = H' ÷ ln S; Berger-Parker = individuals of the most abundant taxon ÷ total.",
            "Índices con logaritmo natural, como en el paquete vegan de R: Simpson = 1 − Σp²; Pielou = H' ÷ ln S; Berger-Parker = individuos del taxón más abundante ÷ total."
        ))
        add(say(
            "Nº efetivo: quantas espécies igualmente abundantes dariam o mesmo Shannon (exp H') ou o mesmo Simpson (1/D, com D = Σp²).",
            "Effective number: how many equally abundant species would give the same Shannon (exp H') or the same Simpson (1/D, with D = Σp²).",
            "Nº efectivo: cuántas especies igualmente abundantes darían el mismo Shannon (exp H') o el mismo Simpson (1/D, con D = Σp²)."
        ))
        add(say(
            "Índice em branco: há registro sem contagem de indivíduos. A linha Total traz os índices do projeto inteiro.",
            "Blank index: a record has no individual count. The Total row has the whole project's indices.",
            "Índice en blanco: hay un registro sin conteo de individuos. La fila Total trae los índices del proyecto entero."
        ))
        if (analysis.recordsWithoutCount > 0) add(say(
            "Registros sem contagem: ${analysis.recordsWithoutCount}.",
            "Records with no count: ${analysis.recordsWithoutCount}.",
            "Registros sin conteo: ${analysis.recordsWithoutCount}."
        ))
    }

    private fun diversityCells(diversity: TaxonAnalysis.Diversity?): List<String> = listOf(
        diversity?.shannon, diversity?.simpson, diversity?.pielou, diversity?.bergerParker,
        diversity?.effectiveShannon, diversity?.effectiveSimpson
    ).map { index(it) }

    /** Cada par de expedicoes uma vez, com os taxons em comum e tres similaridades; com uma expedicao so, nao ha tabela. */
    private fun similarityTable(): XlsxTable? {
        val labels = expeditionLabels()
        if (labels.size < 2) return null
        val header = listOf(
            say("Expedição A", "Expedition A", "Salida A"),
            say("Expedição B", "Expedition B", "Salida B"),
            say("Táxons em comum", "Shared taxa", "Taxones en común"),
            "Jaccard", "Sørensen", "Bray-Curtis"
        )
        val rows = labels.indices.flatMap { first ->
            (first + 1 until labels.size).map { second ->
                val similarity = analysis.similarity(first, second)
                listOf(labels[first], labels[second], similarity.shared.toString(), index(similarity.jaccard), index(similarity.sorensen), index(similarity.brayCurtis))
            }
        }
        return XlsxTable(
            listOf(header) + rows,
            title = say("Similaridade entre expedições", "Similarity between expeditions", "Similitud entre salidas de campo")
        )
    }

    private fun similarityNotes(): List<String> = buildList {
        if (analysis.expeditions.size < 2) {
            add(say(
                "Similaridade: é preciso mais de uma expedição para comparar.",
                "Similarity: more than one expedition is needed to compare.",
                "Similitud: se necesita más de una salida de campo para comparar."
            ))
            return@buildList
        }
        add(say(
            "Similaridade de 0 (nada em comum) a 1 (iguais), como 1 − vegdist() do pacote vegan do R.",
            "Similarity from 0 (nothing in common) to 1 (identical), as 1 − vegdist() in the R package vegan.",
            "Similitud de 0 (nada en común) a 1 (iguales), como 1 − vegdist() del paquete vegan de R."
        ))
        add(say(
            "Jaccard = em comum ÷ (em comum + só em A + só em B). Sørensen = 2 × em comum ÷ (2 × em comum + só em A + só em B).",
            "Jaccard = shared ÷ (shared + only in A + only in B). Sørensen = 2 × shared ÷ (2 × shared + only in A + only in B).",
            "Jaccard = en común ÷ (en común + solo en A + solo en B). Sørensen = 2 × en común ÷ (2 × en común + solo en A + solo en B)."
        ))
        add(say(
            "Bray-Curtis usa os indivíduos: 1 − Σ|A − B| ÷ Σ(A + B). Fica em branco se alguma das duas expedições tem registro sem contagem.",
            "Bray-Curtis uses the individuals: 1 − Σ|A − B| ÷ Σ(A + B). It is left blank if either expedition has a record with no count.",
            "Bray-Curtis usa los individuos: 1 − Σ|A − B| ÷ Σ(A + B). Queda en blanco si alguna de las dos salidas tiene un registro sin conteo."
        ))
    }

    /** Doze meses, com quantas expedicoes houve em cada um para dar a medida do esforco. */
    private fun monthsTable(): XlsxTable {
        val names = when (language) {
            AppLanguage.PT -> listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
            AppLanguage.EN -> listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
            AppLanguage.ES -> listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
        }
        val header = listOf(
            say("Mês", "Month", "Mes"),
            say("Expedições", "Expeditions", "Salidas de campo"),
            say("Registros", "Records", "Registros"),
            say("Indivíduos", "Individuals", "Individuos"),
            say("Riqueza (táxons)", "Richness (taxa)", "Riqueza (taxones)")
        )
        val rows = analysis.months.map {
            listOf(names[it.value - 1], it.expeditions.toString(), it.records.toString(), it.individuals.toString(), it.richness.toString())
        }
        return XlsxTable(listOf(header) + rows, title = say("Por mês", "By month", "Por mes"))
    }

    /** Vinte e quatro horas, pela hora do registro: o horario de atividade. */
    private fun hoursTable(): XlsxTable {
        val header = listOf(
            say("Hora", "Hour", "Hora"),
            say("Registros", "Records", "Registros"),
            say("Indivíduos", "Individuals", "Individuos"),
            say("Riqueza (táxons)", "Richness (taxa)", "Riqueza (taxones)")
        )
        val rows = analysis.hours.map {
            listOf(String.format(Locale.US, "%02d:00", it.value), it.records.toString(), it.individuals.toString(), it.richness.toString())
        }
        return XlsxTable(listOf(header) + rows, title = say("Por hora", "By hour", "Por hora"))
    }

    private fun timeNotes(): List<String> = buildList {
        add(say(
            "Mês pela data do registro ou, sem ela, da expedição. Os anos se somam: outubro de 2025 e de 2026 entram em Outubro.",
            "Month from the record's date or, without it, the expedition's. Years add up: October 2025 and 2026 both go into October.",
            "Mes por la fecha del registro o, sin ella, de la salida de campo. Los años se suman: octubre de 2025 y de 2026 entran en Octubre."
        ))
        add(say(
            "Expedições: quantas houve no mês. Compare com os registros.",
            "Expeditions: how many there were in the month. Compare with the records.",
            "Salidas de campo: cuántas hubo en el mes. Compara con los registros."
        ))
        add(say(
            "Hora pelo campo Hora do registro: 19:00 reúne de 19:00 a 19:59.",
            "Hour from the Event time field: 19:00 gathers 19:00 to 19:59.",
            "Hora por el campo Hora del registro: 19:00 reúne de 19:00 a 19:59."
        ))
        leftOut(
            analysis.recordsWithoutDate to Triple("sem data", "with no date", "sin fecha"),
            analysis.recordsWithoutTime to Triple("sem hora", "with no time", "sin hora")
        )?.let { add(it) }
    }

    /**
     * "Fora da análise: 2 registros de ausência e 1 sem nome científico." So as
     * partes que existem, no singular ou no plural; null se nada ficou de fora.
     */
    private fun leftOut(vararg parts: Pair<Int, Triple<String, String, String>>): String? {
        val present = parts.filter { it.first > 0 }
        if (present.isEmpty()) return null
        val pieces = present.mapIndexed { index, (count, what) ->
            // O substantivo so na primeira parte: "2 registros de ausência e 1 sem nome científico".
            val noun = when {
                index > 0 -> ""
                count == 1 -> say("registro ", "record ", "registro ")
                else -> say("registros ", "records ", "registros ")
            }
            "$count $noun${say(what.first, what.second, what.third)}"
        }
        return say("Fora da análise: ", "Left out: ", "Fuera del análisis: ") + pieces.joinToString(say(" e ", " and ", " y ")) + "."
    }

    private fun constancyLabel(constancy: TaxonAnalysis.Constancy) = when (constancy) {
        TaxonAnalysis.Constancy.CONSTANT -> say("Constante", "Constant", "Constante")
        TaxonAnalysis.Constancy.ACCESSORY -> say("Acessória", "Accessory", "Accesoria")
        TaxonAnalysis.Constancy.ACCIDENTAL -> say("Acidental", "Accidental", "Accidental")
    }

    /** "Macho 2; Fêmea 1", no idioma do projeto; valor fora do vocabulario sai como foi escrito. */
    private fun tallyText(tally: List<Pair<String, Int>>): String = tally.joinToString("; ") { (value, count) -> "${vocabularyLabel(value)} $count" }

    private fun vocabularyLabel(value: String): String = when (value) {
        "male" -> say("Macho", "Male", "Macho")
        "female" -> say("Fêmea", "Female", "Hembra")
        "hermaphrodite" -> say("Hermafrodita", "Hermaphrodite", "Hermafrodita")
        "undetermined", "unknown" -> say("Indeterminado", "Undetermined", "Indeterminado")
        "egg" -> say("Ovo", "Egg", "Huevo")
        "larva" -> say("Larva", "Larva", "Larva")
        "tadpole" -> say("Girino", "Tadpole", "Renacuajo")
        "juvenile" -> say("Jovem", "Juvenile", "Juvenil")
        "subadult" -> say("Subadulto", "Subadult", "Subadulto")
        "adult" -> say("Adulto", "Adult", "Adulto")
        "seedling" -> say("Plântula", "Seedling", "Plántula")
        "sapling" -> say("Juvenil arbóreo", "Sapling", "Brinzal")
        "seed" -> say("Semente", "Seed", "Semilla")
        else -> value
    }

    /** Porcentagem com ate duas casas: 33.33, 50, 100. */
    private fun percent(value: Double): String = String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

    /** Indice com ate tres casas; sem valor, celula vazia. */
    private fun index(value: Double?): String = value?.let { String.format(Locale.US, "%.3f", it).trimEnd('0').trimEnd('.') }.orEmpty()

    /** Nome da aba no idioma do projeto. */
    private fun sheet(pt: String, en: String, es: String) = say(pt, en, es)

    fun zipTo(output: OutputStream) {
        val root = "${paths.root}${OikosExporter.safeName(project.name)}"
        ZipOutputStream(output).use { zip ->
            zip.put("$root/README.txt", readme())
            zip.put("$root/${paths.howToCite}", AppCitation.readme(appVersion, language))
            zip.put("$root/manifest.json", manifestJson())
            if (content != ExportContent.MediaOnly) {
                zip.put("$root/${paths.main}/${paths.occurrencesCsv}", occurrencesCsv())
                zip.put("$root/${paths.main}/${paths.workbook}", xlsx())
                zip.put("$root/${paths.main}/${paths.projectJson}", hierarchicalJson())
                zip.put("$root/${paths.gis}/${paths.geojson}", geoJson())
                zip.put("$root/${paths.support}/${paths.sessionsCsv}", sessionsCsv())
                zip.put("$root/${paths.support}/${paths.trackCsv}", tracksCsv())
                zip.put("$root/${paths.support}/${paths.mofCsv}", measurementsCsv())
            }
            zip.put("$root/${paths.support}/${paths.mediaCsv}", mediaCsv())
            if (content != ExportContent.MediaOnly) {
                // Pacote pronto para IPT/GBIF/SiBBr, dentro do ZIP completo.
                zip.put("$root/${paths.dwca}/dwca.zip", dwcArchiveBytes())
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
                    "measurementRemarks" to inProjectLanguage(project.language, "Oikos FieldBook: gravação ${link.fileName}", "Oikos FieldBook: recording ${link.fileName}", "Oikos FieldBook: grabación ${link.fileName}")
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
                inProjectLanguage(project.language, "Conjunto de dados de ocorrência coletado com o Oikos FieldBook.", "Occurrence dataset collected with Oikos FieldBook.", "Conjunto de datos de registros biológicos colectado con Oikos FieldBook.")
            },
            licenseCode = project.license,
            methods = sessions.mapNotNull { it.samplingProtocol.ifBlank { null } }
                .distinct()
                .joinToString("; ")
                .ifBlank { inProjectLanguage(project.language, "Protocolo de amostragem não informado.", "Sampling protocol not reported.", "Protocolo de muestreo no informado.") },
            packageId = "oikos-fieldbook/PROJ-${project.id.toString().padStart(3, '0')}",
            pubDate = LocalDate.now().toString(),
            geographicDescription = sessions.mapNotNull { it.locality.ifBlank { null } }
                .distinct()
                .take(10)
                .joinToString("; ")
                .ifBlank { inProjectLanguage(project.language, "Localidade não informada.", "Locality not reported.", "Localidad no informada.") },
            // ISO 639-2, como o GBIF le no eml.xml.
            language = when (language) {
                AppLanguage.PT -> "por"
                AppLanguage.EN -> "eng"
                AppLanguage.ES -> "spa"
            },
            westBound = pontos.minOfOrNull { it.longitude ?: 0.0 },
            eastBound = pontos.maxOfOrNull { it.longitude ?: 0.0 },
            southBound = pontos.minOfOrNull { it.latitude ?: 0.0 },
            northBound = pontos.maxOfOrNull { it.latitude ?: 0.0 },
            beginDate = datas.firstOrNull().orEmpty(),
            endDate = datas.lastOrNull().orEmpty(),
            taxa = occurrences.mapNotNull { it.scientificName.ifBlank { null } }.distinct().sorted(),
            softwareCitation = inProjectLanguage(project.language, "Dados registrados com o aplicativo Oikos FieldBook, versão $appVersion. ", "Data recorded with the Oikos FieldBook app, version $appVersion. ", "Datos registrados con la app Oikos FieldBook, versión $appVersion. ") + AppCitation.apa(appVersion)
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

    /**
     * Avisos da exportacao. Na tela, no idioma da tela; dentro dos arquivos
     * (manifest.json, aba de resumo), no idioma do projeto, para o mesmo
     * projeto sair igual qualquer que seja o idioma do app.
     */
    fun validationWarnings(format: ExportFormat? = null, language: AppLanguage = UiLanguage.current): String {
        fun t(pt: String, en: String, es: String) = when (language) {
            AppLanguage.PT -> pt
            AppLanguage.EN -> en
            AppLanguage.ES -> es
        }
        val warnings = mutableListOf<String>()
        if (format == ExportFormat.DwcArchive || format == ExportFormat.Zip) {
            if (project.license.isBlank()) {
                warnings += t("Licença do conjunto não definida: o GBIF exige CC0, CC BY ou CC BY-NC (defina em Editar projeto).", "Dataset license not set: GBIF requires CC0, CC BY or CC BY-NC (set it in Edit project).", "Licencia del conjunto no definida: GBIF exige CC0, CC BY o CC BY-NC (defínela en Editar proyecto).")
            }
            if (project.mediaBaseUrl.isBlank() && mediaLinks.any { it.occurrenceID.isNotBlank() }) {
                warnings += t("Mídias fora do Darwin Core Archive: o padrão exige URL pública. Informe a URL das mídias em Editar projeto.", "Media left out of the Darwin Core Archive: the standard requires a public URL. Enter the media URL in Edit project.", "Archivos multimedia fuera del Darwin Core Archive: el estándar exige una URL pública. Indica la URL de los archivos multimedia en Editar proyecto.")
            }
        }
        fun plural(n: Int, ptOne: String, ptMany: String, enOne: String, enMany: String, esOne: String, esMany: String) =
            t(if (n == 1) ptOne else ptMany, if (n == 1) enOne else enMany, if (n == 1) esOne else esMany)
        if (occurrences.isEmpty()) warnings += t("Nenhuma ocorrência encontrada para exportar.", "No occurrences found to export.", "No se encontraron registros para exportar.")
        val withoutId = occurrences.count { it.occurrenceId.isBlank() }
        if (withoutId > 0) warnings += plural(
            withoutId,
            "1 ocorrência sem occurrenceID.", "$withoutId ocorrências sem occurrenceID.",
            "1 occurrence without occurrenceID.", "$withoutId occurrences without occurrenceID.",
            "1 registro sin occurrenceID.", "$withoutId registros sin occurrenceID."
        )
        val withoutCoordinates = occurrences.count { placeOf(it, sessionById[it.sessionId]).latitude == null }
        if (withoutCoordinates > 0) warnings += plural(
            withoutCoordinates,
            "1 ocorrência sem coordenadas (nem própria, nem da expedição); ela não aparece no GeoJSON.",
            "$withoutCoordinates ocorrências sem coordenadas (nem própria, nem da expedição); elas não aparecem no GeoJSON.",
            "1 occurrence without coordinates (neither its own nor the expedition's); it is not in the GeoJSON.",
            "$withoutCoordinates occurrences without coordinates (neither their own nor the expedition's); they are not in the GeoJSON.",
            "1 registro sin coordenadas (ni propias ni de la salida de campo); no aparece en el GeoJSON.",
            "$withoutCoordinates registros sin coordenadas (ni propias ni de la salida de campo); no aparecen en el GeoJSON."
        )
        val inherited = occurrences.count { (it.latitude == null || it.longitude == null) && placeOf(it, sessionById[it.sessionId]).latitude != null }
        if (inherited > 0) warnings += plural(
            inherited,
            "1 ocorrência sem coordenada própria sai com o ponto da expedição.",
            "$inherited ocorrências sem coordenada própria saem com o ponto da expedição.",
            "1 occurrence without its own coordinate uses the expedition point.",
            "$inherited occurrences without their own coordinate use the expedition point.",
            "1 registro sin coordenada propia sale con el punto de la salida de campo.",
            "$inherited registros sin coordenada propia salen con el punto de la salida de campo."
        )
        val emptyTracks = tracks.count { it.points.size < 2 }
        if (emptyTracks > 0) warnings += plural(
            emptyTracks,
            "1 trilha com menos de 2 pontos; ela aparece no CSV, mas não forma LineString no GeoJSON.",
            "$emptyTracks trilhas com menos de 2 pontos; elas aparecem no CSV, mas não formam LineString no GeoJSON.",
            "1 track with fewer than 2 points; it is in the CSV but does not form a LineString in the GeoJSON.",
            "$emptyTracks tracks with fewer than 2 points; they are in the CSV but do not form a LineString in the GeoJSON.",
            "1 recorrido con menos de 2 puntos; aparece en el CSV, pero no forma un LineString en el GeoJSON.",
            "$emptyTracks recorridos con menos de 2 puntos; aparecen en el CSV, pero no forman un LineString en el GeoJSON."
        )
        val missingMedia = mediaLinks.count { !File(it.sourcePath).exists() }
        if (missingMedia > 0) warnings += plural(
            missingMedia,
            "1 arquivo de mídia não foi encontrado no armazenamento local.",
            "$missingMedia arquivos de mídia não foram encontrados no armazenamento local.",
            "1 media file was not found in local storage.",
            "$missingMedia media files were not found in local storage.",
            "1 archivo multimedia no se encontró en el almacenamiento local.",
            "$missingMedia archivos multimedia no se encontraron en el almacenamiento local."
        )
        val mediaWithoutEvent = mediaLinks.count { it.eventID.isBlank() }
        if (mediaWithoutEvent > 0) warnings += plural(
            mediaWithoutEvent,
            "1 mídia sem eventID de expedição.", "$mediaWithoutEvent mídias sem eventID de expedição.",
            "1 media file without an expedition eventID.", "$mediaWithoutEvent media files without an expedition eventID.",
            "1 archivo multimedia sin el eventID de la salida de campo.", "$mediaWithoutEvent archivos multimedia sin el eventID de la salida de campo."
        )
        val mediaWithoutOccurrence = mediaLinks.count { it.occurrenceID.isBlank() }
        if (mediaWithoutOccurrence > 0) warnings += plural(
            mediaWithoutOccurrence,
            "1 mídia ligada apenas à expedição, sem occurrenceID.", "$mediaWithoutOccurrence mídias ligadas apenas à expedição, sem occurrenceID.",
            "1 media file linked only to the expedition, without occurrenceID.", "$mediaWithoutOccurrence media files linked only to the expedition, without occurrenceID.",
            "1 archivo multimedia vinculado solo a la salida de campo, sin occurrenceID.", "$mediaWithoutOccurrence archivos multimedia vinculados solo a la salida de campo, sin occurrenceID."
        )
        val duplicatedNames = mediaLinks.groupBy { it.relativePath }.count { it.value.size > 1 }
        if (duplicatedNames > 0) warnings += plural(
            duplicatedNames,
            "1 nome de mídia duplicado na exportação.", "$duplicatedNames nomes de mídia duplicados na exportação.",
            "1 duplicated media name in the export.", "$duplicatedNames duplicated media names in the export.",
            "1 nombre de archivo multimedia duplicado en la exportación.", "$duplicatedNames nombres de archivos multimedia duplicados en la exportación."
        )
        return warnings.joinToString("\n")
    }

    private fun manifestJson(): String {
        val files = mutableListOf(
            "README.txt",
            paths.howToCite,
            "manifest.json",
            "${paths.support}/${paths.mediaCsv}"
        )
        if (content != ExportContent.MediaOnly) {
            files += listOf(
                "${paths.main}/${paths.occurrencesCsv}",
                "${paths.main}/${paths.workbook}",
                "${paths.main}/${paths.projectJson}",
                "${paths.gis}/${paths.geojson}",
                "${paths.support}/${paths.sessionsCsv}",
                "${paths.support}/${paths.trackCsv}",
                "${paths.support}/${paths.mofCsv}",
                "${paths.dwca}/dwca.zip"
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
            .put("geodeticDatum", DatumCodes.forExport(project.defaultDatum))
            .put("files", JSONArray(files))
            .put("mediaLinks", JSONArray(mediaLinks.map { JSONObject(it.manifestMap()) }))
            .put("gpsTracks", JSONArray(tracks.map { JSONObject(trackMap(it)) }))
            .put("warnings", JSONArray(validationWarnings(ExportFormat.Zip, language).lines().filter { it.isNotBlank() }))
            .toString(2)
    }

    private fun readme(): String = say(readmePortuguese(), readmeEnglish(), readmeSpanish())

    private fun readmeSpanish(): String = """
        Oikos FieldBook - Exportación completa

        Proyecto: ${project.name}
        Exportado el: ${exportedAt()}
        Datum: ${DatumCodes.withCode(project.defaultDatum)}
        Coordenadas internas/Darwin Core: grados decimales (decimalLatitude, decimalLongitude)
        Altitud: metros sobre el nivel del mar. El GPS mide la altura sobre el elipsoide WGS84; la app la convierte con el geoide EGM2008, y esos registros llevan verticalDatum = EGM2008.
        Formato de coordenadas configurado en el proyecto: ${project.coordinateFormat}
        ${if (project.coordinateFormat == "UTM") "Las coordenadas UTM también aparecen en verbatimCoordinates, verbatimCoordinateSystem, verbatimSRS, utmZone, utmHemisphere, utmEasting y utmNorthing." else ""}

        Estructura:
        - ${paths.main}/: archivos principales para el análisis. El CSV Darwin Core es la tabla principal de registros; el XLSX reúne proyecto, salidas de campo, registros, archivos multimedia, recorridos y campos usados en hojas separadas, y al final las hojas de análisis.
        - ${paths.support}/: tablas de apoyo, como salidas de campo, archivos multimedia, puntos del recorrido GPS y MeasurementOrFact.
        - ${paths.gis}/: GeoJSON para abrir en QGIS, con los registros como Point y los recorridos como LineString.
        - ${paths.dwca}/dwca.zip: paquete Darwin Core Archive (meta.xml, eml.xml, occurrence.txt y extensiones) para IPT, GBIF y SiBBr.
        - ${paths.media}/${paths.photos}/: fotos exportadas con nombres rastreables.
        - ${paths.media}/${paths.audios}/: audios exportados con nombres rastreables.
        - manifest.json: resumen de la exportación y relación completa entre archivos multimedia y registros.

        Cada foto o audio está vinculado a un registro por occurrenceID, o a una salida de campo por eventID.
        Consulta la columna associatedMedia en ${paths.main}/${paths.occurrencesCsv} o la tabla ${paths.support}/${paths.mediaCsv}.
        Cómo citar la app: consulta ${paths.howToCite}.
        Cuando hay más de un archivo multimedia, associatedMedia usa rutas relativas separadas por punto y coma.
        En el Darwin Core Archive, associatedMedia y la extensión multimedia usan la URL pública del proyecto seguida del nombre del archivo: publica juntas las fotos y los audios de ${paths.media}/${paths.photos} y ${paths.media}/${paths.audios}, sin subcarpetas, en esa dirección (como en un registro de Zenodo).

        Para QGIS:
        Abre ${paths.gis}/${paths.geojson}. La geometría usa Point en el orden longitud, latitud.
        Si el proyecto está en UTM, los valores UTM quedan en las propiedades de la capa; la geometría del GeoJSON sigue en longitud/latitud, como exige el formato.
        Los registros sin coordenadas quedan en las planillas, pero no aparecen en el GeoJSON.
    """.trimIndent()

    private fun readmeEnglish(): String = """
        Oikos FieldBook - Full export

        Project: ${project.name}
        Exported at: ${exportedAt()}
        Datum: ${DatumCodes.withCode(project.defaultDatum)}
        Internal/Darwin Core coordinates: decimal degrees (decimalLatitude, decimalLongitude)
        Altitude: meters above sea level. The GPS measures height above the WGS84 ellipsoid; the app converts it with the EGM2008 geoid, and those records carry verticalDatum = EGM2008.
        Coordinate format set in the project: ${project.coordinateFormat}
        ${if (project.coordinateFormat == "UTM") "UTM coordinates also appear in verbatimCoordinates, verbatimCoordinateSystem, verbatimSRS, utmZone, utmHemisphere, utmEasting and utmNorthing." else ""}

        Structure:
        - ${paths.main}/: main files for analysis. The Darwin Core CSV is the main occurrence table; the XLSX gathers project, expeditions, occurrences, media, tracks and fields used in separate sheets, with the analysis sheets at the end.
        - ${paths.support}/: supporting tables, such as expeditions, media, GPS track points and MeasurementOrFact.
        - ${paths.gis}/: GeoJSON to open in QGIS, with occurrences as Point and tracks as LineString.
        - ${paths.dwca}/dwca.zip: Darwin Core Archive package (meta.xml, eml.xml, occurrence.txt and extensions) for IPT, GBIF and SiBBr.
        - ${paths.media}/${paths.photos}/: exported photos with traceable names.
        - ${paths.media}/${paths.audios}/: exported audio with traceable names.
        - manifest.json: export summary and the full link between media and records.

        Each photo or audio file is linked to a record by occurrenceID, or to an expedition by eventID.
        See the associatedMedia column in ${paths.main}/${paths.occurrencesCsv} or the table ${paths.support}/${paths.mediaCsv}.
        How to cite the app: see ${paths.howToCite}.
        When there is more than one media file, associatedMedia uses relative paths separated by semicolons.
        In the Darwin Core Archive, associatedMedia and the multimedia extension use the project's public URL followed by the file name: publish the photos and audio from ${paths.media}/${paths.photos} and ${paths.media}/${paths.audios} together, without subfolders, at that address (as in a Zenodo record).

        For QGIS:
        Open ${paths.gis}/${paths.geojson}. The geometry uses Point in longitude, latitude order.
        If the project is in UTM, the UTM values are in the layer properties; the GeoJSON geometry stays in longitude/latitude, as the format requires.
        Records without coordinates stay in the spreadsheets but do not appear in the GeoJSON.
    """.trimIndent()

    private fun readmePortuguese(): String = """
        Oikos FieldBook - Exportação completa

        Projeto: ${project.name}
        Exportado em: ${exportedAt()}
        Datum: ${DatumCodes.withCode(project.defaultDatum)}
        Coordenadas internas/Darwin Core: Graus Decimais (decimalLatitude, decimalLongitude)
        Altitude: metros sobre o nível do mar. O GPS mede a altura sobre o elipsoide WGS84; o app a converte pelo geoide EGM2008, e esses registros trazem verticalDatum = EGM2008.
        Formato configurado no projeto: ${project.coordinateFormat}
        ${if (project.coordinateFormat == "UTM") "Coordenadas UTM também aparecem em verbatimCoordinates, verbatimCoordinateSystem, verbatimSRS, utmZone, utmHemisphere, utmEasting e utmNorthing." else ""}

        Estrutura:
        - 01_planilha_principal/: arquivos principais para análise. O CSV Darwin Core é a tabela principal de ocorrências; o XLSX reúne projeto, expedições, ocorrências, mídias, trilhas e campos usados em abas, e no fim as abas de análise.
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
        val datum = DatumCodes.forExport(place.datum)
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
            "geodeticDatum" to DatumCodes.forExport(session.geodeticDatum),
            "eventRemarks" to session.notes
        ).withVerbatim(session.latitude, session.longitude, DatumCodes.forExport(session.geodeticDatum))

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
        "geodeticDatum" to DatumCodes.forExport(track.geodeticDatum)
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
                "geodeticDatum" to DatumCodes.forExport(track.geodeticDatum)
            ).withVerbatim(point.latitude, point.longitude, DatumCodes.forExport(track.geodeticDatum))
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
            val folder = if (item.mediaType == "audio") paths.audios else paths.photos
            val fileName = uniqueMediaFileName(item, occurrence, session, extension)
            val relativePath = "${paths.media}/$folder/$fileName"
            MediaLink(
                mediaID = item.mediaCode.ifBlank { MediaCodes.legacy(item.id) },
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
        // renomeia o arquivo exportado. O numero fixo da midia no fim
        // (LSCM0001; MED000123 nas anteriores a ele) nunca muda e torna o nome
        // unico; antes era um contador -01/-02 que dependia da ordem das midias.
        val base = safeToken(exportMediaBaseName(media, occurrence, session))
        return "${base}_${MediaCodes.fileToken(media.mediaCode.ifBlank { MediaCodes.legacy(media.id) })}.$extension"
    }

    private fun exportMediaBaseName(media: MediaEntity, occurrence: OccurrenceEntity?, session: SessionEntity?): String {
        // Data e hora da captura, no mesmo fuso (o do aparelho). Antes a data era
        // a da expedicao (ou UTC) e a hora local: um registro noturno ganhava um
        // instante que nao existiu.
        val local = java.time.Instant.ofEpochMilli(media.createdAt).atZone(java.time.ZoneId.systemDefault())
        val date = if (media.captureTimeKnown) local.toLocalDate().toString() else say("SEM-DATA", "NO-DATE", "SIN-FECHA")
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
    private fun csv(headers: List<String>, rows: List<Map<String, String>>): String = csvTable(
        listOf(headers) + rows.map { row -> headers.map { row[it].orEmpty() } },
        // So colunas numericas: antes um fieldNumber "3.2" virava "3,2".
        headers.indices.filter { headers[it] in numericColumns }.toSet()
    )

    /** Tabela ja em linhas, a primeira com o cabecalho; [decimalColumns] levam virgula no CSV brasileiro. */
    private fun csvTable(rows: List<List<String>>, decimalColumns: Set<Int>): String = buildString {
        val brazilian = project.csvStyle != "intl"
        val separator = if (brazilian) ";" else ","
        if (brazilian) append(UTF8_BOM)
        rows.forEachIndexed { rowIndex, row ->
            appendLine(row.withIndex().joinToString(separator) { (column, value) ->
                csvCell(if (brazilian && rowIndex > 0 && column in decimalColumns) brazilianDecimal(value) else value, separator)
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


/** Tabela de uma aba. A primeira linha de [rows] e o cabecalho; [title] vai em cima, quando a aba tem mais de uma. */
private class XlsxTable(
    val rows: List<List<String>>,
    val title: String? = null,
    /** A ultima linha de [rows] e o total. */
    val totalRow: Boolean = false,
    /** Colunas com nome cientifico: genero e epiteto em italico, autor sem. */
    val nameColumns: Set<Int> = emptySet()
)

/**
 * Aba da planilha, com uma ou mais tabelas, uma embaixo da outra.
 *
 * Toda aba sai com o cabecalho em laranja e as colunas na largura do
 * conteudo: antes o Excel abria tudo na largura padrao, cortando os textos.
 * Aba de uma tabela so tem o cabecalho fixo ao rolar. As abas de analise
 * ([report]) saem prontas para ler e imprimir: bordas, linhas alternadas,
 * total em destaque, nome cientifico em italico e notas no fim. O estilo nao
 * muda nenhum valor.
 */
private class XlsxSheet(
    val name: String,
    val tables: List<XlsxTable>,
    val report: Boolean = false,
    /** Texto depois das tabelas; a primeira linha e o titulo. */
    val notes: List<String> = emptyList(),
    /** Grafico sobre a primeira tabela. */
    val chart: XlsxLineChart? = null
) {
    /** Aba de dados: uma tabela so, sem titulo. */
    constructor(name: String, rows: List<List<String>>) : this(name, listOf(XlsxTable(rows)))
}

/** Grafico de linha de uma serie, com categorias e valores das linhas 1 a [lastRow] da primeira tabela da aba. */
private class XlsxLineChart(
    val title: String,
    val categoryColumn: Int,
    val valueColumn: Int,
    val lastRow: Int,
    val categoryTitle: String,
    val valueTitle: String
)

private object XlsxWriter {
    // Posicoes em cellXfs de styles.xml.
    private const val HEADER = 1
    private const val BODY = 2
    private const val BAND = 3
    private const val TOTAL = 4
    private const val NOTE = 5
    private const val NOTE_TITLE = 6
    private const val TABLE_TITLE = 7

    private const val ORANGE = "FF6A00"
    private const val RELATIONSHIPS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    fun write(sheets: List<XlsxSheet>): ByteArray {
        val chartNumbers = mutableMapOf<Int, Int>()
        sheets.forEachIndexed { index, sheet ->
            val chart = sheet.chart
            val first = sheet.tables.firstOrNull()
            if (chart != null && first != null && chart.lastRow in 1 until first.rows.size) chartNumbers[index] = chartNumbers.size + 1
        }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.put("[Content_Types].xml", contentTypes(sheets.size, chartNumbers.size))
            zip.put("_rels/.rels", relationships("rId1" to ("$RELATIONSHIPS/officeDocument" to "xl/workbook.xml")))
            zip.put("xl/workbook.xml", workbook(sheets))
            zip.put(
                "xl/_rels/workbook.xml.rels",
                relationships(
                    *(sheets.indices.map { "rId${it + 1}" to ("$RELATIONSHIPS/worksheet" to "worksheets/sheet${it + 1}.xml") } +
                        ("rId${sheets.size + 1}" to ("$RELATIONSHIPS/styles" to "styles.xml"))).toTypedArray()
                )
            )
            zip.put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { index, sheet ->
                val number = chartNumbers[index]
                zip.put("xl/worksheets/sheet${index + 1}.xml", sheet(sheet, withDrawing = number != null))
                if (number != null) {
                    zip.put("xl/worksheets/_rels/sheet${index + 1}.xml.rels", relationships("rId1" to ("$RELATIONSHIPS/drawing" to "../drawings/drawing$number.xml")))
                    zip.put("xl/drawings/drawing$number.xml", drawing(sheet))
                    zip.put("xl/drawings/_rels/drawing$number.xml.rels", relationships("rId1" to ("$RELATIONSHIPS/chart" to "../charts/chart$number.xml")))
                    zip.put("xl/charts/chart$number.xml", chart(sheet, sheet.chart!!))
                }
            }
        }
        return output.toByteArray()
    }

    private fun contentTypes(sheetCount: Int, chartCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        repeat(sheetCount) { append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""") }
        repeat(chartCount) {
            append("""<Override PartName="/xl/drawings/drawing${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>""")
            append("""<Override PartName="/xl/charts/chart${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.chart+xml"/>""")
        }
        append("</Types>")
    }

    private fun relationships(vararg entries: Pair<String, Pair<String, String>>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        entries.forEach { (id, target) -> append("""<Relationship Id="$id" Type="${target.first}" Target="${target.second}"/>""") }
        append("</Relationships>")
    }

    private fun workbook(sheets: List<XlsxSheet>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="$RELATIONSHIPS"><sheets>""")
        sheets.forEachIndexed { index, sheet -> append("""<sheet name="${xml(sheetName(sheet))}" sheetId="${index + 1}" r:id="rId${index + 1}"/>""") }
        append("</sheets></workbook>")
    }

    private fun sheetName(sheet: XlsxSheet) = sheet.name.take(31)

    private fun sheet(sheet: XlsxSheet, withDrawing: Boolean): String = buildString {
        val widths = widths(sheet)
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="$RELATIONSHIPS">""")
        // Cabecalho fixo ao rolar, quando a aba tem uma tabela so.
        val single = sheet.tables.singleOrNull()?.takeIf { it.title == null }
        if (single != null && single.rows.size > 1) {
            append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/><selection pane="bottomLeft"/></sheetView></sheetViews>""")
        }
        if (widths.isNotEmpty()) {
            append("<cols>")
            widths.forEachIndexed { index, width -> append("""<col min="${index + 1}" max="${index + 1}" width="$width" customWidth="1"/>""") }
            append("</cols>")
        }
        append("<sheetData>")
        var rowIndex = 0
        fun textRow(text: String, style: Int) {
            append("""<row r="${rowIndex + 1}"><c r="${cellName(0, rowIndex)}"${styleAttribute(style)} t="inlineStr"><is><t>${xml(text)}</t></is></c></row>""")
            rowIndex++
        }
        sheet.tables.forEachIndexed { tableIndex, table ->
            // Uma linha em branco entre as tabelas.
            if (tableIndex > 0) rowIndex++
            table.title?.let { textRow(it, TABLE_TITLE) }
            val headerHeight = if (sheet.report) headerHeight(table.rows.firstOrNull().orEmpty(), widths) else null
            table.rows.forEachIndexed { index, row ->
                val total = table.totalRow && index > 0 && index == table.rows.lastIndex
                val style = when {
                    index == 0 -> HEADER
                    !sheet.report -> 0
                    total -> TOTAL
                    index % 2 == 0 -> BAND
                    else -> BODY
                }
                append(if (index == 0 && headerHeight != null) """<row r="${rowIndex + 1}" ht="$headerHeight" customHeight="1">""" else """<row r="${rowIndex + 1}">""")
                // Na analise a linha vai ate a ultima coluna, para a faixa e a borda nao pararem no meio.
                val cells = if (sheet.report) row + List((table.rows.first().size - row.size).coerceAtLeast(0)) { "" } else row
                cells.forEachIndexed { columnIndex, value ->
                    val ref = cellName(columnIndex, rowIndex)
                    // Cabecalho sempre como texto; demais linhas viram numero quando
                    // o valor e numerico, para que latitude, longitude, DAP e
                    // contagens cheguem ao Excel calculaveis em vez de texto.
                    when {
                        index > 0 && isNumeric(value) -> append("""<c r="$ref"${styleAttribute(style)} t="n"><v>$value</v></c>""")
                        index > 0 && !total && columnIndex in table.nameColumns && value.isNotBlank() ->
                            append("""<c r="$ref"${styleAttribute(style)} t="inlineStr"><is>${ScientificName.runs(value).joinToString("") { richRun(it) }}</is></c>""")
                        else -> append("""<c r="$ref"${styleAttribute(style)} t="inlineStr"><is><t>${xml(value)}</t></is></c>""")
                    }
                }
                append("</row>")
                rowIndex++
            }
        }
        // Uma linha em branco e as notas.
        if (sheet.notes.isNotEmpty()) rowIndex++
        sheet.notes.forEachIndexed { index, note -> textRow(note, if (index == 0) NOTE_TITLE else NOTE) }
        append("</sheetData>")
        if (withDrawing) append("""<drawing r:id="rId1"/>""")
        append("</worksheet>")
    }

    private fun styleAttribute(style: Int) = if (style == 0) "" else """ s="$style""""

    private fun richRun(run: ScientificName.Run): String {
        val italic = if (run.italic) "<i/>" else ""
        return """<r><rPr><rFont val="Calibri"/>$italic<sz val="11"/></rPr><t xml:space="preserve">${xml(run.text)}</t></r>"""
    }

    /**
     * Largura de cada coluna pelo texto mais longo, em caracteres. Na analise
     * o cabecalho quebra linha, entao basta caber a palavra mais longa dele.
     */
    private fun widths(sheet: XlsxSheet): List<Int> {
        val columns = columnCount(sheet)
        return (0 until columns).map { column ->
            val width = sheet.tables.maxOfOrNull { table ->
                val title = table.rows.firstOrNull()?.getOrNull(column).orEmpty()
                val titleWidth = if (sheet.report) title.split(' ').maxOf { it.length } else title.length
                val body = table.rows.drop(1).maxOfOrNull { row -> row.getOrNull(column).orEmpty().lines().maxOf { it.length } } ?: 0
                maxOf(titleWidth + 1, body)
            } ?: 0
            (width + 2).coerceIn(if (sheet.report) 10 else 6, 50)
        }
    }

    private fun columnCount(sheet: XlsxSheet) = sheet.tables.flatMap { it.rows }.maxOfOrNull { it.size } ?: 0

    /** Altura do cabecalho que quebra linha: o Excel nao recalcula ao abrir. */
    private fun headerHeight(header: List<String>, widths: List<Int>): Int {
        val lines = header.mapIndexed { column, title ->
            val capacity = (widths.getOrElse(column) { 10 } - 2).coerceAtLeast(1)
            var count = 1
            var used = 0
            title.split(' ').forEach { word ->
                if (used > 0 && used + 1 + word.length > capacity) {
                    count++
                    used = word.length
                } else {
                    used += if (used == 0) word.length else word.length + 1
                }
            }
            count
        }.maxOrNull() ?: 1
        return lines * 15 + 6
    }

    /** Posicao do grafico: a direita da tabela, sem cobrir nenhum dado. */
    private fun drawing(sheet: XlsxSheet): String {
        val firstColumn = columnCount(sheet) + 1
        return """<?xml version="1.0" encoding="UTF-8"?>""" +
            """<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">""" +
            """<xdr:twoCellAnchor editAs="oneCell">""" +
            """<xdr:from><xdr:col>$firstColumn</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>0</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>""" +
            """<xdr:to><xdr:col>${firstColumn + 8}</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>20</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to>""" +
            """<xdr:graphicFrame macro=""><xdr:nvGraphicFramePr><xdr:cNvPr id="2" name="${xml(sheet.chart?.title.orEmpty())}"/><xdr:cNvGraphicFramePr/></xdr:nvGraphicFramePr>""" +
            """<xdr:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/></xdr:xfrm>""" +
            """<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/chart">""" +
            """<c:chart xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" xmlns:r="$RELATIONSHIPS" r:id="rId1"/>""" +
            """</a:graphicData></a:graphic></xdr:graphicFrame><xdr:clientData/></xdr:twoCellAnchor></xdr:wsDr>"""
    }

    /**
     * Grafico do proprio Excel, ligado as celulas da tabela: muda junto se o
     * usuario corrigir um numero. Os valores tambem vao no cache do grafico,
     * para aparecer em leitores que nao recalculam.
     */
    private fun chart(sheet: XlsxSheet, chart: XlsxLineChart): String {
        val table = sheet.tables.first()
        val rows = table.rows.subList(1, chart.lastRow + 1)
        // Linha do cabecalho na aba (1, ou 2 com o titulo da tabela em cima).
        val headerRow = if (table.title != null) 2 else 1
        val quoted = "'" + sheetName(sheet).replace("'", "''") + "'"
        fun range(column: Int) = "$quoted!\$${columnName(column)}\$${headerRow + 1}:\$${columnName(column)}\$${headerRow + chart.lastRow}"
        val categories = rows.map { it.getOrNull(chart.categoryColumn).orEmpty() }
        val values = rows.map { it.getOrNull(chart.valueColumn).orEmpty() }
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<c:chartSpace xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="$RELATIONSHIPS">""")
            append("""<c:roundedCorners val="0"/><c:chart>""")
            append("""<c:title>${richText(chart.title, size = 1400, bold = true)}<c:overlay val="0"/></c:title><c:autoTitleDeleted val="0"/>""")
            append("""<c:plotArea><c:layout/><c:lineChart><c:grouping val="standard"/><c:varyColors val="0"/>""")
            append("""<c:ser><c:idx val="0"/><c:order val="0"/>""")
            append("""<c:tx><c:strRef><c:f>${xml("$quoted!\$${columnName(chart.valueColumn)}\$$headerRow")}</c:f><c:strCache><c:ptCount val="1"/><c:pt idx="0"><c:v>${xml(table.rows[0].getOrNull(chart.valueColumn).orEmpty())}</c:v></c:pt></c:strCache></c:strRef></c:tx>""")
            append("""<c:spPr><a:ln w="38100" cap="rnd"><a:solidFill><a:srgbClr val="$ORANGE"/></a:solidFill><a:round/></a:ln></c:spPr>""")
            append("""<c:marker><c:symbol val="circle"/><c:size val="8"/><c:spPr><a:solidFill><a:srgbClr val="$ORANGE"/></a:solidFill><a:ln w="19050"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:ln></c:spPr></c:marker>""")
            append("""<c:dLbls><c:spPr><a:noFill/><a:ln><a:noFill/></a:ln></c:spPr>""")
            append("""<c:txPr><a:bodyPr/><a:lstStyle/><a:p><a:pPr><a:defRPr sz="1100" b="1"/></a:pPr><a:endParaRPr lang="pt-BR"/></a:p></c:txPr>""")
            append("""<c:dLblPos val="t"/><c:showLegendKey val="0"/><c:showVal val="1"/><c:showCatName val="0"/><c:showSerName val="0"/><c:showPercent val="0"/><c:showBubbleSize val="0"/></c:dLbls>""")
            append("""<c:cat><c:strRef><c:f>${xml(range(chart.categoryColumn))}</c:f><c:strCache><c:ptCount val="${categories.size}"/>""")
            categories.forEachIndexed { index, value -> append("""<c:pt idx="$index"><c:v>${xml(value)}</c:v></c:pt>""") }
            append("</c:strCache></c:strRef></c:cat>")
            append("""<c:val><c:numRef><c:f>${xml(range(chart.valueColumn))}</c:f><c:numCache><c:formatCode>General</c:formatCode><c:ptCount val="${values.size}"/>""")
            values.forEachIndexed { index, value -> if (isNumeric(value)) append("""<c:pt idx="$index"><c:v>$value</c:v></c:pt>""") }
            append("</c:numCache></c:numRef></c:val><c:smooth val=\"0\"/></c:ser>")
            append("""<c:marker val="1"/><c:axId val="500100"/><c:axId val="500200"/></c:lineChart>""")
            append("""<c:catAx><c:axId val="500100"/><c:scaling><c:orientation val="minMax"/></c:scaling><c:delete val="0"/><c:axPos val="b"/>""")
            append("""<c:title>${richText(chart.categoryTitle, size = 1000, bold = false)}<c:overlay val="0"/></c:title>""")
            append("""<c:numFmt formatCode="General" sourceLinked="1"/><c:majorTickMark val="none"/><c:minorTickMark val="none"/><c:tickLblPos val="nextTo"/>""")
            append("""<c:spPr><a:ln w="9525"><a:solidFill><a:srgbClr val="BFBFBF"/></a:solidFill></a:ln></c:spPr>""")
            append("""<c:crossAx val="500200"/><c:crosses val="autoZero"/><c:auto val="1"/><c:lblAlgn val="ctr"/><c:lblOffset val="100"/><c:noMultiLvlLbl val="0"/></c:catAx>""")
            append("""<c:valAx><c:axId val="500200"/><c:scaling><c:orientation val="minMax"/><c:min val="0"/></c:scaling><c:delete val="0"/><c:axPos val="l"/>""")
            append("""<c:majorGridlines><c:spPr><a:ln w="9525"><a:solidFill><a:srgbClr val="E6E6E6"/></a:solidFill></a:ln></c:spPr></c:majorGridlines>""")
            append("""<c:title>${richText(chart.valueTitle, size = 1000, bold = false, vertical = true)}<c:overlay val="0"/></c:title>""")
            append("""<c:numFmt formatCode="0" sourceLinked="0"/><c:majorTickMark val="none"/><c:minorTickMark val="none"/><c:tickLblPos val="nextTo"/>""")
            append("""<c:spPr><a:ln><a:noFill/></a:ln></c:spPr><c:crossAx val="500100"/><c:crosses val="autoZero"/><c:crossBetween val="between"/></c:valAx>""")
            append("""</c:plotArea><c:plotVisOnly val="1"/><c:dispBlanksAs val="gap"/></c:chart>""")
            append("""<c:spPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill><a:ln><a:noFill/></a:ln></c:spPr>""")
            append("</c:chartSpace>")
        }
    }

    private fun richText(text: String, size: Int, bold: Boolean, vertical: Boolean = false): String {
        val rotation = if (vertical) """ rot="-5400000" vert="horz"""" else ""
        val b = if (bold) "1" else "0"
        return """<c:tx><c:rich><a:bodyPr$rotation/><a:lstStyle/><a:p><a:pPr><a:defRPr sz="$size" b="$b"/></a:pPr>""" +
            """<a:r><a:rPr lang="pt-BR" sz="$size" b="$b"/><a:t>${xml(text)}</a:t></a:r></a:p></c:rich></c:tx>"""
    }

    /** Fontes, preenchimentos e bordas; a ordem de cellXfs segue as constantes HEADER a TABLE_TITLE. */
    private val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
        """<fonts count="5">""" +
        """<font><sz val="11"/><name val="Calibri"/><family val="2"/></font>""" +
        """<font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/><family val="2"/></font>""" +
        """<font><b/><sz val="11"/><name val="Calibri"/><family val="2"/></font>""" +
        """<font><sz val="10"/><color rgb="FF595959"/><name val="Calibri"/><family val="2"/></font>""" +
        """<font><b/><sz val="12"/><color rgb="FF$ORANGE"/><name val="Calibri"/><family val="2"/></font>""" +
        """</fonts>""" +
        """<fills count="5">""" +
        """<fill><patternFill patternType="none"/></fill>""" +
        """<fill><patternFill patternType="gray125"/></fill>""" +
        """<fill><patternFill patternType="solid"><fgColor rgb="FF$ORANGE"/><bgColor indexed="64"/></patternFill></fill>""" +
        """<fill><patternFill patternType="solid"><fgColor rgb="FFFFF3EA"/><bgColor indexed="64"/></patternFill></fill>""" +
        """<fill><patternFill patternType="solid"><fgColor rgb="FFFFE1CC"/><bgColor indexed="64"/></patternFill></fill>""" +
        """</fills>""" +
        """<borders count="3">""" +
        """<border><left/><right/><top/><bottom/><diagonal/></border>""" +
        """<border><left style="thin"><color rgb="FFD9D9D9"/></left><right style="thin"><color rgb="FFD9D9D9"/></right><top style="thin"><color rgb="FFD9D9D9"/></top><bottom style="thin"><color rgb="FFD9D9D9"/></bottom><diagonal/></border>""" +
        """<border><left style="thin"><color rgb="FFD9D9D9"/></left><right style="thin"><color rgb="FFD9D9D9"/></right><top style="medium"><color rgb="FF$ORANGE"/></top><bottom style="thin"><color rgb="FFD9D9D9"/></bottom><diagonal/></border>""" +
        """</borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="8">""" +
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>""" +
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>""" +
        """<xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>""" +
        """<xf numFmtId="0" fontId="2" fillId="4" borderId="2" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>""" +
        """<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
        """<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
        """<xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
        """</cellXfs>""" +
        """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
        """</styleSheet>"""

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

    private fun columnName(columnIndex: Int): String {
        var n = columnIndex
        val col = StringBuilder()
        do {
            col.insert(0, ('A'.code + (n % 26)).toChar())
            n = n / 26 - 1
        } while (n >= 0)
        return col.toString()
    }

    private fun cellName(columnIndex: Int, rowIndex: Int): String = "${columnName(columnIndex)}${rowIndex + 1}"

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
    if (value.none { it < ' ' || it in '\uD800'..'\uDFFF' || it == '\uFFFE' || it == '\uFFFF' }) return value
    val out = StringBuilder(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        when {
            c == '\t' || c == '\n' || c == '\r' -> out.append(c)
            c < ' ' || c == '\uFFFE' || c == '\uFFFF' -> Unit
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

