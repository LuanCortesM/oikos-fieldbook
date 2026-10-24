package com.oikos.fieldbook.export

import com.oikos.fieldbook.data.FieldConfigEntity
import com.oikos.fieldbook.data.MediaEntity
import com.oikos.fieldbook.data.OccurrenceEntity
import com.oikos.fieldbook.data.ProjectEntity
import com.oikos.fieldbook.data.SessionEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** Exportacao de ponta a ponta, com os casos que antes saiam com dado errado. */
class OikosExporterTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val project = ProjectEntity(id = 1, name = "Anuros da Serra", csvStyle = "intl", license = "CC-BY-4.0")
    private val session = SessionEntity(
        id = 1, projectId = 1, eventId = "EV-1", name = "Serra", eventDate = "2026-03-14",
        latitude = -22.97, longitude = -43.2264, coordinateUncertaintyInMeters = 4.0, altitude = 40.0, geodeticDatum = "WGS84"
    )

    private fun occurrence(id: Long, block: OccurrenceEntity.() -> OccurrenceEntity = { this }) =
        OccurrenceEntity(id = id, projectId = 1, sessionId = 1, occurrenceId = "OCC-$id", scientificName = "Boana faber").block()

    private fun export(
        format: ExportFormat,
        occurrences: List<OccurrenceEntity>,
        media: List<MediaEntity> = emptyList(),
        exportProject: ProjectEntity = project,
        content: ExportContent = ExportContent.SpreadsheetsAndAllMedia,
        fields: List<FieldConfigEntity> = emptyList()
    ): File = OikosExporter().export(
        format = format, project = exportProject, sessions = listOf(session), occurrences = occurrences,
        media = media, fields = fields, content = content
    ) { name -> File(folder.root, name) }.file

    /** Linhas do CSV internacional como mapas coluna -> valor (sem aspas nos casos testados). */
    private fun csvRows(file: File): List<Map<String, String>> {
        val lines = file.readLines().filter { it.isNotEmpty() }
        val headers = lines.first().split(',')
        return lines.drop(1).map { line -> headers.zip(line.split(',')).toMap() }
    }

    @Test
    fun coordenadaPropriaNaoHerdaIncertezaNemAltitudeDaExpedicao() {
        val gps = occurrence(1) { copy(latitude = -22.5, longitude = -45.0, coordinateUncertaintyInMeters = null, altitude = null, geodeticDatum = "SIRGAS 2000") }
        val row = csvRows(export(ExportFormat.Csv, listOf(gps))).single()
        assertEquals("-22.5", row["decimalLatitude"])
        assertEquals("", row["coordinateUncertaintyInMeters"])
        assertEquals("", row["minimumElevationInMeters"])
        assertEquals("EPSG:4674", row["geodeticDatum"])
    }

    @Test
    fun semCoordenadaPropriaUsaOPontoDaExpedicaoComDatumDela() {
        val row = csvRows(export(ExportFormat.Csv, listOf(occurrence(1)))).single()
        assertEquals("-22.97", row["decimalLatitude"])
        assertEquals("4", row["coordinateUncertaintyInMeters"])
        assertEquals("40", row["minimumElevationInMeters"])
        assertEquals("EPSG:4326", row["geodeticDatum"])
    }

    @Test
    fun datumSaiComOCodigoEpsgQueOGbifReconhece() {
        // Com "SIRGAS 2000" o GBIF marcava GEODETIC_DATUM_INVALID e assumia WGS84.
        val sirgas = occurrence(1) { copy(latitude = -22.5, longitude = -45.0, geodeticDatum = "SIRGAS 2000") }
        val entries = readZip(export(ExportFormat.DwcArchive, listOf(sirgas)))
        val core = entries.getValue("occurrence.txt").lines()
        val row = core.first().split('\t').zip(core[1].split('\t')).toMap()
        assertEquals("EPSG:4674", row["geodeticDatum"])
        assertFalse(entries.getValue("occurrence.txt"), entries.getValue("occurrence.txt").contains("SIRGAS"))

        val properties = JSONObject(export(ExportFormat.GeoJson, listOf(sirgas)).readText())
            .getJSONArray("features").getJSONObject(0).getJSONObject("properties")
        assertEquals("EPSG:4674", properties.getString("geodeticDatum"))

        val utm = csvRows(export(ExportFormat.Csv, listOf(sirgas), exportProject = project.copy(coordinateFormat = "UTM"))).single()
        assertEquals("EPSG:4674", utm["verbatimSRS"])

        // O LEIA-ME, lido por pessoas, mostra o nome escolhido e o codigo.
        val readme = readZip(export(ExportFormat.Zip, listOf(sirgas))).entries.single { it.key.endsWith("README.txt") }.value
        assertTrue(readme, readme.contains("SIRGAS 2000 (EPSG:4674)"))
    }

    @Test
    fun virgulaDecimalEmCampoNumericoSaiComPonto() {
        val dap = occurrence(1) { copy(dynamicFieldsJson = """{"dbh":"12,4","fieldNumber":"3.2"}""") }
        val dapField = FieldConfigEntity(projectId = 1, dwcTerm = "dbh", labelPt = "DAP (cm)", labelEn = "DBH (cm)", fieldType = "decimal")
        val row = csvRows(export(ExportFormat.Csv, listOf(dap), fields = listOf(dapField))).single()
        assertEquals("12.4", row["dbh"])
    }

    @Test
    fun csvBrasileiroSoTrocaPontoEmColunaNumerica() {
        val registro = occurrence(1) { copy(latitude = -22.5, longitude = -45.25, dynamicFieldsJson = """{"fieldNumber":"3.2"}""") }
        val br = export(ExportFormat.Csv, listOf(registro), exportProject = project.copy(csvStyle = "br"))
        val lines = br.readText().removePrefix("﻿").lines().filter { it.isNotEmpty() }
        val row = lines.first().split(';').zip(lines[1].split(';')).toMap()
        assertEquals("-22,5", row["decimalLatitude"])
        assertEquals("3.2", row["fieldNumber"])
    }

    @Test
    fun geoJsonUsaADataDoRegistroENaoADaExpedicao() {
        val outroDia = occurrence(1) { copy(latitude = -22.5, longitude = -45.0, dynamicFieldsJson = """{"eventDate":"2026-03-15"}""") }
        val json = JSONObject(export(ExportFormat.GeoJson, listOf(outroDia)).readText())
        val properties = json.getJSONArray("features").getJSONObject(0).getJSONObject("properties")
        assertEquals("2026-03-15", properties.getString("eventDate"))
    }

    @Test
    fun geoJsonIncluiRegistroQueHerdaOPontoDaExpedicao() {
        val json = JSONObject(export(ExportFormat.GeoJson, listOf(occurrence(1))).readText())
        val coordinates = json.getJSONArray("features").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(-43.2264, coordinates.getDouble(0), 1e-9)
    }

    @Test
    fun xlsxNaoTrataSufixoDeTipoDoJavaComoNumeroERemoveControles() {
        val estranho = occurrence(1) { copy(recordNumber = "45F", occurrenceRemarks = "linha\u000Bquebrada", individualCount = 3) }
        val sheet = ZipFile(export(ExportFormat.Xlsx, listOf(estranho))).use { zip ->
            zip.entries().toList().filter { it.name.startsWith("xl/worksheets/") }
                .joinToString("") { entry -> zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8) }
        }
        assertFalse(sheet, sheet.contains("<v>45F</v>"))
        assertTrue(sheet.contains("<t>45F</t>"))
        assertFalse(sheet.contains("\u000B"))
        assertTrue(sheet.contains("<v>3</v>"))
    }

    @Test
    fun archiveLevaTermosDarwinCoreNoCoreESemCaminhoDoZip() {
        val subespecie = occurrence(1) {
            copy(recordNumber = "LSCM 123", fieldNotes = "Contagem rápida", dynamicFieldsJson = """{"infraspecificEpithet":"faber"}""")
        }
        val photo = folder.newFile("foto.jpg").apply { writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())) }
        val media = MediaEntity(id = 1, projectId = 1, sessionId = 1, occurrenceId = 1, mediaType = "photo", filePath = photo.path, fileName = "foto.jpg", mimeType = "image/jpeg")
        val entries = readZip(export(ExportFormat.DwcArchive, listOf(subespecie), listOf(media)))
        val header = entries.getValue("occurrence.txt").lines().first().split('\t')
        val row = header.zip(entries.getValue("occurrence.txt").lines()[1].split('\t')).toMap()
        assertEquals("faber", row["infraspecificEpithet"])
        assertEquals("LSCM 123", row["recordNumber"])
        assertEquals("Contagem rápida", row["fieldNotes"])
        assertEquals("", row["associatedMedia"])
        assertFalse("infraspecificEpithet nao vai para MoF", entries["measurementorfact.txt"].orEmpty().contains("Infraspecific"))
    }

    @Test
    fun archiveComUrlPublicaUsaOMesmoLinkNoCoreENaMultimidia() {
        val photo = folder.newFile("f.jpg").apply { writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())) }
        val media = MediaEntity(id = 1, projectId = 1, sessionId = 1, occurrenceId = 1, mediaType = "photo", filePath = photo.path, fileName = "f.jpg", mimeType = "image/jpeg")
        val entries = readZip(
            export(
                ExportFormat.DwcArchive, listOf(occurrence(1)), listOf(media),
                exportProject = project.copy(mediaBaseUrl = "https://zenodo.org/records/1/files/"),
                // "Apenas planilhas" nao pode tirar as midias do archive.
                content = ExportContent.SpreadsheetsOnly
            )
        )
        val core = entries.getValue("occurrence.txt").lines()
        val row = core.first().split('\t').zip(core[1].split('\t')).toMap()
        val link = row.getValue("associatedMedia")
        assertTrue(link, link.startsWith("https://zenodo.org/records/1/files/"))
        assertTrue(entries.getValue("multimedia.txt").contains(link))
    }

    @Test
    fun audioImportadoSaiComAExtensaoDoConteudo() {
        // Arquivo OGG que ficou com nome .wav (bug antigo da importacao).
        val ogg = folder.newFile("AUDIO_1.wav").apply { writeBytes("OggS".toByteArray() + ByteArray(12)) }
        val media = MediaEntity(id = 1, projectId = 1, sessionId = 1, occurrenceId = 1, mediaType = "audio", filePath = ogg.path, fileName = ogg.name, mimeType = "audio/wav")
        val entries = readZip(export(ExportFormat.Zip, listOf(occurrence(1)), listOf(media)))
        assertTrue(entries.keys.toString(), entries.keys.any { it.contains("05_midias/audios/") && it.endsWith(".ogg") })
    }

    @Test
    fun emlNaoUsaNotacaoCientificaPertoDoEquador() {
        val equador = occurrence(1) { copy(latitude = 0.0009, longitude = -0.0004) }
        val eml = readZip(export(ExportFormat.DwcArchive, listOf(equador))).getValue("eml.xml")
        assertFalse(eml, eml.contains("E-4"))
        assertTrue(eml, eml.contains("<northBoundingCoordinate>0.0009</northBoundingCoordinate>"))
        assertTrue(eml, eml.contains("<westBoundingCoordinate>-0.0004</westBoundingCoordinate>"))
    }

    @Test
    fun nomeDaMidiaUsaAEspecieAtualEONumeroFixo() {
        val photo = folder.newFile("p.jpg").apply { writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())) }
        val media = MediaEntity(id = 42, projectId = 1, sessionId = 1, occurrenceId = 1, mediaType = "photo", filePath = photo.path, fileName = "p.jpg", mimeType = "image/jpeg")
        // Identificada no campo como Boana faber, corrigida para Scinax antes de exportar.
        val corrigida = occurrence(1) { copy(scientificName = "Scinax fuscovarius") }
        val names = readZip(export(ExportFormat.Zip, listOf(corrigida), listOf(media))).keys.filter { it.contains("05_midias/fotos/") }
        assertEquals(names.toString(), 1, names.size)
        assertTrue(names.single(), names.single().contains("SCINAX_FUSCOVARIUS"))
        assertTrue(names.single(), names.single().endsWith("_MED000042.jpg"))
    }

    @Test
    fun midiaImportadaSemDataSaiSemDataDeCaptura() {
        val audio = folder.newFile("a.wav").apply { writeBytes("RIFF".toByteArray() + ByteArray(4) + "WAVE".toByteArray()) }
        val media = MediaEntity(
            id = 7, projectId = 1, sessionId = 1, occurrenceId = 1, mediaType = "audio", filePath = audio.path, fileName = "a.wav",
            mimeType = "audio/wav", createdAt = 1_800_000_000_000L, captureTimeKnown = false
        )
        val zip = readZip(export(ExportFormat.Zip, listOf(occurrence(1)), listOf(media), exportProject = project.copy(csvStyle = "intl")))
        val midias = zip.entries.first { it.key.endsWith("midias.csv") }.value.lines().filter { it.isNotBlank() }
        val row = midias.first().split(',').zip(midias[1].split(',')).toMap()
        assertEquals("", row["capturedAt"])
        assertTrue(zip.keys.toString(), zip.keys.any { it.contains("05_midias/audios/SEM-DATA_") })
    }

    @Test
    fun exportacaoTrazComoCitarOApp() {
        val zip = readZip(OikosExporter("9.9.9").export(
            format = ExportFormat.Zip, project = project, sessions = listOf(session), occurrences = listOf(occurrence(1)),
            media = emptyList(), fields = emptyList()
        ) { name -> File(folder.root, name) }.file)
        val citation = zip.entries.first { it.key.endsWith("/COMO_CITAR.txt") }.value
        assertTrue(citation, citation.contains("Versão 9.9.9"))
        val eml = readZip(OikosExporter("9.9.9").export(
            format = ExportFormat.DwcArchive, project = project, sessions = listOf(session), occurrences = listOf(occurrence(1)),
            media = emptyList(), fields = emptyList()
        ) { name -> File(folder.root, "d_$name") }.file).getValue("eml.xml")
        assertTrue(eml, eml.contains("Oikos FieldBook, versão 9.9.9"))
    }

    @Test
    fun projetoEmInglesExportaPastasELeiaMeEmIngles() {
        val audio = folder.newFile("b.wav").apply { writeBytes("RIFF".toByteArray() + ByteArray(4) + "WAVE".toByteArray()) }
        val media = MediaEntity(
            id = 8, projectId = 1, sessionId = 1, occurrenceId = 1, mediaType = "audio", filePath = audio.path, fileName = "b.wav",
            mimeType = "audio/wav", createdAt = 1_800_000_000_000L
        )
        val english = project.copy(language = "en")
        val zip = readZip(OikosExporter("9.9.9").export(
            format = ExportFormat.Zip, project = english, sessions = listOf(session), occurrences = listOf(occurrence(1)),
            media = listOf(media), fields = emptyList()
        ) { name -> File(folder.root, "en_$name") }.file)
        val keys = zip.keys.toString()
        assertTrue(keys, zip.keys.all { it.startsWith("Project_anuros_da_serra/") })
        assertTrue(keys, zip.keys.any { it.endsWith("/HOW_TO_CITE.txt") })
        assertTrue(keys, zip.keys.any { it.endsWith("/01_main_spreadsheet/occurrences_darwin_core.csv") })
        assertTrue(keys, zip.keys.any { it.endsWith("/02_supporting_tables/media.csv") })
        assertTrue(keys, zip.keys.any { it.contains("/05_media/audios/") })
        assertTrue(keys, zip.keys.none { it.contains("midias") || it.contains("planilha") })
        val readme = zip.entries.first { it.key.endsWith("/README.txt") }.value
        assertTrue(readme, readme.startsWith("Oikos FieldBook - Full export"))
        val citation = zip.entries.first { it.key.endsWith("/HOW_TO_CITE.txt") }.value
        assertTrue(citation, citation.startsWith("How to cite Oikos FieldBook") && citation.contains("APA"))
        // O manifest lista os mesmos caminhos do ZIP.
        assertTrue(zip.getValue("Project_anuros_da_serra/manifest.json").contains("05_media/audios/"))
    }

    @Test
    fun projetoEmEspanholExportaPastasLeiaMeEMetadadosEmEspanhol() {
        val audio = folder.newFile("c.wav").apply { writeBytes("RIFF".toByteArray() + ByteArray(4) + "WAVE".toByteArray()) }
        val media = MediaEntity(
            id = 9, projectId = 1, sessionId = 1, occurrenceId = 1, mediaType = "audio", filePath = audio.path, fileName = "c.wav",
            mimeType = "audio/wav", createdAt = 1_800_000_000_000L
        )
        val spanish = project.copy(language = "es")
        val zip = readZip(OikosExporter("9.9.9").export(
            format = ExportFormat.Zip, project = spanish, sessions = listOf(session), occurrences = listOf(occurrence(1)),
            media = listOf(media), fields = emptyList()
        ) { name -> File(folder.root, "es_$name") }.file)
        val keys = zip.keys.toString()
        assertTrue(keys, zip.keys.all { it.startsWith("Proyecto_anuros_da_serra/") })
        assertTrue(keys, zip.keys.any { it.endsWith("/COMO_CITAR.txt") })
        assertTrue(keys, zip.keys.any { it.endsWith("/01_planilla_principal/registros_darwin_core.csv") })
        assertTrue(keys, zip.keys.any { it.endsWith("/02_tablas_de_apoyo/multimedia.csv") })
        assertTrue(keys, zip.keys.any { it.contains("/05_multimedia/audios/") })
        assertTrue(keys, zip.keys.none { it.contains("midias") || it.contains("planilha") || it.endsWith("/media.csv") })
        val readme = zip.entries.first { it.key.endsWith("/README.txt") }.value
        assertTrue(readme, readme.startsWith("Oikos FieldBook - Exportación completa") && readme.contains("Proyecto: "))
        val citation = zip.entries.first { it.key.endsWith("/COMO_CITAR.txt") }.value
        assertTrue(citation, citation.startsWith("Cómo citar Oikos FieldBook") && citation.contains("APA"))
        val eml = readZip(OikosExporter("9.9.9").export(
            format = ExportFormat.DwcArchive, project = spanish, sessions = listOf(session), occurrences = listOf(occurrence(1)),
            media = emptyList(), fields = emptyList()
        ) { name -> File(folder.root, "es_d_$name") }.file).getValue("eml.xml")
        assertTrue(eml, eml.contains("<language>spa</language>"))
        assertTrue(eml, eml.contains("Datos registrados con la app Oikos FieldBook, versión 9.9.9. Maciel"))
    }

    /**
     * GBIF, SiBBr e speciesLink leem o Darwin Core pelo nome das colunas e pelos
     * valores dos vocabularios: o idioma do projeto so pode mudar texto livre
     * (observacoes, metadados), nunca a estrutura do pacote.
     */
    @Test
    fun idiomaDoProjetoNaoMudaColunasNemVocabulariosDoDarwinCore() {
        val record = occurrence(1) {
            copy(
                basisOfRecord = "PreservedSpecimen", occurrenceStatus = "present", sex = "female", lifeStage = "adult",
                individualCount = 2, latitude = -22.5, longitude = -45.0
            )
        }
        fun archive(language: String): Map<String, String> = readZip(OikosExporter("9.9.9").export(
            format = ExportFormat.DwcArchive, project = project.copy(language = language), sessions = listOf(session),
            occurrences = listOf(record), media = emptyList(), fields = emptyList()
        ) { name -> File(folder.root, "lang_${language}_$name") }.file)
        fun rows(text: String): List<Map<String, String>> {
            val lines = text.lines().filter { it.isNotEmpty() }
            val headers = lines.first().split('\t')
            return lines.drop(1).map { headers.zip(it.split('\t')).toMap() }
        }
        val pt = archive("pt")
        listOf("en", "es").forEach { language ->
            val other = archive(language)
            assertEquals(language, pt.keys, other.keys)
            assertEquals(language, pt.getValue("meta.xml"), other.getValue("meta.xml"))
            val a = rows(pt.getValue("occurrence.txt")).single()
            val b = rows(other.getValue("occurrence.txt")).single()
            assertEquals(language, a.keys.toList(), b.keys.toList())
            listOf("basisOfRecord", "occurrenceStatus", "sex", "lifeStage", "individualCount", "decimalLatitude", "decimalLongitude", "eventDate", "occurrenceID")
                .forEach { term -> assertEquals("$language $term", a[term], b[term]) }
        }
        assertTrue(archive("es").getValue("eml.xml").contains("<language>spa</language>"))
    }

    /**
     * Trocar o idioma do app (Configuracoes > Idioma) nao pode mudar os dados
     * exportados: o mesmo projeto sai byte a byte igual com a tela em
     * portugues, ingles ou espanhol, inclusive os avisos gravados no
     * manifest.json e na aba de resumo. So o horario da exportacao muda.
     */
    @Test
    fun idiomaDaTelaNaoMudaNadaDoQueEExportado() {
        val records = listOf(
            occurrence(1) { copy(latitude = -22.5, longitude = -45.0, sex = "female", basisOfRecord = "PreservedSpecimen") },
            // Sem coordenada propria: gera aviso (herda o ponto da expedicao).
            occurrence(2) { copy(occurrenceId = "", lifeStage = "tadpole") }
        )
        listOf("pt", "en", "es").forEach { projectLanguage ->
            // Sem licenca: mais um aviso, que vai para o manifest.json.
            val exportProject = project.copy(language = projectLanguage, license = "")
            val byScreen = AppLanguageScreens.map { screen ->
                com.oikos.fieldbook.util.UiLanguage.testOverride = screen
                try {
                    val file = OikosExporter("9.9.9").export(
                        format = ExportFormat.Zip, project = exportProject, sessions = listOf(session),
                        occurrences = records, media = emptyList(), fields = emptyList()
                    ) { name -> File(folder.root, "tela_${screen}_${projectLanguage}_$name") }.file
                    screen to readZipDeep(file.readBytes()).mapValues { (_, text) -> text.replace(EXPORT_TIME, "<hora>") }
                } finally {
                    com.oikos.fieldbook.util.UiLanguage.testOverride = null
                }
            }
            val (_, reference) = byScreen.first()
            assertTrue(reference.keys.toString(), reference.keys.any { it.endsWith("manifest.json") })
            assertTrue(reference.keys.toString(), reference.keys.any { it.contains(".xlsx!") })
            byScreen.drop(1).forEach { (screen, files) ->
                assertEquals("projeto $projectLanguage, tela $screen", reference.keys, files.keys)
                reference.forEach { (name, text) -> assertEquals("projeto $projectLanguage, tela $screen: $name", text, files[name]) }
            }
        }
    }

    private val AppLanguageScreens = com.oikos.fieldbook.util.AppLanguage.entries.toList()
    private val EXPORT_TIME = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?")

    /** Todas as entradas do ZIP, abrindo tambem os .zip e .xlsx de dentro ("dwca.zip!meta.xml"). */
    private fun readZipDeep(bytes: ByteArray, prefix: String = ""): Map<String, String> {
        val result = mutableMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val content = zip.readBytes()
                if (entry.name.endsWith(".zip") || entry.name.endsWith(".xlsx")) {
                    result += readZipDeep(content, "$prefix${entry.name}!")
                } else {
                    result["$prefix${entry.name}"] = content.toString(Charsets.UTF_8)
                }
            }
        }
        return result
    }

    private fun readZip(file: File): Map<String, String> {
        val result = mutableMapOf<String, String>()
        ZipInputStream(file.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.endsWith(".zip")) {
                    // dwca.zip dentro do ZIP completo
                    result[entry.name] = ""
                } else {
                    result[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
        return result
    }
}
