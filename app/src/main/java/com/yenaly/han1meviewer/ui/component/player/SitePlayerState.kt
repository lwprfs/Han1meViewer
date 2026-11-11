package com.yenaly.han1meviewer.ui.component.player

/** UI state of the inline player shared by the simple sites (XHamster, RouVideo, SupJav, HentaiMama). */
data class SitePlayerState(
    val url: String = "",
    val quality: String = "",
    val qualityOptions: Map<String, String> = emptyMap(),
    val availableSpeeds: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
    val speed: Float = 1.0f,
    val isPlaying: Boolean = false,
    val isReady: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val hasSubtitle: Boolean = false,
    val isFullscreen: Boolean = false,
)
