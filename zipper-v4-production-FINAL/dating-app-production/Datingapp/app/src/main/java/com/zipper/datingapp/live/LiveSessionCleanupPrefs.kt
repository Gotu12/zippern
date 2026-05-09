package com.zipper.datingapp.live

import android.content.Context

/**
 * Persists live session hints so [com.zipper.datingapp.service.LiveTaskCleanupService] can fix
 * Firestore when the process dies without [androidx.lifecycle] teardown (swipe from Recents, etc.).
 */
object LiveSessionCleanupPrefs {
    private const val PREF = "zipper_live_cleanup_v1"
    private const val PENDING_VIEWER_STREAM = "pending_viewer_stream_id"
    private const val HOSTING_LIVE_UID = "hosting_live_uid"

    fun setPendingViewerCounted(ctx: Context, streamId: String) {
        val id = streamId.trim()
        if (id.isEmpty()) return
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(PENDING_VIEWER_STREAM, id)
            .apply()
    }

    fun clearPendingViewer(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(PENDING_VIEWER_STREAM)
            .apply()
    }

    /**
     * Returns the stream id we counted as a viewer (+1) without a matching [stopWatching]; clears stored key.
     */
    fun consumePendingViewerDecrement(ctx: Context): String? {
        val prefs = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val id = prefs.getString(PENDING_VIEWER_STREAM, null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        prefs.edit().remove(PENDING_VIEWER_STREAM).apply()
        return id
    }

    fun setHostingLiveUid(ctx: Context, hostUid: String?) {
        val p = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        val u = hostUid?.trim().orEmpty()
        if (u.isEmpty()) p.remove(HOSTING_LIVE_UID) else p.putString(HOSTING_LIVE_UID, u)
        p.apply()
    }

    fun clearHostingLiveUid(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(HOSTING_LIVE_UID)
            .apply()
    }

    /**
     * If we recorded a solo host session and the process died before [toggleLive] off, return uid and clear.
     */
    fun consumeHostingLiveUid(ctx: Context): String? {
        val prefs = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val id = prefs.getString(HOSTING_LIVE_UID, null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        prefs.edit().remove(HOSTING_LIVE_UID).apply()
        return id
    }

    fun peekHostingLiveUid(ctx: Context): String? =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(HOSTING_LIVE_UID, null)?.trim()?.takeIf { it.isNotEmpty() }
}
