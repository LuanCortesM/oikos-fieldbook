package com.oikos.fieldbook.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Listas de especies num banco separado do caderno de campo. Sao dezenas de MB
 * que podem ser baixados de novo a qualquer momento: fora do banco principal,
 * nao pesam no backup do app nem estouram a cota de 25 MB do backup automatico
 * do Android, e restaurar um backup nao as apaga.
 */
@Database(
    entities = [ChecklistEntity::class, TaxonNameEntity::class, VernacularEntity::class],
    version = ChecklistDatabase.VERSION,
    exportSchema = true
)
abstract class ChecklistDatabase : RoomDatabase() {
    abstract fun checklistDao(): ChecklistDao

    companion object {
        const val VERSION = 2
        const val NAME = "oikos_checklists.db"

        /**
         * v2: classe e ordem de cada nome, para o icone do registro no mapa.
         * As listas ja instaladas continuam; os campos novos ficam vazios ate
         * a lista ser baixada de novo.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE taxon_names ADD COLUMN taxonClass TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE taxon_names ADD COLUMN taxonOrder TEXT NOT NULL DEFAULT ''")
            }
        }

        fun create(context: Context): ChecklistDatabase =
            Room.databaseBuilder(context, ChecklistDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                // E so cache: se um dia o esquema mudar sem migracao, as listas
                // sao baixadas de novo em vez de o app travar.
                .fallbackToDestructiveMigration()
                .build()
    }
}
