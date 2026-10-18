package com.oikos.fieldbook.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * O backup e o ultimo recurso quando o aparelho se perde: se ele falhar, a
 * falha so aparece no dia em que for preciso restaurar. Estes testes fazem a
 * ida e volta completa e conferem as protecoes.
 */
class BackupArchiveTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val manifest = BackupManifest(
        appVersion = "0.2.0 (10)",
        databaseVersion = 5,
        createdAt = "2026-10-17T15:30:00-03:00",
        filesDir = "/data/user/0/com.oikos.fieldbook/files",
        projects = 2,
        occurrences = 31,
        mediaFiles = 2,
        mediaBytes = 9
    )

    private fun sampleSource(): Pair<File, File> {
        val database = temp.newFile("oikos_fieldbook.db").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val media = temp.newFolder("Projetos")
        File(media, "PROJ-1/EXP-2/Audios").mkdirs()
        File(media, "PROJ-1/EXP-2/Audios/canto ação.wav").writeBytes(byteArrayOf(9, 8, 7, 6))
        File(media, "PROJ-1/EXP-2/Fotos").mkdirs()
        File(media, "PROJ-1/EXP-2/Fotos/bromélia.jpg").writeBytes(byteArrayOf(5, 5, 5, 5, 5))
        return database to media
    }

    @Test
    fun `ida e volta preserva banco midias e manifesto`() {
        val (database, media) = sampleSource()
        val bytes = ByteArrayOutputStream().also { BackupArchive.write(it, manifest, database, media) }.toByteArray()

        val restoredDb = File(temp.root, "restaurado/oikos_fieldbook.db")
        val restoredMedia = File(temp.root, "restaurado/Projetos")
        val lido = BackupArchive.extract(ByteArrayInputStream(bytes), restoredDb, restoredMedia)

        assertEquals(manifest, lido)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), restoredDb.readBytes())
        assertArrayEquals(byteArrayOf(9, 8, 7, 6), File(restoredMedia, "PROJ-1/EXP-2/Audios/canto ação.wav").readBytes())
        assertArrayEquals(byteArrayOf(5, 5, 5, 5, 5), File(restoredMedia, "PROJ-1/EXP-2/Fotos/bromélia.jpg").readBytes())
    }

    @Test
    fun `manifesto e lido sem extrair o arquivo`() {
        val (database, media) = sampleSource()
        val bytes = ByteArrayOutputStream().also { BackupArchive.write(it, manifest, database, media) }.toByteArray()
        assertEquals(manifest, BackupArchive.readManifest(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `progresso chega ao total`() {
        val (database, media) = sampleSource()
        var ultimo = 0L to 0L
        ByteArrayOutputStream().use { BackupArchive.write(it, manifest, database, media) { done, total -> ultimo = done to total } }
        assertEquals(13L, ultimo.second)
        assertEquals(ultimo.second, ultimo.first)
    }

    @Test(expected = IOException::class)
    fun `entrada que tenta sair da pasta e recusada`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(BackupArchive.MANIFEST)); zip.write(BackupArchive.manifestJson(manifest).toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry(BackupArchive.DATABASE)); zip.write(byteArrayOf(1)); zip.closeEntry()
                zip.putNextEntry(ZipEntry(BackupArchive.MEDIA_PREFIX + "../../fora.txt")); zip.write(byteArrayOf(1)); zip.closeEntry()
            }
        }.toByteArray()
        BackupArchive.extract(ByteArrayInputStream(bytes), File(temp.root, "x/db"), File(temp.root, "x/Projetos"))
    }

    @Test(expected = IOException::class)
    fun `arquivo sem banco nao e aceito`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(BackupArchive.MANIFEST)); zip.write(BackupArchive.manifestJson(manifest).toByteArray()); zip.closeEntry()
            }
        }.toByteArray()
        BackupArchive.extract(ByteArrayInputStream(bytes), File(temp.root, "y/db"), File(temp.root, "y/Projetos"))
    }

    @Test
    fun `zip qualquer nao e confundido com backup`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip -> zip.putNextEntry(ZipEntry("foto.jpg")); zip.write(byteArrayOf(1)); zip.closeEntry() }
        }.toByteArray()
        assertEquals(null, BackupArchive.readManifest(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `caminho da midia muda para o filesDir do aparelho novo`() {
        val antigo = "/data/user/0/com.oikos.fieldbook/files"
        val novo = "/data/user/10/com.oikos.fieldbook/files"
        assertEquals(
            "$novo/Projetos/PROJ-1/EXP-2/Audios/a.wav",
            BackupArchive.rewritePath("$antigo/Projetos/PROJ-1/EXP-2/Audios/a.wav", antigo, novo)
        )
        // Prefixo parecido mas diferente nao e reescrito.
        assertEquals("/data/user/0/com.oikos.fieldbook/files2/x", BackupArchive.rewritePath("/data/user/0/com.oikos.fieldbook/files2/x", antigo, novo))
        // Midia fora da pasta do app (antiga, importada) fica como estava.
        assertEquals("/sdcard/x.jpg", BackupArchive.rewritePath("/sdcard/x.jpg", antigo, novo))
    }

    @Test
    fun `manifesto invalido vira nulo em vez de erro`() {
        assertEquals(null, BackupArchive.parseManifest("{}"))
        assertEquals(null, BackupArchive.parseManifest("isto nao e json"))
        val ok = BackupArchive.parseManifest(BackupArchive.manifestJson(manifest))
        assertNotNull(ok)
        assertTrue(BackupArchive.manifestJson(manifest).contains("\"app\":\"Oikos FieldBook\""))
        assertFalse(BackupArchive.manifestJson(manifest).isBlank())
    }
}
