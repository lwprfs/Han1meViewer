package com.yenaly.han1meviewer.ui.screen.home.preview

import androidx.compose.runtime.saveable.listSaver
import com.yenaly.han1meviewer.logic.model.HanimePreview
import com.yenaly.han1meviewer.logic.state.WebsiteState

data class PreviewRouteUiState(
    val currentDateCode: String = currentDateCode(),
    val selectedIndex: Int = 0,
) {
    companion object {
        val Saver = listSaver<PreviewRouteUiState, Any>(
            save = { listOf(it.currentDateCode, it.selectedIndex) },
            restore = {
                PreviewRouteUiState(
                    currentDateCode = it[0] as String,
                    selectedIndex = it[1] as Int,
                )
            },
        )
    }
}

data class PreviewMonthHeaderState(
    val dateCode: String,
    val headerImageUrl: String?,
    val prevLabel: String,
    val nextLabel: String,
    val canPrev: Boolean,
    val canNext: Boolean,
)

data class PreviewImageViewerState(
    val imageUrls: List<String>,
    val initialPage: Int,
)

data class PreviewUiState(
    val routeState: PreviewRouteUiState = PreviewRouteUiState(),
    val currentDateLabel: String = "",
    val prevDateLabel: String = "",
    val nextDateLabel: String = "",
    val monthAnimationDirection: Int = 1,
    val displayState: WebsiteState<HanimePreview> = WebsiteState.Loading,
    val commentCount: Int = 0,
    val canPrev: Boolean = false,
    val canNext: Boolean = false,
    val monthHeaderState: PreviewMonthHeaderState,
    val imageViewerState: PreviewImageViewerState? = null,
)

sealed interface PreviewEvent {

    data object OnBack : PreviewEvent

    data class OnSelectTourItem(val index: Int) : PreviewEvent

    data class OnPrevMonth(val fromDateCode: String) : PreviewEvent

    data class OnNextMonth(val fromDateCode: String) : PreviewEvent

    data class OnOpenImage(val index: Int, val imageUrls: List<String>) : PreviewEvent

    data object OnDismissImageViewer : PreviewEvent

    data class OnOpenVideo(val videoCode: String?) : PreviewEvent

    data object OnOpenGetchuPreview : PreviewEvent

    data class OnOpenComment(val label: String, val dateCode: String) : PreviewEvent

    data object OnRetryLoad : PreviewEvent
}
