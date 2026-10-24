package com.oikos.fieldbook.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * O Darwin Core Archive so tem valor se um IPT conseguir le-lo. Estes testes
 * checam exatamente as propriedades de que a ingestao depende.
 */
class DarwinCoreArchiveTest {

    private val coreHeaders = listOf(
        "occurrenceID", "basisOfRecord", "scientificName", "decimalLatitude", "decimalLongitude"
    )

    private val coreRows = listOf(
        mapOf(
            "occurrenceID" to "occ-001",
            "basisOfRecord" to "PreservedSpecimen",
            "scientificName" to "Hexasepalum nordestinum",
            "decimalLatitude" to "-9.12345",
            "decimalLongitude" to "-40.54321"
        )
    )

    private val metadata = ArchiveMetadata(
        datasetTitle = "Levantamento \"Caatinga\" & Cerrado",
        creatorName = "MACIEL, L. S. C.",
        organization = "Instituicao",
        abstractText = "Resumo.",
        licenseCode = "CC-BY-4.0",
        methods = "Parcelas de 100 m2",
        packageId = "oikos-fieldbook/PROJ-001",
        pubDate = "2026-09-19",
        geographicDescription = "Nordeste do Brasil",
        westBound = -40.6,
        eastBound = -40.5,
        southBound = -9.2,
        northBound = -9.1,
        beginDate = "2026-03-14",
        endDate = "2026-03-15",
        taxa = listOf("Hexasepalum nordestinum")
    )

