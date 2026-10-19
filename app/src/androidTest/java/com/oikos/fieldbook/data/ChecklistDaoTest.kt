package com.oikos.fieldbook.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * As consultas do autocompletar rodam no SQLite do aparelho (faixa no indice +
 * LIKE): o teste usa o banco real, nao uma imitacao.
 */
@RunWith(AndroidJUnit4::class)
class ChecklistDaoTest {

    private lateinit var db: ChecklistDatabase
    private lateinit var dao: ChecklistDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, ChecklistDatabase::class.java).build()
        dao = db.checklistDao()
    }

    @After
    fun tearDown() = db.close()

    private fun name(checklist: Long, id: String, canonical: String, status: String = "A", accepted: String = "") = TaxonNameEntity(
        checklistId = checklist, taxonId = id, canonicalName = canonical, authorship = "", rank = "species", status = status,
        acceptedTaxonId = accepted, acceptedName = "", family = "Hylidae", genus = canonical.substringBefore(' '), kingdom = "Animalia",
        searchKey = canonical.lowercase()
    )

    private fun fill(): Long = runBlocking {
        val id = dao.insertChecklist(
            ChecklistEntity(sourceKey = "fauna_brasil", title = "Fauna", version = "1.57", pubDate = "2026-10-01", license = "CC BY 4.0",
                citation = "", sourceUrl = "", importedAt = 0, taxonCount = 4)
        )
        db.runInTransaction {
            dao.insertNamesNow(
                listOf(
                    name(id, "1", "Boana faber"),
                    name(id, "2", "Boana albomarginata"),
                    name(id, "3", "Hyla faber", status = "S", accepted = "1"),
                    name(id, "4", "Boana fabra-falsa", status = "S", accepted = "1")
                )
            )
            dao.insertVernacularsNow(listOf(VernacularEntity(checklistId = id, taxonId = "1", name = "sapo-martelo", language = "pt", searchKey = "sapo-martelo")))
        }
        id
    }

    @Test
    fun buscaPorPrefixoDeCadaPalavraComAceitosPrimeiro() = runBlocking {
        fill()
        val found = dao.searchScientific("boa", "boa~", "boa% fab%", 8, true, listOf("")).map { it.taxon.canonicalName }
        assertEquals(listOf("Boana faber", "Boana fabra-falsa"), found)
        assertEquals(listOf("Hyla faber"), dao.searchScientific("hyl", "hyl~", "hyl% fab%", 8, true, listOf("")).map { it.taxon.canonicalName })
    }

    @Test
    fun nomePopularLevaAoTaxon() = runBlocking {
        fill()
        val found = dao.searchVernacular("sapo", "sapo~", "sapo%", 8, true, listOf(""))
        assertEquals("Boana faber", found.single().taxon.canonicalName)
        assertEquals("sapo-martelo", found.single().matchedVernacular)
    }

    @Test
    fun sinonimoApontaParaOAceitoNaMesmaLista() = runBlocking {
        val id = fill()
        assertEquals("Boana faber", dao.getTaxon(id, "1")?.canonicalName)
        assertEquals(1, dao.countExact("boana faber"))
    }

    @Test
    fun removerListaApagaNomesENomesPopulares() = runBlocking {
        val id = fill()
        dao.deleteChecklist(id)
        assertEquals(0, dao.countChecklists())
        assertEquals(0, dao.countExact("boana faber"))
        assertNull(dao.firstVernacular(id, "1"))
    }
}
