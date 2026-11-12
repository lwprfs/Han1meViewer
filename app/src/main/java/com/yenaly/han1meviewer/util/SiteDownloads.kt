package com.yenaly.han1meviewer.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetwork
import com.yenaly.han1meviewer.HFileManager
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirCookieManager
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetwork
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.logic.SiteCheckIn
import com.yenaly.han1meviewer.logic.network.ServiceCreator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

object DownloadLayout {

    private const val FALLBACK = "untitled"

    fun sanitize(name: String?, fallback: String = FALLBACK): String {
        val cleaned = HFileManager.illegalCharsRegex.replace(name.orEmpty(), "_")
            .trim().trim('.').replace(Regex("\\s+"), " ")
            .take(80)
        return cleaned.ifBlank { fallback }
    }

    fun siteFolderName(site: SiteType): String? = when (site) {
        SiteType.HANIME, SiteType.JAVCHU -> null
        SiteType.MISSAV -> "MissAV"
        SiteType.HENTAIMAMA -> "HentaiMama"
        SiteType.ONAIR -> "OnAir"
        SiteType.FAPFOLDER -> "FapFolder"
        SiteType.SUPJAV -> "SupJav"
        SiteType.ROUVIDEO -> "RouVideo"
        SiteType.XHAMSTER -> "xHamster"
    }

    fun segmentsFor(videoCode: String): List<String> {
        val site = SiteCheckIn.siteOfDishCode(videoCode)
            ?: return listOf(HFileManager.HANIME_DOWNLOAD_FOLDER, videoCode)
        val siteFolder = siteFolderName(site)
            ?: return listOf(HFileManager.HANIME_DOWNLOAD_FOLDER, videoCode)
        val rest = SiteCheckIn.rawCodeOfDishCode(videoCode)
            .split('/')
            .filter { it.isNotBlank() }
            .map { sanitize(it) }
        return listOf(siteFolder) + rest.ifEmpty { listOf(FALLBACK) }
    }

    fun fapFolderVideoCode(ownerKind: FapOwnerKind?, ownerName: String?, postId: Long): String {
        val owner = ownerSegments(ownerKind, ownerName)
        return (owner + listOf("Videos", postId.toString())).joinToString("/")
    }

    fun fapFolderPhotoSegments(
        ownerKind: FapOwnerKind?,
        ownerName: String?,
        albumName: String?,
    ): List<String> {
        val base = listOf("FapFolder") + ownerSegments(ownerKind, ownerName) + "Photos"
        return if (albumName.isNullOrBlank()) base else base + sanitize(albumName)
    }

    private fun ownerSegments(kind: FapOwnerKind?, name: String?): List<String> = when {
        kind == null || name.isNullOrBlank() -> listOf("Other")
        kind == FapOwnerKind.GROUP -> listOf("Groups", sanitize(name))
        else -> listOf("Profiles", sanitize(name))
    }
}

enum class FapOwnerKind { GROUP, PROFILE }

object FapFolderImageLocation {
    private const val KEY = "fapfolder_image_download_uri"

    @Volatile
    var confirmedThisSession = false

    var uri: String?
        get() = Preferences.preferenceSp.getString(KEY, null)?.takeIf { it.isNotBlank() }
        set(value) {
            Preferences.preferenceSp.edit().apply {
                if (value.isNullOrBlank()) remove(KEY) else putString(KEY, value)
            }.apply()
        }

    fun isReady(context: Context): Boolean {
        val tree = uri?.let { runCatching { it.toUri() }.getOrNull() } ?: return false
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == tree && it.isWritePermission
        }
    }

    fun persist(context: Context, tree: android.net.Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                tree,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        uri = tree.toString()
    }

    fun displayName(context: Context): String {
        val tree = uri?.let { runCatching { it.toUri() }.getOrNull() } ?: return "Not set"
        return runCatching { DocumentFile.fromTreeUri(context, tree)?.name }.getOrNull()
            ?: tree.lastPathSegment
            ?: tree.toString()
    }
}

enum class DownloadFailureKind { TOR, COOKIES, NETWORK, STORAGE, EXPIRED, OTHER }

data class DownloadFailure(val kind: DownloadFailureKind, val message: String)

object DownloadFailureStore {
    private const val PREFS = "download_failures"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun put(context: Context, videoCode: String, failure: DownloadFailure) {
        prefs(context).edit()
            .putString(videoCode, failure.kind.name + "\u001E" + failure.message)
            .apply()
    }

    fun get(context: Context, videoCode: String): DownloadFailure? {
        val raw = prefs(context).getString(videoCode, null) ?: return null
        val kind = runCatching {
            DownloadFailureKind.valueOf(raw.substringBefore('\u001E'))
        }.getOrDefault(DownloadFailureKind.OTHER)
        return DownloadFailure(kind, raw.substringAfter('\u001E', ""))
    }

