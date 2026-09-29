package com.yenaly.han1meviewer.logic.exception

import com.yenaly.han1meviewer.R
import java.io.IOException

sealed class HUpdaterException(
    message: String,
    cause: Throwable? = null,

    val errorMessageRes: Int,

    val statusCode: Int? = null,
) : IOException(message, cause) {

    class VersionCheck(
        message: String,
        cause: Throwable? = null,
        reason: Reason,
        statusCode: Int? = null,
    ) : HUpdaterException(message, cause, reason.errorMessageRes, statusCode) {

        val reason: Reason = reason

        enum class Reason(val errorMessageRes: Int) {

            RATE_LIMITED(R.string.update_error_rate_limited),

            BAD_CREDENTIALS(R.string.update_error_bad_credentials),

            FORBIDDEN(R.string.update_error_forbidden),

            NO_RELEASE(R.string.update_error_no_release),

            SERVER(R.string.update_error_server),

            NETWORK(R.string.update_error_network),
        }
    }

    class Download(
        message: String,
        cause: Throwable? = null,
        reason: Reason,
        statusCode: Int? = null,
    ) : HUpdaterException(message, cause, reason.errorMessageRes, statusCode) {

        val reason: Reason = reason

        enum class Reason(val errorMessageRes: Int) {

            RATE_LIMITED(R.string.update_error_download_rate_limited),

            BAD_CREDENTIALS(R.string.update_error_download_bad_credentials),

            ARTIFACT_EXPIRED(R.string.update_error_download_artifact_expired),

            CLIENT(R.string.update_error_download_client),

            SERVER(R.string.update_error_download_server),

            NETWORK(R.string.update_error_download_network),

            INVALID_PAYLOAD(R.string.update_error_download_invalid_payload),
        }
    }
}
