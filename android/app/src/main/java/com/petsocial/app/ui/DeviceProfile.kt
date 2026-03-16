package com.petsocial.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
enum class PhoneSizeClass {
    Compact,
    Standard,
    Large,
}

@Composable
fun rememberPhoneSizeClass(): PhoneSizeClass {
    val configuration = LocalConfiguration.current
    val smallestWidth = configuration.smallestScreenWidthDp
    val width = configuration.screenWidthDp
    return remember(smallestWidth, width) {
        phoneSizeClassFor(smallestWidthDp = smallestWidth, screenWidthDp = width)
    }
}

fun phoneSizeClassFor(
    smallestWidthDp: Int,
    screenWidthDp: Int,
): PhoneSizeClass = when {
    smallestWidthDp >= 500 || screenWidthDp >= 500 -> PhoneSizeClass.Large
    screenWidthDp <= 360 -> PhoneSizeClass.Compact
    else -> PhoneSizeClass.Standard
}

fun contentHorizontalPadding(sizeClass: PhoneSizeClass): Dp = when (sizeClass) {
    PhoneSizeClass.Compact -> 8.dp
    PhoneSizeClass.Standard -> 12.dp
    PhoneSizeClass.Large -> 20.dp
}

fun scannerPreviewHeightDp(
    sizeClass: PhoneSizeClass,
    screenHeightDp: Int,
): Dp {
    val baseline = when (sizeClass) {
        PhoneSizeClass.Compact -> 260
        PhoneSizeClass.Standard -> 320
        PhoneSizeClass.Large -> 420
    }
    val maxHeight = (screenHeightDp * 0.6f).toInt().coerceAtLeast(220)
    return baseline.coerceAtMost(maxHeight).dp
}
