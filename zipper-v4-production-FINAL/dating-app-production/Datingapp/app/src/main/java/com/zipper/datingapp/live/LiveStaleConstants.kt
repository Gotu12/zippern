package com.zipper.datingapp.live

/** Shared thresholds for live discovery and ghost-host teardown. */
object LiveStaleConstants {
    /** Host must ping `live_streams/{id}.hostHeartbeatAtMs` at least this often while live. */
    const val HEARTBEAT_INTERVAL_MS: Long = 30_000L

    /** No heartbeat / activity for this long → hide from Discover and allow server/client teardown. */
    const val STALE_ACTIVITY_MS: Long = 120_000L
}
