package com.zipper.datingapp.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Lazy, thread-safe WebRTC native / JVM bootstrap. Avoids loading
 * `jingle_peerconnection_so` and initializing [PeerConnectionFactory] at application startup.
 */
object WebRTCInitializer {

    private const val TAG = "ZPR_DEBUG"

    private val lock = Any()

    @Volatile
    private var initialized: Boolean = false

    /**
     * Loads the WebRTC JNI library, runs [PeerConnectionFactory.initialize], and verifies that a
     * [PeerConnectionFactory] can be built (then disposed). Safe to call from any thread; heavy
     * work should be scheduled on a UI coroutine by callers when appropriate.
     *
     * @return false if native load, initialize, or factory probe fails (see [TAG] in logcat).
     */
    fun ensureInitialized(context: Context): Boolean {
        if (initialized) return true
        synchronized(lock) {
            if (initialized) return true
            val app = context.applicationContext
            try {
                System.loadLibrary("jingle_peerconnection_so")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "jingle_peerconnection_so load failed (UnsatisfiedLinkError)", e)
                return false
            } catch (e: Throwable) {
                Log.e(TAG, "jingle_peerconnection_so load failed", e)
                return false
            }
            try {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(app)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions(),
                )
            } catch (e: Exception) {
                Log.e(TAG, "PeerConnectionFactory.initialize failed", e)
                return false
            }
            var egl: EglBase? = null
            var adm: AudioDeviceModule? = null
            try {
                egl = EglBase.create()
                val eglCtx = egl!!.eglBaseContext
                val encoderFactory = DefaultVideoEncoderFactory(eglCtx, true, true)
                val decoderFactory = DefaultVideoDecoderFactory(eglCtx)
                adm = JavaAudioDeviceModule.builder(app)
                    .setUseHardwareAcousticEchoCanceler(true)
                    .setUseHardwareNoiseSuppressor(true)
                    .createAudioDeviceModule()
                val probe = PeerConnectionFactory.builder()
                    .setAudioDeviceModule(adm)
                    .setVideoEncoderFactory(encoderFactory)
                    .setVideoDecoderFactory(decoderFactory)
                    .createPeerConnectionFactory()
                probe.dispose()
            } catch (e: Exception) {
                Log.e(TAG, "PeerConnectionFactory.builder().createPeerConnectionFactory() probe failed", e)
                return false
            } finally {
                runCatching { (adm as? JavaAudioDeviceModule)?.release() }
                runCatching { egl?.release() }
            }
            initialized = true
            return true
        }
    }
}
