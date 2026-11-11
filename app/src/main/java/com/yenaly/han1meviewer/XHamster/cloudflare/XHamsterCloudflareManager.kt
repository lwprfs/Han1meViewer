package com.yenaly.han1meviewer.XHamster.cloudflare

import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.util.cloudflare.CloudflareVerificationManager

object XHamsterCloudflareManager : CloudflareVerificationManager(
    tag = "XHCloudflareManager",
    cookieStore = XHamsterCloudflareCookieManager,
    activityClass = XHamsterCloudflareActivity::class.java,
    baseUrl = { XHamsterConstants.BASE_URL },
)
