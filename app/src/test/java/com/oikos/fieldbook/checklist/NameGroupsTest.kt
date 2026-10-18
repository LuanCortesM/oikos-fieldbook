package com.oikos.fieldbook.checklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Grupos do filtro de projeto e distribuicao por estado, como vem das listas do JBRJ. */
class NameGroupsTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `flora e funga pelo segundo nivel da classificacao`() {
        assertEquals("angio", NameGroup.groupOf("Plantae", "Tracheophyta", "Magnoliopsida", "Poales", "Flora e Funga;Angiospermas"))
        assertEquals("samam", NameGroup.groupOf("Plantae", "Tracheophyta", "Polypodiopsida", "", "Flora e Funga;Samambaias e Licófitas"))
        assertEquals("fungos", NameGroup.groupOf("Fungi", "Basidiomycota", "Agaricomycetes", "", "Flora e Funga;Fungos"))
        assertEquals("algas", NameGroup.groupOf("Plantae", "Rhodophyta", "Florideophyceae", "", "Flora e Funga;Algas"))
    }

    @Test
    fun `lista de outra fonte usa reino filo e classe`() {
        assertEquals("gimno", NameGroup.groupOf("Plantae", "Tracheophyta", "Pinopsida", "", ""))
        assertEquals("brio", NameGroup.groupOf("Plantae", "Marchantiophyta", "Jungermanniopsida", "", ""))
        assertEquals("algas", NameGroup.groupOf("Plantae", "Chlorophyta", "Ulvophyceae", "", ""))
        assertEquals("fungos", NameGroup.groupOf("Fungi", "", "", "", ""))
    }

    @Test
    fun `fauna pela classe e pelo filo`() {
        assertEquals("anfib", NameGroup.groupOf("Animalia", "Chordata", "Amphibia", "Anura", "Animalia"))
        assertEquals("rept", NameGroup.groupOf("Animalia", "Chordata", "Reptilia", "Squamata", "Animalia"))
        assertEquals("rept", NameGroup.groupOf("Animalia", "Chordata", "", "Testudines", "Animalia"))
        assertEquals("aves", NameGroup.groupOf("Animalia", "Chordata", "Aves", "", ""))
        assertEquals("mamif", NameGroup.groupOf("Animalia", "Chordata", "Mammalia", "", ""))
        assertEquals("peixes", NameGroup.groupOf("Animalia", "Chordata", "Actinopterygii", "", ""))
        assertEquals("peixes", NameGroup.groupOf("Animalia", "Chordata", "Chondrichthyes", "", ""))
        assertEquals("insetos", NameGroup.groupOf("Animalia", "Arthropoda", "Insecta", "", ""))
        assertEquals("aracn", NameGroup.groupOf("Animalia", "Arthropoda", "Arachnida", "", ""))
        assertEquals("artrop", NameGroup.groupOf("Animalia", "Arthropoda", "Malacostraca", "", ""))
        assertEquals("molus", NameGroup.groupOf("Animalia", "Mollusca", "Gastropoda", "", ""))
        assertEquals("invert", NameGroup.groupOf("Animalia", "Cnidaria", "Anthozoa", "", ""))
        // Tunicado: cordado, mas invertebrado.
        assertEquals("invert", NameGroup.groupOf("Animalia", "Chordata", "Ascidiacea", "", ""))
        // Sem classificacao: sem grupo (passa em qualquer filtro).
        assertEquals("", NameGroup.groupOf("", "", "", "", ""))
    }

    @Test
    fun `selecao do projeto ignora codigo desconhecido`() {
        assertEquals(setOf("anfib", "angio"), NameGroup.parseSelection("anfib, angio,xyz"))
        assertTrue(NameGroup.parseSelection("").isEmpty())
    }

    @Test
    fun `distribuicao da flora com endemismo e dominios`() {
        val d = TaxonDistribution.parse(
            listOf("BR-RJ", "BR-ES | BR-BA"),
            listOf("Nativa"),
            listOf("{\"endemism\":\"Endemica\",\"phytogeographicDomain\":[\"Mata Atlântica\"]}")
        )
        assertEquals(listOf("BA", "ES", "RJ"), d.states)
        assertEquals(listOf("native"), d.establishment)
        assertEquals(true, d.endemic)
        assertEquals(listOf("Mata Atlântica"), d.domains)
    }

    @Test
    fun `distribuicao da fauna sem endemismo`() {
        val d = TaxonDistribution.parse(listOf("BR-RS", "", "BR-SC"), listOf("introduced", "introduced"), emptyList())
        assertEquals(listOf("RS", "SC"), d.states)
        assertEquals(listOf("introduced"), d.establishment)
        assertNull(d.endemic)
        assertFalse(d.isEmpty)
        assertTrue(TaxonDistribution.parse(emptyList(), emptyList(), emptyList()).isEmpty)
    }

    @Test
    fun `arquivo enxuto traz grupo e distribuicao junta por taxon`() {
        val meta = """
            <archive xmlns="http://rs.tdwg.org/dwc/text/" metadata="eml.xml">
              <core encoding="UTF-8" fieldsTerminatedBy="\t" linesTerminatedBy="\n" fieldsEnclosedBy="" ignoreHeaderLines="1" rowType="http://rs.tdwg.org/dwc/terms/Taxon">
                <files><location>taxon.txt</location></files>
                <id index="0"/>
                <field index="1" term="http://rs.tdwg.org/dwc/terms/scientificName"/>
                <field index="2" term="http://rs.tdwg.org/dwc/terms/scientificNameAuthorship"/>
                <field index="3" term="http://rs.tdwg.org/dwc/terms/taxonomicStatus"/>
                <field index="4" term="http://rs.tdwg.org/dwc/terms/higherClassification"/>
                <field index="5" term="http://rs.tdwg.org/dwc/terms/kingdom"/>
                <field index="6" term="http://rs.tdwg.org/dwc/terms/family"/>
              </core>
              <extension encoding="UTF-8" fieldsTerminatedBy="\t" linesTerminatedBy="\n" fieldsEnclosedBy="" ignoreHeaderLines="1" rowType="http://rs.gbif.org/terms/1.0/Distribution">
                <files><location>distribution.txt</location></files>
                <coreid index="0"/>
                <field index="1" term="http://rs.tdwg.org/dwc/terms/locationID"/>
                <field index="2" term="http://rs.tdwg.org/dwc/terms/establishmentMeans"/>
              </extension>
            </archive>
        """.trimIndent()
        val file = File(temp.root, "enxuta.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
            put("meta.xml", meta)
            put("taxon.txt", "id\tscientificName\tscientificNameAuthorship\ttaxonomicStatus\thigherClassification\tkingdom\tfamily\n" +
                "1\tVriesea neoglutinosa\tMez\tNOME_ACEITO\tFlora e Funga;Angiospermas\tPlantae\tBromeliaceae\n" +
                "2\tAgaricales\tUnderw.\tNOME_ACEITO\tFlora e Funga;Fungos\tFungi\tNA\n")
            // Fonte oficial: uma linha por estado; copia do app: estados juntos.
            put("distribution.txt", "coreid\tlocationID\testablishmentMeans\n1\tBR-RJ\tNativa\n1\tBR-SP\tNativa\n2\tBR-AM | BR-PA\tNativa\n")
        }
        val names = mutableListOf<TaxonRecord>()
        val distributions = mutableMapOf<String, TaxonDistribution>()
        ZipFile(file).use { zip ->
            ChecklistParser.read(zip, onTaxon = { names += it }, onVernacular = { _, _, _ -> }, onDistribution = { id, d -> distributions[id] = d })
        }
        assertEquals("Vriesea neoglutinosa", names[0].canonicalName)
        assertEquals("angio", names[0].group)
        assertEquals("fungos", names[1].group)
        // "NA" da lista vira vazio.
        assertEquals("", names[1].family)
        assertEquals(listOf("RJ", "SP"), distributions["1"]!!.states)
        assertEquals(listOf("AM", "PA"), distributions["2"]!!.states)
    }
}
