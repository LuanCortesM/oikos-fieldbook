package com.oikos.fieldbook.dwc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O catalogo e o contrato do aplicativo com o padrao Darwin Core e com a
 * promessa de modularidade. Estes testes travam as duas coisas.
 */
class DarwinCoreCatalogTest {

    @Test
    fun `nao ha termo duplicado`() {
        val termos = DarwinCoreCatalog.allFields.map { it.term }
        val duplicados = termos.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        assertTrue("termos duplicados: $duplicados", duplicados.isEmpty())
    }

    @Test
    fun `todo termo do core tem campo correspondente`() {
        // Ate a v0.1.7, taxonRank, preparations, disposition e
        // associatedSequences viravam coluna em toda exportacao e nunca tinham
        // onde ser preenchidos.
        val comCampo = DarwinCoreCatalog.allFields.map { it.term }.toSet()
        val gerados = setOf("occurrenceID", "eventID", "eventDate", "eventTime", "basisOfRecord")
        val semCampo = DarwinCoreCatalog.coreOccurrenceTerms
            .filterNot { it in comCampo }
            .filterNot { it in gerados }
        assertTrue("colunas sempre vazias: $semCampo", semCampo.isEmpty())
    }

    @Test
    fun `campos novos entram desligados para nao mudar projetos existentes`() {
        val gruposNovos = setOf("Coleção", "Ecologia", "Georreferenciamento", "Autoria")
        val ligadosPorPadrao = DarwinCoreCatalog.allFields
            .filter { it.group in gruposNovos && it.enabled }
            .map { it.term }
        assertTrue("deveriam comecar desligados: $ligadosPorPadrao", ligadosPorPadrao.isEmpty())
    }

    @Test
    fun `byTerm indexa todos os campos`() {
        assertEquals(DarwinCoreCatalog.allFields.size, DarwinCoreCatalog.byTerm.size)
    }

    @Test
    fun `termos proprios do oikos ficam fora do core do archive`() {
        // Sem URI na TDWG eles nao podem ser coluna do core; vao como
        // MeasurementOrFact.
        assertTrue(DarwinCoreCatalog.customTerms.contains("dbh"))
        assertTrue(DarwinCoreCatalog.customTerms.contains("vocalizationType"))
        assertFalse(DarwinCoreCatalog.customTerms.contains("scientificName"))
        assertFalse(DarwinCoreCatalog.customTerms.contains("catalogNumber"))
    }

    @Test
    fun `vocabularios controlados nao tem valor repetido nem vazio`() {
        DarwinCoreCatalog.allFields.filter { it.vocabulary.isNotEmpty() }.forEach { campo ->
            assertEquals(
                "vocabulario de ${campo.term} tem repetidos",
                campo.vocabulary.size,
                campo.vocabulary.distinct().size
            )
            assertTrue(
                "vocabulario de ${campo.term} tem valor em branco",
                campo.vocabulary.none { it.isBlank() }
            )
        }
    }

    @Test
    fun `basisOfRecord oferece espécime tombado e amostra`() {
        // Sem isso nao ha como distinguir observacao de material coletado.
        assertTrue(DwcVocabulary.basisOfRecord.contains("PreservedSpecimen"))
        assertTrue(DwcVocabulary.basisOfRecord.contains("MaterialSample"))
        assertTrue(DwcVocabulary.basisOfRecord.contains("HumanObservation"))
    }

    @Test
    fun `campos de colecao necessarios para tombo existem`() {
        val termos = DarwinCoreCatalog.allFields.map { it.term }.toSet()
        listOf(
            "catalogNumber", "institutionCode", "collectionCode",
            "preparations", "disposition", "scientificNameAuthorship",
            "infraspecificEpithet", "typeStatus", "organismID"
        ).forEach { termo ->
            assertTrue("faltando $termo", termos.contains(termo))
        }
    }
}
