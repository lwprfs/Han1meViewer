package com.yenaly.han1meviewer.logic.model.github

data class Latest(
    val version: String,
    val changelog: String,
    val downloadLink: String,

    val nodeId: String,
)
