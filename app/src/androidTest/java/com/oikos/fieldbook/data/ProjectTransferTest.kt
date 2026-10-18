package com.oikos.fieldbook.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oikos.fieldbook.services.ProjectTransfer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/** Dois "celulares": cada um com seu banco e sua pasta de arquivos. */
@RunWith(AndroidJUnit4::class)
class ProjectTransferTest {

    private lateinit var phoneA: OikosDatabase
    private lateinit var phoneB: OikosDatabase
    private lateinit var filesA: File
    private lateinit var filesB: File

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        phoneA = Room.inMemoryDatabaseBuilder(context, OikosDatabase::class.java).build()
        phoneB = Room.inMemoryDatabaseBuilder(context, OikosDatabase::class.java).build()
        filesA = File(context.cacheDir, "phoneA-${System.nanoTime()}").apply { mkdirs() }
        filesB = File(context.cacheDir, "phoneB-${System.nanoTime()}").apply { mkdirs() }
        // B ja tem um projeto proprio: os ids do pacote nao podem colidir.
        phoneB.projectDao().insert(ProjectEntity(id = 1, name = "Projeto do coordenador"))
        phoneB.sessionDao().insert(SessionEntity(id = 1, projectId = 1, eventId = "event-coordenador", name = "Dia 1"))

