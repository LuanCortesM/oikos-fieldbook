package com.oikos.fieldbook.data

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProjectEntity::class,
        FieldConfigEntity::class,
        SessionEntity::class,
        OccurrenceEntity::class,
        MediaEntity::class,
        GpsTrackEntity::class,
        GpsTrackPointEntity::class
    ],
    version = OikosDatabase.VERSION,
    exportSchema = true
)
abstract class OikosDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun fieldConfigDao(): FieldConfigDao
    abstract fun sessionDao(): SessionDao
    abstract fun occurrenceDao(): OccurrenceDao
    abstract fun mediaDao(): MediaDao
    abstract fun gpsTrackDao(): GpsTrackDao

    companion object {
        /** Versao atual do esquema; o backup recusa arquivos de versao maior. */
        const val VERSION = 7

        fun create(context: Context): OikosDatabase =
            Room.databaseBuilder(context, OikosDatabase::class.java, "oikos_fieldbook.db")
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .addMigrations(MIGRATION_5_6)
                .addMigrations(MIGRATION_6_7)
                .build()

        /** Visivel para o teste de migracao. */
        val ALL_MIGRATIONS: Array<Migration> get() = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "projects", "enableFauna", "INTEGER NOT NULL DEFAULT 1")
                addColumnIfMissing(db, "projects", "enableBotany", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "projects", "enableFungi", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "projects", "defaultDatum", "TEXT NOT NULL DEFAULT 'SIRGAS 2000'")
                addColumnIfMissing(db, "projects", "coordinateFormat", "TEXT NOT NULL DEFAULT 'DD'")
                addColumnIfMissing(db, "projects", "photoQuality", "TEXT NOT NULL DEFAULT 'medium'")
                addColumnIfMissing(db, "projects", "audioFormat", "TEXT NOT NULL DEFAULT 'wav'")
                addColumnIfMissing(db, "projects", "enableFauna", "INTEGER NOT NULL DEFAULT 1")
                addColumnIfMissing(db, "projects", "enableBotany", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "projects", "enableFungi", "INTEGER NOT NULL DEFAULT 0")

                addColumnIfMissing(db, "sessions", "municipality", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "sessions", "stateProvince", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "sessions", "country", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "sessions", "environmentType", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "sessions", "fieldTeam", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "sessions", "gpsTrackEnabled", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "sessions", "altitude", "REAL")
                addColumnIfMissing(db, "sessions", "geodeticDatum", "TEXT NOT NULL DEFAULT 'SIRGAS 2000'")
                addColumnIfMissing(db, "sessions", "coordinateCapturedAt", "INTEGER")

                addColumnIfMissing(db, "occurrences", "occurrenceType", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "occurrences", "formType", "TEXT NOT NULL DEFAULT 'Geral'")
                addColumnIfMissing(db, "occurrences", "recordNumber", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "occurrences", "altitude", "REAL")
                addColumnIfMissing(db, "occurrences", "geodeticDatum", "TEXT NOT NULL DEFAULT 'SIRGAS 2000'")
                addColumnIfMissing(db, "occurrences", "fieldNotes", "TEXT NOT NULL DEFAULT ''")

                addColumnIfMissing(db, "media", "coordinateUncertaintyInMeters", "REAL")
                addColumnIfMissing(db, "media", "mediaSubject", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "media", "bodyPartOrPlantPart", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "media", "behaviorContext", "TEXT NOT NULL DEFAULT ''")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `gps_tracks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `projectId` INTEGER NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `eventId` TEXT NOT NULL,
                        `startTime` INTEGER NOT NULL,
                        `endTime` INTEGER,
                        `totalDistance` REAL NOT NULL,
                        `duration` INTEGER NOT NULL,
                        `pointsCount` INTEGER NOT NULL,
                        `coordinateFormat` TEXT NOT NULL,
                        `geodeticDatum` TEXT NOT NULL,
                        `active` INTEGER NOT NULL,
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gps_tracks_projectId` ON `gps_tracks` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gps_tracks_sessionId` ON `gps_tracks` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gps_tracks_eventId` ON `gps_tracks` (`eventId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `gps_track_points` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `trackId` INTEGER NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `altitude` REAL,
                        `accuracy` REAL,
                        `timestamp` INTEGER NOT NULL,
                        `speed` REAL,
                        FOREIGN KEY(`trackId`) REFERENCES `gps_tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gps_track_points_trackId` ON `gps_track_points` (`trackId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gps_track_points_timestamp` ON `gps_track_points` (`timestamp`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "sessions", "parentEventId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "sessions", "sampleSizeValue", "REAL")
                addColumnIfMissing(db, "sessions", "sampleSizeUnit", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "sessions", "verbatimEventDate", "TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "projects", "license", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "projects", "mediaBaseUrl", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "projects", "csvStyle", "TEXT NOT NULL DEFAULT 'br'")
            }
        }


        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "media", "durationMillis", "INTEGER")
                addColumnIfMissing(db, "media", "sampleRateHz", "INTEGER")
                addColumnIfMissing(db, "media", "bitsPerSample", "INTEGER")
                addColumnIfMissing(db, "media", "channels", "INTEGER")
                addColumnIfMissing(db, "media", "audioSource", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "media", "captureDevice", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "media", "microphone", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "media", "airTemperature", "REAL")
                addColumnIfMissing(db, "media", "relativeHumidity", "REAL")
                addColumnIfMissing(db, "media", "distanceToSourceMeters", "REAL")
                // Ate a v0.2.0, apagar uma ocorrencia apagava os arquivos das midias
                // mas deixava as linhas no banco: midias fantasmas na lista e na
                // exportacao. Os arquivos ja nao existem; so as linhas saem.
                db.execSQL("DELETE FROM media WHERE occurrenceId IS NOT NULL AND occurrenceId NOT IN (SELECT id FROM occurrences)")
            }
        }

        /** Data de captura desconhecida (midia importada sem data no arquivo). */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "media", "captureTimeKnown", "INTEGER NOT NULL DEFAULT 1")
            }
        }

        private fun addColumnIfMissing(db: SupportSQLiteDatabase, tableName: String, columnName: String, definition: String) {
            val exists = db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                var found = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == columnName) {
                        found = true
                        break
                    }
                }
                found
            }
            if (!exists) db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $definition")
        }
    }
}
