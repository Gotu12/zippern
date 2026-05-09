@file:OptIn(com.snap.camerakit.Experimental::class)

package com.zipper.datingapp.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES30
import android.opengl.GLES20
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.view.Choreographer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewStub
import android.widget.FrameLayout
import androidx.annotation.CheckResult
import com.snap.camerakit.ImageProcessor
import com.snap.camerakit.Session
import com.snap.camerakit.Source
import com.snap.camerakit.common.Consumer
import com.snap.camerakit.configureLenses
import com.snap.camerakit.invoke
import com.snap.camerakit.lenses.LENS_GROUP_ID_BUNDLED
import com.snap.camerakit.lenses.LensesComponent
import com.snap.camerakit.lenses.whenHasFirst
import com.snap.camerakit.inputFrom
import com.snap.camerakit.outputFrom
import com.zipper.datingapp.BuildConfig
import com.zipper.datingapp.DatingApp
import com.zipper.datingapp.R
import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns a [Session] and connects Camera Kit's lens input/output manually: camera [SurfaceTexture] via
 * [ImageProcessor.connectInput] ([inputFrom]) and WebRTC [SurfaceTexture] via [connectOutput].
 * Camera frames are provided by [CameraKitVideoCapturer] (CameraX [Preview] → input texture), not
 * [com.snap.camerakit.support.camerax.CameraXImageProcessorSource].
 *
 * **Input crop:** [ImageProcessor.Input.Option.Crop.Center] at 9:16 so Snap center-crops before lenses.
 * Public SDK 1.48 has no output-side `Scaling.CENTER_CROP` on [connectOutput].
 *
 * **Output buffer:** [SurfaceTexture.setDefaultBufferSize] must match the same [width]×[height] as
 * [CameraVideoCapturer.startCapture] / [org.webrtc.SurfaceTextureHelper.setTextureSize] (Camera Kit path:
 * portrait **720×1280**; do not swap dimensions vs WebRTC).
 *
 * **Audio:** [Session] uses [Source.Noop] for `audioProcessorSource` so Camera Kit does not attach the
 * default microphone pipeline; WebRTC owns capture via `JavaAudioDeviceModule`.
 */
