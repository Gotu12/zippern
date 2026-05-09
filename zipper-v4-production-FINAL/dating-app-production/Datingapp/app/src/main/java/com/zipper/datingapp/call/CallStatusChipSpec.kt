package com.zipper.datingapp.call

object CallStatusChipSpec {
    const val CHIP_BG_COLOR: Int = 0xB31A1A1A.toInt()
    const val CHIP_CORNER_RADIUS_DP: Int = 24
    const val CHIP_HORIZONTAL_PADDING_DP: Int = 14
    const val CHIP_VERTICAL_PADDING_DP: Int = 8
    const val CHIP_TOP_MARGIN_DP: Int = 24
    const val CHIP_TEXT_SIZE_SP: Float = 13f
    const val CHIP_ELEVATION_DP: Int = 8
    const val PULSE_MIN_ALPHA: Float = 0.4f
    const val PULSE_MAX_ALPHA: Float = 1f
    const val PULSE_DURATION_MS: Long = 900L
    const val TERMINAL_HOLD_MS: Long = 2000L

    data class Visual(
        val glyph: String,
        val label: String,
        val colorInt: Int,
        val isTerminal: Boolean = false,
        val shouldPulse: Boolean = false
    )

    fun visualFor(status: CallStatus): Visual {
        return when (status) {
            CallStatus.CONNECTING -> Visual("•", "Connecting...", 0xFFFFFFFF.toInt(), shouldPulse = true)
            CallStatus.RINGING -> Visual("•", "Calling...", 0xFFFFFFFF.toInt(), shouldPulse = true)
            CallStatus.CONNECTED -> Visual("✓", "Connected", 0xFF4ADE80.toInt())
            CallStatus.BUSY -> Visual("!", "User is busy", 0xFFFF6B6B.toInt(), isTerminal = true)
            CallStatus.DECLINED -> Visual("!", "Call declined", 0xFFFF6B6B.toInt(), isTerminal = true)
            CallStatus.TIMEOUT -> Visual("!", "User unavailable", 0xFFFF6B6B.toInt(), isTerminal = true)
        }
    }
}
