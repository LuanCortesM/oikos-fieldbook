package com.oikos.fieldbook.data

import android.content.ContentValues
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/**
 * Linhas do banco como JSON, coluna a coluna, sem depender das entidades:
 * usado pela lixeira e pela troca de projetos entre aparelhos. Um retrato
 * antigo continua restauravel num esquema mais novo (colunas novas ficam com o
 * valor padrao; colunas que sumiram sao ignoradas).
 */
object RowSnapshot {

    fun rows(db: SupportSQLiteDatabase, table: String, where: String, args: Array<Any>): JSONArray {
        val result = JSONArray()
        db.query("SELECT * FROM $table WHERE $where", args).use { cursor ->
            while (cursor.moveToNext()) {
                val row = JSONObject()
                for (c in 0 until cursor.columnCount) {
                    val name = cursor.getColumnName(c)
                    when (cursor.getType(c)) {
                        Cursor.FIELD_TYPE_NULL -> row.put(name, JSONObject.NULL)
                        Cursor.FIELD_TYPE_INTEGER -> row.put(name, cursor.getLong(c))
                        Cursor.FIELD_TYPE_FLOAT -> row.put(name, cursor.getDouble(c))
                        Cursor.FIELD_TYPE_BLOB -> row.put(name, android.util.Base64.encodeToString(cursor.getBlob(c), android.util.Base64.NO_WRAP))
                        else -> row.put(name, cursor.getString(c))
                    }
                }
                result.put(row)
            }
        }
        return result
    }

    /** Coluna da tabela como o SQLite a declara. */
    data class Column(val type: String, val notNull: Boolean, val hasDefault: Boolean, val primaryKey: Boolean)

    fun columns(db: SupportSQLiteDatabase, table: String): Map<String, Column> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            val type = cursor.getColumnIndexOrThrow("type")
            val notNull = cursor.getColumnIndexOrThrow("notnull")
            val default = cursor.getColumnIndexOrThrow("dflt_value")
            val pk = cursor.getColumnIndexOrThrow("pk")
            buildMap {
                while (cursor.moveToNext()) {
                    put(cursor.getString(name), Column(cursor.getString(type).orEmpty().uppercase(), cursor.getInt(notNull) == 1, !cursor.isNull(default), cursor.getInt(pk) > 0))
                }
            }
        }

    /**
     * Valores para INSERT: so as colunas que existem hoje, sem as de [skip], e
     * com [overrides] por cima (ids remapeados, caminho novo do arquivo).
     * Coluna obrigatoria que o retrato nao tem (criada depois dele) recebe vazio
     * ou zero: banco criado do zero nao tem DEFAULT nessas colunas, e o INSERT
     * falharia.
     */
    fun contentValues(
        row: JSONObject,
        columns: Map<String, Column>,
        skip: Set<String> = emptySet(),
        overrides: Map<String, Any?> = emptyMap()
    ): ContentValues = ContentValues().apply {
        columns.forEach { (name, column) ->
            if (column.notNull && !column.hasDefault && !column.primaryKey && !row.has(name) && name !in overrides) {
                if (column.type.contains("INT") || column.type.contains("REAL") || column.type.contains("FLOA") || column.type.contains("DOUB")) put(name, 0L) else put(name, "")
            }
        }
        row.keys().forEach { key ->
            if (key !in columns || key in skip || key in overrides) return@forEach
            when (val value = row.get(key)) {
                JSONObject.NULL -> putNull(key)
                is Int -> put(key, value.toLong())
                is Long -> put(key, value)
                is Double -> put(key, value)
                is Float -> put(key, value.toDouble())
                is Boolean -> put(key, if (value) 1L else 0L)
                else -> put(key, value.toString())
            }
        }
        overrides.forEach { (key, value) ->
            if (key !in columns) return@forEach
            when (value) {
                null -> putNull(key)
                is Long -> put(key, value)
                is Int -> put(key, value.toLong())
                is Double -> put(key, value)
                else -> put(key, value.toString())
            }
        }
    }
}