class CameraKitWebRtcBridge(
    private val appContext: Context,
    private val roomIdLog: String,
) {
    private val tag = "CameraKitWebRtc"
    private val main = Handler(Looper.getMainLooper())

    /** Last-reached tracing before native crashes; one line per bridge entry point. */
    private fun traceBridge(here: String) {
        Log.d(tag, ">> $here room=$roomIdLog thread=${Thread.currentThread().name}")
    }

    /**
     * Invalidates in-flight [ensureSessionRunningStaged] work when incremented (stopCapture, release,
     * or a new staged session is scheduled). Checked from delayed runnables and Choreographer callbacks.
     */
    private val sessionStagingGeneration = AtomicLong(0L)
    private var pendingStagedSessionDelayRunnable: Runnable? = null
    private var pendingStagedWarmupFrameCallback: Choreographer.FrameCallback? = null

    private var session: Session? = null
    private var inputDisposable: Closeable? = null
    private var outputDisposable: Closeable? = null
    private var lastAppliedLensId: String? = null
    /** Active [LensesComponent.Prefetcher.observe] or [LensesComponent.Prefetcher.run] sub-op; always cancel before starting a new apply. */
    private var prefetchCloseable: Closeable? = null
    /** [Runnable] passed to [Handler.postDelayed] so we can [Handler.removeCallbacks] on lens change. */
    private var pendingApplyAfterLoaded: Runnable? = null
    /** Beauty: 50ms hold after [LensesComponent.Lens.LaunchData.Empty] apply (SDK has no [Lens] placeholder). */
    private var pendingBeautyMeshReset: Runnable? = null
    /**
     * The next lens [apply] is the first in this session; use a longer [Handler.postDelayed] settle.
     * Reset in [clearLens] / [release].
     */
    private var isFirstLens: Boolean = true
    /**
     * True while [LensesComponent.LensesProcessor.clear] is in progress for a lens switch;
     * defers the next apply until the clear callback runs.
     */
    private var isLensClearInFlight: Boolean = false
    private val firstSessionDelayMs: Long = 200L
    private val defaultFov: Float = 64f
    /**
     * Set by [CameraKitVideoCapturer] to rebind CameraX [Preview] when [setFrontCamera] is used.
     */
    var rebindCameraFacing: ((facingFront: Boolean) -> Unit)? = null

    /**
     * [org.webrtc.SurfaceTextureHelper] handler (capture / shared EGL). Used to run [GLES20.glFinish]
     * on the WebRTC GL thread before tearing down lens output [android.graphics.SurfaceTexture].
     */
    @Volatile
    var glFinishHandler: Handler? = null

    /**
     * Call from main thread only. Creates [Session] if needed. Prefer [ensureSessionRunningStaged] on the
     * video capture path to split camera open across two main-looper messages.
     */
    fun ensureSessionRunningSync() {
        traceBridge("ensureSessionRunningSync")
        assertMainThread()
        if (session != null) return
        buildSessionAndStubIfNeeded()
    }

    private fun removeStagedSessionCallbacks() {
        traceBridge("removeStagedSessionCallbacks")
        pendingStagedSessionDelayRunnable?.let { main.removeCallbacks(it) }
        pendingStagedSessionDelayRunnable = null
        pendingStagedWarmupFrameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        pendingStagedWarmupFrameCallback = null
    }

    /**
     * Cancels delayed / frame callbacks from [ensureSessionRunningStaged] so Snap GL work does not
     * run after [org.webrtc.SurfaceTextureHelper.stopListening] or bridge [release]. Safe from any thread.
     */
    fun cancelPendingSessionStaging() {
        traceBridge("cancelPendingSessionStaging")
        if (Looper.myLooper() == Looper.getMainLooper()) {
            invalidateSessionStaging("capturer_stop_or_surface_teardown")
        } else {
            main.post { invalidateSessionStaging("capturer_stop_or_surface_teardown") }
        }
    }

    private fun invalidateSessionStaging(reason: String) {
        traceBridge("invalidateSessionStaging reason=$reason")
        sessionStagingGeneration.incrementAndGet()
        removeStagedSessionCallbacks()
        Log.d(tag, "session staging invalidated reason=$reason room=$roomIdLog")
    }

    /**
     * Defers the first [Session] build by [firstSessionDelayMs] (separates heavy init from same-frame
     * WebRTC work), then runs a one-frame warm-up [processor.clear] + [Choreographer] before [onSessionReady].
     * Camera input is connected separately via [connectProcessorInputFromCameraSurfaceTexture].
     * Must be called on the main thread; [onSessionReady] always runs on the main thread.
     */
    fun ensureSessionRunningStaged(onSessionReady: () -> Unit) {
        traceBridge("ensureSessionRunningStaged")
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { ensureSessionRunningStaged(onSessionReady) }
            return
        }
        removeStagedSessionCallbacks()
        val gen = sessionStagingGeneration.incrementAndGet()
        if (session != null) {
            onSessionReady()
            return
        }
        val delayRunnable = Runnable {
            traceBridge("ensureSessionRunningStaged.delayRunnable")
            if (sessionStagingGeneration.get() != gen) return@Runnable
            if (session != null) {
                onSessionReady()
                return@Runnable
            }
            if (!buildSessionAndStubIfNeeded()) {
                if (sessionStagingGeneration.get() == gen) onSessionReady()
                return@Runnable
            }
            val s = session
            if (s == null) {
                if (sessionStagingGeneration.get() == gen) onSessionReady()
                return@Runnable
            }
            fun postWarmupFrameThenReady() {
                traceBridge("ensureSessionRunningStaged.postWarmupFrameThenReady")
                if (sessionStagingGeneration.get() != gen) return
                val frameCb = Choreographer.FrameCallback {
                    traceBridge("ensureSessionRunningStaged.choreographerFrame")
                    pendingStagedWarmupFrameCallback = null
                    if (sessionStagingGeneration.get() != gen) return@FrameCallback
                    onSessionReady()
                }
                pendingStagedWarmupFrameCallback = frameCb
                Choreographer.getInstance().postFrameCallback(frameCb)
            }
            try {
                s.lenses.processor.clear { _ ->
                    if (sessionStagingGeneration.get() != gen) return@clear
                    postWarmupFrameThenReady()
                }
            } catch (e: IllegalStateException) {
                if (glConsumerAlreadyAttachedMessage(e)) {
                    Log.w(
                        tag,
                        "session warm-up clear skipped (GLConsumer / attach collision) room=$roomIdLog",
                        e,
                    )
                    if (sessionStagingGeneration.get() == gen) postWarmupFrameThenReady()
                } else {
                    Log.w(tag, "session warm-up clear failed room=$roomIdLog", e)
                    if (sessionStagingGeneration.get() == gen) postWarmupFrameThenReady()
                }
            } catch (t: Throwable) {
                Log.w(tag, "session warm-up clear failed room=$roomIdLog", t)
                if (sessionStagingGeneration.get() == gen) postWarmupFrameThenReady()
            }
        }
        pendingStagedSessionDelayRunnable = delayRunnable
        main.postDelayed(delayRunnable, firstSessionDelayMs)
    }

    /**
     * Prepare a [SurfaceTexture] before Snap [ImageProcessor] attaches it to its GL consumer.
     * Call from the thread that currently owns the texture's GL attachment when known (main for CameraX
     * input; [SurfaceTextureHelper] handler for WebRTC output — see [prepareLensOutputSurfaceTextureOnCaptureThread]).
     *
     * @return false if the texture is released or should not be connected this cycle (caller may retry).
     */
    fun prepareSurfaceTextureForSnapConnect(surfaceTexture: SurfaceTexture): Boolean {
        traceBridge("prepareSurfaceTextureForSnapConnect")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && surfaceTexture.isReleased) {
            Log.w(tag, "SurfaceTexture already released; skip Snap connect room=$roomIdLog")
            return false
        }
        return try {
            surfaceTexture.detachFromGLContext()
            true
        } catch (e: IllegalStateException) {
            if (glConsumerAlreadyAttachedMessage(e)) {
                Log.w(
                    tag,
                    "detachFromGLContext: GLConsumer already attached — skip this connect cycle room=$roomIdLog",
                    e,
                )
                false
            } else {
                // e.g. not attached to a GL context yet — Snap can attach.
                Log.d(tag, "detachFromGLContext: no prior GL attach (ok) room=$roomIdLog")
                true
            }
        }
    }

    /**
     * Must run on the [org.webrtc.SurfaceTextureHelper] handler thread so [detachFromGLContext] matches
     * WebRTC's EGL context that owns the OES texture, before [connectLensOutputToWebRtcSync] on main.
     */
    fun prepareLensOutputSurfaceTextureOnCaptureThread(surfaceTexture: SurfaceTexture): Boolean {
        traceBridge("prepareLensOutputSurfaceTextureOnCaptureThread")
        return try {
            prepareSurfaceTextureForSnapConnect(surfaceTexture)
        } catch (e: RuntimeException) {
            // Covers updateTexImage / EGL consumer races when the SurfaceTexture is torn down (e.g. Mali + Compose).
            Log.w(
                "CameraKitBridge",
                "prepareLensOutputSurfaceTextureOnCaptureThread: dropped (surface/EGL). room=$roomIdLog",
                e,
            )
            false
        }
    }

    private fun buildSessionAndStubIfNeeded(): Boolean {
        traceBridge("buildSessionAndStubIfNeeded")
        if (session != null) return true
        return try {
            val ctx = appContext.applicationContext
            val stubHost = FrameLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            }
            val layout: View = LayoutInflater.from(ctx).inflate(R.layout.camera_kit_stub, stubHost, true)
            val stub = layout.findViewById<ViewStub>(R.id.camera_kit_stub)
            session = Session(context = ctx) {
                // Prevent Camera Kit from attaching the default microphone-backed audio pipeline.
                audioProcessorSource(Source.Noop.get())
                attachTo(stub)
                configureLenses { lensesBuilder ->
                    lensesBuilder.configureCache { cacheConfig ->
                        cacheConfig.lensContentMaxSize = DatingApp.SNAP_LENS_DISK_CACHE_MAX_BYTES
                    }
                }
            }
            session?.let { s ->
                runCatching {
                    s.lenses.audio.adjust(
                        LensesComponent.Audio.Adjustment.Volume.Mute,
                        Consumer { },
                    )
                }.onFailure { t ->
                    Log.w(tag, "lenses audio mute failed room=$roomIdLog", t)
                }
            }
            Log.d(
                tag,
                "Camera Kit Session built (manual input; camera in [CameraKitVideoCapturer]) cacheMaxBytes=${DatingApp.SNAP_LENS_DISK_CACHE_MAX_BYTES} room=$roomIdLog",
            )
            true
        } catch (t: Throwable) {
            Log.e(tag, "Failed to build Camera Kit Session room=$roomIdLog", t)
            false
        }
    }

    private fun snapInputOptions(): Set<ImageProcessor.Input.Option> =
        setOf(ImageProcessor.Input.Option.Crop.Center(9, 16))

    /**
     * Connects camera frames (CameraX → [surfaceTexture]) into the lens pipeline. Call **before**
     * [connectLensOutputToWebRtcSync]. Main thread only.
     */
    fun connectProcessorInputFromCameraSurfaceTexture(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        facingFront: Boolean,
    ) {
        traceBridge("connectProcessorInputFromCameraSurfaceTexture")
        assertMainThread()
        val s = session ?: run {
            Log.e(tag, "connectProcessorInput: session null room=$roomIdLog")
            return
        }
        runCatching { inputDisposable?.close() }
        inputDisposable = null
        if (!prepareSurfaceTextureForSnapConnect(surfaceTexture)) {
            Log.w(tag, "connectInput skipped: SurfaceTexture not ready for Snap room=$roomIdLog")
            return
        }
        surfaceTexture.setDefaultBufferSize(width, height)
        try {
            val input = inputFrom(
                surfaceTexture,
                width,
                height,
                rotationDegrees,
                facingFront,
                defaultFov,
                defaultFov,
            )
            inputDisposable = s.processor.connectInput(input, snapInputOptions())
            Log.d(tag, "connectInput camera ST ${width}x${height} rot=$rotationDegrees front=$facingFront room=$roomIdLog")
        } catch (e: IllegalStateException) {
            Log.w(
                tag,
                "connectInput: IllegalStateException (EGL/SurfaceTexture); skipping room=$roomIdLog",
                e,
            )
        } catch (t: Throwable) {
            Log.e(tag, "connectInput failed room=$roomIdLog", t)
        }
    }

    /**
     * Pipes post-lens frames into the [SurfaceTexture] consumed by WebRTC [org.webrtc.SurfaceTextureHelper].
     * Main thread only.
     */
    fun connectLensOutputToWebRtcSync(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        traceBridge("connectLensOutputToWebRtcSync")
        assertMainThread()
        val s = session ?: run {
            Log.e(tag, "connectLensOutput: session null room=$roomIdLog")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && surfaceTexture.isReleased) {
            Log.w(tag, "connectOutput skipped: SurfaceTexture released room=$roomIdLog")
            return
        }
        runCatching { outputDisposable?.close() }
        outputDisposable = null
        try {
            // Same tuple as [SurfaceTextureHelper.setTextureSize] (e.g. portrait 720×1280). No public output Size/CENTER_CROP in SDK 1.48.
            surfaceTexture.setDefaultBufferSize(width, height)
            val surface = android.view.Surface(surfaceTexture)
            outputDisposable = s.processor.connectOutput(
                outputFrom(surface, ImageProcessor.Output.Purpose.RECORDING),
                setOf(ImageProcessor.Output.Option.IgnoreDeviceRotation),
            )
            Log.d(tag, "connectOutput RECORDING buffer=${width}x${height} ignoreDeviceRotation room=$roomIdLog")
        } catch (e: IllegalStateException) {
            Log.w(
                tag,
                "connectLensOutput: IllegalStateException (EGL/SurfaceTexture); skipping room=$roomIdLog",
                e,
            )
        } catch (t: Throwable) {
            Log.e(tag, "connectOutput failed room=$roomIdLog", t)
        }
    }

    fun disconnectLensOutputSync() {
        traceBridge("disconnectLensOutputSync")
        assertMainThread()
        runCatching { outputDisposable?.close() }
        outputDisposable = null
    }

    private fun assertMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            error("CameraKitWebRtcBridge session/output must run on main thread")
        }
    }

    private fun glConsumerAlreadyAttachedMessage(e: Throwable): Boolean {
        val m = e.message ?: return false
        return m.contains("GLConsumer", ignoreCase = true) ||
            m.contains("already attached", ignoreCase = true) ||
            m.contains("already connected", ignoreCase = true)
    }

    /** Removes any applied lens (pass-through camera). */
    fun clearLens() {
        traceBridge("clearLens")
        main.post {
            traceBridge("clearLens.mainPost")
            cancelLensPrefetchOperations()
            isFirstLens = true
            runCatching { session?.lenses?.processor?.clear { } }
            lastAppliedLensId = null
        }
    }

    private fun cancelLensPrefetchOperations() {
        traceBridge("cancelLensPrefetchOperations")
        pendingApplyAfterLoaded?.let { main.removeCallbacks(it) }
        pendingApplyAfterLoaded = null
        pendingBeautyMeshReset?.let { main.removeCallbacks(it) }
        pendingBeautyMeshReset = null
        runCatching { prefetchCloseable?.close() }
        prefetchCloseable = null
    }

    private fun runAfterLensClearIfNeeded(
        processor: LensesComponent.Processor,
        needsClear: Boolean,
        onReady: () -> Unit,
    ) {
        traceBridge("runAfterLensClearIfNeeded needsClear=$needsClear")
        if (!needsClear) {
            isLensClearInFlight = false
            onReady()
            return
        }
        isLensClearInFlight = true
        runCatching {
            processor.clear {
                isLensClearInFlight = false
                main.post { onReady() }
            }
        }.onFailure { e ->
            isLensClearInFlight = false
            Log.w(tag, "processor.clear failed room=$roomIdLog", e)
            main.post { onReady() }
        }
    }

    /**
     * Prefetcher callbacks are posted to the main [Handler], but we defer work to the next
     * [android.os.MessageQueue] idle point so the main looper is not mid-pass when scheduling apply.
     */
    private fun runOnNextMainQueueIdle(block: () -> Unit) {
        traceBridge("runOnNextMainQueueIdle")
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { runOnNextMainQueueIdle(block) }
            return
        }
        main.looper.queue.addIdleHandler(
            object : android.os.MessageQueue.IdleHandler {
                override fun queueIdle(): Boolean {
                    block()
                    return false
                }
            },
        )
    }

    /**
     * Heuristic "beauty" filter detection for prefetch prioritization. Camera Kit 1.48 has no
     * `Prefetcher.Rotation.IMMEDIATE` API; for beauty we call [LensesComponent.Prefetcher.run]
     * up front instead of [LensesComponent.Prefetcher.observe] (strongest download priority available).
     */
    private fun isLikelyBeautyLens(lens: LensesComponent.Lens): Boolean {
        traceBridge("isLikelyBeautyLens id=${lens.id}")
        if (lens.name?.contains("beauty", ignoreCase = true) == true) return true
        return lens.vendorData.values.any { v ->
            v?.contains("beauty", ignoreCase = true) == true ||
                v?.contains("makeup", ignoreCase = true) == true ||
                v?.contains("skin", ignoreCase = true) == true
        }
    }

    /**
     * Applies a lens when [LensesComponent.Prefetcher] reports [LensesComponent.Prefetcher.Status.LOADED], or after
     * a successful [LensesComponent.Prefetcher.run]. Clears GPU/lens state when switching to a different [LensesComponent.Lens] id.
     * All [prefetcher] callbacks hop to the main looper for apply and UI.
     */
    private fun applyLensWhenContentReady(
        lens: LensesComponent.Lens,
        onLoading: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        traceBridge("applyLensWhenContentReady id=${lens.id}")
        val s = session ?: return
        val lenses = s.lenses
        val needsClear = lastAppliedLensId != null && lastAppliedLensId != lens.id
        cancelLensPrefetchOperations()
        runAfterLensClearIfNeeded(lenses.processor, needsClear) {
            applyLensWhenContentReadyAfterClear(lens, onLoading, onError)
        }
    }

    private fun applyLensWhenContentReadyAfterClear(
        lens: LensesComponent.Lens,
        onLoading: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        traceBridge("applyLensWhenContentReadyAfterClear id=${lens.id}")
        val s = session ?: return
        val lenses = s.lenses
        var applied = false
        var loadingNotified = false

        fun runApplyOnMainAfterGpuSettle() {
            traceBridge("runApplyOnMainAfterGpuSettle id=${lens.id}")
            if (applied) return
            val delayMs = if (isFirstLens) 500L else 100L
            val r = Runnable {
                pendingApplyAfterLoaded = null
                if (applied) return@Runnable
                runCatching { prefetchCloseable?.close() }
                prefetchCloseable = null
                val queue = main.looper.queue
                fun applyResolvedLens() {
                    if (applied) return
                    runCatching {
                        lenses.processor.apply(lens) { success ->
                            main.post {
                                if (!success) {
                                    onError("apply")
                                    return@post
                                }
                                applied = true
                                lastAppliedLensId = lens.id
                                isFirstLens = false
                            }
                        }
                    }.onFailure { e ->
                        Log.w(tag, "processor.apply failed id=${lens.id} room=$roomIdLog", e)
                        onError(e.message ?: "apply")
                    }
                }
                fun invokeApplyAfterIdle() {
                    if (applied) return
                    if (isLikelyBeautyLens(lens)) {
                        // No Lens.EMPTY in 1.48; [LaunchData.Empty] pre-apply + short delay re-seeds the pipeline.
                        runCatching {
                            lenses.processor.apply(
                                lens,
                                LensesComponent.Lens.LaunchData.Empty,
                            ) { _ ->
                                val next = Runnable {
                                    pendingBeautyMeshReset = null
                                    if (applied) return@Runnable
                                    applyResolvedLens()
                                }
                                pendingBeautyMeshReset = next
                                main.postDelayed(next, 50L)
                            }
                        }.onFailure { e ->
                            Log.w(tag, "beauty LaunchData.Empty apply failed id=${lens.id} room=$roomIdLog", e)
                            onError(e.message ?: "pre-apply")
                        }
                    } else {
                        applyResolvedLens()
                    }
                }
                val idle = object : android.os.MessageQueue.IdleHandler {
                    override fun queueIdle(): Boolean {
                        if (applied) return false
                        runCatching { GLES30.glFlush() }
                        invokeApplyAfterIdle()
                        return false
                    }
                }
                queue.addIdleHandler(idle)
            }
            pendingApplyAfterLoaded = r
            main.postDelayed(r, delayMs)
        }

        // SDK 1.48: no Prefetcher.Rotation.IMMEDIATE; prefetch with run() first is the strongest priority available.
        if (isLikelyBeautyLens(lens)) {
            if (!loadingNotified) {
                loadingNotified = true
                onLoading()
            }
            prefetchCloseable = lenses.prefetcher.run(listOf(lens)) { success ->
                main.post {
                    if (applied) return@post
                    prefetchCloseable = null
                    runOnNextMainQueueIdle {
                        if (applied) return@runOnNextMainQueueIdle
                        if (success) {
                            runApplyOnMainAfterGpuSettle()
                        } else {
                            Log.w(tag, "beauty lens prefetch run failed id=${lens.id} room=$roomIdLog")
                            onError("prefetch")
                        }
                    }
                }
            }
            return
        }

        prefetchCloseable = lenses.prefetcher.observe(lens) { status ->
            main.post {
                if (applied) return@post
                if (status == LensesComponent.Prefetcher.Status.LOADED) {
                    runCatching { prefetchCloseable?.close() }
                    prefetchCloseable = null
                    runOnNextMainQueueIdle {
                        if (!applied) runApplyOnMainAfterGpuSettle()
                    }
                    return@post
                }
                if (!loadingNotified) {
                    loadingNotified = true
                    onLoading()
                }
                runCatching { prefetchCloseable?.close() }
                prefetchCloseable = null
                prefetchCloseable = lenses.prefetcher.run(listOf(lens)) { success ->
                    main.post {
                        prefetchCloseable = null
                        runOnNextMainQueueIdle {
                            if (applied) return@runOnNextMainQueueIdle
                            if (success) {
                                runApplyOnMainAfterGpuSettle()
                            } else {
                                Log.w(tag, "lens prefetch run failed id=${lens.id} room=$roomIdLog")
                                onError("prefetch")
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Applies the first lens returned for the query. Uses bundled demo lenses when no portal group is set.
     */
    fun applyLensByGroupAndId(
        groupId: String,
        lensId: String,
        onLoading: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        traceBridge("applyLensByGroupAndId group=$groupId lens=$lensId")
        if (groupId.isBlank() || lensId.isBlank()) {
            Log.w(tag, "applyLensByGroupAndId: empty ids room=$roomIdLog")
            return
        }
        main.post {
            traceBridge("applyLensByGroupAndId.mainPost")
            val s = session ?: return@post
            s.lenses.repository.observe(
                LensesComponent.Repository.QueryCriteria.ById(lensId, groupId),
            ) { result ->
                result.whenHasFirst { lens ->
                    main.post {
                        applyLensWhenContentReady(lens, onLoading, onError)
                    }
                }
            }
        }
    }

    fun applyDefaultBundledLens(
        onLoading: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        traceBridge("applyDefaultBundledLens")
        main.post {
            traceBridge("applyDefaultBundledLens.mainPost")
            val s = session ?: return@post
            s.lenses.repository.observe(
                LensesComponent.Repository.QueryCriteria.Available(setOf(LENS_GROUP_ID_BUNDLED)),
            ) { result ->
                result.whenHasFirst { lens ->
                    main.post {
                        applyLensWhenContentReady(lens, onLoading, onError)
                    }
                }
            }
        }
    }

    fun applyConfiguredGroupFirstLens(
        onLoading: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        traceBridge("applyConfiguredGroupFirstLens")
        val gid = BuildConfig.SNAP_LENS_GROUP_ID.trim()
        if (gid.isEmpty()) {
            applyDefaultBundledLens(onLoading, onError)
            return
        }
        main.post {
            traceBridge("applyConfiguredGroupFirstLens.mainPost")
            val s = session ?: return@post
            val criteria = LensesComponent.Repository.QueryCriteria.Available(setOf(gid))
            s.lenses.repository.observe(criteria) { result ->
                when (result) {
                    is LensesComponent.Repository.Result.Some -> {
                        val lens = result.lenses.firstOrNull()
                        if (lens != null) {
                            main.post {
                                applyLensWhenContentReady(lens, onLoading, onError)
                            }
                        } else {
                            Log.e("SnapDebug", "Some result but empty lenses for group: $gid")
                        }
                    }
                    is LensesComponent.Repository.Result.None -> {
                        Log.e("SnapDebug", "No lenses found for group: $gid")
                    }
                }
            }
        }
    }

    /**
     * Observes every lens in the portal group (same semantics as
     * [LensesComponent.Repository.QueryCriteria.Available] + [Result.Some]).
     * Call on the main thread only. Ensures a [Session] exists so the repository can load.
     */
    @CheckResult
    fun observeAvailableLenses(
        groupId: String,
        onUpdate: (List<LensesComponent.Lens>) -> Unit,
    ): Closeable {
        traceBridge("observeAvailableLenses group=$groupId")
        assertMainThread()
        val gid = groupId.trim()
        if (gid.isEmpty()) return Closeable { }
        if (session == null) {
            ensureSessionRunningSync()
        }
        val s = session ?: run {
            Log.w(tag, "observeAvailableLenses: session still null room=$roomIdLog")
            return Closeable { }
        }
        // Camera Kit 1.48: Available requires a Set<String>, not a single String.
        val criteria = LensesComponent.Repository.QueryCriteria.Available(setOf(gid))
        return s.lenses.repository.observe(criteria) { result ->
            when (result) {
                is LensesComponent.Repository.Result.Some -> {
                    val allLenses = result.lenses
                    Log.d("SnapDebug", "Success! Found ${allLenses.size} lenses in group")
                    main.post { onUpdate(allLenses) }
                }
                is LensesComponent.Repository.Result.None -> {
                    Log.e("SnapDebug", "No lenses found for group: $gid")
                    main.post { onUpdate(emptyList()) }
                }
            }
        }
    }

    /** Applies a concrete lens model from [observeAvailableLenses]. */
    fun applyLens(
        lens: LensesComponent.Lens,
        onLoading: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        traceBridge("applyLens id=${lens.id}")
        main.post {
            traceBridge("applyLens.mainPost id=${lens.id}")
            session ?: return@post
            applyLensWhenContentReady(lens, onLoading, onError)
        }
    }

    fun setFrontCamera(front: Boolean) {
        traceBridge("setFrontCamera front=$front")
        main.post {
            traceBridge("setFrontCamera.mainPost front=$front")
            rebindCameraFacing?.invoke(front)
        }
    }

    fun release() {
        traceBridge("release")
        val cap = glFinishHandler
        val teardown = Runnable {
            traceBridge("release.mainPost")
            invalidateSessionStaging("release")
            rebindCameraFacing = null
            glFinishHandler = null
            cancelLensPrefetchOperations()
            isFirstLens = true
            lastAppliedLensId = null
            isLensClearInFlight = false
            runCatching { inputDisposable?.close() }
            inputDisposable = null
            runCatching { outputDisposable?.close() }
            outputDisposable = null
            runCatching { session?.close() }
            session = null
        }
        if (cap != null) {
            cap.post {
                traceBridge("release.captureThreadGlFinish")
                runCatching { GLES20.glFinish() }
                    .onFailure { t -> Log.w(tag, "glFinish on WebRTC capture GL thread failed room=$roomIdLog", t) }
                main.post(teardown)
            }
        } else {
            main.post(teardown)
        }
    }
}