    private fun build(
        mofRows: List<Map<String, String>> = emptyList(),
        mmRows: List<Map<String, String>> = emptyList()
    ): Map<String, String> {
        val bytes = DarwinCoreArchive.build(
            occurrenceHeaders = coreHeaders,
            occurrenceRows = coreRows,
            measurementHeaders = listOf("occurrenceID", "measurementType", "measurementValue"),
            measurementRows = mofRows,
            multimediaHeaders = listOf("occurrenceID", "type", "format", "identifier"),
            multimediaRows = mmRows,
            metadata = metadata
        )
        val saida = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entrada = zip.nextEntry ?: break
                saida[entrada.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return saida
    }

    @Test
    fun `arquivo minimo contem meta eml e occurrence`() {
        val conteudo = build()
        assertTrue(conteudo.containsKey("meta.xml"))
        assertTrue(conteudo.containsKey("eml.xml"))
        assertTrue(conteudo.containsKey("occurrence.txt"))
    }

    @Test
    fun `extensoes so entram quando tem linha`() {
        val semExtensao = build()
        assertFalse(semExtensao.containsKey("measurementorfact.txt"))
        assertFalse(semExtensao.containsKey("multimedia.txt"))
        assertFalse(semExtensao.getValue("meta.xml").contains("measurementorfact.txt"))

        val comExtensao = build(
            mofRows = listOf(mapOf("occurrenceID" to "occ-001", "measurementType" to "DAP", "measurementValue" to "12.4")),
            mmRows = listOf(mapOf("occurrenceID" to "occ-001", "type" to "StillImage", "format" to "image/jpeg", "identifier" to "midias/fotos/a.jpg"))
        )
        assertTrue(comExtensao.containsKey("measurementorfact.txt"))
        assertTrue(comExtensao.containsKey("multimedia.txt"))
    }

    @Test
    fun `nome do autor sai separado em prenome e sobrenome`() {
        val eml = build().getValue("eml.xml")
        assertTrue(eml, eml.contains("<individualName><givenName>L. S. C.</givenName><surName>MACIEL</surName></individualName>"))
        assertEquals("Luan da Silva Cortes" to "Maciel", DarwinCoreArchive.personName("Luan da Silva Cortes Maciel"))
        assertEquals("" to "Maciel", DarwinCoreArchive.personName(" Maciel "))
    }

    @Test
    fun `meta e eml sao xml bem formado`() {
        val conteudo = build(
            mofRows = listOf(mapOf("occurrenceID" to "occ-001", "measurementType" to "DAP", "measurementValue" to "12.4"))
        )
        val builder = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
        listOf("meta.xml", "eml.xml").forEach { nome ->
            builder.parse(ByteArrayInputStream(conteudo.getValue(nome).toByteArray(Charsets.UTF_8)))
        }
    }

    @Test
    fun `cada coluna do core declara a uri do termo`() {
        val meta = build().getValue("meta.xml")
        coreHeaders.forEachIndexed { indice, termo ->
            val esperado = "<field index=\"$indice\" term=\"http://rs.tdwg.org/dwc/terms/$termo\"/>"
            assertTrue("faltou declaracao de $termo", meta.contains(esperado))
        }
    }

    @Test
    fun `core usa id e extensao usa coreid`() {
        val meta = build(
            mofRows = listOf(mapOf("occurrenceID" to "occ-001", "measurementType" to "DAP", "measurementValue" to "1"))
        ).getValue("meta.xml")
        assertTrue(meta.contains("<id index=\"0\"/>"))
        assertTrue(meta.contains("<coreid index=\"0\"/>"))
    }

    @Test
    fun `termos de multimidia usam o namespace do dublin core`() {
        val meta = build(
            mmRows = listOf(mapOf("occurrenceID" to "occ-001", "type" to "StillImage", "format" to "image/jpeg", "identifier" to "a.jpg"))
        ).getValue("meta.xml")
        assertTrue(meta.contains("http://purl.org/dc/terms/type"))
        assertTrue(meta.contains("http://purl.org/dc/terms/format"))
        assertEquals("http://purl.org/dc/terms/identifier", DarwinCoreArchive.termUri("identifier"))
        assertEquals("http://rs.tdwg.org/dwc/terms/scientificName", DarwinCoreArchive.termUri("scientificName"))
    }

    @Test
    fun `occurrence txt nao leva BOM`() {
        val occ = build().getValue("occurrence.txt")
        assertFalse("TSV do archive nao pode ter BOM", occ.startsWith("\uFEFF"))
    }

    @Test
    fun `tabulacao e quebra de linha no valor nao quebram a linha do TSV`() {
        val linhas = DarwinCoreArchive.tsv(
            listOf("occurrenceID", "occurrenceRemarks"),
            listOf(mapOf("occurrenceID" to "occ-1", "occurrenceRemarks" to "com\ttab\ne quebra"))
        ).trim().split("\n")
        assertEquals(2, linhas.size)
        assertEquals(1, linhas[1].count { it == '\t' })
        assertTrue(linhas[1].contains("com tab e quebra"))
    }

    @Test
    fun `caracteres especiais do xml sao escapados no eml`() {
        val eml = build().getValue("eml.xml")
        assertTrue(eml.contains("&quot;Caatinga&quot;"))
        assertTrue(eml.contains("&amp;"))
        assertFalse(eml.contains("\"Caatinga\""))
    }

    @Test
    fun `acentos sobrevivem em utf8`() {
        // Ate a v0.2.0 este teste usava so texto sem acento e nao verificava
        // o que o nome prometia.
        val localidade = "São João do Piauí, região de várzea"
        val titulo = "Inventário florístico da Caatinga: bromélias e anuros"
        val bytes = DarwinCoreArchive.build(
            occurrenceHeaders = listOf("occurrenceID", "locality"),
            occurrenceRows = listOf(mapOf("occurrenceID" to "occ-1", "locality" to localidade)),
            measurementHeaders = emptyList(),
            measurementRows = emptyList(),
            multimediaHeaders = emptyList(),
            multimediaRows = emptyList(),
            metadata = metadata.copy(datasetTitle = titulo)
        )
        val arquivos = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                arquivos[e.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertTrue(arquivos.getValue("eml.xml").contains(titulo))
        assertTrue(arquivos.getValue("occurrence.txt").contains(localidade))
    }

    @Test
    fun `eml traz contato e criador validos sem pessoa responsavel`() {
        val eml = DarwinCoreArchive.emlXml(metadata.copy(creatorName = "", organization = ""))
        // Sem pessoa nem instituicao, o titulo entra como organizacao: o
        // esquema EML exige ao menos um dos dois em creator e contact.
        assertTrue(eml.contains("<contact>"))
        assertTrue(eml.contains("<organizationName>Levantamento &quot;Caatinga&quot; &amp; Cerrado</organizationName>"))
        assertFalse(eml.contains("<surName></surName>"))
    }

    @Test
    fun `contact vem depois de coverage e antes de methods como o esquema exige`() {
        val eml = DarwinCoreArchive.emlXml(metadata)
        val coverage = eml.indexOf("<coverage>")
        val contact = eml.indexOf("<contact>")
        val methods = eml.indexOf("<methods>")
        assertTrue(coverage in 0 until contact)
        assertTrue(contact < methods)
        assertTrue(eml.indexOf("<metadataProvider>") in eml.indexOf("</creator>") until eml.indexOf("<pubDate>"))
    }

    @Test
    fun `licenca sai no formato reconhecido pelo GBIF`() {
        assertTrue(DarwinCoreArchive.licenseParagraph("CC-BY-4.0").contains("http://creativecommons.org/licenses/by/4.0/legalcode"))
        assertTrue(DarwinCoreArchive.licenseParagraph("CC0-1.0").contains("http://creativecommons.org/publicdomain/zero/1.0/legalcode"))
        assertTrue(DarwinCoreArchive.licenseParagraph("CC-BY-NC-4.0").contains("http://creativecommons.org/licenses/by-nc/4.0/legalcode"))
        assertTrue(DarwinCoreArchive.licenseParagraph("").contains("não definida"))
    }
}
