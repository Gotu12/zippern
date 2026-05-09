package com.zipper.datingapp.webrtc

/**
 * Face-filter presets applied by [WebRTCManager.BeautyCapturerObserver] in the WebRTC pipeline
 * when Camera Kit is **not** active, and used as UI state when Camera Kit maps presets to lenses
 * ([WebRTCManager.setFilterType] with [com.zipper.datingapp.camera.CameraKitWebRtcBridge]).
 *
 * **Beauty filters** (BEAUTY … FAIR) apply YUV luma/chroma adjustments to the face region.
 * Legacy canvas/ML Kit AR sticker overlays were removed; use Snap lenses when
 * [com.zipper.datingapp.BuildConfig.SNAP_CAMERA_KIT_CONFIGURED] is true.
 *
 * Use [WebRTCManager.setFilterType] to change the active filter at runtime.
 * [FilterType.NONE] is the default and incurs no beauty pass-through work (legacy path).
 */
enum class FilterType {
    /** No filter — pure pass-through (default). */
    NONE,

    /** Subtle skin-brightening: additive luma boost (+18). */
    BEAUTY,

    /** Soft matte look: moderate luma boost (+10) with 12 % chroma desaturation. */
    SMOOTH,

    /** High-key daylight glow: aggressive luma boost (+30). */
    BRIGHT,

    /** Warm golden glow: luma +22 with warm chroma bias in V/U planes. */
    GLOW,

    /** Porcelain / fair-skin: luma +28 with cool chroma bias. */
    FAIR;

    val displayName: String
        get() = when (this) {
            NONE -> "None"
            BEAUTY -> "Beauty"
            SMOOTH -> "Smooth"
            BRIGHT -> "Bright"
            GLOW -> "Glow"
            FAIR -> "Fair"
        }

    /** True for beauty/color-grade types (non-NONE). */
    val isBeauty: Boolean
        get() = this != NONE
}
