package com.example.sonntag.cloud

import com.example.sonntag.data.repos.PreferencesRepository
import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.sync.IV_SIZE
import com.example.sonntag.sync.RowValues
import com.example.sonntag.sync.SALT_SIZE
import com.example.sonntag.sync.SyncCrypto
import com.example.sonntag.sync.SyncSection
import com.example.sonntag.sync.SyncStamp
import com.example.sonntag.sync.SyncStore
import com.example.sonntag.sync.base64Decode
import com.example.sonntag.sync.base64Encode
import com.example.sonntag.sync.naturalKey
import com.example.sonntag.sync.newUuid
import com.example.sonntag.sync.referencedTable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.Instant
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.concurrent.Volatile

/** Versao do formato da nuvem. Um app mais velho que a nuvem se recusa a escrever nela. */
private const val ESQUEMA = 1

private const val META_ESQUEMA = "esquema"
private const val META_ID = "id"
private const val META_SAL = "sal"
private const val META_VERIFICADOR = "verificador"

/** Texto cifrado no verificador: decifrar de volta prova que a senha confere. */
private const val MARCA = "sonntag-nuvem"

/** Campo extra no conteudo cifrado de uma lapide que so aponta o uuid que a substitui. */
private const val ALIAS_KEY = "_alias"

private const val PREF_ATIVA = "nuvem_ativa"
private const val PREF_HOST = "nuvem_host"
private const val PREF_PORTA = "nuvem_porta"
private const val PREF_BANCO = "nuvem_banco"
private const val PREF_USUARIO = "nuvem_usuario"
private const val PREF_SENHA = "nuvem_senha"
private const val PREF_CHAVE = "nuvem_chave"
/** Sal da nuvem conectada; confere a senha da congregacao sem ir ao servidor. */
private const val PREF_SAL = "nuvem_sal"
private const val PREF_ID = "nuvem_id"
private const val PREF_CURSOR = "nuvem_cursor"
private const val PREF_ENVIO = "nuvem_envio"
private const val PREF_ULTIMA = "nuvem_ultima"

/** Nome de cada aparelho da nuvem (JSON id -> nome), guardado para funcionar offline. */
private const val PREF_APARELHOS = "nuvem_aparelhos"

/**
 * Linhas ja enviadas cujo carimbo e posterior ao ultimo envio ("tabela|uuid|updated_at",
 * uma por linha). Acontece com a escolha feita num conflito (carimbo um segundo acima
 * das duas versoes) ou com relogio adiantado; sem esta lista, elas contariam como
 * pendentes para sempre.
 */
private const val PREF_JA_ENVIADAS = "nuvem_ja_enviadas"

/** Prefixo, em `sonntag_meta`, das chaves com o nome de cada aparelho. */
private const val META_APARELHO = "aparelho:"

/**
 * Os dias e horarios de reuniao ficam fora, como na rede local: cada instalacao tem os
 * seus, e aceitar o do vizinho regeraria a agenda no horario errado.
 */
private val FORA_DA_NUVEM = setOf("meeting_days")

/** Ordem de gravacao: quem e referenciado antes de quem referencia. */
val CLOUD_TABLES: List<String> = SyncSection.entries.flatMap { it.tables }.distinct() - FORA_DA_NUVEM
private val TABELAS = CLOUD_TABLES

/** Prefixo dos segredos guardados via [SecretBox]; sem ele, o valor e de antes da cifra. */
private const val SELADO = "s1:"

/**
 * Nao somem ao trocar os dados locais pelos da nuvem: sem `settings` o app volta para
 * a configuracao inicial, e as reunioes sao regeradas de qualquer forma.
 */
private val PRESERVADAS_AO_SUBSTITUIR = setOf("settings", "meetings")

/** Tabelas cujo conteudo indica que a instalacao ja esta em uso. */
private val TABELAS_COM_DADOS = listOf(
    "members", "weekend_programs", "midweek_programs", "av_assignments",
    "cleaning_groups", "events", "preaching_spots", "preaching_groups",
)

/** Colunas que nao contam como conteudo ao comparar duas versoes. */
private val CARIMBO = setOf("id", "uuid", "updated_at", "updated_by")

private val json = Json

/** Resultado de conectar, antes de decidir como juntar os dados. */
class CloudProbe(
    val cloudId: String,
    val key: ByteArray,
    val salt: String,
    val cloudHasData: Boolean,
    val localHasData: Boolean,
)

enum class CloudStartMode {
    /** Junta os dois lados; em cada linha vence a versao mais recente. */
    COMBINAR,

    /** Descarta o que so existe aqui e fica com o que esta na nuvem. */
    USAR_NUVEM,
}

/** Qual das duas versoes de um conflito fica. */
enum class ConflictSide { LOCAL, REMOTA }

/**
 * Uma edicao simultanea registrada: as duas versoes (referencias como uuid) e a que esta
 * em uso agora, para a tela saber qual e qual.
 */
