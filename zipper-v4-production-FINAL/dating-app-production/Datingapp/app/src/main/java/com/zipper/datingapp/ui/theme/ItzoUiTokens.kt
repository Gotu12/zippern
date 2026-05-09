package com.zipper.datingapp.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Itzo-aligned chrome shared by PK HUD, live audio party, discovery CTAs, and 1:1 call surfaces.
 * (Hex values match `PkArenaHud` / Itzo `pk_item_layout` / `colors.xml` / `activity_video_call.xml`.)
 */
object ItzoUiTokens {
    val PkBarLeft = Color(0xFFAB47BC)
    val PkBarRight = Color(0xFFD81B60)
    val FrameTeal = Color(0xFF00E6CB)
    val FrameViolet = Color(0xFF9E74F7)
    val AppAccent = Color(0xFFFF8C42)
    val NeonAccent = Color(0xFFFF2BE7)

    /** Itzo `app_bg_color` — root behind streams when not pure black (solo host/viewer). */
    val AppBackgroundDeep = Color(0xFF0F0F0F)

    /** Itzo `activity_video_call` top/bottom bars — #E6000000. */
    val ChromeBarScrim = Color(0xE6000000)

    /** Itzo `black_transparent` — live host / audience nameplate pill (`live_host_shappe` feel). */
    val LiveHostNameplateScrim = Color(0xA7000000)

    /** Itzo `black_transparentt` — full-screen veil on audio party host (`activity_audio_host`). */
    val AudioPartyStageVeil = Color(0x4A000000)

    /** Dark stage behind mic-only live / voice call (teal–violet wash). */
    val StageTop = Color(0xFF0A1520)
    val StageMid = Color(0xFF120F1A)
    val StageBottom = Color(0xFF070812)

    val CallAccept = FrameTeal
    val CallDecline = Color(0xFFE53935)

    fun stageBackgroundBrush(): Brush = Brush.verticalGradient(
        listOf(StageTop, StageMid, StageBottom),
    )

    fun livePrimaryCtaBrush(): Brush = Brush.horizontalGradient(
        listOf(PkBarLeft, PkBarRight),
    )

    /** Warm light room — peach / pink wash (dialogs / previews). */
    val AudioPartyRoomTop = Color(0xFFFFF1EA)
    val AudioPartyRoomMid = Color(0xFFFFE3EE)
    val AudioPartyRoomBottom = Color(0xFFFFD7E8)

    /** Full-bleed dusty mauve stack (Itzo-style live room). */
    val AudioPartyBleedTop = Color(0xFF453545)
    val AudioPartyBleedMid = Color(0xFF5A4360)
    val AudioPartyBleedBottom = Color(0xFF725565)

    fun audioPartyFullBleedRoomBrush(): Brush = Brush.verticalGradient(
        listOf(AudioPartyBleedTop, AudioPartyBleedMid, AudioPartyBleedBottom),
    )

    /** Primary copy on the light room (titles, labels). */
    val AudioPartyOnRoomText = Color(0xFF4A3040)
    val AudioPartyOnRoomMuted = Color(0xFF6B5560)

    /** High-contrast copy on full-bleed mauve + veil. */
    val AudioPartyOnBleedText = Color(0xFFF8F0F4)
    val AudioPartyOnBleedMuted = Color(0xFFC9B8C4)

    /** Bean / economy chip on the light room header. */
    val AudioPartyBeanChipBg = Color(0xFFE9D5FF)
    val AudioPartyBeanChipContent = Color(0xFF5C3566)

    /** Soft scrim so floating stage content stays readable on the pastel background. */
    val AudioPartyRoomContentVeil = Color(0x12000000)

    fun audioPartyRoomBackgroundBrush(): Brush = Brush.verticalGradient(
        listOf(AudioPartyRoomTop, AudioPartyRoomMid, AudioPartyRoomBottom),
    )

    /** Seat / chip labels on white(ish) surfaces over the mauve room. */
    val AudioPartyChipLabelOnLight = Color(0xFF3A2838)
}
