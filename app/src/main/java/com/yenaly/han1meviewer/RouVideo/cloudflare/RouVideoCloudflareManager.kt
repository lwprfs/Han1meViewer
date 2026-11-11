package com.yenaly.han1meviewer.RouVideo.cloudflare

import com.yenaly.han1meviewer.RouVideo.common.RouVideoConstants
import com.yenaly.han1meviewer.util.cloudflare.CloudflareVerificationManager

object RouVideoCloudflareManager : CloudflareVerificationManager(
    tag = "RVCloudflareManager",
    cookieStore = RouVideoCloudflareCookieManager,
    activityClass = RouVideoCloudflareActivity::class.java,
    baseUrl = { RouVideoConstants.BASE_URL },
)
