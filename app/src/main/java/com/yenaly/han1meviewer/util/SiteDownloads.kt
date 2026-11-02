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
            else -> ServiceCreator.downloadClient
        }

    fun headersFor(videoCode: String, url: String): Map<String, String> {
        val host = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
        return when (SiteCheckIn.siteOfDishCode(videoCode)) {
            SiteType.MISSAV -> {
                val base = Preferences.missAvBaseUrl.trimEnd('/')
                mapOf("Referer" to "$base/", "Origin" to base)
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

    fun isDownloadPermissionGranted(context: Context): Boolean {
        val saf = Preferences.safDownloadPath
        if (!saf.isNullOrBlank()) {
            val uri = runCatching { saf.toUri() }.getOrNull() ?: return false
            return context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isWritePermission
            }
        }
        if (Preferences.isUsePrivateStorage) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

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
        val safPath = Preferences.safDownloadPath
        if (!safPath.isNullOrBlank()) {
            val tree = DocumentFile.fromTreeUri(context, safPath.toUri()) ?: return null
            var dir: DocumentFile = tree
            for (seg in segments) {
                dir = dir.findFile(seg)?.takeIf { it.isDirectory }
                    ?: dir.createDirectory(seg)
                    ?: return null
            }
            val existing = dir.findFile(fileName)
            if (existing != null && existing.length() > 0L) return null
            val target = existing ?: dir.createFile(mime, fileName) ?: return null
            return context.contentResolver.openOutputStream(target.uri, "wt")
        }
        val dir = File(HFileManager.getAppDownloadFolder(context), segments.joinToString("/"))
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, fileName)
        if (file.exists() && file.length() > 0L) return null
        return FileOutputStream(file)
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
            return@withContext SaveResult.Error("No permission to write to the download location.")
        }
        var saved = 0
        var skipped = 0
        var failed = 0
        var lastError: String? = null
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
            onProgress?.invoke(index + 1, urls.size)
        }
        if (saved == 0 && skipped == 0 && failed > 0) {
            SaveResult.Error(lastError ?: "Download failed.")
        } else SaveResult.Saved(saved, skipped, failed)
    }

    fun saveBatchInBackground(
        context: Context,
        urls: List<String>,
        segments: List<String>,
        baseName: String,
        onResult: (SaveResult) -> Unit,
    ) {
        val appContext = context.applicationContext
        scope.launch {
            val result = saveBatch(
                appContext, urls, segments, baseName, onProgress = null,
            )
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    private fun fapHeaders(): Map<String, String> = mapOf(
        "Referer" to FapFolderConstants.HEADER_REFERER,
        "User-Agent" to FapFolderConstants.USER_AGENT_FAPFOLDER,
    )
}
