package com.oikos.fieldbook.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dado de campo nao se repete: uma migracao que perde coluna ou linha destroi
 * trabalho irrecuperavel. Este teste cria um banco na versao 4 (a da v0.2.0),
 * grava um projeto, uma expedicao e uma ocorrencia, migra para a 5 e confere
 * que tudo continua la e que o Room aceita o esquema resultante.
 *
 * Os esquemas 1 a 3 nunca foram exportados (exportSchema so foi ligado na v4),
 * entao as migracoes antigas nao podem ser testadas por este caminho.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OikosDatabase::class.java
    )

    @Test
    fun migracao4para5PreservaDadosDeCampo() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(
                "INSERT INTO projects (id, name, description, institution, principalInvestigator, language, defaultDatum, " +
                    "coordinateFormat, photoQuality, audioFormat, enableFauna, enableBotany, enableFungi, createdAt, updatedAt) " +
                    "VALUES (1, 'Anuros e bromélias', '', 'JBRJ', 'Maciel', 'pt', 'SIRGAS 2000', 'DD', 'medium', 'wav', 1, 1, 0, 0, 0)"
            )
            execSQL(
                "INSERT INTO sessions (id, projectId, eventId, name, eventDate, startTime, endTime, locality, municipality, " +
                    "stateProvince, country, habitat, environmentType, samplingProtocol, samplingEffort, parentEventId, " +
                    "sampleSizeValue, sampleSizeUnit, verbatimEventDate, recordedBy, fieldTeam, gpsTrackEnabled, latitude, " +
                    "longitude, altitude, coordinateUncertaintyInMeters, geodeticDatum, coordinateCapturedAt, notes, createdAt, updatedAt) " +
                    "VALUES (1, 1, 'event-1', 'Trilha do Arboreto', '2026-03-14', '08:00', '', '', '', '', 'Brasil', '', '', '', '', '', " +
                    "100.0, 'squareMetre', '', '', '', 0, -22.97, -43.23, 40.0, 5.0, 'SIRGAS 2000', NULL, '', 0, 0)"
            )
            execSQL(
                "INSERT INTO occurrences (id, projectId, sessionId, occurrenceId, scientificName, vernacularName, basisOfRecord, " +
                    "occurrenceType, formType, recordNumber, occurrenceStatus, individualCount, sex, lifeStage, behavior, " +
                    "reproductiveCondition, recordedBy, identifiedBy, dateIdentified, latitude, longitude, altitude, " +
                    "coordinateUncertaintyInMeters, geodeticDatum, occurrenceRemarks, fieldNotes, dynamicFieldsJson, createdAt, updatedAt) " +
                    "VALUES (1, 1, 1, 'TB-20260314-0001-ABCDEF12', 'Boana faber', '', 'HumanObservation', '', 'Fauna', '', 'present', " +
                    "2, '', '', '', '', '', '', '', -22.97, -43.23, NULL, 5.0, 'SIRGAS 2000', '', '', '{\"dbh\":\"12.5\"}', 0, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, *OikosDatabase.ALL_MIGRATIONS)

        db.query("SELECT name, license, mediaBaseUrl, csvStyle FROM projects WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Anuros e bromélias", cursor.getString(0))
            assertEquals("", cursor.getString(1))
            assertEquals("", cursor.getString(2))
            assertEquals("br", cursor.getString(3))
        }
        db.query("SELECT sampleSizeValue, sampleSizeUnit FROM sessions WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(100.0, cursor.getDouble(0), 0.0)
            assertEquals("squareMetre", cursor.getString(1))
        }
        db.query("SELECT scientificName, dynamicFieldsJson FROM occurrences WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Boana faber", cursor.getString(0))
            assertEquals("{\"dbh\":\"12.5\"}", cursor.getString(1))
        }
    }

    @Test
    fun migracao5para6AcrescentaMetadadosDeAudioSemPerderMidia() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                "INSERT INTO projects (id, name, description, institution, principalInvestigator, language, defaultDatum, " +
                    "coordinateFormat, photoQuality, audioFormat, enableFauna, enableBotany, enableFungi, license, mediaBaseUrl, " +
                    "csvStyle, createdAt, updatedAt) VALUES (1, 'P', '', '', '', 'pt', 'SIRGAS 2000', 'DD', 'medium', 'wav', 1, 0, 0, " +
                    "'CC-BY-4.0', '', 'br', 0, 0)"
            )
            execSQL(
                "INSERT INTO sessions (id, projectId, eventId, name, eventDate, startTime, endTime, locality, municipality, " +
                    "stateProvince, country, habitat, environmentType, samplingProtocol, samplingEffort, parentEventId, " +
                    "sampleSizeValue, sampleSizeUnit, verbatimEventDate, recordedBy, fieldTeam, gpsTrackEnabled, latitude, " +
                    "longitude, altitude, coordinateUncertaintyInMeters, geodeticDatum, coordinateCapturedAt, notes, createdAt, updatedAt) " +
                    "VALUES (1, 1, 'event-1', 'E', '', '', '', '', '', '', '', '', '', '', '', '', NULL, '', '', '', '', 0, NULL, NULL, " +
                    "NULL, NULL, 'SIRGAS 2000', NULL, '', 0, 0)"
            )
            execSQL(
                "INSERT INTO media (id, projectId, sessionId, occurrenceId, mediaType, filePath, fileName, mimeType, createdAt, " +
                    "latitude, longitude, coordinateUncertaintyInMeters, caption, mediaSubject, bodyPartOrPlantPart, behaviorContext, notes) " +
                    "VALUES (1, 1, 1, NULL, 'audio', '/x/a.wav', 'a.wav', 'audio/wav', 0, NULL, NULL, NULL, 'canto', '', '', '', '')"
            )
            // Midia de uma ocorrencia ja apagada (id 99): linha fantasma da v0.2.0.
            execSQL(
                "INSERT INTO media (id, projectId, sessionId, occurrenceId, mediaType, filePath, fileName, mimeType, createdAt, " +
                    "latitude, longitude, coordinateUncertaintyInMeters, caption, mediaSubject, bodyPartOrPlantPart, behaviorContext, notes) " +
                    "VALUES (2, 1, 1, 99, 'photo', '/x/b.jpg', 'b.jpg', 'image/jpeg', 0, NULL, NULL, NULL, '', '', '', '', '')"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, *OikosDatabase.ALL_MIGRATIONS)
        db.query("SELECT COUNT(*) FROM media").use { cursor ->
            cursor.moveToFirst()
            assertEquals("a midia orfa sai, a da expedicao fica", 1, cursor.getInt(0))
        }
        db.query("SELECT fileName, caption, durationMillis, audioSource, airTemperature FROM media WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals("a.wav", cursor.getString(0))
            assertEquals("canto", cursor.getString(1))
            assertEquals(true, cursor.isNull(2))
            assertEquals("", cursor.getString(3))
            assertEquals(true, cursor.isNull(4))
        }
    }

    @Test
    fun migracao6para7MarcaMidiasAntigasComDataConhecida() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                "INSERT INTO projects (id, name, description, institution, principalInvestigator, language, defaultDatum, " +
                    "coordinateFormat, photoQuality, audioFormat, enableFauna, enableBotany, enableFungi, license, mediaBaseUrl, " +
                    "csvStyle, createdAt, updatedAt) VALUES (1, 'P', '', '', '', 'pt', 'SIRGAS 2000', 'DD', 'medium', 'wav', 1, 0, 0, " +
                    "'', '', 'br', 0, 0)"
            )
            execSQL(
                "INSERT INTO sessions (id, projectId, eventId, name, eventDate, startTime, endTime, locality, municipality, " +
                    "stateProvince, country, habitat, environmentType, samplingProtocol, samplingEffort, parentEventId, " +
                    "sampleSizeValue, sampleSizeUnit, verbatimEventDate, recordedBy, fieldTeam, gpsTrackEnabled, latitude, " +
                    "longitude, altitude, coordinateUncertaintyInMeters, geodeticDatum, coordinateCapturedAt, notes, createdAt, updatedAt) " +
                    "VALUES (1, 1, 'e1', 'S', '', '', '', '', '', '', '', '', '', '', '', '', NULL, '', '', '', '', 0, NULL, NULL, " +
                    "NULL, NULL, 'SIRGAS 2000', NULL, '', 0, 0)"
            )
            execSQL(
                "INSERT INTO media (id, projectId, sessionId, occurrenceId, mediaType, filePath, fileName, mimeType, createdAt, latitude, longitude, coordinateUncertaintyInMeters, caption, mediaSubject, bodyPartOrPlantPart, behaviorContext, notes, durationMillis, sampleRateHz, bitsPerSample, channels, audioSource, captureDevice, microphone, airTemperature, relativeHumidity, distanceToSourceMeters) " +
                    "VALUES (1, 1, 1, NULL, 'audio', '/x/a.wav', 'a.wav', 'audio/wav', 5, NULL, NULL, NULL, '', '', '', '', '', 1000, 44100, 16, 1, 'MIC', '', '', NULL, NULL, NULL)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, *OikosDatabase.ALL_MIGRATIONS)
        db.query("SELECT captureTimeKnown, fileName FROM media WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals("gravacoes antigas tem data conhecida", 1, cursor.getInt(0))
            assertEquals("a.wav", cursor.getString(1))
        }
    }

    @Test
    fun migracaoRepetidaNaoQuebra() {
        // As migracoes usam addColumnIfMissing: rodar de novo nao pode falhar.
        helper.createDatabase(TEST_DB, 4).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, OikosDatabase.VERSION, true, *OikosDatabase.ALL_MIGRATIONS)
        OikosDatabase.ALL_MIGRATIONS.forEach { it.migrate(db) }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
