package com.zipper.datingapp.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import com.zipper.datingapp.webrtc.WebRTCManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import android.widget.Toast
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.zipper.datingapp.BuildConfig
import com.zipper.datingapp.R
import com.zipper.datingapp.call.CallStatus
import com.zipper.datingapp.call.CallStatusChipSpec
import com.zipper.datingapp.data.CallState
import com.zipper.datingapp.data.Gift
import com.zipper.datingapp.data.LuckyGiftsOutcome
import com.zipper.datingapp.data.LiveStreamChatMessage
import com.zipper.datingapp.data.PkBattleSessionState
import com.zipper.datingapp.data.UserProfile
import com.zipper.datingapp.data.CoinPackage
import com.zipper.datingapp.data.coinPackages
import com.zipper.datingapp.ui.components.GiftSelectionSheet
import com.zipper.datingapp.ui.webrtc.PkArenaHud
import com.zipper.datingapp.ui.webrtc.WebRtcOverlayBottomChat
import com.zipper.datingapp.ui.webrtc.WebRtcOverlayTopBar
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.zipper.datingapp.ui.components.FuturisticConfirmCloseDialog
import com.zipper.datingapp.ui.components.GenderAgeBadge
import com.zipper.datingapp.ui.DatingUiState
import com.zipper.datingapp.ui.theme.GoldAccent
import com.zipper.datingapp.ui.theme.ItzoUiTokens
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zipper.datingapp.economy.VirtualEconomyMath
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Top / bottom gradient scrim heights on the in-call video overlay. PiP drag bounds must use the same
 * fractions so the preview cannot slide under the semi-transparent header/footer bands.
 */
private const val VIDEO_CALL_TOP_SCRIM_FRACTION = 0.24f
private const val VIDEO_CALL_BOTTOM_SCRIM_FRACTION = 0.34f

private const val VIDEO_CALL_WEBRTC_PIP_TOP_CLEAR_FRACTION = 0.34f

private const val VIDEO_CALL_WEBRTC_PIP_BOTTOM_CLEAR_FRACTION = 0.44f

/**
 * Three vertical bands on the PiP layout height ([maxHpx]): top forbidden, middle = draggable PiP only,
 * bottom forbidden. Private 1:1 uses equal thirds; live overlay uses a shorter middle band (more bottom).
 */
private const val VIDEO_CALL_PIP_MIDDLE_ZONE_START_PRIVATE = 1f / 3f
private const val VIDEO_CALL_PIP_MIDDLE_ZONE_END_PRIVATE = 2f / 3f
private const val VIDEO_CALL_PIP_MIDDLE_ZONE_START_LIVE = 0.34f
private const val VIDEO_CALL_PIP_MIDDLE_ZONE_END_LIVE = 0.48f

/**
 * Vertical space taken by [WebRtcOverlayBottomChat] (messages up to 120.dp + control row + chat field + full-width end call + padding).
 * Combined with the caller's bottom padding on that composable when computing PiP max Y.
 */
/** Chat strip + control row + stacked composer/end call; keeps PiP drag clamp above real panel height. */
private val WebRtcLiveOverlayBottomPanelReserveDp = 310.dp

/**
 * PiP is a direct child of the screen [Box] so it stacks above in-call chrome (e.g. timer zIndex 60f,
 * live overlay zIndex 45f). Stay below the gift sheet (4000f) and permission gate (see below) .
 */
private const val VIDEO_CALL_PIP_ROOT_Z_INDEX = 75f

private const val VIDEO_CALL_PERMISSION_OVERLAY_Z_INDEX = 85f

private const val VIDEO_CALL_PK_NEON_BOTTOM_SHEET_HEIGHT_FRACTION = 0.5f

private data class PkVideoCallHudBinding(
    val hostDisplayName: String,
    val guestDisplayName: String,
    val hostLevel: Int,
    val guestLevel: Int,
    val hostOnLeft: Boolean,
)

