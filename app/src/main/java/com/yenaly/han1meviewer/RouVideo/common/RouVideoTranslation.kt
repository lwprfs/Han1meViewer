package com.yenaly.han1meviewer.RouVideo.common

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.logic.network.HDns
import com.yenaly.han1meviewer.logic.network.HProxySelector
import com.yenaly.yenaly_libs.utils.LanguageHelper
import com.yenaly.yenaly_libs.utils.applicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object RouVideoTranslation {

    enum class Kind(val key: String, val maxEntries: Int, val long: Boolean = false) {
        TAG("tag", 2000),
        TITLE("title", 3000),
        DESC("desc", 300, long = true),
        COMMENT("comment", 1000, long = true),
    }

    data class Entry(val kind: Kind, val source: String, val text: String, val time: Long)

    data class Stats(
        val tags: Int,
        val titles: Int,
        val descriptions: Int,
        val comments: Int,
        val bytes: Long,
    ) {
        val total: Int get() = tags + titles + descriptions + comments
    }

    private const val KEY_ENABLED = "rouvideo_tr_enabled"
    private const val KEY_TAGS = "rouvideo_tr_tags"
    private const val KEY_TITLES = "rouvideo_tr_titles"
    private const val KEY_DESC = "rouvideo_tr_desc"
    private const val KEY_COMMENTS = "rouvideo_tr_comments"
    private const val KEY_SITES_OFF = "rouvideo_tr_sites_off"

    private const val BATCH_ITEMS = 15
    private const val BATCH_CHARS = 500
    private const val MAX_PENDING = 300
    private const val RATE_LIMIT_PAUSE_MS = 10 * 60_000L
    private const val ERROR_PAUSE_MS = 30_000L
    private const val GAP_MS = 400L

    private val FOREIGN = Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]")

    private val sp get() = Preferences.preferenceSp

    private val enabledState = mutableStateOf(sp.getBoolean(KEY_ENABLED, true))
    private val tagsState = mutableStateOf(sp.getBoolean(KEY_TAGS, true))
    private val titlesState = mutableStateOf(sp.getBoolean(KEY_TITLES, false))
    private val descState = mutableStateOf(sp.getBoolean(KEY_DESC, false))
    private val commentsState = mutableStateOf(sp.getBoolean(KEY_COMMENTS, false))
    private val sitesOffState = mutableStateOf(
        sp.getString(KEY_SITES_OFF, null).orEmpty().split(',')
            .mapNotNull { n -> SiteType.entries.firstOrNull { it.name == n } }.toSet()
    )

    var enabled: Boolean
        get() = enabledState.value
        set(v) { enabledState.value = v; sp.edit().putBoolean(KEY_ENABLED, v).apply() }

    var tagsEnabled: Boolean
        get() = tagsState.value
        set(v) { tagsState.value = v; sp.edit().putBoolean(KEY_TAGS, v).apply() }

    var titlesEnabled: Boolean
        get() = titlesState.value
        set(v) { titlesState.value = v; sp.edit().putBoolean(KEY_TITLES, v).apply() }

    var descriptionsEnabled: Boolean
        get() = descState.value
        set(v) { descState.value = v; sp.edit().putBoolean(KEY_DESC, v).apply() }

    var commentsEnabled: Boolean
        get() = commentsState.value
        set(v) { commentsState.value = v; sp.edit().putBoolean(KEY_COMMENTS, v).apply() }

    val SUPPORTED_SITES: List<SiteType> = listOf(SiteType.HANIME, SiteType.JAVCHU, SiteType.ROUVIDEO)

    fun siteEnabled(site: SiteType): Boolean =
        site in SUPPORTED_SITES && site !in sitesOffState.value

    fun setSiteEnabled(site: SiteType, on: Boolean) {
        val next = if (on) sitesOffState.value - site else sitesOffState.value + site
        sitesOffState.value = next
        sp.edit().putString(KEY_SITES_OFF, next.joinToString(",") { it.name }).apply()
    }

    val available: Boolean
        get() = LanguageHelper.preferredLanguage.language.equals("en", ignoreCase = true)

    fun isActive(kind: Kind, site: SiteType = SiteType.ROUVIDEO): Boolean =
        available && enabled && siteEnabled(site) && when (kind) {
            Kind.TAG -> tagsEnabled
            Kind.TITLE -> titlesEnabled
            Kind.DESC -> descriptionsEnabled
            Kind.COMMENT -> commentsEnabled
        }

    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>()
    private val pending = LinkedHashMap<String, Pair<Kind, String>>()
    private val failed = HashSet<String>()
    private val deriveQueue = ArrayDeque<Pair<Kind, String>>()
    private val deriveTried = HashSet<String>()
    private var deriveDirty = true

    private val pieces = object : LinkedHashMap<String, String>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 500
    }

    private val version = mutableIntStateOf(0)

    @Volatile
    private var loaded = false

    @Volatile
    private var pausedUntil = 0L

    @Volatile
    private var usedNetwork = false

    private var worker: Job? = null
    private var saveJob: Job? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .dns(HDns())
            .proxySelector(HProxySelector())
            .build()
    }

    private val file: File
        get() = File(applicationContext.filesDir, "rouvideo_translations.json")

    private fun key(kind: Kind, source: String) = kind.key + "\u0001" + source

    fun title(text: String, site: SiteType = SiteType.ROUVIDEO): String = shown(Kind.TITLE, text, site)

    fun description(text: String, site: SiteType = SiteType.ROUVIDEO): String =
        shown(Kind.DESC, text, site)

    fun comment(text: String, site: SiteType = SiteType.ROUVIDEO): String =
        shown(Kind.COMMENT, text, site)

    fun tag(text: String, site: SiteType = SiteType.ROUVIDEO): String = shown(Kind.TAG, text, site)

    private fun shown(kind: Kind, text: String, site: SiteType): String {
        if (text.isBlank() || !FOREIGN.containsMatchIn(text) || !isActive(kind, site)) return text
        return translate(kind, text)
    }

    fun translate(kind: Kind, source: String): String {
        version.intValue
        ensureLoaded()
        val k = key(kind, source)
        synchronized(lock) {
            entries[k]?.let { return it.text }
            if (FOREIGN.containsMatchIn(source) && k !in failed) {
                if (k !in pending) deriveDirty = true
                pending.remove(k)
                pending[k] = kind to source
                while (pending.size > MAX_PENDING) pending.remove(pending.keys.first())
            } else {
                return source
            }
        }
        schedule()
        return source
    }

    fun pausedForSeconds(): Long {
        version.intValue
        return ((pausedUntil - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
    }

    fun stats(): Stats {
        version.intValue
        ensureLoaded()
        var t = 0; var ti = 0; var d = 0; var c = 0
        synchronized(lock) {
            entries.values.forEach {
                when (it.kind) {
                    Kind.TAG -> t++
                    Kind.TITLE -> ti++
                    Kind.DESC -> d++
                    Kind.COMMENT -> c++
                }
            }
        }
        return Stats(t, ti, d, c, runCatching { file.length() }.getOrDefault(0L))
    }

    fun snapshot(kind: Kind? = null): List<Entry> {
        version.intValue
        ensureLoaded()
        return synchronized(lock) {
            entries.values.filter { kind == null || it.kind == kind }
        }.sortedByDescending { it.time }
    }

    fun delete(entry: Entry) {
        synchronized(lock) {
            val k = key(entry.kind, entry.source)
            entries.remove(k)
            failed.remove(k)
            deriveTried.remove(k)
        }
        changed()
    }

    fun clear(kind: Kind? = null) {
        synchronized(lock) {
            if (kind == null) {
                entries.clear()
                failed.clear()
                pending.clear()
                deriveQueue.clear()
                deriveTried.clear()
                pieces.clear()
            } else {
                entries.entries.removeAll { it.value.kind == kind }
                failed.removeAll { it.startsWith(kind.key + "\u0001") }
                deriveTried.removeAll { it.startsWith(kind.key + "\u0001") }
            }
            deriveDirty = true
        }
        pausedUntil = 0L
        changed()
    }

    private fun schedule() {
        synchronized(lock) {
            if (worker?.isActive == true) return
            worker = scope.launch {
                delay(300)
                while (true) {
                    val wait = pausedUntil - System.currentTimeMillis()
                    if (wait > 0) delay(wait)
                    if (!processNext()) break
                    if (usedNetwork) {
                        usedNetwork = false
                        delay(GAP_MS)
                    }
                }
            }
        }
    }

    private fun processNext(): Boolean {

        val derive = synchronized(lock) {
            if (deriveQueue.isEmpty() && deriveDirty) {
                deriveDirty = false
                val hits = pending.entries.toList().asReversed().filter { (k, v) ->
                    v.first == Kind.TITLE && k !in deriveTried && isActive(v.first, anySite()) &&
                        findNeighbor(v.second) != null
                }
                hits.forEach { pending.remove(it.key); deriveQueue.addLast(it.value) }
            }
            deriveQueue.removeFirstOrNull()
        }
        if (derive != null) {
            val (kind, source) = derive
            try {
                val text = deriveFromNeighbor(source)
                synchronized(lock) {
                    if (text != null) {
                        putEntry(kind, source, text)
                    } else {
                        val k = key(kind, source)
                        deriveTried.add(k)
                        pending[k] = derive
                    }
                }
                if (text != null) changed()
            } catch (e: Exception) {
                onNetworkError(e, listOf(derive))
            }
            return true
        }

        val group = synchronized(lock) {
            val candidates = pending.entries.toList().asReversed()
                .filter { isActive(it.value.first, anySite()) }
            pending.keys.retainAll(candidates.map { it.key }.toSet())
            val first = candidates.firstOrNull() ?: return false
            val picked = mutableListOf(first)
            if (!first.value.first.long) {
                var chars = first.value.second.length
                for (c in candidates.drop(1)) {
                    if (picked.size >= BATCH_ITEMS) break
                    val (ck, cs) = c.value
                    if (ck.long) continue
                    if (chars + cs.length > BATCH_CHARS) continue

                    if (ck == Kind.TITLE && c.key !in deriveTried &&
                        picked.any { it.value.first == Kind.TITLE && split(cs, it.value.second) != null }
                    ) continue
                    picked += c; chars += cs.length
                }
            }
            picked.forEach { pending.remove(it.key) }
            picked.map { it.value }
        }
        try {
            val out = if (group.first().first.long) {
                listOf(translateLong(group.first().second))
            } else {
                translateGroup(group.map { it.second.replace('\n', ' ') })
            }
            synchronized(lock) {
                group.forEachIndexed { i, (kind, source) ->
                    val text = out.getOrNull(i).orEmpty().trim()
                    if (text.isEmpty() || text == source) failed.add(key(kind, source))
                    else putEntry(kind, source, text)
                }
            }
            changed()
        } catch (e: Exception) {
            onNetworkError(e, group)
        }
        return true
    }

    private fun anySite(): SiteType =
        SUPPORTED_SITES.firstOrNull { siteEnabled(it) } ?: SiteType.ROUVIDEO

    private fun onNetworkError(e: Exception, group: List<Pair<Kind, String>>) {
        synchronized(lock) {
            group.forEach { (kind, source) ->
                if (pending.size < MAX_PENDING) pending.putIfAbsent(key(kind, source), kind to source)
            }
        }
        pausedUntil = System.currentTimeMillis() +
            if (e is RateLimited) RATE_LIMIT_PAUSE_MS else ERROR_PAUSE_MS
        version.intValue++
    }

    private fun putEntry(kind: Kind, source: String, text: String) {
        val k = key(kind, source)
        entries.remove(k)
        entries[k] = Entry(
            kind, source,
            if (kind == Kind.TAG) titleCase(text) else text,
            System.currentTimeMillis(),
        )
        deriveDirty = true
        Kind.entries.forEach { trim(it) }
    }

    private fun trim(kind: Kind) {
        var count = entries.values.count { it.kind == kind }
        if (count <= kind.maxEntries) return
        val it = entries.entries.iterator()
        while (it.hasNext() && count > kind.maxEntries) {
            if (it.next().value.kind == kind) { it.remove(); count-- }
        }
    }

    private fun titleCase(s: String): String =
        s.split(' ').joinToString(" ") { w ->
            if (w.isEmpty() || w.any { it.isUpperCase() }) w else w.replaceFirstChar { it.uppercase() }
        }

    private class Split(val dX: String, val dS: String, val prefix: Int, val suffix: Int)

    private fun split(x: String, s: String): Split? {
        if (x == s) return null
        val maxShared = minOf(x.length, s.length)
        var p = 0
        while (p < maxShared && x[p] == s[p]) p++
        var q = 0
        while (q < maxShared - p && x[x.length - 1 - q] == s[s.length - 1 - q]) q++

        fun digit(str: String, i: Int) = i in str.indices && str[i].isDigit()
        while (p > 0 && digit(x, p - 1) && (digit(x, p) || digit(s, p))) p--
        while (q > 0 && digit(x, x.length - q) &&
            (digit(x, x.length - q - 1) || digit(s, s.length - q - 1))
        ) q--

        val shared = p + q
        if (shared < 4 || shared * 2 < x.length || shared * 2 < s.length) return null
        val dS = s.substring(p, s.length - q)
        if (dS.isEmpty()) return null
        return Split(x.substring(p, x.length - q), dS, p, q)
    }

    private fun findNeighbor(x: String): Pair<Entry, Split>? {
        var best: Pair<Entry, Split>? = null
        var bestShared = -1
        for (e in entries.values) {
            if (e.kind != Kind.TITLE) continue
            val sp = split(x, e.source) ?: continue
            val shared = sp.prefix + sp.suffix
            if (shared > bestShared) { best = e to sp; bestShared = shared }
        }
        return best
    }

    private fun deriveFromNeighbor(x: String): String? {
        val (neighbor, sp) = synchronized(lock) { findNeighbor(x) } ?: return null
        val trS = fragment(sp.dS)
        val trX = fragment(sp.dX)
        val text = neighbor.text
        val digits = trS.isNotEmpty() && trS.all { it.isDigit() }
        val result = if (digits) {
            val re = Regex("(?<!\\d)" + Regex.escape(trS) + "(?!\\d)")
            if (re.findAll(text).count() != 1) return null
            text.replace(re, Regex.escapeReplacement(trX))
        } else {
            if (trS.isEmpty()) return null
            val first = text.indexOf(trS)
            if (first < 0 || text.indexOf(trS, first + 1) >= 0) return null
            text.substring(0, first) + trX + text.substring(first + trS.length)
        }
        return result.replace(Regex("\\s{2,}"), " ").trim().takeIf { it.isNotEmpty() }
    }

    private fun fragment(piece: String): String {
        if (piece.isEmpty() || !FOREIGN.containsMatchIn(piece)) return piece
        synchronized(lock) { pieces[piece]?.let { return it } }
        val out = request(piece.replace('\n', ' ')).joinToString(" ").trim()
        synchronized(lock) { pieces[piece] = out }
        return out
    }

    private class RateLimited : java.io.IOException("HTTP 429")

    private fun request(text: String): List<String> {
        usedNetwork = true
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=en&dt=t" +
            "&q=" + URLEncoder.encode(text, "UTF-8")
        val body = client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.code == 429) throw RateLimited()
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            r.body.string()
        }
        val segments = JSONArray(body).getJSONArray(0)
        val sb = StringBuilder()
        for (i in 0 until segments.length()) {
            val seg = segments.optJSONArray(i) ?: continue
            if (!seg.isNull(0)) sb.append(seg.getString(0))
        }
        return sb.toString().replace("\r\n", "\n").trimEnd('\n').split('\n')
    }

    private fun translateGroup(texts: List<String>): List<String> {
        val out = request(texts.joinToString("\n"))
        if (out.size == texts.size) return out
        if (texts.size == 1) return listOf(out.joinToString(" "))
        val mid = texts.size / 2
        return translateGroup(texts.subList(0, mid)) + translateGroup(texts.subList(mid, texts.size))
    }

    private fun translateLong(text: String): String {
        val chunks = mutableListOf<String>()
        val cur = StringBuilder()
        fun flushChunk() { if (cur.isNotEmpty()) { chunks += cur.toString(); cur.clear() } }
        for (line in text.split('\n')) {
            var rest = line
            while (rest.length > 450) {
                flushChunk(); chunks += rest.take(450); rest = rest.drop(450)
            }
            if (cur.length + rest.length + 1 > 450) flushChunk()
            if (cur.isNotEmpty()) cur.append('\n')
            cur.append(rest)
        }
        flushChunk()
        return chunks.joinToString("\n") { request(it).joinToString("\n") }
    }

    private fun changed() {
        version.intValue++
        synchronized(lock) {
            if (saveJob?.isActive == true) return
            saveJob = scope.launch {
                delay(1000)
                save()
            }
        }
    }

    private fun save() {
        runCatching {
            val arr = JSONArray()
            synchronized(lock) {
                entries.values.forEach {
                    arr.put(
                        JSONObject().put("k", it.kind.key).put("s", it.source)
                            .put("t", it.text).put("ts", it.time)
                    )
                }
            }
            val f = file
            if (arr.length() == 0) f.delete() else f.writeText(arr.toString(), Charsets.UTF_8)
        }
        version.intValue++
    }

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            val f = file
            if (!f.exists()) return
            val arr = JSONArray(f.readText(Charsets.UTF_8))
            synchronized(lock) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val kind = Kind.entries.firstOrNull { it.key == o.getString("k") } ?: continue
                    val source = o.getString("s")
                    entries[key(kind, source)] =
                        Entry(kind, source, o.getString("t"), o.optLong("ts"))
                }
            }
        }
    }
}
