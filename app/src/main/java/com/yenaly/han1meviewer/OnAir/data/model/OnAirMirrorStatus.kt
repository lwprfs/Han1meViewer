package com.yenaly.han1meviewer.OnAir.data.model

sealed interface OnAirMirrorStatus {
    val host: String
    val checkedAt: Long

    data class Checking(
        override val host: String,
        override val checkedAt: Long = System.currentTimeMillis(),
    ) : OnAirMirrorStatus

    data class Online(
        override val host: String,
        val httpCode: Int?,
        val detail: String,
        override val checkedAt: Long = System.currentTimeMillis(),
    ) : OnAirMirrorStatus

    data class Offline(
        override val host: String,
        val reason: String,
        override val checkedAt: Long = System.currentTimeMillis(),
    ) : OnAirMirrorStatus

    data class Error(
        override val host: String,
        val message: String,
        override val checkedAt: Long = System.currentTimeMillis(),
    ) : OnAirMirrorStatus
}
