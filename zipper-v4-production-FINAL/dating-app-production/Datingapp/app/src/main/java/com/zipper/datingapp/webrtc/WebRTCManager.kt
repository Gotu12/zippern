package com.zipper.datingapp.webrtc

import android.Manifest
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.media.AudioDeviceInfo
import android.media.AudioDeviceCallback
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.annotation.CheckResult
import java.util.concurrent.atomic.AtomicInteger
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.snap.camerakit.lenses.LensesComponent
import com.snap.camerakit.supported
import com.zipper.datingapp.BuildConfig
import com.zipper.datingapp.camera.CameraKitVideoCapturer
import com.zipper.datingapp.camera.CameraKitWebRtcBridge
import com.zipper.datingapp.service.FirebaseService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.io.ByteArrayOutputStream
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.webrtc.*
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.*
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * WebRTC-based live/call video. [PeerConnectionFactory] and [SurfaceViewRenderer.init] are deferred until
 * the UI has attached surfaces (see [runAfterSurfaceReady]) to avoid EGL / view-hierarchy crashes.
 *
 * **1:1 (calls / PK)** — `broadcastMode = false`: signaling at `rooms/{roomId}/offer`, `answer`,
 * `streamerCandidates`, `viewerCandidates`.
 *
 * **Live broadcast** — `broadcastMode = true`: host listens to `rooms/{roomId}/activeViewers/{viewerUid}`
 * and creates one [PeerConnection] per viewer; SDP/ICE live under `rooms/{roomId}/peers/{viewerUid}/`.
 * Viewers register `activeViewers/{myUid}` (with `onDisconnect().removeValue()`), then signal on
 * `peers/{myUid}/`. Ensure Realtime Database rules allow authenticated users to read/write these paths
 * for the appropriate `roomId`.
 *
 * **PK co-publisher (second camera for spectators)** — set [broadcastLivePublishersSessionId] and
 * [broadcastLivePublishersPublisherUid] so signaling uses
 * `live_publishers/{sessionId}/{publisherUid}/…` instead of `rooms/{roomId}/…` (same child layout as
 * `rooms`). [roomId] is still used for logging and legacy paths when the live_publishers pair is absent.
 */
