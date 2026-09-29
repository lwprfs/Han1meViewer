@file:Suppress("unused")
@file:JvmName("Base64Util")

package com.yenaly.yenaly_libs.utils

import android.util.Base64

@JvmName("encodeToString")
fun String.encodeToStringByBase64(flag: Int = Base64.DEFAULT): String {
    return Base64.encodeToString(this.toByteArray(), flag)
}

@JvmName("decodeFromString")
fun String.decodeFromStringByBase64(flag: Int = Base64.DEFAULT): String {
    return String(Base64.decode(this.toByteArray(), flag))
}

@JvmName("decodeFromByteArray")
fun ByteArray.decodeFromByteArrayByBase64(flag: Int = Base64.DEFAULT): ByteArray {
    return Base64.decode(this, flag)
}
