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
import com.yenaly.han1meviewer.R
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

    suspend fun checkAndGetSubtitle(pageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            if (pageUrl.isBlank()) return@withContext null
            val httpClient = requireClient()

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

            val html = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null
                else response.body.string()
            } ?: return@withContext null

            if (html.isBlank()) return@withContext null

            val document = Jsoup.parse(html)

            for (subSingle in document.select("div.sub-single")) {
                val isEnglish = subSingle.selectFirst(
                    "img[src*=/assets/flags/gb.png], img[src*=/flags/gb.png]"
                ) != null

                if (!isEnglish) continue

                val href = subSingle.selectFirst("a.green-link")?.attr("href")
                    ?: subSingle.selectFirst("a[href$=.srt]")?.attr("href")

                if (href.isNullOrBlank()) {
                    return@withContext ""
                }

                val fullUrl = buildFullUrl(href)

                return@withContext if (
                    fullUrl.endsWith(".srt", ignoreCase = true) ||
                    fullUrl.contains("download", ignoreCase = true)
                ) {
                    fullUrl
                } else {
                    ""
                }
            }

            ""
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout checking subtitle: $pageUrl")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Check error for $pageUrl: ${e.message}")
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
    val context = LocalContext.current

    remember(context) {
        MissAvSubtitleHelper.init(context)
        true
    }

    var searchResults by remember { mutableStateOf<List<SubtitleResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var downloadStates by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var checkingStates by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    var englishUrls by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var downloadedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun searchSubtitles() {
        if (videoCode.isBlank() || isLoading) return

        isLoading = true
        hasSearched = true
        errorMsg = null
        searchResults = emptyList()
        downloadStates = emptyMap()
        checkingStates = emptyMap()
        englishUrls = emptyMap()
        downloadedKeys = emptySet()

        scope.launch {
            try {
                val results = MissAvSubtitleHelper.searchSubtitles(videoCode).take(7)
                searchResults = results
                isLoading = false

                if (results.isEmpty()) {
                    errorMsg = "No subtitles found for this video"
                    return@launch
                }

                results.forEachIndexed { index, result ->
                    if (index >= 3) return@forEachIndexed
                    launch {
                        val key = result.link
                        checkingStates = checkingStates + (key to true)
                        try {
                            val url = MissAvSubtitleHelper.checkAndGetSubtitle(result.link)
                            englishUrls = englishUrls + (key to url)
                        } catch (e: Exception) {
                            Log.e("MissAvSubtitle", "Check failed for $key: ${e.message}")
                            englishUrls = englishUrls + (key to null)
                        } finally {
                            checkingStates = checkingStates + (key to false)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MissAvSubtitle", "Search error: ${e.message}")
                errorMsg = "Failed to search subtitles"
                isLoading = false
            }
        }
    }

    fun download(result: SubtitleResult) {
        val key = result.link
        val url = englishUrls[key]
        if (url.isNullOrEmpty()) return
        if (downloadStates[key] == true) return
        if (key in downloadedKeys) return

        downloadStates = downloadStates + (key to true)
        scope.launch {
            try {
                val ext = url.substringAfterLast('.', "srt").take(5)
                    .lowercase()
                    .let { if (it.matches(Regex("[a-z0-9]{1,5}"))) it else "srt" }
                val fileName = "${videoCode}_subtitle.$ext"

                val uri = MissAvSubtitleHelper.downloadSubtitle(context, url, fileName)
                if (uri != null) {
                    downloadedKeys = downloadedKeys + key
                    onSubtitleDownloaded(uri)
                    snackbarHostState.showSnackbar("Subtitle downloaded successfully!")
                } else {
                    snackbarHostState.showSnackbar("Failed to download subtitle")
                }
            } catch (e: Exception) {
                Log.e("MissAvSubtitle", "Download error: ${e.message}")
                snackbarHostState.showSnackbar("Error: ${e.message}")
            } finally {
                downloadStates = downloadStates + (key to false)
            }
        }
    }

    fun checkSingle(result: SubtitleResult) {
        val key = result.link
        if (checkingStates[key] == true) return
        checkingStates = checkingStates + (key to true)
        scope.launch {
            try {
                val url = MissAvSubtitleHelper.checkAndGetSubtitle(result.link)
                englishUrls = englishUrls + (key to url)
            } catch (e: Exception) {
                Log.e("MissAvSubtitle", "Check failed: ${e.message}")
                englishUrls = englishUrls + (key to null)
            } finally {
                checkingStates = checkingStates + (key to false)
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Subtitles (subtitlecat.com)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Surface(
                    modifier = Modifier
                        .clickable { if (!isLoading) searchSubtitles() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_download_24),
                                contentDescription = "Search",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Text(
                            text = if (isLoading) "Searching…" else "Search",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

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
                                isChecking = checkingStates[key] == true,
                                isDownloading = downloadStates[key] == true,
                                isAlreadyDownloaded = key in downloadedKeys,
                                hasEnglish = englishUrls[key],
                                onCheck = { checkSingle(result) },
                                onDownload = { download(result) },
                            )
                        }
                    }
                }

                !hasSearched -> {
                    Text(
                        text = "Tap Search to find subtitles",
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
fun SubtitleResultItem(
    result: SubtitleResult,
    index: Int,
    isChecking: Boolean,
    isDownloading: Boolean,
    isAlreadyDownloaded: Boolean,
    hasEnglish: String?,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                isAlreadyDownloaded -> {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ) {
                        Text(
                            text = "Downloaded",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(
                                horizontal = 8.dp,
                                vertical = 4.dp,
                            ),
                        )
                    }
                }

                isChecking -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                hasEnglish == null -> {
                    ActionSurface(
                        text = "Check",
                        onClick = onCheck,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }

                hasEnglish.isEmpty() -> {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Text(
                            text = "No English",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(
                                horizontal = 8.dp,
                                vertical = 4.dp,
                            ),
                        )
                    }
                }

                else -> {
                    ActionSurface(
                        text = "Download",
                        onClick = onDownload,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
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
