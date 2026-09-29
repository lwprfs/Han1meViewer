package com.yenaly.han1meviewer.logic.network

import com.yenaly.han1meviewer.logic.exception.HUpdaterException
import okhttp3.ResponseBody
import retrofit2.Response

private val RateLimitHints = listOf(
    "rate limit",
    "secondary rate",
    "too many requests",
    "api rate limit exceeded",
)

private const val DETAIL_LIMIT = 300

internal fun <T> Response<T>.orThrowVersionCheck(api: String): T {
    if (isSuccessful) {
        return body() ?: throw HUpdaterException.VersionCheck(
            message = "GitHub 接口 $api 返回了空响应体",
            reason = HUpdaterException.VersionCheck.Reason.SERVER,
        )
    }
    throw toVersionCheckException(api)
}

private fun Response<*>.toVersionCheckException(
    api: String,
): HUpdaterException.VersionCheck {
    val code = code()
    val body = errorBodyText()
    val hints = "$body ${message()}".lowercase()
    val reason = when {
        code == 429 || hints.containsAny(RateLimitHints) -> {
            HUpdaterException.VersionCheck.Reason.RATE_LIMITED
        }

        code == 401 -> HUpdaterException.VersionCheck.Reason.BAD_CREDENTIALS

        code == 403 -> HUpdaterException.VersionCheck.Reason.FORBIDDEN

        code == 404 || code == 410 -> HUpdaterException.VersionCheck.Reason.NO_RELEASE

        else -> HUpdaterException.VersionCheck.Reason.SERVER
    }
    return HUpdaterException.VersionCheck(
        message = diagnostic("GitHub 请求失败（$api）", code, body),
        reason = reason,
        statusCode = code,
    )
}

internal fun Response<ResponseBody>.orThrowDownload(api: String): ResponseBody {
    if (isSuccessful) {
        return body() ?: throw HUpdaterException.Download(
            message = "下载接口 $api 返回了空响应体",
            reason = HUpdaterException.Download.Reason.SERVER,
        )
    }
    throw toDownloadException(api)
}

private fun Response<*>.toDownloadException(api: String): HUpdaterException.Download {
    val code = code()
    val body = errorBodyText()
    val hints = "$body ${message()}".lowercase()
    val reason = when {
        code == 429 || hints.containsAny(RateLimitHints) -> {
            HUpdaterException.Download.Reason.RATE_LIMITED
        }

        code == 401 -> HUpdaterException.Download.Reason.BAD_CREDENTIALS

        code == 403 || code == 404 || code == 410 -> {
            HUpdaterException.Download.Reason.ARTIFACT_EXPIRED
        }

        code >= 500 -> HUpdaterException.Download.Reason.SERVER

        else -> HUpdaterException.Download.Reason.CLIENT
    }
    return HUpdaterException.Download(
        message = diagnostic("更新包下载失败（$api）", code, body),
        reason = reason,
        statusCode = code,
    )
}

private fun Response<*>.errorBodyText(): String {
    return runCatching { errorBody()?.string() }.getOrNull().orEmpty()
}

private fun diagnostic(prefix: String, code: Int, body: String): String {
    return buildString {
        append("$prefix：HTTP $code")
        if (body.isNotBlank()) append("，响应：${body.take(DETAIL_LIMIT)}")
    }
}

private fun String.containsAny(keywords: List<String>): Boolean {
    return keywords.any { it in this }
}