        phoneA.projectDao().insert(ProjectEntity(id = 1, name = "Anuros"))
        phoneA.sessionDao().insert(SessionEntity(id = 1, projectId = 1, eventId = "event-equipe-a", name = "Trilha"))
        phoneA.occurrenceDao().insert(OccurrenceEntity(id = 1, projectId = 1, sessionId = 1, occurrenceId = "OC-A-1", scientificName = "Boana faber"))
        phoneA.occurrenceDao().insert(OccurrenceEntity(id = 2, projectId = 1, sessionId = 1, occurrenceId = "OC-A-2", scientificName = "Hylodes asper"))
        val audio = File(filesA, "Projetos/PROJ-1/EXP-1/Audios/canto.wav").apply { parentFile?.mkdirs(); writeText("RIFF") }
        phoneA.mediaDao().insert(MediaEntity(id = 1, projectId = 1, sessionId = 1, occurrenceId = 2, mediaType = "audio", filePath = audio.path, fileName = "canto.wav", mimeType = "audio/wav"))
        val trackId = phoneA.gpsTrackDao().insertTrack(GpsTrackEntity(projectId = 1, sessionId = 1, eventId = "event-equipe-a", startTime = 1000, active = true))
        phoneA.gpsTrackDao().insertPoint(GpsTrackPointEntity(trackId = trackId, latitude = -22.9, longitude = -43.2, timestamp = 1000))
        Unit
    }

    @After
    fun tearDown() {
        phoneA.close()
        phoneB.close()
        filesA.deleteRecursively()
        filesB.deleteRecursively()
    }

    private fun packageFromA(): ByteArray =
        ByteArrayOutputStream().also { ProjectTransfer(phoneA, filesA).export(1, null, it, "teste") }.toByteArray()

    @Test
    fun projetoChegaInteiroComNovosIdsEArquivos() = runBlocking {
        val bytes = packageFromA()
        val manifest = ProjectTransfer(phoneB, filesB).readManifest(ByteArrayInputStream(bytes))
        assertEquals("Anuros", manifest.projectName)
        assertEquals(2, manifest.occurrences)

        val summary = ProjectTransfer(phoneB, filesB).import(ByteArrayInputStream(bytes), null)
        assertEquals(2, summary.occurrencesAdded)
        assertEquals(1, summary.mediaAdded)
        assertEquals(1, summary.tracksAdded)
        assertTrue("projeto novo, sem colidir com o id 1 de B", summary.projectId != 1L)
        val imported = phoneB.occurrenceDao().getForProject(summary.projectId)
        assertEquals(setOf("OC-A-1", "OC-A-2"), imported.map { it.occurrenceId }.toSet())
        val media = phoneB.mediaDao().getForProject(summary.projectId).single()
        assertEquals(imported.first { it.occurrenceId == "OC-A-2" }.id, media.occurrenceId)
        assertTrue(media.filePath.startsWith(filesB.path))
        assertEquals("RIFF", File(media.filePath).readText())
        val track = phoneB.gpsTrackDao().getForProject(summary.projectId).single()
        assertEquals("trilha chega encerrada", false, track.active)
        assertEquals(1, phoneB.gpsTrackDao().getPoints(track.id).size)
    }

    @Test
    fun reimportarNaoDuplicaETrazSoANovidade() = runBlocking {
        val first = ProjectTransfer(phoneB, filesB).import(ByteArrayInputStream(packageFromA()), null)
        // Como projeto novo de novo: recusado, com a dica de juntar.
        try {
            ProjectTransfer(phoneB, filesB).import(ByteArrayInputStream(packageFromA()), null)
            fail("deveria recusar o pacote ja importado")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("já foi importado"))
        }
        // A equipe registrou mais uma especie e mandou o pacote atualizado.
        phoneA.occurrenceDao().insert(OccurrenceEntity(id = 3, projectId = 1, sessionId = 1, occurrenceId = "OC-A-3", scientificName = "Scinax sp."))
        val second = ProjectTransfer(phoneB, filesB).import(ByteArrayInputStream(packageFromA()), first.projectId)
        assertEquals(1, second.occurrencesAdded)
        assertEquals(2, second.occurrencesSkipped)
        assertEquals(1, second.sessionsMerged)
        assertEquals(0, second.mediaAdded)
        assertEquals(0, second.tracksAdded)
        assertEquals(3, phoneB.occurrenceDao().getForProject(first.projectId).size)
        assertEquals(1, phoneB.mediaDao().getForProject(first.projectId).size)
    }

    @Test
    fun juntarAoProjetoDoCoordenador() = runBlocking {
        val summary = ProjectTransfer(phoneB, filesB).import(ByteArrayInputStream(packageFromA()), 1)
        assertEquals(1L, summary.projectId)
        assertEquals(2, phoneB.sessionDao().getForProject(1).size)
        assertEquals(2, phoneB.occurrenceDao().getForProject(1).size)
    }

    @Test
    fun expedicaoQueJaEstaEmOutroProjetoRecebeAsNovidades() = runBlocking {
        // Primeira importacao como projeto novo; depois o coordenador junta um
        // pacote atualizado ao PROPRIO projeto (id 1). A expedicao ja existe
        // no projeto importado: antes as ocorrencias novas dela sumiam.
        val first = ProjectTransfer(phoneB, filesB).import(ByteArrayInputStream(packageFromA()), null)
        phoneA.occurrenceDao().insert(OccurrenceEntity(id = 3, projectId = 1, sessionId = 1, occurrenceId = "OC-A-3", scientificName = "Scinax sp."))
        val second = ProjectTransfer(phoneB, filesB).import(ByteArrayInputStream(packageFromA()), 1)
        assertEquals(1, second.occurrencesAdded)
        assertEquals(1, second.sessionsInOtherProject)
        assertEquals(3, phoneB.occurrenceDao().getForProject(first.projectId).size)
    }

    @Test
    fun midiaNovaDeOcorrenciaJaImportadaEntra() = runBlocking {
        val first = ProjectTransfer(phoneB, filesB).import(ByteArrayInputStream(packageFromA()), null)
        // Foto tirada depois para a ocorrencia OC-A-1, que ja estava no coordenador.
        val photo = File(filesA, "Projetos/PROJ-1/EXP-1/Fotos/girino.jpg").apply { parentFile?.mkdirs(); writeText("JPEG") }
        phoneA.mediaDao().insert(MediaEntity(id = 2, projectId = 1, sessionId = 1, occurrenceId = 1, mediaType = "photo", filePath = photo.path, fileName = "girino.jpg", mimeType = "image/jpeg", createdAt = 5000))
        val second = ProjectTransfer(phoneB, filesB).import(ByteArrayInputStream(packageFromA()), first.projectId)
        assertEquals(1, second.mediaAdded)
        val local = phoneB.occurrenceDao().getForProject(first.projectId).first { it.occurrenceId == "OC-A-1" }
        val media = phoneB.mediaDao().getForProject(first.projectId).single { it.fileName == "girino.jpg" }
        assertEquals(local.id, media.occurrenceId)
        assertEquals("JPEG", File(media.filePath).readText())
    }

    @Test
    fun trilhaJaImportadaRecebeOsPontosQueFaltavam() = runBlocking {
        val first = ProjectTransfer(phoneB, filesB).import(ByteArrayInputStream(packageFromA()), null)
        val trackA = phoneA.gpsTrackDao().getForProject(1).single()
        phoneA.gpsTrackDao().insertPoint(GpsTrackPointEntity(trackId = trackA.id, latitude = -22.91, longitude = -43.21, timestamp = 61_000))
        val second = ProjectTransfer(phoneB, filesB).import(ByteArrayInputStream(packageFromA()), first.projectId)
        assertEquals(1, second.tracksExtended)
        assertEquals(1, second.trackPointsAdded)
        val trackB = phoneB.gpsTrackDao().getForProject(first.projectId).single()
        assertEquals(2, phoneB.gpsTrackDao().getPoints(trackB.id).size)
        assertEquals(61_000L, trackB.endTime)
    }

    @Test
    fun nomeDeArquivoComPastasNaoEscreveForaDoProjeto() = runBlocking {
        // Pacote adulterado: fileName tentando subir de pasta.
        phoneA.mediaDao().insert(
            MediaEntity(
                id = 3, projectId = 1, sessionId = 1, occurrenceId = 1, mediaType = "photo",
                filePath = File(filesA, "Projetos/PROJ-1/EXP-1/Fotos/x.jpg").apply { parentFile?.mkdirs(); writeText("X") }.path,
                fileName = "../../../../oikos_fieldbook.db", mimeType = "image/jpeg"
            )
        )
        val summary = ProjectTransfer(phoneB, filesB).import(ByteArrayInputStream(packageFromA()), null)
        val media = phoneB.mediaDao().getForProject(summary.projectId).first { it.fileName.contains("oikos_fieldbook") }
        val projects = File(filesB, "Projetos").canonicalPath
        assertTrue(media.filePath, File(media.filePath).canonicalPath.startsWith(projects + File.separator))
        assertTrue(!File(filesB, "oikos_fieldbook.db").exists())
    }

    @Test
    fun projetoApagadoAntesDeJuntarDaMensagemClara() = runBlocking {
        try {
            ProjectTransfer(phoneB, filesB).import(ByteArrayInputStream(packageFromA()), 999)
            fail()
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("não existe mais"))
        }
        // Nada foi gravado.
        assertEquals(1, phoneB.sessionDao().getForProject(1).size)
        assertEquals(0, phoneB.occurrenceDao().getForProject(999).size)
    }

    @Test
    fun backupNaoEPacote() {
        val notPackage = ByteArrayOutputStream().also { out ->
            java.util.zip.ZipOutputStream(out).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
                zip.write("{}".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        try {
            ProjectTransfer(phoneB, filesB).readManifest(ByteArrayInputStream(notPackage))
            fail()
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("Restaurar"))
        }
    }
}
