package com.yenaly.han1meviewer.logic.model

data class HanimePreview(
    val headerPicUrl: String?,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val latestHanime: List<HanimeInfo>,
    val previewInfo: List<PreviewInfo>,
) {
    data class PreviewInfo(
        val title: String?,
        val videoTitle: String?,
        val coverUrl: String?,
        val introduction: String?,
        val brand: String?,
        val releaseDate: String?,
        val videoCode: String?,
        val tags: List<String>,
        val relatedPicsUrl: List<String>,
    )
}
