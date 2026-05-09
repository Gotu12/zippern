package com.zipper.datingapp.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Visible “staging” rows in live overlays; scroll reveals older lines. */
const val LIVE_CHAT_STAGING_VISIBLE_MESSAGES: Int = 5

/** Rough row height (text + padding) for viewport sizing. */
private const val LIVE_CHAT_STAGING_ROW_DP: Int = 44

/**
 * Max height for the on-screen chat staging strip (~[LIVE_CHAT_STAGING_VISIBLE_MESSAGES] rows + gaps).
 * Composer sits outside the frosted panel on glass / PK neon layouts.
 */
fun liveChatStagingViewportMaxHeight(): Dp {
    val rows = LIVE_CHAT_STAGING_VISIBLE_MESSAGES
    val gaps = 4 * (rows - 1).coerceAtLeast(0)
    return (rows * LIVE_CHAT_STAGING_ROW_DP + gaps).dp
}

/**
 * Max height while reading chat history (staging strip stays shorter via [liveChatStagingViewportMaxHeight]).
 * **38%** of screen logical height.
 */
fun liveChatHistoryStripMaxHeight(screenHeightDp: Int): Dp =
    (screenHeightDp * 0.38f).dp

/** Slot-machine style scroll toward newest after layout updates (staging / live edge). */
suspend fun rollerAnimateScrollToLatest(scrollState: ScrollState) {
    repeat(2) { pass ->
        delay(if (pass == 0) 40L else 24L)
        scrollState.scrollTo(scrollState.maxValue)
    }
    val target = scrollState.maxValue
    if (target <= 0) return
    val start = scrollState.value.coerceIn(0, target)
    val delta = target - start
    if (delta <= 6) {
        scrollState.scrollTo(target)
        return
    }
    val steps = 14
    repeat(steps) { i ->
        val t = (i + 1) / steps.toFloat()
        val eased = 1f - (1f - t) * (1f - t)
        scrollState.scrollTo((start + delta * eased).toInt().coerceIn(0, scrollState.maxValue))
        delay(20L)
    }
    scrollState.scrollTo(scrollState.maxValue)
}
