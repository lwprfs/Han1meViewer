package com.yenaly.han1meviewer.SupJav.cloudflare

import com.yenaly.han1meviewer.util.cloudflare.BaseCloudflareActivity
import com.yenaly.han1meviewer.util.cloudflare.CloudflareCookieStore

class SupJavCloudflareActivity : BaseCloudflareActivity() {
    override val tag = "SJCloudflareActivity"
    override val cookieStore: CloudflareCookieStore get() = SupJavCloudflareCookieManager
}
