package com.yenaly.han1meviewer.RouVideo.cloudflare

import android.content.Context
import com.yenaly.han1meviewer.RouVideo.common.RouVideoConstants
import com.yenaly.han1meviewer.util.cloudflare.CloudflareChallengeInterceptor

class RouVideoCloudflareInterceptor(
    context: Context,
) : CloudflareChallengeInterceptor(
    context = context,
    tag = "RVCloudflareInterceptor",
    cookieStore = RouVideoCloudflareCookieManager,
    manager = RouVideoCloudflareManager,
    hostnames = { RouVideoConstants.HOSTNAME },
    baseUrls = { listOf(RouVideoConstants.BASE_URL) },
)
