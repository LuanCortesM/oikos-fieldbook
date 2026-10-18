package com.oikos.fieldbook.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Lista de especies instalada no aparelho (Flora e Funga do Brasil, Fauna do Brasil...). */
@Entity(tableName = "checklists", indices = [Index(value = ["sourceKey"], unique = true)])
data class ChecklistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Identificador da fonte no app ("flora_brasil", "fauna_brasil", "arquivo:<nome>"). */
    val sourceKey: String,
    val title: String,
    /** Versao publicada pela fonte (ex.: 393.432), lida do eml.xml. */
    val version: String,
    val pubDate: String,
    val license: String,
    val citation: String,
    val sourceUrl: String,
    val importedAt: Long,
    val taxonCount: Int
)

@Entity(
    tableName = "taxon_names",
    foreignKeys = [ForeignKey(entity = ChecklistEntity::class, parentColumns = ["id"], childColumns = ["checklistId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("checklistId"), Index("searchKey"), Index(value = ["checklistId", "taxonId"])]
)
data class TaxonNameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val checklistId: Long,
    val taxonId: String,
    val canonicalName: String,
    val authorship: String,
    val rank: String,
    /** A = aceito, S = sinonimo, O = outro. */
    val status: String,
    val acceptedTaxonId: String,
    val acceptedName: String,
    val family: String,
    val genus: String,
    val kingdom: String,
    /** Nome normalizado (minusculas, sem acento) para busca por prefixo no indice. */
    val searchKey: String
)

@Entity(
    tableName = "taxon_vernacular",
    foreignKeys = [ForeignKey(entity = ChecklistEntity::class, parentColumns = ["id"], childColumns = ["checklistId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("checklistId"), Index("searchKey"), Index(value = ["checklistId", "taxonId"])]
)
data class VernacularEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val checklistId: Long,
    val taxonId: String,
    val name: String,
    val language: String,
    val searchKey: String
)

/** Sugestao do autocompletar: o nome e a lista de onde veio. */
data class TaxonSuggestion(
    @Embedded val taxon: TaxonNameEntity,
    val checklistTitle: String,
    val checklistVersion: String,
    /** Nome popular que casou com a busca, quando a busca foi por nome popular. */
    val matchedVernacular: String?
)

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklists ORDER BY title")
    fun observeChecklists(): Flow<List<ChecklistEntity>>

    @Query("SELECT * FROM checklists ORDER BY title")
    suspend fun getChecklists(): List<ChecklistEntity>

    @Query("SELECT * FROM checklists WHERE sourceKey = :sourceKey")
    suspend fun getBySource(sourceKey: String): ChecklistEntity?

    @Insert
    suspend fun insertChecklist(checklist: ChecklistEntity): Long

    @Query("DELETE FROM checklists WHERE id = :id")
    suspend fun deleteChecklist(id: Long)

    @Query("DELETE FROM checklists WHERE sourceKey LIKE :pattern")
    suspend fun deleteBySourceSuffix(pattern: String)

    @Query("UPDATE checklists SET taxonCount = :count, version = :version, pubDate = :pubDate, license = :license, citation = :citation, title = :title WHERE id = :id")
    suspend fun finishChecklist(id: Long, count: Int, version: String, pubDate: String, license: String, citation: String, title: String)

    /** Insercao em lote chamada de dentro do laco de leitura (ja fora da main thread). */
    @Insert
    fun insertNamesNow(names: List<TaxonNameEntity>)

    @Insert
    fun insertVernacularsNow(names: List<VernacularEntity>)

    @Query("UPDATE checklists SET sourceKey = :sourceKey WHERE id = :id")
    suspend fun renameSource(id: Long, sourceKey: String)

    /**
     * Busca por prefixo em intervalo de indice ([lo], [hi]) e filtro fino por
     * [pattern] (varias palavras: "boa% fab%"). Aceitos primeiro, nomes curtos primeiro.
     */
    @Query(
        """
        SELECT t.*, c.title AS checklistTitle, c.version AS checklistVersion, NULL AS matchedVernacular
        FROM taxon_names t JOIN checklists c ON c.id = t.checklistId
        WHERE t.searchKey >= :lo AND t.searchKey < :hi AND t.searchKey LIKE :pattern
        ORDER BY CASE t.status WHEN 'A' THEN 0 WHEN 'S' THEN 1 ELSE 2 END, length(t.canonicalName), t.canonicalName
        LIMIT :limit
        """
    )
    suspend fun searchScientific(lo: String, hi: String, pattern: String, limit: Int): List<TaxonSuggestion>

    @Query(
        """
        SELECT t.*, c.title AS checklistTitle, c.version AS checklistVersion, v.name AS matchedVernacular
        FROM taxon_vernacular v
        JOIN taxon_names t ON t.checklistId = v.checklistId AND t.taxonId = v.taxonId
        JOIN checklists c ON c.id = t.checklistId
        WHERE v.searchKey >= :lo AND v.searchKey < :hi AND v.searchKey LIKE :pattern
        ORDER BY CASE t.status WHEN 'A' THEN 0 ELSE 1 END, length(v.name)
        LIMIT :limit
        """
    )
    suspend fun searchVernacular(lo: String, hi: String, pattern: String, limit: Int): List<TaxonSuggestion>

    @Query("SELECT * FROM taxon_names WHERE checklistId = :checklistId AND taxonId = :taxonId LIMIT 1")
    suspend fun getTaxon(checklistId: Long, taxonId: String): TaxonNameEntity?

    @Query("SELECT COUNT(*) FROM taxon_names WHERE searchKey = :key")
    suspend fun countExact(key: String): Int

    @Query("SELECT * FROM taxon_names WHERE searchKey = :key LIMIT 20")
    suspend fun exactMatches(key: String): List<TaxonNameEntity>

    /** Nome aceito com esta chave na mesma lista (para sinonimo sem id do aceito). */
    @Query("SELECT * FROM taxon_names WHERE checklistId = :checklistId AND searchKey = :key AND status = 'A' LIMIT 1")
    suspend fun findAccepted(checklistId: Long, key: String): TaxonNameEntity?

    @Query("SELECT COUNT(*) FROM checklists")
    suspend fun countChecklists(): Int

    @Query(
        """
        SELECT name FROM taxon_vernacular
        WHERE checklistId = :checklistId AND taxonId = :taxonId
        ORDER BY CASE WHEN lower(language) LIKE 'port%' OR lower(language) = 'pt' THEN 0 ELSE 1 END
        LIMIT 1
        """
    )
    suspend fun firstVernacular(checklistId: Long, taxonId: String): String?
}
