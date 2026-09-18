package com.yenaly.han1meviewer.MissAV

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.USER_AGENT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val size: String,
    val downloads: String,
    val languages: String,
)

object MissAvSubtitleHelper {
    private const val SUBTITLE_CAT_BASE = "https://www.subtitlecat.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun searchSubtitles(query: String): List<SubtitleResult> = withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) return@withContext emptyList()

            val searchTerms = listOf(
                query,
                query.replace(Regex("""\s+"""), ""),
                query.takeWhile { it.isDigit() },
            ).distinct()

            val allResults = mutableListOf<SubtitleResult>()

            for (searchTerm in searchTerms) {
                if (searchTerm.length < 3) continue

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
                    .header("Cache-Control", "no-cache")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) continue

                val html = response.body.string()
                val document = Jsoup.parse(html)

                for (row in document.select(
                    "table.sub-table tbody tr, div.subtitle-item, div.result-item"
                )) {
                    runCatching {
                        val titleElement = row.selectFirst(
                            "td a, div.title a, a.subtitle-link"
                        ) ?: return@runCatching
                        val title = titleElement.text()
                        if (title.isBlank()) return@runCatching

                        val link = titleElement.attr("href")
                        val fullLink = when {
                            link.startsWith("http") -> link
                            link.startsWith("/") -> "$SUBTITLE_CAT_BASE$link"
                            else -> "$SUBTITLE_CAT_BASE/$link"
                        }

                        val size = row
                            .selectFirst("td.sub-table__size-cell, .size-cell, .file-size")
                            ?.text()
                            ?.trim()
                            ?: "Unknown"

                        val allCells = row.select("td")
                        val downloads = if (allCells.size > 3) allCells[3].text() else "Unknown"
                        val languages = if (allCells.size > 4) allCells[4].text() else "Unknown"

                        val result = SubtitleResult(title, fullLink, size, downloads, languages)
                        if (allResults.none { it.link == result.link }) {
                            allResults.add(result)
                        }
                    }
                }
            }

            allResults.distinctBy { it.link }.take(7)
        } catch (e: SocketTimeoutException) {
            Log.e("SubtitleHelper", "Timeout searching subtitles")
            emptyList()
        } catch (e: Exception) {
            Log.e("SubtitleHelper", "Error searching subtitles: ${e.message}")
            emptyList()
        }
    }

    suspend fun checkAndGetSubtitle(pageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            if (pageUrl.isBlank()) return@withContext null

            val request = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", SUBTITLE_CAT_BASE)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val html = response.body.string()
            val document = Jsoup.parse(html)

            for (subSingle in document.select(
                "div.sub-single, .download-section, .subtitle-download"
            )) {

                subSingle.selectFirst(
                    "img[src*=/assets/flags/gb.png], img[src*=/flags/gb.png]"
                ) ?: continue

                val href = subSingle.selectFirst(
                    "a.green-link, a.download-link, a[href$=.srt]"
                )?.attr("href")
                if (href.isNullOrEmpty()) continue

                val fullUrl = when {
                    href.startsWith("http") -> href
                    href.startsWith("/") -> "$SUBTITLE_CAT_BASE$href"
                    else -> "$SUBTITLE_CAT_BASE/$href"
                }
                if (fullUrl.endsWith(".srt", ignoreCase = true) ||
                    fullUrl.contains("download")
                ) {
                    return@withContext fullUrl
                }
            }
            null
        } catch (e: Exception) {
            Log.e("SubtitleHelper", "Check error: ${e.message}")
            null
        }
    }

    suspend fun downloadSubtitle(context: Context, url: String, fileName: String): Uri? =
        withContext(Dispatchers.IO) {
            try {
                if (url.isBlank()) return@withContext null

                val dir = File(context.getExternalFilesDir("subtitles"), "missav")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", SUBTITLE_CAT_BASE)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null

                val bytes = response.body.bytes()
                if (bytes.isEmpty()) return@withContext null

                FileOutputStream(file).use { it.write(bytes) }

                if (file.exists() && file.length() > 0) Uri.fromFile(file) else null
            } catch (e: Exception) {
                Log.e("SubtitleHelper", "Download error: ${e.message}")
                null
            }
        }
}

@Composable
fun MissAvSubtitleSection(
    videoCode: String,
    onSubtitleDownloaded: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var searchResults by remember { mutableStateOf<List<SubtitleResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var downloadStates by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var checkingStates by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    var englishUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

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

                results.forEach { result ->
                    launch {
                        val key = result.link
                        checkingStates = checkingStates + (key to true)
                        val url = MissAvSubtitleHelper
                            .checkAndGetSubtitle(result.link)
                            .orEmpty()
                        englishUrls = englishUrls + (key to url)
                        checkingStates = checkingStates + (key to false)
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
        val url = englishUrls[key].orEmpty()
        if (url.isEmpty()) return
        if (downloadStates[key] == true) return
        if (key in downloadedKeys) return

        downloadStates = downloadStates + (key to true)
        scope.launch {
            try {
                val fileName = "${videoCode}_subtitle.srt"
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
                val url = MissAvSubtitleHelper
                    .checkAndGetSubtitle(result.link)
                    .orEmpty()
                englishUrls = englishUrls + (key to url)
            } finally {
                checkingStates = checkingStates + (key to false)
            }
        }
    }

    Column(
        modifier = modifier
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
                text = "Subtitles",
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
            isLoading -> {
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(vertical = 8.dp),
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
                    if (result.languages != "Unknown") {
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
