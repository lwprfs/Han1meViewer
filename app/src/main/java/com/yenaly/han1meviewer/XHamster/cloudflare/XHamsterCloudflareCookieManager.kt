package com.yenaly.han1meviewer.XHamster.cloudflare

import com.yenaly.han1meviewer.util.cloudflare.CloudflareCookieStore

object XHamsterCloudflareCookieManager : CloudflareCookieStore(
    tag = "XHCloudflareCookie",
    cookiePrefix = "xh_cf_cookie",
    expiryPrefix = "xh_cf_expiry",
)
