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
    fun migracaoRepetidaNaoQuebra() {
        // As migracoes usam addColumnIfMissing: rodar de novo nao pode falhar.
        helper.createDatabase(TEST_DB, 4).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, *OikosDatabase.ALL_MIGRATIONS)
        OikosDatabase.ALL_MIGRATIONS.last().migrate(db)
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
