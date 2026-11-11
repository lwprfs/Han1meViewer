package com.yenaly.han1meviewer.HentaiMama.cloudflare

import com.yenaly.han1meviewer.util.cloudflare.BaseCloudflareActivity
import com.yenaly.han1meviewer.util.cloudflare.CloudflareCookieStore

class HentaiMamaCloudflareActivity : BaseCloudflareActivity() {
    override val tag = "HMCloudflareActivity"
    override val cookieStore: CloudflareCookieStore get() = HentaiMamaCloudflareCookieManager
}
