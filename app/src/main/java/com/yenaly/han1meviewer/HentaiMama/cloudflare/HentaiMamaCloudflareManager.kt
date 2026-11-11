package com.yenaly.han1meviewer.HentaiMama.cloudflare

import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork
import com.yenaly.han1meviewer.util.cloudflare.CloudflareVerificationManager

object HentaiMamaCloudflareManager : CloudflareVerificationManager(
    tag = "HMCloudflareManager",
    cookieStore = HentaiMamaCloudflareCookieManager,
    activityClass = HentaiMamaCloudflareActivity::class.java,
    baseUrl = { HentaiMamaNetwork.baseUrl },
)
