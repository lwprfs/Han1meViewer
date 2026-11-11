package com.yenaly.han1meviewer.RouVideo.cloudflare

import com.yenaly.han1meviewer.util.cloudflare.CloudflareCookieStore

object RouVideoCloudflareCookieManager : CloudflareCookieStore(
    tag = "RVCloudflareCookie",
    cookiePrefix = "rv_cf_cookie",
    expiryPrefix = "rv_cf_expiry",
)
