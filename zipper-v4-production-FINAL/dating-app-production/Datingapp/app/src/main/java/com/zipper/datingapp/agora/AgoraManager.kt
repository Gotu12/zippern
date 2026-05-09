package com.zipper.datingapp.agora

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.TextureView
import com.zipper.datingapp.BuildConfig
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thin wrapper around Agora RTC for Zipper live: solo host/audience, PK (multi-publisher channel), 1:1.
 * Engine uses application context; teardown via [leaveChannel] / [destroy].
 */
class AgoraManager(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _remoteUid = MutableStateFlow<Int?>(null)
    val remoteUid: StateFlow<Int?> = _remoteUid.asStateFlow()

    /** All remote publisher uids currently in the channel (PK spectators bind two tiles). */
    private val _remoteUids = MutableStateFlow<Set<Int>>(emptySet())
    val remoteUids: StateFlow<Set<Int>> = _remoteUids.asStateFlow()

    private val _joinChannelSuccess = MutableStateFlow(false)
    val joinChannelSuccess: StateFlow<Boolean> = _joinChannelSuccess.asStateFlow()

    private var engine: RtcEngine? = null
    private var destroyed = false

    private val eventHandler =
        object : IRtcEngineEventHandler() {
            override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                mainHandler.post {
                    _joinChannelSuccess.value = true
                    Log.d(TAG, "onJoinChannelSuccess channel=$channel uid=$uid elapsed=$elapsed")
                }
            }

            override fun onUserJoined(uid: Int, elapsed: Int) {
                mainHandler.post {
                    _remoteUids.value = _remoteUids.value + uid
                    if (_remoteUid.value == null || _remoteUid.value == uid) {
                        _remoteUid.value = uid
                    }
                    Log.d(TAG, "onUserJoined uid=$uid elapsed=$elapsed")
                }
            }

            override fun onUserOffline(uid: Int, reason: Int) {
                mainHandler.post {
                    val newSet = _remoteUids.value - uid
                    _remoteUids.value = newSet
                    if (_remoteUid.value == uid) {
                        _remoteUid.value = newSet.minOrNull()
                        runCatching { engine?.muteRemoteVideoStream(uid, true) }
                    }
                    Log.d(TAG, "onUserOffline uid=$uid reason=$reason")
                }
            }

            override fun onLeaveChannel(stats: RtcStats?) {
                mainHandler.post {
                    _joinChannelSuccess.value = false
                    Log.d(TAG, "onLeaveChannel")
                }
            }

            override fun onError(err: Int) {
                Log.e(TAG, "onError code=$err")
            }
        }

    private fun ensureEngine(): RtcEngine? {
        if (destroyed) return null
        engine?.let {
            return it
        }
        val appId = BuildConfig.AGORA_APP_ID.trim()
        if (appId.isEmpty()) {
            Log.w(TAG, "AGORA_APP_ID empty — engine not created")
            return null
        }
        return try {
            val cfg =
                RtcEngineConfig().apply {
                    mContext = appContext
                    mAppId = appId
                    mEventHandler = eventHandler
                }
            RtcEngine.create(cfg).also { engine = it }
        } catch (e: Exception) {
            Log.e(TAG, "RtcEngine.create failed", e)
            null
        }
    }

    /** Stable positive uid from Firebase uid string (collisions unlikely at dating-app scale). */
    fun agoraUidFromFirebaseUid(uid: String): Int = Companion.fromFirebaseUid(uid)

    fun bindLocalVideo(
        textureView: TextureView,
        renderMode: Int,
        mirrorMode: Int,
    ): Boolean {
        val rtc = ensureEngine() ?: return false
        val canvas = VideoCanvas(textureView, renderMode, mirrorMode)
        rtc.setupLocalVideo(canvas)
        rtc.setLocalRenderMode(renderMode, mirrorMode)
        return true
    }

    fun bindRemoteVideo(
        textureView: TextureView,
        remoteUid: Int,
        renderMode: Int,
        mirrorMode: Int,
    ): Boolean {
        val rtc = ensureEngine() ?: return false
        val canvas = VideoCanvas(textureView, renderMode, mirrorMode)
        canvas.uid = remoteUid
        rtc.setupRemoteVideo(canvas)
        return true
    }

    fun joinAsBroadcaster(
        channelId: String,
        uid: Int,
        token: String?,
        startPreviewBeforeJoin: Boolean,
    ): Int {
        val rtc = ensureEngine() ?: return -1
        rtc.enableVideo()
        rtc.enableAudio()
        val options =
            ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                publishMicrophoneTrack = true
                publishCameraTrack = true
                autoSubscribeAudio = true
                autoSubscribeVideo = false
                startPreview = startPreviewBeforeJoin
            }
        val tok = token?.trim().orEmpty()
        val code =
            if (tok.isEmpty()) {
                rtc.joinChannel(null, channelId, uid, options)
            } else {
                rtc.joinChannel(tok, channelId, uid, options)
            }
        if (code != 0) {
            Log.e(TAG, "joinAsBroadcaster failed code=$code channel=$channelId")
        }
        return code
    }

    /**
     * PK / co-host: same live-broadcasting channel as [joinAsBroadcaster], but subscribes to peer video/audio
     * so each battler sees the opponent.
     */
    fun joinAsCoHostBroadcaster(
        channelId: String,
        uid: Int,
        token: String?,
        startPreviewBeforeJoin: Boolean,
    ): Int {
        val rtc = ensureEngine() ?: return -1
        rtc.enableVideo()
        rtc.enableAudio()
        val options =
            ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                publishMicrophoneTrack = true
                publishCameraTrack = true
                autoSubscribeAudio = true
                autoSubscribeVideo = true
                startPreview = startPreviewBeforeJoin
            }
        val tok = token?.trim().orEmpty()
        val code =
            if (tok.isEmpty()) {
                rtc.joinChannel(null, channelId, uid, options)
            } else {
                rtc.joinChannel(tok, channelId, uid, options)
            }
        if (code != 0) {
            Log.e(TAG, "joinAsCoHostBroadcaster failed code=$code channel=$channelId")
        }
        return code
    }

    /**
     * Private 1:1 video call — both peers publish and subscribe
     * ([Constants.CHANNEL_PROFILE_COMMUNICATION]). Token from the `getAgoraToken` HTTPS callable with `isPublisher = true` for each user.
     */
    fun joinOneToOneVideo(
        channelId: String,
        uid: Int,
        token: String?,
    ): Int {
        val rtc = ensureEngine() ?: return -1
        rtc.enableVideo()
        rtc.enableAudio()
        val options =
            ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                publishMicrophoneTrack = true
                publishCameraTrack = true
                autoSubscribeAudio = true
                autoSubscribeVideo = true
                startPreview = true
            }
        val tok = token?.trim().orEmpty()
        val code =
            if (tok.isEmpty()) {
                rtc.joinChannel(null, channelId, uid, options)
            } else {
                rtc.joinChannel(tok, channelId, uid, options)
            }
        if (code != 0) {
            Log.e(TAG, "joinOneToOneVideo failed code=$code channel=$channelId")
        }
        return code
    }

    fun muteLocalAudioStream(mute: Boolean) {
        runCatching { engine?.muteLocalAudioStream(mute) }
    }

    fun muteAllRemoteAudioStreams(mute: Boolean) {
        runCatching { engine?.muteAllRemoteAudioStreams(mute) }
    }

    fun muteRemoteAudioStream(remoteUid: Int, mute: Boolean) {
        runCatching { engine?.muteRemoteAudioStream(remoteUid, mute) }
    }

    fun switchCamera(): Int {
        val rtc = engine ?: return -1
        return try {
            rtc.switchCamera()
        } catch (e: Exception) {
            Log.e(TAG, "switchCamera", e)
            -1
        }
    }

    /**
     * Audio-only live party host / seated guest (Itzo-style room): no camera track, mic published,
     * hears all broadcasters on the same channel.
     */
    fun joinAsAudioPartyBroadcaster(
        channelId: String,
        uid: Int,
        token: String?,
    ): Int {
        val rtc = ensureEngine() ?: return -1
        rtc.disableVideo()
        rtc.enableAudio()
        val options =
            ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                publishMicrophoneTrack = true
                publishCameraTrack = false
                autoSubscribeAudio = true
                autoSubscribeVideo = false
                startPreview = false
            }
        val tok = token?.trim().orEmpty()
        val code =
            if (tok.isEmpty()) {
                rtc.joinChannel(null, channelId, uid, options)
            } else {
                rtc.joinChannel(tok, channelId, uid, options)
            }
        if (code != 0) {
            Log.e(TAG, "joinAsAudioPartyBroadcaster failed code=$code channel=$channelId")
        }
        return code
    }

    /** Audio party listener: hear host + seated publishers, no local publish. */
    fun joinAsAudioPartyAudience(
        channelId: String,
        uid: Int,
        token: String?,
    ): Int {
        val rtc = ensureEngine() ?: return -1
        rtc.disableVideo()
        rtc.enableAudio()
        val options =
            ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
                clientRoleType = Constants.CLIENT_ROLE_AUDIENCE
                publishMicrophoneTrack = false
                publishCameraTrack = false
                autoSubscribeAudio = true
                autoSubscribeVideo = false
                startPreview = false
            }
        val tok = token?.trim().orEmpty()
        val code =
            if (tok.isEmpty()) {
                rtc.joinChannel(null, channelId, uid, options)
            } else {
                rtc.joinChannel(tok, channelId, uid, options)
            }
        if (code != 0) {
            Log.e(TAG, "joinAsAudioPartyAudience failed code=$code channel=$channelId")
        }
        return code
    }

    /**
     * Switch between audience and seated mic without leaving channel. Requires a token minted for
     * the target role when your backend enforces role-specific tokens.
     */
    fun updateAudioPartyPublishingMic(publishMic: Boolean): Int {
        val rtc = engine ?: return -1
        val options =
            ChannelMediaOptions().apply {
                clientRoleType =
                    if (publishMic) {
                        Constants.CLIENT_ROLE_BROADCASTER
                    } else {
                        Constants.CLIENT_ROLE_AUDIENCE
                    }
                publishMicrophoneTrack = publishMic
                publishCameraTrack = false
                autoSubscribeAudio = true
                autoSubscribeVideo = false
            }
        val code = rtc.updateChannelMediaOptions(options)
        if (code != 0) {
            Log.e(TAG, "updateAudioPartyPublishingMic publish=$publishMic code=$code")
        }
        return code
    }

    /**
     * Background music muxed into the live stream ([loopback] false = sent to remote listeners).
     * [cycle] 1 = once, negative = loop until stopped.
     */
    fun startAudioMixingFromFile(
        filePath: String,
        loopback: Boolean = false,
        cycle: Int = 1,
    ): Int {
        val rtc = engine ?: return -1
        return try {
            rtc.startAudioMixing(filePath, loopback, cycle, 0)
        } catch (e: Exception) {
            Log.e(TAG, "startAudioMixingFromFile", e)
            -1
        }
    }

    fun stopAudioMixing() {
        runCatching { engine?.stopAudioMixing() }
    }

    fun pauseAudioMixing() {
        runCatching { engine?.pauseAudioMixing() }
    }

    fun resumeAudioMixing() {
        runCatching { engine?.resumeAudioMixing() }
    }

    fun joinAsAudience(
        channelId: String,
        uid: Int,
        token: String?,
    ): Int {
        val rtc = ensureEngine() ?: return -1
        rtc.enableVideo()
        rtc.enableAudio()
        val options =
            ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
                clientRoleType = Constants.CLIENT_ROLE_AUDIENCE
                publishMicrophoneTrack = false
                publishCameraTrack = false
                autoSubscribeAudio = true
                autoSubscribeVideo = true
                startPreview = false
            }
        val tok = token?.trim().orEmpty()
        val code =
            if (tok.isEmpty()) {
                rtc.joinChannel(null, channelId, uid, options)
            } else {
                rtc.joinChannel(tok, channelId, uid, options)
            }
        if (code != 0) {
            Log.e(TAG, "joinAsAudience failed code=$code channel=$channelId")
        }
        return code
    }

    fun startPreviewIfNeeded() {
        engine?.startPreview()
    }

    fun leaveChannel() {
        try {
            engine?.leaveChannel()
        } catch (_: Exception) {
        }
        _remoteUid.value = null
        _remoteUids.value = emptySet()
        _joinChannelSuccess.value = false
    }

    fun destroy() {
        destroyed = true
        // Release camera preview before leaving channel so the next Activity (1:1 call) can open capture cleanly.
        try {
            engine?.stopPreview()
        } catch (_: Exception) {
        }
        leaveChannel()
        try {
            RtcEngine.destroy()
        } catch (_: Exception) {
        }
        engine = null
    }

    companion object {
        private const val TAG = "AgoraManager"

        /** Same uid derivation as joinChannel — must match Cloud Function `getAgoraToken` validation. */
        fun fromFirebaseUid(uid: String): Int {
            if (uid.isEmpty()) return 1
            var h = uid.hashCode()
            if (h == 0) h = 1
            return h and Int.MAX_VALUE
        }
    }
}