    fun clear(context: Context, videoCode: String) {
        prefs(context).edit().remove(videoCode).apply()
    }
}

object SiteDownloadClients {

    fun clientFor(videoCode: String): OkHttpClient =
        when (SiteCheckIn.siteOfDishCode(videoCode)) {
            SiteType.ONAIR -> runCatching { OnAirNetwork.sharedOkHttpClient }
                .getOrDefault(ServiceCreator.downloadClient)
            SiteType.HENTAIMAMA -> runCatching { HentaiMamaNetwork.sharedOkHttpClient }
                .getOrDefault(ServiceCreator.downloadClient)
            SiteType.FAPFOLDER -> runCatching { FapFolderNetwork.playbackOkHttpClient }
                .getOrDefault(ServiceCreator.downloadClient)
            SiteType.MISSAV -> runCatching { com.yenaly.han1meviewer.MissAV.data.remote.MissAvNetwork.downloadOkHttpClient }
                .getOrDefault(ServiceCreator.downloadClient)
            SiteType.SUPJAV -> runCatching { com.yenaly.han1meviewer.SupJav.data.remote.SupJavNetwork.sharedOkHttpClient }
                .getOrDefault(ServiceCreator.downloadClient)

            SiteType.ROUVIDEO -> runCatching { com.yenaly.han1meviewer.RouVideo.data.remote.RouVideoNetwork.streamClient }
                .getOrDefault(ServiceCreator.downloadClient)

            SiteType.XHAMSTER -> runCatching { com.yenaly.han1meviewer.XHamster.data.remote.XHamsterNetwork.streamClient }
                .getOrDefault(ServiceCreator.downloadClient)
            else -> ServiceCreator.downloadClient
        }

    fun useUrlConnectionFallback(videoCode: String): Boolean =
        SiteCheckIn.siteOfDishCode(videoCode) == SiteType.MISSAV

    fun headersFor(videoCode: String, url: String): Map<String, String> {
        val host = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
        return when (SiteCheckIn.siteOfDishCode(videoCode)) {
            SiteType.MISSAV -> {
                val base = Preferences.missAvBaseUrl.trimEnd('/')
                mapOf(
                    "Referer" to "$base/",
                    "Origin" to base,
                    "Accept" to "*/*",
                    "Accept-Language" to "en-US,en;q=0.9",
                )
            }
            SiteType.HENTAIMAMA -> {
                val base = runCatching { HentaiMamaNetwork.baseUrl.trimEnd('/') }.getOrDefault("")
                if (base.isBlank()) emptyMap() else mapOf("Referer" to "$base/", "Origin" to base)
            }
            SiteType.ONAIR -> {
                val base = runCatching { OnAirNetwork.baseUrl.trimEnd('/') }.getOrDefault("")
                buildMap {
                    if (base.isNotBlank()) {
                        put("Referer", "$base/")
                        put("Origin", base)
                        val baseHost = runCatching { java.net.URI(base).host }.getOrNull()
                        val cookie = runCatching { OnAirCookieManager.cookiesRaw }.getOrDefault("")
                        if (cookie.isNotBlank() && baseHost != null && baseHost == host) {
                            put("Cookie", cookie)
                        }
                    }
                }
            }
            SiteType.SUPJAV -> {
                val referer = com.yenaly.han1meviewer.SupJav.data.remote.SupJavNetwork
                    .streamRefererFor(SiteCheckIn.rawCodeOfDishCode(videoCode))
                mapOf("Referer" to referer, "Origin" to referer.trimEnd('/'))
            }
            SiteType.ROUVIDEO -> mapOf(
                "Referer" to com.yenaly.han1meviewer.RouVideo.common.RouVideoConstants.REFERER,
                "Origin" to com.yenaly.han1meviewer.RouVideo.common.RouVideoConstants.ORIGIN,
            )
            SiteType.XHAMSTER -> mapOf(
                "Referer" to com.yenaly.han1meviewer.XHamster.common.XHamsterConstants.REFERER,
                "Origin" to com.yenaly.han1meviewer.XHamster.common.XHamsterConstants.ORIGIN,
            )
            SiteType.FAPFOLDER -> mapOf(
                "Referer" to FapFolderConstants.HEADER_REFERER,
                "Origin" to FapFolderConstants.HEADER_ORIGIN,
                "User-Agent" to FapFolderConstants.USER_AGENT_FAPFOLDER,
            )
            else -> emptyMap()
        }
    }

