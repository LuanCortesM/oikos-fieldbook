package com.oikos.fieldbook.checklist

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.oikos.fieldbook.data.ChecklistDao
import com.oikos.fieldbook.data.ChecklistEntity
import com.oikos.fieldbook.data.ChecklistDatabase
import com.oikos.fieldbook.data.TaxonNameEntity
import com.oikos.fieldbook.data.TaxonSuggestion
import com.oikos.fieldbook.data.VernacularEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.oikos.fieldbook.util.allocatableBytes
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

/** Resultado da conferencia de um nome digitado; [synonymOf] preenchido quando so existe como sinonimo. */
data class NameCheck(val found: Boolean, val synonymOf: String? = null)

/** Fonte oficial que o app sabe baixar sozinho. */
data class ChecklistSource(
    val key: String,
    val title: String,
    val scope: String,
    val description: String,
    val archiveUrl: String,
    val emlUrl: String,
    /** Copia do mesmo arquivo no ChecklistBank, usada quando o IPT recusa a conexao. */
    val mirrorArchiveUrl: String,
    /** Metadados da copia no ChecklistBank; "issued" e o pubDate do eml.xml. */
    val mirrorInfoUrl: String,
    val approxDownloadMb: Int,
    /** Espaco no banco depois de importada, medido no emulador em 17/10/2026. */
    val approxInstalledMb: Int
) {
    val candidateUrls: List<String> get() = listOf(archiveUrl, mirrorArchiveUrl)
}

/**
 * O que a fonte publica agora. O IPT informa a versao (393.432); o ChecklistBank
 * so a data de publicacao, que e o pubDate do eml.xml da mesma versao.
 */
data class RemoteVersion(val version: String?, val pubDate: String?) {
    fun isNewerThan(installed: ChecklistEntity): Boolean = when {
        version != null -> version != installed.version
        pubDate != null -> pubDate != installed.pubDate.trim()
        else -> false
    }

    val label: String get() = version ?: pubDate.orEmpty()
}

/**
 * Fontes conferidas em 17/10/2026. No IPT do JBRJ, archive.do entrega sempre a
 * versao mais nova e eml.do traz a versao no packageId (poucos KB para checar
 * atualizacao). Nessa data o IPT recusava (403) clientes que nao fossem
 * navegador, inclusive o rastreador do GBIF, entao o app recorre a copia do
 * arquivo original guardada pelo ChecklistBank (Catalogue of Life/GBIF), feita
 * para acesso por programas. A copia pode estar algumas semanas atras do IPT.
 */
object ChecklistSources {
    val all = listOf(
        ChecklistSource(
            key = "flora_brasil",
            title = "Flora e Funga do Brasil",
            scope = "Brasil · plantas, algas e fungos",
            description = "Lista oficial do JBRJ.",
            archiveUrl = "https://ipt.jbrj.gov.br/jbrj/archive.do?r=lista_especies_flora_brasil",
            emlUrl = "https://ipt.jbrj.gov.br/jbrj/eml.do?r=lista_especies_flora_brasil",
            mirrorArchiveUrl = "https://api.checklistbank.org/dataset/2031/archive.zip",
            mirrorInfoUrl = "https://api.checklistbank.org/dataset/2031",
            approxDownloadMb = 20,
            approxInstalledMb = 35
        ),
        ChecklistSource(
            key = "fauna_brasil",
            title = "Catálogo Taxonômico da Fauna do Brasil",
            scope = "Brasil · animais",
            description = "Catálogo oficial da fauna brasileira.",
            archiveUrl = "https://ipt.jbrj.gov.br/jbrj/archive.do?r=catalogo_taxonomico_da_fauna_do_brasil",
            emlUrl = "https://ipt.jbrj.gov.br/jbrj/eml.do?r=catalogo_taxonomico_da_fauna_do_brasil",
            mirrorArchiveUrl = "https://api.checklistbank.org/dataset/281817/archive.zip",
            mirrorInfoUrl = "https://api.checklistbank.org/dataset/281817",
            approxDownloadMb = 18,
            approxInstalledMb = 44
        )
    )

    fun byKey(key: String): ChecklistSource? = all.firstOrNull { it.key == key }

    fun isMirror(url: String): Boolean = url.contains("checklistbank.org")
}

/**
 * Baixa, atualiza, importa e consulta listas de especies. Sem servidor proprio:
 * os arquivos vem direto da fonte oficial, e a busca roda no banco local.
 */
class ChecklistService(private val context: Context, private val database: ChecklistDatabase) {
    private val dao: ChecklistDao = database.checklistDao()

