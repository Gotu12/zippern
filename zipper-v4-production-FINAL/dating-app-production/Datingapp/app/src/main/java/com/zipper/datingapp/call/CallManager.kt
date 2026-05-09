package com.zipper.datingapp.call

import com.zipper.datingapp.ui.DatingUiState

object CallManager {
    const val RING_TIMEOUT_MS: Long = 30_000L

    fun isBusy(state: DatingUiState): Boolean {
        if (state.isCurrentlyInCall) return true
        // Callee is "busy" for a second invite while an incoming call is still ringing.
        if (state.incomingCall != null) return true
        return false
    }
}
