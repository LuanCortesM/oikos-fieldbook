package com.oikos.fieldbook.checklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Icone de cada registro no mapa: classe, ordem e familia como vem do Catalogo da Fauna. */
class MapIconsTest {

    @Test
    fun anuroPelaOrdem() {
        assertEquals(MapIconGroup.FROG, MapIcons.animalGroup(TaxonGroupInfo("Animalia", "Amphibia", "Anura", "Hylidae")))
    }

    @Test
    fun anfibioQueNaoEAnuroNaoViraSapo() {
        // Cobra-cega (Gymnophiona) nao recebe o icone de anuro.
        assertNull(MapIcons.animalGroup(TaxonGroupInfo("Animalia", "Amphibia", "Gymnophiona", "Siphonopidae")))
    }

    @Test
    fun serpentePelaFamiliaELagartoComoReptil() {
        assertEquals(MapIconGroup.SNAKE, MapIcons.animalGroup(TaxonGroupInfo("Animalia", "Reptilia", "Squamata", "Dipsadidae")))
        assertEquals(MapIconGroup.SNAKE, MapIcons.animalGroup(TaxonGroupInfo("Animalia", "Reptilia", "Squamata", "Viperidae")))
        assertEquals(MapIconGroup.REPTILE, MapIcons.animalGroup(TaxonGroupInfo("Animalia", "Reptilia", "Squamata", "Teiidae")))
        // Anfisbena e Squamata, mas nao serpente.
        assertEquals(MapIconGroup.REPTILE, MapIcons.animalGroup(TaxonGroupInfo("Animalia", "Reptilia", "Squamata", "Amphisbaenidae")))
        assertEquals(MapIconGroup.REPTILE, MapIcons.animalGroup(TaxonGroupInfo("Animalia", "Reptilia", "Testudines", "Chelidae")))
    }

    @Test
    fun demaisClasses() {
        assertEquals(MapIconGroup.BIRD, MapIcons.animalGroup(TaxonGroupInfo(taxonClass = "Aves")))
        assertEquals(MapIconGroup.MAMMAL, MapIcons.animalGroup(TaxonGroupInfo(taxonClass = "Mammalia")))
        assertEquals(MapIconGroup.INSECT, MapIcons.animalGroup(TaxonGroupInfo(taxonClass = "Insecta")))
        assertEquals(MapIconGroup.ARACHNID, MapIcons.animalGroup(TaxonGroupInfo(taxonClass = "Arachnida")))
        assertEquals(MapIconGroup.FISH, MapIcons.animalGroup(TaxonGroupInfo(taxonClass = "Actinopterygii")))
        assertNull(MapIcons.animalGroup(TaxonGroupInfo(taxonClass = "Gastropoda")))
    }

    @Test
    fun campoGrupoAnimalComoPista() {
        assertEquals(MapIconGroup.FROG, MapIcons.groupFromAnimalGroupText("Anfíbios"))
        assertEquals(MapIconGroup.FROG, MapIcons.groupFromAnimalGroupText("rã"))
        assertEquals(MapIconGroup.SNAKE, MapIcons.groupFromAnimalGroupText("Serpentes"))
        assertEquals(MapIconGroup.BIRD, MapIcons.groupFromAnimalGroupText("Aves"))
        assertEquals(MapIconGroup.MAMMAL, MapIcons.groupFromAnimalGroupText("Mamíferos"))
        assertNull(MapIcons.groupFromAnimalGroupText(""))
        // "Grave" nao e "ave"; "ras" so como palavra.
        assertNull(MapIcons.groupFromAnimalGroupText("grave"))
    }

    @Test
    fun bromeliaPorPalavra() {
        assertTrue(MapIcons.mentionsBromeliad(listOf("Axila de bromélia")))
        assertTrue(MapIcons.mentionsBromeliad(listOf("em Bromeliaceae epífita")))
        assertTrue(MapIcons.mentionsBromeliad(listOf("bromelia sem acento")))
        assertFalse(MapIcons.mentionsBromeliad(listOf("Borda de mata", "Sob rocha")))
    }

    @Test
    fun anuroPorPalavraInteira() {
        assertTrue(MapIcons.mentionsAnuran(listOf("Girinos no tanque central")))
        assertTrue(MapIcons.mentionsAnuran(listOf("uma perereca na roseta")))
        assertTrue(MapIcons.mentionsAnuran(listOf("rã escondida")))
        // "rã" e "sapo" so como palavra inteira.
        assertFalse(MapIcons.mentionsAnuran(listOf("Touceira com 14 rosetas sobre rocha.", "sapotácea", "maranhão")))
    }

    @Test
    fun nomesCitadosNoTexto() {
        val names = MapIcons.nameCandidates(listOf("Com Scinax cf. fuscovarius e Vriesea itatiaiae na roseta"))
        assertTrue("Scinax fuscovarius" in names)
        assertTrue("Vriesea itatiaiae" in names)
        assertTrue("Scinax" in names)
        assertTrue(names.indexOf("Vriesea itatiaiae") < names.indexOf("Scinax"))
    }
}
