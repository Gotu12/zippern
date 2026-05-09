package com.zipper.datingapp.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.zipper.datingapp.MainActivity
import com.zipper.datingapp.R

/**
 * Handles all FCM push notifications for Zipper Live:
 *
 * | type               | Channel          | Description                                   |
 * |--------------------|------------------|-----------------------------------------------|
 * | incoming_call      | incoming_calls   | Full-screen heads-up with Accept / Decline    |
 * | missed_call        | incoming_calls   | "Missed call from X" heads-up                 |
 * | message            | messages         | DM heads-up with BigTextStyle + quick-reply   |
 * | live_start         | live_streams     | "X is now live" — tapping opens LiveScreen    |
 * | audio_party_lobby  | live_streams     | Opens Live tab audio-party lobby               |
 * | gift_received      | gifts            | "X sent you a gift" celebration notification  |
 * | new_follower       | social           | "X started following you"                     |
 * | profile_liked      | social           | "X liked your profile"                        |
 *
 * Every notify() is guarded by POST_NOTIFICATIONS on API 33+ (never silently fails).
 */
class CallFirebaseMessagingService : FirebaseMessagingService() {

    private val tag = "FCM"

    // ── Token refresh ──────────────────────────────────────────────────────────────────────────────

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(tag, "FCM token refreshed — saving to Firestore")
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .set(
                mapOf(
                    "fcmToken" to token,
                    "tokenUpdatedAtMs" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener { Log.d(tag, "FCM token saved uid=$uid") }
            .addOnFailureListener { e -> Log.e(tag, "FCM token save failed uid=$uid", e) }
    }

    // ── Message dispatch ───────────────────────────────────────────────────────────────────────────

    override fun onMessageReceived(message: RemoteMessage) {
        ensureAllChannels()
        when (message.data["type"]) {
            "incoming_call"  -> handleIncomingCall(message.data)
            "missed_call"    -> handleMissedCall(message.data)
            "message"        -> handleNewMessage(message.data)
            "live_start"     -> handleLiveStart(message.data)
            "audio_party_lobby" -> handleOpenAudioPartyLobby(message.data)
            "gift_received"  -> handleGiftReceived(message.data)
            "new_follower"   -> handleNewFollower(message.data)
            "profile_liked"  -> handleProfileLiked(message.data)
            else             -> Log.w(tag, "FCM: unhandled type '${message.data["type"]}'")
        }
    }

    // ── Handlers ───────────────────────────────────────────────────────────────────────────────────

    private fun handleIncomingCall(data: Map<String, String>) {
        val callerName = data["callerName"] ?: getString(R.string.notif_incoming_call_body_fallback)
        val callerId   = data["callerId"]   ?: ""
        val roomId     = data["roomId"]     ?: ""
        val isVideoCall = when (data["isVideoCall"]?.lowercase()) {
            "false", "0", "no" -> false
            else -> true
        }

        // Full-screen / content intent — opens the call UI
        val openCallIntent = Intent(this, MainActivity::class.java).apply {
            action = "com.zipper.datingapp.action.OPEN_INCOMING_CALL"
            flags  = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_INCOMING_CALL, true)
            putExtra(EXTRA_CALLER_ID,     callerId)
            putExtra(EXTRA_CALLER_NAME,   callerName)
            putExtra(EXTRA_ROOM_ID,       roomId)
            putExtra(EXTRA_IS_VIDEO_CALL, isVideoCall)
        }
        val fullScreenPi = PendingIntent.getActivity(
            this, REQ_CALL_FULL,
            openCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Accept action — same intent; MainActivity reads EXTRA_INCOMING_CALL = true
        val acceptIntent = Intent(this, MainActivity::class.java).apply {
            action = "com.zipper.datingapp.action.OPEN_INCOMING_CALL"
            flags  = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_INCOMING_CALL, true)
            putExtra(EXTRA_CALLER_ID,     callerId)
            putExtra(EXTRA_CALLER_NAME,   callerName)
            putExtra(EXTRA_ROOM_ID,       roomId)
            putExtra(EXTRA_IS_VIDEO_CALL, isVideoCall)
            putExtra(EXTRA_AUTO_ACCEPT,   true)
        }
        val acceptPi = PendingIntent.getActivity(
            this, REQ_CALL_ACCEPT,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline action — broadcast to dismiss the notification
        val declineIntent = Intent(this, MainActivity::class.java).apply {
            action = "com.zipper.datingapp.action.DECLINE_CALL"
            flags  = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_ROOM_ID, roomId)
            putExtra(EXTRA_CALLER_ID, callerId)
        }
        val declinePi = PendingIntent.getActivity(
            this, REQ_CALL_DECLINE,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CH_INCOMING_CALLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_incoming_call_title))
            .setContentText(getString(R.string.notif_incoming_call_body, callerName))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_notification,
                    getString(R.string.notif_call_accept),
                    acceptPi
                )
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_notification,
                    getString(R.string.notif_call_decline),
                    declinePi
                )
            )
            .build()

        notifySafe(NOTIF_INCOMING_CALL, notification)
    }

    private fun handleMissedCall(data: Map<String, String>) {
        val callerName = data["callerName"] ?: getString(R.string.notif_incoming_call_body_fallback)

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, REQ_MISSED,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CH_INCOMING_CALLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_missed_call_title))
            .setContentText(getString(R.string.notif_missed_call_body, callerName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        notifySafe(NOTIF_MISSED_CALL, notification)
    }

    private fun handleNewMessage(data: Map<String, String>) {
        val senderName = data["senderName"] ?: getString(R.string.notif_new_message_title)
        val preview    = data["preview"]    ?: getString(R.string.notif_new_message_body)
        val senderId   = data["senderId"]   ?: ""

        val msgIntent = Intent(this, MainActivity::class.java).apply {
            action = "com.zipper.datingapp.action.OPEN_MESSAGES"
            flags  = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SENDER_ID,   senderId)
            putExtra(EXTRA_SENDER_NAME, senderName)
        }
        val pi = PendingIntent.getActivity(
            this, REQ_MSG,
            msgIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Quick-reply remote input
        val replyInput = RemoteInput.Builder(REPLY_KEY)
            .setLabel(getString(R.string.notif_new_message_reply_hint))
            .build()
        val replyIntent = Intent(this, MainActivity::class.java).apply {
            action = "com.zipper.datingapp.action.OPEN_MESSAGES"
            flags  = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SENDER_ID,   senderId)
            putExtra(EXTRA_SENDER_NAME, senderName)
        }
        val replyPi = PendingIntent.getActivity(
            this, REQ_MSG_REPLY,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            getString(R.string.notif_new_message_reply_hint),
            replyPi
        ).addRemoteInput(replyInput).build()

        val notifId = senderId.hashCode().let { if (it == Int.MIN_VALUE) 0 else Math.abs(it) }

        val notification = NotificationCompat.Builder(this, CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(senderName)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .addAction(replyAction)
            .build()

        notifySafe(notifId, notification)
    }

    private fun handleLiveStart(data: Map<String, String>) {
        val hostName = data["hostName"] ?: getString(R.string.notif_live_start_title)

        val liveIntent = Intent(this, MainActivity::class.java).apply {
            action = "com.zipper.datingapp.action.OPEN_LIVE"
            flags  = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_HOST_ID,   data["hostId"])
            putExtra(EXTRA_HOST_NAME, hostName)
        }
        val pi = PendingIntent.getActivity(
            this, REQ_LIVE,
            liveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CH_LIVE_STREAMS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_live_start_title))
            .setContentText(getString(R.string.notif_live_start_body, hostName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        notifySafe(
            data["hostId"].hashCode().let { if (it == Int.MIN_VALUE) 0 else Math.abs(it) },
            notification
        )
    }

    /** Opens Live tab on the audio-party lobby (discover surface + audio-only filter). */
    private fun handleOpenAudioPartyLobby(@Suppress("UNUSED_PARAMETER") data: Map<String, String>) {
        val lobbyIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_AUDIO_PARTY_LOBBY
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            REQ_AUDIO_PARTY_LOBBY,
            lobbyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CH_LIVE_STREAMS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.live_audio_party_lobby_title))
            .setContentText(getString(R.string.live_audio_party_lobby_notif_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        notifySafe(REQ_AUDIO_PARTY_LOBBY, notification)
    }

    private fun handleGiftReceived(data: Map<String, String>) {
        val senderName = data["senderName"] ?: getString(R.string.notif_gift_received_body_fallback)
        val giftName   = data["giftName"]   ?: "gift"

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, REQ_GIFT,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CH_GIFTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_gift_received_title))
            .setContentText(getString(R.string.notif_gift_received_body, senderName, giftName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        notifySafe(
            "gift_${data["senderId"]}".hashCode().let { if (it == Int.MIN_VALUE) 0 else Math.abs(it) },
            notification
        )
    }

    private fun handleNewFollower(data: Map<String, String>) {
        val followerName = data["followerName"] ?: getString(R.string.notif_new_follower_body_fallback)

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, REQ_SOCIAL,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CH_SOCIAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_new_follower_title))
            .setContentText(getString(R.string.notif_new_follower_body, followerName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        notifySafe(
            "follow_${data["followerId"]}".hashCode().let { if (it == Int.MIN_VALUE) 0 else Math.abs(it) },
            notification
        )
    }

    private fun handleProfileLiked(data: Map<String, String>) {
        val likerName = data["likerName"] ?: getString(R.string.notif_profile_liked_body_fallback)

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, REQ_LIKED,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CH_SOCIAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_profile_liked_title))
            .setContentText(getString(R.string.notif_profile_liked_body, likerName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        notifySafe(
            "like_${data["likerId"]}".hashCode().let { if (it == Int.MIN_VALUE) 0 else Math.abs(it) },
            notification
        )
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────────────

    private fun notifySafe(id: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(tag, "POST_NOTIFICATIONS not granted — notification id=$id suppressed")
                return
            }
        }
        try {
            NotificationManagerCompat.from(this).notify(id, notification)
        } catch (e: SecurityException) {
            Log.e(tag, "notify failed id=$id", e)
        }
    }

    /** Creates all notification channels (idempotent on API 26+, no-op on older). */
    private fun ensureAllChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CH_INCOMING_CALLS,
                getString(R.string.notif_channel_calls_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notif_channel_calls_desc)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setBypassDnd(true)
                enableVibration(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CH_MESSAGES,
                getString(R.string.notif_channel_messages_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notif_channel_messages_desc)
                enableVibration(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CH_LIVE_STREAMS,
                getString(R.string.notif_channel_live_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notif_channel_live_desc)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CH_GIFTS,
                getString(R.string.notif_channel_gifts_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notif_channel_gifts_desc)
                enableVibration(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CH_SOCIAL,
                getString(R.string.notif_channel_social_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notif_channel_social_desc)
            }
        )
    }

    companion object {
        const val CH_INCOMING_CALLS  = "incoming_calls"
        const val CH_MESSAGES        = "messages"
        const val CH_LIVE_STREAMS    = "live_streams"
        const val CH_GIFTS           = "gifts"
        const val CH_SOCIAL          = "social"

        const val NOTIF_INCOMING_CALL = 2001
        const val NOTIF_MISSED_CALL   = 2002

        private const val REQ_CALL_FULL    = 1001
        private const val REQ_CALL_ACCEPT  = 1002
        private const val REQ_CALL_DECLINE = 1003
        private const val REQ_MSG          = 1004
        private const val REQ_MSG_REPLY    = 1005
        private const val REQ_LIVE         = 1006
        private const val REQ_GIFT         = 1007
        private const val REQ_SOCIAL       = 1008
        private const val REQ_LIKED        = 1009
        private const val REQ_MISSED       = 1010
        private const val REQ_AUDIO_PARTY_LOBBY = 1011

        /** [Intent.action] for opening the Live tab audio-party lobby (matches AndroidManifest / [handleOpenAudioPartyLobby]). */
        const val ACTION_OPEN_AUDIO_PARTY_LOBBY = "com.zipper.datingapp.action.OPEN_AUDIO_PARTY_LOBBY"

        const val REPLY_KEY = "reply_text"

        const val EXTRA_INCOMING_CALL = "incoming_call"
        const val EXTRA_CALLER_ID     = "caller_id"
        const val EXTRA_CALLER_NAME   = "caller_name"
        const val EXTRA_ROOM_ID       = "room_id"
        const val EXTRA_IS_VIDEO_CALL = "is_video_call"
        const val EXTRA_AUTO_ACCEPT   = "auto_accept"
        const val EXTRA_SENDER_ID     = "sender_id"
        const val EXTRA_SENDER_NAME   = "sender_name"
        const val EXTRA_HOST_ID       = "host_id"
        const val EXTRA_HOST_NAME     = "host_name"
    }
}
