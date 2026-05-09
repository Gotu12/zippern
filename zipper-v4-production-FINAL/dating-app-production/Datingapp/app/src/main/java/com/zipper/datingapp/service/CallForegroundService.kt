package com.zipper.datingapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.zipper.datingapp.MainActivity
import com.zipper.datingapp.R

/**
 * Persistent foreground service that keeps the call alive while audio/video is streaming.
 * Started by the call flow; stopped when the call ends.
 *
 * Extras read from the start intent:
 * - [EXTRA_PARTNER_NAME] — display name shown in the notification body
 * - [EXTRA_IS_VIDEO]     — true for video calls, false for voice-only
 */
class CallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createOngoingCallChannelIfNeeded()

        val partnerName = intent?.getStringExtra(EXTRA_PARTNER_NAME)
        val isVideo     = intent?.getBooleanExtra(EXTRA_IS_VIDEO, false) ?: false

        val body = when {
            partnerName != null && isVideo  -> getString(R.string.notif_ongoing_call_body_video, partnerName)
            partnerName != null && !isVideo -> getString(R.string.notif_ongoing_call_body_audio, partnerName)
            else                            -> getString(R.string.notif_ongoing_call_body_fallback)
        }

        // Tapping the notification returns to the active call UI
        val openCallIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentPi = PendingIntent.getActivity(
            this, 0, openCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ONGOING_CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_ongoing_call_title))
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(contentPi)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ONGOING_CALL_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(ONGOING_CALL_NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun createOngoingCallChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                ONGOING_CALL_CHANNEL_ID,
                getString(R.string.notif_channel_ongoing_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_ongoing_desc)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
        )
    }

    companion object {
        const val ONGOING_CALL_CHANNEL_ID      = "ongoing_calls"
        const val ONGOING_CALL_NOTIFICATION_ID = 3001

        const val EXTRA_PARTNER_NAME = "partner_name"
        const val EXTRA_IS_VIDEO     = "is_video"
    }
}
