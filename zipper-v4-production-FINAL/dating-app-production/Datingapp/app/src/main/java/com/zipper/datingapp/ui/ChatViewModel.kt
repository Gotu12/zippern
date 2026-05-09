package com.zipper.datingapp.ui

import androidx.lifecycle.ViewModel
import com.zipper.datingapp.data.Gift

/**
 * Chat-specific facade so chat UI can keep a narrow API surface.
 * Real-time messages are loaded in [com.zipper.datingapp.ui.DatingViewModel.startObservingMessages]
 * via Firestore [com.zipper.datingapp.service.FirebaseService.observeMessages] (snapshot listeners, timestamp ASC).
 */
class ChatViewModel : ViewModel() {
    fun initiateCall(isVideoCall: Boolean, receiverId: String, delegate: (Boolean, String) -> Unit) {
        delegate(isVideoCall, receiverId)
    }

    fun sendGiftMessage(receiverId: String, gift: Gift, delegate: (String, Gift) -> Unit) {
        delegate(receiverId, gift)
    }
}
