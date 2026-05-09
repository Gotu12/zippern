package com.zipper.datingapp.webrtc

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RtpSender

/**
 * Cellular / metered-aware capture, encode bitrate, and FPS policy.
 */
internal fun isLikelyCellularOrMetered(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    val cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    return cellular || !unmetered
}

internal fun captureTargetFps(context: Context): Int =
    if (isLikelyCellularOrMetered(context)) 24 else 30

/** Max outbound video bitrate for sender [RtpParameters] tuning (bps). */
internal fun outboundVideoMaxBitrateBps(context: Context): Int {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return 900_000
    val net = cm.activeNetwork ?: return 900_000
    val caps = cm.getNetworkCapabilities(net) ?: return 900_000
    val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    val ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    return when {
        (wifi || ethernet) && unmetered -> 2_500_000
        wifi || ethernet -> 1_400_000
        else -> 850_000
    }
}

/**
 * Applies encoder bitrate / framerate caps on outbound video senders after SDP negotiation.
 * Safe to call multiple times (e.g. after setLocalDescription and again on ICE connected).
 */
internal fun applyOutboundVideoBitrate(peerConnection: PeerConnection?, context: Context) {
    val pc = peerConnection ?: return
    val maxBps = outboundVideoMaxBitrateBps(context)
    val maxFps = captureTargetFps(context).coerceIn(15, 30)
    for (sender in pc.senders) {
        val track = sender.track() ?: continue
        if (track.kind() != MediaStreamTrack.VIDEO_TRACK_KIND) continue
        applyVideoSenderBitrate(sender, maxBps, maxFps)
    }
}

private fun applyVideoSenderBitrate(sender: RtpSender, maxBps: Int, maxFps: Int) {
    try {
        val params = sender.parameters ?: return
        val encodings = params.encodings ?: return
        if (encodings.isEmpty()) return
        var changed = false
        for (encoding in encodings) {
            if (encoding.maxBitrateBps == null || (encoding.maxBitrateBps ?: 0) > maxBps) {
                encoding.maxBitrateBps = maxBps
                changed = true
            }
            val curFps = encoding.maxFramerate
            if (curFps == null || curFps > maxFps) {
                encoding.maxFramerate = maxFps
                changed = true
            }
        }
        if (changed) {
            sender.parameters = params
        }
    } catch (e: Exception) {
        android.util.Log.w("WebRtcNetworkPolicy", "applyVideoSenderBitrate failed", e)
    }
}
