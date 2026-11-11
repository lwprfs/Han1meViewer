package com.yenaly.han1meviewer.logic.network

import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.yenaly.han1meviewer.HJson
import com.yenaly.han1meviewer.logic.model.github.Latest
import com.yenaly.han1meviewer.logic.update.DeltaUnavailableReason
import com.yenaly.han1meviewer.logic.update.UpdateManager
import com.yenaly.han1meviewer.logic.update.UpdateStage
import com.yenaly.han1meviewer.util.Bspatch
import com.yenaly.han1meviewer.util.copyTo
import com.yenaly.yenaly_libs.utils.applicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

internal object HDeltaUpdater {

    private const val TAG = "HDeltaUpdater"

    private const val FORMAT_BSDIFF40 = "bsdiff40"
    private const val INFO_NAME = "update.json"
    private const val PATCH_NAME = "update.patch"
    private const val MAX_APK_SIZE = 1L shl 30

    @Serializable
    data class DeltaInfo(
        val format: String = "",
        val baseSha256: String = "",
        val newSha256: String = "",
        val newSize: Long = -1,
        val patchSize: Long = -1,
    )

    data class Inspection(
        val delta: Latest.Delta?,
        val reason: DeltaUnavailableReason?,
        val detail: String?,
        val installedPrefix: String,
    )

    @Volatile
    private var cachedInstalledSha: Pair<String, String>? = null

    private fun installedSha(installed: File): String {
        val key = "${installed.path}:${installed.length()}:${installed.lastModified()}"
        cachedInstalledSha?.let { if (it.first == key) return it.second }
        return installed.sha256().also { cachedInstalledSha = key to it }
    }

    fun inspect(deltas: List<Latest.Delta>): Inspection {
        if (deltas.isEmpty()) {
            return Inspection(null, DeltaUnavailableReason.NO_DELTA_PUBLISHED, null, "")
        }
        val appInfo = applicationContext.applicationInfo
        if (!appInfo.splitSourceDirs.isNullOrEmpty()) {
            return Inspection(null, DeltaUnavailableReason.SPLIT_INSTALL, null, "")
        }
        val installed = File(appInfo.sourceDir).takeIf { it.isFile && it.canRead() }
            ?: return Inspection(null, DeltaUnavailableReason.INSTALLED_APK_UNREADABLE, null, "")
        val sha = installedSha(installed)
        val prefix = sha.take(16)
        val delta = deltas.firstOrNull {
            !it.baseSha256Prefix.isNullOrEmpty() && sha.startsWith(it.baseSha256Prefix, ignoreCase = true)
        } ?: deltas.firstOrNull { it.baseSha256Prefix.isNullOrEmpty() }
        if (delta == null) {
            val published = deltas.mapNotNull { it.baseSha256Prefix }.joinToString(", ")
            return Inspection(
                null,
                DeltaUnavailableReason.BASE_MISMATCH,
                "installed=$prefix, patches built for: $published",
                prefix,
            )
        }
        return Inspection(delta, null, null, prefix)
    }

