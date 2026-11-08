package com.yenaly.han1meviewer.RouVideo.common

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.yenaly.han1meviewer.Preferences
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

/**
 * On-demand Chinese -> English translation for rou.video (tags, titles, descriptions).
 *
 * - Only available when the app language is English ([available]).
 * - Everything is opt-in per kind and can be switched off; switching off shows the original text.
 * - Text is translated lazily: only strings that were actually drawn are queued, newest first.
 * - Requests go to Google's free endpoint in small batches, one at a time. On HTTP 429 the queue
 *   pauses for [RATE_LIMIT_PAUSE_MS]; on other network errors for [ERROR_PAUSE_MS].
 * - Results are cached on disk (bounded per kind) so nothing is requested twice.
 */
object RouVideoTranslation {

    enum class Kind(val key: String, val maxEntries: Int) {
        TAG("tag", 2000),
        TITLE("title", 3000),
        DESC("desc", 300),
    }

    data class Entry(val kind: Kind, val source: String, val text: String, val time: Long)

    data class Stats(val tags: Int, val titles: Int, val descriptions: Int, val bytes: Long) {
        val total: Int get() = tags + titles + descriptions
    }

    private const val KEY_ENABLED = "rouvideo_tr_enabled"
    private const val KEY_TAGS = "rouvideo_tr_tags"
    private const val KEY_TITLES = "rouvideo_tr_titles"
    private const val KEY_DESC = "rouvideo_tr_desc"

    private const val BATCH_ITEMS = 15
    private const val BATCH_CHARS = 500
    private const val MAX_PENDING = 300
    private const val RATE_LIMIT_PAUSE_MS = 10 * 60_000L
    private const val ERROR_PAUSE_MS = 30_000L
    private const val GAP_MS = 400L

    private val HAN = Regex("\\p{IsHan}")

    private val sp get() = Preferences.preferenceSp

    private val enabledState = mutableStateOf(sp.getBoolean(KEY_ENABLED, true))
    private val tagsState = mutableStateOf(sp.getBoolean(KEY_TAGS, true))
    private val titlesState = mutableStateOf(sp.getBoolean(KEY_TITLES, false))
    private val descState = mutableStateOf(sp.getBoolean(KEY_DESC, false))

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

    /** Translation settings are only offered (and applied) when the app language is English. */
    val available: Boolean
        get() = LanguageHelper.preferredLanguage.language.equals("en", ignoreCase = true)

    fun isActive(kind: Kind): Boolean = available && enabled && when (kind) {
        Kind.TAG -> tagsEnabled
        Kind.TITLE -> titlesEnabled
        Kind.DESC -> descriptionsEnabled
    }

    // ---- state -----------------------------------------------------------------------------

    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>()
    private val pending = LinkedHashMap<String, Pair<Kind, String>>()
    private val failed = HashSet<String>()

    /** Bumped on every cache / queue change so composables that read it recompose. */
    private val version = mutableIntStateOf(0)

    @Volatile
    private var loaded = false

    @Volatile
    private var pausedUntil = 0L

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

    // ---- public API ------------------------------------------------------------------------

    fun title(text: String): String =
        if (text.isBlank() || !isActive(Kind.TITLE)) text else translate(Kind.TITLE, text)

    fun description(text: String): String =
        if (text.isBlank() || !isActive(Kind.DESC)) text else translate(Kind.DESC, text)

