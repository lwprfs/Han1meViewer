package com.yenaly.han1meviewer.MissAV.ui.video

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import com.yenaly.han1meviewer.MissAV.ui.home.MissAvSubtitleSettingsRepo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicInteger
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.SiteVideoSectionHeader
import com.yenaly.han1meviewer.USER_AGENT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SubtitleResult(
    val title: String,
    val link: String,
    val size: String = "N/A",
    val downloads: String = "N/A",
    val languages: String = "N/A",
)

sealed class SubtitleLangStatus {
    data class Available(val url: String) : SubtitleLangStatus()
    data class Translatable(val folder: String, val origFile: String) : SubtitleLangStatus()
    data object Missing : SubtitleLangStatus()
}

object MissAvSubtitleHelper {
    private const val SUBTITLE_CAT_BASE = "https://www.subtitlecat.com"
    private const val TAG = "SubtitleHelper"

    @Volatile
    private var client: OkHttpClient? = null

    fun init(context: Context) {
        if (client != null) return
        synchronized(this) {
            if (client != null) return
            val cacheDir = File(context.cacheDir, "subtitle_http_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            client = OkHttpClient.Builder()
                .cache(Cache(cacheDir, 10L * 1024 * 1024))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }

    private fun requireClient(): OkHttpClient {
        return client ?: synchronized(this) {
            client ?: throw IllegalStateException(
                "MissAvSubtitleHelper.init(context) must be called first"
            )
        }
    }

    suspend fun searchSubtitles(query: String): List<SubtitleResult> = withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) return@withContext emptyList()

            val searchTerms = listOf(
                query,
                query.replace(Regex("""\s+"""), ""),
                query.substringBefore(" ").take(30),
            ).distinct().filter { it.length >= 3 }

            val allResults = mutableListOf<SubtitleResult>()
            val httpClient = requireClient()

            for (searchTerm in searchTerms) {
                try {
                    val encodedQuery = URLEncoder.encode(searchTerm, "UTF-8")
                    val url = "$SUBTITLE_CAT_BASE/index.php?search=$encodedQuery"

                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .header(
                            "Accept",
                            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                        )
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .build()

                    val html = httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) null
                        else response.body.string()
                    } ?: continue

                    val document = Jsoup.parse(html)

                    val rows = document.select("table.sub-table tbody tr")
                    Log.d(TAG, "Search '$searchTerm': found ${rows.size} rows")

                    for (row in rows) {
                        runCatching {

                            val titleElement = row.selectFirst("td > a")
                                ?: return@runCatching

                            val title = titleElement.text().trim()
                            if (title.isBlank()) return@runCatching

                            val href = titleElement.attr("href")
                            if (href.isBlank()) return@runCatching

                            val fullLink = buildFullUrl(href)

                            val metricCells = row.select("td.sub-table__metric")

                            val size = metricCells.getOrNull(0)
                                ?.selectFirst(".sub-table__metric-value")
                                ?.text()?.trim() ?: "N/A"

                            val downloads = metricCells.getOrNull(1)
                                ?.selectFirst(".sub-table__metric-value")
                                ?.text()?.trim() ?: "N/A"

                            val languages = metricCells.getOrNull(2)
                                ?.selectFirst(".sub-table__metric-value")
                                ?.text()?.trim() ?: "N/A"

                            val result = SubtitleResult(
                                title = title,
                                link = fullLink,
                                size = size,
                                downloads = downloads,
                                languages = languages,
                            )

                            if (allResults.none { it.link == result.link }) {
                                allResults.add(result)
                            }
                        }.onFailure { e ->
                            Log.w(TAG, "Failed parsing a search row: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Search term '$searchTerm' failed: ${e.message}")
                }
            }

            allResults.distinctBy { it.link }.take(7)
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout searching subtitles")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error searching subtitles: ${e.message}")
            emptyList()
        }
    }

    suspend fun checkLanguage(pageUrl: String, langCode: String): SubtitleLangStatus? =
        withContext(Dispatchers.IO) {
            try {
                if (pageUrl.isBlank()) return@withContext null
                val request = Request.Builder()
                    .url(pageUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", SUBTITLE_CAT_BASE)
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    )
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()

                val html = requireClient().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) null else response.body.string()
                } ?: return@withContext null
                if (html.isBlank()) return@withContext null

                val document = Jsoup.parse(html)
                val translateRegex = Regex(
                    """translate_from_server_folder\('([^']*)',\s*'([^']*)',\s*'([^']*)'\)""",
                )

                for (subSingle in document.select("div.sub-single")) {
                    val code = subSingle.selectFirst("img.flag")?.attr("alt")?.trim().orEmpty()
                    if (!code.equals(langCode, ignoreCase = true)) continue

                    val href = subSingle.selectFirst("a.green-link")?.attr("href")
                        ?: subSingle.selectFirst("a[href\$=.srt]")?.attr("href")
                    if (!href.isNullOrBlank()) {
                        return@withContext SubtitleLangStatus.Available(buildFullUrl(href))
                    }

                    val onclick = subSingle.selectFirst("button[onclick*=translate_from_server_folder]")
                        ?.attr("onclick").orEmpty()
                    val match = translateRegex.find(onclick)
                    if (match != null) {
                        return@withContext SubtitleLangStatus.Translatable(
                            folder = match.groupValues[3],
                            origFile = match.groupValues[2],
                        )
                    }
                    return@withContext SubtitleLangStatus.Missing
                }
                SubtitleLangStatus.Missing
            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "Timeout checking subtitle: $pageUrl")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Check error for $pageUrl: ${e.message}")
                null
            }
        }

    private data class Cue(val timing: String, val lines: List<String>)

    private fun parseSrt(text: String): List<Cue> {
        val clean = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val cues = mutableListOf<Cue>()
        for (block in clean.split(Regex("\n{2,}"))) {
            val lines = block.split('\n')
            val ti = lines.indexOfFirst { it.contains("-->") }
            if (ti < 0) continue
            cues += Cue(lines[ti].trim(), lines.drop(ti + 1).filter { it.isNotBlank() })
        }
        return cues
    }

    private fun googleTranslate(text: String, target: String): String {
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto" +
            "&tl=${URLEncoder.encode(target, "UTF-8")}&dt=t&q=${URLEncoder.encode(text, "UTF-8")}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        val body = requireClient().newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("Translate HTTP ${r.code}")
            r.body.string()
        }
        val segments = JSONArray(body).getJSONArray(0)
        val sb = StringBuilder()
        for (i in 0 until segments.length()) {
            val seg = segments.optJSONArray(i) ?: continue
            if (!seg.isNull(0)) sb.append(seg.getString(0))
        }
        return sb.toString()
    }

    private fun translateLines(lines: List<String>, target: String): List<String> {
        if (lines.isEmpty()) return lines
        val out = googleTranslate(lines.joinToString("\n"), target)
            .replace("\r\n", "\n").trimEnd('\n').split('\n')
        return if (out.size == lines.size) out else emptyList()
    }

    suspend fun translateSubtitle(
        context: Context,
        status: SubtitleLangStatus.Translatable,
        targetLang: String,
        fileName: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val srtUrl = buildFullUrl(status.folder.trimEnd('/') + "/" + status.origFile)
            val request = Request.Builder()
                .url(srtUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", SUBTITLE_CAT_BASE)
                .build()
            val bytes = requireClient().newCall(request).execute().use { r ->
                if (r.isSuccessful) r.body.bytes() else null
            } ?: return@withContext null

            val cues = parseSrt(String(bytes, Charsets.UTF_8))
            if (cues.isEmpty()) return@withContext null

            val chunks = mutableListOf<List<Int>>()
            var current = mutableListOf<Int>()
            var chars = 0
            cues.forEachIndexed { i, cue ->
                val len = cue.lines.sumOf { it.length + 1 }
                if (current.isNotEmpty() && (chars + len > 1500 || current.size >= 40)) {
                    chunks += current; current = mutableListOf(); chars = 0
                }
                current += i; chars += len
            }
            if (current.isNotEmpty()) chunks += current

            val translated = arrayOfNulls<List<String>>(cues.size)
            val done = AtomicInteger(0)
            val failed = AtomicInteger(0)
            val gate = Semaphore(4)
            onProgress(0, chunks.size)

            coroutineScope {
                chunks.map { idx ->
                    async {
                        gate.withPermit {
                            var ok = false
                            repeat(2) { attempt ->
                                if (ok) return@repeat
                                try {
                                    val src = idx.flatMap { cues[it].lines }
                                    val out = translateLines(src, targetLang)
                                    if (out.isNotEmpty() || src.isEmpty()) {
                                        var p = 0
                                        for (i in idx) {
                                            val n = cues[i].lines.size
                                            translated[i] = out.subList(p, p + n); p += n
                                        }
                                    } else {

                                        for (i in idx) {
                                            val one = cues[i].lines
                                            val r = translateLines(one, targetLang)
                                            translated[i] = if (r.isNotEmpty() || one.isEmpty()) r
                                            else listOf(
                                                googleTranslate(one.joinToString(" "), targetLang)
                                                    .replace("\n", " ").trim(),
                                            )
                                        }
                                    }
                                    ok = true
                                } catch (e: Exception) {
                                    Log.w(TAG, "Translate chunk failed (try $attempt): ${e.message}")
                                }
                            }
                            if (!ok) failed.incrementAndGet()
                            onProgress(done.incrementAndGet(), chunks.size)
                        }
                    }
                }.awaitAll()
            }

            if (failed.get() == chunks.size) return@withContext null

            val sb = StringBuilder()
            cues.forEachIndexed { i, cue ->
                sb.append(i + 1).append('\n').append(cue.timing).append('\n')
                (translated[i] ?: cue.lines).forEach { sb.append(it).append('\n') }
                sb.append('\n')
            }

            val dir = File(context.getExternalFilesDir("subtitles"), "missav")
            if (!dir.exists() && !dir.mkdirs()) return@withContext null
            val file = File(dir, fileName.replace(Regex("""[\\/:*?"<>|]"""), "_"))
            file.writeText(sb.toString(), Charsets.UTF_8)
            if (file.length() == 0L) return@withContext null
            FileProvider.getUriForFile(context, "${context.packageName}.fileProvider", file)
        } catch (e: Exception) {
            Log.e(TAG, "Translate error: ${e.message}")
            null
        }
    }

    suspend fun downloadSubtitle(
        context: Context,
        url: String,
        fileName: String,
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            if (url.isBlank()) return@withContext null
            val httpClient = requireClient()

            val safeFileName = fileName
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
                .take(100)
                .ifBlank { "subtitle.srt" }

            val dir = File(context.getExternalFilesDir("subtitles"), "missav")
            if (!dir.exists() && !dir.mkdirs()) {
                Log.e(TAG, "Failed to create subtitle directory: ${dir.absolutePath}")
                return@withContext null
            }

            val file = File(dir, safeFileName)

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", SUBTITLE_CAT_BASE)
                .build()

            val bytes = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null
                else response.body.bytes()
            } ?: return@withContext null

            if (bytes.isEmpty()) return@withContext null

            FileOutputStream(file).use { it.write(bytes) }

            if (!file.exists() || file.length() == 0L) return@withContext null

            val authority = "${context.packageName}.fileProvider"
            return@withContext try {
                FileProvider.getUriForFile(context, authority, file)
            } catch (e: Exception) {
                Log.e(TAG, "FileProvider error: ${e.message}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}")
            null
        }
    }

    private const val SUBDOG_BASE = "https://api.sub-dog.top"

    suspend fun subDogExists(videoCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val id = videoCode.trim().uppercase()
            if (id.isBlank()) return@withContext false
            val request = Request.Builder()
                .url("$SUBDOG_BASE/api/subtitles/exists/${Uri.encode(id)}")
                .header("User-Agent", USER_AGENT)
                .build()
            val body = requireClient().newCall(request).execute().use { r ->
                if (r.isSuccessful) r.body.string() else null
            } ?: return@withContext false
            org.json.JSONObject(body).optBoolean("exists", false)
        } catch (e: Exception) {
            Log.w(TAG, "Sub-Dog exists check failed: ${e.message}")
            false
        }
    }

    suspend fun downloadSubDogSubtitle(
        context: Context,
        videoCode: String,
        fileName: String,
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val id = videoCode.trim().uppercase()
            if (id.isBlank()) return@withContext null
            val request = Request.Builder()
                .url("$SUBDOG_BASE/api/subtitles/${Uri.encode(id)}")
                .header("User-Agent", USER_AGENT)
                .build()
            val raw = requireClient().newCall(request).execute().use { r ->
                if (r.isSuccessful) r.body.string() else null
            } ?: return@withContext null
            if (!raw.contains("-->")) return@withContext null

            val srt = vttToSrt(raw)
            val dir = File(context.getExternalFilesDir("subtitles"), "missav")
            if (!dir.exists() && !dir.mkdirs()) return@withContext null
            val file = File(dir, fileName.replace(Regex("""[\\/:*?"<>|]"""), "_"))
            file.writeText(srt, Charsets.UTF_8)
            if (file.length() == 0L) return@withContext null
            FileProvider.getUriForFile(context, "${context.packageName}.fileProvider", file)
        } catch (e: Exception) {
            Log.e(TAG, "Sub-Dog download error: ${e.message}")
            null
        }
    }

    private fun vttToSrt(text: String): String {
        if (!text.trimStart().startsWith("WEBVTT")) return text
        val timing = Regex("""(?m)^(?:(\d{2}):)?(\d{2}):(\d{2})\.(\d{3})\s*-->\s*(?:(\d{2}):)?(\d{2}):(\d{2})\.(\d{3}).*$""")
        return text.replace(Regex("""(?m)^WEBVTT.*$"""), "")
            .replace(timing) { m ->
                val g = m.groupValues
                "${g[1].ifEmpty { "00" }}:${g[2]}:${g[3]},${g[4]} --> " +
                    "${g[5].ifEmpty { "00" }}:${g[6]}:${g[7]},${g[8]}"
            }
            .trim()
    }

    private fun buildFullUrl(href: String): String = when {
        href.startsWith("http://") || href.startsWith("https://") -> href
        href.startsWith("/") -> "$SUBTITLE_CAT_BASE$href"
        else -> "$SUBTITLE_CAT_BASE/$href"
    }
}