class CloudConflict(
    val id: Long,
    val table: String,
    val uuid: String,
    val local: RowValues,
    val remote: RowValues,
    val remoteDevice: String,
    val detectedAt: String,
    val current: RowValues?,
) {
    /** A versao que esta valendo aqui; null se nenhuma das duas (mudou depois). */
    val inUse: ConflictSide?
        get() = when {
            current == null -> null
            sameContent(current, local) -> ConflictSide.LOCAL
            sameContent(current, remote) -> ConflictSide.REMOTA
            else -> null
        }
}

/** Mesmo conteudo, ignorando carimbo e as colunas que so um lado conhece. */
internal fun sameContent(a: RowValues, b: RowValues): Boolean =
    b.keys.filter { it !in CARIMBO && a.containsKey(it) }.all { a[it].orEmpty() == b[it].orEmpty() }

data class CloudSyncResult(
    val recebidos: Int,
    val enviados: Int,
    val conflitos: Int,
    val ignorados: Int,
)

/**
 * Sincronizacao com um PostgreSQL compartilhado.
 *
 * O banco local continua sendo a fonte das telas; a nuvem e um ponto de encontro. Cada
 * sincronizacao baixa o que chegou desde a ultima vez (pela sequencia do servidor, que
 * independe do relogio dos aparelhos), funde e depois envia o que mudou aqui.
 *
 * Na fusao, vence a versao mais recente de cada linha — sem dialogo, porque a
 * sincronizacao automatica nao pode parar para perguntar. A versao perdedora, quando os
 * dois lados mudaram, fica em `sync_conflicts`.
 */
