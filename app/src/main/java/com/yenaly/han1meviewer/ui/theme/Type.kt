package com.yenaly.han1meviewer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

val AppFontFamily: FontFamily = FontFamily.SansSerif

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: Float = 0f,
) = TextStyle(
    fontFamily = AppFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.em,
)

val AppTypography = Typography(
    displayLarge = style(57, 64, FontWeight.SemiBold, -0.01f),
    displayMedium = style(45, 52, FontWeight.SemiBold),
    displaySmall = style(36, 44, FontWeight.SemiBold),
    headlineLarge = style(32, 40, FontWeight.SemiBold),
    headlineMedium = style(28, 36, FontWeight.SemiBold),
    headlineSmall = style(24, 32, FontWeight.SemiBold),
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(16, 24, FontWeight.SemiBold, 0.01f),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0.01f),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.02f),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.02f),
    bodySmall = style(12, 16, FontWeight.Normal, 0.03f),
    labelLarge = style(14, 20, FontWeight.Medium, 0.01f),
    labelMedium = style(12, 16, FontWeight.Medium, 0.04f),
    labelSmall = style(11, 16, FontWeight.Medium, 0.04f),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)