/** Axis bounds for PiP dragging — stable snapshot used inside [pointerInput] without restarting every layout measure. */
private data class PipDragClamp(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float)

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoCallScreen(
    partner: UserProfile,
    isVideoButton: Boolean = true,
    callState: CallState = CallState.ACTIVE,
    callStatus: CallStatus = CallStatus.CONNECTING,
    isIncomingCall: Boolean = false,
    onAcceptCall: () -> Unit = {},
    onToggleMic: (Boolean) -> Unit = {},
    onToggleCamera: (Boolean) -> Unit = {},
    onToggleSpeaker: (Boolean) -> Unit = {},
    isPkSearching: Boolean = false,
    pkStatusText: String? = null,
    pkRetryAvailable: Boolean = false,
    onStartPk: (durationSeconds: Int) -> Unit = { },
    onInviteFollowerPk: () -> Unit = {},
    onRetryPk: (durationSeconds: Int) -> Unit = { },
    onCancelPk: () -> Unit = {},
    pkBattleSession: PkBattleSessionState? = null,
    /** Tug bar / PK HUD host vs guest accent (raw gender strings). */
    pkHudHostGender: String = "",
    pkHudGuestGender: String = "",
    onPkBattleTimerFinished: () -> Unit = {},
    /** Host-only rematch (same guest via RTDB). */
    pkRematchPending: Boolean = false,
    onPkRematchChallenge: () -> Unit = {},
    /** True when this device is the live/PK host (rematch button). */
    pkHudShowRematchForHost: Boolean = false,
    /** Elapsed seconds while connected; payer billing uses this in [DatingViewModel.endCall]. */
    onEndCall: (elapsedBillableSeconds: Int) -> Unit,
    /** When true, shows the same live chat / viewer strip as [LiveStreamActivity] (Firestore `live_streams/{room}`). */
    showWebrtcLiveOverlay: Boolean = false,
    /** When false, top overlay hides follower/viewer lines (private 1:1 vs PK/broadcast). */
    webrtcOverlayShowBroadcastMetrics: Boolean = true,
    liveChatMessages: List<LiveStreamChatMessage> = emptyList(),
    liveChatSenderPhotoLookup: (String) -> String? = { null },
    onLiveChatGiftRowClick: ((LiveStreamChatMessage) -> Unit)? = null,
    liveViewerCount: Int = 0,
    pkHostViewerCount: Int = 0,
    pkGuestViewerCount: Int = 0,
    pkHostViewerUserIds: List<String> = emptyList(),
    pkGuestViewerUserIds: List<String> = emptyList(),
    liveAudienceViewerUserIds: List<String> = emptyList(),
    overlayHostDisplayName: String = "",
    overlayHostAvatarUrl: String = "",
    overlayHostFollowerCount: Int = 0,
    onSendLiveChat: (String) -> Unit = {},
    availableGifts: List<Gift> = emptyList(),
    coinBalance: Int = 0,
    isGiftTransactionProcessing: Boolean = false,
    onSendGift: (Gift, Int) -> Unit = { _, _ -> },
    /** During live PK, gifts are sent to the chosen battler (host or guest uid). */
    onSendGiftToPkBattler: (String, Gift, Int) -> Unit = { _, _, _ -> },
    onTopUpCoins: () -> Unit = {},
    onProcessLuckyGifts: suspend (String, Int, Int) -> LuckyGiftsOutcome,
    onLuckyGiftVisual: (Gift, String, Int, String?) -> Unit = { _, _, _, _ -> },
    /** When non-null, diamond button opens [DiamondShopPopup] over the call (WebRTC keeps running). */
    diamondShopUiState: DatingUiState? = null,
    onBuyDiamonds: (CoinPackage) -> Unit = {},
    /** Host-only live chat moderation (same Firestore doc as [showWebrtcLiveOverlay]). */
    liveStreamDocIdForModeration: String? = null,
    streamHostUserIdForModeration: String? = null,
    currentUserIdForModeration: String? = null,
    onKickLiveChatter: ((String) -> Unit)? = null,
    onBlockLiveChatter: ((String) -> Unit)? = null,
    onFollowLiveChatter: ((String, String) -> Unit)? = null,
    onLikeLiveChatter: ((String, String) -> Unit)? = null,
    /** Switches front/back camera; defaults to the active [WebRTCManager] session. */
    onSwitchCamera: () -> Unit = { WebRTCManager.activeManager()?.switchCamera() },
    /** When non-null, AR/beauty filters target this session; otherwise [WebRTCManager.activeManager]. */
    webRtcSessionManager: WebRTCManager? = null,
    /**
     * When true, shows [LiveCallDiamondTracker] during [CallState.ACTIVE] (outgoing / billing party).
     * Aligns with [com.zipper.datingapp.ui.DatingUiState.webRtcCallIsStreamer] for 1:1 WebRTC calls.
     */
    isCallDiamondPayer: Boolean = false,
    /** Marketing one-minute free preview "call" (no WebRTC). */
    isFreeTeaserCall: Boolean = false,
    /** Fires once when the 60s teaser hits 0; host should tear down WebRTC and show paywall. */
    onFreeTeaserExpired: () -> Unit = {},
    /** PK live overlay: open profile from host/challenger spectator list sheet. */
    onOpenLiveViewerProfile: (UserProfile) -> Unit = {},
    /** PK live overlay: follow from spectator list sheet. */
    onFollowLiveViewer: (UserProfile) -> Unit = {},
    /** PK audience (not battler): like the live host from the neon side rail. */
    onPkSpectatorLikeHost: () -> Unit = {},
) {
    val context = LocalContext.current
    val requiredPermissions = remember(isVideoButton) {
        if (isVideoButton) {
            listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        } else {
            listOf(Manifest.permission.RECORD_AUDIO)
        }
    }
    val mediaPermissionsState = rememberMultiplePermissionsState(requiredPermissions)
    var launchedMediaPermissionPrompt by remember { mutableStateOf(false) }
    var mediaPermissionsWereGranted by remember { mutableStateOf(false) }
    var showGiftSheet by remember { mutableStateOf(false) }
    var showDiamondShopPopup by remember { mutableStateOf(false) }
    var showPkToolsSheet by remember { mutableStateOf(false) }
    var showEndCallConfirm by remember { mutableStateOf(false) }
    var pkWebRtcViewerSheet by remember { mutableStateOf<Pair<List<String>, String>?>(null) }
    var selectedPkDurationSec by remember { mutableIntStateOf(300) }
    val requestEndCall: () -> Unit = { showEndCallConfirm = true }
    var callDuration by remember { mutableIntStateOf(0) }
    val latestCoinBalance by rememberUpdatedState(coinBalance)
    val latestIsCallDiamondPayer by rememberUpdatedState(isCallDiamondPayer)
    val latestIsFreeTeaserCall by rememberUpdatedState(isFreeTeaserCall)
    val latestIsVideoButton by rememberUpdatedState(isVideoButton)
    val latestCustomVideoPrice by rememberUpdatedState(partner.customVideoPrice)
    val latestCustomAudioPrice by rememberUpdatedState(partner.customAudioPrice)
    BackHandler {
        when {
            pkWebRtcViewerSheet != null -> pkWebRtcViewerSheet = null
            showDiamondShopPopup -> showDiamondShopPopup = false
            showPkToolsSheet -> showPkToolsSheet = false
            showEndCallConfirm -> showEndCallConfirm = false
            showGiftSheet -> showGiftSheet = false
            else -> requestEndCall()
        }
    }

    LaunchedEffect(partner.id, showWebrtcLiveOverlay, callState, callStatus) {
        Log.i(
            "CALL_CHAT_UI",
            "surface=VideoCallScreen partner=${partner.id} overlay=$showWebrtcLiveOverlay state=$callState status=$callStatus v=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"
        )
    }

    LaunchedEffect(mediaPermissionsState.allPermissionsGranted) {
        if (mediaPermissionsState.allPermissionsGranted) mediaPermissionsWereGranted = true
    }
    LaunchedEffect(callState, mediaPermissionsState.allPermissionsGranted) {
        if (callState == CallState.ACTIVE &&
            mediaPermissionsWereGranted && !mediaPermissionsState.allPermissionsGranted
        ) {
            Toast.makeText(
                context,
                context.getString(R.string.video_call_permission_revoked_end),
                Toast.LENGTH_LONG
            ).show()
            onEndCall(callDuration)
        }
    }

    LaunchedEffect(callState) {
        if (callState == CallState.IDLE) {
            launchedMediaPermissionPrompt = false
            showGiftSheet = false
        }
    }
    LaunchedEffect(callState, mediaPermissionsState.allPermissionsGranted, launchedMediaPermissionPrompt) {
        if (callState != CallState.IDLE && !mediaPermissionsState.allPermissionsGranted && !launchedMediaPermissionPrompt) {
            launchedMediaPermissionPrompt = true
            mediaPermissionsState.launchMultiplePermissionRequest()
        }
    }

    var isMuted by remember { mutableStateOf(false) }
    // Speaker starts ON for video calls (loudspeaker), OFF for audio-only (earpiece).
    var isSpeakerOn by remember { mutableStateOf(isVideoButton) }
    var isCameraOn by remember { mutableStateOf(isVideoButton) }
    /** Full-screen shows remote when true, local when false (PIP shows the other). */
    var remoteVideoIsMainFeed by remember { mutableStateOf(true) }
    var lastPkClickMs by remember { mutableStateOf(0L) }
    /** Window Y of bottom edge of top gradient scrim — PiP must stay below (measured). */
    var pipMeasureTopScrimBottomYWindow by remember { mutableFloatStateOf(Float.NaN) }
    /** Window Y of top edge of bottom gradient scrim — PiP must stay above (measured). */
    var pipMeasureBottomScrimTopYWindow by remember { mutableFloatStateOf(Float.NaN) }
    /** Window Y of top edge of [WebRtcOverlayBottomChat] (measured). */
    var pipMeasureWebrtcGlassTopYWindow by remember { mutableFloatStateOf(Float.NaN) }
    /** Window Y of bottom edge of live top bar chrome (measured). */
    var pipMeasureWebrtcTopChromeBottomYWindow by remember { mutableFloatStateOf(Float.NaN) }
    // Observe incoming remote + local video tracks from the shared WebRTC session.
    // Using StateFlow avoids opening the camera a second time (which would conflict with WebRTC).
    val remoteVideoTrack by WebRTCManager.globalRemoteVideoTrack.collectAsStateWithLifecycle()
    val localVideoTrack by WebRTCManager.globalLocalVideoTrack.collectAsStateWithLifecycle()
    val eglCtx by WebRTCManager.globalEglContext.collectAsStateWithLifecycle()
    val isFrontCamera by WebRTCManager.globalIsFrontCamera.collectAsStateWithLifecycle()
    val showRemoteVideo = callState == CallState.ACTIVE && isVideoButton && remoteVideoTrack != null
    val showCallVideoSurfaces =
        callState == CallState.ACTIVE && isVideoButton && !isFreeTeaserCall &&
            (remoteVideoTrack != null || localVideoTrack != null)
    /**
     * Unified in-call glass UI (WebRtc top bar + bottom sheet) for **every** active video call —
     * private 1:1 or live — not only when [showWebrtcLiveOverlay] is true (room id can be unset briefly).
     */
    val showVideoCallGlassChrome = isVideoButton && callState != CallState.IDLE
    val useEmbeddedCallStrip = showVideoCallGlassChrome
    val liveChatBottomPad = if (useEmbeddedCallStrip) 88.dp else 200.dp
    val pkNeonPanelActive =
        pkBattleSession != null && showVideoCallGlassChrome
    /** Private or live non-PK: glass bottom sheet (~45% height) — banner + actions + composer + docked chat ([WebRtcOverlayBottomChat]). */
    val liveGlassBottomSheet = showVideoCallGlassChrome && !pkNeonPanelActive
    val liveChatBottomInset = when {
        pkNeonPanelActive -> 0.dp
        liveGlassBottomSheet -> 0.dp
        else -> liveChatBottomPad
    }
    LaunchedEffect(pkBattleSession, showVideoCallGlassChrome) {
        val pkPanel = pkBattleSession != null && showVideoCallGlassChrome
        if (!pkPanel) pkWebRtcViewerSheet = null
    }
    val myPkUid = FirebaseAuth.getInstance().currentUser?.uid?.trim().orEmpty()
    val pkAmBattler = remember(pkBattleSession?.hostUserId, pkBattleSession?.guestUserId, myPkUid) {
        val s = pkBattleSession
        if (s == null) true
        else {
            myPkUid.isNotBlank() &&
                (myPkUid == s.hostUserId.trim() || myPkUid == s.guestUserId.trim())
        }
    }
    val pkHudBinding = remember(
        pkBattleSession?.hostUserId,
        pkBattleSession?.guestUserId,
        diamondShopUiState?.currentUser?.id,
        diamondShopUiState?.currentUser?.name,
        diamondShopUiState?.currentUser?.level,
        partner.id,
        partner.name,
        partner.level,
    ) {
        val pk = pkBattleSession
        if (pk == null) {
            PkVideoCallHudBinding("", "", 1, 1, true)
        } else {
            val authUid = FirebaseAuth.getInstance().currentUser?.uid?.trim().orEmpty()
            val me = diamondShopUiState?.currentUser
            val myUid = me?.id?.trim().orEmpty().ifBlank { authUid }
            fun nameAndLevelForUid(uidRaw: String): Pair<String, Int> {
                val uid = uidRaw.trim()
                if (uid.isEmpty()) return "" to 1
                return when {
                    uid == myUid -> {
                        val n = me?.name?.trim().orEmpty().ifBlank { partner.name.trim() }
                        n to (me?.level?.coerceAtLeast(1) ?: 1)
                    }
                    uid == partner.id.trim() -> {
                        partner.name.trim().ifBlank { "Challenger" } to partner.level.coerceAtLeast(1)
                    }
                    else -> partner.name to partner.level.coerceAtLeast(1)
                }
            }
            val h = nameAndLevelForUid(pk.hostUserId)
            val g = nameAndLevelForUid(pk.guestUserId)
            PkVideoCallHudBinding(
                hostDisplayName = h.first,
                guestDisplayName = g.first,
                hostLevel = h.second,
                guestLevel = g.second,
                hostOnLeft = myUid == pk.hostUserId.trim(),
            )
        }
    }
    val statusPulse = rememberInfiniteTransition(label = "statusPulse").animateFloat(
        initialValue = CallStatusChipSpec.PULSE_MIN_ALPHA,
        targetValue = CallStatusChipSpec.PULSE_MAX_ALPHA,
        animationSpec = infiniteRepeatable(
            animation = tween(CallStatusChipSpec.PULSE_DURATION_MS.toInt()),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chipPulse"
    )

    LaunchedEffect(callState) {
        if (callState == CallState.IDLE) {
            remoteVideoIsMainFeed = true
            callDuration = 0
        }
        if (callState == CallState.ACTIVE) {
            callDuration = 0
            while (isActive) {
                delay(1000)
                callDuration++
                if (latestIsCallDiamondPayer && !latestIsFreeTeaserCall) {
                    val rate =
                        if (latestIsVideoButton) {
                            latestCustomVideoPrice ?: VirtualEconomyMath.DEFAULT_CALL_VIDEO_DIAMONDS_PER_MIN
                        } else {
                            latestCustomAudioPrice ?: VirtualEconomyMath.DEFAULT_CALL_AUDIO_DIAMONDS_PER_MIN
                        }
                    if (rate > 0) {
                        val billedMinutes = (callDuration / 60) + 1
                        val required = billedMinutes * rate
                        if (latestCoinBalance < required) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.call_ended_insufficient_diamonds),
                                Toast.LENGTH_LONG,
                            ).show()
                            onEndCall(callDuration)
                            break
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(callStatus) {
        if (CallStatusChipSpec.visualFor(callStatus).isTerminal) {
            delay(CallStatusChipSpec.TERMINAL_HOLD_MS)
            onEndCall(callDuration)
        }
    }
    LaunchedEffect(showVideoCallGlassChrome, callState, isVideoButton) {
        if (!showVideoCallGlassChrome || callState == CallState.IDLE || !isVideoButton) {
            pipMeasureWebrtcGlassTopYWindow = Float.NaN
            pipMeasureWebrtcTopChromeBottomYWindow = Float.NaN
        }
    }

    // [DatingViewModel.onCallActivityLaunched] moves outgoing callers DIALING → CONNECTING while ringback
    // continues. Treat outgoing CONNECTING like ringing so premium UI is not replaced by inline audio UI.
    val showPremiumRingingOverlay = remember(callState, isIncomingCall) {
        when (callState) {
            CallState.RINGING, CallState.DIALING -> true
            CallState.CONNECTING -> !isIncomingCall
            else -> false
        }
    }

    /**
     * Extra top spacing (below [WindowInsets.statusBars]) so the in-call header does not sit under
     * floating bars: [WebRtcOverlayTopBar], [LiveCallDiamondTracker], or [CallElapsedTimeAndBalanceBar].
     */
    val videoTopHeaderInsetBelowStatusDp = remember(
        isVideoButton,
        callState,
        showVideoCallGlassChrome,
        showPremiumRingingOverlay,
        useEmbeddedCallStrip,
        isCallDiamondPayer,
        isFreeTeaserCall,
    ) {
        if (!isVideoButton || showPremiumRingingOverlay || callState != CallState.ACTIVE) {
            return@remember 0.dp
        }
        when {
            showVideoCallGlassChrome ->
                // [WebRtcOverlayTopBar] uses padding(top = 96.dp); bar includes live timer line under name (~88–96dp).
                120.dp
            isFreeTeaserCall ->
                8.dp
            isCallDiamondPayer && !isFreeTeaserCall ->
                // [LiveCallDiamondTracker] is 3 compact rows + padding under status + 8.dp.
                44.dp
            else ->
                // [CallElapsedTimeAndBalanceBar] — two-line pill under status + 8.dp.
                20.dp
        }
    }

    val inVideoCallSurfacesBranch =
        callState == CallState.ACTIVE && isVideoButton && !isFreeTeaserCall
    val hasBothVideoTracks = remoteVideoTrack != null && localVideoTrack != null

    val mainGlInited = remember(partner.id) { AtomicBoolean(false) }
    val pipGlInited = remember(partner.id) { AtomicBoolean(false) }
    val mainRenderer = remember(partner.id, context) {
        SurfaceViewRenderer(context).also { v -> v.setZOrderMediaOverlay(false) }
    }
    val pipRenderer = remember(partner.id, context) {
        SurfaceViewRenderer(context).also { v ->
            // Media overlay only stacks above other SurfaceViews; Compose (glass chat) still paints on top.
            // OnTop places this surface above normal window content so self-view stays above the overlay.
            v.setZOrderOnTop(true)
            v.holder.setFormat(PixelFormat.TRANSLUCENT)
        }
    }
    DisposableEffect(mainRenderer, pipRenderer) {
        onDispose {
            runCatching {
                remoteVideoTrack?.removeSink(mainRenderer)
                remoteVideoTrack?.removeSink(pipRenderer)
                localVideoTrack?.removeSink(mainRenderer)
                localVideoTrack?.removeSink(pipRenderer)
                mainRenderer.release()
                pipRenderer.release()
            }.onFailure { Log.e("VideoCallScreen", "Video renderer release failed", it) }
        }
    }

    val callLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(callLifecycleOwner, partner.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                WebRTCManager.activeManager()?.refreshVideoSinkAttachments("video_call_on_resume")
            }
        }
        callLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { callLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Video vs audio: audio-only uses a dedicated full-screen UI (no SurfaceView, chat, or live overlays).
    Box(modifier = Modifier.fillMaxSize()) {
        if (!isVideoButton && !showPremiumRingingOverlay) {
            AudioOnlyCallContent(
                partner = partner,
                callState = callState,
                isIncomingCall = isIncomingCall,
                callDurationSeconds = callDuration,
                isMuted = isMuted,
                isSpeakerOn = isSpeakerOn,
                onMuteClick = {
                    isMuted = !isMuted
                    onToggleMic(!isMuted)
                },
                onSpeakerClick = {
                    isSpeakerOn = !isSpeakerOn
                    context.getSystemService(AudioManager::class.java)?.let { am ->
                        WebRTCManager.applyLivePlaybackSpeakerMode(am, preferLoudSpeaker = isSpeakerOn)
                    }
                    onToggleSpeaker(isSpeakerOn)
                },
                onEndCall = requestEndCall
            )
        } else if (isVideoButton) {
    // When live remote video is active we make the root background transparent so the
    // SurfaceView "hole" in the window shows the hardware surface below it.
    Box(modifier = Modifier.fillMaxSize().background(if (showCallVideoSurfaces) Color.Transparent else Color.Black)) {
        // Main / PiP [SurfaceViewRenderer]s live at [VideoCallScreen] scope so PiP can use root zIndex above blur/timer.
        key(callState == CallState.ACTIVE && isVideoButton && !isFreeTeaserCall) {
            if (showCallVideoSurfaces) {
                DisposableEffect(remoteVideoTrack, localVideoTrack, remoteVideoIsMainFeed) {
                    val r = remoteVideoTrack
                    val l = localVideoTrack
                    fun detachAll() {
                        r?.removeSink(mainRenderer)
                        r?.removeSink(pipRenderer)
                        l?.removeSink(mainRenderer)
                        l?.removeSink(pipRenderer)
                    }
                    detachAll()
                    when {
                        r != null && l != null -> {
                            if (remoteVideoIsMainFeed) {
                                r.addSink(mainRenderer)
                                l.addSink(pipRenderer)
                            } else {
                                l.addSink(mainRenderer)
                                r.addSink(pipRenderer)
                            }
                        }
                        r != null -> r.addSink(mainRenderer)
                        l != null -> l.addSink(mainRenderer)
                    }
                    onDispose { detachAll() }
                }
                Box(modifier = Modifier.fillMaxSize().zIndex(0f)) {
                    AndroidView(
                        factory = { mainRenderer },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            val ctx: EglBase.Context? = eglCtx
                            if (ctx != null && mainGlInited.compareAndSet(false, true)) {
                                runCatching {
                                    view.init(ctx, null)
                                }.onFailure { e ->
                                    mainGlInited.set(false)
                                    Log.e("VideoCallScreen", "Main renderer init failed (reactive EGL)", e)
                                }
                            }
                            val r = remoteVideoTrack
                            val l = localVideoTrack
                            val both = r != null && l != null
                            // Natural (non-mirrored) preview for both cameras — matches PK / post-flip expectations.
                            view.setMirror(false)
                            // Camera Kit + WebRTC: local feed uses FIT (full frame); remote uses FILL.
                            val localSc = RendererCommon.ScalingType.SCALE_ASPECT_FIT
                            val remoteSc = RendererCommon.ScalingType.SCALE_ASPECT_FILL
                            view.setScalingType(
                                when {
                                    !BuildConfig.SNAP_CAMERA_KIT_CONFIGURED -> remoteSc
                                    !both && l != null -> localSc
                                    !both -> remoteSc
                                    remoteVideoIsMainFeed -> remoteSc
                                    else -> localSc
                                }
                            )
                        }
                    )
                }
            }
        }
        if (!showCallVideoSurfaces) {
            Box(modifier = Modifier.fillMaxSize().zIndex(0f)) {
                if (partner.photoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = partner.photoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().blur(if (callState != CallState.ACTIVE || !isCameraOn) 20.dp else 0.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(ItzoUiTokens.StageMid), contentAlignment = Alignment.Center) {
                        Text(partner.name.take(1), color = Color.White.copy(alpha = 0.1f), fontSize = 120.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // zIndex 1 — all non-PIP Compose UI (must paint above the remote SurfaceView).
        // Do not use a full-screen gradient: it composites above both SurfaceViews, so the draggable
        // PIP video disappears “under” the scrim everywhere. Use top + bottom bands only; the middle
        // stays free so PIP (media overlay) stays visible while dragged.
        Box(modifier = Modifier.fillMaxSize().zIndex(1f)) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(VIDEO_CALL_TOP_SCRIM_FRACTION)
                        .onGloballyPositioned { coords ->
                            pipMeasureTopScrimBottomYWindow =
                                coords.positionInWindow().y + coords.size.height
                        }
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.38f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(VIDEO_CALL_BOTTOM_SCRIM_FRACTION)
                        .onGloballyPositioned { coords ->
                            pipMeasureBottomScrimTopYWindow = coords.positionInWindow().y
                        }
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    ItzoUiTokens.StageBottom.copy(alpha = 0.75f)
                                )
                            )
                        )
                )
            }

            if (pkBattleSession != null && showVideoCallGlassChrome && callState == CallState.ACTIVE) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.36f)
                ) {
                    PkArenaHud(
                        session = pkBattleSession,
                        modifier = Modifier.fillMaxSize(),
                        hostGender = pkHudHostGender,
                        guestGender = pkHudGuestGender,
                        onTimerFinished = onPkBattleTimerFinished,
                        liveChatMessages = liveChatMessages,
                        liveChatSenderPhotoLookup = liveChatSenderPhotoLookup,
                        showRematchForHost = pkHudShowRematchForHost,
                        rematchPending = pkRematchPending,
                        rematchWaitingLabel = stringResource(R.string.pk_rematch_waiting_guest),
                        onRematchChallenge = onPkRematchChallenge,
                        hostDisplayName = pkHudBinding.hostDisplayName,
                        guestDisplayName = pkHudBinding.guestDisplayName,
                        hostLevel = pkHudBinding.hostLevel,
                        guestLevel = pkHudBinding.guestLevel,
                        hostOnLeft = pkHudBinding.hostOnLeft,
                        pkRoundBadgeBottomInset = 52.dp,
                        giftPriceLookup = { id ->
                            availableGifts.find { it.id == id }?.price?.coerceAtLeast(0) ?: 0
                        },
                    )
                }
            }

        if (!showPremiumRingingOverlay && callStatus != CallStatus.CONNECTED) {
            CallStatusChip(
                callStatus = callStatus,
                pulseAlpha = statusPulse.value,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(
                        top = CallStatusChipSpec.CHIP_TOP_MARGIN_DP.dp + videoTopHeaderInsetBelowStatusDp
                    )
            )
        }

        // Top Info (Partner Name & State/Duration)
        if (!showPremiumRingingOverlay && !useEmbeddedCallStrip) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 80.dp + videoTopHeaderInsetBelowStatusDp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (callState != CallState.ACTIVE || !isCameraOn) {
                Surface(modifier = Modifier.size(120.dp), shape = CircleShape, color = Color.White.copy(alpha = 0.2f)) {
                    if (partner.photoUrl.isEmpty()) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(partner.name.take(1), color = Color.White, style = MaterialTheme.typography.displayLarge)
                        }
                    } else {
                        AsyncImage(model = partner.photoUrl, contentDescription = null, contentScale = ContentScale.Crop)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(partner.name, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                GenderAgeBadge(
                    gender = partner.genderText,
                    age = partner.currentAge
                )
            }
            
            val statusText = when (callState) {
                CallState.CONNECTING -> "Connecting..."
                CallState.ACTIVE -> stringResource(
                    R.string.call_in_call_with_timer,
                    formatCallDurationClock(callDuration)
                )
                else -> ""
            }
            
            Text(
                text = statusText,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(12.dp))
            if (isPkSearching) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = ItzoUiTokens.PkBarRight
                    )
                    Text("Looking for random match...", color = Color.White)
                    TextButton(onClick = onCancelPk) {
                        Text("Cancel", color = ItzoUiTokens.PkBarRight)
                    }
                }
            } else if (pkRetryAvailable) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(pkStatusText ?: "No match found", color = Color.White)
                    TextButton(
                        enabled = !isPkSearching,
                        onClick = {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastPkClickMs < 1000L) return@TextButton
                            lastPkClickMs = now
                            onRetryPk(selectedPkDurationSec)
                        }
                    ) {
                        Text("Retry", color = ItzoUiTokens.PkBarRight)
                    }
                }
            } else if (!pkStatusText.isNullOrBlank()) {
                Text(pkStatusText, color = Color(0xFF4ADE80), fontWeight = FontWeight.Medium)
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedPkDurationSec == 180,
                            onClick = { selectedPkDurationSec = 180 },
                            label = { Text(stringResource(R.string.pk_duration_3_min)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedPkDurationSec == 300,
                            onClick = { selectedPkDurationSec = 300 },
                            label = { Text(stringResource(R.string.pk_duration_5_min)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = !isPkSearching,
                        onClick = {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastPkClickMs < 1000L) return@Button
                            lastPkClickMs = now
                            onStartPk(selectedPkDurationSec)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            stringResource(R.string.pk_match_randomly),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        enabled = !isPkSearching,
                        onClick = {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastPkClickMs < 1000L) return@OutlinedButton
                            lastPkClickMs = now
                            onInviteFollowerPk()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                    ) {
                        Text(
                            stringResource(R.string.pk_invite_follower),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        } // end: hide inline content while premium ringing overlay is shown

        val luxuryBottomPad = 64.dp

        // Bottom controls — hidden while premium ringing overlay is shown, or when chat strip has all actions.
        if (!showPremiumRingingOverlay && !useEmbeddedCallStrip) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = luxuryBottomPad)
                .zIndex(8f),
            color = Color.Transparent
        ) {
                val bottomScroll = rememberScrollState()
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(bottomScroll)
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LuxuryGlassCircleButton(
                            icon = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = if (isSpeakerOn) "Speaker on" else "Speaker off",
                            onClick = {
                                isSpeakerOn = !isSpeakerOn
                                context.getSystemService(AudioManager::class.java)?.let { am ->
                                    WebRTCManager.applyLivePlaybackSpeakerMode(
                                        am,
                                        preferLoudSpeaker = isSpeakerOn,
                                    )
                                }
                                onToggleSpeaker(isSpeakerOn)
                            },
                            iconEmphasized = isSpeakerOn
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("Speaker", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LuxuryGlassCircleButton(
                            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            onClick = {
                                isMuted = !isMuted
                                onToggleMic(!isMuted)
                            },
                            iconEmphasized = isMuted
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(if (isMuted) "Unmute" else "Mute", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LuxuryGlassCircleButton(
                            icon = if (isFrontCamera) Icons.Default.FlipCameraAndroid else Icons.Default.FlipCameraIos,
                            contentDescription = stringResource(R.string.call_flip_camera),
                            onClick = onSwitchCamera,
                            enabled = mediaPermissionsState.allPermissionsGranted && isCameraOn && callState == CallState.ACTIVE,
                            iconEmphasized = !isFrontCamera
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("Flip", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                    if (callState == CallState.ACTIVE && isVideoButton) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LuxuryProminentGiftButton(
                                onClick = { if (!isGiftTransactionProcessing) showGiftSheet = true },
                                enabled = !isGiftTransactionProcessing,
                                giftProcessing = isGiftTransactionProcessing
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.live_send_gift_action),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LuxuryEndCallButton(onClick = requestEndCall)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.call_decline_label),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                }
            }
        }
        }

        if (showVideoCallGlassChrome) {
            val pkHostSpectatorsChipLabel =
                stringResource(R.string.live_pk_spectators_chip_host, pkHostViewerCount)
            val pkGuestSpectatorsChipLabel =
                stringResource(R.string.live_pk_spectators_chip_challenger, pkGuestViewerCount)
            val itzoPrivateVideoBar = liveGlassBottomSheet
            val callRatePerMinute =
                if (isVideoButton) {
                    partner.customVideoPrice ?: VirtualEconomyMath.DEFAULT_CALL_VIDEO_DIAMONDS_PER_MIN
                } else {
                    partner.customAudioPrice ?: VirtualEconomyMath.DEFAULT_CALL_AUDIO_DIAMONDS_PER_MIN
                }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(45f)
            ) {
                WebRtcOverlayTopBar(
                    displayName = overlayHostDisplayName.ifBlank { partner.name },
                    avatarUrl = overlayHostAvatarUrl.ifBlank { partner.photoUrl },
                    followerCount = overlayHostFollowerCount,
                    viewerCount = liveViewerCount,
                    showBroadcastMetrics = webrtcOverlayShowBroadcastMetrics && !itzoPrivateVideoBar,
                    showLiveViewerCount = !pkNeonPanelActive,
                    sessionElapsedSeconds = callDuration,
                    onLeaveStream = requestEndCall,
                    itzoVideoCallBar = itzoPrivateVideoBar,
                    payerDiamondBalance =
                        if (itzoPrivateVideoBar && isCallDiamondPayer) coinBalance else -1,
                    payerDiamondRatePerMinute =
                        if (itzoPrivateVideoBar && isCallDiamondPayer) callRatePerMinute else 0,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = if (itzoPrivateVideoBar) 48.dp else 96.dp)
                        .zIndex(6f)
                        .onGloballyPositioned { coords ->
                            pipMeasureWebrtcTopChromeBottomYWindow =
                                coords.positionInWindow().y + coords.size.height
                        }
                )
                WebRtcOverlayBottomChat(
                    messages = liveChatMessages,
                    onSendMessage = onSendLiveChat,
                    endCallLabel = stringResource(R.string.call_end_stream),
                    onEndCall = requestEndCall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .then(
                            when {
                                pkNeonPanelActive ->
                                    Modifier.fillMaxHeight(VIDEO_CALL_PK_NEON_BOTTOM_SHEET_HEIGHT_FRACTION)
                                liveGlassBottomSheet ->
                                    Modifier.fillMaxHeight(0.45f)
                                else -> Modifier
                            }
                        )
                        .padding(bottom = liveChatBottomInset)
                        .zIndex(6f)
                        .onGloballyPositioned { coords ->
                            pipMeasureWebrtcGlassTopYWindow = coords.positionInWindow().y
                        },
                    isMicOn = !isMuted,
                    isSpeakerOn = isSpeakerOn,
                    onToggleMic = { enabled ->
                        isMuted = !enabled
                        onToggleMic(enabled)
                    },
                    onToggleSpeaker = { speakerOn ->
                        isSpeakerOn = speakerOn
                        context.getSystemService(AudioManager::class.java)?.let { am ->
                            WebRTCManager.applyLivePlaybackSpeakerMode(
                                am,
                                preferLoudSpeaker = speakerOn,
                            )
                        }
                        onToggleSpeaker(speakerOn)
                    },
                    isVideoCall = true,
                    onFlipCamera = onSwitchCamera,
                    flipCameraEnabled = mediaPermissionsState.allPermissionsGranted &&
                        isCameraOn &&
                        callState == CallState.ACTIVE &&
                        callStatus == CallStatus.CONNECTED,
                    onOpenGiftSheet = { if (!isGiftTransactionProcessing) showGiftSheet = true },
                    onOpenDiamondShop = diamondShopUiState?.let { { showDiamondShopPopup = true } },
                    giftActionInProgress = isGiftTransactionProcessing,
                    audiencePanelFillHeight = pkNeonPanelActive || liveGlassBottomSheet,
                    pkNeonBattleChrome = pkNeonPanelActive,
                    pkNeonStreamerSide = pkAmBattler,
                    onPkSpectatorLike = if (pkNeonPanelActive && !pkAmBattler) {
                        { onPkSpectatorLikeHost() }
                    } else {
                        null
                    },
                    liveStreamDocIdForModeration = liveStreamDocIdForModeration,
                    streamHostUserIdForModeration = streamHostUserIdForModeration,
                    currentUserIdForModeration = currentUserIdForModeration,
                    onKickLiveChatter = onKickLiveChatter,
                    onBlockLiveChatter = onBlockLiveChatter,
                    onFollowLiveChatter = onFollowLiveChatter,
                    onLikeLiveChatter = onLikeLiveChatter,
                    senderPhotoLookup = liveChatSenderPhotoLookup,
                    onGiftRowClick = onLiveChatGiftRowClick,
                    pkHostSpectatorsLabel = if (pkNeonPanelActive) pkHostSpectatorsChipLabel else null,
                    pkGuestSpectatorsLabel = if (pkNeonPanelActive) pkGuestSpectatorsChipLabel else null,
                    onPkHostSpectatorsClick = if (pkNeonPanelActive) {
                        { pkWebRtcViewerSheet = pkHostViewerUserIds to pkHostSpectatorsChipLabel }
                    } else {
                        null
                    },
                    onPkGuestSpectatorsClick = if (pkNeonPanelActive) {
                        { pkWebRtcViewerSheet = pkGuestViewerUserIds to pkGuestSpectatorsChipLabel }
                    } else {
                        null
                    },
                    pkAllSpectatorsSummaryLine = null,
                    onPkAllSpectatorsClick = null,
                    onOpenArFilters = null,
                    onOpenPkToolsMenu = if (pkNeonPanelActive) {
                        { showPkToolsSheet = true }
                    } else {
                        null
                    },
                    // Same chrome for paid private 1:1 and marketing teaser ([isFreeTeaserCall]): flags/modifiers above do not branch on teaser.
                    showComplianceBanner = pkNeonPanelActive || liveGlassBottomSheet,
                    bottomSheetComposer = !pkNeonPanelActive,
                    transparentDockPanel = !pkNeonPanelActive,
                    itzoStandardCallDock = !pkNeonPanelActive,
                    skipNavigationBarsInset = true,
                )
            }
            if (showPkToolsSheet) {
                PkToolsMenuSheet(
                    onDismiss = { showPkToolsSheet = false },
                    onDiamondStore = if (pkNeonPanelActive) {
                        {
                            if (diamondShopUiState != null) {
                                showDiamondShopPopup = true
                            } else {
                                onTopUpCoins()
                            }
                        }
                    } else {
                        diamondShopUiState?.let { { showDiamondShopPopup = true } }
                    },
                    onFaceFilters = null,
                )
            }
            val shopState = diamondShopUiState
            if (showDiamondShopPopup && shopState != null) {
                DiamondShopPopup(
                    uiState = shopState,
                    onDismiss = { showDiamondShopPopup = false },
                    onBuyDiamonds = onBuyDiamonds
                )
            }
            pkWebRtcViewerSheet?.let { (uids, title) ->
                val sheetUi = diamondShopUiState ?: DatingUiState()
                val sheetMe = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                LiveWatchingViewersSheet(
                    viewerUids = uids,
                    uiState = sheetUi,
                    currentUserId = sheetMe,
                    sheetTitle = title,
                    onDismiss = { pkWebRtcViewerSheet = null },
                    onOpenViewerProfile = { p ->
                        pkWebRtcViewerSheet = null
                        onOpenLiveViewerProfile(p)
                    },
                    onFollowViewer = { p -> onFollowLiveViewer(p) },
                )
            }
        }

    }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121212))
            )
        }

        // Show PiP whenever both tracks exist; do not gate on [isCameraOn] — toggling camera off
        // still leaves the local capturer/track and hid PiP entirely (felt like a regression).
        if (isVideoButton &&
            inVideoCallSurfacesBranch &&
            showCallVideoSurfaces &&
            hasBothVideoTracks &&
            mediaPermissionsState.allPermissionsGranted
        ) {
            val pipWidth = 120.dp
            val pipHeight = 160.dp
            // Live: [WebRtcOverlayTopBar] is padding(top = 96.dp) + ~72–80dp chrome — 104.dp was too small (PiP sat on the pill).
            val pipTopBelowInsets =
                if (showVideoCallGlassChrome) 188.dp else 56.dp
            val pipEndMargin = 16.dp
            val edgePad = 16.dp
            val density = LocalDensity.current
            val viewConfig = LocalViewConfiguration.current
            var dragOffset by remember(callState, showVideoCallGlassChrome, useEmbeddedCallStrip) {
                mutableStateOf(Offset.Zero)
            }
            var insetOriginInWindow by remember { mutableStateOf(Offset.Zero) }
            var pipWindowPositionReady by remember { mutableStateOf(false) }

            // Root-level zIndex paints above gradient scrims, live overlay (45f), and timer/wallet (60f).
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(VIDEO_CALL_PIP_ROOT_Z_INDEX)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    .onGloballyPositioned { coords ->
                        insetOriginInWindow = coords.positionInWindow()
                        pipWindowPositionReady = true
                    }
            ) {
                val maxWpx = with(density) { maxWidth.toPx() }
                val maxHpx = with(density) { maxHeight.toPx() }
                val pipWpx = with(density) { pipWidth.toPx() }
                val pipHpx = with(density) { pipHeight.toPx() }
                val topReservePx = with(density) { pipTopBelowInsets.toPx() }
                val endReservePx = with(density) { pipEndMargin.toPx() }
                val edgePx = with(density) { edgePad.toPx() }
                val bottomReservedDpOnly = with(density) {
                    if (showVideoCallGlassChrome) {
                        (liveChatBottomPad + WebRtcLiveOverlayBottomPanelReserveDp + 52.dp).toPx()
                    } else {
                        (152.dp + 120.dp).toPx()
                    }
                }
                val bottomReservedPx = if (showVideoCallGlassChrome) {
                    maxOf(bottomReservedDpOnly, maxHpx * VIDEO_CALL_WEBRTC_PIP_BOTTOM_CLEAR_FRACTION)
                } else {
                    maxOf(
                        bottomReservedDpOnly,
                        maxHpx * VIDEO_CALL_BOTTOM_SCRIM_FRACTION,
                    )
                }
                val pipEdgeGapPx = with(density) { 4.dp.toPx() }
                val statusTopPx =
                    with(density) { WindowInsets.statusBars.getTop(density).toFloat() }
                val navBottomPx =
                    with(density) { WindowInsets.navigationBars.getBottom(density).toFloat() }
                val fullHpx = maxHpx + statusTopPx + navBottomPx
                val topScrimBottomLocalPx =
                    fullHpx * VIDEO_CALL_TOP_SCRIM_FRACTION - statusTopPx
                val topForbiddenEndLocalPx = if (showVideoCallGlassChrome) {
                    maxOf(topScrimBottomLocalPx, maxHpx * VIDEO_CALL_WEBRTC_PIP_TOP_CLEAR_FRACTION)
                } else {
                    topScrimBottomLocalPx
                }
                val bottomScrimTopLocalPx =
                    fullHpx * (1f - VIDEO_CALL_BOTTOM_SCRIM_FRACTION) - statusTopPx
                val minDragXBase = edgePx - maxWpx + endReservePx + pipWpx
                val minDragX = if (showVideoCallGlassChrome) {
                    maxOf(
                        minDragXBase,
                        -(maxWpx * 0.38f) + endReservePx + pipWpx * 0.5f
                    )
                } else {
                    minDragXBase
                }
                val maxDragX = endReservePx - edgePx
                val minDragYLegacy = edgePx - topReservePx + with(density) { 88.dp.toPx() }
                val fractionTopMinDragY =
                    topForbiddenEndLocalPx - topReservePx + pipEdgeGapPx
                val middleStartFrac =
                    if (showVideoCallGlassChrome) {
                        VIDEO_CALL_PIP_MIDDLE_ZONE_START_LIVE
                    } else {
                        VIDEO_CALL_PIP_MIDDLE_ZONE_START_PRIVATE
                    }
                val middleEndFrac =
                    if (showVideoCallGlassChrome) {
                        VIDEO_CALL_PIP_MIDDLE_ZONE_END_LIVE
                    } else {
                        VIDEO_CALL_PIP_MIDDLE_ZONE_END_PRIVATE
                    }
                val middleZoneTopPx = maxHpx * middleStartFrac
                val middleZoneBottomPx = maxHpx * middleEndFrac
                val minDragYThreeZones =
                    middleZoneTopPx + pipEdgeGapPx - topReservePx
                val maxDragYThreeZones =
                    middleZoneBottomPx - pipEdgeGapPx - topReservePx - pipHpx
                val originY = insetOriginInWindow.y
                val topMinDragCandidates = buildList {
                    add(minDragYThreeZones)
                    add(fractionTopMinDragY)
                    if (pipMeasureTopScrimBottomYWindow.isFinite()) {
                        add(
                            pipMeasureTopScrimBottomYWindow - originY + pipEdgeGapPx - topReservePx,
                        )
                    }
                    if (showVideoCallGlassChrome && pipMeasureWebrtcTopChromeBottomYWindow.isFinite()) {
                        add(
                            pipMeasureWebrtcTopChromeBottomYWindow - originY + pipEdgeGapPx -
                                topReservePx,
                        )
                    }
                }
                val minDragY = maxOf(topMinDragCandidates.maxOrNull()!!, minDragYLegacy).coerceAtLeast(0f)
                val maxDragYScrimFraction =
                    bottomScrimTopLocalPx - topReservePx - pipHpx - pipEdgeGapPx
                val maxDragYFromBottomPanel =
                    maxHpx - bottomReservedPx - topReservePx - pipHpx - pipEdgeGapPx
                val bottomMaxDragCandidates = buildList {
                    add(maxDragYThreeZones)
                    add(maxDragYScrimFraction)
                    add(maxDragYFromBottomPanel)
                    if (pipMeasureBottomScrimTopYWindow.isFinite()) {
                        add(
                            pipMeasureBottomScrimTopYWindow - originY - pipEdgeGapPx -
                                topReservePx - pipHpx,
                        )
                    }
                    if (showVideoCallGlassChrome && pipMeasureWebrtcGlassTopYWindow.isFinite()) {
                        add(
                            pipMeasureWebrtcGlassTopYWindow - originY - pipEdgeGapPx -
                                topReservePx - pipHpx,
                        )
                    }
                }
                val maxDragY = bottomMaxDragCandidates.minOrNull()!!.coerceAtLeast(minDragY)

                val pipDragClamp = remember(minDragX, maxDragX, minDragY, maxDragY) {
                    val xLo = minOf(minDragX, maxDragX)
                    val xHi = maxOf(minDragX, maxDragX)
                    val yLoRaw = minOf(minDragY, maxDragY)
                    val yHiRaw = maxOf(minDragY, maxDragY)
                    PipDragClamp(xLo, xHi, yLoRaw, yHiRaw)
                }
                val pipDragClampRef = rememberUpdatedState(pipDragClamp)

                LaunchedEffect(pipDragClamp) {
                    dragOffset = Offset(
                        dragOffset.x.coerceIn(pipDragClamp.minX, pipDragClamp.maxX),
                        dragOffset.y.coerceIn(pipDragClamp.minY, pipDragClamp.maxY),
                    )
                }

                val pipLeftLocal = maxWpx - endReservePx - pipWpx + dragOffset.x
                val pipTopLocal = topReservePx + dragOffset.y
                val popupOffset = IntOffset(
                    (insetOriginInWindow.x + pipLeftLocal).roundToInt(),
                    (insetOriginInWindow.y + pipTopLocal).roundToInt()
                )

                if (pipWindowPositionReady) {
                    // Popup shares the activity window; the glass chat still wins hit-testing over the PiP hole.
                    // Fullscreen transparent Dialog + FLAG_NOT_TOUCH_MODAL: PiP is in a layer above the overlay,
                    // touches on the PiP hit the preview; touches elsewhere pass through to chat / video.
                    Dialog(
                        onDismissRequest = {},
                        properties = DialogProperties(
                            dismissOnBackPress = false,
                            dismissOnClickOutside = false,
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false,
                            securePolicy = SecureFlagPolicy.Inherit,
                        ),
                    ) {
                        val dialogView = LocalView.current
                        SideEffect {
                            dialogView.post {
                                (dialogView.parent as? DialogWindowProvider)?.window?.let { window ->
                                    window.setLayout(
                                        WindowManager.LayoutParams.MATCH_PARENT,
                                        WindowManager.LayoutParams.MATCH_PARENT,
                                    )
                                    @Suppress("DEPRECATION")
                                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                                    window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                                    window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                                    window.setBackgroundDrawable(
                                        ColorDrawable(android.graphics.Color.TRANSPARENT),
                                    )
                                    window.setDimAmount(0f)
                                }
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .offset { popupOffset }
                                    .size(width = pipWidth, height = pipHeight)
                                    .pointerInput(remoteVideoIsMainFeed, viewConfig.touchSlop) {
                                        val slop = viewConfig.touchSlop.toFloat()
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            val pointerId = down.id
                                            var total = Offset.Zero
                                            var hasDraggedPastSlop = false
                                            drag(pointerId) { change ->
                                                val delta = change.positionChange()
                                                total += delta
                                                if (hypot(total.x.toDouble(), total.y.toDouble()) >= slop) {
                                                    hasDraggedPastSlop = true
                                                    val b = pipDragClampRef.value
                                                    dragOffset = Offset(
                                                        x = (dragOffset.x + delta.x).coerceIn(b.minX, b.maxX),
                                                        y = (dragOffset.y + delta.y).coerceIn(b.minY, b.maxY),
                                                    )
                                                }
                                                change.consume()
                                            }
                                            if (!hasDraggedPastSlop) {
                                                remoteVideoIsMainFeed = !remoteVideoIsMainFeed
                                            }
                                        }
                                    }
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .background(Color.Black),
                            ) {
                                AndroidView(
                                    factory = { pipRenderer },
                                    modifier = Modifier.fillMaxSize(),
                                    update = { view ->
                                        (view.parent as? android.view.ViewGroup)?.bringChildToFront(view)
                                        view.setZOrderOnTop(true)
                                        view.holder.setFormat(PixelFormat.TRANSLUCENT)
                                        val ctx: EglBase.Context? = eglCtx
                                        if (ctx != null && pipGlInited.compareAndSet(false, true)) {
                                            runCatching {
                                                view.init(ctx, null)
                                            }.onFailure { e ->
                                                pipGlInited.set(false)
                                                Log.e(
                                                    "VideoCallScreen",
                                                    "PIP renderer init failed (reactive EGL)",
                                                    e,
                                                )
                                            }
                                        }
                                        view.setMirror(false)
                                        val localSc = RendererCommon.ScalingType.SCALE_ASPECT_FIT
                                        val remoteSc = RendererCommon.ScalingType.SCALE_ASPECT_FILL
                                        val r = remoteVideoTrack
                                        val l = localVideoTrack
                                        val both = r != null && l != null
                                        view.setScalingType(
                                            when {
                                                !BuildConfig.SNAP_CAMERA_KIT_CONFIGURED -> remoteSc
                                                !both && l != null -> localSc
                                                !both -> remoteSc
                                                remoteVideoIsMainFeed -> localSc
                                                else -> remoteSc
                                            }
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (callState != CallState.IDLE && !mediaPermissionsState.allPermissionsGranted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(VIDEO_CALL_PERMISSION_OVERLAY_Z_INDEX)
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (isVideoButton) stringResource(R.string.video_call_perm_camera_mic)
                        else stringResource(R.string.video_call_perm_mic),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { mediaPermissionsState.launchMultiplePermissionRequest() },
                        colors = ButtonDefaults.buttonColors(containerColor = ItzoUiTokens.PkBarRight)
                    ) {
                        Text(stringResource(R.string.video_call_grant_permissions))
                    }
                }
            }
        }

        if (showGiftSheet && isVideoButton) {
            val pk = pkBattleSession
            val pkRecipients =
                if (pk != null && showVideoCallGlassChrome && callState == CallState.ACTIVE) {
                    val hostUid = pk.hostUserId.trim()
                    val guestUid = pk.guestUserId.trim()
                    val hostLabel = overlayHostDisplayName.trim().ifBlank { "Host" }
                    val guestLabel = partner.name.trim().ifBlank { "Guest" }
                    when {
                        hostUid.isNotBlank() && guestUid.isNotBlank() ->
                            listOf(hostUid to hostLabel, guestUid to guestLabel)
                        hostUid.isNotBlank() -> listOf(hostUid to hostLabel)
                        else -> null
                    }
                } else null
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(4000f)
            ) {
                GiftSelectionSheet(
                    gifts = availableGifts,
                    coinBalance = coinBalance,
                    isProcessing = isGiftTransactionProcessing,
                    pkGiftScoreHint = if (pkRecipients != null) {
                        stringResource(R.string.live_pk_gift_counts_for_score)
                    } else null,
                    onTopUpCoins = onTopUpCoins,
                    onDismiss = { showGiftSheet = false },
                    onSend = { gift, count ->
                        onSendGift(gift, count)
                        showGiftSheet = false
                    },
                    pkBattlerRecipients = pkRecipients,
                    onSendGiftToPkBattler = if (pkRecipients != null) { uid, gift, count ->
                        onSendGiftToPkBattler(uid, gift, count)
                        showGiftSheet = false
                    } else null,
                    luckyGiftReceiverId = partner.id.takeIf { pkRecipients == null },
                    onLuckySend = onProcessLuckyGifts,
                    onLuckyGiftVisual = onLuckyGiftVisual,
                )
            }
        }

        when {
            useEmbeddedCallStrip -> Unit
            callState == CallState.ACTIVE && isFreeTeaserCall -> {
                FreeTeaserCallTracker(
                    callState = callState,
                    onExpired = onFreeTeaserExpired,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 8.dp)
                        .zIndex(60f)
                )
            }
            callState == CallState.ACTIVE && isCallDiamondPayer && !isFreeTeaserCall &&
                !(showVideoCallGlassChrome && liveGlassBottomSheet) -> {
                val rate = if (isVideoButton) {
                    partner.customVideoPrice ?: VirtualEconomyMath.DEFAULT_CALL_VIDEO_DIAMONDS_PER_MIN
                } else {
                    partner.customAudioPrice ?: VirtualEconomyMath.DEFAULT_CALL_AUDIO_DIAMONDS_PER_MIN
                }
                LiveCallDiamondTracker(
                    callState = callState,
                    diamondsPerMinute = rate,
                    userDiamondBalance = coinBalance,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 8.dp)
                        .zIndex(60f)
                )
            }
            callState == CallState.ACTIVE && !isFreeTeaserCall &&
                !(showVideoCallGlassChrome && liveGlassBottomSheet) -> {
                CallElapsedTimeAndBalanceBar(
                    elapsedSeconds = callDuration,
                    diamondBalance = coinBalance,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 8.dp)
                        .zIndex(60f)
                )
            }
        }

        if (showPremiumRingingOverlay) {
            RingingScreen(
                partner = partner,
                isIncomingCall = isIncomingCall,
                isVideoCall = isVideoButton,
                callState = callState,
                onAcceptCall = onAcceptCall,
                onEndCall = requestEndCall
            )
        }

        if (showEndCallConfirm) {
            FuturisticConfirmCloseDialog(
                onDismissRequest = { showEndCallConfirm = false },
                onConfirm = {
                    showEndCallConfirm = false
                    onEndCall(callDuration)
                },
            )
        }
    }
}