class CloudSync(
    private val database: SonntagDatabase,
    private val store: SyncStore,
    private val stamp: SyncStamp,
    private val crypto: SyncCrypto,
    private val prefs: PreferencesRepository,
    private val secrets: SecretBox,
) {
    /** Uma sincronizacao por vez: duas fusoes simultaneas gravariam uma sobre a outra. */
    private val mutex = Mutex()

    private val _active = MutableStateFlow(prefs.get(PREF_ATIVA) == "1")

    /** Ligada ou nao; a sincronizacao automatica acompanha. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    val isActive: Boolean get() = _active.value

    val deviceId: String get() = stamp.deviceId

    /**
     * Verdadeiro enquanto a fusao avisa as telas do que gravou. Quem vigia o banco
     * atras de alteracoes locais usa isto para nao confundir o que veio da nuvem com
     * uma edicao a enviar — senao cada sincronizacao dispararia outra.
     */
    @Volatile
    var applyingRemote: Boolean = false
        private set

    /** Conexao reaproveitada entre sincronizacoes: abrir uma a cada 10 s custa caro no celular. */
    private var sessao: CloudSession? = null
    private var sessaoConfig: CloudConfig? = null

    /** O nome deste aparelho ja foi publicado na nuvem nesta execucao. */
    private var anunciado = false

    val lastSyncAt: String? get() = prefs.get(PREF_ULTIMA)?.takeIf { it.isNotEmpty() }

    /** Configuracao salva, mesmo desconectada (para preencher o formulario). */
    fun savedConfig(): CloudConfig? {
        val host = prefs.get(PREF_HOST)?.takeIf { it.isNotEmpty() } ?: return null
        return CloudConfig(
            host = host,
            port = prefs.get(PREF_PORTA)?.toIntOrNull() ?: CloudConfig.DEFAULT_PORT,
            database = prefs.get(PREF_BANCO).orEmpty().ifEmpty { CloudConfig.DEFAULT_DATABASE },
            user = prefs.get(PREF_USUARIO).orEmpty(),
            password = secret(PREF_SENHA).orEmpty(),
        )
    }

    /** Alteracoes feitas aqui que ainda nao subiram. */
    fun pendingCount(): Int {
        if (!isActive) return 0
        val desde = prefs.get(PREF_ENVIO)?.takeIf { it.isNotEmpty() }
        val jaEnviadas = prefs.get(PREF_JA_ENVIADAS).orEmpty().lines().filter { it.isNotEmpty() }.toSet()
        val linhas = TABELAS.sumOf { t ->
            if (desde == null) {
                store.select("SELECT COUNT(*) FROM $t WHERE updated_by = ?", 1, stamp.deviceId).first()[0]!!.toInt()
            } else {
                store.select(
                    "SELECT uuid, updated_at FROM $t WHERE updated_by = ? AND updated_at >= ?",
                    2, stamp.deviceId, desde,
                ).count { (uuid, em) -> "$t|$uuid|$em" !in jaEnviadas }
            }
        }
        val apelidos = store.select("SELECT COUNT(*) FROM cloud_aliases WHERE enviado = 0", 1).first()[0]!!.toInt()
        return linhas + apelidos
    }

    /** Edicoes simultaneas registradas e ainda nao revisadas. */
    fun conflictCount(): Int =
        store.select("SELECT COUNT(*) FROM sync_conflicts WHERE resolvido = 0", 1).first()[0]!!.toInt()

    /** Nome que o aparelho [id] publicou na nuvem, se ja o conhecemos. */
    fun deviceName(id: String): String? = runCatching {
        json.parseToJsonElement(prefs.get(PREF_APARELHOS).orEmpty().ifEmpty { "{}" })
            .jsonObject[id]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    /** Edicoes simultaneas ainda nao revisadas, da mais recente para a mais antiga. */
    fun conflicts(): List<CloudConflict> =
        store.select(
            "SELECT id, tabela, row_uuid, local_json, remoto_json, remoto_device, detectado_em " +
                "FROM sync_conflicts WHERE resolvido = 0 ORDER BY detectado_em DESC, id DESC",
            7,
        ).mapNotNull { r ->
            val tabela = r[1]!!
            val uuid = r[2]!!
            CloudConflict(
                id = r[0]!!.toLong(),
                table = tabela,
                uuid = uuid,
                local = parseRow(r[3]) ?: return@mapNotNull null,
                remote = parseRow(r[4]) ?: return@mapNotNull null,
                remoteDevice = r[5].orEmpty(),
                detectedAt = r[6].orEmpty(),
                current = linhaAtual(tabela, uuid),
            )
        }

    /**
     * Fica com a versao escolhida de cada conflito. Se ela ja e a que esta em uso, so
     * marca como revisado; senao grava com carimbo novo, para vencer em todos os
     * aparelhos na proxima sincronizacao.
     */
    fun resolveConflicts(choices: Map<Long, ConflictSide>) {
        val porId = conflicts().associateBy { it.id }
        val alteradas = mutableSetOf<String>()
        database.transaction {
            choices.forEach { (id, lado) ->
                val conflito = porId[id] ?: return@forEach
                val escolhida = if (lado == ConflictSide.LOCAL) conflito.local else conflito.remote
                val atual = conflito.current
                if (atual != null && !sameContent(atual, escolhida)) {
                    gravarVersao(conflito.table, conflito.uuid, escolhida, carimboAcima(conflito))
                    alteradas += conflito.table
                }
                store.execute("UPDATE sync_conflicts SET resolvido = 1 WHERE id = ?", id.toString())
            }
        }
        // Aviso normal (nao da fusao): a sincronizacao automatica ve a edicao e envia.
        store.notifyChanged(alteradas)
    }

    /**
     * Carimbo que vence as duas versoes do conflito. "Agora" nao basta: se o relogio do
     * outro aparelho esta adiantado, a escolha perderia para a versao descartada.
     */
    private fun carimboAcima(c: CloudConflict): String {
        val agora = stamp.now()
        val maior = listOfNotNull(c.local["updated_at"], c.remote["updated_at"], c.current?.get("updated_at"))
            .maxOrNull() ?: return agora
        if (agora > maior) return agora
        return runCatching {
            Instant.fromEpochSeconds(Instant.parse(maior).epochSeconds + 1).toString()
        }.getOrDefault(agora)
    }

    private fun gravarVersao(table: String, uuid: String, valores: RowValues, updatedAt: String) {
        val colunas = store.columns(table).toSet()
        val paraId = colunas.mapNotNull { c -> referencedTable(c)?.let { c to store.uuidToLocalId(it) } }.toMap()
        val gravar = valores.filterKeys { it in colunas && it !in CARIMBO }.mapValues { (c, v) ->
            paraId[c]?.let { m -> v?.let { m[it]?.toString() } } ?: v
        } + mapOf("updated_at" to updatedAt, "updated_by" to stamp.deviceId)
        store.updateByUuid(table, uuid, gravar)
    }

    /** A linha como esta aqui agora, com referencias como uuid. */
    private fun linhaAtual(table: String, uuid: String): RowValues? {
        val colunas = store.columns(table).filter { it != "id" }
        val paraUuid = colunas.mapNotNull { c -> referencedTable(c)?.let { c to store.localIdToUuid(it) } }.toMap()
        return store.findByUuid(table, uuid, colunas)?.mapValues { (c, v) ->
            paraUuid[c]?.let { m -> v?.toLongOrNull()?.let { m[it] } } ?: v
        }
    }

    private fun parseRow(text: String?): RowValues? = runCatching {
        json.parseToJsonElement(text.orEmpty()).jsonObject.mapValues { (_, v) -> v.jsonPrimitive.contentOrNull }
    }.getOrNull()

    private fun secret(pref: String): String? {
        val valor = prefs.get(pref)?.takeIf { it.isNotEmpty() } ?: return null
        if (!valor.startsWith(SELADO)) {
            // Guardado antes da cifra: sela agora e segue.
            prefs.set(pref, SELADO + secrets.seal(valor))
            return valor
        }
        return secrets.open(valor.removePrefix(SELADO))
    }

    private fun setSecret(pref: String, value: String) {
        prefs.set(pref, if (value.isEmpty()) "" else SELADO + secrets.seal(value))
    }

    // ─── Conexao ─────────────────────────────────────────────────────────────

    /**
     * Conecta, cria as tabelas que faltarem e confere a senha da congregacao. Nada
     * local e alterado ainda: o chamador decide como juntar com [activate].
     */
    suspend fun probe(config: CloudConfig, passphrase: String): CloudProbe = mutex.withLock {
        openCloudSession(config).use { session ->
            session.ensureSchema()
            // Quem chega primeiro define; os demais leem o que ficou.
            session.putMetaIfAbsent(
                mapOf(
                    META_ESQUEMA to ESQUEMA.toString(),
                    META_ID to newUuid(),
                    META_SAL to base64Encode(crypto.randomBytes(SALT_SIZE)),
                ),
            )
            var meta = session.meta()
            if ((meta[META_ESQUEMA]?.toIntOrNull() ?: ESQUEMA) > ESQUEMA) {
                throw CloudException(CloudFailure.VERSAO_NOVA)
            }
            val key = crypto.deriveKey(passphrase, base64Decode(meta.getValue(META_SAL)))
            if (META_VERIFICADOR !in meta) {
                session.putMetaIfAbsent(
                    mapOf(META_VERIFICADOR to base64Encode(seal(MARCA.encodeToByteArray(), key))),
                )
                meta = session.meta()
            }
            val confere = open(base64Decode(meta.getValue(META_VERIFICADOR)), key)
                ?.decodeToString() == MARCA
            if (!confere) throw CloudException(CloudFailure.SENHA_CONGREGACAO)

            CloudProbe(
                cloudId = meta.getValue(META_ID),
                key = key,
                salt = meta.getValue(META_SAL),
                cloudHasData = session.countRows() > 0,
                localHasData = TABELAS_COM_DADOS.any { store.countAlive(it) > 0 },
            )
        }
    }

    /** Salva a conexao e faz a primeira sincronizacao. */
    suspend fun activate(config: CloudConfig, probe: CloudProbe, mode: CloudStartMode): CloudSyncResult {
        // Outra nuvem: o ponto em que paramos na anterior nao vale nesta.
        if (prefs.get(PREF_ID) != probe.cloudId) {
            prefs.set(PREF_ENVIO, "")
            prefs.set(PREF_ULTIMA, "")
            store.execute("DELETE FROM cloud_aliases")
        }
        anunciado = false
        // A primeira volta le a nuvem inteira: ao substituir, o que nao vier nela some
        // daqui, entao uma leitura parcial apagaria o que so nao mudou recentemente.
        prefs.set(PREF_CURSOR, "0")
        prefs.set(PREF_HOST, config.host)
        prefs.set(PREF_PORTA, config.port.toString())
        prefs.set(PREF_BANCO, config.database)
        prefs.set(PREF_USUARIO, config.user)
        setSecret(PREF_SENHA, config.password)
        setSecret(PREF_CHAVE, base64Encode(probe.key))
        prefs.set(PREF_SAL, probe.salt)
        prefs.set(PREF_ID, probe.cloudId)
        val result = sync(replace = mode == CloudStartMode.USAR_NUVEM, collectLocal = true)
        prefs.set(PREF_ATIVA, "1")
        _active.value = true
        return result
    }

    /**
     * Arquivo com a conexao atual, cifrado com a senha da congregacao. A senha e
     * conferida antes: um arquivo cifrado com outra deixaria o outro aparelho preso
     * num erro de senha que ninguem entenderia.
     */
    suspend fun connectionFile(passphrase: String): ByteArray {
        val config = savedConfig()?.takeIf { it.password.isNotEmpty() }
            ?: throw CloudException(CloudFailure.NAO_CONFIGURADA)
        val chave = secret(PREF_CHAVE)?.let(::base64Decode)
            ?: throw CloudException(CloudFailure.NAO_CONFIGURADA)
        // Conexoes feitas antes de o sal ser guardado: busca uma vez no servidor.
        val sal = prefs.get(PREF_SAL)?.takeIf { it.isNotEmpty() }
            ?: mutex.withLock { openCloudSession(config).use { it.meta() } }[META_SAL]
                ?.also { prefs.set(PREF_SAL, it) }
            ?: throw CloudException(CloudFailure.NUVEM_TROCADA)
        if (!crypto.deriveKey(passphrase, base64Decode(sal)).contentEquals(chave)) {
            throw CloudException(CloudFailure.SENHA_CONGREGACAO)
        }
        return CloudConnectionFile.encode(config, passphrase, crypto)
    }

    /** Null se a senha nao abre o arquivo; [IllegalArgumentException] se nao e um arquivo de conexao. */
    fun readConnectionFile(bytes: ByteArray, passphrase: String): CloudConfig? =
        CloudConnectionFile.decode(bytes, passphrase, crypto)

    /**
     * Para de sincronizar e esquece as senhas. Os dados locais ficam; o servidor, o
     * banco e o usuario tambem, para facilitar reconectar.
     */
    suspend fun disconnect() {
        _active.value = false
        mutex.withLock {
            prefs.set(PREF_ATIVA, "0")
            prefs.set(PREF_SENHA, "")
            prefs.set(PREF_CHAVE, "")
            fecharSessao()
        }
    }

    /**
     * [collectLocal] falso pula a varredura das tabelas locais: sem edicao aqui desde a
     * ultima volta, so ha o que baixar. E o caso comum da sincronizacao periodica.
     */
    suspend fun syncNow(collectLocal: Boolean = true): CloudSyncResult {
        if (!isActive) throw CloudException(CloudFailure.NAO_CONFIGURADA)
        return sync(replace = false, collectLocal = collectLocal)
    }

    // ─── Ciclo ───────────────────────────────────────────────────────────────

    private suspend fun sync(replace: Boolean, collectLocal: Boolean): CloudSyncResult = mutex.withLock {
        val config = savedConfig() ?: throw CloudException(CloudFailure.NAO_CONFIGURADA)
        // Sem a chave nao ha como abrir os segredos (app reinstalado, chave do
        // aparelho perdida): so conectando de novo.
        val key = secret(PREF_CHAVE)?.let(::base64Decode)
            ?: throw CloudException(CloudFailure.NAO_CONFIGURADA)

        comSessao(config) { session -> ciclo(session, key, replace, collectLocal) }
    }

    /**
     * Roda [block] na conexao guardada. Se ela morreu enquanto esperava (troca de rede,
     * servidor que fecha ociosas), tenta uma vez numa nova antes de dar como sem conexao.
     * Repetir o ciclo inteiro e seguro: baixar e enviar de novo nao duplica nada.
     */
    private fun <T> comSessao(config: CloudConfig, block: (CloudSession) -> T): T {
        val reaproveitada = sessao?.takeIf { sessaoConfig == config }
        if (reaproveitada != null) {
            try {
                return block(reaproveitada)
            } catch (e: CloudException) {
                fecharSessao()
                if (e.failure != CloudFailure.SEM_CONEXAO && e.failure != CloudFailure.ERRO) throw e
            }
        }
        fecharSessao()
        val nova = openCloudSession(config)
        sessao = nova
        sessaoConfig = config
        return try {
            block(nova)
        } catch (e: Exception) {
            fecharSessao()
            throw e
        }
    }

    private fun guardarNomes(session: CloudSession) {
        val nomes = session.meta().filterKeys { it.startsWith(META_APARELHO) }
            .mapKeys { it.key.removePrefix(META_APARELHO) }
        prefs.set(PREF_APARELHOS, buildJsonObject { nomes.forEach { (k, v) -> put(k, JsonPrimitive(v)) } }.toString())
    }

    private fun fecharSessao() {
        sessao?.close()
        sessao = null
        sessaoConfig = null
    }

    private fun ciclo(session: CloudSession, key: ByteArray, replace: Boolean, collectLocal: Boolean): CloudSyncResult {
        // Tomado antes de ler: o que mudar daqui em diante entra no proximo envio.
        val inicio = stamp.now()
        val envioAnterior = prefs.get(PREF_ENVIO)?.takeIf { it.isNotEmpty() }
        val cursor = prefs.get(PREF_CURSOR)?.toLongOrNull() ?: 0L

        if (!anunciado) {
            // Tabelas recriadas (ou outro banco no mesmo endereco): continuar do cursor
            // antigo pularia tudo, e enviar misturaria duas nuvens.
            val id = session.meta()[META_ID]
            if (id == null || id != prefs.get(PREF_ID)) throw CloudException(CloudFailure.NUVEM_TROCADA)
            session.putMeta(mapOf(META_APARELHO + stamp.deviceId to deviceLabel()))
            guardarNomes(session)
            anunciado = true
        }

        val remotas = session.pull(cursor)
        val fusao = Fusao(key, envioAnterior, replace)
        if (remotas.isNotEmpty()) {
            database.transaction { fusao.aplicar(remotas) }
            applyingRemote = true
            try {
                store.notifyChanged(fusao.alteradas)
            } finally {
                applyingRemote = false
            }
            prefs.set(PREF_CURSOR, remotas.maxOf { it.seq }.toString())
        }

        // Ao substituir, o que ficou aqui nao deve voltar para a nuvem.
        val desde = if (replace) inicio else envioAnterior
        val varrer = collectLocal || replace || fusao.forcarEnvio.isNotEmpty()
        val apelidos = apelidosPendentes()
        val locais = if (varrer) linhasLocais(desde, fusao.forcarEnvio, key) else emptyList()
        val enviados = session.push(locais + apelidos.map { it.toRow(key) }, origin = stamp.deviceId)
        apelidos.forEach {
            store.execute(
                "UPDATE cloud_aliases SET enviado = 1 WHERE tabela = ? AND de = ? AND para = ?",
                it.tabela, it.de, it.para,
            )
        }
        // Conflito novo pode ser com um aparelho que entrou depois do anuncio.
        if (fusao.conflitos > 0) guardarNomes(session)
        // Sem varredura, nada garante que o que mudou antes de `inicio` subiu.
        if (varrer) {
            prefs.set(PREF_ENVIO, inicio)
            prefs.set(
                PREF_JA_ENVIADAS,
                locais.filter { it.updatedBy == stamp.deviceId && it.updatedAt >= inicio }
                    .joinToString("\n") { "${it.tabela}|${it.uuid}|${it.updatedAt}" },
            )
        }
        prefs.set(PREF_ULTIMA, stamp.now())

        return CloudSyncResult(
            recebidos = fusao.recebidos,
            enviados = enviados,
            conflitos = fusao.conflitos,
            ignorados = fusao.ignorados,
        )
    }

    // ─── Envio ───────────────────────────────────────────────────────────────

    /** Linhas alteradas desde [desde] (todas, se null), com referencias como uuid. */
    private fun linhasLocais(desde: String?, forcar: Set<Pair<String, String>>, key: ByteArray): List<CloudRow> =
        TABELAS.flatMap { table ->
            val colunas = store.columns(table).filter { it != "id" }
            val paraUuid = colunas.mapNotNull { c -> referencedTable(c)?.let { c to store.localIdToUuid(it) } }.toMap()
            store.rows(table, colunas)
                .filter { row ->
                    val uuid = row["uuid"].orEmpty()
                    uuid.isNotEmpty() && (
                        desde == null || (row["updated_at"] ?: "") >= desde || (table to uuid) in forcar
                        )
                }
                .map { row ->
                    val valores = row.mapValues { (c, v) -> paraUuid[c]?.let { m -> v?.toLongOrNull()?.let { m[it] } } ?: v }
                    CloudRow(
                        tabela = table,
                        uuid = row.getValue("uuid")!!,
                        updatedAt = row["updated_at"].orEmpty(),
                        updatedBy = row["updated_by"].orEmpty(),
                        deleted = row["deleted"] == "1",
                        dados = seal(encodeRow(valores, alias = null), key),
                    )
                }
        }

    private class Apelido(val tabela: String, val de: String, val para: String)

    private fun apelidosPendentes(): List<Apelido> =
        store.select("SELECT tabela, de, para FROM cloud_aliases WHERE enviado = 0", 3)
            .map { Apelido(it[0]!!, it[1]!!, it[2]!!) }

    /**
     * Lapide que diz "esta linha agora se chama [Apelido.para]". Quem ainda a tem com o
     * uuid antigo renomeia em vez de apagar.
     */
    private fun Apelido.toRow(key: ByteArray) = CloudRow(
        tabela = tabela,
        uuid = de,
        updatedAt = stamp.now(),
        updatedBy = stamp.deviceId,
        deleted = true,
        dados = seal(encodeRow(emptyMap(), alias = para), key),
    )

    // ─── Fusao ───────────────────────────────────────────────────────────────

    /**
     * Aplica ao banco local o que veio da nuvem.
     *
     * A parte delicada sao as linhas **derivadas** (reunioes, semanas de limpeza): cada
     * aparelho gera as suas, com uuids proprios. Elas se reconhecem pela chave natural
     * e todos convergem para o **menor** uuid — regra que da o mesmo resultado em
     * qualquer aparelho, na ordem que for. Adotar sempre o que chega faria dois
     * aparelhos trocarem de uuid um com o outro para sempre.
     */
    private inner class Fusao(
        private val key: ByteArray,
        private val envioAnterior: String?,
        private val replace: Boolean,
    ) {
        var recebidos = 0
        var conflitos = 0
        var ignorados = 0
        val alteradas = mutableSetOf<String>()

        /** Linhas que precisam subir mesmo sem terem mudado desde o ultimo envio. */
        val forcarEnvio = mutableSetOf<Pair<String, String>>()

        /** tabela -> (uuid antigo -> uuid que o substitui). */
        private val apelidos: MutableMap<String, MutableMap<String, String>> =
            store.select("SELECT tabela, de, para FROM cloud_aliases", 3)
                .groupBy({ it[0]!! }, { it[1]!! to it[2]!! })
                .mapValues { (_, pares) -> pares.toMap().toMutableMap() }
                .toMutableMap()

        private fun resolver(table: String, uuid: String): String {
            var atual = uuid
            val mapa = apelidos[table] ?: return uuid
            repeat(16) { atual = mapa[atual] ?: return atual }
            return atual
        }

        private fun registrarApelido(table: String, de: String, para: String, enviado: Boolean) {
            if (de == para) return
            apelidos.getOrPut(table) { mutableMapOf() }[de] = para
            store.execute(
                "INSERT OR REPLACE INTO cloud_aliases (tabela, de, para, enviado) VALUES (?, ?, ?, ?)",
                table, de, para, if (enviado) "1" else "0",
            )
        }

        fun aplicar(remotas: List<CloudRow>) {
            val porTabela = remotas.groupBy { it.tabela }
            TABELAS.forEach { table ->
                val daTabela = porTabela[table].orEmpty()
                // Ao substituir, a tabela sem nada na nuvem tambem precisa ser esvaziada.
                if (daTabela.isNotEmpty() || replace) aplicarTabela(table, daTabela)
            }
        }

        private fun aplicarTabela(table: String, remotas: List<CloudRow>) {
            val colunas = store.columns(table).filter { it != "id" }
            val colunasSet = colunas.toSet()
            val obrigatorias = store.notNullColumns(table)
            val referencias = colunas.mapNotNull { c -> referencedTable(c)?.let { c to it } }.toMap()
            val paraUuid = referencias.mapValues { (_, alvo) -> store.localIdToUuid(alvo) }
            val paraId = referencias.mapValues { (_, alvo) -> store.uuidToLocalId(alvo) }

            // Linhas daqui na mesma moeda da nuvem: referencias como uuid.
            val locais = linkedMapOf<String, RowValues>()
            store.rows(table, colunas).forEach { row ->
                val uuid = row["uuid"] ?: return@forEach
                locais[uuid] = row.mapValues { (c, v) ->
                    paraUuid[c]?.let { m -> v?.toLongOrNull()?.let { m[it] } } ?: v
                }
            }
            val chave = naturalKey(table)
            val porChave = mutableMapOf<String, String>()
            fun indexar(uuid: String, row: RowValues) {
                val k = chave?.let { chaveDe(row, it) } ?: return
                val atual = porChave[k]
                // Com duplicata local, a viva representa a chave.
                if (atual == null || locais[atual]?.get("deleted") == "1") porChave[k] = uuid
            }
            locais.forEach { (uuid, row) -> indexar(uuid, row) }

            val tocadas = mutableSetOf<String>()

            /** Converte para ids locais; null se uma referencia obrigatoria nao resolve. */
            fun paraGravar(valores: RowValues, uuid: String): RowValues? {
                val out = linkedMapOf<String, String?>()
                for ((c, v) in valores) {
                    if (c !in colunasSet) continue
                    val destino = paraId[c]
                    out[c] = if (destino == null) v else {
                        val id = v?.let { destino[it] }?.toString()
                        if (id == null && c in obrigatorias) return null
                        id
                    }
                }
                out["uuid"] = uuid
                return out
            }

            for (remota in remotas) {
                val (bruto, alias) = decode(remota.dados, key) ?: run { ignorados++; continue }

                // Lapide de apelido: a linha continua viva sob outro uuid.
                if (alias != null) {
                    val para = resolver(table, alias)
                    registrarApelido(table, remota.uuid, para, enviado = true)
                    val local = locais[remota.uuid] ?: continue
                    if (locais.containsKey(para)) {
                        if (local["deleted"] != "1") {
                            store.execute("UPDATE $table SET deleted = 1 WHERE uuid = ?", remota.uuid)
                            locais[remota.uuid] = local + ("deleted" to "1")
                            alteradas += table
                        }
                    } else {
                        renomear(table, remota.uuid, para, locais, porChave)
                    }
                    tocadas += para
                    continue
                }

                // Referencias apontando para uuids ja substituidos.
                val remotaValores: RowValues = bruto.mapValues { (c, v) ->
                    val alvo = referencias[c]
                    if (alvo != null && v != null) resolver(alvo, v) else v
                }

                val porApelido = resolver(table, remota.uuid).takeIf { it != remota.uuid && locais.containsKey(it) }
                // Lapide que so casa pela chave natural costuma ser a duplicata que morreu,
                // e aplica-la apagaria a sobrevivente. Ao substituir e diferente: a nuvem
                // manda, e a linha daqui ainda nao tocada nesta volta deve morrer junto.
                val porChaveNatural = if (locais.containsKey(remota.uuid) || porApelido != null) null
                else chave?.let { chaveDe(remotaValores, it) }?.let { porChave[it] }
                    ?.takeIf { !remota.deleted || (replace && it !in tocadas) }

                var alvo: String? = when {
                    locais.containsKey(remota.uuid) -> remota.uuid
                    porApelido != null -> porApelido
                    else -> porChaveNatural
                }

                if (alvo == null) {
                    if (remota.deleted) continue
                    val valores = paraGravar(remotaValores, remota.uuid) ?: run { ignorados++; continue }
                    store.insert(table, valores)
                    locais[remota.uuid] = remotaValores
                    indexar(remota.uuid, remotaValores)
                    tocadas += remota.uuid
                    alteradas += table
                    recebidos++
                    continue
                }

                var renomeada = false
                if (porChaveNatural != null) {
                    // A mesma linha gerada nos dois lados: fica o menor uuid.
                    if (remota.uuid < porChaveNatural) {
                        renomear(table, porChaveNatural, remota.uuid, locais, porChave)
                        registrarApelido(table, porChaveNatural, remota.uuid, enviado = false)
                        alvo = remota.uuid
                        renomeada = true
                    } else {
                        registrarApelido(table, remota.uuid, porChaveNatural, enviado = false)
                    }
                } else if (porApelido != null) {
                    // Alguem ainda escreve no uuid antigo: reavisa a nuvem.
                    registrarApelido(table, remota.uuid, porApelido, enviado = false)
                }
                tocadas += alvo

                val local = locais.getValue(alvo)
                val vence = replace || maisNova(remota.updatedAt, remota.updatedBy, local)
                val igual = mesmoConteudo(local, remotaValores)
                val localPendente = local["updated_by"] == stamp.deviceId &&
                    (envioAnterior == null || (local["updated_at"] ?: "") >= envioAnterior)
                val doisLados = !replace && !igual && localPendente && remota.updatedBy != stamp.deviceId

                if (vence && !igual) {
                    val valores = paraGravar(remotaValores, alvo) ?: run { ignorados++; continue }
                    if (doisLados) registrarConflito(table, alvo, local, remotaValores, remota.updatedBy)
                    store.updateByUuid(table, alvo, valores)
                    locais[alvo] = remotaValores + ("uuid" to alvo)
                    alteradas += table
                    recebidos++
                } else if (!vence) {
                    if (doisLados) registrarConflito(table, alvo, local, remotaValores, remota.updatedBy)
                    // A versao daqui ganhou, mas na nuvem ela pode estar so sob o outro
                    // uuid, ou nem estar: sobe de novo sob o que ficou.
                    if (renomeada || alvo != remota.uuid) forcarEnvio += table to alvo
                }
            }

            if (replace && table !in PRESERVADAS_AO_SUBSTITUIR) {
                locais.filter { (uuid, row) -> uuid !in tocadas && row["deleted"] != "1" }.keys.forEach { uuid ->
                    // Sem carimbo novo: nao e uma exclusao a espalhar, so o que nao veio.
                    store.execute("UPDATE $table SET deleted = 1 WHERE uuid = ?", uuid)
                    alteradas += table
                }
            }
        }

        private fun renomear(
            table: String,
            de: String,
            para: String,
            locais: MutableMap<String, RowValues>,
            porChave: MutableMap<String, String>,
        ) {
            store.updateByUuid(table, de, mapOf("uuid" to para), allowUuidChange = true)
            locais.remove(de)?.let { locais[para] = it + ("uuid" to para) }
            porChave.entries.filter { it.value == de }.forEach { it.setValue(para) }
            alteradas += table
        }

        private fun registrarConflito(
            table: String,
            uuid: String,
            local: RowValues,
            remota: RowValues,
            remotoDevice: String,
        ) {
            store.execute(
                "INSERT OR REPLACE INTO sync_conflicts " +
                    "(tabela, row_uuid, local_json, remoto_json, remoto_device, detectado_em, resolvido) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 0)",
                table, uuid, encodeRow(local, null).decodeToString(),
                encodeRow(remota, null).decodeToString(), remotoDevice, stamp.now(),
            )
            conflitos++
        }
    }

    // ─── Formato ─────────────────────────────────────────────────────────────

    private fun maisNova(updatedAt: String, updatedBy: String, local: RowValues): Boolean {
        val la = local["updated_at"].orEmpty()
        return updatedAt > la || (updatedAt == la && updatedBy > local["updated_by"].orEmpty())
    }

    /** So as colunas que os dois lados conhecem: versoes diferentes do app convivem. */
    private fun mesmoConteudo(local: RowValues, remota: RowValues): Boolean =
        remota.keys.filter { it !in CARIMBO && local.containsKey(it) }.all { local[it] == remota[it] }

    private fun chaveDe(row: RowValues, colunas: List<String>): String? {
        if (colunas.isEmpty()) return "singleton"
        val partes = colunas.map { row[it] }
        if (partes.any { it == null }) return null
        return partes.joinToString("\u0000")
    }

    private fun encodeRow(values: RowValues, alias: String?): ByteArray = buildJsonObject {
        values.forEach { (c, v) -> if (c != "id") put(c, v?.let { JsonPrimitive(it) } ?: JsonNull) }
        alias?.let { put(ALIAS_KEY, JsonPrimitive(it)) }
    }.toString().encodeToByteArray()

    private fun decode(dados: ByteArray, key: ByteArray): Pair<RowValues, String?>? {
        val plain = open(dados, key) ?: return null
        val obj = runCatching { json.parseToJsonElement(plain.decodeToString()).jsonObject }.getOrNull() ?: return null
        val alias = obj[ALIAS_KEY]?.jsonPrimitive?.contentOrNull
        val valores = obj.filterKeys { it != ALIAS_KEY }.mapValues { (_, v) -> v.jsonPrimitive.contentOrNull }
        return valores to alias
    }

    /** iv + texto cifrado. */
    private fun seal(plain: ByteArray, key: ByteArray): ByteArray {
        val iv = crypto.randomBytes(IV_SIZE)
        return iv + crypto.encryptWithKey(plain, key, iv)
    }

    private fun open(dados: ByteArray, key: ByteArray): ByteArray? {
        if (dados.size <= IV_SIZE) return null
        return crypto.decryptWithKey(dados.copyOfRange(IV_SIZE, dados.size), key, dados.copyOfRange(0, IV_SIZE))
    }
}
