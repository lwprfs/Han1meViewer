package com.yenaly.han1meviewer.logic.network

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.yenaly.han1meviewer.BuildConfig
import com.yenaly.han1meviewer.FirebaseConstants
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.exception.HUpdaterException
import com.yenaly.han1meviewer.logic.model.github.CommitComparison
import com.yenaly.han1meviewer.logic.model.github.Latest
import com.yenaly.han1meviewer.util.checkNeedUpdate
import com.yenaly.han1meviewer.util.copyTo
import com.yenaly.han1meviewer.util.runSuspendCatching
import com.yenaly.yenaly_libs.utils.applicationContext
import okio.use
import java.io.File
import java.util.zip.ZipInputStream

object HUpdater {

    const val TAG = "HUpdater"

    const val DEFAULT_BRANCH = "main"

    private const val MESSAGE_DETAIL_LIMIT = 120

    private val linefeedRegex = Regex("\\n{2,}")

    enum class Channel(val logName: String) {

        CI("CI Artifact"),

        RELEASE("Release"),
    }

    suspend fun checkForUpdate(forceCheck: Boolean = false): Latest? {
        if (!forceCheck && !Preferences.isUpdateDialogVisible) return null
        val channel = currentChannel()
        Log.d(TAG, "开始检查更新，渠道：${channel.logName}")
        return when (channel) {
            Channel.CI -> checkCiChannel()
            Channel.RELEASE -> checkReleaseChannel()
        }
    }

    private fun currentChannel(): Channel {
        if (!Preferences.useCIUpdateChannel) return Channel.RELEASE

        val ciEnabled = try {
            Firebase.remoteConfig.getBoolean(FirebaseConstants.ENABLE_CI_UPDATE)
        } catch (e: Exception) {
            Log.w(TAG, "读取 Remote Config 的 ${FirebaseConstants.ENABLE_CI_UPDATE} 失败：${e.message}")
            throw HUpdaterException.VersionCheck(
                message = "无法读取远端配置（${FirebaseConstants.ENABLE_CI_UPDATE}）：${e.message}",
                cause = e,
                reason = HUpdaterException.VersionCheck.Reason.NETWORK,
            )
        }
        if (!ciEnabled) {
            Log.w(TAG, "CI 频道已被 Remote Config（${FirebaseConstants.ENABLE_CI_UPDATE}）下线")
            throw HUpdaterException.VersionCheck(
                message = "CI 更新频道已被远端开关（${FirebaseConstants.ENABLE_CI_UPDATE}）下线",
                reason = HUpdaterException.VersionCheck.Reason.NO_RELEASE,
            )
        }
        return Channel.CI
    }

    private suspend fun checkCiChannel(): Latest? {
        val curSha = BuildConfig.COMMIT_SHA

        val runs = requestWorkflowRuns()
        val workflowRun = runs.workflowRuns.firstOrNull()
        if (workflowRun == null) {

            Log.w(TAG, "CI 频道没有可用的工作流运行记录")
            return null
        }

        val shortSha = workflowRun.headSha.take(7)
        if (shortSha == curSha) {
            Log.d(TAG, "已是最新的 CI 构建：$shortSha")
            return null
        }

        val artifacts = requestArtifacts(workflowRun.artifactsUrl)
        val artifact = artifacts.artifacts.firstOrNull()
        val archiveUrl = artifact?.downloadLink
        val nodeId = artifact?.nodeId.orEmpty()
        if (archiveUrl.isNullOrBlank()) {

            Log.w(TAG, "工作流运行 ${workflowRun.headSha} 没有可下载的构建产物")
            throw HUpdaterException.VersionCheck(
                message = "CI 工作流（$shortSha）没有可下载的构建产物",
                cause = null,
                reason = HUpdaterException.VersionCheck.Reason.NO_RELEASE,
            )
        }

        val changelog = runSuspendCatching {
            requestCommitComparison(curSha, shortSha).commits.toChangelogPrettyString()
        }.getOrNull() ?: workflowRun.title
        return Latest("$shortSha (CI)", changelog, archiveUrl, nodeId)
    }

    private suspend fun checkReleaseChannel(): Latest? {
        val ver = requestLatestRelease()
        if (!checkNeedUpdate(ver.tagName)) return null
        val asset = ver.assets.firstOrNull()
        if (asset == null) {

            Log.w(TAG, "Release ${ver.tagName} 没有任何可下载的 asset")
            throw HUpdaterException.VersionCheck(
                message = "Release ${ver.tagName} 没有任何可下载的 asset",
                cause = null,
                reason = HUpdaterException.VersionCheck.Reason.NO_RELEASE,
            )
        }
        return Latest(ver.tagName, ver.body, asset.browserDownloadURL, asset.nodeID)
    }

