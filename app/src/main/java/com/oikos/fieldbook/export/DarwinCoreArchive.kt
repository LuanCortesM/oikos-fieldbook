package com.oikos.fieldbook.export

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Metadados do conjunto de dados, usados para gerar o eml.xml. */
data class ArchiveMetadata(
    val datasetTitle: String,
    /** Pessoa responsavel; pode ficar vazio, e entao so a instituicao aparece. */
    val creatorName: String,
    val organization: String,
    val abstractText: String,
    /** "CC0-1.0", "CC-BY-4.0", "CC-BY-NC-4.0" ou vazio (licenca ainda nao definida). */
    val licenseCode: String,
    val methods: String,
    val language: String = "por",
    val packageId: String,
    val pubDate: String,
    val geographicDescription: String,
    val westBound: Double?,
    val eastBound: Double?,
    val southBound: Double?,
    val northBound: Double?,
    val beginDate: String,
    val endDate: String,
    val taxa: List<String>,
    /** Software usado na coleta, com a citacao; vira um passo de metodo no EML. */
    val softwareCitation: String = ""
)

/**
 * Monta um Darwin Core Archive valido (DwC-A).
 *
 * Um DwC-A e o formato que IPT, GBIF e SiBBr ingerem diretamente: arquivos de
 * texto delimitados por tabulacao, um meta.xml declarando a qual termo do padrao
 * cada coluna corresponde (pela URI completa da TDWG) e um eml.xml com os
 * metadados do conjunto. Planilha com nome de coluna do Darwin Core nao
 * substitui isso: sem o meta.xml nao ha como a maquina saber que a coluna
 * chamada scientificName e http://rs.tdwg.org/dwc/terms/scientificName.
 *
 * Core: Occurrence. Extensoes: MeasurementOrFact e Simple Multimedia.
 */
object DarwinCoreArchive {

    private const val CORE_ROW_TYPE = "http://rs.tdwg.org/dwc/terms/Occurrence"
    private const val MOF_ROW_TYPE = "http://rs.tdwg.org/dwc/terms/MeasurementOrFact"
    private const val MULTIMEDIA_ROW_TYPE = "http://rs.gbif.org/terms/1.0/Multimedia"

    /** Termos que pertencem ao Dublin Core, nao ao namespace do Darwin Core. */
    private val dublinCoreTerms = setOf(
        "type", "modified", "language", "license", "rightsHolder", "accessRights",
        "bibliographicCitation", "references", "identifier", "created", "title",
        "description", "creator", "contributor", "publisher", "audience", "source",
        "format"
    )

    /** Termos da Audubon Core usados pela extensao de multimidia. */
    private val audubonTerms = setOf("accessURI", "providerManagedID", "captureDevice")

    fun termUri(term: String): String = when (term) {
        in dublinCoreTerms -> "http://purl.org/dc/terms/" + term
        in audubonTerms -> "http://rs.tdwg.org/ac/terms/" + term
        else -> "http://rs.tdwg.org/dwc/terms/" + term
    }

