package com.zipper.datingapp.live

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.zipper.datingapp.service.FirebaseService

/**
 * If this device was hosting solo live but heartbeats stopped, clear Firestore live state.
 * Complements [com.zipper.datingapp.service.LiveTaskCleanupService] (Recents swipe) and Cloud Functions.
 */
class LiveHostTeardownWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val authUid = FirebaseAuth.getInstance().currentUser?.uid?.trim().orEmpty()
        if (authUid.isEmpty()) return Result.success()
        val hostingUid = LiveSessionCleanupPrefs.peekHostingLiveUid(applicationContext)
        if (hostingUid != null && hostingUid != authUid) return Result.success()
        val firebase = FirebaseService()
        val activity =
            runCatching { firebase.fetchLiveStreamLastActivityMs(listOf(authUid)) }
                .getOrElse { emptyMap() }[authUid]
                ?: 0L
        val now = System.currentTimeMillis()
        val stale = activity > 0L && (now - activity) > LiveStaleConstants.STALE_ACTIVITY_MS
        val shouldTeardown = hostingUid == authUid && stale
        if (!shouldTeardown) return Result.success()
        Log.w(TAG, "Stale solo live on device uid=$authUid lastActivity=$activity — tearing down")
        runCatching { firebase.endLiveStream(authUid, null) }
            .onFailure { e -> Log.w(TAG, "endLiveStream from worker", e) }
        LiveSessionCleanupPrefs.clearHostingLiveUid(applicationContext)
        return Result.success()
    }

    companion object {
        private const val TAG = "LiveHostTeardownWorker"
    }
}
