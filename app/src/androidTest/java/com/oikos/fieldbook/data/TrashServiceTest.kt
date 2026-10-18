package com.oikos.fieldbook.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oikos.fieldbook.services.TrashService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/** Apagar e restaurar tem de devolver exatamente o que havia, com os mesmos ids e arquivos. */
@RunWith(AndroidJUnit4::class)
class TrashServiceTest {

    private lateinit var db: OikosDatabase
    private lateinit var filesDir: File
    private lateinit var trash: TrashService
    private lateinit var photo: File

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, OikosDatabase::class.java).build()
        filesDir = File(context.cacheDir, "trash-test-${System.nanoTime()}").apply { mkdirs() }
        trash = TrashService(db, filesDir)
        db.projectDao().insert(ProjectEntity(id = 1, name = "Anuros"))
        db.sessionDao().insert(SessionEntity(id = 1, projectId = 1, eventId = "event-1", name = "Trilha"))
        db.occurrenceDao().insert(OccurrenceEntity(id = 7, projectId = 1, sessionId = 1, occurrenceId = "OC-7", scientificName = "Boana faber"))
        photo = File(filesDir, "Projetos/PROJ-1/EXP-1/Fotos/foto.jpg").apply { parentFile?.mkdirs(); writeText("jpeg") }
        db.mediaDao().insert(
            MediaEntity(id = 3, projectId = 1, sessionId = 1, occurrenceId = 7, mediaType = "photo", filePath = photo.path, fileName = "foto.jpg", mimeType = "image/jpeg")
        )
        Unit
    }

    @After
    fun tearDown() {
        db.close()
        filesDir.deleteRecursively()
    }

    @Test
    fun ocorrenciaVaiELevaAsMidiasEVoltaIgual() = runBlocking {
        val item = trash.trashOccurrence(7, "Boana faber")
        assertNull(db.occurrenceDao().getOccurrence(7))
        assertTrue("a linha da midia sai junto", db.mediaDao().getForOccurrence(7).isEmpty())
        assertFalse(photo.exists())
        assertEquals(1, trash.list().size)
        assertEquals(1, item.occurrences)
        assertEquals(1, item.media)

        trash.restore(trash.list().single())
        assertEquals("Boana faber", db.occurrenceDao().getOccurrence(7)?.scientificName)
        assertEquals(3L, db.mediaDao().getForOccurrence(7).single().id)
        assertEquals("jpeg", photo.readText())
        assertTrue(trash.list().isEmpty())
    }

    @Test
    fun projetoInteiroVaiEVolta() = runBlocking {
        trash.trashProject(1, "Anuros")
        assertNull(db.projectDao().getProject(1))
        assertTrue(db.occurrenceDao().getForProject(1).isEmpty())
        assertFalse(photo.exists())

        trash.restore(trash.list().single())
        assertNotNull(db.projectDao().getProject(1))
        assertNotNull(db.sessionDao().getSession(1))
        assertEquals(1, db.occurrenceDao().getForProject(1).size)
        assertTrue(photo.exists())
    }

    @Test
    fun ocorrenciaNaoVoltaSemAExpedicao() = runBlocking {
        trash.trashOccurrence(7, "Boana faber")
        trash.trashSession(1, 1, "Trilha")
        val occurrenceItem = trash.list().first { it.kind == "occurrence" }
        try {
            trash.restore(occurrenceItem)
            fail("deveria pedir para restaurar a expedicao antes")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("expedição"))
        }
        // Nada pela metade: a ocorrencia continua na lixeira e fora do banco.
        assertNull(db.occurrenceDao().getOccurrence(7))
        assertEquals(2, trash.list().size)
        trash.restore(trash.list().first { it.kind == "session" })
        trash.restore(trash.list().single())
        assertNotNull(db.occurrenceDao().getOccurrence(7))
        assertTrue(photo.exists())
    }

    @Test
    fun itensVencidosSaoEliminados() {
        trash.trashOccurrence(7, "Boana faber")
        trash.purgeOlderThan(30, now = System.currentTimeMillis() + 31L * 24 * 60 * 60 * 1000)
        assertTrue(trash.list().isEmpty())
    }

    @Test
    fun naoRestauraNaExpedicaoErradaDepoisDeTrocarOBanco() = runBlocking {
        val item = trash.trashOccurrence(7, "Boana faber")
        // Backup de outro aparelho restaurado: o id 1 agora e outra expedicao.
        db.openHelper.writableDatabase.execSQL("DELETE FROM sessions WHERE id = 1")
        db.sessionDao().insert(SessionEntity(id = 1, projectId = 1, eventId = "event-de-outro-celular", name = "Outra"))
        try {
            trash.restore(item)
            fail("prenderia a ocorrencia a expedicao errada")
        } catch (expected: IOException) {
            assertTrue(expected.message!!, expected.message!!.contains("agora é outra"))
        }
        assertNull(db.occurrenceDao().getOccurrence(7))
    }

    @Test
    fun midiaNaoVoltaSemAOcorrencia() = runBlocking {
        val mediaItem = trash.trashMedia(3, "foto.jpg")
        trash.trashOccurrence(7, "Boana faber")
        try {
            trash.restore(trash.list().first { it.id == mediaItem.id })
            fail("midia sem ocorrencia ficaria orfa")
        } catch (expected: IOException) {
            assertTrue(expected.message!!, expected.message!!.contains("ocorrência desta mídia não existe mais"))
        }
        assertTrue(db.mediaDao().getForOccurrence(7).isEmpty())
    }

    @Test
    fun duasExclusoesNoMesmoInstanteNaoDividemAPasta() = runBlocking {
        db.occurrenceDao().insert(OccurrenceEntity(id = 8, projectId = 1, sessionId = 1, occurrenceId = "OC-8", scientificName = "Hylodes asper"))
        val first = trash.trashOccurrence(7, "Boana faber")
        val second = trash.trashOccurrence(8, "Hylodes")
        assertTrue(first.directory != second.directory)
        assertEquals(2, trash.list().size)
    }
}
