package com.oikos.fieldbook.checklist

import org.w3c.dom.Element
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.Normalizer
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/** Um nome da lista, ja normalizado. */
data class TaxonRecord(
    val taxonId: String,
    /** Nome sem autoria: "Vriesea neoglutinosa". */
    val canonicalName: String,
    val authorship: String,
    /** Termo do Darwin Core: species, variety, genus... */
    val rank: String,
    val status: TaxonStatus,
    val acceptedTaxonId: String,
    /** Nome aceito completo, com autoria, quando este e sinonimo. */
    val acceptedName: String,
    val family: String,
    val genus: String,
    val kingdom: String
)

enum class TaxonStatus(val code: String) {
    ACCEPTED("A"), SYNONYM("S"), OTHER("O");

    companion object {
        fun fromCode(code: String): TaxonStatus = entries.firstOrNull { it.code == code } ?: OTHER

        /** Aceita o vocabulario do GBIF e o da Flora e Funga do Brasil (NOME_ACEITO, SINONIMO). */
        fun parse(value: String): TaxonStatus {
            val v = value.trim().lowercase(Locale.ROOT)
            return when {
                v.isEmpty() -> OTHER
                v == "accepted" || v == "valid" || v == "nome_aceito" || v == "aceito" || v == "doubtful" -> ACCEPTED
                v.contains("synonym") || v.contains("sinonimo") || v == "misapplied" -> SYNONYM
                else -> OTHER
            }
        }
    }
}

/** Metadados da lista, lidos do eml.xml do proprio arquivo. */
data class ChecklistMetadata(
    val title: String,
    val version: String,
    val pubDate: String,
    val license: String,
    val citation: String
)

/**
 * Le uma lista de especies em Darwin Core Archive (core Taxon), o formato em que
 * a Flora e Funga do Brasil e o Catalogo Taxonomico da Fauna do Brasil sao
 * publicados no IPT do JBRJ. Le o meta.xml para achar colunas e separadores,
 * percorre o taxon.txt em fluxo (sem carregar 100 MB na memoria) e a extensao
 * VernacularName, se houver.
 *
 * Sem dependencia de Android, para teste na JVM.
 */
object ChecklistParser {

    private const val DWC = "http://rs.tdwg.org/dwc/terms/"
    private const val TAXON_ROW = "http://rs.tdwg.org/dwc/terms/Taxon"
    private const val VERNACULAR_ROW = "http://rs.gbif.org/terms/1.0/VernacularName"

    private data class FileSpec(
        val location: String,
        val separator: Char,
        val quote: Char?,
        val headerLines: Int,
        val idIndex: Int,
        val columns: Map<String, Int>
    )

    fun read(
        zip: ZipFile,
        onTaxon: (TaxonRecord) -> Unit,
        onVernacular: (taxonId: String, name: String, language: String) -> Unit,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): ChecklistMetadata {
        val meta = zip.getEntry("meta.xml") ?: throw IllegalArgumentException("Arquivo sem meta.xml: não é um Darwin Core Archive.")
        val specs = zip.getInputStream(meta).use { parseMeta(it) }
        val core = specs[TAXON_ROW] ?: throw IllegalArgumentException("O arquivo não é uma lista de espécies (core Taxon).")
        val coreEntry = zip.getEntry(core.location) ?: throw IllegalArgumentException("Arquivo ${core.location} ausente.")
        val vernacularSpec = specs[VERNACULAR_ROW]
        val vernacularEntry = vernacularSpec?.let { zip.getEntry(it.location) }
        val total = coreEntry.size.coerceAtLeast(0) + (vernacularEntry?.size?.coerceAtLeast(0) ?: 0)

        var read = 0L
        readRows(zip, coreEntry, core) { cells, bytes ->
            read += bytes
            toTaxon(cells, core)?.let(onTaxon)
            onProgress(read, total)
        }
        if (vernacularSpec != null && vernacularEntry != null) {
            val nameIndex = vernacularSpec.columns[DWC + "vernacularName"]
            val languageIndex = vernacularSpec.columns["http://purl.org/dc/terms/language"]
            readRows(zip, vernacularEntry, vernacularSpec) { cells, bytes ->
                read += bytes
                val id = cells.getOrNull(vernacularSpec.idIndex).orEmpty()
                val name = nameIndex?.let { cells.getOrNull(it) }.orEmpty().trim()
                if (id.isNotEmpty() && name.isNotEmpty()) onVernacular(id, name, languageIndex?.let { cells.getOrNull(it) }.orEmpty())
                onProgress(read, total)
            }
        }
        val emlEntry = zip.getEntry("eml.xml")
        return emlEntry?.let { entry -> zip.getInputStream(entry).use { parseEml(it) } } ?: ChecklistMetadata("", "", "", "", "")
    }