    /** O que a fonte publica agora (poucos KB: o eml.xml do IPT ou os metadados da copia). */
    suspend fun latestVersion(source: ChecklistSource): RemoteVersion? = withContext(Dispatchers.IO) {
        runCatching {
            val eml = open(source.emlUrl).use { connection -> connection.inputStream.use { ChecklistParser.parseEml(it) } }
            RemoteVersion(eml.version.ifBlank { null }, eml.pubDate.ifBlank { null }).takeIf { it.label.isNotEmpty() }
        }.getOrNull() ?: runCatching {
            val json = open(source.mirrorInfoUrl).use { connection -> connection.inputStream.bufferedReader().use { it.readText() } }
            JSONObject(json).optString("issued").trim().ifBlank { null }?.let { RemoteVersion(null, it) }
        }.getOrNull()
    }

    /** Tenta o IPT e, se ele recusar ou entregar outra coisa, a copia do ChecklistBank. */
    suspend fun download(source: ChecklistSource, onProgress: (stage: String, fraction: Float?) -> Unit): ChecklistEntity =
        withContext(Dispatchers.IO) {
            // Arquivo baixado + nomes no banco + folga: em celular cheio, melhor
            // avisar antes do que falhar no meio da importacao.
            val neededMb = source.approxDownloadMb + source.approxInstalledMb + 50
            val freeMb = allocatableBytes(context, context.filesDir) / (1024 * 1024)
            if (freeMb < neededMb) {
                throw IOException("espaço insuficiente: são necessários cerca de $neededMb MB livres e há $freeMb MB")
            }
            val zip = File(context.cacheDir, "checklist-${source.key}.zip")
            val failures = mutableListOf<String>()
            try {
                for (url in source.candidateUrls) {
                    try {
                        onProgress("Conectando...", null)
                        fetch(url, zip, onProgress)
                        return@withContext importZip(source.key, url, zip, onProgress)
                    } catch (error: IOException) {
                        failures += "${URL(url).host}: ${describe(error)}"
                    }
                }
                // Sem rede, as duas fontes falham igual: uma frase basta.
                if (failures.all { it.endsWith(NO_CONNECTION) }) throw IOException(NO_CONNECTION)
                throw IOException(failures.joinToString("; "))
            } finally {
                zip.delete()
            }
        }

