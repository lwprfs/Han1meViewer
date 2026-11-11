package com.yenaly.han1meviewer.SupJav.cloudflare

import com.yenaly.han1meviewer.util.cloudflare.CloudflareCookieStore

object SupJavCloudflareCookieManager : CloudflareCookieStore(
    tag = "SJCloudflareCookie",
    cookiePrefix = "sj_cf_cookie",
    expiryPrefix = "sj_cf_expiry",
)