private fun formatCallDurationClock(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%02d:%02d", m, sec)
}

/**
 * Elapsed call time + current diamond balance for the callee / non–billing party.
 * Billing party uses [LiveCallDiamondTracker] instead (time + estimated spend + remaining).
 */
@Composable
private fun CallElapsedTimeAndBalanceBar(
    elapsedSeconds: Int,
    diamondBalance: Int,
    modifier: Modifier = Modifier,
) {
    val clock = formatCallDurationClock(elapsedSeconds)
    val breakdown = stringResource(
        R.string.call_elapsed_breakdown,
        elapsedSeconds / 60,
        elapsedSeconds % 60
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color(0xFF14141C).copy(alpha = 0.88f),
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "●",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF4ADE80),
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = "⏱ $clock",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "\u00A0|\u00A0",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.38f)
                )
                Text(
                    text = stringResource(R.string.call_diamond_wallet_short, diamondBalance),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFC400)
                )
            }
            Text(
                text = breakdown,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.62f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Billing-party live clock + estimated diamond spend. State and [LaunchedEffect] are scoped here so
 * only this composable recomposes every second; remote/local [AndroidView] WebRTC sinks stay in stable parents.
 *
 * Upfront minute billing: minute bucket = `(elapsedSeconds / 60) + 1` (second 0 bills one minute).
 */
@Composable
private fun LiveCallDiamondTracker(
    callState: CallState,
    diamondsPerMinute: Int,
    userDiamondBalance: Int,
    modifier: Modifier = Modifier,
) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(callState) {
        elapsedSeconds = 0
        if (callState != CallState.ACTIVE) return@LaunchedEffect
        while (isActive) {
            delay(1000)
            elapsedSeconds++
        }
    }
    val rate = diamondsPerMinute.coerceAtLeast(0)
    val billedMinutes = (elapsedSeconds / 60) + 1
    val diamondsSpent = billedMinutes * rate
    val diamondsRemaining = userDiamondBalance - diamondsSpent
    val clock = formatCallDurationClock(elapsedSeconds)
    val breakdown = stringResource(
        R.string.call_elapsed_breakdown,
        elapsedSeconds / 60,
        elapsedSeconds % 60
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color(0xFF14141C).copy(alpha = 0.88f),
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "●",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF4ADE80),
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = "⏱ $clock",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "\u00A0|\u00A0",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.38f)
                )
                Text(
                    text = stringResource(R.string.call_diamond_wallet_short, userDiamondBalance),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFC400)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.call_diamond_est_spend, diamondsSpent),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFF6B7A)
                )
                Text(
                    text = "\u00A0|\u00A0",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.38f)
                )
                Text(
                    text = stringResource(R.string.call_diamond_remaining_short, diamondsRemaining),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFC400)
                )
            }
            Text(
                text = breakdown,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.62f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Shell aligned with [LiveCallDiamondTracker]: teaser content differs (countdown vs billing rows). */
@Composable
private fun FreeTeaserCallTracker(
    callState: CallState,
    onExpired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var remainingSec by remember { mutableIntStateOf(60) }
    var didFire by remember { mutableStateOf(false) }
    val urgent = remainingSec in 1..9
    val flash = rememberInfiniteTransition(label = "teaserFlash").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse),
        label = "flashAlpha"
    )
    LaunchedEffect(callState) {
        didFire = false
        if (callState != CallState.ACTIVE) return@LaunchedEffect
        remainingSec = 60
        while (isActive && remainingSec > 0) {
            delay(1000)
            remainingSec--
        }
        if (isActive && remainingSec <= 0 && !didFire) {
            didFire = true
            onExpired()
        }
    }
    val mm = remainingSec / 60
    val ss = remainingSec % 60
    val label = stringResource(R.string.teaser_free_call_label)
    val previewBadge = stringResource(R.string.teaser_preview_badge)
    val accent = if (urgent) Color(0xFFFF1744) else Color(0xFFFFC400)
    val borderAlpha = if (urgent) flash.value else 0.85f
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (urgent) Color(0xFF220808).copy(alpha = 0.95f) else Color(0xFF14141C).copy(alpha = 0.88f),
        shadowElevation = 10.dp,
        border = if (urgent) {
            BorderStroke(
                width = 2.dp,
                color = accent.copy(alpha = borderAlpha)
            )
        } else {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = previewBadge,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = if (urgent) Color(0xFFFF8A80) else Color(0xFFFFF59D),
                modifier = Modifier.then(if (urgent) Modifier.scale(1f + (flash.value - 0.7f) * 0.06f) else Modifier)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = String.format(Locale.US, "%02d:%02d", mm, ss),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = if (urgent) Color(0xFFFF5252) else Color.White,
                modifier = Modifier.then(if (urgent) Modifier.alpha(flash.value) else Modifier)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeaserRechargeBottomSheet(
    uiState: DatingUiState,
    onDismiss: () -> Unit,
    onOpenFullShop: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101018),
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.25f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.teaser_paywall_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.teaser_paywall_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.78f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${uiState.coinBalance}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFFC400)
                )
                Spacer(Modifier.width(6.dp))
                Text("💎", fontSize = 22.sp)
                Text(
                    text = " " + stringResource(R.string.teaser_paywall_balance_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.teaser_paywall_packages_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 118.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(coinPackages) { pkg ->
                    DiamondPackageCard(pkg = pkg, onClick = {
                        Toast.makeText(
                            context,
                            context.getString(R.string.teaser_paywall_contact_reseller),
                            Toast.LENGTH_LONG
                        ).show()
                    })
                }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onOpenFullShop) {
                Text(
                    stringResource(R.string.teaser_paywall_open_full_shop),
                    color = ItzoUiTokens.PkBarRight,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private const val LUXURY_GLASS_ALPHA = 0.2f

@Composable
private fun LuxuryGlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconEmphasized: Boolean = false
) {
    val iconTint = when {
        !enabled -> Color.White.copy(alpha = 0.38f)
        iconEmphasized -> ItzoUiTokens.PkBarRight
        else -> Color.White
    }
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = LUXURY_GLASS_ALPHA))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun LuxuryEndCallButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .border(BorderStroke(1.25.dp, Color.White.copy(alpha = 0.42f)), CircleShape)
            .clip(CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ItzoUiTokens.CallDecline.copy(alpha = 0.94f),
                        ItzoUiTokens.CallDecline.copy(alpha = 0.68f),
                    ),
                ),
                shape = CircleShape,
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.CallEnd,
            contentDescription = stringResource(R.string.call_end_stream),
            tint = Color.White,
            modifier = Modifier.size(26.dp)
        )
    }
}

