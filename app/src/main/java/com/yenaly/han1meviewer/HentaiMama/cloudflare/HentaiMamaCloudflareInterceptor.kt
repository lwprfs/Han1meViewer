package com.yenaly.han1meviewer.HentaiMama.cloudflare

import android.content.Context
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaConstants
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork
import com.yenaly.han1meviewer.util.cloudflare.CloudflareChallengeInterceptor

class HentaiMamaCloudflareInterceptor(
    context: Context,
) : CloudflareChallengeInterceptor(
    context = context,
    tag = "HMCloudflareInterceptor",
    cookieStore = HentaiMamaCloudflareCookieManager,
    manager = HentaiMamaCloudflareManager,
    hostnames = { HentaiMamaConstants.HOSTNAME },
    baseUrls = { listOf(HentaiMamaConstants.BASE_URL, HentaiMamaNetwork.baseUrl) },
)
