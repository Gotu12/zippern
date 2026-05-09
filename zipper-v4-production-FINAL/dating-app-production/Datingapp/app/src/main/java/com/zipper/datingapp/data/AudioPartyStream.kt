package com.zipper.datingapp.data

import com.google.firebase.firestore.DocumentSnapshot

/** Firestore `streams/{hostUid}` — Itzo-style audio party seats (WebRTC cohosts use `live_publishers`). */
data class AudioPartyStreamState(
    /** False until Firestore `streams/{host}` exists; avoids treating default [seatCount] as live layout. */
    val streamActive: Boolean = false,
    /** Stage capacity: guest slots `1 .. seatCount-1` around the throne strip (6, 10, 14, or 17 / Itzo-style). */
    val seatCount: Int = 10,
    val seats: Map<Int, AudioPartySeat> = emptyMap(),
    /** Itzo `audio_bg` theme key on `streams/{host}` (`default`, `pink`, `violet`, `teal`). */
    val roomBackgroundKey: String = "default",
    /** Optional promoted guest (Firestore `coHostUserId` on `streams/{hostUid}`). */
    val coHostUserId: String = "",
    val nowPlayingTitle: String = "",
    val nowPlayingArtist: String = "",
    val stageEmoji: String = "",
    val stageEmojiAtMs: Long = 0L,
)

data class AudioPartySeat(
    val index: Int,
    val userId: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val locked: Boolean = false,
    val mutedByHost: Boolean = false,
    val priceCoins: Int = 0,
    val publishingAudio: Boolean = false,
)

fun normalizeAudioPartyStageSeatCount(raw: Int?): Int = when (raw) {
    6 -> 6
    14 -> 14
    17 -> 17
    else -> 10
}

fun normalizeAudioPartyStageSeatCountFromFirestore(raw: Long?): Int =
    normalizeAudioPartyStageSeatCount(raw?.toInt())

fun DocumentSnapshot.toAudioPartyStreamState(): AudioPartyStreamState {
    if (!exists()) return AudioPartyStreamState(streamActive = false)
    val sc = normalizeAudioPartyStageSeatCountFromFirestore(getLong("seatCount"))
    @Suppress("UNCHECKED_CAST")
    val rawSeats = get("seats") as? Map<String, *> ?: emptyMap<String, Any?>()
    val seats = mutableMapOf<Int, AudioPartySeat>()
    for ((k, v) in rawSeats) {
        val idx = k.toIntOrNull() ?: continue
        @Suppress("UNCHECKED_CAST")
        val m = v as? Map<String, *> ?: continue
        seats[idx] = AudioPartySeat(
            index = idx,
            userId = (m["userId"] as? String).orEmpty().trim(),
            displayName = (m["displayName"] as? String).orEmpty().trim(),
            photoUrl = (m["photoUrl"] as? String).orEmpty().trim(),
            locked = truthy(m["locked"]),
            mutedByHost = truthy(m["mutedByHost"]),
            priceCoins = ((m["priceCoins"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
            publishingAudio = truthy(m["publishingAudio"]),
        )
    }
    return AudioPartyStreamState(
        streamActive = true,
        seatCount = sc,
        seats = seats,
        roomBackgroundKey = getString("roomBackgroundKey").orEmpty().trim().ifBlank { "default" },
        coHostUserId = getString("coHostUserId").orEmpty().trim(),
        nowPlayingTitle = getString("nowPlayingTitle").orEmpty().trim(),
        nowPlayingArtist = getString("nowPlayingArtist").orEmpty().trim(),
        stageEmoji = getString("stageEmoji").orEmpty().trim(),
        stageEmojiAtMs = getLong("stageEmojiAtMs") ?: 0L,
    )
}

private fun truthy(v: Any?): Boolean =
    v == true || v == 1L || v == 1 || "${v}" == "1"

/** Guest seat indices `[1, seatCount-1]`. */
fun AudioPartyStreamState.guestSeatIndexRange(): IntRange {
    val last = (seatCount - 1).coerceAtLeast(1)
    return 1..last
}

/** Co-host UIDs publishing mic for `live_publishers/{hostUid}/{uid}` fan-out. */
fun AudioPartyStreamState.activeCoPublisherUids(hostUid: String, excludeUid: String? = null): List<String> {
    val h = hostUid.trim()
    val x = excludeUid?.trim().orEmpty()
    return seats.values.asSequence()
        .filter {
            it.userId.isNotBlank() &&
                it.userId != h &&
                (x.isEmpty() || it.userId != x) &&
                it.publishingAudio &&
                !it.mutedByHost
        }
        .map { it.userId }
        .distinct()
        .toList()
}

sealed class AudioPartyHostSeatAction {
    data class SetLocked(val seatIndex: Int, val locked: Boolean) : AudioPartyHostSeatAction()
    data class SetMuted(val seatIndex: Int, val muted: Boolean) : AudioPartyHostSeatAction()
    data class Kick(val seatIndex: Int) : AudioPartyHostSeatAction()
    data class SetPrice(val seatIndex: Int, val priceCoins: Int) : AudioPartyHostSeatAction()
    /** Promote the seated guest at [seatIndex] to co-host (writes `coHostUserId`). */
    data class SetCoHostFromSeat(val seatIndex: Int) : AudioPartyHostSeatAction()
    data object ClearCoHost : AudioPartyHostSeatAction()
    /**
     * True host transfer: the guest at [seatIndex] becomes the room owner (`live_streams` + `streams` move to their uid).
     * The former host is placed in that seat as a guest.
     */
    data class PromoteSeatGuestToHost(val seatIndex: Int) : AudioPartyHostSeatAction()
}
