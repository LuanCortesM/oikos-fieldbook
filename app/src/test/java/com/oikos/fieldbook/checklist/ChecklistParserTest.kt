package com.oikos.fieldbook.checklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * As listas chegam do IPT do JBRJ com o vocabulario da Flora e Funga do Brasil
 * (NOME_ACEITO, ESPECIE...) e, de outras fontes, com o do GBIF. Um erro aqui
 * vira nome errado no registro, por isso os casos reais estao cobertos.
 */
class ChecklistParserTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val floraMeta = """
        <archive xmlns="http://rs.tdwg.org/dwc/text/" metadata="eml.xml">
          <core encoding="UTF-8" fieldsTerminatedBy="\t" linesTerminatedBy="\n" fieldsEnclosedBy="" ignoreHeaderLines="1" rowType="http://rs.tdwg.org/dwc/terms/Taxon">
            <files><location>taxon.txt</location></files>
            <id index="0" />
            <field index="1" term="http://rs.tdwg.org/dwc/terms/taxonID"/>
            <field index="2" term="http://rs.tdwg.org/dwc/terms/acceptedNameUsageID"/>
            <field index="3" term="http://rs.tdwg.org/dwc/terms/scientificName"/>
            <field index="4" term="http://rs.tdwg.org/dwc/terms/acceptedNameUsage"/>
            <field index="5" term="http://rs.tdwg.org/dwc/terms/kingdom"/>
            <field index="6" term="http://rs.tdwg.org/dwc/terms/family"/>
            <field index="7" term="http://rs.tdwg.org/dwc/terms/genus"/>
            <field index="8" term="http://rs.tdwg.org/dwc/terms/specificEpithet"/>
            <field index="9" term="http://rs.tdwg.org/dwc/terms/infraspecificEpithet"/>
            <field index="10" term="http://rs.tdwg.org/dwc/terms/taxonRank"/>
            <field index="11" term="http://rs.tdwg.org/dwc/terms/scientificNameAuthorship"/>
            <field index="12" term="http://rs.tdwg.org/dwc/terms/taxonomicStatus"/>
          </core>
          <extension encoding="UTF-8" fieldsTerminatedBy="\t" linesTerminatedBy="\n" fieldsEnclosedBy="" ignoreHeaderLines="1" rowType="http://rs.gbif.org/terms/1.0/VernacularName">
            <files><location>vernacularname.txt</location></files>
            <coreid index="0" />
            <field index="1" term="http://rs.tdwg.org/dwc/terms/vernacularName"/>
            <field index="2" term="http://purl.org/dc/terms/language"/>
          </extension>
        </archive>
    """.trimIndent()

    private val floraRows = listOf(
        "id\ttaxonID\tacceptedNameUsageID\tscientificName\tacceptedNameUsage\tkingdom\tfamily\tgenus\tspecificEpithet\tinfraspecificEpithet\ttaxonRank\tscientificNameAuthorship\ttaxonomicStatus",
        "6268\t6268\t\tVriesea neoglutinosa Mez\t\tPlantae\tBromeliaceae\tVriesea\tneoglutinosa\t\tESPECIE\tMez\tNOME_ACEITO",
        "9001\t9001\t6268\tVriesea glutinosa var. neoglutinosa (Mez) Reitz\tVriesea neoglutinosa Mez\tPlantae\tBromeliaceae\tVriesea\tglutinosa\tneoglutinosa\tVARIEDADE\t(Mez) Reitz\tSINONIMO",
        "7777\t7777\t\tConchocarpus cuneifolius Nees & Mart. var. cuneifolius\t\tPlantae\tRutaceae\tConchocarpus\tcuneifolius\tcuneifolius\tVARIEDADE\t\tNOME_ACEITO",
        "8888\t8888\t\tVriesea Lindl.\t\tPlantae\tBromeliaceae\tVriesea\t\t\tGENERO\tLindl.\tNOME_ACEITO",
        // Linha incompleta: sem nome, deve ser ignorada.
        "9999\t9999\t\t\t\tPlantae\t\t\t\t\t\t\t"
    )

    private val vernacularRows = listOf(
        "id\tvernacularName\tlanguage",
        "6268\tbromélia\tPORTUGUES",
        "6268\t \tPORTUGUES"
    )

    private val eml = """
        <eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1" packageId="https://ipt.jbrj.gov.br/jbrj/resource?id=lista_especies_flora_brasil/v393.432" system="http://gbif.org" scope="system">
          <dataset>
            <title xml:lang="por">Flora e Funga do Brasil</title>
            <pubDate>2026-10-10</pubDate>
            <intellectualRights><para>Licensed under <ulink url="http://creativecommons.org/licenses/by/4.0/legalcode"><citetitle>CC-BY 4.0</citetitle></ulink>.</para></intellectualRights>
          </dataset>
          <additionalMetadata><metadata><gbif>
            <citation identifier="https://doi.org/10.15468/1mtkaw">Flora e Funga do Brasil. Jardim Bot&amp;acirc;nico do Rio de Janeiro &amp; colaboradores.</citation>
          </gbif></metadata></additionalMetadata>
        </eml:eml>
    """.trimIndent()

    private fun archive(meta: String, files: Map<String, String>, withEml: Boolean = true): ZipFile {
        val file = File(temp.root, "lista-${System.nanoTime()}.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("meta.xml", meta)
            files.forEach { (name, text) -> put(name, text) }
            if (withEml) put("eml.xml", eml)
        }
        return ZipFile(file)
    }

    private fun readAll(zip: ZipFile): Triple<List<TaxonRecord>, List<Triple<String, String, String>>, ChecklistMetadata> {
        val taxa = mutableListOf<TaxonRecord>()
        val vernaculars = mutableListOf<Triple<String, String, String>>()
        val metadata = zip.use { ChecklistParser.read(it, { taxa += it }, { id, name, language -> vernaculars += Triple(id, name, language) }) }
        return Triple(taxa, vernaculars, metadata)
    }

    @Test
    fun leListaDaFloraComVocabularioDoJbrj() {
        val (taxa, _, _) = readAll(archive(floraMeta, mapOf("taxon.txt" to floraRows.joinToString("\n"), "vernacularname.txt" to vernacularRows.joinToString("\n"))))
        assertEquals(4, taxa.size)
        val accepted = taxa.first { it.taxonId == "6268" }
        assertEquals("Vriesea neoglutinosa", accepted.canonicalName)
        assertEquals("Mez", accepted.authorship)
        assertEquals("species", accepted.rank)
        assertEquals(TaxonStatus.ACCEPTED, accepted.status)
        assertEquals("Bromeliaceae", accepted.family)
        assertEquals("Plantae", accepted.kingdom)
    }

    @Test
    fun sinonimoApontaParaONomeAceito() {
        val (taxa, _, _) = readAll(archive(floraMeta, mapOf("taxon.txt" to floraRows.joinToString("\n"))))
        val synonym = taxa.first { it.taxonId == "9001" }
        assertEquals(TaxonStatus.SYNONYM, synonym.status)
        assertEquals("Vriesea glutinosa var. neoglutinosa", synonym.canonicalName)
        assertEquals("variety", synonym.rank)
        assertEquals("6268", synonym.acceptedTaxonId)
        assertEquals("Vriesea neoglutinosa Mez", synonym.acceptedName)
    }

    @Test
    fun autonimoMantemOMarcadorInfraespecifico() {
        val (taxa, _, _) = readAll(archive(floraMeta, mapOf("taxon.txt" to floraRows.joinToString("\n"))))
        assertEquals("Conchocarpus cuneifolius var. cuneifolius", taxa.first { it.taxonId == "7777" }.canonicalName)
        assertEquals("Vriesea", taxa.first { it.taxonId == "8888" }.canonicalName)
        assertEquals("genus", taxa.first { it.taxonId == "8888" }.rank)
    }

    @Test
    fun nomesPopularesVazioSaoIgnorados() {
        val (_, vernaculars, _) = readAll(archive(floraMeta, mapOf("taxon.txt" to floraRows.joinToString("\n"), "vernacularname.txt" to vernacularRows.joinToString("\n"))))
        assertEquals(listOf(Triple("6268", "bromélia", "PORTUGUES")), vernaculars)
    }

    @Test
    fun metadadosVemDoEml() {
        val (_, _, metadata) = readAll(archive(floraMeta, mapOf("taxon.txt" to floraRows.joinToString("\n"))))
        assertEquals("Flora e Funga do Brasil", metadata.title)
        assertEquals("393.432", metadata.version)
        assertEquals("2026-10-10", metadata.pubDate)
        assertEquals("CC BY 4.0", metadata.license)
        assertTrue(metadata.citation, metadata.citation.contains("Jardim Bot") && metadata.citation.contains("& colaboradores"))
    }

    @Test
    fun semEmlOsMetadadosFicamVazios() {
        val (taxa, _, metadata) = readAll(archive(floraMeta, mapOf("taxon.txt" to floraRows.joinToString("\n")), withEml = false))
        assertEquals(4, taxa.size)
        assertEquals("", metadata.version)
    }

    @Test
    fun leListaNoFormatoDoGbifComAspas() {
        val meta = """
            <archive xmlns="http://rs.tdwg.org/dwc/text/">
              <core encoding="UTF-8" fieldsTerminatedBy="," linesTerminatedBy="\n" fieldsEnclosedBy="&quot;" ignoreHeaderLines="1" rowType="http://rs.tdwg.org/dwc/terms/Taxon">
                <files><location>Taxon.csv</location></files>
                <id index="0" />
                <field index="1" term="http://rs.tdwg.org/dwc/terms/scientificName"/>
                <field index="2" term="http://rs.tdwg.org/dwc/terms/scientificNameAuthorship"/>
                <field index="3" term="http://rs.tdwg.org/dwc/terms/taxonRank"/>
                <field index="4" term="http://rs.tdwg.org/dwc/terms/taxonomicStatus"/>
                <field index="5" term="http://rs.tdwg.org/dwc/terms/acceptedNameUsageID"/>
              </core>
            </archive>
        """.trimIndent()
        val rows = listOf(
            "taxonID,scientificName,scientificNameAuthorship,taxonRank,taxonomicStatus,acceptedNameUsageID",
            "\"1\",\"Boana faber (Wied-Neuwied, 1821)\",\"(Wied-Neuwied, 1821)\",\"species\",\"accepted\",\"\"",
            "\"2\",\"Hyla faber Wied-Neuwied, 1821\",\"Wied-Neuwied, 1821\",\"species\",\"homotypic synonym\",\"1\"",
            "\"3\",\"Rana \"\"sp.\"\" nova\",\"\",\"species\",\"doubtful\",\"\""
        )
        val (taxa, _, _) = readAll(archive(meta, mapOf("Taxon.csv" to rows.joinToString("\r\n")), withEml = false))
        assertEquals(3, taxa.size)
        assertEquals("1", taxa[0].taxonId)
        assertEquals("Boana faber", taxa[0].canonicalName)
        assertEquals("(Wied-Neuwied, 1821)", taxa[0].authorship)
        assertEquals(TaxonStatus.SYNONYM, taxa[1].status)
        assertEquals("Hyla faber", taxa[1].canonicalName)
        assertEquals("Rana \"sp.\" nova", taxa[2].canonicalName)
        assertEquals(TaxonStatus.ACCEPTED, taxa[2].status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun arquivoSemNucleoTaxonERecusado() {
        val meta = """
            <archive xmlns="http://rs.tdwg.org/dwc/text/">
              <core fieldsTerminatedBy="\t" ignoreHeaderLines="1" rowType="http://rs.tdwg.org/dwc/terms/Occurrence">
                <files><location>occurrence.txt</location></files>
                <id index="0" />
              </core>
            </archive>
        """.trimIndent()
        readAll(archive(meta, mapOf("occurrence.txt" to "id\n1"), withEml = false))
    }

    @Test
    fun rankEStatusEmPortuguesEIngles() {
        assertEquals("species", ChecklistParser.normalizeRank("ESPECIE"))
        assertEquals("subspecies", ChecklistParser.normalizeRank("SUB_ESPECIE"))
        assertEquals("form", ChecklistParser.normalizeRank("FORMA"))
        assertEquals("genus", ChecklistParser.normalizeRank("Genus"))
        assertEquals("section", ChecklistParser.normalizeRank("SECTION"))
        assertEquals(TaxonStatus.ACCEPTED, TaxonStatus.parse("NOME_ACEITO"))
        assertEquals(TaxonStatus.SYNONYM, TaxonStatus.parse("SINONIMO"))
        assertEquals(TaxonStatus.SYNONYM, TaxonStatus.parse("heterotypicSynonym"))
        assertEquals(TaxonStatus.OTHER, TaxonStatus.parse(""))
        assertEquals(TaxonStatus.OTHER, TaxonStatus.parse("bare name"))
    }

    @Test
    fun chaveDeBuscaIgnoraAcentoECaixa() {
        // Hifen vira espaco: "sapo cururu" precisa achar "sapo-cururu".
        assertEquals("bromelia de pedra", ChecklistParser.searchKey("  Bromélia-de-Pedra "))
        assertEquals(ChecklistParser.searchKey("sapo cururu"), ChecklistParser.searchKey("Sapo-cururu"))
        assertEquals("perereca martelo", ChecklistParser.searchKey("Perereca   (martelo)"))
        assertEquals("vriesea glutinosa var. neoglutinosa", ChecklistParser.searchKey("Vriesea glutinosa var. neoglutinosa"))
    }
}
