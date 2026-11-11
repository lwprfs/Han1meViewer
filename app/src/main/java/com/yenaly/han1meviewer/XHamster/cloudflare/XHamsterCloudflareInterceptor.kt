package com.yenaly.han1meviewer.XHamster.cloudflare

import android.content.Context
import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.util.cloudflare.CloudflareChallengeInterceptor

class XHamsterCloudflareInterceptor(
    context: Context,
) : CloudflareChallengeInterceptor(
    context = context,
    tag = "XHCloudflareInterceptor",
    cookieStore = XHamsterCloudflareCookieManager,
    manager = XHamsterCloudflareManager,
    hostnames = { XHamsterConstants.HOSTNAME },
    baseUrls = { listOf(XHamsterConstants.BASE_URL) },
)