    suspend fun tryApply(
        target: File,
        deltas: List<Latest.Delta>,
        progress: (suspend (Int, Long, Long) -> Unit)? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val context = applicationContext
        val zipFile = File(context.cacheDir, "update.delta.zip")
        val patchFile = File(context.cacheDir, "update.patch")
        try {
            UpdateManager.onStage(UpdateStage.CHECKING_DELTA)
            val inspection = inspect(deltas)
            UpdateManager.onInstalledBuild(inspection.installedPrefix)
            val delta = inspection.delta
            if (delta == null) {
                Log.i(TAG, "增量更新不可用：${inspection.reason} ${inspection.detail.orEmpty()}")
                UpdateManager.onDeltaUnavailable(inspection.reason!!, inspection.detail)
                return@withContext false
            }
            val installed = installedApk()!!
            val installedSha = installedSha(installed)
            UpdateManager.onDeltaSelected(delta)

            UpdateManager.onStage(UpdateStage.DOWNLOADING_DELTA)
            downloadTo(zipFile, delta.downloadLink, progress)
            ensureActive()

            val info = extractDelta(zipFile, patchFile)
            if (info.format != FORMAT_BSDIFF40 ||
                info.newSha256.length != 64 ||
                info.newSize <= 0 || info.newSize > MAX_APK_SIZE
            ) {
                Log.w(TAG, "增量包元数据无效：$info")
                UpdateManager.onDeltaUnavailable(DeltaUnavailableReason.BAD_METADATA, info.toString())
                return@withContext false
            }
            if (!info.baseSha256.equals(installedSha, ignoreCase = true)) {
                Log.i(TAG, "补丁基线与当前安装的 APK 不一致，跳过增量更新")
                UpdateManager.onDeltaUnavailable(
                    DeltaUnavailableReason.BASE_MISMATCH,
                    "installed=${installedSha.take(16)}, patch=${info.baseSha256.take(16)}",
                )
                return@withContext false
            }
            ensureActive()

            UpdateManager.onStage(UpdateStage.APPLYING_PATCH)
            target.delete()
            Bspatch.apply(installed, patchFile, target)
            ensureActive()

            UpdateManager.onStage(UpdateStage.VERIFYING)
            if (target.length() != info.newSize ||
                !target.sha256().equals(info.newSha256, ignoreCase = true)
            ) {
                Log.w(TAG, "还原出的 APK 校验失败")
                target.delete()
                UpdateManager.onDeltaUnavailable(
                    DeltaUnavailableReason.VERIFY_FAILED,
                    "expected ${info.newSize} B / ${info.newSha256.take(16)}",
                )
                return@withContext false
            }
            if (!hasSameSigner(target)) {
                Log.w(TAG, "还原出的 APK 签名与当前安装的不一致")
                target.delete()
                UpdateManager.onDeltaUnavailable(DeltaUnavailableReason.SIGNER_MISMATCH)
                return@withContext false
            }

            Log.i(TAG, "增量更新成功：patch ${patchFile.length()} B -> apk ${target.length()} B")
            true
        } catch (e: CancellationException) {
            target.delete()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "增量更新失败，将回退到完整 APK：${e.message}", e)
            target.delete()
            val reason = when (e) {
                is java.io.IOException, is com.yenaly.han1meviewer.logic.exception.HUpdaterException ->
                    if (UpdateManager.state.value.stage == UpdateStage.DOWNLOADING_DELTA)
                        DeltaUnavailableReason.DOWNLOAD_FAILED
                    else DeltaUnavailableReason.PATCH_FAILED
                else -> DeltaUnavailableReason.PATCH_FAILED
            }
            UpdateManager.onDeltaUnavailable(reason, e.message?.take(160))
            false
        } finally {
            zipFile.delete()
            patchFile.delete()
        }
    }

    private suspend fun downloadTo(
        file: File,
        url: String,
        progress: (suspend (Int, Long, Long) -> Unit)?,
    ) {
        file.delete()
        val body = HanimeNetwork.githubService.request(url).orThrowDownload(url)
        file.outputStream().use { out ->
            body.use {
                it.byteStream().copyTo(out, it.contentLength(), progress = progress)
            }
        }
    }

    private fun extractDelta(zipFile: File, patchOut: File): DeltaInfo {
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
            val infoEntry = entries.firstOrNull { it.name.substringAfterLast('/') == INFO_NAME }
                ?: throw java.io.IOException("$INFO_NAME not found in delta package")
            val patchEntry = entries.firstOrNull { it.name.substringAfterLast('/') == PATCH_NAME }
                ?: throw java.io.IOException("$PATCH_NAME not found in delta package")

            val json = zip.getInputStream(infoEntry).use { it.readBytes().toString(Charsets.UTF_8) }
            patchOut.delete()
            zip.getInputStream(patchEntry).use { input ->
                patchOut.outputStream().use { out -> pump(input, out) }
            }
            return HJson.decodeFromString<DeltaInfo>(json)
        }
    }

    private fun pump(input: InputStream, out: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
    }

    private fun installedApk(): File? {
        val appInfo = applicationContext.applicationInfo
        if (!appInfo.splitSourceDirs.isNullOrEmpty()) return null
        return File(appInfo.sourceDir).takeIf { it.isFile && it.canRead() }
    }

    private fun File.sha256(): String {
        val md = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hasSameSigner(apk: File): Boolean {
        return try {
            val pm = applicationContext.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val archive = pm.getPackageArchiveInfo(apk.path, flags)
            val installed = pm.getPackageInfo(applicationContext.packageName, flags)
            val archiveSigners = signers(archive)
            val installedSigners = signers(installed)
            if (archiveSigners.isNullOrEmpty() || installedSigners.isNullOrEmpty()) {
                Log.w(TAG, "无法读取签名，交给系统安装器校验")
                true
            } else {
                archiveSigners == installedSigners
            }
        } catch (e: Exception) {
            Log.w(TAG, "签名校验异常，交给系统安装器校验：${e.message}")
            true
        }
    }

    private fun signers(info: android.content.pm.PackageInfo?): Set<String>? {
        info ?: return null
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return signatures?.map { it.toCharsString() }?.toSet()
    }
}
