package com.yenaly.han1meviewer.RouVideo.cloudflare

import com.yenaly.han1meviewer.util.cloudflare.BaseCloudflareActivity
import com.yenaly.han1meviewer.util.cloudflare.CloudflareCookieStore

/** Registered in the manifest; all behaviour lives in [BaseCloudflareActivity]. */
class RouVideoCloudflareActivity : BaseCloudflareActivity() {
    override val tag = "RVCloudflareActivity"
    override val cookieStore: CloudflareCookieStore get() = RouVideoCloudflareCookieManager
}
