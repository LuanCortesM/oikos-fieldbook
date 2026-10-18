package com.oikos.fieldbook.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
        const val VERSION = 1
        const val NAME = "oikos_checklists.db"

        fun create(context: Context): ChecklistDatabase =
            Room.databaseBuilder(context, ChecklistDatabase::class.java, NAME)
                // E so cache: se um dia o esquema mudar sem migracao, as listas
                // sao baixadas de novo em vez de o app travar.
                .fallbackToDestructiveMigration()
                .build()
    }
}
