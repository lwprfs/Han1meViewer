package com.yenaly.han1meviewer.HentaiMama.cloudflare

import com.yenaly.han1meviewer.util.cloudflare.CloudflareCookieStore

object HentaiMamaCloudflareCookieManager : CloudflareCookieStore(
    tag = "HMCloudflareCookie",
    cookiePrefix = "hm_cf_cookie",
    expiryPrefix = "hm_cf_expiry",
)
