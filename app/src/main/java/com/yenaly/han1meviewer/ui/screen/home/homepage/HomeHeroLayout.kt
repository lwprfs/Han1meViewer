package com.yenaly.han1meviewer.ui.screen.home.homepage

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.Preferences

sealed interface HomeHeroSpec {

    data object Default : HomeHeroSpec

    data class Constrained(
        val bannerSize: DpSize,
        val panelWidth: Dp? = null
    ) : HomeHeroSpec
}

val HomeHeroPanelSpacing = 12.dp

val HomeHeroHorizontalPadding = 12.dp

private const val HeroHeightWindowRatio = 0.42f

private val HeroPanelMinWidth = 260.dp

private val HeroPanelMaxWidth = 420.dp

@Composable
fun rememberHomeHeroSpec(hasSideContent: Boolean): HomeHeroSpec {
    val isPreview = LocalInspectionMode.current
    if (!isPreview && !Preferences.tabletMode) return HomeHeroSpec.Default

    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val containerWidth = with(density) { containerSize.width.toDp() }
    val containerHeight = with(density) { containerSize.height.toDp() }

    val naturalHeight = containerWidth * 9f / 16f
    val heroHeight = minOf(naturalHeight, containerHeight * HeroHeightWindowRatio)
    if (heroHeight >= naturalHeight) return HomeHeroSpec.Default

    val bannerWidth = heroHeight * 16f / 9f
    val remainingWidth = containerWidth - bannerWidth -
            HomeHeroHorizontalPadding * 2 - HomeHeroPanelSpacing
    val panelWidth = when {
        !hasSideContent -> null
        remainingWidth < HeroPanelMinWidth -> null
        else -> remainingWidth.coerceAtMost(HeroPanelMaxWidth)
    }
    return HomeHeroSpec.Constrained(DpSize(bannerWidth, heroHeight), panelWidth)
}