@Composable
fun MissAvSubtitleSection(
    videoCode: String,
    onSubtitleDownloaded: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (Preferences.missAvSubtitleSource == "subdog") {
        MissAvSubDogSubtitleSection(videoCode, onSubtitleDownloaded, modifier)
    } else {
        MissAvSubtitleCatSection(videoCode, onSubtitleDownloaded, modifier)
    }
}

@Composable
private fun MissAvSubDogSubtitleSection(
    videoCode: String,
    onSubtitleDownloaded: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    remember(context) {
        MissAvSubtitleHelper.init(context)
        true
    }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun fetch() {
        if (busy || videoCode.isBlank()) return
        busy = true
        isError = false
        status = "Looking for a Sub-Dog subtitle…"
        scope.launch {
            try {
                if (!MissAvSubtitleHelper.subDogExists(videoCode)) {
                    isError = true
                    status = "Sub-Dog has no subtitle for this video"
                    return@launch
                }
                val uri = MissAvSubtitleHelper.downloadSubDogSubtitle(
                    context, videoCode, "${videoCode}_subdog.srt",
                )
                if (uri != null) {
                    onSubtitleDownloaded(uri)
                    status = "Chinese subtitle loaded"
                } else {
                    isError = true
                    status = "Could not download the Sub-Dog subtitle"
                }
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SiteVideoSectionHeader(
            title = "Subtitles (Sub-Dog, Chinese)",
            actionText = if (busy) "Working…" else "Get subtitle",
            onActionClick = { fetch() },
        )
        Text(
            text = status ?: "Tap Get subtitle to load the Sub-Dog subtitle",
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun MissAvSubtitleCatSection(
    videoCode: String,
    onSubtitleDownloaded: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    remember(context) {
        MissAvSubtitleHelper.init(context)
        true
    }

    val langCode = Preferences.missAvSubtitleCatLanguage
    val langName = MissAvSubtitleSettingsRepo.languageName(langCode)
    val translateEnabled = Preferences.missAvSubtitleCatTranslate

    var manualQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SubtitleResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var downloadStates by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var checkingStates by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var progressStates by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    var statuses by remember { mutableStateOf<Map<String, SubtitleLangStatus?>>(emptyMap()) }
    var downloadedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun checkSingle(result: SubtitleResult) {
        val key = result.link
        if (checkingStates[key] == true) return
        checkingStates = checkingStates + (key to true)
        scope.launch {
            try {
                statuses = statuses + (key to MissAvSubtitleHelper.checkLanguage(key, langCode))
            } catch (e: Exception) {
                Log.e("MissAvSubtitle", "Check failed for $key: ${e.message}")
                statuses = statuses + (key to null)
            } finally {
                checkingStates = checkingStates + (key to false)
            }
        }
    }

    fun searchSubtitles() {
        val query = manualQuery.trim().ifBlank { videoCode }
        if (query.isBlank() || isLoading) return
        focusManager.clearFocus()

        isLoading = true
        hasSearched = true
        errorMsg = null
        searchResults = emptyList()
        downloadStates = emptyMap()
        checkingStates = emptyMap()
        progressStates = emptyMap()
        statuses = emptyMap()
        downloadedKeys = emptySet()

        scope.launch {
            try {
                val results = MissAvSubtitleHelper.searchSubtitles(query).take(7)
                searchResults = results
                isLoading = false

                if (results.isEmpty()) {
                    errorMsg = "No subtitles found for \"$query\""
                    return@launch
                }
                results.take(3).forEach { checkSingle(it) }
            } catch (e: Exception) {
                Log.e("MissAvSubtitle", "Search error: ${e.message}")
                errorMsg = "Failed to search subtitles"
                isLoading = false
            }
        }
    }

    fun download(result: SubtitleResult) {
        val key = result.link
        val status = statuses[key] ?: return
        if (downloadStates[key] == true || key in downloadedKeys) return

        downloadStates = downloadStates + (key to true)
        scope.launch {
            try {
                val fileName = "${videoCode}_subtitle_$langCode.srt"
                val uri = when (status) {
                    is SubtitleLangStatus.Available ->
                        MissAvSubtitleHelper.downloadSubtitle(context, status.url, fileName)

                    is SubtitleLangStatus.Translatable -> {
                        if (!translateEnabled) null
                        else MissAvSubtitleHelper.translateSubtitle(
                            context, status, langCode, fileName,
                        ) { done, total ->
                            progressStates = progressStates +
                                (key to (done * 100 / total.coerceAtLeast(1)))
                        }
                    }

                    SubtitleLangStatus.Missing -> null
                }
                if (uri != null) {
                    downloadedKeys = downloadedKeys + key
                    onSubtitleDownloaded(uri)
                    snackbarHostState.showSnackbar(
                        if (status is SubtitleLangStatus.Translatable)
                            "$langName subtitle translated and loaded"
                        else "Subtitle downloaded successfully!",
                    )
                } else {
                    snackbarHostState.showSnackbar(
                        if (status is SubtitleLangStatus.Translatable)
                            "Translation failed"
                        else "Failed to download subtitle",
                    )
                }
            } catch (e: Exception) {
                Log.e("MissAvSubtitle", "Download error: ${e.message}")
                snackbarHostState.showSnackbar("Error: ${e.message}")
            } finally {
                downloadStates = downloadStates + (key to false)
                progressStates = progressStates - key
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SiteVideoSectionHeader(
                title = "Subtitles (subtitlecat.com · $langName)",
                actionText = if (isLoading) "Searching…" else "Search",
                onActionClick = { if (!isLoading) searchSubtitles() },
            )

            SubtitleSearchBar(
                query = manualQuery,
                onQueryChange = { manualQuery = it },
                onSearch = { searchSubtitles() },
            )

            when {
                isLoading && searchResults.isEmpty() -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "Searching for subtitles…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                errorMsg != null -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_error_outline_24),
                            contentDescription = "Error",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = errorMsg.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                searchResults.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(
                            items = searchResults,
                            key = { _, r -> r.link },
                        ) { index, result ->
                            val key = result.link
                            SubtitleResultItem(
                                result = result,
                                index = index,
                                languageName = langName,
                                translateEnabled = translateEnabled,
                                isChecking = checkingStates[key] == true,
                                isDownloading = downloadStates[key] == true,
                                isAlreadyDownloaded = key in downloadedKeys,
                                progress = progressStates[key] ?: -1,
                                status = statuses[key],
                                onCheck = { checkSingle(result) },
                                onDownload = { download(result) },
                            )
                        }
                    }
                }

                !hasSearched -> {
                    Text(
                        text = "Tap Search to find $langName subtitles, or type a title above",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp),
        )
    }
}

@Composable
private fun SubtitleSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search subtitles manually…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            IconButton(onClick = onSearch, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun SubtitleResultItem(
    result: SubtitleResult,
    index: Int,
    languageName: String,
    translateEnabled: Boolean,
    isChecking: Boolean,
    isDownloading: Boolean,
    isAlreadyDownloaded: Boolean,
    progress: Int,
    status: SubtitleLangStatus?,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "${index + 1}. ${result.title}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = result.size,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = result.downloads,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (result.languages != "N/A" && result.languages != "Unknown") {
                        Text(
                            text = "• ${result.languages}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            when {
                isDownloading -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (progress >= 0) {
                            Text(
                                text = "Translating $progress%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                isAlreadyDownloaded -> StatusBadge(
                    text = "Loaded",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )

                isChecking -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                status == null -> {
                    ActionSurface(
                        text = "Check",
                        onClick = onCheck,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }

                status is SubtitleLangStatus.Available -> {
                    ActionSurface(
                        text = "Download",
                        onClick = onDownload,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                status is SubtitleLangStatus.Translatable && translateEnabled -> {
                    ActionSurface(
                        text = "Translate",
                        onClick = onDownload,
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    )
                }

                else -> StatusBadge(
                    text = "No $languageName",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, containerColor: Color, contentColor: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ActionSurface(
    text: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_download_24),
                contentDescription = text,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