class WebRTCManager(
    private val context: Context,
    private val roomId: String,
    private val localView: SurfaceViewRenderer,
    private val remoteView: SurfaceViewRenderer,
    private val onPeerDisconnected: (() -> Unit)? = null,
    private val onConnectionStateChanged: ((PeerConnection.IceConnectionState?) -> Unit)? = null,
    /**
     * When true, live broadcast uses per-viewer signaling under `rooms/{roomId}/peers/{viewerUid}/`
     * and `activeViewers/{viewerUid}` so the host maintains one [PeerConnection] per viewer.
     */
    private val broadcastMode: Boolean = false,
    /** Current user's uid; required for broadcast viewers (signaling path `peers/{this}/...`). */
    private val viewerSignalingId: String? = null,
    /**
     * When `broadcastMode` and the viewer is receive-only: if false, SDP omits remote audio and incoming
     * audio tracks are disabled (use for a second video-only connection, e.g. PK guest tile while host
     * connection carries audio).
     */
    private val broadcastViewerReceiveAudio: Boolean = true,
    /**
     * Non-null pair switches broadcast signaling root to
     * `live_publishers/{sessionId}/{publisherUid}/` (mirrors `rooms/{roomId}` shape).
     */
    private val broadcastLivePublishersSessionId: String? = null,
    private val broadcastLivePublishersPublisherUid: String? = null,
    /**
     * When true, 1:1 calls skip local camera capture and do not add a local video track.
     */
    private val audioOnly: Boolean = false,
    /**
     * PK arena in [com.zipper.datingapp.LiveStreamActivity]: two equal [SurfaceViewRenderer] tiles side by side.
     * When false, local uses [SurfaceViewRenderer.setZOrderMediaOverlay](true) so PIP sits above full-screen remote;
     * that z-order makes the local tile paint over the remote tile in a 50/50 split — remote looks black.
     */
    private val pkArenaSideBySide: Boolean = false,
    /**
     * Share an existing factory/EGL from another [WebRTCManager] (e.g. second live viewer link). When set,
     * [onDestroy] does **not** dispose the factory or EGL.
     */
    private val injectedPeerConnectionFactory: PeerConnectionFactory? = null,
    private val injectedEglBase: EglBase? = null,
    /** When false, do not write to global remote/local video StateFlows (dual live viewer). */
    private val publishGlobalMediaStreams: Boolean = true,
    /**
     * When true, the host path applies external sequencing (Camera Kit prewarm + stagger before
     * WebRTC); skip the internal [scheduleStartCallAfterCameraKitStagger] delay in [startCall].
     */
    private val skipCameraKitInternalStagger: Boolean = false,
    /** When false, do not assign [companion object] [currentManager]. */
    private val takeCurrentManagerSlot: Boolean = true,
    /**
     * When false, live host capture uses [Camera2Enumerator] / default [CameraVideoCapturer] instead of
     * Snap Camera Kit (graceful degradation on low-RAM or under-3GB devices).
     */
    private val useSnapCameraKitPipeline: Boolean = true,
    /**
     * Remote party Firebase uid (1:1 private calls). When set, publishes RTDB presence under
     * `rooms/{roomId}/call_participant_presence/{uid}` to detect unexpected disconnects.
     */
    private val callRemotePeerUid: String? = null,
    /** RTDB presence: peer node removed — prefer over [onPeerDisconnected] when set. */
    private val onPeerPresenceLost: (() -> Unit)? = null,
    /** ICE/PC failure recovery exhausted ([scheduleIceFailureRecovery]) — prefer over [onPeerDisconnected] when set. */
    private val onCallFailed: (() -> Unit)? = null,
    /** Stall watchdog / full reconnect — UI may show "Reconnecting… (attempt n/max)". */
    private val onStallWatchdogReconnect: ((attempt: Int, maxAttempts: Int) -> Unit)? = null,
    /** Local camera could not resume after app foregrounding / process lifecycle. */
    private val onCameraCaptureResumeFailed: (() -> Unit)? = null,
) {
    /**
     * Live viewers watch only — no local camera (avoids opening the camera just to fail, and saves
     * battery). SDP uses a RECV_ONLY video transceiver; host still sends one-way video+audio.
     */
    private val broadcastViewerReceiveOnly: Boolean =
        broadcastMode && !viewerSignalingId.isNullOrBlank()
    private val tag = "WebRTCManager"

    private var previousIceConnectionStateLabel: String = "NEW"

    /** Wall clock: when set, next remote video frame logs [FRAME_RECOVERY]. */
    @Volatile
    private var awaitingFrameRecoverySinceWallMs: Long = 0L

    /** Firestore 1:1 full renegotiation rounds used this session; reset on ICE CONNECTED/COMPLETED. */
    private val sessionReconnectAttempts = AtomicInteger(0)

    @Volatile private var fullRenegotiationInFlight: Boolean = false

    private var connectivityNetworkCallback: ConnectivityManager.NetworkCallback? = null

    private var webRtcStatsDumpRunnable: Runnable? = null

    private fun jsonEsc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else ->
                    if (c.code < 0x20) {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
            }
        }
        return sb.toString()
    }

    private fun jsonQuote(s: String) = "\"${jsonEsc(s)}\""

    private fun jsonValue(v: Any?): String = when (v) {
        null -> "null"
        is Boolean -> v.toString()
        is Int, is Long, is Float, is Double -> v.toString()
        else -> jsonQuote(v.toString())
    }

    /** Structured JSON for logcat filtering (e.g. black-screen investigations). */
    private inline fun logWebRTC(event: String, vararg pairs: Pair<String, Any?>) {
        val ts = System.currentTimeMillis()
        val sb = StringBuilder(128)
            .append("{\"event\":")
            .append(jsonQuote(event))
        for ((k, v) in pairs) {
            sb.append(',').append(jsonQuote(k)).append(':').append(jsonValue(v))
        }
        sb.append(',').append("\"ts\":").append(ts).append('}')
        Log.d("WebRTC_Debug", sb.toString())
    }

    private fun startWebRtcStatsDumpIfNeeded() {
        if (webRtcStatsDumpRunnable != null) return
        val runnable = object : Runnable {
            override fun run() {
                if (destroyed) return
                mainHandler.postDelayed(this, 30_000L)
                if (peerConnection == null) return
                dumpWebRTCStats()
            }
        }
        webRtcStatsDumpRunnable = runnable
        mainHandler.postDelayed(runnable, 30_000L)
    }

    private fun stopWebRtcStatsDump() {
        webRtcStatsDumpRunnable?.let { mainHandler.removeCallbacks(it) }
        webRtcStatsDumpRunnable = null
    }

    private fun dumpWebRTCStats() {
        val pc = peerConnection ?: return
        val nowMono = SystemClock.elapsedRealtime()
        val ice = pc.iceConnectionState().toString()
        val pcs = pc.connectionState().toString()
        val msSinceRemoteFrame =
            if (lastRemoteVideoFrameMonotonicMs > 0L) {
                (nowMono - lastRemoteVideoFrameMonotonicMs).coerceAtLeast(0L)
            } else {
                -1L
            }
        val watchdogCoroutineActive =
            iceRestartJob?.isActive == true ||
                iceFailureRecoveryJob?.isActive == true ||
                videoStallWatchdogRunnable != null
        val presenceListenerActive = callPresencePeerListener != null
        val ts = System.currentTimeMillis()
        val sb = StringBuilder(256)
            .append("{\"ice_state\":").append(jsonQuote(ice))
            .append(",\"pc_connection_state\":").append(jsonQuote(pcs))
            .append(",\"ms_since_last_remote_frame\":").append(msSinceRemoteFrame)
            .append(",\"watchdog_coroutine_active\":").append(watchdogCoroutineActive)
            .append(",\"presence_listener_active\":").append(presenceListenerActive)
            .append(",\"ts\":").append(ts)
            .append('}')
        Log.d("WebRTC_Stats", sb.toString())
    }

    /** Guard [onDestroy] so Activity teardown + manual end-call can both invoke safely. */
    @Volatile private var destroyed = false

    /**
     * When true, frames from [CameraVideoCapturer] must not reach the [VideoSource] (surface invalid,
     * process backgrounding, etc.). Set in [stopCapturingFrames], cleared in [resumeCapturingFrames].
     * [pauseCamera] / [resumeCamera] (e.g. PK) do not touch this flag.
     */
    @Volatile
    private var suppressCameraFrameDelivery = false

    /**
     * Live host path (Camera Kit / local capture): used by [LiveStreamForegroundService] so we only
     * pause the broadcast publisher, not viewers or injected-factory PK guest links.
     */
    internal fun isLiveBroadcastHostWithLocalCapture(): Boolean {
        if (destroyed) return false
        if (!broadcastMode || audioOnly) return false
        if (broadcastViewerReceiveOnly) return false
        if (!viewerSignalingId.isNullOrBlank()) return false
        return true
    }

    /** True once [prepareLocalVideoTrackAndCapturer] has created [videoCapturer]. */
    internal fun hasLocalVideoCapturer(): Boolean = !destroyed && videoCapturer != null

    /**
     * Stops capture and drops frames at the capturer boundary. Prefer over [pauseCamera] when the
     * preview [android.view.Surface] is torn down or the app is backgrounded under the live FGS.
     *
     * No-ops until a capturer exists so early [SurfaceHolder.surfaceDestroyed] churn during Compose
     * init does not leave [suppressCameraFrameDelivery] stuck and black-hole all future frames.
     */
    fun stopCapturingFrames() {
        if (!hasLocalVideoCapturer()) {
            Log.d(tag, "stopCapturingFrames: skipped (no capturer yet) room=$roomId")
            return
        }
        suppressCameraFrameDelivery = true
        pauseCamera()
        Log.d(tag, "stopCapturingFrames: suppressed + capture stopped room=$roomId")
    }

    /**
     * Clears [suppressCameraFrameDelivery] and restarts capture. Call when the host preview surface
     * is valid again and/or the activity has resumed.
     *
     * **Order:** [suppressCameraFrameDelivery] is always cleared first, even if the capturer is not
     * created yet, so frames are allowed as soon as [prepareLocalVideoTrackAndCapturer] runs.
     */
    fun resumeCapturingFrames() {
        if (suppressCameraFrameDelivery) {
            Log.d(tag, "resumeCapturingFrames: clearing suppression (was true) room=$roomId")
        }
        suppressCameraFrameDelivery = false
        if (hasLocalVideoCapturer()) {
            if (!tryStartCaptureAfterResume()) {
                onMain {
                    if (onCameraCaptureResumeFailed != null) {
                        onCameraCaptureResumeFailed.invoke()
                    } else {
                        Toast.makeText(
                            context.applicationContext,
                            "Camera in use by another app",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
        Log.d(tag, "resumeCapturingFrames: suppression cleared room=$roomId (resumeCamera=${hasLocalVideoCapturer()})")
    }

    /**
     * Re-binds the local [VideoTrack] to [localView] (host preview). Use when the track becomes
     * available or after surface/renderer churn so the preview is not left black.
     */
    fun bindLocalVideoTrackToRenderer(reason: String) {
        onMain {
            val track = localVideoTrack
            if (track == null) {
                Log.d(tag, "bindLocalVideoTrackToRenderer: no local track yet reason=$reason room=$roomId")
                return@onMain
            }
            val sink = sinkForLocalVideo()
            runCatching { track.removeSink(sink) }
            runCatching { track.addSink(sink) }
            Log.i(
                "WebRTCManager",
                "Binding local video track to SurfaceViewRenderer — reason=$reason room=$roomId",
            )
        }
    }

    /**
     * Drops Camera Kit → WebRTC video frames after [onDestroy]. Primary session matches [activeManager];
     * co-publishers that inject a shared [PeerConnectionFactory] are allowed while not destroyed.
     */
    internal fun mayDeliverCameraKitVideoFrames(): Boolean {
        if (suppressCameraFrameDelivery) {
            Log.v("WebRTCManager", "Frame dropped: Delivery suppressed")
            return false
        }
        if (destroyed) return false
        if (WebRTCManager.activeManager() === this) return true
        return injectedPeerConnectionFactory != null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    /** Posted from [startCall] when staggering Camera Kit vs PeerConnection; cleared in [onDestroy]. */
    private var pendingCameraKitStaggerRunnable: Runnable? = null

    private var trimMemoryCallbackRegistered = false
    private val trimMemoryCallback = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
                Log.w(
                    tag,
                    "Memory: TRIM_MEMORY_RUNNING_CRITICAL (or higher) level=$level " +
                        "destroyed=$destroyed room=$roomId — system may reclaim GPU/native during Go-Live",
                )
            } else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                Log.d(tag, "Memory: onTrimMemory level=$level room=$roomId destroyed=$destroyed")
            }
        }

        override fun onLowMemory() {
            Log.w(tag, "Memory: onLowMemory room=$roomId destroyed=$destroyed")
        }

        override fun onConfigurationChanged(newConfig: Configuration) {}
    }
    private val database: DatabaseReference = run {
        val sid = broadcastLivePublishersSessionId?.trim().orEmpty()
        val pid = broadcastLivePublishersPublisherUid?.trim().orEmpty()
        if (sid.isNotEmpty() && pid.isNotEmpty()) {
            FirebaseDatabase.getInstance().reference
                .child("live_publishers")
                .child(sid)
                .child(pid)
        } else {
            FirebaseDatabase.getInstance().reference.child("rooms").child(roomId)
        }
    }

    /** Legacy 1:1 uses [database]; broadcast viewer uses `database/peers/{viewerUid}`. */
    private var signalingRoot: DatabaseReference = database

    /** WebRTC native callbacks run off the main thread; UI and [PeerConnection] must stay on main. */
    /** Re-apply [routeLiveAudienceToSpeaker] when wired/BT outputs connect or disconnect (API 23+). */
    private var liveAudienceAudioDeviceCallback: AudioDeviceCallback? = null

    /**
     * Live broadcast **audience** path: use loudspeaker only when no headset/USB/BT sink is active;
     * otherwise leave speakerphone off so VoIP audio follows the attached device.
     */
    private fun routeLiveAudienceToSpeaker() {
        if (!broadcastViewerReceiveOnly) return
        if (!broadcastViewerReceiveAudio) return
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            applyLivePlaybackSpeakerMode(am, preferLoudSpeaker = true)
            Log.d(
                tag,
                "Live audience audio routed room=$roomId ext=${hasExternalVoiceOutputDevice(am)}",
            )
            ensureLiveAudienceAudioRouteCallbackRegistered()
        } catch (e: Exception) {
            Log.e(tag, "Live audience speaker routing failed room=$roomId", e)
        }
    }

    private fun ensureLiveAudienceAudioRouteCallbackRegistered() {
        if (!broadcastViewerReceiveOnly || !broadcastViewerReceiveAudio) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (liveAudienceAudioDeviceCallback != null) return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                onMain { routeLiveAudienceToSpeaker() }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                onMain { routeLiveAudienceToSpeaker() }
            }
        }
        runCatching {
            am.registerAudioDeviceCallback(cb, mainHandler)
            liveAudienceAudioDeviceCallback = cb
        }.onFailure { Log.w(tag, "registerAudioDeviceCallback failed room=$roomId", it) }
    }

    private fun unregisterLiveAudienceAudioRouteCallback() {
        val cb = liveAudienceAudioDeviceCallback ?: return
        liveAudienceAudioDeviceCallback = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.unregisterAudioDeviceCallback(cb)
        }.onFailure { Log.w(tag, "unregisterAudioDeviceCallback failed room=$roomId", it) }
    }

    private fun restoreDefaultAudioRoutingIfLiveAudience() {
        if (!broadcastViewerReceiveOnly) return
        if (!broadcastViewerReceiveAudio) return
        unregisterLiveAudienceAudioRouteCallback()
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false
            am.mode = AudioManager.MODE_NORMAL
            Log.d(tag, "Live audience: audio routing restored to defaults room=$roomId")
        } catch (e: Exception) {
            Log.w(tag, "restoreDefaultAudioRoutingIfLiveAudience failed", e)
        }
    }

    private fun onMain(block: () -> Unit) {
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                block()
            } else {
                mainHandler.post {
                    try {
                        block()
                    } catch (e: Exception) {
                        Log.e(tag, "onMain posted block failed", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "onMain failed", e)
        }
    }

    /** Optional mirror of SDP answer into Firestore `calls/{roomId}` for debugging / secondary clients. */
    private fun writeAnswerMirrorToFirestore(sdp: SessionDescription) {
        if (isStreamer) return
        try {
            FirebaseFirestore.getInstance().collection("calls").document(roomId)
                .set(
                    mapOf(
                        "answerType" to sdp.type.canonicalForm(),
                        "answerDescription" to sdp.description,
                        "updatedAtMs" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
                .addOnSuccessListener {
                    Log.d("WebRTC_SIGNAL", "Firestore calls/$roomId answer mirror written")
                }
                .addOnFailureListener { e ->
                    Log.e(tag, "Firestore calls/$roomId answer mirror FAILED", e)
                }
        } catch (e: Exception) {
            Log.e(tag, "writeAnswerMirrorToFirestore", e)
        }
    }

    private var peerConnection: PeerConnection? = null
    private var factory: PeerConnectionFactory? = null
    /** Custom ADM with hardware AEC/NS; must [AudioDeviceModule.release] after [PeerConnectionFactory.dispose]. */
    private var audioDeviceModule: AudioDeviceModule? = null
    private var eglBase: EglBase? = null

    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var beautyObserver: BeautyCapturerObserver? = null
    /** When using Snap Camera Kit, owns [Session] + lens output; null for ML Kit / legacy camera path. */
    private var cameraKitBridge: CameraKitWebRtcBridge? = null
    /**
     * Last remote video track received via [onTrack] / [onAddTrack] / [onAddStream].
     * Stored so [attachPkChallengerSink] can bind it retroactively if the track arrived
     * before the PK renderer was ready.
     */
    private var remoteVideoTrack: VideoTrack? = null
    /**
     * Incoming remote audio the user hears (viewer/callee). Not used for broadcast host viewer-uplink tracks.
     */
    private var remoteAudioTrack: AudioTrack? = null
    @Volatile
    private var remoteAudioHearEnabled: Boolean = true
    /**
     * PK Battle: when set via [attachPkChallengerSink], the challenger's incoming video
     * is routed to this renderer instead of the default [remoteView].
     */
    private var pkChallengerSink: SurfaceViewRenderer? = null
    /**
     * Remote track that arrived before [initSurfaceRenderersIfNeeded] completed.
     * Parked here so it is never permanently lost; attached once the EGL context is ready.
     */
    private var pendingRemoteTrack: VideoTrack? = null

    /**
     * 1:1 calls only: when true, local camera is rendered on [remoteView] (full screen) and remote
     * on [localView] (PIP). Toggled by [swapCallVideoPipLayout]. Ignored when [pkChallengerSink] is set.
     */
    private var callVideoLayoutSwapped: Boolean = false

    /**
     * Active face-filter type applied by [BeautyCapturerObserver].
     * Written from the UI thread; read on the capture thread — `@Volatile` is sufficient.
     */
    @Volatile private var activeFilterType: FilterType = FilterType.NONE

    /** Legacy compatibility: toggle beauty filter on/off (maps to BEAUTY / NONE). */
    fun setFilterEnabled(enabled: Boolean) {
        activeFilterType = if (enabled) FilterType.BEAUTY else FilterType.NONE
        Log.d(tag, "Beauty filter ${if (enabled) "ENABLED (BEAUTY)" else "DISABLED (NONE)"} room=$roomId")
    }

    /**
     * Apply a specific face filter. Safe to call from any thread.
     * [FilterType.NONE] disables all processing (zero-overhead pass-through).
     */
    fun setFilterType(type: FilterType) {
        activeFilterType = type
        val kit = cameraKitBridge
        if (kit != null) {
            when (type) {
                FilterType.NONE -> kit.clearLens()
                else -> kit.applyConfiguredGroupFirstLens()
            }
            Log.d(tag, "Camera Kit lens request $type room=$roomId")
            return
        }
        Log.e("AR_FILTER", "Filter type set to $type room=$roomId")
    }

    fun getActiveFilterType(): FilterType = activeFilterType

    /**
     * Observes all lenses published for [groupId] (portal lens group UUID). Returns null if Camera Kit is not active.
     * Prefer calling from the main thread; updates are posted to the main looper.
     */
    @CheckResult
    fun observeSnapLenses(groupId: String, onUpdate: (List<LensesComponent.Lens>) -> Unit): Closeable? =
        cameraKitBridge?.observeAvailableLenses(groupId, onUpdate)

    /** Applies a lens by id within the given portal group (matches Snap `QueryCriteria.ById`). */
    fun applySnapLensFromPortal(
        groupId: String,
        lensId: String,
        onLoading: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        activeFilterType = FilterType.BEAUTY
        cameraKitBridge?.applyLensByGroupAndId(
            groupId = groupId,
            lensId = lensId,
            onLoading = onLoading,
            onError = onError,
        )
    }

    /**
     * Switch between front and back cameras while the call is active.
     * No-op if the capturer is not a [CameraVideoCapturer] or has not been initialised.
     */
    /**
     * Swaps which feed is full-screen vs PIP for standard (non-broadcast, non-PK-sink) 1:1 video calls.
     * Safe to call before tracks attach; re-binds sinks when both tracks exist.
     */
    fun swapCallVideoPipLayout() {
        onMain {
            if (broadcastMode || audioOnly || pkChallengerSink != null) return@onMain
            callVideoLayoutSwapped = !callVideoLayoutSwapped
            refreshCallVideoMirrors()
            val l = localVideoTrack
            val r = remoteVideoTrack
            runCatching {
                l?.removeSink(localView)
                l?.removeSink(remoteView)
                r?.removeSink(localView)
                r?.removeSink(remoteView)
            }
            runCatching {
                l?.addSink(sinkForLocalVideo())
                r?.addSink(sinkForRemoteVideo())
            }
            applyCameraKitVideoScaling()
            Log.d(tag, "swapCallVideoPipLayout swapped=$callVideoLayoutSwapped room=$roomId")
        }
    }

    /**
     * Snap / Camera Kit: the SDK scales like “cover”; WebRTC [SCALE_ASPECT_FILL] on the self view adds a second crop.
     * Force the renderer that shows the outgoing (local) track to [SCALE_ASPECT_FIT]; remote stays fill.
     */
    private fun applyCameraKitVideoScaling() {
        if (!useCameraKitPipeline()) {
            localView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            remoteView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            return
        }
        runCatching {
            sinkForLocalVideo().setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            sinkForRemoteVideo().setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        }
    }

    /**
     * Re-binds local/remote [VideoTrack] sinks to [SurfaceViewRenderer]s after [Lifecycle.Event.ON_RESUME]
     * or when the UI layer was torn down and surfaces recovered. Does not recreate the peer connection.
     */
    fun refreshVideoSinkAttachments(reason: String) {
        onMain {
            try {
                if (localVideoTrack == null && remoteVideoTrack == null) {
                    Log.d(tag, "refreshVideoSinkAttachments: no video tracks, skip reason=$reason room=$roomId")
                    return@onMain
                }
                if (!initPeerConnectionFactoryIfNeeded()) {
                    Log.w(tag, "refreshVideoSinkAttachments: factory not ready reason=$reason room=$roomId")
                    return@onMain
                }
                if (!initSurfaceRenderersIfNeeded()) {
                    Log.w(tag, "refreshVideoSinkAttachments: renderers not ready reason=$reason room=$roomId")
                    return@onMain
                }
                refreshCallVideoMirrors()
                applyCameraKitVideoScaling()
                val l = localVideoTrack
                val r = remoteVideoTrack
                runCatching {
                    l?.removeSink(localView)
                    l?.removeSink(remoteView)
                    remoteVideoFrameTapSink?.let { tap ->
                        r?.removeSink(tap)
                    }
                    r?.removeSink(localView)
                    r?.removeSink(remoteView)
                    pkChallengerSink?.let { sink ->
                        if (sink !== localView && sink !== remoteView) {
                            l?.removeSink(sink)
                            r?.removeSink(sink)
                        }
                    }
                }
                runCatching { l?.addSink(sinkForLocalVideo()) }
                runCatching {
                    r?.let { track ->
                        attachRemoteVideoWithFrameTap(track, sinkForRemoteVideo())
                    }
                }
                runCatching {
                    localView.requestLayout()
                    localView.invalidate()
                    remoteView.requestLayout()
                    remoteView.invalidate()
                    pkChallengerSink?.requestLayout()
                    pkChallengerSink?.invalidate()
                }
                Log.d(tag, "refreshVideoSinkAttachments OK reason=$reason room=$roomId pkSink=${pkChallengerSink != null}")
            } catch (e: Exception) {
                Log.e("WEBRTC_CRASH", "refreshVideoSinkAttachments reason=$reason room=$roomId", e)
            }
        }
    }

    private fun sinkForRemoteVideo(): SurfaceViewRenderer {
        pkChallengerSink?.let { return it }
        return if (callVideoLayoutSwapped) localView else remoteView
    }

    private fun sinkForLocalVideo(): SurfaceViewRenderer {
        return if (callVideoLayoutSwapped) remoteView else localView
    }

    private fun refreshCallVideoMirrors() {
        if (pkChallengerSink != null) {
            // PK arena: show natural (non-mirrored) video on every tile; do not early-return without updating sinks.
            runCatching {
                localView.setMirror(false)
                remoteView.setMirror(false)
                pkChallengerSink?.setMirror(false)
            }
            return
        }
        if (useCameraKitPipeline()) {
            // Camera Kit / AllowsCameraPreview already mirrors front camera for preview; WebRTC mirror would flip again.
            sinkForLocalVideo().setMirror(false)
            sinkForRemoteVideo().setMirror(false)
            return
        }
        val mirrorLocalPreview = _globalIsFrontCamera.value
        sinkForLocalVideo().setMirror(mirrorLocalPreview)
        sinkForRemoteVideo().setMirror(false)
    }

    fun switchCamera() {
        val capturer = videoCapturer
        if (capturer is CameraVideoCapturer) {
            try {
                capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                    override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                        Log.d(tag, "Camera switched: isFrontCamera=$isFrontCamera room=$roomId")
                        _globalIsFrontCamera.value = isFrontCamera
                        onMain { refreshCallVideoMirrors() }
                    }
                    override fun onCameraSwitchError(errorDescription: String?) {
                        Log.e(tag, "Camera switch failed room=$roomId: $errorDescription")
                    }
                })
            } catch (e: Exception) {
                Log.e(tag, "switchCamera() failed room=$roomId", e)
            }
        } else {
            Log.w(tag, "switchCamera: capturer is not a CameraVideoCapturer (${capturer?.javaClass?.simpleName})")
        }
    }


    /** Returns the EGL context for this session; used to initialise a [SurfaceViewRenderer] in Compose. */
    fun eglContext(): EglBase.Context? = eglBase?.eglBaseContext

    private var isRemoteDescriptionSet = false
    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private var isStreamer = false
    private var offerListener: ValueEventListener? = null
    private var answerListener: ValueEventListener? = null
    private var candidatesListener: ChildEventListener? = null
    private var candidatesRef: DatabaseReference? = null
    private var peerDisconnectedNotified = false
    private var localMediaPrepared = false
    private var renderersInitialized = false

    /**
     * Coroutine scope tied to this WebRTCManager instance. Used for:
     *  - ICE restart timer (DISCONNECTED → wait 3 s → [PeerConnection.restartIce])
     *
     * Cancelled in [onDestroy] so no zombie coroutines survive after resources are freed.
     */
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    /** Pending ICE-restart attempt; cancelled if the connection recovers or the manager is destroyed. */
    private var iceRestartJob: Job? = null
    /** After ICE/PC [FAILED], one [restartIce] attempt before [onPeerDisconnected]. */
    private var iceFailureRecoveryJob: Job? = null

    /** 1:1 Firestore (`call_*`): no SDP answer on doc after local offer posted. */
    private var callAnswerTimeoutJob: Job? = null

    private val firebaseSignaling = FirebaseService()

    private val firestoreSignalingRegistrations: MutableMap<String, ListenerRegistration> =
        Collections.synchronizedMap(mutableMapOf())

    private val processedRemoteFirestoreIceIds: MutableSet<String> =
        Collections.synchronizedSet(mutableSetOf())

    private fun useFirestoreForOneToOneSignaling(): Boolean =
        !broadcastMode && roomId.startsWith("call_")

    private fun putFirestoreRegistration(key: String, registration: ListenerRegistration) {
        synchronized(firestoreSignalingRegistrations) {
            firestoreSignalingRegistrations.remove(key)?.remove()
            firestoreSignalingRegistrations[key] = registration
        }
    }

    private fun removeAllFirestoreSignalingRegistrations() {
        synchronized(firestoreSignalingRegistrations) {
            firestoreSignalingRegistrations.values.forEach { r ->
                runCatching { r.remove() }
            }
            firestoreSignalingRegistrations.clear()
        }
        processedRemoteFirestoreIceIds.clear()
    }

    private fun parseFirestoreSdpMap(raw: Any?): Pair<String, String>? {
        val m = raw as? Map<*, *> ?: return null
        val typeStr = m["type"]?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val desc = m["sdp"]?.toString()?.takeIf { it.isNotBlank() }
            ?: m["description"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return null
        return typeStr to desc
    }

    private fun writeFirestoreOfferAfterSetLocal(s: SessionDescription) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid?.trim().orEmpty()
        if (uid.isEmpty()) {
            Log.e(tag, "writeFirestoreOfferAfterSetLocal: missing uid room=$roomId")
            return
        }
        managerScope.launch(Dispatchers.IO) {
            val ok = firebaseSignaling.mergeCallWebRtcOffer(
                roomId,
                uid,
                s.type.canonicalForm(),
                s.description,
            )
            if (ok) {
                withContext(Dispatchers.Main) {
                    if (!destroyed) startCallerAnswerTimeoutAfterOfferWritten()
                }
            } else {
                Log.e(tag, "mergeCallWebRtcOffer failed room=$roomId")
            }
        }
    }

    private fun startCallerAnswerTimeoutAfterOfferWritten() {
        if (!useFirestoreForOneToOneSignaling() || !isStreamer) return
        callAnswerTimeoutJob?.cancel()
        callAnswerTimeoutJob = managerScope.launch {
            delay(CALL_ANSWER_FIRESTORE_TIMEOUT_MS)
            if (destroyed) return@launch
            if (isRemoteDescriptionSet) return@launch
            val snap = runCatching {
                withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance().collection("calls").document(roomId).get().await()
                }
            }.getOrNull()
            val parsed = parseFirestoreSdpMap(snap?.get("answer"))
            if (parsed != null) return@launch
            if (peerDisconnectedNotified) return@launch
            Log.w(tag, "Firestore signaling: no answer within ${CALL_ANSWER_FIRESTORE_TIMEOUT_MS}ms room=$roomId")
            withContext(Dispatchers.IO) {
                firebaseSignaling.markCallNoAnswerSdpTimeout(roomId)
                firebaseSignaling.cleanupCallFirestoreSignalingCollections(roomId)
            }
            peerDisconnectedNotified = true
            onMain { onPeerDisconnected?.invoke() }
        }
    }

    private fun attachFirestoreOfferListener() {
        val docRef = FirebaseFirestore.getInstance().collection("calls").document(roomId)
        val registration = docRef.addSnapshotListener(MetadataChanges.INCLUDE) { snap, e ->
            if (e != null) {
                Log.w(tag, "Firestore offer listener error room=$roomId", e)
                return@addSnapshotListener
            }
            if (snap == null || !snap.exists()) return@addSnapshotListener
            onMain {
                try {
                    if (isRemoteDescriptionSet) return@onMain
                    val offerRaw = snap.get("offer") ?: return@onMain
                    val (typeStr, desc) = parseFirestoreSdpMap(offerRaw) ?: return@onMain
                    val remoteSdp =
                        SessionDescription(SessionDescription.Type.fromCanonicalForm(typeStr), desc)
                    peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            onMain {
                                try {
                                    Log.d("WebRTC_SIGNAL", "SDP REMOTE OFFER applied (Firestore) room=$roomId")
                                    isRemoteDescriptionSet = true
                                    drainPendingCandidates()
                                    createAnswer()
                                } catch (ex: Exception) {
                                    Log.e(tag, "HANDSHAKE after setRemote OFFER Firestore room=$roomId", ex)
                                }
                            }
                        }

                        override fun onSetFailure(p0: String?) {
                            Log.e(tag, "setRemoteDescription OFFER (Firestore) failed room=$roomId: $p0")
                        }
                    }, remoteSdp)
                } catch (ex: Exception) {
                    Log.w(tag, "attachFirestoreOfferListener parse room=$roomId", ex)
                }
            }
        }
        putFirestoreRegistration("call_doc_offer", registration)
    }

    private fun listenForAnswerFirestore() {
        val docRef = FirebaseFirestore.getInstance().collection("calls").document(roomId)
        val registration = docRef.addSnapshotListener(MetadataChanges.INCLUDE) { snap, e ->
            if (e != null) {
                Log.w(tag, "Firestore answer listener error room=$roomId", e)
                return@addSnapshotListener
            }
            if (snap == null || !snap.exists()) return@addSnapshotListener
            onMain {
                try {
                    if (isRemoteDescriptionSet) return@onMain
                    val ansRaw = snap.get("answer") ?: return@onMain
                    val (typeStr, desc) = parseFirestoreSdpMap(ansRaw) ?: return@onMain
                    val remoteSdp =
                        SessionDescription(SessionDescription.Type.fromCanonicalForm(typeStr), desc)
                    peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            onMain {
                                try {
                                    Log.d("WebRTC_SIGNAL", "SDP REMOTE ANSWER applied (Firestore) room=$roomId")
                                    callAnswerTimeoutJob?.cancel()
                                    callAnswerTimeoutJob = null
                                    isRemoteDescriptionSet = true
                                    drainPendingCandidates()
                                } catch (ex: Exception) {
                                    Log.e(tag, "HANDSHAKE after setRemote ANSWER Firestore room=$roomId", ex)
                                }
                            }
                        }

                        override fun onSetFailure(p0: String?) {
                            Log.e(tag, "setRemoteDescription ANSWER (Firestore) failed room=$roomId: $p0")
                        }
                    }, remoteSdp)
                } catch (ex: Exception) {
                    Log.w(tag, "listenForAnswerFirestore parse room=$roomId", ex)
                }
            }
        }
        putFirestoreRegistration("call_doc_answer", registration)
    }

    private fun listenForIceCandidatesFirestore() {
        val remoteCollName = if (isStreamer) "answerCandidates" else "offerCandidates"
        val collRef = FirebaseFirestore.getInstance().collection("calls").document(roomId)
            .collection(remoteCollName)
        val registration = collRef.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, e ->
            if (e != null) {
                Log.w(tag, "Firestore ICE listener error path=$remoteCollName room=$roomId", e)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            for (change in snapshot.documentChanges) {
                if (change.type != DocumentChange.Type.ADDED) continue
                val doc = change.document
                val docId = doc.id
                val firstTime = synchronized(processedRemoteFirestoreIceIds) {
                    processedRemoteFirestoreIceIds.add(docId)
                }
                if (!firstTime) continue
                val data = doc.data ?: continue
                val sdpStr = (data["candidate"] as? String)?.takeIf { it.isNotBlank() }
                    ?: (data["sdp"] as? String)?.takeIf { it.isNotBlank() }
                    ?: continue
                val mid = (data["sdpMid"] as? String).orEmpty()
                val idx = when (val raw = data["sdpMLineIndex"]) {
                    is Long -> raw.toInt()
                    is Int -> raw
                    else -> 0
                }
                val candidate = IceCandidate(mid, idx, sdpStr)
                onMain {
                    Log.d(
                        "WebRTC_SIGNAL",
                        "ICE IN (Firestore) room=$roomId coll=$remoteCollName mid=$mid idx=$idx remoteSet=$isRemoteDescriptionSet",
                    )
                    if (isRemoteDescriptionSet) {
                        peerConnection?.addIceCandidate(candidate)
                    } else {
                        pendingIceCandidates.add(candidate)
                    }
                }
            }
        }
        putFirestoreRegistration("ice_$remoteCollName", registration)
    }

    /**
     * Forwards decoded remote frames to the real [SurfaceViewRenderer] and records arrival time for the
     * stall watchdog (browser-side this is similar to monitoring `HTMLVideoElement.currentTime`).
     */
    private inner class ForwardingVideoSink(private val downstream: VideoSink) : VideoSink {
        override fun onFrame(frame: VideoFrame) {
            if (!destroyed) {
                lastRemoteVideoFrameMonotonicMs = SystemClock.elapsedRealtime()
                val t0 = awaitingFrameRecoverySinceWallMs
                if (t0 > 0L) {
                    awaitingFrameRecoverySinceWallMs = 0L
                    val recoveryMs = System.currentTimeMillis() - t0
                    logWebRTC("FRAME_RECOVERY", "recovery_ms" to recoveryMs, "room" to roomId)
                }
            }
            downstream.onFrame(frame)
        }
    }

    private var remoteVideoFrameTapSink: ForwardingVideoSink? = null
    @Volatile private var lastRemoteVideoFrameMonotonicMs: Long = 0L
    /** ElapsedRealtime when ICE reached CONNECTED/COMPLETED (watchdog: no frame while "connected"). */
    @Volatile private var remoteVideoConnectedRealtimeMs: Long = 0L
    private var videoStallWatchdogRunnable: Runnable? = null
    private var processLifecycleObserver: DefaultLifecycleObserver? = null
    @Volatile private var callResilienceHooksRegistered: Boolean = false
    private var callPresenceSelfRef: DatabaseReference? = null
    private var callPresencePeerRef: DatabaseReference? = null
    private var callPresencePeerListener: ValueEventListener? = null
    @Volatile private var callPeerPresenceEverSeen: Boolean = false
    @Volatile private var lastRemoteVideoTrackEnabled: Boolean = true
    @Volatile private var lastLocalVideoTrackEnabled: Boolean = true
    /** Min spacing between stall-watchdog triggered [restartIce] to avoid duplicate renegotiation. */
    @Volatile private var lastWatchdogIceRecoveryElapsedMs: Long = 0L

    private data class HostViewerSession(
        val viewerId: String,
        var peerConnection: PeerConnection?,
        var remoteDescriptionSet: Boolean,
        val pendingIce: MutableList<IceCandidate>,
        var answerListener: ValueEventListener?,
        var iceListener: ChildEventListener?,
        var iceRef: DatabaseReference?
    )

    private val hostViewerSessions: MutableMap<String, HostViewerSession> = LinkedHashMap()
    private var activeViewersRef: DatabaseReference? = null
    private var activeViewersListener: ChildEventListener? = null

    private val borrowsPeerFactoryStack: Boolean =
        injectedPeerConnectionFactory != null && injectedEglBase != null

    init {
        if (takeCurrentManagerSlot) {
            currentManager = this
        }
    }

    /**
     * Runs [block] on the main looper after a surface is ready (next layout pass).
     *
     * Prefers [localView] for the post target. For broadcast viewers [localView] is a dummy
     * [SurfaceViewRenderer] that is never added to any window — its [android.view.View.post]
     * would never fire. In that case we fall back to [remoteView] (which IS in the window),
     * ensuring WebRTC initialisation always proceeds.
     */
    private fun runAfterSurfaceReady(block: () -> Unit) {
        val wrapped = Runnable {
            // Select the view most likely to be attached to a window.
            val targetView = if (localView.isAttachedToWindow) localView else remoteView
            val runBlock = Runnable {
                try {
                    block()
                } catch (e: Exception) {
                    Log.e("WEBRTC_CRASH", "Init failed", e)
                }
            }
            if (targetView.isAttachedToWindow) {
                targetView.post(runBlock)
            } else {
                // Neither view is attached yet — schedule on the main handler with a short
                // delay so Compose has time to add the AndroidView to the window.
                mainHandler.postDelayed(runBlock, 80)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            wrapped.run()
        } else {
            mainHandler.post(wrapped)
        }
    }

    /** [PeerConnectionFactory.initialize] may already be done via [WebRTCInitializer] before go-live. */
    @Synchronized
    private fun initPeerConnectionFactoryIfNeeded(): Boolean {
        if (factory != null) return true
        if (borrowsPeerFactoryStack) {
            factory = injectedPeerConnectionFactory
            eglBase = injectedEglBase
            if (publishGlobalMediaStreams) {
                eglBase?.eglBaseContext?.let { ctx -> _globalEglContext.value = ctx }
            }
            return factory != null && eglBase != null
        }
        if (!ensurePeerConnectionFactoryInitialized(context.applicationContext)) {
            Log.e(tag, "PeerConnectionFactory.initialize failed (global)")
            return false
        }
        return try {
            if (eglBase == null) {
                eglBase = EglBase.create()
            }
            val egl = eglBase!!
            // Reactive Compose bridge: publish EGL as soon as the shared context exists.
            if (publishGlobalMediaStreams) {
                _globalEglContext.value = egl.eglBaseContext
            }
            val encoderFactory = DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(egl.eglBaseContext)
            val adm = JavaAudioDeviceModule.builder(context.applicationContext)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()
            audioDeviceModule = adm
            factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
            true
        } catch (e: Exception) {
            Log.e("WEBRTC_CRASH", "PeerConnectionFactory builder failed", e)
            runCatching { (audioDeviceModule as? JavaAudioDeviceModule)?.release() }
            audioDeviceModule = null
            false
        }
    }

    /**
     * Live **host** should not play back any remote audio (viewers / echo); capture still sends local mic.
     */
    private fun muteRemoteAudioIfBroadcastHost(track: MediaStreamTrack?, source: String, viewerIdForLog: String? = null) {
        if (!broadcastMode || !isStreamer) return
        if (track == null || track.kind() != MediaStreamTrack.AUDIO_TRACK_KIND) return
        runCatching {
            track.setEnabled(false)
            val v = viewerIdForLog?.let { " viewer=$it" }.orEmpty()
            Log.d(tag, "Broadcast host: remote audio disabled ($source)$v room=$roomId")
        }.onFailure { Log.w(tag, "muteRemoteAudioIfBroadcastHost failed", it) }
    }

    /** Second broadcast link (e.g. PK guest video): keep video but drop remote audio to avoid double playback. */
    private fun suppressRemoteAudioIfVideoOnlyViewer(track: MediaStreamTrack?, source: String) {
        if (!broadcastViewerReceiveOnly || broadcastViewerReceiveAudio) return
        if (track == null || track.kind() != MediaStreamTrack.AUDIO_TRACK_KIND) return
        runCatching {
            track.setEnabled(false)
            Log.d(tag, "Broadcast viewer video-only: remote audio suppressed ($source) room=$roomId")
        }.onFailure { Log.w(tag, "suppressRemoteAudioIfVideoOnlyViewer failed", it) }
    }

    /**
     * Ensures EGL + [remoteView]/[localView] init ([remoteView.init]) before [VideoTrack.addSink].
     */
    private fun initSurfaceRenderersIfNeeded(): Boolean {
        if (renderersInitialized) return true
        val egl = eglBase ?: run {
            Log.e("WEBRTC_CRASH", "Init failed: eglBase null before renderer init", NullPointerException("eglBase"))
            return false
        }
        return try {
            // ── Z-ordering (THE PRIMARY FIX for black remote video) ──────────────────────────────
            // Without explicit z-order assignment both SurfaceViews compete for the same hardware
            // compositor layer.  The local PIP surface ends up winning and occludes the full-screen
            // remote feed, which is why audio connects but the partner's video is completely black.
            //
            //  remoteView  → default layer (behind the window) = full-screen background video.
            //  localView   → media-overlay layer (above remoteView, below window content) = PIP card.
            //
            // Broadcast viewers use a tiny dummy localView; overlay=true can still hide the host video.
            // PK 50/50 arena: both tiles must stay in the default stacking order or local occludes remote.
            remoteView.setZOrderMediaOverlay(false)
            localView.setZOrderMediaOverlay(
                when {
                    broadcastViewerReceiveOnly -> false
                    pkArenaSideBySide -> false
                    // Live host (solo or PK Compose): fullscreen local sits under Compose chrome like
                    // VideoCallScreen main sink; overlay=true hides it behind/with window compositing quirks.
                    broadcastMode && !broadcastViewerReceiveOnly -> false
                    else -> true // 1:1 PiP: local above remote
                }
            )
            // ─────────────────────────────────────────────────────────────────────────────────────
            localView.init(egl.eglBaseContext, null)
            remoteView.init(egl.eglBaseContext, null)
            applyCameraKitVideoScaling()
            refreshCallVideoMirrors()
            renderersInitialized = true
            true
        } catch (e: Exception) {
            Log.e("WEBRTC_CRASH", "Init failed", e)
            false
        }
    }

    private fun finishAttachRemoteVideoTrack(track: VideoTrack, source: String) {
        try {
            remoteVideoTrack?.let { old ->
                if (old !== track) {
                    detachRemoteTapFromTrack(old)
                    runCatching { old.removeSink(localView) }
                    runCatching { old.removeSink(remoteView) }
                    pkChallengerSink?.let { sink ->
                        if (sink !== localView && sink !== remoteView) {
                            runCatching { old.removeSink(sink) }
                        }
                    }
                }
            }
            remoteVideoTrack = track
            lastRemoteVideoTrackEnabled = track.enabled()
            if (publishGlobalMediaStreams) {
                _globalRemoteVideoTrack.value = track
            }
            val target = sinkForRemoteVideo()
            val sinkLabel = when {
                target === remoteView -> "remoteView"
                target === localView -> "localView"
                else -> "other"
            }
            runCatching { attachRemoteVideoWithFrameTap(track, target) }
                .onSuccess {
                    Log.d(
                        "WebRTC_SIGNAL",
                        "remote video addSink OK source=$source room=$roomId sink=$sinkLabel " +
                            "broadcastViewerRecvOnly=$broadcastViewerReceiveOnly " +
                            "(solo/PK: streamer RTDB path is rooms/$roomId/...)"
                    )
                }
                .onFailure { Log.e("WEBRTC_CRASH", "remote addSink failed source=$source room=$roomId", it) }
        } catch (e: Exception) {
            Log.e("WEBRTC_CRASH", "finishAttachRemoteVideoTrack source=$source room=$roomId", e)
        }
    }

    /** Runs on the main thread; (re)initializes factory/renderers if needed, then [finishAttachRemoteVideoTrack]. */
    private fun attachRemoteVideoTrackOrQueue(track: VideoTrack, source: String) {
        if (!initPeerConnectionFactoryIfNeeded()) {
            pendingRemoteTrack = track
            if (publishGlobalMediaStreams) {
                _globalRemoteVideoTrack.value = track
            }
            Log.w(tag, "attachRemoteVideoTrackOrQueue: factory not ready ($source) — queued room=$roomId")
            return
        }
        if (!initSurfaceRenderersIfNeeded()) {
            pendingRemoteTrack = track
            if (publishGlobalMediaStreams) {
                _globalRemoteVideoTrack.value = track
            }
            Log.w(tag, "attachRemoteVideoTrackOrQueue: renderers not ready ($source) — queued room=$roomId")
            return
        }
        flushPendingRemoteVideoTrackIfDifferent(track)
        finishAttachRemoteVideoTrack(track, source)
    }

    /** If a track arrived before EGL/views were ready, attach it once (unless superseded by [newTrack]). */
    private fun flushPendingRemoteVideoTrackIfDifferent(newTrack: VideoTrack) {
        val pending = pendingRemoteTrack ?: return
        pendingRemoteTrack = null
        if (pending !== newTrack) {
            finishAttachRemoteVideoTrack(pending, "flushPendingBeforeNewRemote")
        }
    }

    private fun flushPendingRemoteVideoTrackAfterSurfaceReady(reason: String) {
        val t = pendingRemoteTrack ?: return
        pendingRemoteTrack = null
        finishAttachRemoteVideoTrack(t, reason)
    }

    private fun ensureLocalMediaPrepared() {
        if (localMediaPrepared) return
        try {
            setupLocalStream()
            localMediaPrepared = true
        } catch (t: Throwable) {
            Log.e("WEBRTC_CRASH", "Init failed", t)
        }
    }

    private fun setupLocalStream() {
        prepareLocalVideoTrackAndCapturer()
        prepareLocalAudioTrackOnly()
    }

    private fun registerTrimMemoryCallbackIfNeeded() {
        if (trimMemoryCallbackRegistered) return
        trimMemoryCallbackRegistered = true
        try {
            context.applicationContext.registerComponentCallbacks(trimMemoryCallback)
        } catch (e: Exception) {
            trimMemoryCallbackRegistered = false
            Log.w(tag, "registerComponentCallbacks(trimMemory) failed room=$roomId", e)
        }
    }

    private fun unregisterTrimMemoryCallbackIfNeeded() {
        if (!trimMemoryCallbackRegistered) return
        trimMemoryCallbackRegistered = false
        runCatching {
            context.applicationContext.unregisterComponentCallbacks(trimMemoryCallback)
        }.onFailure { Log.w(tag, "unregisterComponentCallbacks(trimMemory) failed room=$roomId", it) }
    }

    private fun registerProcessLifecycleCameraGuard() {
        if (processLifecycleObserver != null) return
        val obs = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (destroyed) return
                stopCapturingFrames()
            }

            override fun onStart(owner: LifecycleOwner) {
                if (destroyed) return
                resumeCapturingFrames()
                refreshVideoSinkAttachments("process_lifecycle_visible")
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(obs)
        processLifecycleObserver = obs
        Log.d(tag, "Process lifecycle camera guard attached room=$roomId")
    }

    private fun unregisterProcessLifecycleCameraGuard() {
        val obs = processLifecycleObserver ?: return
        runCatching { ProcessLifecycleOwner.get().lifecycle.removeObserver(obs) }
            .onFailure { Log.w(tag, "removeProcessLifecycleObserver failed room=$roomId", it) }
        processLifecycleObserver = null
    }

    private fun detachCallPresence() {
        val listener = callPresencePeerListener
        val peerRef = callPresencePeerRef
        callPresencePeerListener = null
        callPresencePeerRef = null
        if (listener != null && peerRef != null) {
            runCatching { peerRef.removeEventListener(listener) }
                .onFailure { Log.w(tag, "detachCallPresence removeEventListener failed room=$roomId", it) }
        }
        runCatching { callPresenceSelfRef?.removeValue() }
        callPresenceSelfRef = null
        callPeerPresenceEverSeen = false
    }

    private fun handleCallPeerPresenceSnapshot(snapshot: DataSnapshot) {
        if (destroyed) return
        if (snapshot.exists()) {
            callPeerPresenceEverSeen = true
            return
        }
        if (!callPeerPresenceEverSeen) return
        Log.w(tag, "RTDB call presence: peer disappeared room=$roomId")
        val peerKey = snapshot.ref.key ?: callRemotePeerUid?.trim().orEmpty()
        logWebRTC("PEER_PRESENCE_LOST", "room" to roomId, "peer" to peerKey)
        if (!peerDisconnectedNotified) {
            peerDisconnectedNotified = true
            val handler = onPeerPresenceLost ?: onPeerDisconnected
            handler?.invoke()
        }
    }

    private fun startCallParticipantPresenceIfNeeded() {
        if (broadcastMode) return
        val peer = callRemotePeerUid?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val myUid = FirebaseAuth.getInstance().currentUser?.uid?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (peer == myUid) return
        detachCallPresence()
        callPeerPresenceEverSeen = false
        val root = database.child("call_participant_presence")
        val selfRef = root.child(myUid)
        callPresenceSelfRef = selfRef
        try {
            selfRef.onDisconnect().removeValue()
            selfRef.setValue(
                mapOf(
                    "uid" to myUid,
                    "lastSeen" to ServerValue.TIMESTAMP,
                    "state" to "in_call",
                ),
            )
        } catch (e: Exception) {
            Log.e(tag, "call presence self write failed room=$roomId", e)
        }
        val peerRef = root.child(peer)
        callPresencePeerRef = peerRef
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onMain { handleCallPeerPresenceSnapshot(snapshot) }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(tag, "call presence peer listener cancelled room=$roomId", error.toException())
            }
        }
        callPresencePeerListener = listener
        peerRef.addValueEventListener(listener)
        Log.d(tag, "RTDB call presence armed peer=$peer self=$myUid room=$roomId")
    }

    private fun startRemoteVideoStallWatchdogIfNeeded() {
        if (audioOnly) return
        if (videoStallWatchdogRunnable != null) return
        val runnable = object : Runnable {
            override fun run() {
                if (destroyed) return
                mainHandler.postDelayed(this, 5_000)
                val pc = peerConnection ?: return
                val iceOk = when (pc.iceConnectionState()) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED,
                    -> true
                    else -> false
                }
                if (!iceOk) return

                val rTrack = remoteVideoTrack
                if (rTrack != null) {
                    val en = rTrack.enabled()
                    if (en != lastRemoteVideoTrackEnabled) {
                        lastRemoteVideoTrackEnabled = en
                        Log.w(tag, "Remote video track enabled changed to $en (media mute/unmute) room=$roomId")
                        if (en) {
                            refreshVideoSinkAttachments("remote_track_unmuted")
                        }
                    }
                    if (rTrack.state() == MediaStreamTrack.State.ENDED) {
                        Log.e(tag, "Remote video track ENDED → restartIce + rebind room=$roomId")
                        tryWatchdogIceRecovery("remote_track_ended")
                        refreshVideoSinkAttachments("remote_track_ended")
                    }
                }

                val lTrack = localVideoTrack
                if (lTrack != null && !audioOnly) {
                    val len = lTrack.enabled()
                    if (len != lastLocalVideoTrackEnabled) {
                        lastLocalVideoTrackEnabled = len
                        Log.w(tag, "Local video track enabled changed to $len room=$roomId")
                    }
                    if (lTrack.state() == MediaStreamTrack.State.ENDED) {
                        Log.e(tag, "Local video track ENDED → restart capturer room=$roomId")
                        runCatching { resumeCamera() }
                        bindLocalVideoTrackToRenderer("local_track_ended")
                    }
                }

                if (rTrack == null) return
                val now = SystemClock.elapsedRealtime()
                val lastFrame = lastRemoteVideoFrameMonotonicMs
                if (lastFrame > 0L) {
                    if (now - lastFrame < 4_500L) return
                    Log.w(
                        tag,
                        "Remote video stall watchdog: no decoded frames for ${now - lastFrame}ms → restartIce room=$roomId",
                    )
                    tryWatchdogIceRecovery("stall_no_frames")
                    refreshVideoSinkAttachments("stall_watchdog")
                    return
                }
                val sinceIce = remoteVideoConnectedRealtimeMs
                if (sinceIce > 0L && now - sinceIce > 15_000L) {
                    Log.w(tag, "Remote video stall watchdog: ICE connected but no frames yet → restartIce room=$roomId")
                    tryWatchdogIceRecovery("stall_no_first_frame")
                    refreshVideoSinkAttachments("stall_watchdog_no_first_frame")
                }
            }
        }
        videoStallWatchdogRunnable = runnable
        mainHandler.postDelayed(runnable, 5_000)
        Log.d(tag, "Remote video stall watchdog started room=$roomId")
    }

    private fun stopRemoteVideoStallWatchdog() {
        videoStallWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        videoStallWatchdogRunnable = null
    }

    private fun scheduleIceFailureRecovery(reason: String) {
        if (destroyed) return
        iceFailureRecoveryJob?.cancel()
        iceFailureRecoveryJob = managerScope.launch {
            Log.w(tag, "ICE/PC failure recovery: restartIce ($reason) room=$roomId")
            safeRestartIce("failure_recovery_$reason")
            delay(4_000)
            if (destroyed) return@launch
            val ice = peerConnection?.iceConnectionState()
            val pstate = peerConnection?.connectionState()
            val stillBad =
                ice == PeerConnection.IceConnectionState.FAILED ||
                    ice == PeerConnection.IceConnectionState.CLOSED ||
                    pstate == PeerConnection.PeerConnectionState.FAILED ||
                    pstate == PeerConnection.PeerConnectionState.CLOSED
            if (stillBad && !peerDisconnectedNotified) {
                Log.e(tag, "ICE/PC failure recovery exhausted ice=$ice pc=$pstate room=$roomId")
                peerDisconnectedNotified = true
                val handler = onCallFailed ?: onPeerDisconnected
                handler?.invoke()
            }
        }
    }

    private fun detachRemoteTapFromTrack(track: VideoTrack?) {
        val t = track ?: return
        remoteVideoFrameTapSink?.let { tap ->
            runCatching { t.removeSink(tap) }
        }
        remoteVideoFrameTapSink = null
    }

    private fun attachRemoteVideoWithFrameTap(track: VideoTrack, target: VideoSink) {
        remoteVideoFrameTapSink?.let { tap ->
            runCatching { track.removeSink(tap) }
        }
        val tap = ForwardingVideoSink(target)
        remoteVideoFrameTapSink = tap
        runCatching { track.addSink(tap) }
    }

    private fun ensureCallResilienceHooksRegistered() {
        if (callResilienceHooksRegistered) return
        callResilienceHooksRegistered = true
        registerProcessLifecycleCameraGuard()
        startCallParticipantPresenceIfNeeded()
        startRemoteVideoStallWatchdogIfNeeded()
        startWebRtcStatsDumpIfNeeded()
        registerConnectivityReconnectCallbackIfNeeded()
    }

    private fun shouldUseFirestoreFullRenegotiation(): Boolean =
        useFirestoreForOneToOneSignaling() && !broadcastMode

    private fun registerConnectivityReconnectCallbackIfNeeded() {
        if (broadcastMode || destroyed) return
        if (connectivityNetworkCallback != null) return
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (peerConnection == null || destroyed) return
                onMain {
                    logWebRTC("NET_AVAILABLE", "room" to roomId)
                    safeRestartIce("network_available")
                }
            }

            override fun onLost(network: Network) {
                if (destroyed) return
                Log.w(tag, "network lost room=$roomId")
            }
        }
        connectivityNetworkCallback = cb
        runCatching { cm.registerDefaultNetworkCallback(cb) }
            .onFailure { Log.w(tag, "registerDefaultNetworkCallback failed room=$roomId", it) }
    }

    private fun unregisterConnectivityReconnectCallback() {
        val cb = connectivityNetworkCallback ?: return
        connectivityNetworkCallback = null
        runCatching {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(cb)
        }.onFailure { Log.w(tag, "unregisterNetworkCallback failed room=$roomId", it) }
    }

    /**
     * Host/caller: create SDP offer and publish to RTDB or Firestore (1:1). Used after initial
     * [createPeerConnection] and after [performFullFirestoreRenegotiation].
     */
    private fun createAndSignalLocalOffer() {
        val constraints = oneToOneMediaConstraints()
        try {
            peerConnection?.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    onMain {
                        try {
                            val s = sdp ?: return@onMain
                            peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                                override fun onSetSuccess() {
                                    onMain {
                                        try {
                                            Log.d(
                                                "WebRTC_SIGNAL",
                                                "SDP OFFER setLocal OK room=$roomId type=${s.type} len=${s.description.length}",
                                            )
                                            applyOutboundVideoBitrate(peerConnection, context.applicationContext)
                                            if (useFirestoreForOneToOneSignaling()) {
                                                writeFirestoreOfferAfterSetLocal(s)
                                            } else {
                                                signalingRoot.child("offer").setValue(
                                                    mapOf(
                                                        "type" to s.type.canonicalForm(),
                                                        "description" to s.description,
                                                    ),
                                                )
                                            }
                                        } catch (e: Exception) {
                                            Log.e(tag, "HANDSHAKE offer setLocal / signaling write failed room=$roomId", e)
                                        }
                                    }
                                }

                                override fun onSetFailure(p0: String?) {
                                    Log.e(tag, "setLocalDescription OFFER failed room=$roomId: $p0")
                                }
                            }, s)
                        } catch (e: Exception) {
                            Log.e(tag, "HANDSHAKE createOffer → setLocal OFFER room=$roomId", e)
                        }
                    }
                }

                override fun onCreateFailure(p0: String?) {
                    Log.e(tag, "createOffer failed room=$roomId: $p0")
                }
            }, constraints)
        } catch (e: Exception) {
            Log.e(tag, "createOffer invocation failed room=$roomId", e)
        }
    }

    private fun scheduleFirestoreFullRenegotiationIfNeeded(reason: String) {
        if (destroyed || !shouldUseFirestoreFullRenegotiation()) return
        if (fullRenegotiationInFlight) return
        fullRenegotiationInFlight = true
        iceRestartJob?.cancel()
        iceRestartJob = null
        iceFailureRecoveryJob?.cancel()
        iceFailureRecoveryJob = managerScope.launch {
            try {
                var followReason = reason
                while (!destroyed && !peerDisconnectedNotified) {
                    if (sessionReconnectAttempts.get() >= SESSION_FULL_RENEG_MAX_ATTEMPTS) {
                        peerDisconnectedNotified = true
                        val h = onCallFailed ?: onPeerDisconnected
                        onMain { h?.invoke() }
                        break
                    }
                    sessionReconnectAttempts.incrementAndGet()
                    onMain {
                        onStallWatchdogReconnect?.invoke(
                            sessionReconnectAttempts.get(),
                            SESSION_FULL_RENEG_MAX_ATTEMPTS,
                        )
                    }
                    performFullFirestoreRenegotiation(followReason)
                    followReason = "ice_still_bad_after_reneg"
                    delay(5_000)
                    if (destroyed) break
                    val ice = peerConnection?.iceConnectionState()
                    val pstate = peerConnection?.connectionState()
                    val recovered =
                        ice == PeerConnection.IceConnectionState.CONNECTED ||
                            ice == PeerConnection.IceConnectionState.COMPLETED ||
                            pstate == PeerConnection.PeerConnectionState.CONNECTED
                    if (recovered) break
                }
            } finally {
                fullRenegotiationInFlight = false
            }
        }
    }

    private suspend fun performFullFirestoreRenegotiation(traceReason: String) {
        if (destroyed) return
        Log.w(
            tag,
            "performFullFirestoreRenegotiation reason=$traceReason attempt=${sessionReconnectAttempts.get()} room=$roomId",
        )
        logWebRTC(
            "FULL_RENEG",
            "reason" to traceReason,
            "attempt" to sessionReconnectAttempts.get(),
            "room" to roomId,
        )
        withContext(Dispatchers.Main) {
            removeAllFirestoreSignalingRegistrations()
            callAnswerTimeoutJob?.cancel()
            callAnswerTimeoutJob = null
            iceRestartJob?.cancel()
            iceRestartJob = null
            runCatching { peerConnection?.dispose() }
            peerConnection = null
            isRemoteDescriptionSet = false
            pendingIceCandidates.clear()
        }
        createPeerConnection()
        if (destroyed) return
        withContext(Dispatchers.Main) {
            if (isStreamer) {
                createAndSignalLocalOffer()
            } else {
                attachFirestoreOfferListener()
            }
        }
    }

    /**
     * [null] = skipped (null PC, destroyed, or already CLOSED). [true] = [restartIce] invoked.
     * [false] = [restartIce] threw — caller may treat as hard failure.
     */
    private fun safeRestartIce(reason: String): Boolean? {
        val pc = peerConnection
        if (destroyed || pc == null) {
            Log.d(tag, "safeRestartIce skip ($reason): pc null or destroyed room=$roomId")
            return null
        }
        if (pc.connectionState() == PeerConnection.PeerConnectionState.CLOSED) {
            Log.d(tag, "safeRestartIce skip ($reason): PeerConnection CLOSED room=$roomId")
            return null
        }
        if (pc.iceConnectionState() == PeerConnection.IceConnectionState.CLOSED) {
            Log.d(tag, "safeRestartIce skip ($reason): ICE CLOSED room=$roomId")
            return null
        }
        return try {
            logWebRTC("RESTART_ICE", "reason" to reason, "room" to roomId)
            pc.restartIce()
            Log.d(tag, "safeRestartIce ok ($reason) room=$roomId")
            true
        } catch (e: Exception) {
            Log.e(tag, "safeRestartIce failed ($reason) room=$roomId", e)
            false
        }
    }

    /** Stall watchdog recovery with minimum 8s between [restartIce] attempts. */
    private fun tryWatchdogIceRecovery(reason: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastWatchdogIceRecoveryElapsedMs < 8_000L) {
            val cooldownRemMs = (8_000L - (now - lastWatchdogIceRecoveryElapsedMs)).coerceAtLeast(0L)
            logWebRTC("WATCHDOG_COOLDOWN", "cooldown_ms" to cooldownRemMs, "reason" to reason)
            Log.d(
                tag,
                "tryWatchdogIceRecovery skip (cooldown ${now - lastWatchdogIceRecoveryElapsedMs}ms) reason=$reason room=$roomId",
            )
            return false
        }
        val msSinceFrame =
            if (lastRemoteVideoFrameMonotonicMs > 0L) {
                (now - lastRemoteVideoFrameMonotonicMs).coerceAtLeast(0L)
            } else {
                0L
            }
        logWebRTC("WATCHDOG_TRIGGER", "reason" to reason, "ms_since_frame" to msSinceFrame)
        if (safeRestartIce("watchdog_$reason") != true) return false
        lastWatchdogIceRecoveryElapsedMs = SystemClock.elapsedRealtime()
        awaitingFrameRecoverySinceWallMs = System.currentTimeMillis()
        onMain {
            val attempt =
                sessionReconnectAttempts.get().coerceAtLeast(1).coerceAtMost(SESSION_FULL_RENEG_MAX_ATTEMPTS)
            onStallWatchdogReconnect?.invoke(attempt, SESSION_FULL_RENEG_MAX_ATTEMPTS)
        }
        return true
    }

    /**
     * Best-effort: [org.webrtc.VideoSource] holds a [NativeAndroidVideoTrackSource] with
     * `nativeAndroidVideoTrackSource`; if the JNI pointer is 0, skip feeding frames to avoid native crashes.
     */
    private fun isNativeAndroidVideoTrackSourceHandleAlive(source: VideoSource): Boolean {
        return try {
            val fWrapper = VideoSource::class.java.getDeclaredField("nativeAndroidVideoTrackSource")
            fWrapper.isAccessible = true
            val wrapper = fWrapper.get(source) ?: return false
            val fPtr = wrapper.javaClass.getDeclaredField("nativeAndroidVideoTrackSource")
            fPtr.isAccessible = true
            fPtr.getLong(wrapper) != 0L
        } catch (e: Exception) {
            Log.d(tag, "native VideoSource handle check skipped (${e.javaClass.simpleName}) room=$roomId")
            true
        }
    }

    /**
     * Sits between [CameraVideoCapturer] and [VideoSource.capturerObserver] so we never call into JNI
     * after the native AndroidVideoTrackSource is torn down.
     */
    private inner class GuardedVideoCapturerObserver(
        private val downstream: CapturerObserver,
    ) : CapturerObserver {
        override fun onCapturerStarted(success: Boolean) {
            downstream.onCapturerStarted(success)
        }

        override fun onCapturerStopped() {
            downstream.onCapturerStopped()
        }

        override fun onFrameCaptured(frame: VideoFrame) {
            if (destroyed) {
                frame.release()
                return
            }
            if (suppressCameraFrameDelivery) {
                frame.release()
                return
            }
            val vs = videoSource
            if (vs == null || !isNativeAndroidVideoTrackSourceHandleAlive(vs)) {
                Log.w(tag, "GuardedCapturer: drop frame (VideoSource/native handle invalid) room=$roomId")
                frame.release()
                return
            }
            downstream.onFrameCaptured(frame)
        }
    }

    /**
     * Video capture + local video track only (Camera Kit or beauty pipeline). Audio is added by
     * [prepareLocalAudioTrackOnly], optionally after [CAMERA_KIT_TO_PEER_CONNECTION_STAGGER_MS].
     */
    private fun prepareLocalVideoTrackAndCapturer() {
        val f = factory ?: return
        val wantLocalVideo = !audioOnly && !broadcastViewerReceiveOnly
        if (!wantLocalVideo) {
            Log.d(tag, "prepareLocalVideoTrackAndCapturer: skipping (audio-only or receive-only)")
            return
        }
        videoSource = f.createVideoSource(false)
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase!!.eglBaseContext)
        val vs = videoSource ?: run {
            Log.e("AR_FILTER", "Init result: FAILED videoSource null after createVideoSource room=$roomId")
            return
        }
        val (capW, capH) = localVideoCaptureWidthHeight()
        val fps = captureTargetFps(context)
        val landscapeW = max(capW, capH)
        val landscapeH = min(capW, capH)
        val portraitW = min(capW, capH)
        val portraitH = max(capW, capH)
        vs.adaptOutputFormat(landscapeW, landscapeH, portraitW, portraitH, fps)
        vs.setIsScreencast(false)
        registerTrimMemoryCallbackIfNeeded()
        val guardedCapturerObserver = GuardedVideoCapturerObserver(vs.capturerObserver)
        if (useCameraKitPipeline()) {
            cameraKitBridge = CameraKitWebRtcBridge(context, roomId)
            cameraKitBridge!!.glFinishHandler = surfaceTextureHelper?.handler
            videoCapturer = CameraKitVideoCapturer(
                context = context,
                bridge = cameraKitBridge!!,
                roomId = roomId,
                mayDeliverCapturedFrames = { mayDeliverCameraKitVideoFrames() },
            )
            beautyObserver = null
            Log.e(
                "CameraKit",
                "Video path: Camera Kit → WebRTC OES texture via SurfaceTextureHelper ${capW}x${capH} " +
                    "(not NV21); adaptOutputFormat landscape=${landscapeW}x${landscapeH} portrait=${portraitW}x${portraitH} room=$roomId",
            )
            videoCapturer?.initialize(surfaceTextureHelper, context, guardedCapturerObserver)
        } else {
            cameraKitBridge = null
            val beautyObs = BeautyCapturerObserver(guardedCapturerObserver)
            beautyObserver = beautyObs
            videoCapturer = createVideoCapturer()
            Log.e(
                "AR_FILTER",
                "Init result: BeautyCapturerObserver + GuardedCapturerObserver room=$roomId " +
                    "adaptOutputFormat landscape=${landscapeW}x${landscapeH} portrait=${portraitW}x${portraitH}",
            )
            videoCapturer?.initialize(surfaceTextureHelper, context, beautyObs)
        }
        try {
            videoCapturer?.startCapture(capW, capH, fps)
        } catch (e: Exception) {
            Log.e(
                "WEBRTC_VIDEO",
                "FATAL: videoCapturer.startCapture failed room=$roomId — local preview/encode will be black",
                e
            )
        }

        localVideoTrack = f.createVideoTrack("video_track", videoSource)
        lastLocalVideoTrackEnabled = localVideoTrack?.enabled() ?: true
        if (publishGlobalMediaStreams) {
            _globalLocalVideoTrack.value = localVideoTrack
        }
        val vTrack = localVideoTrack
        if (vTrack != null) {
            try {
                vTrack.addSink(sinkForLocalVideo())
            } catch (e: Exception) {
                Log.e("WEBRTC_CRASH", "addSink local failed", e)
            }
        } else {
            Log.e("WEBRTC_CRASH", "localVideoTrack is null, skip addSink", IllegalStateException("no video track"))
        }
    }

    private fun prepareLocalAudioTrackOnly() {
        val f = factory ?: return
        val audioSource = f.createAudioSource(MediaConstraints())
        localAudioTrack = f.createAudioTrack("audio_track", audioSource)
    }

    /** Best-effort guard before Camera Kit touches GPU; reduces "Failed to choose EGL config" races. */
    private fun assertEglContextStableForCameraKit(): Boolean {
        return try {
            val egl = eglBase ?: run {
                Log.e(tag, "CameraKit EGL: eglBase null room=$roomId")
                return false
            }
            val ctx = egl.eglBaseContext
            if (ctx == null) {
                Log.e(tag, "CameraKit EGL: eglBaseContext null room=$roomId")
                return false
            }
            Log.d(tag, "CameraKit EGL: shared context ready room=$roomId")
            true
        } catch (e: Exception) {
            Log.e(tag, "CameraKit EGL stability check failed room=$roomId", e)
            false
        }
    }

    private fun useCameraKitStaggerForThisStartCall(): Boolean =
        !skipCameraKitInternalStagger &&
            broadcastMode && !audioOnly && !broadcastViewerReceiveOnly && useCameraKitPipeline()

    private fun scheduleStartCallAfterCameraKitStagger() {
        pendingCameraKitStaggerRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable {
            pendingCameraKitStaggerRunnable = null
            if (destroyed) return@Runnable
            try {
                prepareLocalAudioTrackOnly()
                localMediaPrepared = true
            } catch (t: Throwable) {
                Log.e("WEBRTC_CRASH", "staggered audio prep failed room=$roomId", t)
                return@Runnable
            }
            if (localAudioTrack == null) {
                Log.e(tag, "staggered startCall: local audio track missing room=$roomId")
                return@Runnable
            }
            val needLocalVideo = !audioOnly && !broadcastViewerReceiveOnly
            if (needLocalVideo && localVideoTrack == null) {
                Log.e(tag, "staggered startCall: local video missing room=$roomId")
                return@Runnable
            }
            finishHostStartCallSignaling()
        }
        pendingCameraKitStaggerRunnable = r
        mainHandler.postDelayed(r, CAMERA_KIT_TO_PEER_CONNECTION_STAGGER_MS)
    }

    private fun finishHostStartCallSignaling() {
        isStreamer = true
        signalingRoot = database
        peerDisconnectedNotified = false

        if (broadcastMode) {
            Log.d("WebRTC_SIGNAL", "Broadcast host: listening activeViewers room=$roomId")
            attachBroadcastHostActiveViewersListener()
            ensureCallResilienceHooksRegistered()
            return
        }

        Log.d(
            "WebRTC_SIGNAL",
            "1:1 host flat RTDB signaling room=$roomId → offer/answer/ICE at rooms/$roomId/* (broadcastMode=false)"
        )
        Log.e("E2E_DIAG_WEBRTC", "1:1 Call - isHost: true, RoomID: $roomId, Mode: Broadcast=$broadcastMode, AudioOnly=$audioOnly")
        managerScope.launch {
            if (destroyed) return@launch
            createPeerConnection()
            if (destroyed) return@launch
            createAndSignalLocalOffer()
        }
    }

    // ── Face-aware beauty filter pipeline ─────────────────────────────────────────────────────────

    /**
     * Intercepts every camera frame on the [SurfaceTextureHelper] capture thread.
     *
     * Uses ML Kit Face Detection to locate the face bounding box, then applies beauty
     * processing **only to the face region** for a natural look. The face rect is cached
     * and detection runs every 3rd frame to keep CPU usage low.
     *
     * **[FilterType.NONE]** — pure pass-through, zero extra work per frame.
     *
     * ### Per-filter effects (applied to face region only, YUV broadcast range [16–235]):
     * - **BEAUTY** — luma +18, neutral chroma (subtle skin brightening).
     * - **SMOOTH** — luma +10, mild chroma desaturation (soft matte look).
     * - **BRIGHT** — luma +30, neutral chroma (high-key daylight glow).
     * - **GLOW**   — luma +22, warm chroma bias in U plane (golden glow).
     * - **FAIR**   — luma +28, cool chroma bias (porcelain / fair-skin look).
     *
     * ### Memory strategy — zero Bitmap, zero GC stalls
     * Scratch `ByteArray`s are allocated once per resolution and reused; native I420 buffers
     * use `ByteBuffer.allocateDirect` — GC is never involved.
     */
    private inner class BeautyCapturerObserver(
        private val delegate: CapturerObserver
    ) : CapturerObserver {

        private var filterW = 0
        private var filterH = 0
        private var yScratch = ByteArray(0)
        private var uScratch = ByteArray(0)
        private var vScratch = ByteArray(0)
        private var loggedFirstFilteredFrame = false

        private val faceDetector: FaceDetector? = try {
            FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                    .build()
            )
        } catch (t: Throwable) {
            Log.w(tag, "ML Kit face detector unavailable — filters will apply full-frame", t)
            null
        }

        /** Full ML Kit Face object (includes landmarks) cached from the last detection run. */
        @Volatile private var cachedFace: Face? = null
        /** Expanded bounding box used by the beauty/YUV pipeline (derived from cachedFace). */
        @Volatile private var cachedFaceRect: Rect? = null
        /** Raw-buffer coordinates for key landmarks, rotated back out of ML Kit's upright space. */
        @Volatile private var rawLeftEye: PointF? = null
        @Volatile private var rawRightEye: PointF? = null
        @Volatile private var rawNose: PointF? = null
        @Volatile private var rawMouthCenter: PointF? = null

        private var frameCounter = 0
        @Volatile private var detectionInFlight = false
        private var nv21Scratch = ByteArray(0)
        /** Dedicated buffer for ML Kit input — separate from nv21Scratch to avoid a race between
         *  the capture thread (writer) and the async ML Kit callback (reader). */
        private var mlKitInputScratch = ByteArray(0)

        // ── Smoothed bounding box for jitter-free AR sticker placement ────────────────
        @Volatile private var smoothedFaceLeft   = 0f
        @Volatile private var smoothedFaceTop    = 0f
        @Volatile private var smoothedFaceRight  = 0f
        @Volatile private var smoothedFaceBottom = 0f
        @Volatile private var smoothedFaceValid  = false
        private val SMOOTH_ALPHA = 0.35f  // 0 = no movement, 1 = raw detection (no smoothing)
        private var yTmpScratch = ByteArray(0)
        private var lastFaceSeenMs = 0L
        /** Max age of smoothed face rect / AR keep gate (aligned with landmark grace). */
        private val FaceHoldMs = 300L
        /** After this long without a successful face detection, drop sticky landmarks + smoothed box. */
        private val FaceLandmarkStaleMs = 550L
        private val pointSmoothAlpha = 0.3f

        private fun smoothPoint(previous: PointF?, target: PointF): PointF {
            return if (previous == null) {
                PointF(target.x, target.y)
            } else {
                PointF(
                    previous.x + (target.x - previous.x) * pointSmoothAlpha,
                    previous.y + (target.y - previous.y) * pointSmoothAlpha,
                )
            }
        }

        fun closeFaceDetector() {
            runCatching { faceDetector?.close() }
            smoothedFaceValid = false
            rawLeftEye = null
            rawRightEye = null
            rawNose = null
            rawMouthCenter = null
        }

        override fun onCapturerStarted(success: Boolean) = delegate.onCapturerStarted(success)
        override fun onCapturerStopped() {
            closeFaceDetector()
            delegate.onCapturerStopped()
        }

        override fun onFrameCaptured(frame: VideoFrame) {
            val ft = activeFilterType
            if (ft == FilterType.NONE) {
                delegate.onFrameCaptured(frame)
                return
            }

            val processed = runCatching { processFrame(frame, ft) }.getOrElse {
                Log.w(tag, "BeautyFilter: processFrame exception — passing frame through", it)
                frame
            }
            if (processed !== frame) {
                if (!loggedFirstFilteredFrame) {
                    loggedFirstFilteredFrame = true
                    Log.e(
                        "AR_FILTER",
                        "First filtered frame routed to VideoSource (${ft}) ${frame.buffer.width}x${frame.buffer.height} faceRect=${cachedFaceRect} room=$roomId"
                    )
                }
                // [delegate] takes ownership of [processed] and will release it; release the original
                // camera frame we replaced. (Releasing [processed] here caused double-free crashes on CaptureThread.)
                delegate.onFrameCaptured(processed)
                frame.release()
            } else {
                delegate.onFrameCaptured(frame)
            }
        }

        private fun runFaceDetection(i420: VideoFrame.I420Buffer, w: Int, h: Int, rotation: Int) {
            val detector = faceDetector ?: return
            try {
                val uvW = (w + 1) / 2
                val uvH = (h + 1) / 2
                val nv21Size = w * h + uvW * uvH * 2

                if (nv21Scratch.size != nv21Size) {
                    nv21Scratch = ByteArray(nv21Size)
                }

                val yBuf = i420.dataY.duplicate()
                val strideY = i420.strideY
                for (row in 0 until h) {
                    yBuf.position(row * strideY)
                    yBuf.get(nv21Scratch, row * w, w)
                }

                val uBuf = i420.dataU.duplicate()
                val vBuf = i420.dataV.duplicate()
                val strideU = i420.strideU
                val strideV = i420.strideV
                var uvIdx = w * h
                for (row in 0 until uvH) {
                    for (col in 0 until uvW) {
                        vBuf.position(row * strideV + col)
                        nv21Scratch[uvIdx++] = vBuf.get()
                        uBuf.position(row * strideU + col)
                        nv21Scratch[uvIdx++] = uBuf.get()
                    }
                }

                // Copy nv21Scratch into a dedicated buffer before handing it to ML Kit so
                // subsequent frames on the capture thread cannot overwrite nv21Scratch while
                // the async success listener is still reading it.
                if (mlKitInputScratch.size != nv21Size) {
                    mlKitInputScratch = ByteArray(nv21Size)
                }
                System.arraycopy(nv21Scratch, 0, mlKitInputScratch, 0, nv21Size)

                detectionInFlight = true
                val inputImage = InputImage.fromByteArray(
                    mlKitInputScratch, w, h, rotation, InputImage.IMAGE_FORMAT_NV21
                )
                detector.process(inputImage)
                    .addOnSuccessListener { faces ->
                        try {
                            val now = System.currentTimeMillis()
                            val firstFace = faces.firstOrNull()
                            if (firstFace != null) {
                                cachedFace = firstFace
                                cachedFaceRect = firstFace.boundingBox?.let { uprightBounds ->
                                    val rawRect = transformRectToRawBuffer(uprightBounds, w, h, rotation)
                                    val marginX = (rawRect.width() * 0.22f).toInt()
                                    val marginY = (rawRect.height() * 0.22f).toInt()
                                    Rect(
                                        (rawRect.left - marginX).coerceAtLeast(0),
                                        (rawRect.top - marginY).coerceAtLeast(0),
                                        (rawRect.right + marginX).coerceAtMost(w),
                                        (rawRect.bottom + marginY).coerceAtMost(h)
                                    )
                                }
                                rawLeftEye = firstFace
                                    .getLandmark(FaceLandmark.LEFT_EYE)
                                    ?.position
                                    ?.let { transformPointToRawBuffer(it, w, h, rotation) }
                                rawRightEye = firstFace
                                    .getLandmark(FaceLandmark.RIGHT_EYE)
                                    ?.position
                                    ?.let { transformPointToRawBuffer(it, w, h, rotation) }
                                rawNose = firstFace
                                    .getLandmark(FaceLandmark.NOSE_BASE)
                                    ?.position
                                    ?.let { transformPointToRawBuffer(it, w, h, rotation) }
                                rawMouthCenter = firstFace
                                    .getLandmark(FaceLandmark.MOUTH_BOTTOM)
                                    ?.position
                                    ?.let { transformPointToRawBuffer(it, w, h, rotation) }
                                    ?.let { smoothPoint(rawMouthCenter, it) }
                                // Update exponentially-smoothed bounding box for AR stickers.
                                val b = firstFace.boundingBox?.let {
                                    transformRectToRawBuffer(it, w, h, rotation)
                                }
                                if (b != null) {
                                    lastFaceSeenMs = now
                                    if (!smoothedFaceValid) {
                                        smoothedFaceLeft = b.left.toFloat()
                                        smoothedFaceTop = b.top.toFloat()
                                        smoothedFaceRight = b.right.toFloat()
                                        smoothedFaceBottom = b.bottom.toFloat()
                                        smoothedFaceValid = true
                                    } else {
                                        smoothedFaceLeft += (b.left - smoothedFaceLeft) * SMOOTH_ALPHA
                                        smoothedFaceTop += (b.top - smoothedFaceTop) * SMOOTH_ALPHA
                                        smoothedFaceRight += (b.right - smoothedFaceRight) * SMOOTH_ALPHA
                                        smoothedFaceBottom += (b.bottom - smoothedFaceBottom) * SMOOTH_ALPHA
                                    }
                                }
                            } else {
                                cachedFace = null
                                cachedFaceRect = null
                                if (now - lastFaceSeenMs > FaceLandmarkStaleMs) {
                                    smoothedFaceValid = false
                                    rawLeftEye = null
                                    rawRightEye = null
                                    rawNose = null
                                    rawMouthCenter = null
                                }
                            }
                        } catch (t: Throwable) {
                            Log.w(tag, "Face rect transform failed", t)
                        } finally {
                            detectionInFlight = false
                        }
                    }
                    .addOnFailureListener {
                        detectionInFlight = false
                    }
            } catch (t: Throwable) {
                Log.w(tag, "Face detection failed — falling back to full-frame", t)
                detectionInFlight = false
            }
        }

        /**
         * ML Kit returns face rects in the upright coordinate system (after applying [rotation]).
         * Transform back to raw I420 buffer coordinates for per-pixel processing.
         *
         * For front-facing cameras the display layer mirrors the video horizontally
         * (SurfaceViewRenderer.setMirror(true)) but the I420 pixel buffer itself is never
         * flipped.  We therefore mirror the X axis here so that bounding-box positions match
         * what the viewer sees on screen.
         */
        private fun transformRectToRawBuffer(
            uprightRect: Rect, rawW: Int, rawH: Int, rotation: Int
        ): Rect {
            val r = when (rotation) {
                90 -> Rect(
                    uprightRect.top,
                    rawH - uprightRect.right,
                    uprightRect.bottom,
                    rawH - uprightRect.left
                )
                180 -> Rect(
                    rawW - uprightRect.right,
                    rawH - uprightRect.bottom,
                    rawW - uprightRect.left,
                    rawH - uprightRect.top
                )
                270 -> Rect(
                    rawW - uprightRect.bottom,
                    uprightRect.left,
                    rawW - uprightRect.top,
                    uprightRect.right
                )
                else -> uprightRect
            }
            // Front-camera horizontal mirror: flip left↔right in raw-buffer X.
            return if (_globalIsFrontCamera.value) {
                Rect(rawW - r.right, r.top, rawW - r.left, r.bottom)
            } else r
        }

        /**
         * ML Kit landmarks are in the upright image space after applying [rotation].
         * Transform them back to the raw I420 buffer coordinates so AR stickers line up
         * with the unrotated bitmap built from the camera frame.
         *
         * For front-facing cameras an additional horizontal mirror is applied so landmark
         * X positions match the mirrored display rendered by SurfaceViewRenderer.
         */
        private fun transformPointToRawBuffer(
            uprightPoint: PointF,
            rawW: Int,
            rawH: Int,
            rotation: Int,
        ): PointF {
            val p = when (rotation) {
                90  -> PointF(uprightPoint.y, rawH - uprightPoint.x)
                180 -> PointF(rawW - uprightPoint.x, rawH - uprightPoint.y)
                270 -> PointF(rawW - uprightPoint.y, uprightPoint.x)
                else -> PointF(uprightPoint.x, uprightPoint.y)
            }
            // Front-camera horizontal mirror: flip X so the landmark matches the displayed image.
            return if (_globalIsFrontCamera.value) PointF(rawW - p.x, p.y) else p
        }

        private fun processFrame(frame: VideoFrame, filterType: FilterType): VideoFrame {
            val i420: VideoFrame.I420Buffer = try {
                frame.buffer.toI420() ?: return frame
            } catch (t: Throwable) {
                Log.w(tag, "BeautyFilter: toI420 failed", t)
                return frame
            }

            val w = i420.width
            val h = i420.height
            val uvW = (w + 1) / 2
            val uvH = (h + 1) / 2

            frameCounter++
            // Every 2nd frame (was 3rd) for lower sticker-lag at the same CPU budget.
            if (frameCounter % 2 == 0 && !detectionInFlight) {
                runFaceDetection(i420, w, h, frame.rotation)
            }

            if (w != filterW || h != filterH) {
                filterW = w; filterH = h
                yScratch = ByteArray(w * h)
                uScratch = ByteArray(uvW * uvH)
                vScratch = ByteArray(uvW * uvH)
            }

            return try {
                val lumaBoost = when (filterType) {
                    FilterType.NONE   -> 0
                    FilterType.BEAUTY -> 18
                    FilterType.SMOOTH -> 10
                    FilterType.BRIGHT -> 30
                    FilterType.GLOW   -> 22
                    FilterType.FAIR   -> 28
                }

                val faceRect = cachedFaceRect
                val featherPx = if (faceRect != null) 26 else 0

                // ── Y plane: edge-preserving beauty blur + luma lift ─────────────────────
                val yBuf = i420.dataY
                val strideY = i420.strideY

                if (faceRect == null) {
                    // No face detected yet: full-frame fallback (original behaviour)
                    for (row in 0 until h) {
                        val srcBase = row * strideY
                        val dstBase = row * w
                        for (col in 0 until w) {
                            val luma = (yBuf.get(srcBase + col).toInt() and 0xFF) + lumaBoost
                            yScratch[dstBase + col] = luma.coerceIn(16, 235).toByte()
                        }
                    }
                } else {
                    copyI420Plane(yBuf, strideY, w, h, yScratch)
                    if (yTmpScratch.size != w * h) yTmpScratch = ByteArray(w * h)
                    val leftEye = rawLeftEye
                    val rightEye = rawRightEye
                    val beautyRoll = if (leftEye != null && rightEye != null) {
                        val dx = leftEye.x - rightEye.x
                        val dy = leftEye.y - rightEye.y
                        Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    } else 0f
                    applyEdgePreservingBeauty(
                        src = yScratch,
                        dst = yTmpScratch,
                        width = w,
                        height = h,
                        faceRect = faceRect,
                        featherPx = featherPx,
                        intensity = lumaBoost,
                        angleDeg = beautyRoll,
                    )
                    System.arraycopy(yTmpScratch, 0, yScratch, 0, yScratch.size)
                }

                // ── U / V planes: chroma adjustment per filter ───────────────────────────
                copyI420Plane(i420.dataU, i420.strideU, uvW, uvH, uScratch)
                copyI420Plane(i420.dataV, i420.strideV, uvW, uvH, vScratch)

                val uvRect = faceRect?.let { r ->
                    Rect(r.left / 2, r.top / 2, (r.right + 1) / 2, (r.bottom + 1) / 2)
                }
                val uvFeather = (featherPx + 1) / 2

                when (filterType) {
                    FilterType.GLOW -> {
                        if (uvRect == null) {
                            for (i in uScratch.indices) {
                                val u = (uScratch[i].toInt() and 0xFF)
                                uScratch[i] = (u - 6).coerceIn(16, 240).toByte()
                                val v = (vScratch[i].toInt() and 0xFF)
                                vScratch[i] = (v + 8).coerceIn(16, 240).toByte()
                            }
                        } else {
                            applyChromaInRegion(uvW, uvH, uvRect, uvFeather) { blend, i ->
                                val u = (uScratch[i].toInt() and 0xFF)
                                uScratch[i] = (u + (-6 * blend).toInt()).coerceIn(16, 240).toByte()
                                val v = (vScratch[i].toInt() and 0xFF)
                                vScratch[i] = (v + (8 * blend).toInt()).coerceIn(16, 240).toByte()
                            }
                        }
                    }
                    FilterType.FAIR -> {
                        if (uvRect == null) {
                            for (i in uScratch.indices) {
                                val u = (uScratch[i].toInt() and 0xFF)
                                uScratch[i] = (u + 6).coerceIn(16, 240).toByte()
                                val v = (vScratch[i].toInt() and 0xFF)
                                vScratch[i] = (v - 5).coerceIn(16, 240).toByte()
                            }
                        } else {
                            applyChromaInRegion(uvW, uvH, uvRect, uvFeather) { blend, i ->
                                val u = (uScratch[i].toInt() and 0xFF)
                                uScratch[i] = (u + (6 * blend).toInt()).coerceIn(16, 240).toByte()
                                val v = (vScratch[i].toInt() and 0xFF)
                                vScratch[i] = (v + (-5 * blend).toInt()).coerceIn(16, 240).toByte()
                            }
                        }
                    }
                    FilterType.SMOOTH -> {
                        val mid = 128
                        if (uvRect == null) {
                            for (i in uScratch.indices) {
                                val u = (uScratch[i].toInt() and 0xFF)
                                uScratch[i] = (mid + ((u - mid) * 0.88f).toInt()).coerceIn(16, 240).toByte()
                                val v = (vScratch[i].toInt() and 0xFF)
                                vScratch[i] = (mid + ((v - mid) * 0.88f).toInt()).coerceIn(16, 240).toByte()
                            }
                        } else {
                            applyChromaInRegion(uvW, uvH, uvRect, uvFeather) { blend, i ->
                                val desat = 1f - 0.12f * blend
                                val u = (uScratch[i].toInt() and 0xFF)
                                uScratch[i] = (mid + ((u - mid) * desat).toInt()).coerceIn(16, 240).toByte()
                                val v = (vScratch[i].toInt() and 0xFF)
                                vScratch[i] = (mid + ((v - mid) * desat).toInt()).coerceIn(16, 240).toByte()
                            }
                        }
                    }
                    else -> Unit
                }

                val out = JavaI420Buffer.allocate(w, h)
                out.dataY.put(yScratch, 0, w * h); out.dataY.rewind()
                out.dataU.put(uScratch, 0, uvW * uvH); out.dataU.rewind()
                out.dataV.put(vScratch, 0, uvW * uvH); out.dataV.rewind()

                VideoFrame(out, frame.rotation, frame.timestampNs)
            } catch (t: Throwable) {
                Log.w(tag, "BeautyFilter: processFrame error — frame passed through unchanged", t)
                frame
            } finally {
                i420.release()
            }
        }

        // ── Helpers ──────────────────────────────────────────────────────────────

        /**
         * Smooth elliptical falloff from face region (avoids a visible “square” mask vs rectangular bounds).
         * Full strength near the center, smoothstep to 0 outside an expanded ellipse.
         */
        private fun blendFaceEllipse(col: Int, row: Int, rect: Rect, outerFeatherPx: Int): Float {
            if (outerFeatherPx <= 0) return 1f
            val cx = (rect.left + rect.right) * 0.5f
            val cy = (rect.top + rect.bottom) * 0.5f
            val halfW = max(rect.width(), 1) * 0.5f
            val halfH = max(rect.height(), 1) * 0.5f
            val rx = halfW + outerFeatherPx * 0.9f
            val ry = halfH + outerFeatherPx * 0.9f
            val nx = (col - cx) / rx
            val ny = (row - cy) / ry
            val dist = hypot(nx.toDouble(), ny.toDouble()).toFloat()
            val inner = 0.82f
            val outerBand = 0.14f + outerFeatherPx / maxOf(rx, ry)
            val outer = inner + outerBand
            return when {
                dist <= inner -> 1f
                dist >= outer -> 0f
                else -> {
                    val t = ((dist - inner) / (outer - inner)).coerceIn(0f, 1f)
                    val smooth = t * t * (3f - 2f * t)
                    1f - smooth
                }
            }
        }

        /**
         * Same elliptical falloff as [blendFaceEllipse], but the sampling point is rotated
         * into face-local space first so the beauty mask follows the face roll angle.
         */
        private fun blendFaceEllipseRotated(
            col: Int,
            row: Int,
            rect: Rect,
            outerFeatherPx: Int,
            angleDeg: Float,
        ): Float {
            if (outerFeatherPx <= 0) return 1f
            val cx = (rect.left + rect.right) * 0.5f
            val cy = (rect.top + rect.bottom) * 0.5f
            val halfW = max(rect.width(), 1) * 0.5f
            val halfH = max(rect.height(), 1) * 0.5f
            val rx = halfW + outerFeatherPx * 0.9f
            val ry = halfH + outerFeatherPx * 0.9f

            val theta = Math.toRadians(angleDeg.toDouble())
            val cosT = kotlin.math.cos(theta).toFloat()
            val sinT = kotlin.math.sin(theta).toFloat()

            val dx = col - cx
            val dy = row - cy

            // Rotate the sample point into face-local space (equivalent to rotating the mask)
            val localX = dx * cosT + dy * sinT
            val localY = -dx * sinT + dy * cosT

            val nx = localX / rx
            val ny = localY / ry
            val dist = hypot(nx.toDouble(), ny.toDouble()).toFloat()
            val inner = 0.82f
            val outerBand = 0.14f + outerFeatherPx / maxOf(rx, ry)
            val outer = inner + outerBand
            return when {
                dist <= inner -> 1f
                dist >= outer -> 0f
                else -> {
                    val t = ((dist - inner) / (outer - inner)).coerceIn(0f, 1f)
                    val smooth = t * t * (3f - 2f * t)
                    1f - smooth
                }
            }
        }

        private inline fun applyChromaInRegion(
            uvW: Int, uvH: Int, uvRect: Rect, uvFeather: Int,
            action: (blend: Float, index: Int) -> Unit
        ) {
            val rStart = (uvRect.top - uvFeather).coerceAtLeast(0)
            val rEnd   = (uvRect.bottom + uvFeather).coerceAtMost(uvH)
            val cStart = (uvRect.left - uvFeather).coerceAtLeast(0)
            val cEnd   = (uvRect.right + uvFeather).coerceAtMost(uvW)
            for (row in rStart until rEnd) {
                for (col in cStart until cEnd) {
                    val blend = blendFaceEllipse(col, row, uvRect, uvFeather)
                    if (blend > 0f) {
                        action(blend, row * uvW + col)
                    }
                }
            }
        }

        private fun copyI420Plane(
            src: java.nio.ByteBuffer,
            stride: Int,
            width: Int,
            height: Int,
            dst: ByteArray
        ) {
            for (row in 0 until height) {
                src.position(row * stride)
                src.get(dst, row * width, width)
            }
        }

        /**
         * Lightweight bilateral-like beauty pass on the Y plane only.
         * Smooths skin texture while preserving edge detail (eyes/lips/nose bridge)
         * by only averaging neighboring luma values that are close to the center pixel.
         */
        private fun applyEdgePreservingBeauty(
            src: ByteArray,
            dst: ByteArray,
            width: Int,
            height: Int,
            faceRect: Rect,
            featherPx: Int,
            intensity: Int,
            angleDeg: Float,
        ) {
            val radius = when (intensity) {
                10 -> 1    // SMOOTH
                18 -> 2    // BEAUTY
                22 -> 2    // GLOW
                28 -> 3    // FAIR
                30 -> 3    // BRIGHT
                else -> 0
            }
            if (radius == 0) {
                System.arraycopy(src, 0, dst, 0, src.size)
                return
            }
            System.arraycopy(src, 0, dst, 0, src.size)
            val yStart = (faceRect.top - featherPx).coerceAtLeast(0)
            val yEnd = (faceRect.bottom + featherPx).coerceAtMost(height)
            val xStart = (faceRect.left - featherPx).coerceAtLeast(0)
            val xEnd = (faceRect.right + featherPx).coerceAtMost(width)
            for (row in yStart until yEnd) {
                for (col in xStart until xEnd) {
                    val idx = row * width + col
                    val center = src[idx].toInt() and 0xFF
                    val blend = blendFaceEllipseRotated(col, row, faceRect, featherPx, angleDeg)
                    var sum = center * 4
                    var weight = 4
                    fun sample(x: Int, y: Int) {
                        val sx = x.coerceIn(0, width - 1)
                        val sy = y.coerceIn(0, height - 1)
                        val v = src[sy * width + sx].toInt() and 0xFF
                        if (kotlin.math.abs(v - center) <= 18) {
                            sum += v
                            weight += 1
                        }
                    }
                    sample(col - radius, row)
                    sample(col + radius, row)
                    sample(col, row - radius)
                    sample(col, row + radius)
                    val blurred = sum / weight
                    val lifted = (blurred + intensity * 0.35f).toInt().coerceIn(16, 235)
                    val out = (center + ((lifted - center) * blend)).toInt().coerceIn(16, 235)
                    dst[idx] = out.toByte()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────

    private fun oneToOneMediaConstraints(): MediaConstraints = MediaConstraints().apply {
        val recvAudio = when {
            broadcastViewerReceiveOnly && !broadcastViewerReceiveAudio -> false
            else -> true
        }
        mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveAudio", if (recvAudio) "true" else "false")
        )
        val recvVideo = when {
            broadcastMode && audioOnly -> "false"
            !audioOnly -> "true"
            else -> "false"
        }
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", recvVideo))
    }

    private fun useCameraKitPipeline(): Boolean =
        useSnapCameraKitPipeline &&
            BuildConfig.SNAP_CAMERA_KIT_CONFIGURED &&
            supported(context.applicationContext)

    /**
     * Local capture needs [Manifest.permission.RECORD_AUDIO]; video needs [Manifest.permission.CAMERA].
     * Call before [ensureLocalMediaPrepared] to avoid camera/mic open while permission is denied (ANR / freeze risk).
     */
    private fun hasRuntimeMediaPermissionsForLocalStream(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(tag, "RECORD_AUDIO not granted; defer local media room=$roomId")
            return false
        }
        val needCamera = !audioOnly && !broadcastViewerReceiveOnly
        if (needCamera &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(tag, "CAMERA not granted; defer local video room=$roomId")
            return false
        }
        return true
    }

    /**
     * [CameraVideoCapturer.startCapture] size: Camera Kit uses portrait **720×1280**; beauty / default uses **1280×720**.
     * On cellular / metered networks, non–Camera Kit capture drops toward **960×540** for encoder stability.
     */
    private fun localVideoCaptureWidthHeight(): Pair<Int, Int> =
        when {
            useCameraKitPipeline() -> 720 to 1280
            isLikelyCellularOrMetered(context) -> 960 to 540
            else -> 1280 to 720
        }

    private fun createVideoCapturer(): VideoCapturer? {
        val enumerator = if (Camera2Enumerator.isSupported(context)) Camera2Enumerator(context) else Camera1Enumerator(true)
        for (name in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(name)) return enumerator.createCapturer(name, null)
        }
        return enumerator.deviceNames.firstOrNull()?.let { enumerator.createCapturer(it, null) }
    }

    fun startCall() {
        runAfterSurfaceReady {
            if (!initPeerConnectionFactoryIfNeeded()) {
                Log.e(tag, "startCall aborted: factory init failed")
                return@runAfterSurfaceReady
            }
            if (!initSurfaceRenderersIfNeeded()) {
                Log.e(tag, "startCall aborted: renderer init failed")
                return@runAfterSurfaceReady
            }
            flushPendingRemoteVideoTrackAfterSurfaceReady("postRendererInitStartCall")
            if (factory == null) {
                Log.e(tag, "startCall aborted: PeerConnectionFactory is null")
                return@runAfterSurfaceReady
            }
            if (!hasRuntimeMediaPermissionsForLocalStream()) {
                return@runAfterSurfaceReady
            }

            if (useCameraKitStaggerForThisStartCall()) {
                if (!assertEglContextStableForCameraKit()) {
                    Log.e(tag, "startCall aborted: EGL not ready for Camera Kit room=$roomId")
                    return@runAfterSurfaceReady
                }
                try {
                    prepareLocalVideoTrackAndCapturer()
                } catch (t: Throwable) {
                    Log.e("WEBRTC_CRASH", "Camera Kit video pipeline failed room=$roomId", t)
                    return@runAfterSurfaceReady
                }
                val needLocalVideo = !audioOnly && !broadcastViewerReceiveOnly
                if (needLocalVideo && localVideoTrack == null) {
                    Log.e(tag, "startCall aborted: local video track missing after Camera Kit prep room=$roomId")
                    return@runAfterSurfaceReady
                }
                scheduleStartCallAfterCameraKitStagger()
                return@runAfterSurfaceReady
            }

            ensureLocalMediaPrepared()
            if (localAudioTrack == null) {
                Log.e(tag, "startCall aborted: local audio track missing (mic permission?)")
                return@runAfterSurfaceReady
            }
            val needLocalVideo = !audioOnly && !broadcastViewerReceiveOnly
            if (needLocalVideo && localVideoTrack == null) {
                Log.e(tag, "startCall aborted: local video track missing (permissions / capturer failed?)")
                return@runAfterSurfaceReady
            }
            finishHostStartCallSignaling()
        }
    }

    private fun attachBroadcastHostActiveViewersListener() {
        activeViewersRef?.let { ref ->
            activeViewersListener?.let { ref.removeEventListener(it) }
        }
        val ref = database.child("activeViewers")
        activeViewersRef = ref
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val viewerId = snapshot.key?.trim().orEmpty()
                val blockSelfUid = broadcastLivePublishersPublisherUid?.trim()?.takeIf { it.isNotEmpty() } ?: roomId
                if (viewerId.isEmpty() || viewerId == blockSelfUid) return
                Log.e("E2E_DIAG_LIVE", "Viewer joined activeViewers node: viewerUid=$viewerId room=$roomId — spawning host peer")
                onMain { spawnHostPeerForViewer(viewerId) }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val viewerId = snapshot.key?.trim().orEmpty()
                if (viewerId.isEmpty()) return
                onMain { tearDownHostPeerForViewer(viewerId) }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e(tag, "activeViewers cancelled room=$roomId", error.toException())
            }
        }
        activeViewersListener = listener
        ref.addChildEventListener(listener)
    }

    private fun spawnHostPeerForViewer(viewerId: String) {
        synchronized(hostViewerSessions) {
            if (hostViewerSessions.containsKey(viewerId)) return
        }
        val f = factory ?: run {
            Log.e(tag, "spawnHostPeerForViewer: factory null viewer=$viewerId")
            return
        }
        val peerRef = database.child("peers").child(viewerId)
        Log.d(
            "WebRTC_SIGNAL",
            "Broadcast host will signal at rooms/$roomId/peers/$viewerId/{offer,answer,streamerCandidates,viewerCandidates}"
        )
        val session = HostViewerSession(
            viewerId = viewerId,
            peerConnection = null,
            remoteDescriptionSet = false,
            pendingIce = mutableListOf(),
            answerListener = null,
            iceListener = null,
            iceRef = null
        )
        synchronized(hostViewerSessions) {
            hostViewerSessions[viewerId] = session
        }

        managerScope.launch {
            if (destroyed) return@launch
            if (synchronized(hostViewerSessions) { hostViewerSessions[viewerId] } !== session) return@launch
            val iceServers = iceServersForNewPeerConnection()
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }

            val pc = f.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                onMain {
                    try {
                        peerRef.child("streamerCandidates").push().setValue(
                            mapOf(
                                "sdpMid" to candidate.sdpMid,
                                "sdpMLineIndex" to candidate.sdpMLineIndex,
                                "sdp" to candidate.sdp
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(tag, "host ICE out viewer=$viewerId", e)
                    }
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                onMain {
                    val track = transceiver?.receiver?.track()
                    muteRemoteAudioIfBroadcastHost(track, "hostPC.onTrack", viewerId)
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                onMain {
                    Log.d(tag, "host viewer=$viewerId ICE: $state")
                    if (state == PeerConnection.IceConnectionState.CONNECTED ||
                        state == PeerConnection.IceConnectionState.COMPLETED
                    ) {
                        val hostPc = synchronized(hostViewerSessions) { hostViewerSessions[viewerId]?.peerConnection }
                        applyOutboundVideoBitrate(hostPc, context.applicationContext)
                    }
                    onConnectionStateChanged?.invoke(state)
                }
            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {
                onMain {
                    stream?.audioTracks?.forEach { t ->
                        muteRemoteAudioIfBroadcastHost(t, "hostPC.onAddStream(audio)", viewerId)
                    }
                }
            }
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, p1: Array<out MediaStream>?) {
                onMain {
                    muteRemoteAudioIfBroadcastHost(receiver?.track(), "hostPC.onAddTrack", viewerId)
                }
            }
        }) ?: run {
            synchronized(hostViewerSessions) { hostViewerSessions.remove(viewerId) }
            Log.e(tag, "createPeerConnection null for viewer=$viewerId")
            return@launch
        }

        session.peerConnection = pc
        try {
            localVideoTrack?.let { pc.addTrack(it, listOf("main")) }
            localAudioTrack?.let { pc.addTrack(it, listOf("main")) }
        } catch (e: Exception) {
            Log.e(tag, "host addTrack viewer=$viewerId", e)
        }

        val iceInRef = peerRef.child("viewerCandidates")
        val iceListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, p1: String?) {
                try {
                    val sdp = snapshot.child("sdp").getValue(String::class.java) ?: return
                    val mid = snapshot.child("sdpMid").getValue(String::class.java) ?: return
                    val idx = snapshot.child("sdpMLineIndex").getValue(Int::class.java) ?: return
                    val candidate = IceCandidate(mid, idx, sdp)
                    onMain {
                        val cur = synchronized(hostViewerSessions) { hostViewerSessions[viewerId] } ?: return@onMain
                        val conn = cur.peerConnection ?: return@onMain
                        if (cur.remoteDescriptionSet) {
                            conn.addIceCandidate(candidate)
                            Log.e("E2E_DIAG_ICE", "Added ICE Candidate from remote (broadcast live) viewer=$viewerId mid=$mid room=$roomId")
                        } else {
                            Log.e("E2E_DIAG_ICE", "Queued ICE Candidate broadcast viewer=$viewerId mid=$mid pendingSize=${cur.pendingIce.size + 1}")
                            cur.pendingIce.add(candidate)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "host ICE in malformed viewer=$viewerId", e)
                }
            }

            override fun onChildChanged(p0: DataSnapshot, p1: String?) {}
            override fun onChildRemoved(p0: DataSnapshot) {}
            override fun onChildMoved(p0: DataSnapshot, p1: String?) {}
            override fun onCancelled(p0: DatabaseError) {}
        }
        iceInRef.addChildEventListener(iceListener)
        session.iceListener = iceListener
        session.iceRef = iceInRef

        val ansListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onMain {
                    val cur = synchronized(hostViewerSessions) { hostViewerSessions[viewerId] } ?: return@onMain
                    if (!snapshot.exists() || cur.remoteDescriptionSet) return@onMain
                    val desc = snapshot.child("description").getValue(String::class.java) ?: return@onMain
                    val type = snapshot.child("type").getValue(String::class.java) ?: return@onMain
                    val remote = SessionDescription(SessionDescription.Type.fromCanonicalForm(type), desc)
                    val conn = cur.peerConnection ?: return@onMain
                    conn.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            onMain {
                                val slot = synchronized(hostViewerSessions) { hostViewerSessions[viewerId] } ?: return@onMain
                                slot.remoteDescriptionSet = true
                                val drainCount = slot.pendingIce.size
                                if (drainCount > 0) {
                                    Log.e("E2E_DIAG_ICE", "Draining $drainCount pending ICE candidates (broadcast host) viewer=$viewerId room=$roomId")
                                }
                                slot.pendingIce.forEach { c ->
                                    slot.peerConnection?.addIceCandidate(c)
                                    Log.e("E2E_DIAG_ICE", "Added ICE Candidate from remote (broadcast drained) viewer=$viewerId mid=${c.sdpMid} room=$roomId")
                                }
                                slot.pendingIce.clear()
                                Log.d("WebRTC_SIGNAL", "Broadcast host REMOTE ANSWER applied viewer=$viewerId room=$roomId")
                            }
                        }

                        override fun onSetFailure(p0: String?) {
                            Log.e(tag, "host setRemote ANSWER failed viewer=$viewerId: $p0")
                        }
                    }, remote)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        peerRef.child("answer").addValueEventListener(ansListener)
        session.answerListener = ansListener

        // Host is the broadcaster: send A/V to viewers; do not subscribe to viewer microphone (prevents loopback echo).
        val constraints = MediaConstraints().apply {
            mandatory.add(
                MediaConstraints.KeyValuePair(
                    "OfferToReceiveVideo",
                    if (audioOnly) "false" else "true"
                )
            )
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }
        try {
            pc.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    onMain {
                        val s = sdp ?: return@onMain
                        val conn = synchronized(hostViewerSessions) { hostViewerSessions[viewerId]?.peerConnection } ?: return@onMain
                        conn.setLocalDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                onMain {
                                    try {
                                        Log.d(
                                            "WebRTC_SIGNAL",
                                            "Broadcast OFFER setLocal OK viewer=$viewerId room=$roomId len=${s.description.length}"
                                        )
                                        applyOutboundVideoBitrate(pc, context.applicationContext)
                                        Log.d(
                                            "WebRTC_SIGNAL",
                                            "Host writing SDP offer → rooms/$roomId/peers/$viewerId/offer"
                                        )
                                        peerRef.child("offer").setValue(
                                            mapOf(
                                                "type" to s.type.canonicalForm(),
                                                "description" to s.description
                                            )
                                        )
                                    } catch (e: Exception) {
                                        Log.e(tag, "broadcast offer write viewer=$viewerId", e)
                                    }
                                }
                            }

                            override fun onSetFailure(p0: String?) {
                                Log.e(tag, "host setLocal OFFER failed viewer=$viewerId: $p0")
                            }
                        }, s)
                    }
                }

                override fun onCreateFailure(p0: String?) {
                    Log.e(tag, "host createOffer failed viewer=$viewerId: $p0")
                }
            }, constraints)
        } catch (e: Exception) {
            Log.e(tag, "host createOffer invoke viewer=$viewerId", e)
        }
        }
    }

    private fun tearDownHostPeerForViewer(viewerId: String) {
        val session = synchronized(hostViewerSessions) { hostViewerSessions.remove(viewerId) } ?: return
        session.answerListener?.let { database.child("peers").child(viewerId).child("answer").removeEventListener(it) }
        session.iceListener?.let { l -> session.iceRef?.removeEventListener(l) }
        runCatching { session.peerConnection?.dispose() }
        Log.d("WebRTC_SIGNAL", "Tore down host peer viewer=$viewerId room=$roomId")
    }

    fun joinCall() {
        runAfterSurfaceReady {
            if (!initPeerConnectionFactoryIfNeeded()) {
                Log.e(tag, "joinCall aborted: factory init failed")
                return@runAfterSurfaceReady
            }
            if (!initSurfaceRenderersIfNeeded()) {
                Log.e(tag, "joinCall aborted: renderer init failed")
                return@runAfterSurfaceReady
            }
            flushPendingRemoteVideoTrackAfterSurfaceReady("postRendererInitJoinCall")
            if (factory == null) {
                Log.e(tag, "joinCall aborted: PeerConnectionFactory is null")
                return@runAfterSurfaceReady
            }
            if (!hasRuntimeMediaPermissionsForLocalStream()) {
                return@runAfterSurfaceReady
            }
            ensureLocalMediaPrepared()
            if (localAudioTrack == null) {
                Log.e(tag, "joinCall aborted: local audio track missing (mic permission?)")
                return@runAfterSurfaceReady
            }
            val needLocalVideoJoin = !audioOnly && !broadcastViewerReceiveOnly
            if (needLocalVideoJoin && localVideoTrack == null) {
                Log.e(tag, "joinCall aborted: local video track missing (permissions / capturer failed?)")
                return@runAfterSurfaceReady
            }
            isStreamer = false
            peerDisconnectedNotified = false

            if (broadcastMode) {
                val vid = viewerSignalingId?.trim().orEmpty()
                if (vid.isEmpty()) {
                    Log.e(tag, "joinCall broadcast aborted: viewerSignalingId missing room=$roomId")
                    return@runAfterSurfaceReady
                }
                signalingRoot = database.child("peers").child(vid)
                Log.d(
                    "WebRTC_SIGNAL",
                    "Viewer listening to path: rooms/$roomId/peers/$vid/offer (hostRoomId=roomId, myViewerId=$vid)"
                )
                try {
                    val av = database.child("activeViewers").child(vid)
                    av.onDisconnect().removeValue()
                    av.setValue(mapOf("joinedAt" to ServerValue.TIMESTAMP))
                    Log.d("WebRTC_SIGNAL", "Broadcast viewer registered activeViewers/$vid room=$roomId")
                } catch (e: Exception) {
                    Log.e(tag, "activeViewers register failed viewer=$vid", e)
                }
            } else {
                signalingRoot = database
                Log.d(
                    "WebRTC_SIGNAL",
                    "1:1 callee flat RTDB signaling room=$roomId → offer/answer/ICE at rooms/$roomId/* (broadcastMode=false)"
                )
                Log.e("E2E_DIAG_WEBRTC", "1:1 Call - isHost: false, RoomID: $roomId, Mode: Broadcast=$broadcastMode, AudioOnly=$audioOnly")
            }

            if (broadcastViewerReceiveOnly) {
                routeLiveAudienceToSpeaker()
            }

            managerScope.launch {
                if (destroyed) return@launch
                createPeerConnection()
                if (destroyed) return@launch
                if (useFirestoreForOneToOneSignaling()) {
                    attachFirestoreOfferListener()
                } else {
                    offerListener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        onMain {
                            try {
                                if (!snapshot.exists() || isRemoteDescriptionSet) return@onMain
                                val desc = snapshot.child("description").getValue(String::class.java) ?: return@onMain
                                val type = snapshot.child("type").getValue(String::class.java) ?: return@onMain
                                val sdp = SessionDescription(SessionDescription.Type.fromCanonicalForm(type), desc)
                                peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                                    override fun onSetSuccess() {
                                        onMain {
                                            try {
                                                Log.d("WebRTC_SIGNAL", "SDP REMOTE OFFER applied room=$roomId")
                                                isRemoteDescriptionSet = true
                                                drainPendingCandidates()
                                                createAnswer()
                                            } catch (e: Exception) {
                                                Log.e(tag, "HANDSHAKE after setRemote OFFER (createAnswer) room=$roomId", e)
                                            }
                                        }
                                    }

                                    override fun onSetFailure(p0: String?) {
                                        Log.e(tag, "setRemoteDescription OFFER failed room=$roomId: $p0")
                                    }
                                }, sdp)
                            } catch (e: Exception) {
                                Log.w(tag, "Ignoring malformed offer payload in room $roomId", e)
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}
                }
                offerListener?.let { signalingRoot.child("offer").addValueEventListener(it) }
                }
            }
        }
    }

    private fun createAnswer() {
        try {
            peerConnection?.createAnswer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    onMain {
                        try {
                            val s = sdp ?: return@onMain
                            peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                                override fun onSetSuccess() {
                                    onMain {
                                        try {
                                            Log.d(
                                                "WebRTC_SIGNAL",
                                                "SDP ANSWER setLocal OK room=$roomId type=${s.type} len=${s.description.length}"
                                            )
                                            applyOutboundVideoBitrate(peerConnection, context.applicationContext)
                                            if (useFirestoreForOneToOneSignaling()) {
                                                managerScope.launch(Dispatchers.IO) {
                                                    val ok = firebaseSignaling.mergeCallWebRtcAnswer(
                                                        roomId,
                                                        s.type.canonicalForm(),
                                                        s.description,
                                                    )
                                                    if (!ok) {
                                                        Log.e(tag, "mergeCallWebRtcAnswer failed room=$roomId")
                                                    }
                                                }
                                            } else {
                                                signalingRoot.child("answer").setValue(
                                                    mapOf(
                                                        "type" to s.type.canonicalForm(),
                                                        "description" to s.description
                                                    )
                                                )
                                                writeAnswerMirrorToFirestore(s)
                                            }
                                        } catch (e: Exception) {
                                            Log.e(tag, "HANDSHAKE answer setLocal / signaling room=$roomId", e)
                                        }
                                    }
                                }

                                override fun onSetFailure(p0: String?) {
                                    Log.e(tag, "setLocalDescription ANSWER failed room=$roomId: $p0")
                                }
                            }, s)
                        } catch (e: Exception) {
                            Log.e(tag, "HANDSHAKE createAnswer → setLocal ANSWER room=$roomId", e)
                        }
                    }
                }

                override fun onCreateFailure(p0: String?) {
                    Log.e(tag, "createAnswer failed room=$roomId: $p0")
                }
            }, oneToOneMediaConstraints())
        } catch (e: Exception) {
            Log.e(tag, "createAnswer invocation failed room=$roomId", e)
        }
    }

    private suspend fun iceServersForNewPeerConnection(): List<PeerConnection.IceServer> {
        val fromFn = firebaseSignaling.getTurnCredentials()
        return if (fromFn.isNotEmpty()) {
            logWebRTC("TURN_CREDENTIALS", "source" to "cloud_function", "count" to fromFn.size)
            fromFn
        } else {
            logWebRTC("TURN_CREDENTIALS", "source" to "stun_fallback", "reason" to "empty_or_error")
            IceServerCatalog.currentOrStun()
        }
    }

    private suspend fun createPeerConnection() {
        if (factory == null) {
            Log.e(tag, "createPeerConnection skipped: factory is null")
            return
        }
        val iceServers = iceServersForNewPeerConnection()
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }

        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                onMain {
                    try {
                        if (useFirestoreForOneToOneSignaling()) {
                            val coll = if (isStreamer) "offerCandidates" else "answerCandidates"
                            managerScope.launch(Dispatchers.IO) {
                                val ok = firebaseSignaling.addCallWebRtcIceCandidate(
                                    roomId,
                                    coll,
                                    candidate.sdp,
                                    candidate.sdpMid,
                                    candidate.sdpMLineIndex,
                                )
                                if (!ok) {
                                    Log.e(tag, "Firestore ICE add failed room=$roomId coll=$coll")
                                }
                            }
                            Log.d(
                                "WebRTC_SIGNAL",
                                "ICE OUT (Firestore) room=$roomId coll=$coll mid=${candidate.sdpMid} idx=${candidate.sdpMLineIndex}",
                            )
                            return@onMain
                        }
                        val path = if (isStreamer) "streamerCandidates" else "viewerCandidates"
                        Log.d(
                            "WebRTC_SIGNAL",
                            "ICE OUT room=$roomId path=$path mid=${candidate.sdpMid} idx=${candidate.sdpMLineIndex} sdpPrefix=${candidate.sdp.take(48)}"
                        )
                        signalingRoot.child(path).push().setValue(
                            mapOf(
                                "sdpMid" to candidate.sdpMid,
                                "sdpMLineIndex" to candidate.sdpMLineIndex,
                                "sdp" to candidate.sdp
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(tag, "onIceCandidate / signaling write failed room=$roomId", e)
                    }
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                onMain {
                    try {
                        val receiver = transceiver?.receiver ?: return@onMain
                        val mediaTrack = receiver.track() ?: return@onMain
                        muteRemoteAudioIfBroadcastHost(mediaTrack, "onTrack")
                        suppressRemoteAudioIfVideoOnlyViewer(mediaTrack, "onTrack")
                        if (mediaTrack.kind() == MediaStreamTrack.AUDIO_TRACK_KIND) {
                            (mediaTrack as? AudioTrack)?.let { rememberRemoteAudioTrack(it) }
                            return@onMain
                        }
                        if (mediaTrack.kind() != MediaStreamTrack.VIDEO_TRACK_KIND) return@onMain
                        val track = mediaTrack as? VideoTrack ?: return@onMain
                        Log.d(
                            "WebRTC_SIGNAL",
                            "onTrack: remote VIDEO renderersInit=$renderersInitialized room=$roomId"
                        )
                        attachRemoteVideoTrackOrQueue(track, "onTrack")
                    } catch (e: Exception) {
                        Log.e("WEBRTC_CRASH", "remote onTrack failed room=$roomId", e)
                    }
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                onMain {
                    try {
                        val toLabel = state?.toString() ?: "null"
                        logWebRTC("ICE_STATE_CHANGE", "from" to previousIceConnectionStateLabel, "to" to toLabel)
                        previousIceConnectionStateLabel = toLabel
                        Log.d(tag, "ICE: $state")
                        onConnectionStateChanged?.invoke(state)
                        when (state) {
                            PeerConnection.IceConnectionState.CONNECTED,
                            PeerConnection.IceConnectionState.COMPLETED -> {
                                sessionReconnectAttempts.set(0)
                                applyOutboundVideoBitrate(peerConnection, context.applicationContext)
                                // Connection (re-)established — cancel any pending restart timer
                                // and reset the disconnected-notified guard so future drops recover.
                                iceRestartJob?.cancel()
                                iceRestartJob = null
                                iceFailureRecoveryJob?.cancel()
                                iceFailureRecoveryJob = null
                                peerDisconnectedNotified = false
                                remoteVideoConnectedRealtimeMs = SystemClock.elapsedRealtime()
                            }
                            PeerConnection.IceConnectionState.DISCONNECTED -> {
                                // Transient loss (e.g. brief Wi-Fi handover). Wait 3 s; if still disconnected, restart ICE.
                                if (iceRestartJob?.isActive != true) {
                                    iceRestartJob = managerScope.launch {
                                        delay(3_000)
                                        val currentState = peerConnection?.iceConnectionState()
                                        if (currentState == PeerConnection.IceConnectionState.DISCONNECTED) {
                                            Log.w(tag, "ICE still DISCONNECTED after 3 s — restartIce() room=$roomId")
                                            val ok = safeRestartIce("ice_still_disconnected_after_3s")
                                            if (ok == false) {
                                                if (shouldUseFirestoreFullRenegotiation()) {
                                                    scheduleFirestoreFullRenegotiationIfNeeded("ice_disconnected_restart_failed")
                                                } else if (!peerDisconnectedNotified) {
                                                    peerDisconnectedNotified = true
                                                    onPeerDisconnected?.invoke()
                                                }
                                            } else if (ok == null && !peerDisconnectedNotified) {
                                                if (shouldUseFirestoreFullRenegotiation()) {
                                                    scheduleFirestoreFullRenegotiationIfNeeded("ice_disconnected_pc_unavailable")
                                                } else {
                                                    peerDisconnectedNotified = true
                                                    onPeerDisconnected?.invoke()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            PeerConnection.IceConnectionState.FAILED -> {
                                iceRestartJob?.cancel()
                                iceRestartJob = null
                                if (shouldUseFirestoreFullRenegotiation()) {
                                    scheduleFirestoreFullRenegotiationIfNeeded("ice_failed")
                                } else {
                                    scheduleIceFailureRecovery("ice_failed")
                                }
                            }
                            PeerConnection.IceConnectionState.CLOSED -> {
                                iceRestartJob?.cancel()
                                iceRestartJob = null
                                iceFailureRecoveryJob?.cancel()
                                iceFailureRecoveryJob = null
                                if (!peerDisconnectedNotified) {
                                    peerDisconnectedNotified = true
                                    onPeerDisconnected?.invoke()
                                }
                            }
                            else -> Unit
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "onIceConnectionChange callback failed room=$roomId", e)
                    }
                }
            }

            override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {
                onMain {
                    try {
                        Log.d(tag, "PeerConnectionState: $state room=$roomId")
                        when (state) {
                            PeerConnection.PeerConnectionState.CONNECTED -> {
                                sessionReconnectAttempts.set(0)
                                iceFailureRecoveryJob?.cancel()
                                iceFailureRecoveryJob = null
                            }
                            PeerConnection.PeerConnectionState.FAILED ->
                                if (shouldUseFirestoreFullRenegotiation()) {
                                    scheduleFirestoreFullRenegotiationIfNeeded("peer_connection_failed")
                                } else {
                                    scheduleIceFailureRecovery("peer_connection_failed")
                                }
                            PeerConnection.PeerConnectionState.CLOSED -> {
                                iceFailureRecoveryJob?.cancel()
                                iceFailureRecoveryJob = null
                                if (!peerDisconnectedNotified) {
                                    peerDisconnectedNotified = true
                                    onPeerDisconnected?.invoke()
                                }
                            }
                            else -> Unit
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "onConnectionChange failed room=$roomId", e)
                    }
                }
            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {
                onMain {
                    try {
                        stream?.audioTracks?.forEach { t ->
                            muteRemoteAudioIfBroadcastHost(t, "onAddStream(audio)")
                            suppressRemoteAudioIfVideoOnlyViewer(t, "onAddStream(audio)")
                            rememberRemoteAudioTrack(t)
                        }
                        val track = stream?.videoTracks?.firstOrNull() ?: return@onMain
                        if (track === remoteVideoTrack) return@onMain
                        attachRemoteVideoTrackOrQueue(track, "onAddStream")
                    } catch (e: Exception) {
                        Log.e("WEBRTC_CRASH", "onAddStream failed room=$roomId", e)
                    }
                }
            }

            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                onMain {
                    try {
                        val mediaTrack = receiver?.track() ?: return@onMain
                        muteRemoteAudioIfBroadcastHost(mediaTrack, "onAddTrack")
                        suppressRemoteAudioIfVideoOnlyViewer(mediaTrack, "onAddTrack")
                        if (mediaTrack.kind() == MediaStreamTrack.AUDIO_TRACK_KIND) {
                            (mediaTrack as? AudioTrack)?.let { rememberRemoteAudioTrack(it) }
                            return@onMain
                        }
                        if (mediaTrack.kind() != MediaStreamTrack.VIDEO_TRACK_KIND) return@onMain
                        val track = mediaTrack as? VideoTrack ?: return@onMain
                        if (track === remoteVideoTrack) return@onMain
                        Log.d("WebRTC_SIGNAL", "onAddTrack: remote VIDEO room=$roomId")
                        attachRemoteVideoTrackOrQueue(track, "onAddTrack")
                    } catch (e: Exception) {
                        Log.e("WEBRTC_CRASH", "remote onAddTrack failed room=$roomId", e)
                    }
                }
            }
        })

        try {
            localVideoTrack?.let { peerConnection?.addTrack(it, listOf("main")) }
            localAudioTrack?.let { peerConnection?.addTrack(it, listOf("main")) }
        } catch (e: Exception) {
            Log.e("WEBRTC_CRASH", "addTrack failed", e)
        }

        // UNIFIED_PLAN safeguard: if this is a video/broadcast call but the local video track
        // is absent (camera init failed), inject an explicit RECV_ONLY transceiver so the SDP
        // still contains a video m= section and the remote video track can arrive via onTrack.
        // Without this the engine generates an audio-only offer/answer, causing a black screen.
        if (localVideoTrack == null && !audioOnly) {
            try {
                peerConnection?.addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
                )
                Log.d(tag, "createPeerConnection: injected RECV_ONLY video transceiver (no local video track) room=$roomId")
            } catch (e: Exception) {
                Log.e(tag, "addTransceiver RECV_ONLY video failed room=$roomId", e)
            }
        }

        listenForIceCandidatesMaybeFirestore()
        if (isStreamer) {
            if (!useFirestoreForOneToOneSignaling()) {
                listenForAnswer()
            } else {
                listenForAnswerFirestore()
            }
        }
        ensureCallResilienceHooksRegistered()
    }

    private fun listenForIceCandidatesMaybeFirestore() {
        if (useFirestoreForOneToOneSignaling()) {
            listenForIceCandidatesFirestore()
        } else {
            listenForIceCandidates()
        }
    }

    private fun listenForIceCandidates() {
        val remoteCandidatesPath = if (isStreamer) "viewerCandidates" else "streamerCandidates"
        candidatesRef = signalingRoot.child(remoteCandidatesPath)
        candidatesListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, p1: String?) {
                try {
                    val sdp = snapshot.child("sdp").getValue(String::class.java) ?: return
                    val mid = snapshot.child("sdpMid").getValue(String::class.java) ?: return
                    val idx = snapshot.child("sdpMLineIndex").getValue(Int::class.java) ?: return
                    val candidate = IceCandidate(mid, idx, sdp)
                    Log.d(
                        "WebRTC_SIGNAL",
                        "ICE IN room=$roomId path=$remoteCandidatesPath mid=$mid idx=$idx sdpPrefix=${sdp.take(48)} remoteSet=$isRemoteDescriptionSet"
                    )
                    onMain {
                        if (isRemoteDescriptionSet) {
                            peerConnection?.addIceCandidate(candidate)
                            Log.e("E2E_DIAG_ICE", "Added ICE Candidate from remote (live) mid=$mid room=$roomId")
                        } else {
                            Log.e("E2E_DIAG_ICE", "Queued ICE Candidate (remoteDesc not set yet) mid=$mid room=$roomId pendingSize=${pendingIceCandidates.size + 1}")
                            pendingIceCandidates.add(candidate)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Ignoring malformed ICE candidate in room $roomId", e)
                }
            }

            override fun onChildChanged(p0: DataSnapshot, p1: String?) {}
            override fun onChildRemoved(p0: DataSnapshot) {}
            override fun onChildMoved(p0: DataSnapshot, p1: String?) {}
            override fun onCancelled(p0: DatabaseError) {}
        }
        candidatesListener?.let { listener -> candidatesRef?.addChildEventListener(listener) }
    }

    private fun listenForAnswer() {
        answerListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onMain {
                    try {
                        if (!snapshot.exists() || isRemoteDescriptionSet) return@onMain
                        val desc = snapshot.child("description").getValue(String::class.java) ?: return@onMain
                        val type = snapshot.child("type").getValue(String::class.java) ?: return@onMain
                        val remote = SessionDescription(SessionDescription.Type.fromCanonicalForm(type), desc)
                        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                onMain {
                                    try {
                                        Log.d("WebRTC_SIGNAL", "SDP REMOTE ANSWER applied room=$roomId")
                                        isRemoteDescriptionSet = true
                                        drainPendingCandidates()
                                    } catch (e: Exception) {
                                        Log.e(tag, "HANDSHAKE after setRemote ANSWER room=$roomId", e)
                                    }
                                }
                            }

                            override fun onSetFailure(p0: String?) {
                                Log.e(tag, "setRemoteDescription ANSWER failed room=$roomId: $p0")
                            }
                        }, remote)
                    } catch (e: Exception) {
                        Log.w(tag, "Ignoring malformed answer payload in room $roomId", e)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        answerListener?.let { signalingRoot.child("answer").addValueEventListener(it) }
    }

    private fun drainPendingCandidates() {
        val count = pendingIceCandidates.size
        if (count > 0) {
            Log.e("E2E_DIAG_ICE", "Draining $count pending ICE candidates after setRemoteDescription room=$roomId")
        }
        pendingIceCandidates.forEach { candidate ->
            peerConnection?.addIceCandidate(candidate)
            Log.e("E2E_DIAG_ICE", "Added ICE Candidate from remote (drained) mid=${candidate.sdpMid} room=$roomId")
        }
        pendingIceCandidates.clear()
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }

    /**
     * Routes the PK challenger's incoming video to [renderer] for split-screen display.
     *
     * Safe to call before or after the remote video track arrives:
     * - If called **before** the track arrives, [pkChallengerSink] is stored and the next
     *   [onTrack]/[onAddTrack] callback will route the track directly to [renderer].
     * - If called **after** the track has already arrived, it is migrated from [remoteView]
     *   to [renderer] immediately on the main thread.
     *
     * The renderer is initialised with the shared [EglBase] context automatically. Can be
     * called from any thread.
     */
    fun attachPkChallengerSink(renderer: SurfaceViewRenderer) {
        onMain {
            try {
                // Detach the track from both 1:1 sinks so it doesn't render in two places.
                remoteVideoTrack?.let { track ->
                    remoteVideoFrameTapSink?.let { tap -> runCatching { track.removeSink(tap) } }
                    remoteVideoFrameTapSink = null
                    runCatching { track.removeSink(remoteView) }
                    runCatching { track.removeSink(localView) }
                }
                // Initialise the PK renderer with the same EGL context as the other surfaces.
                eglBase?.let { egl ->
                    runCatching { renderer.init(egl.eglBaseContext, null) }
                    renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    renderer.setMirror(false)
                }
                pkChallengerSink = renderer
                // If the remote track has already arrived, attach it to the new renderer now.
                remoteVideoTrack?.let { track ->
                    attachRemoteVideoWithFrameTap(track, renderer)
                    Log.d("WebRTC_SIGNAL", "attachPkChallengerSink: existing track routed to PK renderer room=$roomId")
                }
                Log.d("WebRTC_SIGNAL", "attachPkChallengerSink: set, trackAlreadyPresent=${remoteVideoTrack != null} room=$roomId")
            } catch (e: Exception) {
                Log.e("WEBRTC_CRASH", "attachPkChallengerSink failed room=$roomId", e)
            }
        }
    }

    /**
     * PK guest: fan out the same encoded camera/mic to spectators on a second RTDB tree using this
     * factory and tracks (caller must not [onDestroy] this manager until fanout is stopped).
     */
    fun peekFactoryAndLiveTracks(): Triple<PeerConnectionFactory?, VideoTrack?, AudioTrack?> =
        Triple(factory, localVideoTrack, localAudioTrack)

    /** Second live viewer: share factory/EGL after the first [joinCall] path has built them. */
    fun tryExportFactoryForSecondaryViewer(): Pair<PeerConnectionFactory, EglBase>? {
        val f = factory ?: return null
        val e = eglBase ?: return null
        return f to e
    }

    fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    /**
     * Viewer / callee: when `false`, disables playback of the remote party's audio (you stop hearing them).
     * Does not mute the streamer's microphone for other viewers.
     */
    fun setRemoteAudioHearEnabled(enabled: Boolean) {
        remoteAudioHearEnabled = enabled
        onMain { applyRemoteAudioHearEnabled() }
    }

    private fun applyRemoteAudioHearEnabled() {
        val t = remoteAudioTrack ?: return
        if (broadcastMode && isStreamer) return
        if (broadcastViewerReceiveOnly && !broadcastViewerReceiveAudio) {
            runCatching { t.setEnabled(false) }
            return
        }
        runCatching { t.setEnabled(remoteAudioHearEnabled) }
    }

    private fun rememberRemoteAudioTrack(track: AudioTrack) {
        if (broadcastMode && isStreamer) return
        remoteAudioTrack = track
        applyRemoteAudioHearEnabled()
    }

    fun setVideoEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    /**
     * Stops the camera capturer and mutes the local video track.
     * Call from [Activity.onPause] to release the camera hardware lock while the app is backgrounded.
     * Safe to call even if no video is active (audio-only call or already paused).
     */
    fun pauseCamera() {
        try {
            videoCapturer?.stopCapture()
            localVideoTrack?.setEnabled(false)
            Log.d(tag, "pauseCamera: capture stopped, video track muted room=$roomId")
        } catch (e: Exception) {
            Log.e(tag, "pauseCamera failed room=$roomId", e)
        }
    }

    /**
     * Restarts the camera capturer and unmutes the local video track.
     * Call from [Activity.onResume] to restore the camera feed after the app returns to foreground.
     * Safe to call even if [pauseCamera] was never called.
     */
    fun resumeCamera() {
        tryStartCaptureAfterResume()
    }

    /** @return false if [startCapture] failed (e.g. camera busy). */
    private fun tryStartCaptureAfterResume(): Boolean {
        return try {
            val (capW, capH) = localVideoCaptureWidthHeight()
            val fps = captureTargetFps(context)
            videoCapturer?.startCapture(capW, capH, fps)
            localVideoTrack?.setEnabled(true)
            Log.d(tag, "resumeCamera: capture restarted, video track unmuted room=$roomId")
            logWebRTC("CAMERA_RESUME_OK", "room" to roomId)
            true
        } catch (e: Exception) {
            Log.e(tag, "resumeCamera failed room=$roomId", e)
            logWebRTC("CAMERA_RESUME_FAIL", "room" to roomId)
            false
        }
    }

    private fun detachRoomListeners() {
        detachCallPresence()
        if (broadcastMode && isStreamer) {
            activeViewersListener?.let { l -> activeViewersRef?.removeEventListener(l) }
            activeViewersListener = null
            activeViewersRef = null
            synchronized(hostViewerSessions) {
                hostViewerSessions.keys.toList().forEach { tearDownHostPeerForViewer(it) }
            }
        } else {
            if (useFirestoreForOneToOneSignaling()) {
                removeAllFirestoreSignalingRegistrations()
                callAnswerTimeoutJob?.cancel()
                callAnswerTimeoutJob = null
            } else {
                offerListener?.let { signalingRoot.child("offer").removeEventListener(it) }
                answerListener?.let { signalingRoot.child("answer").removeEventListener(it) }
                candidatesListener?.let { listener -> candidatesRef?.removeEventListener(listener) }
            }
            offerListener = null
            answerListener = null
            candidatesListener = null
            candidatesRef = null
        }
    }

    fun endPeerSessionOnly() {
        iceRestartJob?.cancel()
        iceFailureRecoveryJob?.cancel()
        callAnswerTimeoutJob?.cancel()
        fullRenegotiationInFlight = false
        sessionReconnectAttempts.set(0)
        detachRoomListeners()
        runCatching { peerConnection?.dispose() }
        peerConnection = null
        isRemoteDescriptionSet = false
        pendingIceCandidates.clear()
        synchronized(hostViewerSessions) { hostViewerSessions.clear() }
        pkChallengerSink = null
        remoteVideoTrack = null
        remoteAudioTrack = null
        if (publishGlobalMediaStreams) {
            _globalRemoteVideoTrack.value = null
        }
    }

    /**
     * Full WebRTC teardown for end-of-call / end-of-live-stream. Call exactly once per session.
     * Disposes signaling listeners, **[PeerConnection.dispose]**, capturer, tracks, [PeerConnectionFactory],
     * and surface renderers — required for multi-hour sessions without native memory growth.
     * There is no separate `release()` method; always use this for teardown.
     */
    fun onDestroy() {
        if (destroyed) return
        // Stop delivering frames into VideoSource / beauty pipeline before tearing down native capture.
        // Setting [destroyed] first caused races with in-flight Camera2 frames (refcount < 1 on CaptureThread).
        suppressCameraFrameDelivery = true
        runCatching { videoCapturer?.stopCapture() }
        runCatching { surfaceTextureHelper?.stopListening() }
        destroyed = true
        detachCallPresence()
        unregisterConnectivityReconnectCallback()
        unregisterTrimMemoryCallbackIfNeeded()
        unregisterProcessLifecycleCameraGuard()
        stopRemoteVideoStallWatchdog()
        stopWebRtcStatsDump()
        pendingCameraKitStaggerRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingCameraKitStaggerRunnable = null
        restoreDefaultAudioRoutingIfLiveAudience()
        // Nullify global track StateFlows immediately so Compose observers detach
        // their SurfaceViewRenderer sinks before any native resources are freed.
        if (publishGlobalMediaStreams) {
            _globalRemoteVideoTrack.value = null
            _globalLocalVideoTrack.value = null
        }
        // Clear global EGL only after CaptureThread SurfaceTexture pipeline is disposed (below).
        // Early null caused Compose/other paths to churn while SurfaceTextureHelper still called
        // updateTexImage → "EGLConsumer is not attached" spam on the capture thread.
        pendingRemoteTrack = null

        detachRoomListeners()

        // Cancel the ICE-restart timer and the manager's coroutine scope first so no
        // callbacks can fire against already-freed native resources.
        iceRestartJob?.cancel()
        iceRestartJob = null
        iceFailureRecoveryJob?.cancel()
        iceFailureRecoveryJob = null
        callAnswerTimeoutJob?.cancel()
        callAnswerTimeoutJob = null
        managerScope.cancel()

        // Remove video sinks before releasing renderers to prevent stale native callbacks.
        runCatching { localVideoTrack?.removeSink(localView) }
        runCatching { localVideoTrack?.removeSink(remoteView) }
        runCatching { remoteVideoTrack?.let { track ->
            remoteVideoFrameTapSink?.let { tap -> runCatching { track.removeSink(tap) } }
            remoteVideoFrameTapSink = null
            pkChallengerSink?.let { track.removeSink(it) }
            runCatching { track.removeSink(remoteView) }
            runCatching { track.removeSink(localView) }
        }}
        // PK or future layouts: extra sink not aliased to localView/remoteView must be released.
        runCatching {
            pkChallengerSink?.let { sink ->
                if (sink !== remoteView && sink !== localView) sink.release()
            }
        }

        beautyObserver?.closeFaceDetector()
        beautyObserver = null

        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        cameraKitBridge = null
        runCatching { videoSource?.dispose() }
        runCatching {
            surfaceTextureHelper?.stopListening()
            surfaceTextureHelper?.dispose()
        }
        runCatching { peerConnection?.dispose() }
        runCatching { localAudioTrack?.dispose() }
        runCatching { localVideoTrack?.dispose() }
        localVideoTrack = null
        localAudioTrack = null
        if (!borrowsPeerFactoryStack) {
            runCatching { factory?.dispose() }
            runCatching { (audioDeviceModule as? JavaAudioDeviceModule)?.release() }
            _globalEglContext.value = null
            runCatching { eglBase?.release() }
        }
        factory = null
        audioDeviceModule = null
        eglBase = null
        runCatching { localView.release() }
        runCatching { remoteView.release() }

        videoCapturer = null
        videoSource = null
        surfaceTextureHelper = null
        peerConnection = null
        renderersInitialized = false
        localMediaPrepared = false
        pkChallengerSink = null
        remoteVideoTrack = null
        remoteAudioTrack = null
        callResilienceHooksRegistered = false
        remoteVideoConnectedRealtimeMs = 0L
        lastRemoteVideoFrameMonotonicMs = 0L
        awaitingFrameRecoverySinceWallMs = 0L

        when {
            broadcastMode && !isStreamer -> {
                val vid = viewerSignalingId?.trim().orEmpty()
                if (vid.isNotEmpty()) {
                    runCatching {
                        database.child("activeViewers").child(vid).removeValue()
                        database.child("peers").child(vid).removeValue()
                    }
                }
            }
            else -> runCatching { database.removeValue() }
        }
        if (takeCurrentManagerSlot && currentManager === this) {
            currentManager = null
        }
    }

    companion object {
        /**
         * Max full ICE/PC renegotiation rounds for Firestore 1:1 calls (see [scheduleFirestoreFullRenegotiationIfNeeded]).
         * Keep in sync with reconnect overlay denominator in [com.zipper.datingapp.LiveStreamActivity].
         */
        const val SESSION_FULL_RENEG_MAX_ATTEMPTS = 3

        /**
         * Globally-observable remote video track. Compose screens (e.g. VideoCallScreen) can
         * `collectAsStateWithLifecycle()` this to attach a [SurfaceViewRenderer] sink without
         * needing a direct reference to the current [WebRTCManager] instance.
         * Cleared to null in [onDestroy] so observers see the track disappear when the call ends.
         */
        private val _globalRemoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
        val globalRemoteVideoTrack: StateFlow<VideoTrack?> = _globalRemoteVideoTrack.asStateFlow()

        /**
         * Globally-observable local video track. Compose screens use this to render the local
         * camera preview via a [SurfaceViewRenderer] sink, eliminating the need for a separate
         * CameraX [ProcessCameraProvider] that would conflict with the WebRTC camera capturer.
         * Cleared to null in [onDestroy].
         */
        private val _globalLocalVideoTrack = MutableStateFlow<VideoTrack?>(null)
        val globalLocalVideoTrack: StateFlow<VideoTrack?> = _globalLocalVideoTrack.asStateFlow()

        /**
         * Shared EGL context for the active [WebRTCManager] session. Compose UIs collect this so
         * [SurfaceViewRenderer.init] can run after the engine creates [EglBase], not only on first frame.
         */
        private val _globalEglContext = MutableStateFlow<EglBase.Context?>(null)
        val globalEglContext: StateFlow<EglBase.Context?> = _globalEglContext.asStateFlow()

        /** True when the active camera is the front-facing camera; updated after every [switchCamera] call. */
        private val _globalIsFrontCamera = MutableStateFlow(true)
        val globalIsFrontCamera: StateFlow<Boolean> = _globalIsFrontCamera.asStateFlow()

        @Volatile
        private var currentManager: WebRTCManager? = null

        @Volatile
        private var peerConnectionFactoryInitialized: Boolean = false

        /** Returns the EGL context for the current call session; used to init Compose SurfaceViewRenderers. */
        fun activeEglContext(): EglBase.Context? = currentManager?.eglContext()

        private val peerConnectionFactoryInitLock = Any()

        /**
         * Delay after Camera Kit video capture starts before adding the mic track and opening the
         * host PeerConnection / broadcast signaling path (see [startCall] stagger path).
         */
        const val CAMERA_KIT_TO_PEER_CONNECTION_STAGGER_MS: Long = 1000L

        /** After Firestore offer is written, wait for callee answer map on [calls] doc. */
        const val CALL_ANSWER_FIRESTORE_TIMEOUT_MS: Long = 30_000L

        /**
         * Process-wide init; safe to call from any thread, multiple times.
         * Must succeed before [PeerConnectionFactory] is built. Delegates to [WebRTCInitializer].
         */
        fun ensurePeerConnectionFactoryInitialized(appContext: Context): Boolean {
            synchronized(peerConnectionFactoryInitLock) {
                if (peerConnectionFactoryInitialized) return true
                val ok = WebRTCInitializer.ensureInitialized(appContext)
                if (ok) peerConnectionFactoryInitialized = true
                return ok
            }
        }

        fun activeManager(): WebRTCManager? = currentManager

        /**
         * True when wired/USB/BT (or similar) **output** is active, so we must not force
         * [AudioManager.isSpeakerphoneOn] or audio stays on the built-in speaker instead of the headset.
         */
        fun hasExternalVoiceOutputDevice(am: AudioManager): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    for (d in devices) {
                        if (!d.isSink) continue
                        when (d.type) {
                            AudioDeviceInfo.TYPE_WIRED_HEADSET,
                            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                            AudioDeviceInfo.TYPE_USB_HEADSET,
                            AudioDeviceInfo.TYPE_USB_DEVICE,
                            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                            AudioDeviceInfo.TYPE_BLE_HEADSET,
                            AudioDeviceInfo.TYPE_BLE_SPEAKER,
                            AudioDeviceInfo.TYPE_HDMI,
                            AudioDeviceInfo.TYPE_DOCK,
                            AudioDeviceInfo.TYPE_AUX_LINE,
                            -> return true
                        }
                    }
                } catch (e: Exception) {
                    Log.w("WebRTCManager", "getDevices failed", e)
                }
            }
            @Suppress("DEPRECATION")
            if (am.isWiredHeadsetOn) return true
            @Suppress("DEPRECATION")
            if (am.isBluetoothA2dpOn) return true
            return false
        }

        /**
         * Live / VoIP-style playback: use [preferLoudSpeaker] only when no external sink is present;
         * when headphones or USB/BT audio is connected, speakerphone stays off so WebRTC follows that route.
         */
        fun applyLivePlaybackSpeakerMode(am: AudioManager, preferLoudSpeaker: Boolean) {
            try {
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                val useSpeaker = !hasExternalVoiceOutputDevice(am) && preferLoudSpeaker
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = useSpeaker
                Log.d(
                    "WebRTCManager",
                    "applyLivePlaybackSpeakerMode preferLoud=$preferLoudSpeaker speakerOn=$useSpeaker " +
                        "ext=${hasExternalVoiceOutputDevice(am)}",
                )
            } catch (e: Exception) {
                Log.e("WebRTCManager", "applyLivePlaybackSpeakerMode failed", e)
            }
        }
    }
}
