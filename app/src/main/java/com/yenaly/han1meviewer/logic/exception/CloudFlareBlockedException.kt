package com.yenaly.han1meviewer.logic.exception

import com.yenaly.han1meviewer.R

open class CloudFlareBlockedException(reason: String) : RuntimeException(reason) {
    companion object {
        val localizedMessages = intArrayOf(
            R.string.website_blocked_msg,
            R.string.website_blocked_msg_2,
            R.string.website_blocked_msg_3,
        )
    }
}