    fun Request.Builder.applySiteHeaders(videoCode: String, url: String): Request.Builder {
        headersFor(videoCode, url).forEach { (k, v) -> header(k, v) }
        return this
    }
}

object SiteImageSaver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isDownloadPermissionGranted(context: Context): Boolean =
        FapFolderImageLocation.isReady(context)

    private fun extensionOf(url: String): String {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        val ext = path.substringAfterLast('.', "")
        return if (ext in setOf("jpg", "jpeg", "png", "webp", "gif")) ext else "jpg"
    }

    private fun mimeOf(ext: String) = when (ext) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }

    private fun openOutput(
        context: Context,
        segments: List<String>,
        fileName: String,
        mime: String,
    ): OutputStream? {
        val treeUri = FapFolderImageLocation.uri ?: return null
        val tree = DocumentFile.fromTreeUri(context, treeUri.toUri()) ?: return null
        var dir: DocumentFile = tree

        val parts = if (segments.firstOrNull() == "FapFolder") segments.drop(1) else segments
        for (seg in parts) {
            dir = dir.findFile(seg)?.takeIf { it.isDirectory }
                ?: dir.createDirectory(seg)
                ?: return null
        }
        val existing = dir.findFile(fileName)
        if (existing != null && existing.length() > 0L) return null
        val target = existing ?: dir.createFile(mime, fileName) ?: return null
        return context.contentResolver.openOutputStream(target.uri, "wt")
    }

    private fun fetch(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String>,
        out: OutputStream,
    ) {
        val request = Request.Builder().url(url).get().apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body
            body.byteStream().use { input -> input.copyTo(out) }
        }
    }

    sealed interface SaveResult {
        data class Saved(val count: Int, val skipped: Int, val failed: Int) : SaveResult
        data class Error(val message: String) : SaveResult
    }

    suspend fun saveOne(
        context: Context,
        url: String,
        segments: List<String>,
        baseName: String,
        client: OkHttpClient = FapFolderNetwork.sharedOkHttpClient,
        headers: Map<String, String> = fapHeaders(),
    ): SaveResult = withContext(Dispatchers.IO) {
        saveBatch(context, listOf(url), segments, baseName, client, headers, onProgress = null)
    }

    suspend fun saveBatch(
        context: Context,
        urls: List<String>,
        segments: List<String>,
        baseName: String,
        client: OkHttpClient = FapFolderNetwork.sharedOkHttpClient,
        headers: Map<String, String> = fapHeaders(),
        onProgress: ((done: Int, total: Int) -> Unit)?,
    ): SaveResult = withContext(Dispatchers.IO) {
        if (!isDownloadPermissionGranted(context)) {
            return@withContext SaveResult.Error("Choose an image download folder first.")
        }
        var saved = 0
        var skipped = 0
        var failed = 0
        var lastError: String? = null
        val jobId = ImageDownloadTracker.start(segments.lastOrNull() ?: baseName, urls.size)
        try {
        val pad = urls.size.toString().length.coerceAtLeast(2)
        urls.forEachIndexed { index, url ->
            val ext = extensionOf(url)
            val name = DownloadLayout.sanitize(baseName, "photo") +
                    (if (urls.size > 1) "_" + (index + 1).toString().padStart(pad, '0') else "") +
                    "." + ext
            try {
                val out = openOutput(context, segments, name, mimeOf(ext))
                if (out == null) {
                    skipped++
                } else {
                    try {
                        out.use { fetch(client, url, headers, it) }
                        saved++
                    } catch (e: Exception) {
                        failed++
                        lastError = e.message
                    }
                }
            } catch (e: Exception) {
                failed++
                lastError = e.message
            }
            ImageDownloadTracker.update(jobId, index + 1)
            onProgress?.invoke(index + 1, urls.size)
        }
        if (saved == 0 && skipped == 0 && failed > 0) {
            SaveResult.Error(lastError ?: "Download failed.")
        } else SaveResult.Saved(saved, skipped, failed)
        } finally {
            ImageDownloadTracker.finish(jobId)
        }
    }

    fun saveBatchInBackground(
        context: Context,
        urls: List<String>,
        segments: List<String>,
        baseName: String,
        client: OkHttpClient = FapFolderNetwork.sharedOkHttpClient,
        headers: Map<String, String> = fapHeaders(),
        onResult: (SaveResult) -> Unit,
    ) {
        val appContext = context.applicationContext
        scope.launch {
            val result = saveBatch(
                appContext, urls, segments, baseName, client, headers, onProgress = null,
            )
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    private fun fapHeaders(): Map<String, String> = mapOf(
        "Referer" to FapFolderConstants.HEADER_REFERER,
        "User-Agent" to FapFolderConstants.USER_AGENT_FAPFOLDER,
    )
}
