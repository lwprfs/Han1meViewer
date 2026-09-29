@file:JvmName("UnicodeUtil")

package com.yenaly.yenaly_libs.utils

@JvmName("decode")
fun stringDecodeToUnicode(src: String): String {
    val builder = StringBuilder()
    for (element in src) {

        var s = Integer.toHexString(element.code)

        if (s.length == 2) {
            s = "00$s"
        }
        builder.append("\\u$s")
    }
    return builder.toString()
}

@JvmName("encode")
fun unicodeEncodeToString(unicode: String): String {
    val builder = StringBuilder()
    val hex = unicode.split("\\\\u".toRegex()).toTypedArray()
    for (i in 1 until hex.size) {
        val data = hex[i].toInt(16)
        builder.append(data.toChar())
    }
    return builder.toString()
}
