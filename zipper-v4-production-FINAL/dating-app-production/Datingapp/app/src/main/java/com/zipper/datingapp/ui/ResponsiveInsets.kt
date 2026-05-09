package com.zipper.datingapp.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Merges [WindowInsets.navigationBars] with extra padding for scrollable content
 * so list/grid items stay above the gesture navigation bar.
 */
@Composable
fun paddingWithNavigationBars(
    bottomExtra: Dp = 0.dp,
    topExtra: Dp = 0.dp,
    startExtra: Dp = 0.dp,
    endExtra: Dp = 0.dp
): PaddingValues {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val nav = WindowInsets.navigationBars
    return PaddingValues(
        start = startExtra + with(density) { nav.getLeft(density, layoutDirection).toDp() },
        top = topExtra + with(density) { nav.getTop(density).toDp() },
        end = endExtra + with(density) { nav.getRight(density, layoutDirection).toDp() },
        bottom = bottomExtra + with(density) { nav.getBottom(density).toDp() }
    )
}
