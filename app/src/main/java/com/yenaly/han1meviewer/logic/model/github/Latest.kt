package com.yenaly.han1meviewer.logic.model.github

data class Latest(
    val version: String,
    val changelog: String,
    val downloadLink: String,

    val nodeId: String,

    /** 可用的增量包候选（基线各不相同），客户端挑选与已安装 APK 匹配的那个。 */
    val deltas: List<Delta> = emptyList(),
) {

    data class Delta(
        val downloadLink: String,
        val baseSha256Prefix: String? = null,
    )
}