    fun build(
        occurrenceHeaders: List<String>,
        occurrenceRows: List<Map<String, String>>,
        measurementHeaders: List<String>,
        measurementRows: List<Map<String, String>>,
        multimediaHeaders: List<String>,
        multimediaRows: List<Map<String, String>>,
        metadata: ArchiveMetadata
    ): ByteArray {
        val usaMof = measurementRows.isNotEmpty()
        val usaMultimidia = multimediaRows.isNotEmpty()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.writeText(
                "meta.xml",
                metaXml(
                    occurrenceHeaders,
                    if (usaMof) measurementHeaders else emptyList(),
                    if (usaMultimidia) multimediaHeaders else emptyList()
                )
            )
            zip.writeText("eml.xml", emlXml(metadata))
            zip.writeText("occurrence.txt", tsv(occurrenceHeaders, occurrenceRows))
            if (usaMof) zip.writeText("measurementorfact.txt", tsv(measurementHeaders, measurementRows))
            if (usaMultimidia) zip.writeText("multimedia.txt", tsv(multimediaHeaders, multimediaRows))
        }
        return output.toByteArray()
    }

    /**
     * Texto delimitado por tabulacao, sem BOM e sem aspas de campo, exatamente
     * como declarado no meta.xml. Tabulacao e quebra de linha dentro do valor
     * viram espaco, que e a forma segura para este delimitador.
     */
    fun tsv(headers: List<String>, rows: List<Map<String, String>>): String = buildString {
        append(headers.joinToString(SEP))
        append(EOL)
        rows.forEach { row ->
            append(headers.joinToString(SEP) { header -> clean(row[header].orEmpty()) })
            append(EOL)
        }
    }

    fun clean(value: String): String = xmlSafe(value)
        .replace(SEP, " ")
        .replace("\r\n", " ")
        .replace("\n", " ")
        .replace("\r", " ")
        .trim()

    private const val SEP = "\t"
    private const val EOL = "\n"

    fun metaXml(
        occurrenceHeaders: List<String>,
        measurementHeaders: List<String>,
        multimediaHeaders: List<String>
    ): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<archive xmlns=\"http://rs.tdwg.org/dwc/text/\" metadata=\"eml.xml\">\n")
        append(section("core", "occurrence.txt", CORE_ROW_TYPE, occurrenceHeaders, isCore = true))
        if (measurementHeaders.isNotEmpty()) {
            append(section("extension", "measurementorfact.txt", MOF_ROW_TYPE, measurementHeaders, isCore = false))
        }
        if (multimediaHeaders.isNotEmpty()) {
            append(section("extension", "multimedia.txt", MULTIMEDIA_ROW_TYPE, multimediaHeaders, isCore = false))
        }
        append("</archive>\n")
    }

    private fun section(
        tag: String,
        fileName: String,
        rowType: String,
        headers: List<String>,
        isCore: Boolean
    ): String = buildString {
        append("  <").append(tag)
        append(" encoding=\"UTF-8\" fieldsTerminatedBy=\"\\t\" linesTerminatedBy=\"\\n\"")
        append(" fieldsEnclosedBy=\"\" ignoreHeaderLines=\"1\" rowType=\"").append(rowType).append("\">\n")
        append("    <files><location>").append(fileName).append("</location></files>\n")
        // Coluna 0 e sempre o occurrenceID: identificador da linha no core e
        // chave estrangeira para o core nas extensoes.
        append(if (isCore) "    <id index=\"0\"/>\n" else "    <coreid index=\"0\"/>\n")
        headers.forEachIndexed { index, term ->
            if (isCore || index > 0) {
                append("    <field index=\"").append(index)
                append("\" term=\"").append(termUri(term)).append("\"/>\n")
            }
        }
        append("  </").append(tag).append(">\n")
    }

    fun emlXml(m: ArchiveMetadata): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<eml:eml xmlns:eml=\"eml://ecoinformatics.org/eml-2.1.1\"")
        append(" xmlns:dc=\"http://purl.org/dc/terms/\"")
        append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
        append(" xsi:schemaLocation=\"eml://ecoinformatics.org/eml-2.1.1 ")
        append("http://rs.gbif.org/schema/eml-gbif-profile/1.1/eml.xsd\"")
        append(" packageId=\"").append(xml(m.packageId)).append("\"")
        append(" system=\"http://gbif.org\" scope=\"system\"")
        append(" xml:lang=\"").append(m.language).append("\">\n")
        append("  <dataset>\n")
        append("    <title xml:lang=\"").append(m.language).append("\">")
        append(xml(m.datasetTitle)).append("</title>\n")
        // A ordem dos elementos segue a sequencia do esquema EML 2.1.1:
        // title, creator, metadataProvider, pubDate, language, abstract,
        // intellectualRights, coverage, contact, methods. contact e obrigatorio.
        append(party("creator", m))
        append(party("metadataProvider", m))
        append("    <pubDate>").append(xml(m.pubDate)).append("</pubDate>\n")
        append("    <language>").append(m.language).append("</language>\n")
        append("    <abstract><para>").append(xml(m.abstractText)).append("</para></abstract>\n")
        append("    <intellectualRights>").append(licenseParagraph(m.licenseCode, m.language)).append("</intellectualRights>\n")

        val temGeo = m.westBound != null && m.eastBound != null &&
            m.southBound != null && m.northBound != null
        val temData = m.beginDate.isNotBlank() && m.endDate.isNotBlank()
        if (temGeo || temData || m.taxa.isNotEmpty()) {
            append("    <coverage>\n")
            if (temGeo) {
                append("      <geographicCoverage>\n")
                append("        <geographicDescription>").append(xml(m.geographicDescription))
                append("</geographicDescription>\n")
                append("        <boundingCoordinates>\n")
                append("          <westBoundingCoordinate>").append(bound(m.westBound)).append("</westBoundingCoordinate>\n")
                append("          <eastBoundingCoordinate>").append(bound(m.eastBound)).append("</eastBoundingCoordinate>\n")
                append("          <northBoundingCoordinate>").append(bound(m.northBound)).append("</northBoundingCoordinate>\n")
                append("          <southBoundingCoordinate>").append(bound(m.southBound)).append("</southBoundingCoordinate>\n")
                append("        </boundingCoordinates>\n")
                append("      </geographicCoverage>\n")
            }
            if (temData) {
                append("      <temporalCoverage><rangeOfDates>\n")
                append("        <beginDate><calendarDate>").append(xml(m.beginDate))
                append("</calendarDate></beginDate>\n")
                append("        <endDate><calendarDate>").append(xml(m.endDate))
                append("</calendarDate></endDate>\n")
                append("      </rangeOfDates></temporalCoverage>\n")
            }
            if (m.taxa.isNotEmpty()) {
                append("      <taxonomicCoverage>\n")
                m.taxa.take(200).forEach { taxon ->
                    append("        <taxonomicClassification><taxonRankValue>")
                    append(xml(taxon))
                    append("</taxonRankValue></taxonomicClassification>\n")
                }
                append("      </taxonomicCoverage>\n")
            }
            append("    </coverage>\n")
        }
        append(party("contact", m))
        if (m.methods.isNotBlank() || m.softwareCitation.isNotBlank()) {
            append("    <methods>")
            if (m.methods.isNotBlank()) {
                append("<methodStep><description><para>").append(xml(m.methods)).append("</para></description></methodStep>")
            }
            // O aplicativo usado na coleta, com a forma de citar.
            if (m.softwareCitation.isNotBlank()) {
                append("<methodStep><description><para>").append(xml(m.softwareCitation)).append("</para></description></methodStep>")
            }
            append("</methods>\n")
        }
        append("  </dataset>\n")
        append("</eml:eml>\n")
    }

    /**
     * Parte responsavel (creator, metadataProvider, contact). O EML exige ao
     * menos pessoa ou organizacao; se o projeto nao informar nenhuma das duas,
     * o titulo do conjunto entra como organizacao para o arquivo continuar valido.
     */
    private fun party(tag: String, m: ArchiveMetadata): String = buildString {
        append("    <").append(tag).append(">\n")
        if (m.creatorName.isNotBlank()) {
            append("      <individualName><surName>").append(xml(m.creatorName)).append("</surName></individualName>\n")
        }
        val organization = m.organization.ifBlank { if (m.creatorName.isBlank()) m.datasetTitle else "" }
        if (organization.isNotBlank()) {
            append("      <organizationName>").append(xml(organization)).append("</organizationName>\n")
        }
        append("    </").append(tag).append(">\n")
    }

    /** Texto da licenca no mesmo formato que o IPT do GBIF gera e reconhece. */
    fun licenseParagraph(code: String, language: String = "por"): String = when (code) {
        "CC0-1.0" -> "<para>To the extent possible under law, the publisher has waived all rights to these data and has dedicated them to the <ulink url=\"http://creativecommons.org/publicdomain/zero/1.0/legalcode\"><citetitle>Public Domain (CC0 1.0)</citetitle></ulink>. Users may copy, modify, distribute and use the work, including for commercial purposes, without restriction.</para>"
        "CC-BY-4.0" -> "<para>This work is licensed under a <ulink url=\"http://creativecommons.org/licenses/by/4.0/legalcode\"><citetitle>Creative Commons Attribution (CC-BY) 4.0 License</citetitle></ulink>.</para>"
        "CC-BY-NC-4.0" -> "<para>This work is licensed under a <ulink url=\"http://creativecommons.org/licenses/by-nc/4.0/legalcode\"><citetitle>Creative Commons Attribution Non Commercial (CC-BY-NC) 4.0 License</citetitle></ulink>.</para>"
        else -> when (language) {
            "eng" -> "<para>License not yet defined by the data holder. Set CC0, CC BY 4.0 or CC BY-NC 4.0 in the project before publishing.</para>"
            "spa" -> "<para>Licencia aún no definida por el titular de los datos. Define CC0, CC BY 4.0 o CC BY-NC 4.0 en el proyecto antes de publicar.</para>"
            else -> "<para>Licença ainda não definida pelo detentor dos dados. Defina CC0, CC BY 4.0 ou CC BY-NC 4.0 no projeto antes de publicar.</para>"
        }
    }

    /**
     * Coordenada do retangulo como xs:decimal. append(Double) escrevia
     * "9.0E-4" perto do equador ou de Greenwich, o que o esquema do EML recusa.
     */
    private fun bound(value: Double?): String =
        value?.let { java.math.BigDecimal(it).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() }.orEmpty()

    private fun xml(value: String): String = xmlSafe(value)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun ZipOutputStream.writeText(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(StandardCharsets.UTF_8))
        closeEntry()
    }
}
