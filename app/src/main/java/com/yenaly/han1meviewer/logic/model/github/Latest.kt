package com.yenaly.han1meviewer.logic.model.github

data class Latest(
    val version: String,
    val changelog: String,
    val downloadLink: String,

    val nodeId: String,

    /**
     * 可选的增量更新包。为空表示这次更新没有可用的增量包，只能下载完整 APK。
     */
    val delta: Delta? = null,
) {

    /**
     * 增量更新包：一个 zip，里面包含 `update.json` 和 `update.patch`。
     *
     * @param downloadLink zip 的下载地址（CI artifact 或 Release asset）
     * @param baseSha256Prefix 补丁所基于的旧 APK 的 SHA-256 前缀（取自文件名），
     * 用来在下载前快速判断补丁是否适用于当前已安装的版本。
     */
    data class Delta(
        val downloadLink: String,
        val baseSha256Prefix: String? = null,
    )
}
