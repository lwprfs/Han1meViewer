package com.yenaly.han1meviewer.RouVideo.cloudflare

import com.yenaly.han1meviewer.util.cloudflare.BaseCloudflareActivity
import com.yenaly.han1meviewer.util.cloudflare.CloudflareCookieStore

class RouVideoCloudflareActivity : BaseCloudflareActivity() {
    override val tag = "RVCloudflareActivity"
    override val cookieStore: CloudflareCookieStore get() = RouVideoCloudflareCookieManager
}
