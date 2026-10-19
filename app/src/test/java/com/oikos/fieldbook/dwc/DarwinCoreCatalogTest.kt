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
    fun `nada fora do Darwin Core vai para o core do archive`() {
        // substrate nao existe no Darwin Core; measurement* sao da classe
        // MeasurementOrFact. Antes os dois iam para o core com URI dwc invalida.
        assertTrue("substrate" in DarwinCoreCatalog.nonCoreTerms)
        assertTrue("measurementType" in DarwinCoreCatalog.nonCoreTerms)
        assertTrue(DarwinCoreCatalog.customTerms.all { it in DarwinCoreCatalog.nonCoreTerms })
        assertFalse("scientificName" in DarwinCoreCatalog.nonCoreTerms)
    }

    @Test
    fun `medidas numericas declaram unidade`() {
        // DAP e CAP saiam no MeasurementOrFact sem unidade: 12.4 de que?
        val semUnidade = DarwinCoreCatalog.advancedOccurrenceFields
            .filter { it.type == "decimal" || it.type == "integer" }
            .filter { it.unit.isBlank() }
            .map { it.term }
        assertTrue("medidas sem unidade: $semUnidade", semUnidade.isEmpty())
    }

    @Test
    fun `nome cientifico nao passa por autocorrecao`() {
        assertTrue("scientificName" in DarwinCoreCatalog.noAutocorrectTerms)
        assertTrue(DarwinCoreCatalog.noAutocorrectTerms.all { it in DarwinCoreCatalog.byTerm })
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

    @Test
    fun `todo campo do catalogo tem rotulo em espanhol`() {
        val termos = DarwinCoreCatalog.allFields.map { it.term }.toSet()
        val faltando = termos - spanishFieldLabels.keys
        assertTrue("sem espanhol: $faltando", faltando.isEmpty())
        val sobrando = spanishFieldLabels.keys - termos
        assertTrue("termo inexistente: $sobrando", sobrando.isEmpty())
        assertTrue(spanishFieldLabels.values.none { it.isBlank() })
    }
}