    private fun readRows(zip: ZipFile, entry: ZipEntry, spec: FileSpec, onRow: (List<String>, Long) -> Unit) {
        BufferedReader(InputStreamReader(zip.getInputStream(entry), Charsets.UTF_8), 1 shl 16).use { reader ->
            var lineNumber = 0
            while (true) {
                val line = reader.readLine() ?: break
                lineNumber++
                if (lineNumber <= spec.headerLines) continue
                onRow(split(line, spec.separator, spec.quote), line.length + 1L)
            }
        }
    }

    private fun split(line: String, separator: Char, quote: Char?): List<String> {
        if (quote == null) return line.split(separator)
        val cells = ArrayList<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == quote && quoted && i + 1 < line.length && line[i + 1] == quote -> { current.append(quote); i++ }
                c == quote -> quoted = !quoted
                c == separator && !quoted -> { cells += current.toString(); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        cells += current.toString()
        return cells
    }

    private fun toTaxon(cells: List<String>, spec: FileSpec): TaxonRecord? {
        fun cell(term: String): String = spec.columns[DWC + term]?.let { cells.getOrNull(it) }.orEmpty().trim()
        val id = cell("taxonID").ifEmpty { cells.getOrNull(spec.idIndex).orEmpty().trim() }
        val fullName = cell("scientificName")
        if (id.isEmpty() || fullName.isEmpty()) return null
        val authorship = cell("scientificNameAuthorship")
        return TaxonRecord(
            taxonId = id,
            canonicalName = canonical(fullName, authorship, cell("genus"), cell("specificEpithet"), cell("infraspecificEpithet")),
            authorship = authorship,
            rank = normalizeRank(cell("taxonRank")),
            status = TaxonStatus.parse(cell("taxonomicStatus")),
            acceptedTaxonId = cell("acceptedNameUsageID"),
            acceptedName = cell("acceptedNameUsage"),
            family = cell("family"),
            genus = cell("genus"),
            kingdom = cell("kingdom")
        )
    }

    /**
     * Nome sem autoria. As listas do JBRJ trazem a autoria dentro de
     * scientificName ("Vriesea neoglutinosa Mez"); ela e retirada quando o fim
     * do nome coincide com scientificNameAuthorship.
     */
    fun canonical(fullName: String, authorship: String, genus: String = "", epithet: String = "", infra: String = ""): String {
        val name = fullName.trim()
        if (authorship.isNotBlank() && name.endsWith(authorship.trim())) {
            return name.removeSuffix(authorship.trim()).trim()
        }
        if (genus.isNotBlank() && epithet.isNotBlank()) {
            val prefix = listOf(genus, epithet).joinToString(" ")
            if (name.startsWith(prefix)) {
                // Mantem o marcador infraespecifico (var., subsp., f.) mesmo nos
                // autonimos, em que a autoria fica no meio:
                // "Conchocarpus cuneifolius Nees & Mart. var. cuneifolius".
                val rest = name.removePrefix(prefix).trim().split(" ")
                val marker = rest.firstOrNull { it in setOf("var.", "subsp.", "ssp.", "f.", "fo.") }
                return if (infra.isNotBlank()) listOfNotNull(prefix, marker, infra).joinToString(" ") else prefix
            }
        }
        return name
    }

    /** Ranks da Flora e Funga do Brasil (ESPECIE, VARIEDADE...) para o termo do Darwin Core. */
    fun normalizeRank(value: String): String = when (value.trim().uppercase(Locale.ROOT)) {
        "ESPECIE", "ESPÉCIE", "SPECIES" -> "species"
        "SUB_ESPECIE", "SUBESPECIE", "SUBSPECIES" -> "subspecies"
        "VARIEDADE", "VARIETY" -> "variety"
        "FORMA", "FORM" -> "form"
        "GENERO", "GÊNERO", "GENUS" -> "genus"
        "FAMILIA", "FAMÍLIA", "FAMILY" -> "family"
        "ORDEM", "ORDER" -> "order"
        "CLASSE", "CLASS" -> "class"
        "DIVISAO", "DIVISÃO", "FILO", "PHYLUM" -> "phylum"
        "TRIBO", "TRIBE" -> "tribe"
        "SUB_FAMILIA", "SUBFAMILIA", "SUBFAMILY" -> "subfamily"
        "" -> ""
        else -> value.trim().lowercase(Locale.ROOT)
    }

    /**
     * Chave de busca: minusculas, sem acento, espacos simples. Hifen vira
     * espaco: antes "sapo cururu" nao achava "sapo-cururu" (nem "pau brasil",
     * "pau-brasil").
     */
    fun searchKey(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale.ROOT)
            .replace("[^a-z0-9 .]+".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    private fun parseMeta(input: InputStream): Map<String, FileSpec> {
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }.newDocumentBuilder().parse(input)
        val result = mutableMapOf<String, FileSpec>()
        val nodes = document.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as? Element ?: continue
            if (element.tagName != "core" && element.tagName != "extension") continue
            val rowType = element.getAttribute("rowType")
            val location = element.getElementsByTagName("location").item(0)?.textContent?.trim().orEmpty()
            val separator = unescape(element.getAttribute("fieldsTerminatedBy").ifEmpty { "," }).firstOrNull() ?: ','
            val quote = unescape(element.getAttribute("fieldsEnclosedBy")).firstOrNull()
            val headerLines = element.getAttribute("ignoreHeaderLines").toIntOrNull() ?: 0
            val idElement = (element.getElementsByTagName("id").item(0) ?: element.getElementsByTagName("coreid").item(0)) as? Element
            val idIndex = idElement?.getAttribute("index")?.toIntOrNull() ?: 0
            val columns = mutableMapOf<String, Int>()
            val fields = element.getElementsByTagName("field")
            for (f in 0 until fields.length) {
                val field = fields.item(f) as Element
                val index = field.getAttribute("index").toIntOrNull() ?: continue
                columns[field.getAttribute("term")] = index
            }
            result[rowType] = FileSpec(location, separator, quote, headerLines, idIndex, columns)
        }
        return result
    }

    private fun unescape(value: String): String = value.replace("\\t", "\t").replace("\\n", "\n").replace("&quot;", "\"")

    fun parseEml(input: InputStream): ChecklistMetadata {
        val text = input.bufferedReader(Charsets.UTF_8).readText()
        fun first(regex: String): String = Regex(regex, RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val license = when {
            text.contains("creativecommons.org/publicdomain/zero") -> "CC0 1.0"
            text.contains("creativecommons.org/licenses/by-nc/") -> "CC BY-NC 4.0"
            text.contains("creativecommons.org/licenses/by/") -> "CC BY 4.0"
            else -> ""
        }
        fun decode(value: String): String = value.replace("&amp;amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&").replace("\\s+".toRegex(), " ")
        val citation = decode(first("<citation[^>]*>(.*?)</citation>"))
        return ChecklistMetadata(
            title = decode(first("<title[^>]*>(.*?)</title>")),
            version = first("packageId=\"[^\"]*/v([^\"]+)\""),
            pubDate = first("<pubDate>(.*?)</pubDate>"),
            license = license,
            citation = citation
        )
    }
}
