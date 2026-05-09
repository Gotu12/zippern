package com.zipper.datingapp.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import org.webrtc.CameraVideoCapturer
import org.webrtc.CapturerObserver
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

/**
 * Feeds WebRTC from Camera Kit's lens compositor. CameraX [Preview] writes to a dedicated
 * [SurfaceTexture] ([connectProcessorInputFromCameraSurfaceTexture]); [SurfaceTextureHelper]'s
 * texture is only the **lens output** for encoding ([connectLensOutputToWebRtcSync]).
 * Must be started/stopped on the main looper together with [CameraKitWebRtcBridge].
 */
class CameraKitVideoCapturer(
    private val context: Context,
    private val bridge: CameraKitWebRtcBridge,
    private val roomId: String,
    /** When false, lens output frames are dropped (e.g. [WebRTCManager] destroyed or no longer active). */
    private val mayDeliverCapturedFrames: () -> Boolean = { true },
) : CameraVideoCapturer {

    private val tag = "CameraKitCapturer"
    private val main = Handler(Looper.getMainLooper())
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var capturerObserver: CapturerObserver? = null
    @Volatile private var captureStarted = false
    private var useFrontCamera = true
    private var captureWidth = 0
    private var captureHeight = 0
    private var cameraProvider: ProcessCameraProvider? = null
    @Volatile private var cameraInputTexture: SurfaceTexture? = null
    private var cameraInputSurface: Surface? = null
    private val frameDiagCounter = AtomicInteger(0)

    override fun initialize(
        surfaceTextureHelper: SurfaceTextureHelper?,
        applicationContext: Context,
        capturerObserver: CapturerObserver?,
    ) {
        this.surfaceTextureHelper = surfaceTextureHelper
        this.capturerObserver = capturerObserver
    }

    override fun startCapture(width: Int, height: Int, framerate: Int) {
        val h = surfaceTextureHelper ?: return
        val obs = capturerObserver ?: return
        if (captureStarted) return
        captureStarted = true
        captureWidth = width
        captureHeight = height
        main.post {
            try {
                Log.d(
                    tag,
                    "startCapture ${width}x${height} room=$roomId " +
                        "(input ST = CameraX; STH = lens out)",
                )
                h.setTextureSize(width, height)
                bridge.rebindCameraFacing = { front ->
                    useFrontCamera = front
                    if (captureStarted) {
                        main.post { bindCameraXAndConnectBridge(h, obs) }
                    }
                }
                bindCameraXAndConnectBridge(h, obs)
            } catch (t: Throwable) {
                Log.e(tag, "startCapture failed room=$roomId", t)
                captureStarted = false
            }
        }
    }

    private fun ensureCameraInputTexture(w: Int, h: Int): SurfaceTexture {
        var st = cameraInputTexture
        if (st == null) {
            st = SurfaceTexture(0)
            cameraInputTexture = st
        }
        st.setDefaultBufferSize(w, h)
        return st
    }

    /**
     * Binds [Preview] to our [Surface] and, after the session is ready, connects Snap input (camera ST)
     * then WebRTC output (STH) — in that order.
     */
    private fun bindCameraXAndConnectBridge(h: SurfaceTextureHelper, obs: CapturerObserver) {
        val st = ensureCameraInputTexture(captureWidth, captureHeight)
        val ctx = context.applicationContext
        val providerFuture = ProcessCameraProvider.getInstance(ctx)
        providerFuture.addListener(
            {
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    cameraInputSurface?.release()
                    val surface = Surface(st)
                    cameraInputSurface = surface
                    val preview = Preview.Builder()
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        Size(captureWidth, captureHeight),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                    ),
                                )
                                .build(),
                        )
                        .build()
                    preview.setSurfaceProvider { request ->
                        st.setDefaultBufferSize(request.resolution.width, request.resolution.height)
                        runCatching {
                            request.provideSurface(surface, mainExecutor) { }
                        }.onFailure { e ->
                            Log.e(tag, "provideSurface failed room=$roomId", e)
                        }
                    }
                    val selector =
                        if (useFrontCamera) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }
                    val owner: LifecycleOwner = ProcessLifecycleOwner.get()
                    runCatching { provider.unbindAll() }
                    provider.bindToLifecycle(owner, selector, preview)
                    val inW = captureWidth
                    val inH = captureHeight
                    bridge.ensureSessionRunningStaged {
                        if (!captureStarted) {
                            Log.d(tag, "staging ready but capture stopped; skip bridge connect room=$roomId")
                            return@ensureSessionRunningStaged
                        }
                        val sth = surfaceTextureHelper
                        if (sth == null) {
                            Log.w(tag, "SurfaceTextureHelper null after staging; WebRTC not initialized? room=$roomId")
                            return@ensureSessionRunningStaged
                        }
                        runCatching {
                            // Pause frame delivery before rebinding GL consumers so Snap and WebRTC don't race.
                            sth.stopListening()
                            bridge.connectProcessorInputFromCameraSurfaceTexture(
                                st,
                                inW,
                                inH,
                                0,
                                useFrontCamera,
                            )
                            // Detach WebRTC's OES texture on the helper thread (owns EGL), then connect on main.
                            sth.handler.post {
                                if (!captureStarted) return@post
                                val prepared = try {
                                    bridge.prepareLensOutputSurfaceTextureOnCaptureThread(sth.surfaceTexture)
                                } catch (e: RuntimeException) {
                                    Log.w(
                                        tag,
                                        "prepareLensOutput on capture thread failed (surface/EGL); skip connect room=$roomId",
                                        e,
                                    )
                                    false
                                }
                                if (!prepared) {
                                    Log.w(tag, "prepareLensOutput not ready; skip Snap→WebRTC connect room=$roomId")
                                    return@post
                                }
                                main.post {
                                    if (!captureStarted) return@post
                                    try {
                                        bridge.connectLensOutputToWebRtcSync(sth.surfaceTexture, inW, inH)
                                    } catch (e: RuntimeException) {
                                        Log.w(
                                            tag,
                                            "connectLensOutput failed (runtime/surface) room=$roomId",
                                            e,
                                        )
                                        return@post
                                    }
                                    sth.handler.post {
                                        if (!captureStarted) return@post
                                        try {
                                            sth.startListening(
                                                VideoSink { frame: VideoFrame ->
                                                    if (!mayDeliverCapturedFrames()) return@VideoSink
                                                    val nth = frameDiagCounter.incrementAndGet()
                                                    if (nth % 30 == 0) {
                                                        Log.d(
                                                            "CameraKitBridge",
                                                            "Frame successfully captured and sent to WebRTC room=$roomId n=$nth",
                                                        )
                                                    }
                                                    try {
                                                        obs.onFrameCaptured(frame)
                                                    } catch (e: RuntimeException) {
                                                        Log.w(
                                                            "CameraKitBridge",
                                                            "Dropped frame: observer/surface churn during recomposition. room=$roomId",
                                                            e,
                                                        )
                                                    }
                                                },
                                            )
                                        } catch (e: RuntimeException) {
                                            Log.w(
                                                tag,
                                                "startListening failed (runtime) room=$roomId",
                                                e,
                                            )
                                        }
                                    }
                                }
                            }
                        }.onFailure { e ->
                            Log.e(tag, "connect Snap/bridge failed room=$roomId", e)
                            captureStarted = false
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "CameraX bind failed room=$roomId", e)
                    captureStarted = false
                }
            },
            mainExecutor,
        )
    }

    override fun stopCapture() {
        if (!captureStarted) return
        captureStarted = false
        bridge.cancelPendingSessionStaging()
        val h = surfaceTextureHelper
        h?.stopListening()
        main.post {
            bridge.rebindCameraFacing = null
            runCatching { cameraProvider?.unbindAll() }
            cameraInputSurface?.release()
            cameraInputSurface = null
            bridge.disconnectLensOutputSync()
        }
    }

    override fun changeCaptureFormat(width: Int, height: Int, framerate: Int) {
        val h = surfaceTextureHelper ?: return
        val obs = capturerObserver
        captureWidth = width
        captureHeight = height
        h.setTextureSize(width, height)
        if (captureStarted && obs != null) {
            main.post { bindCameraXAndConnectBridge(h, obs) }
        }
    }

    override fun dispose() {
        stopCapture()
        main.post {
            runCatching { cameraInputTexture?.release() }
            cameraInputTexture = null
            runCatching { cameraProvider?.unbindAll() }
            cameraProvider = null
        }
        bridge.release()
        surfaceTextureHelper = null
        capturerObserver = null
    }

    override fun isScreencast(): Boolean = false

    override fun switchCamera(handler: CameraVideoCapturer.CameraSwitchHandler?) {
        applyCameraSwitch(handler, null)
    }

    override fun switchCamera(handler: CameraVideoCapturer.CameraSwitchHandler?, deviceName: String?) {
        applyCameraSwitch(handler, deviceName)
    }

    private fun applyCameraSwitch(
        handler: CameraVideoCapturer.CameraSwitchHandler?,
        deviceName: String?,
    ) {
        useFrontCamera = when {
            deviceName.isNullOrBlank() -> !useFrontCamera
            else -> deviceName.contains("front", ignoreCase = true) ||
                deviceName.contains("1", ignoreCase = true)
        }
        bridge.setFrontCamera(useFrontCamera)
        handler?.onCameraSwitchDone(useFrontCamera)
    }
}
