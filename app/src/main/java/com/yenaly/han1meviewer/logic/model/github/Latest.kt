package com.yenaly.han1meviewer.logic.model.github

data class Latest(
    val version: String,
    val changelog: String,
    val downloadLink: String,

    val nodeId: String,

    val deltas: List<Delta> = emptyList(),

    val apkName: String = "",

    val apkSize: Long = 0L,

    val isCi: Boolean = false,
) {

    data class Delta(
        val downloadLink: String,
        val baseSha256Prefix: String? = null,

        val name: String = "",

        val sizeBytes: Long = 0L,
    )
}
