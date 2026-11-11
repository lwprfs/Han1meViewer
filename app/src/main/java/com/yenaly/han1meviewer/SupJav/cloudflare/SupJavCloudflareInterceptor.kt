package com.yenaly.han1meviewer.SupJav.cloudflare

import android.content.Context
import com.yenaly.han1meviewer.SupJav.common.SupJavConstants
import com.yenaly.han1meviewer.util.cloudflare.CloudflareChallengeInterceptor

class SupJavCloudflareInterceptor(
    context: Context,
) : CloudflareChallengeInterceptor(
    context = context,
    tag = "SJCloudflareInterceptor",
    cookieStore = SupJavCloudflareCookieManager,
    manager = SupJavCloudflareManager,
    hostnames = { SupJavConstants.HOSTNAME },
    baseUrls = { listOf(SupJavConstants.BASE_URL) },
)