    /** Cached translation of [source], or [source] itself while it is being fetched. */
    fun translate(kind: Kind, source: String): String {
        version.intValue // subscribe composables to cache updates
        ensureLoaded()
        val k = key(kind, source)
        synchronized(lock) {
            entries[k]?.let { return it.text }
            if (HAN.containsMatchIn(source) && k !in failed) {
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

    /** Seconds until the queue resumes after being rate-limited / offline, or 0. */
    fun pausedForSeconds(): Long {
        version.intValue
        return ((pausedUntil - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
    }

    fun stats(): Stats {
        version.intValue
        ensureLoaded()
        var t = 0; var ti = 0; var d = 0
        synchronized(lock) {
            entries.values.forEach {
                when (it.kind) { Kind.TAG -> t++; Kind.TITLE -> ti++; Kind.DESC -> d++ }
            }
        }
        return Stats(t, ti, d, runCatching { file.length() }.getOrDefault(0L))
    }

    /** Cached translations, newest first, optionally limited to one kind. */
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
        }
        changed()
    }

    fun clear(kind: Kind? = null) {
        synchronized(lock) {
            if (kind == null) {
                entries.clear()
                failed.clear()
            } else {
                entries.entries.removeAll { it.value.kind == kind }
                failed.removeAll { it.startsWith(kind.key + "\u0001") }
            }
        }
        pausedUntil = 0L
        changed()
    }

    // ---- queue -----------------------------------------------------------------------------

    private fun schedule() {
        synchronized(lock) {
            if (worker?.isActive == true) return
            worker = scope.launch {
                delay(300)
                while (true) {
                    val wait = pausedUntil - System.currentTimeMillis()
                    if (wait > 0) delay(wait)
                    if (!processNext()) break
                    delay(GAP_MS)
                }
            }
        }
    }

    /** Translates the next group from the queue. Returns false when the queue is empty. */
    private fun processNext(): Boolean {
        val group = synchronized(lock) {
            // newest first: whatever was drawn last is most likely still on screen
            val candidates = pending.entries.toList().asReversed()
                .filter { isActive(it.value.first) }
            pending.keys.retainAll(candidates.map { it.key }.toSet())
            val first = candidates.firstOrNull() ?: return false
            val picked = mutableListOf(first)
            if (first.value.first != Kind.DESC) {
                var chars = first.value.second.length
                for (c in candidates.drop(1)) {
                    if (picked.size >= BATCH_ITEMS) break
                    if (c.value.first == Kind.DESC) continue
                    val len = c.value.second.length
                    if (chars + len > BATCH_CHARS) continue
                    picked += c; chars += len
                }
            }
            picked.forEach { pending.remove(it.key) }
            picked.map { it.value }
        }
        try {
            val (kind0, source0) = group.first()
            val out = if (kind0 == Kind.DESC) {
                listOf(translateLong(source0))
            } else {
                translateGroup(group.map { it.second.replace('\n', ' ') })
            }
            synchronized(lock) {
                group.forEachIndexed { i, (kind, source) ->
                    val text = out.getOrNull(i).orEmpty().trim()
                    val k = key(kind, source)
                    if (text.isEmpty() || text == source) {
                        failed.add(k)
                    } else {
                        entries.remove(k)
                        entries[k] = Entry(
                            kind, source,
                            if (kind == Kind.TAG) titleCase(text) else text,
                            System.currentTimeMillis(),
                        )
                    }
                }
                Kind.entries.forEach { trim(it) }
            }
            changed()
        } catch (e: RateLimited) {
            requeue(group)
            pausedUntil = System.currentTimeMillis() + RATE_LIMIT_PAUSE_MS
            version.intValue++
        } catch (e: Exception) {
            requeue(group)
            pausedUntil = System.currentTimeMillis() + ERROR_PAUSE_MS
            version.intValue++
        }
        return true
    }

    private fun requeue(group: List<Pair<Kind, String>>) {
        synchronized(lock) {
            group.forEach { (kind, source) ->
                if (pending.size < MAX_PENDING) pending.putIfAbsent(key(kind, source), kind to source)
            }
        }
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

    // ---- Google Translate ------------------------------------------------------------------

    private class RateLimited : java.io.IOException("HTTP 429")

    private fun request(text: String): List<String> {
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

    /** One output per input; if Google merges/splits lines, retry on halves until it lines up. */
    private fun translateGroup(texts: List<String>): List<String> {
        val out = request(texts.joinToString("\n"))
        if (out.size == texts.size) return out
        if (texts.size == 1) return listOf(out.joinToString(" "))
        val mid = texts.size / 2
        return translateGroup(texts.subList(0, mid)) + translateGroup(texts.subList(mid, texts.size))
    }

    /** Long text (descriptions): split into <= ~450 char chunks on line boundaries. */
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

    // ---- persistence -----------------------------------------------------------------------

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