    private suspend fun requestLatestRelease() = try {
        HanimeNetwork.githubService.getLatestVersion()
            .orThrowVersionCheck("GET releases/latest")
    } catch (e: HUpdaterException) {
        throw e
    } catch (e: Exception) {

        throw e.toVersionCheckException("GET releases/latest")
    }

    private suspend fun requestWorkflowRuns() = try {
        HanimeNetwork.githubService.getWorkflowRuns()
            .orThrowVersionCheck("GET actions/workflows/ci.yml/runs")
    } catch (e: HUpdaterException) {
        throw e
    } catch (e: Exception) {
        throw e.toVersionCheckException("GET actions/workflows/ci.yml/runs")
    }

    private suspend fun requestArtifacts(url: String) = try {
        HanimeNetwork.githubService.getArtifacts(url)
            .orThrowVersionCheck("GET workflow run artifacts")
    } catch (e: HUpdaterException) {
        throw e
    } catch (e: Exception) {
        throw e.toVersionCheckException("GET workflow run artifacts")
    }

    private suspend fun requestCommitComparison(curSha: String, latestSha: String) =
        HanimeNetwork.githubService.getCommitComparison(curSha, latestSha)
            .orThrowVersionCheck("GET compare/$curSha...$latestSha")

    private fun Exception.toVersionCheckException(api: String): HUpdaterException.VersionCheck {
        return HUpdaterException.VersionCheck(
            message = "请求 $api 失败：${message ?: this::class.java.simpleName}",
            cause = this,
            reason = HUpdaterException.VersionCheck.Reason.NETWORK,
        )
    }

    suspend fun File.injectUpdate(url: String, progress: (suspend (Int, Long, Long) -> Unit)? = null) {
        val isZip = url.endsWith("zip")
        Log.d(TAG, "开始下载更新包（${if (isZip) "CI zip" else "release"}）：$url")
        downloadTo(url, isZip, progress)
    }

    private suspend fun File.downloadTo(
        url: String,
        isZip: Boolean,
        progress: (suspend (Int, Long, Long) -> Unit)?,
    ) {
        val res = try {
            HanimeNetwork.githubService.request(url)
        } catch (e: Exception) {
            throw HUpdaterException.Download(
                message = "请求下载地址失败：${e.message ?: e::class.java.simpleName}",
                cause = e,
                reason = HUpdaterException.Download.Reason.NETWORK,
            )
        }

        val body = res.orThrowDownload(url)
        if (isZip) {
            Log.d(TAG, "Injecting update from zip ($url)")
            body.use {
                it.byteStream().use { stream ->
                    ZipInputStream(stream).use { zip ->
                        val entry = zip.nextEntry
                        if (entry == null) {
                            throw HUpdaterException.Download(
                                message = "下载到的 CI 压缩包内容为空（$url）",
                                cause = null,
                                reason = HUpdaterException.Download.Reason.INVALID_PAYLOAD,
                            )
                        }
                        outputStream().use { out ->
                            Log.i(TAG, "content length: ${it.contentLength()}")

                            zip.copyTo(out, (it.contentLength() * 1.79).toLong(), progress = progress)
                        }
                    }
                }
            }
        } else {
            Log.d(TAG, "Injecting update from release ($url)")
            outputStream().use { out ->
                body.use {
                    Log.i(TAG, "content length: ${it.contentLength()}")
                    it.byteStream().copyTo(out, it.contentLength(), progress = progress)
                }
            }
        }
    }

    fun errorMessage(e: Throwable?): CharSequence? {
        val reason = e as? HUpdaterException ?: return null
        val template = applicationContext.getString(reason.errorMessageRes)
        if (!template.contains("%s")) return template

        val detail = reason.statusCode?.let { "HTTP $it" }
            ?: e.cause?.message?.takeIf { it.isNotBlank() }
            ?: e.message?.takeIf { it.isNotBlank() }
            ?: return template

        return runCatching {
            applicationContext.getString(reason.errorMessageRes, detail.take(MESSAGE_DETAIL_LIMIT))
        }.getOrDefault(template)
    }

    fun errorDetail(e: Throwable?): String? {
        return e?.message?.takeIf { it.isNotBlank() }
    }

    private val CommitComparison.Commit.CommitDetail.CommitAuthor.isAuthorShouldIgnore: Boolean
        get() = name.contains("dependabot")

    private fun List<CommitComparison.Commit>.toChangelogPrettyString(): String {
        return filterNot { commit ->
            commit.commit.author.isAuthorShouldIgnore
        }.distinct().reversed().joinToString("\n\n") { commit ->
            val message = commit.commit.message.replace(linefeedRegex, "\n")
            "↓ (@${commit.commit.author.name})\n$message"
        }
    }
}
