package com.yenaly.han1meviewer.XHamster.cloudflare

import com.yenaly.han1meviewer.util.cloudflare.BaseCloudflareActivity
import com.yenaly.han1meviewer.util.cloudflare.CloudflareCookieStore

/** Registered in the manifest; all behaviour lives in [BaseCloudflareActivity]. */
class XHamsterCloudflareActivity : BaseCloudflareActivity() {
    override val tag = "XHCloudflareActivity"
    override val cookieStore: CloudflareCookieStore get() = XHamsterCloudflareCookieManager
}