/**
 * High-visibility gift entry for monetization — gradient, pulse, and strong contrast vs glass controls.
 */
@Composable
private fun LuxuryProminentGiftButton(
    onClick: () -> Unit,
    enabled: Boolean,
    giftProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    val pulse = rememberInfiniteTransition(label = "giftPulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "giftPulseScale"
    )
    val borderPulse = rememberInfiniteTransition(label = "giftBorder").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "giftBorderAlpha"
    )
    Box(
        modifier = modifier
            .size(64.dp)
            .scale(if (enabled && !giftProcessing) pulse.value else 1f)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        GoldAccent,
                        ItzoUiTokens.PkBarRight,
                        ItzoUiTokens.AppAccent,
                    )
                )
            )
            .border(
                width = 3.dp,
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.35f * borderPulse.value),
                        ItzoUiTokens.FrameViolet.copy(alpha = 0.85f),
                        Color.White.copy(alpha = 0.9f * borderPulse.value),
                        ItzoUiTokens.AppAccent,
                        Color.White.copy(alpha = 0.35f * borderPulse.value)
                    )
                ),
                shape = CircleShape
            )
            .clickable(enabled = enabled && !giftProcessing) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.CardGiftcard,
            contentDescription = "Gift",
            tint = if (giftProcessing) Color.White.copy(alpha = 0.45f) else Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}

