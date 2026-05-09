package com.zipper.datingapp.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Three canonical width buckets mirroring Material3 WindowSizeClass breakpoints.
 * Computed once from [BoxWithConstraints.maxWidth] in DatingAppApp and propagated
 * via [LocalWindowWidthClass] so any composable can read it without parameter threading.
 */
enum class AppWindowWidthClass { Compact, Medium, Expanded }

/**
 * Max width for centered column layouts on tablets, foldables inner display, and landscape split-screen.
 * Matches MainActivity shell ([HomeScreen], Live discover).
 */
val AppResponsiveContentMaxWidth = 960.dp

/** Horizontal gutter for scrollable / list content by width bucket. */
fun AppWindowWidthClass.defaultHorizontalContentPadding(): Dp = when (this) {
    AppWindowWidthClass.Compact -> 12.dp
    AppWindowWidthClass.Medium -> 16.dp
    AppWindowWidthClass.Expanded -> 20.dp
}

/** Returns the [AppWindowWidthClass] for this screen width. */
fun Dp.toWindowWidthClass(): AppWindowWidthClass = when {
    this < 600.dp -> AppWindowWidthClass.Compact
    this < 840.dp -> AppWindowWidthClass.Medium
    else          -> AppWindowWidthClass.Expanded
}

/**
 * CompositionLocal carrying the current [AppWindowWidthClass].
 * Default is [AppWindowWidthClass.Compact] so previews and tests
 * that do not provide a value behave like a phone.
 */
val LocalWindowWidthClass = compositionLocalOf { AppWindowWidthClass.Compact }