    private fun fetch(url: String, target: File, onProgress: (stage: String, fraction: Float?) -> Unit) {
        open(url).use { connection ->
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        onProgress("Baixando ${done / (1024 * 1024)} MB", if (total > 0) done.toFloat() / total else null)
                    }
                }
            }
        }
        // Um firewall pode responder 200 com uma pagina HTML no lugar do arquivo.
        val magic = target.inputStream().use { input -> ByteArray(4).also { input.read(it) } }
        if (!(magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte())) throw IOException("a resposta não é um arquivo .zip")
    }

    /** Lista que o pesquisador baixou por conta propria (qualquer DwC-A de checklist). */
    suspend fun importFile(uri: Uri, onProgress: (stage: String, fraction: Float?) -> Unit): ChecklistEntity =
        withContext(Dispatchers.IO) {
            val name = displayName(uri) ?: "lista"
            val zip = File(context.cacheDir, "checklist-arquivo.zip")
            try {
                context.contentResolver.openInputStream(uri)?.use { input -> zip.outputStream().use { input.copyTo(it) } }
                    ?: throw IOException("Não foi possível ler o arquivo escolhido.")
                importZip("arquivo:$name", "", zip, onProgress)
            } finally {
                zip.delete()
            }
        }

    /**
     * Importa em uma lista nova e so depois apaga a antiga da mesma fonte: se o
     * download vier corrompido, a lista que ja estava instalada continua valendo.
     */
    private suspend fun importZip(
        sourceKey: String,
        sourceUrl: String,
        zipFile: File,
        onProgress: (stage: String, fraction: Float?) -> Unit
    ): ChecklistEntity {
        val temporaryKey = "$sourceKey#importando"
        dao.getBySource(temporaryKey)?.let { dao.deleteChecklist(it.id) }
        val id = dao.insertChecklist(
            ChecklistEntity(
                sourceKey = temporaryKey, title = "", version = "", pubDate = "", license = "", citation = "",
                sourceUrl = sourceUrl, importedAt = System.currentTimeMillis(), taxonCount = 0
            )
        )
        try {
            val names = ArrayList<TaxonNameEntity>(BATCH)
            val vernaculars = ArrayList<VernacularEntity>(BATCH)
            var count = 0
            fun flushNames() {
                if (names.isEmpty()) return
                database.runInTransaction { dao.insertNamesNow(names) }
                names.clear()
            }
            fun flushVernaculars() {
                if (vernaculars.isEmpty()) return
                database.runInTransaction { dao.insertVernacularsNow(vernaculars) }
                vernaculars.clear()
            }
            val metadata = ZipFile(zipFile).use { zip ->
                ChecklistParser.read(
                    zip,
                    onTaxon = { record ->
                        names += TaxonNameEntity(
                            checklistId = id,
                            taxonId = record.taxonId,
                            canonicalName = record.canonicalName,
                            authorship = record.authorship,
                            rank = record.rank,
                            status = record.status.code,
                            acceptedTaxonId = record.acceptedTaxonId,
                            acceptedName = record.acceptedName,
                            family = record.family,
                            genus = record.genus,
                            kingdom = record.kingdom,
                            taxonClass = record.taxonClass,
                            taxonOrder = record.taxonOrder,
                            searchKey = ChecklistParser.searchKey(record.canonicalName)
                        )
                        count++
                        if (names.size >= BATCH) flushNames()
                    },
                    onVernacular = { taxonId, name, language ->
                        vernaculars += VernacularEntity(
                            checklistId = id, taxonId = taxonId, name = name, language = language,
                            searchKey = ChecklistParser.searchKey(name)
                        )
                        if (vernaculars.size >= BATCH) flushVernaculars()
                    },
                    onProgress = { done, total -> onProgress("Importando nomes", if (total > 0) done.toFloat() / total else null) }
                )
            }
            flushNames()
            flushVernaculars()
            if (count == 0) throw IOException("O arquivo não contém nomes de táxons.")
            val title = metadata.title.ifBlank { ChecklistSources.byKey(sourceKey)?.title ?: sourceKey.removePrefix("arquivo:") }
            dao.finishChecklist(id, count, metadata.version, metadata.pubDate, metadata.license, metadata.citation, title)
            dao.getBySource(sourceKey)?.let { old -> dao.deleteChecklist(old.id) }
            dao.renameSource(id, sourceKey)
            return dao.getBySource(sourceKey)!!
        } catch (error: Throwable) {
            // Mesmo cancelada (app fechado no meio), a lista pela metade sai: antes
            // a exclusao era cancelada junto e a lista sem titulo ficava na busca.
            withContext(NonCancellable) { dao.deleteChecklist(id) }
            throw error
        }
    }

    suspend fun remove(checklist: ChecklistEntity) = dao.deleteChecklist(checklist.id)

    /** Listas pela metade de uma importacao interrompida (app morto no meio). */
    suspend fun removeInterruptedImports() = dao.deleteBySourceSuffix("%#importando")

    /**
     * Sugestoes para o autocompletar. "boa fab" acha Boana faber (prefixo de
     * cada palavra); nomes populares tambem sao buscados.
     */
    suspend fun search(query: String, limit: Int = 8): List<TaxonSuggestion> {
        val key = ChecklistParser.searchKey(query)
        if (key.length < 2) return emptyList()
        val tokens = key.split(' ').filter { it.isNotEmpty() }
        val first = tokens.first()
        val pattern = tokens.joinToString(" ") { "$it%" }
        val scientific = dao.searchScientific(first, "$first~", pattern, limit)
        val vernacular = if (scientific.size < limit) dao.searchVernacular(first, "$first~", pattern, limit - scientific.size) else emptyList()
        return (scientific + vernacular).distinctBy { it.taxon.id }
    }

    /**
     * Nome aceito de uma sugestao. Sinonimo cujo aceito nao esta na lista
     * devolve nulo, e quem chama avisa. Antes o sinonimo era registrado como se
     * fosse o nome aceito, sem aviso nenhum.
     */
    suspend fun accepted(suggestion: TaxonSuggestion): TaxonNameEntity? {
        val taxon = suggestion.taxon
        if (taxon.status != TaxonStatus.SYNONYM.code) return taxon
        if (taxon.acceptedTaxonId.isNotBlank()) {
            dao.getTaxon(taxon.checklistId, taxon.acceptedTaxonId)?.let { return it }
        }
        // Sem o id (ou com id de outro arquivo), procura pelo nome aceito: o
        // texto pode trazer a autoria, entao tenta do nome inteiro ate genero
        // e epiteto.
        val words = ChecklistParser.searchKey(taxon.acceptedName).split(' ').filter { it.isNotEmpty() }
        for (size in words.size downTo 2) {
            dao.findAccepted(taxon.checklistId, words.take(size).joinToString(" "))?.let { return it }
        }
        return null
    }

    suspend fun vernacularFor(taxon: TaxonNameEntity): String? = dao.firstVernacular(taxon.checklistId, taxon.taxonId)

    /**
     * O nome digitado existe em alguma lista instalada, e como? Nulo se nao ha
     * lista. Antes um sinonimo digitado aparecia como "nome confere".
     */
    suspend fun check(fullName: String, authorship: String): NameCheck? {
        if (dao.countChecklists() == 0) return null
        val canonical = ChecklistParser.canonical(fullName, authorship)
        val matches = dao.exactMatches(ChecklistParser.searchKey(canonical))
        if (matches.isEmpty()) return NameCheck(found = false)
        if (matches.any { it.status == TaxonStatus.ACCEPTED.code }) return NameCheck(found = true)
        val synonym = matches.firstOrNull { it.status == TaxonStatus.SYNONYM.code }
        return NameCheck(found = true, synonymOf = synonym?.acceptedName?.ifBlank { "?" })
    }

    /**
     * Icone do registro no mapa (regras em [MapIcons]). So consulta as listas:
     * nao muda nada no registro.
     */
    suspend fun mapIconGroup(occurrence: com.oikos.fieldbook.data.OccurrenceEntity): MapIconGroup {
        if (occurrence.occurrenceType == "Armadilha fotográfica") return MapIconGroup.CAMERA_TRAP
        val fields = runCatching { org.json.JSONObject(occurrence.dynamicFieldsJson) }.getOrNull()
        fun field(key: String): String = fields?.optString(key).orEmpty().trim()
        val texts = buildList {
            add(occurrence.occurrenceRemarks)
            add(occurrence.fieldNotes)
            add(occurrence.behavior)
            MapIcons.ASSOCIATION_KEYS.forEach { add(field(it)) }
        }.filter { it.isNotBlank() }
        val taxon = lookupForIcon(occurrence.scientificName, field("scientificNameAuthorship"), field("genus"))
        val info = TaxonGroupInfo(
            kingdom = taxon?.kingdom?.ifBlank { null } ?: field("kingdom"),
            taxonClass = taxon?.taxonClass?.ifBlank { null } ?: field("class"),
            taxonOrder = taxon?.taxonOrder?.ifBlank { null } ?: field("order"),
            family = taxon?.family?.ifBlank { null } ?: field("family")
        )
        return when {
            occurrence.formType == "Fungos" || info.kingdom.equals("Fungi", ignoreCase = true) -> MapIconGroup.FUNGI
            occurrence.formType == "Botanica" || info.kingdom.equals("Plantae", ignoreCase = true) ->
                if (info.family.equals("Bromeliaceae", ignoreCase = true) && anuranAssociated(texts)) MapIconGroup.FROG_BROMELIAD
                else MapIconGroup.FLORA
            else -> {
                val group = MapIcons.animalGroup(info) ?: MapIcons.groupFromAnimalGroupText(field("animalGroup")) ?: MapIconGroup.OTHER
                if (group == MapIconGroup.FROG && bromeliadAssociated(texts)) MapIconGroup.FROG_BROMELIAD else group
            }
        }
    }

    /** Nome exato e, se nao houver, o genero; prefere a linha que traz classe e ordem. */
    private suspend fun lookupForIcon(fullName: String, authorship: String, genus: String): TaxonNameEntity? {
        if (fullName.isBlank() || dao.countChecklists() == 0) return null
        fun best(rows: List<TaxonNameEntity>) = rows.firstOrNull { it.taxonClass.isNotBlank() || it.taxonOrder.isNotBlank() } ?: rows.firstOrNull()
        val canonical = ChecklistParser.canonical(fullName, authorship)
        best(dao.exactMatches(ChecklistParser.searchKey(canonical)))?.let { return it }
        val genusName = genus.ifBlank { canonical.substringBefore(' ') }
        if (genusName.isBlank()) return null
        return best(dao.exactMatches(ChecklistParser.searchKey(genusName)))
    }

    /** Registro de anuro que cita bromelia, por palavra ou pelo nome de uma Bromeliaceae. */
    private suspend fun bromeliadAssociated(texts: List<String>): Boolean {
        if (MapIcons.mentionsBromeliad(texts)) return true
        return MapIcons.nameCandidates(texts).any { name ->
            lookupForIcon(name, "", "")?.family.equals("Bromeliaceae", ignoreCase = true)
        }
    }

    /** Registro de bromelia que cita anuro, por palavra ou pelo nome de um Anura. */
    private suspend fun anuranAssociated(texts: List<String>): Boolean {
        if (MapIcons.mentionsAnuran(texts)) return true
        return MapIcons.nameCandidates(texts).any { name ->
            lookupForIcon(name, "", "")?.taxonOrder.equals("Anura", ignoreCase = true)
        }
    }

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "OikosFieldBook (Android; com.oikos.fieldbook)")
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            throw IOException("erro HTTP $code")
        }
        return connection
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T = try { block(this) } finally { disconnect() }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun describe(error: IOException): String = when (error) {
        is java.net.UnknownHostException, is java.net.ConnectException -> NO_CONNECTION
        is java.net.SocketTimeoutException -> "a conexão ficou lenta demais e foi interrompida"
        else -> error.message ?: error.javaClass.simpleName
    }

    companion object {
        private const val BATCH = 2_000
        private const val NO_CONNECTION = "sem conexão com a internet"
    }
}