/**
 * Row 1: Speaker · Flip · Mute. Row 2: More · Gift or End (audio) · Keypad.
 * Video hang-up lives in the top overlay so it is not beside chat/send.
 */
@Composable
private fun LuxuryCallControlsGrid(
    isVideoCall: Boolean,
    flipEnabled: Boolean,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isFrontCamera: Boolean = true,
    onSpeakerClick: () -> Unit,
    onFlipClick: () -> Unit,
    onMuteClick: () -> Unit,
    onMoreClick: () -> Unit,
    onKeypadClick: () -> Unit,
    /** Elapsed seconds at hang-up; forwarded to [DatingViewModel.endCall] for billing. */
    onEndCall: (elapsedBillableSeconds: Int) -> Unit,
    callDurationSecondsForBilling: Int = 0,
    showGiftButton: Boolean = false,
    /** When false (video), [LuxuryEndCallButton] is omitted — use top-right hang up instead. */
    showEndCallInGrid: Boolean = true,
    giftProcessing: Boolean = false,
    onGiftClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LuxuryGlassCircleButton(
                icon = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = if (isSpeakerOn) "Speaker on" else "Speaker off",
                onClick = onSpeakerClick,
                iconEmphasized = isSpeakerOn
            )
            LuxuryGlassCircleButton(
                icon = if (isFrontCamera) Icons.Default.FlipCameraAndroid else Icons.Default.FlipCameraIos,
                contentDescription = stringResource(R.string.call_flip_camera),
                onClick = onFlipClick,
                enabled = isVideoCall && flipEnabled,
                iconEmphasized = !isFrontCamera
            )
            LuxuryGlassCircleButton(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                onClick = onMuteClick,
                iconEmphasized = isMuted
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LuxuryGlassCircleButton(
                icon = Icons.Default.MoreHoriz,
                contentDescription = stringResource(R.string.call_more_options_hint),
                onClick = onMoreClick
            )
            when {
                showGiftButton -> LuxuryProminentGiftButton(
                    onClick = onGiftClick,
                    enabled = true,
                    giftProcessing = giftProcessing
                )
                showEndCallInGrid -> LuxuryEndCallButton(onClick = { onEndCall(callDurationSecondsForBilling) })
                else -> Spacer(modifier = Modifier.size(64.dp))
            }
            LuxuryGlassCircleButton(
                icon = Icons.Default.Dialpad,
                contentDescription = stringResource(R.string.call_keypad_hint),
                onClick = onKeypadClick
            )
        }
    }
}

