package com.zipper.datingapp.webrtc

import org.webrtc.PeerConnection

/** Central ICE server list for all WebRTC peer connections (merged STUN + backend relay when available). */
object IceServerCatalog {

    @Volatile
    private var mergedBackendPlusStun: List<PeerConnection.IceServer>? = null

    fun updateFromBackend(backend: List<PeerConnection.IceServer>) {
        mergedBackendPlusStun = IceDefaults.stunOnly() + backend
    }

    /** Uses cached backend ICE merged with Google STUN; falls back to STUN-only if never refreshed. */
    fun currentOrStun(): List<PeerConnection.IceServer> =
        mergedBackendPlusStun?.takeIf { it.isNotEmpty() } ?: IceDefaults.stunOnly()
}

object IceDefaults {
    fun stunOnly(): List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
    )
}