/**
 * Native phone–style audio call: no video surface, chat, or live overlays.
 */
@Composable
private fun AudioOnlyCallContent(
    partner: UserProfile,
    callState: CallState,
    isIncomingCall: Boolean,
    callDurationSeconds: Int,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onMuteClick: () -> Unit,
    onSpeakerClick: () -> Unit,
    onEndCall: () -> Unit
) {
    val context = LocalContext.current
    val statusText = when (callState) {
        CallState.DIALING -> "Calling…"
        CallState.RINGING -> if (isIncomingCall) "Incoming…" else "Ringing…"
        CallState.CONNECTING -> "Connecting…"
        CallState.ACTIVE -> stringResource(
            R.string.call_in_call_with_timer,
            formatCallDurationClock(callDurationSeconds)
        )
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ItzoUiTokens.stageBackgroundBrush())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(top = 52.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = partner.name,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = statusText,
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier.size(280.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(268.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ItzoUiTokens.FrameTeal.copy(alpha = 0.35f),
                                    ItzoUiTokens.FrameViolet.copy(alpha = 0.18f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .border(
                            width = 3.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.5f),
                                    ItzoUiTokens.FrameTeal.copy(alpha = 0.65f),
                                    ItzoUiTokens.FrameViolet.copy(alpha = 0.55f),
                                    Color.White.copy(alpha = 0.4f)
                                )
                            ),
                            shape = CircleShape
                        )
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(ItzoUiTokens.StageMid)
                ) {
                    if (partner.photoUrl.isNotEmpty()) {
                        AsyncImage(
                            model = partner.photoUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                partner.name.take(1),
                                color = Color.White,
                                fontSize = 72.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LuxuryGlassCircleButton(
                        icon = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (isSpeakerOn) "Speaker on" else "Speaker off",
                        onClick = onSpeakerClick,
                        iconEmphasized = isSpeakerOn
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Speaker", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LuxuryGlassCircleButton(
                        icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        onClick = onMuteClick,
                        iconEmphasized = isMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(if (isMuted) "Unmute" else "Mute", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LuxuryGlassCircleButton(
                        icon = Icons.Default.Message,
                        contentDescription = "Message",
                        onClick = {
                            Toast.makeText(context, "Message", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Message", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LuxuryEndCallButton(onClick = onEndCall)
                    Spacer(Modifier.height(6.dp))
                    Text("End", color = ItzoUiTokens.CallDecline, fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Full-screen ringing / outgoing-connecting UI over the call surface.
 * Premium layout: centered avatar with pulsing ring, WhatsApp-style FABs (Material theme = Outfit).
 */
@Composable
private fun RingingScreen(
    partner: UserProfile,
    isIncomingCall: Boolean,
    isVideoCall: Boolean,
    callState: CallState,
    onAcceptCall: () -> Unit,
    onEndCall: () -> Unit
) {
    PremiumRingingCallFullScreen(
        callerName = partner.name,
        callerPhotoUrl = partner.photoUrl,
        isIncoming = isIncomingCall,
        isVideoCall = isVideoCall,
        callState = callState,
        onAccept = onAcceptCall,
        onDecline = onEndCall
    )
}

/**
 * App-root incoming overlay (replaces AlertDialog). Same premium treatment as in-call ringing.
 */
@Composable
fun PremiumAppIncomingCallOverlay(
    callerName: String,
    callerPhotoUrl: String,
    isVideoCall: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    PremiumRingingCallFullScreen(
        callerName = callerName,
        callerPhotoUrl = callerPhotoUrl,
        isIncoming = true,
        isVideoCall = isVideoCall,
        callState = CallState.RINGING,
        onAccept = onAccept,
        onDecline = onDecline,
        modifier = modifier
    )
}

@Composable
private fun PremiumRingingCallFullScreen(
    callerName: String,
    callerPhotoUrl: String,
    isIncoming: Boolean,
    isVideoCall: Boolean,
    callState: CallState = CallState.RINGING,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ringPulse = rememberInfiniteTransition(label = "ringPulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "ringScale"
    )
    val borderPulse = rememberInfiniteTransition(label = "borderPulse").animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "borderAlpha"
    )
    val avatarDp = 220.dp
    val subtitle = when {
        !isIncoming && callState == CallState.CONNECTING ->
            stringResource(R.string.call_connecting_out_label)
        !isIncoming -> stringResource(R.string.call_calling_out_label)
        isVideoCall -> stringResource(R.string.call_incoming_video_label)
        else -> stringResource(R.string.call_incoming_voice_label)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ItzoUiTokens.stageBackgroundBrush())
    ) {
        if (callerPhotoUrl.isNotEmpty()) {
            AsyncImage(
                model = callerPhotoUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(32.dp),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.15f))
            Text(
                text = subtitle.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isIncoming) ItzoUiTokens.FrameTeal else Color.White.copy(alpha = 0.55f),
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(20.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(300.dp)) {
                if (isIncoming) {
                    Box(
                        modifier = Modifier
                            .size((272f * ringPulse.value).dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        ItzoUiTokens.FrameTeal.copy(alpha = 0.38f * borderPulse.value),
                                        ItzoUiTokens.FrameViolet.copy(alpha = 0.12f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                }
                Box(
                    modifier = Modifier
                        .size(avatarDp)
                        .border(
                            width = 4.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    ItzoUiTokens.FrameTeal.copy(alpha = 0.35f + 0.45f * borderPulse.value),
                                    ItzoUiTokens.PkBarRight.copy(alpha = 0.85f * borderPulse.value),
                                    Color.White.copy(alpha = 0.5f + 0.35f * borderPulse.value),
                                    ItzoUiTokens.FrameViolet.copy(alpha = 0.65f * borderPulse.value),
                                    ItzoUiTokens.FrameTeal.copy(alpha = 0.35f + 0.45f * borderPulse.value)
                                )
                            ),
                            shape = CircleShape
                        )
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(ItzoUiTokens.StageMid)
                ) {
                    if (callerPhotoUrl.isEmpty()) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = callerName.take(1).ifBlank { "?" },
                                color = Color.White,
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        AsyncImage(
                            model = callerPhotoUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = callerName.ifBlank { stringResource(R.string.live_chat_sender_default) },
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(0.2f))
            if (isIncoming) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 28.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FloatingActionButton(
                            onClick = onDecline,
                            containerColor = ItzoUiTokens.CallDecline,
                            contentColor = Color.White,
                            elevation = FloatingActionButtonDefaults.elevation(
                                defaultElevation = 10.dp,
                                pressedElevation = 14.dp
                            ),
                            shape = CircleShape,
                            modifier = Modifier.size(88.dp)
                        ) {
                            Icon(
                                Icons.Default.CallEnd,
                                contentDescription = stringResource(R.string.call_decline_label),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.call_decline_label),
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FloatingActionButton(
                            onClick = onAccept,
                            containerColor = ItzoUiTokens.CallAccept,
                            contentColor = Color.White,
                            elevation = FloatingActionButtonDefaults.elevation(
                                defaultElevation = 12.dp,
                                pressedElevation = 16.dp
                            ),
                            shape = CircleShape,
                            modifier = Modifier.size(88.dp)
                        ) {
                            Icon(
                                Icons.Default.Call,
                                contentDescription = stringResource(R.string.call_accept_label),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.call_accept_label),
                            color = Color.White.copy(alpha = 0.95f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 36.dp)
                ) {
                    FloatingActionButton(
                        onClick = onDecline,
                        containerColor = ItzoUiTokens.CallDecline,
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 10.dp,
                            pressedElevation = 14.dp
                        ),
                        shape = CircleShape,
                        modifier = Modifier.size(88.dp)
                    ) {
                        Icon(
                            Icons.Default.CallEnd,
                            contentDescription = stringResource(R.string.call_cancel_outgoing_label),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.call_cancel_outgoing_label),
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun CallStatusChip(
    callStatus: CallStatus,
    pulseAlpha: Float,
    modifier: Modifier = Modifier
) {
    val visual = CallStatusChipSpec.visualFor(callStatus)
    val tint = if (visual.shouldPulse) Color(visual.colorInt).copy(alpha = pulseAlpha) else Color(visual.colorInt)

    Surface(
        modifier = modifier,
        color = Color(CallStatusChipSpec.CHIP_BG_COLOR),
        shape = RoundedCornerShape(CallStatusChipSpec.CHIP_CORNER_RADIUS_DP.dp),
        tonalElevation = CallStatusChipSpec.CHIP_ELEVATION_DP.dp
    ) {
        Text(
            text = "${visual.glyph} ${visual.label}",
            modifier = Modifier.padding(
                horizontal = CallStatusChipSpec.CHIP_HORIZONTAL_PADDING_DP.dp,
                vertical = CallStatusChipSpec.CHIP_VERTICAL_PADDING_DP.dp
            ),
            color = tint,
            fontWeight = FontWeight.SemiBold,
            fontSize = CallStatusChipSpec.CHIP_TEXT_SIZE_SP.sp
        )
        }
}

@Composable
fun CallControlItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(56.dp).clickable { onClick() },
            shape = CircleShape,
            color = if (active) Color.White else Color.White.copy(alpha = 0.2f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = if (active) Color.Black else Color.White)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

