package com.zipper.datingapp.ui.screens

import android.Manifest
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import com.zipper.datingapp.ui.paddingWithNavigationBars
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.zipper.datingapp.R
import com.zipper.datingapp.data.CoinPackage
import com.zipper.datingapp.data.Gift
import com.zipper.datingapp.data.LuckyGiftsOutcome
import com.zipper.datingapp.data.LiveDiscoverLiveFilter
import com.zipper.datingapp.data.LiveDiscoverSurface
import com.zipper.datingapp.data.LiveStreamChatMessage
import com.zipper.datingapp.data.PkBattlePhase
import com.zipper.datingapp.data.AudioPartyHostSeatAction
import com.zipper.datingapp.data.AudioPartySeat
import com.zipper.datingapp.data.UserProfile
import com.zipper.datingapp.data.activeCoPublisherUids
import com.zipper.datingapp.data.guestSeatIndexRange
import com.zipper.datingapp.service.FirebaseService
import com.zipper.datingapp.data.composeLazyKey
import com.zipper.datingapp.ui.DatingUiState
import com.zipper.datingapp.ui.DatingViewModel
import com.zipper.datingapp.ui.LIVE_CHAT_STAGING_VISIBLE_MESSAGES
import com.zipper.datingapp.ui.liveChatHistoryStripMaxHeight
import com.zipper.datingapp.ui.liveChatStagingViewportMaxHeight
import com.zipper.datingapp.ui.rollerAnimateScrollToLatest
import com.zipper.datingapp.ui.LiveDiscoverNotice
import com.zipper.datingapp.ui.components.FuturisticConfirmCloseDialog
import com.zipper.datingapp.ui.components.GenderAgeBadge
import com.zipper.datingapp.ui.components.GiftSelectionSheet
import com.zipper.datingapp.ui.components.LiveChatNewMessagesCue
import com.zipper.datingapp.ui.components.LiveChatOlderMessagesCue
import com.zipper.datingapp.ui.components.LiveStreamComplianceBanner
import com.zipper.datingapp.ui.components.LiveStreamComplianceBannerVariant
import com.zipper.datingapp.ui.components.VerifiedBadge
import com.zipper.datingapp.ui.theme.AppResponsiveContentMaxWidth
import com.zipper.datingapp.ui.theme.AppWindowWidthClass
import com.zipper.datingapp.ui.theme.LocalWindowWidthClass
import com.zipper.datingapp.ui.theme.ItzoUiTokens
import com.zipper.datingapp.ui.theme.PremiumUiTokens
import com.zipper.datingapp.ui.theme.defaultHorizontalContentPadding
import com.zipper.datingapp.ui.webrtc.LiveStreamGiftChatRow
import com.zipper.datingapp.ui.webrtc.WebRtcOverlayBottomChat
import com.zipper.datingapp.ui.webrtc.PkArenaEndCard
import com.zipper.datingapp.ui.webrtc.PkArenaHud
import com.zipper.datingapp.ui.webrtc.PkArenaSpectatorBattleHud
import com.zipper.datingapp.ui.webrtc.pkMvpSupporterPhotoUrls
import com.snap.camerakit.lenses.LensesComponent
import com.zipper.datingapp.BuildConfig
import com.zipper.datingapp.isDeviceCapableOfAR
import com.zipper.datingapp.webrtc.FilterType
import com.zipper.datingapp.webrtc.WebRTCManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import androidx.compose.animation.core.AnimationVector1D

/** Matches VideoCallScreen luxury control glass (audience speaker toggle). */
private const val LIVE_WATCHER_LUXURY_GLASS_ALPHA = 0.2f

/** Trailing gutter for PK spectator vertical rail (chat, gift, diamond, speaker, like). */
private val PkSpectatorActionStripOuterEnd = 64.dp
private val PkSpectatorFabSize = 48.dp
private val PkSpectatorFabSpacing = 10.dp
private val PkSpectatorTrayIconSize = 22.dp

@Composable
private fun PkSpectatorTrayFab(
    onClick: () -> Unit,
    enabled: Boolean,
    accessibilityLabel: String,
    containerLayer: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(PkSpectatorFabSize)
            .shadow(5.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.45f), ambientColor = Color.Black.copy(alpha = 0.12f))
            .clip(CircleShape)
            .then(containerLayer)
            .semantics { contentDescription = accessibilityLabel }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Readable white labels over live video when the host chrome has no dark scrim. */
private val WatcherTopChromeTextShadow =
    Shadow(color = Color.Black.copy(alpha = 0.92f), offset = Offset(0f, 1f), blurRadius = 10f)

/** Min vertical drag (px) to switch live; swipe up → next, swipe down → previous. */
private val LiveWatcherSwipeThreshold = 72.dp

@Composable
private fun rememberLiveWatcherSwipeModifier(
    enabled: Boolean,
    streamKey: String,
    onSwipeStep: (Int) -> Unit,
): Modifier {
    val density = LocalDensity.current
    val thresholdPx = with(density) { LiveWatcherSwipeThreshold.toPx() }
    val handler = rememberUpdatedState(onSwipeStep)
    return remember(enabled, streamKey, thresholdPx) {
        if (!enabled) {
            Modifier
        } else {
            Modifier.pointerInput(streamKey, thresholdPx) {
                var accumulated = 0f
                var lastFire = 0L
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount -> accumulated += dragAmount },
                    onDragEnd = {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastFire >= 550L) {
                            when {
                                accumulated <= -thresholdPx -> {
                                    lastFire = now
                                    handler.value(1)
                                }
                                accumulated >= thresholdPx -> {
                                    lastFire = now
                                    handler.value(-1)
                                }
                            }
                        }
                        accumulated = 0f
                    },
                    onDragCancel = { accumulated = 0f }
                )
            }
        }
    }
}

private enum class LiveWatcherDmOverlayPage {
    Hidden,
    /** Full list of DM threads while still watching live. */
    Inbox,
    /** Single conversation (host or picked user). */
    Thread,
}

private fun formatLiveStreamDurationClock(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%02d:%02d", m, sec)
}

private class LiveWatcherConnHolder {
    var host: WebRTCManager? = null
    var guest: WebRTCManager? = null
    val audioPartyCoSubs: MutableList<WebRTCManager> = mutableListOf()
    var seatPublisher: WebRTCManager? = null

    fun clearAudioPartyCoSubs() {
        for (m in audioPartyCoSubs) {
            runCatching { m.onDestroy() }
        }
        audioPartyCoSubs.clear()
    }

    fun disposeSeatPublisher() {
        runCatching { seatPublisher?.onDestroy() }
        seatPublisher = null
    }
}

private class LiveHostPkChallengerConnHolder {
    var mgr: WebRTCManager? = null
}

/** Host receives seated guests’ audio via extra [WebRTCManager] links (see [LiveWatcherConnHolder.audioPartyCoSubs]). */
private class LiveHostAudioPartyCoHolder {
    val coSubs: MutableList<WebRTCManager> = mutableListOf()

    fun clear() {
        for (m in coSubs) {
            runCatching { m.onDestroy() }
        }
        coSubs.clear()
    }
}

private fun resolvePkBattlerGender(battlerUid: String, uiState: DatingUiState): String {
    val u = battlerUid.trim()
    if (u.isEmpty()) return ""
    if (u == uiState.currentUser?.id) return uiState.currentUser?.gender.orEmpty()
    if (u == uiState.activeCallPartner?.id) return uiState.activeCallPartner?.gender.orEmpty()
    return uiState.profiles.find { it.id == u }?.gender
        ?: uiState.filteredProfiles.find { it.id == u }?.gender
        ?: ""
}

private enum class NamePillGenderAccent { Female, Male, Default }

private fun normalizeNamePillGender(raw: String): NamePillGenderAccent {
    val g = raw.trim().lowercase()
    return when (g) {
        "female", "f" -> NamePillGenderAccent.Female
        "male", "m" -> NamePillGenderAccent.Male
        else -> NamePillGenderAccent.Default
    }
}

private fun namePillAccentBrush(gender: String): Brush? =
    when (normalizeNamePillGender(gender)) {
        NamePillGenderAccent.Female -> Brush.linearGradient(
            colors = listOf(Color(0xFFFF6B6B), Color(0xFFFF1493))
        )
        NamePillGenderAccent.Male -> Brush.linearGradient(
            colors = listOf(Color(0xFF4FACFE), Color(0xFF00F2FE))
        )
        NamePillGenderAccent.Default -> null
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    uiState: DatingUiState,
    isGuestPreview: Boolean = false,
    onGuestRestricted: () -> Unit = {},
    /** `audioParty` true = audio-only broadcast; ignored when ending live (host already live). */
    onToggleLive: (audioParty: Boolean) -> Unit,
    /** Host audio party: 6 / 10 / 14 seat stage layout (Firestore-synced). */
    onAudioPartySeatCountChange: (Int) -> Unit = {},
    onAudioPartyClaimSeat: (hostId: String, seatIndex: Int) -> Unit = { _, _ -> },
    onAudioPartyLeaveSeat: (hostId: String, seatIndex: Int) -> Unit = { _, _ -> },
    onAudioPartyHostSeatAction: (hostId: String, AudioPartyHostSeatAction) -> Unit = { _, _ -> },
    onAudioPartySetMyMic: (hostId: String, publishing: Boolean) -> Unit = { _, _ -> },
    onAudioPartyMergeNowPlaying: (title: String, artist: String) -> Unit = { _, _ -> },
    onAudioPartySendEmoji: (String) -> Unit = { },
    /** Host: merges `streams/{uid}.roomBackgroundKey` for audio party room theme. */
    onAudioPartyRoomBackgroundChange: (String) -> Unit = {},
    /** Called when the host ends a Compose live session (minutes streamed, ≥1). */
    onRecordStreamingMinutes: (Int) -> Unit = {},
    onSendGift: (Gift, Int) -> Unit,
    /** PK on a watched live: gift to host or guest by Firebase uid. */
    onSendPkBattlerGift: (String, Gift, Int) -> Unit = { _, _, _ -> },
    onWatchLive: (UserProfile) -> Unit,
    onFollow: (UserProfile) -> Unit,
    onStopWatching: () -> Unit,
    onGoBattleMode: () -> Unit,
    onCall: (UserProfile) -> Unit,
    onMessage: (UserProfile) -> Unit,
    /** Private DM to the watched host without leaving the live player or switching tabs. */
    onSendDmWhileWatching: (UserProfile, String) -> Unit = { _, _ -> },
    onBuyDiamonds: (CoinPackage) -> Unit = {},
    onUploadIntroVideo: (Uri) -> Unit,
    onSendComment: (String) -> Unit,
    onClearGiftAnimation: () -> Unit,
    onTopUpCoins: () -> Unit,
    onStartPk: (durationSeconds: Int) -> Unit,
    onInviteFollowerPk: () -> Unit = {},
    onRetryPk: (durationSeconds: Int) -> Unit,
    onCancelPk: () -> Unit,
    onPkBattleTimerFinished: () -> Unit = {},
    onPkRematchChallenge: () -> Unit = {},
    onPkFindAnotherOpponent: () -> Unit = {},
    /** Compose PK host: challenger video link lost — end round and offer find-another. */
    onPkOpponentPeerDisconnected: () -> Unit = {},
    /** Called when a viewer taps the like (heart) button while watching a live. */
    onLikeLive: (UserProfile) -> Unit = {},
    /** After WebRTC preview starts, re-sync Firestore `isLive` for cross-device Live tab. */
    onHostStreamReady: () -> Unit = {},
    onKickLiveChatter: (streamDocId: String, targetUserId: String) -> Unit = { _, _ -> },
    onBlockLiveChatter: (streamDocId: String, hostUid: String, targetUserId: String) -> Unit = { _, _, _ -> },
    onFollowLiveChatter: (targetUserId: String, displayNameHint: String) -> Unit = { _, _ -> },
    onLikeLiveChatter: (targetUserId: String, displayNameHint: String) -> Unit = { _, _ -> },
    onPrivateGiftToChatter: (UserProfile, Gift, Int) -> Unit = { _, _, _ -> },
    /** Pull-to-refresh on the discover pane: server re-fetch of profiles + wallet. */
    onRefreshLiveDiscover: () -> Unit = {},
    /** Clears [DatingUiState.liveDiscoverNotice] after the Live tab shows a Toast for new live / PK. */
    onClearLiveDiscoverNotice: () -> Unit = {},
    /** Opens full-screen profile on Home tab (same flow as Followed list). */
    onOpenUserProfile: (UserProfile) -> Unit = {},
    /** Start Firestore DM observation for full chat overlay while watching live (no tab switch). */
    onStartDmObservationForPartner: (UserProfile) -> Unit = {},
    onClearDmThreadFocus: () -> Unit = {},
    onChatSendMessage: (String, String) -> Unit = { _, _ -> },
    onChatSendImageMessage: (String, Uri) -> Unit = { _, _ -> },
    onChatConsumeImageUploadError: () -> Unit = {},
    onChatSendGiftMessage: (String, Gift) -> Unit = { _, _ -> },
    onChatSendGift: (Gift, Int) -> Unit = { _, _ -> },
    onChatStartCall: (Boolean, String) -> Unit = { _, _ -> },
    /** Load/clear profile gift history when live viewer profile popup opens or closes. */
    onLiveProfileGiftTargetChanged: (String?) -> Unit = {},
    onLikeProfile: (UserProfile) -> Unit = {},
    onGiftToProfile: (UserProfile, Gift, Int) -> Unit = { _, _, _ -> },
    /** PK spectator: host uid, session key, rooting for host (true) or challenger (false). */
    onConfirmPkWatchPerspective: (streamHostId: String, sessionKey: String, rootingForHost: Boolean) -> Unit = { _, _, _ -> },
    /** Swipe up/down while watching live: `+1` next stream, `-1` previous (Discover order). */
    onSwipeToAdjacentLive: (Int) -> Unit = {},
    /** Clears [DatingUiState.liveSwipeNoOthersNonce] after showing the “no other streams” toast. */
    onClearLiveSwipeNoOthersNotice: () -> Unit = {},
    onBlockUserFromProfile: (UserProfile) -> Unit = {},
    onReportUserFromProfile: (UserProfile, String) -> Unit = { _, _ -> },
    onProcessLuckyGifts: suspend (String, Int, Int) -> LuckyGiftsOutcome,
    onLuckyGiftVisual: (Gift, String, Int, String?) -> Unit = { _, _, _, _ -> },
    onGetAgoraToken: suspend (channelName: String, isPublisher: Boolean) -> String? = { _, _ -> null },
    onSetLiveDiscoverSurface: (LiveDiscoverSurface) -> Unit = {},
    onSetLiveDiscoverLiveFilter: (LiveDiscoverLiveFilter) -> Unit = {},
    onRequestAudioPartyVideoCallWithHost: (String) -> Unit = {},
    onHostRespondAudioPartyVideoCall: (String, Boolean) -> Unit = { _, _ -> },
) {
    val pkBannerHostIds = uiState.activeLivePkBanners.keys
    val liveProfiles = uiState.profiles.filter { it.isLive }
    /** Hosts with an active PK Firestore banner may appear here even if `isLive` is briefly false — keeps PK joinable from Discover. */
    val liveStreamsRowProfiles = remember(
        uiState.profiles,
        uiState.activeLivePkBanners,
        uiState.liveDiscoverLiveFilter,
    ) {
        val pkIds = uiState.activeLivePkBanners.keys
        val live = uiState.profiles.filter { it.isLive }
        val pkExtra = uiState.profiles.filter { !it.isLive && it.id in pkIds }
        val base = (pkExtra + live).distinctBy { it.id }
        val filtered = when (uiState.liveDiscoverLiveFilter) {
            LiveDiscoverLiveFilter.ALL -> base
            LiveDiscoverLiveFilter.AUDIO_PARTY_ONLY -> base.filter { it.isLive && it.liveStreamAudioOnly }
        }
        filtered.sortedByDescending { it.id in pkIds }
    }
    val onlineProfiles = uiState.profiles.filter { it.isOnline && !it.isLive && it.id !in pkBannerHostIds }
    val newProfiles = uiState.profiles.sortedByDescending { it.registrationDate }.take(6)
    val widthClass = LocalWindowWidthClass.current
    val liveCardWidth = run {
        val fraction = when (widthClass) {
            AppWindowWidthClass.Compact -> 0.42f
            AppWindowWidthClass.Medium -> 0.38f
            AppWindowWidthClass.Expanded -> 0.36f
        }
        (LocalConfiguration.current.screenWidthDp * fraction).dp.coerceIn(138.dp, 200.dp)
    }
    val context = LocalContext.current

    LaunchedEffect(uiState.liveDiscoverNotice) {
        when (val n = uiState.liveDiscoverNotice) {
            null -> return@LaunchedEffect
            is LiveDiscoverNotice.NewLive -> Toast.makeText(
                context,
                context.getString(R.string.live_discover_notify_new_live, n.hostDisplayName),
                Toast.LENGTH_LONG
            ).show()
            is LiveDiscoverNotice.PkStarted -> Toast.makeText(
                context,
                context.getString(R.string.live_discover_notify_pk, n.summaryLine),
                Toast.LENGTH_LONG
            ).show()
        }
        onClearLiveDiscoverNotice()
    }

    if (uiState.isLive) {
        var pkHostBattlerProfilePeek by remember { mutableStateOf<UserProfile?>(null) }
        val resolvedPkHostPeek = remember(
            pkHostBattlerProfilePeek?.id,
            uiState.profiles,
            uiState.filteredProfiles,
            pkHostBattlerProfilePeek
        ) {
            val seed = pkHostBattlerProfilePeek ?: return@remember null
            val id = seed.id
            uiState.profiles.find { it.id == id }
                ?: uiState.filteredProfiles.find { it.id == id }
                ?: seed
        }
        LaunchedEffect(pkHostBattlerProfilePeek?.id) {
            onLiveProfileGiftTargetChanged(pkHostBattlerProfilePeek?.id)
        }
        Box(modifier = Modifier.fillMaxSize()) {
            LiveStreamingView(
                uiState = uiState,
                isGuestPreview = isGuestPreview,
                onGuestRestricted = onGuestRestricted,
                onEndLive = { onToggleLive(false) },
                onGoBattle = onGoBattleMode,
                onSendComment = onSendComment,
                onStartPk = onStartPk,
                onInviteFollowerPk = onInviteFollowerPk,
                onRetryPk = onRetryPk,
                onCancelPk = onCancelPk,
                onPkBattleTimerFinished = onPkBattleTimerFinished,
                onPkRematchChallenge = onPkRematchChallenge,
                onPkFindAnotherOpponent = onPkFindAnotherOpponent,
                onPkOpponentPeerDisconnected = onPkOpponentPeerDisconnected,
                onRecordStreamingMinutes = onRecordStreamingMinutes,
                onHostStreamReady = onHostStreamReady,
                onKickLiveChatter = onKickLiveChatter,
                onBlockLiveChatter = onBlockLiveChatter,
                onFollowLiveChatter = onFollowLiveChatter,
                onLikeLiveChatter = onLikeLiveChatter,
                onPrivateGiftToChatter = onPrivateGiftToChatter,
                onSendPkBattlerGift = onSendPkBattlerGift,
                onVideoCallChatter = onCall,
                onMessageChatter = onMessage,
                onTopUpCoins = onTopUpCoins,
                onBuyDiamonds = onBuyDiamonds,
                onFollow = onFollow,
                onOpenPkOpponentProfile = { pkHostBattlerProfilePeek = it },
                onProcessLuckyGifts = onProcessLuckyGifts,
                onLuckyGiftVisual = onLuckyGiftVisual,
                onAudioPartySeatCountChange = onAudioPartySeatCountChange,
                onAudioPartyClaimSeat = onAudioPartyClaimSeat,
                onAudioPartyLeaveSeat = onAudioPartyLeaveSeat,
                onAudioPartyHostSeatAction = onAudioPartyHostSeatAction,
                onAudioPartySetMyMic = onAudioPartySetMyMic,
                onAudioPartyMergeNowPlaying = onAudioPartyMergeNowPlaying,
                onAudioPartySendEmoji = onAudioPartySendEmoji,
                onAudioPartyRoomBackgroundChange = onAudioPartyRoomBackgroundChange,
                onGetAgoraToken = onGetAgoraToken,
                onHostRespondAudioPartyVideoCall = onHostRespondAudioPartyVideoCall,
                onSendHostStreamGift = onSendGift,
            )
            if (resolvedPkHostPeek != null) {
                val p = resolvedPkHostPeek
                val followingIds = uiState.currentUser?.followingIds.orEmpty().toSet()
                val isFollowed = p.id in followingIds || uiState.followedProfiles.any { it.id == p.id }
                val isLiked = p.id in uiState.likedProfileIds
                BackHandler { pkHostBattlerProfilePeek = null }
                Dialog(
                    onDismissRequest = { pkHostBattlerProfilePeek = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
                        val showSafetyMenuPk = !isGuestPreview &&
                            uiState.currentUser?.isGuest != true &&
                            !uiState.currentUser?.id.isNullOrBlank() &&
                            p.id != uiState.currentUser?.id
                        ProfileDetailScreen(
                            profile = p,
                            isGuestPreview = isGuestPreview,
                            onGuestRestricted = onGuestRestricted,
                            isFollowed = isFollowed,
                            isLiked = isLiked,
                            recentSentGifts = uiState.profileDetailSentGifts,
                            recentReceivedGifts = uiState.profileDetailReceivedGifts,
                            onDismiss = { pkHostBattlerProfilePeek = null },
                            onFollow = { onFollow(p) },
                            onLike = {
                                if (!isGuestPreview) onLikeProfile(p) else onGuestRestricted()
                            },
                            onVideoCall = {
                                pkHostBattlerProfilePeek = null
                                onCall(p)
                            },
                            onMessage = {
                                pkHostBattlerProfilePeek = null
                                onMessage(p)
                            },
                            onWatchLive = {
                                pkHostBattlerProfilePeek = null
                                onWatchLive(p)
                            },
                            availableGifts = uiState.availableGifts,
                            coinBalance = uiState.coinBalance,
                            isGiftTransactionProcessing = uiState.isGiftTransactionProcessing,
                            onTopUpCoins = onTopUpCoins,
                            onSendGiftToProfile = { gift, count -> onGiftToProfile(p, gift, count) },
                            onProcessLuckyGifts = onProcessLuckyGifts,
                            onLuckyGiftVisual = onLuckyGiftVisual,
                            showSafetyOverflowMenu = showSafetyMenuPk,
                            signedInViewerUid = uiState.currentUser?.id.orEmpty(),
                            onBlockUser = if (showSafetyMenuPk) {
                                {
                                    onBlockUserFromProfile(p)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.profile_blocked_toast),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    pkHostBattlerProfilePeek = null
                                }
                            } else null,
                            onReportUser = if (showSafetyMenuPk) {
                                { reason -> onReportUserFromProfile(p, reason) }
                            } else null,
                        )
                    }
                }
            }
        }
    } else if (uiState.watchingLivePartner != null) {
        LaunchedEffect(uiState.liveSwipeNoOthersNonce) {
            if (uiState.liveSwipeNoOthersNonce > 0L) {
                Toast.makeText(
                    context,
                    context.getString(R.string.live_swipe_no_other_streams),
                    Toast.LENGTH_SHORT
                ).show()
                onClearLiveSwipeNoOthersNotice()
            }
        }
        val liveHostPartner = uiState.watchingLivePartner!!
        Box(modifier = Modifier.fillMaxSize()) {
            LiveWatcherView(
                partner = liveHostPartner,
                uiState = uiState,
                onStopWatching = onStopWatching,
                onSendGift = onSendGift,
                onSendPkBattlerGift = onSendPkBattlerGift,
                onPkBattleTimerFinished = onPkBattleTimerFinished,
                onFollow = onFollow,
                onCall = onCall,
                onMessage = onMessage,
                onSendComment = onSendComment,
                onTopUpCoins = onTopUpCoins,
                onLikeLive = onLikeLive,
                isGuestPreview = isGuestPreview,
                onGuestRestricted = onGuestRestricted,
                onPrivateGiftToChatter = onPrivateGiftToChatter,
                onVideoCallChatter = onCall,
                onMessageChatter = onMessage,
                onSendDmWhileWatching = onSendDmWhileWatching,
                onBuyDiamonds = onBuyDiamonds,
                onOpenStreamUserProfile = { },
                onStartDmObservationForPartner = onStartDmObservationForPartner,
                onClearDmThreadFocus = onClearDmThreadFocus,
                onChatSendMessage = onChatSendMessage,
                onChatSendImageMessage = onChatSendImageMessage,
                onChatConsumeImageUploadError = onChatConsumeImageUploadError,
                onChatSendGiftMessage = onChatSendGiftMessage,
                onChatSendGift = onChatSendGift,
                onChatStartCall = onChatStartCall,
                onLiveProfileGiftTargetChanged = onLiveProfileGiftTargetChanged,
                onLikeProfile = onLikeProfile,
                onGiftToProfile = onGiftToProfile,
                onWatchLive = onWatchLive,
                onConfirmPkWatchPerspective = onConfirmPkWatchPerspective,
                onSwipeToAdjacentLive = onSwipeToAdjacentLive,
                onBlockUserFromProfile = onBlockUserFromProfile,
                onReportUserFromProfile = onReportUserFromProfile,
                onProcessLuckyGifts = onProcessLuckyGifts,
                onLuckyGiftVisual = onLuckyGiftVisual,
                onAudioPartyClaimSeat = onAudioPartyClaimSeat,
                onAudioPartyLeaveSeat = onAudioPartyLeaveSeat,
                onAudioPartyHostSeatAction = onAudioPartyHostSeatAction,
                onAudioPartySetMyMic = onAudioPartySetMyMic,
                onAudioPartyMergeNowPlaying = onAudioPartyMergeNowPlaying,
                onAudioPartySendEmoji = onAudioPartySendEmoji,
                onGetAgoraToken = onGetAgoraToken,
                onRequestAudioPartyVideoCallWithHost = {
                    onRequestAudioPartyVideoCallWithHost(liveHostPartner.id)
                },
            )
            if (uiState.showWatchingLiveEndedOverlay) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .zIndex(220f)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.live_stream_ended_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = onStopWatching) {
                            Text(stringResource(R.string.live_stream_ended_ok))
                        }
                    }
                }
            }
        }
    } else {
        var showAudioPartyStartDialog by remember { mutableStateOf(false) }
        var audioPartySeatDraft by remember { mutableIntStateOf(10) }
        /** Matches HomeScreen: cap discover width on tablets / landscape split-screen; gutters via [defaultHorizontalContentPadding]. */
        val discoverHorizontalPadding = widthClass.defaultHorizontalContentPadding()
        val surfaceMain = uiState.liveDiscoverSurface == LiveDiscoverSurface.MAIN
        val audioPartyDiscoverEmpty =
            uiState.liveDiscoverLiveFilter == LiveDiscoverLiveFilter.AUDIO_PARTY_ONLY ||
                uiState.liveDiscoverSurface == LiveDiscoverSurface.AUDIO_PARTY_LOBBY
        BackHandler(enabled = !surfaceMain) {
            onSetLiveDiscoverSurface(LiveDiscoverSurface.MAIN)
            onSetLiveDiscoverLiveFilter(LiveDiscoverLiveFilter.ALL)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = AppResponsiveContentMaxWidth)
                    .fillMaxSize(),
            ) {
            // Static Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = discoverHorizontalPadding, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!surfaceMain) {
                        IconButton(onClick = {
                            onSetLiveDiscoverSurface(LiveDiscoverSurface.MAIN)
                            onSetLiveDiscoverLiveFilter(LiveDiscoverLiveFilter.ALL)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                    Text(
                        if (surfaceMain) stringResource(R.string.live_discover)
                        else stringResource(R.string.live_audio_party_lobby_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                onToggleLive(false)
                            } catch (e: Exception) {
                                Log.e("CRASH_PK", "Fatal error launching Go Live", e)
                                Toast.makeText(context, "Failed to start live. Please try again.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.background(
                            ItzoUiTokens.livePrimaryCtaBrush(),
                            RoundedCornerShape(12.dp),
                        ),
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.live_go_live_video))
                    }
                    Button(
                        onClick = {
                            audioPartySeatDraft = uiState.audioPartySeatCount
                            showAudioPartyStartDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF7C4DFF), Color(0xFFE040FB)),
                            ),
                            RoundedCornerShape(12.dp),
                        ),
                    ) {
                        Icon(Icons.Filled.GraphicEq, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.live_go_live_audio_party))
                    }
                }
            }

            if (surfaceMain) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = discoverHorizontalPadding, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = uiState.liveDiscoverLiveFilter == LiveDiscoverLiveFilter.ALL,
                        onClick = { onSetLiveDiscoverLiveFilter(LiveDiscoverLiveFilter.ALL) },
                        label = { Text(stringResource(R.string.live_discover_filter_all)) },
                    )
                    FilterChip(
                        selected = uiState.liveDiscoverLiveFilter == LiveDiscoverLiveFilter.AUDIO_PARTY_ONLY,
                        onClick = { onSetLiveDiscoverLiveFilter(LiveDiscoverLiveFilter.AUDIO_PARTY_ONLY) },
                        label = { Text(stringResource(R.string.live_discover_filter_audio_party)) },
                    )
                }
            }

            if (showAudioPartyStartDialog) {
                AudioPartyGoLivePickerDialog(
                    seatDraft = audioPartySeatDraft,
                    onSeatDraftChange = { audioPartySeatDraft = it },
                    onDismiss = { showAudioPartyStartDialog = false },
                    onConfirm = {
                        showAudioPartyStartDialog = false
                        try {
                            onAudioPartySeatCountChange(audioPartySeatDraft)
                            onToggleLive(true)
                        } catch (e: Exception) {
                            Log.e("CRASH_PK", "Fatal error starting audio party", e)
                            Toast.makeText(
                                context,
                                "Failed to start audio party. Please try again.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }

            // Scrollable Content Area (pull to bypass Firestore listener cache lag)
            PullToRefreshBox(
                isRefreshing = uiState.isLiveTabRefreshing,
                onRefresh = onRefreshLiveDiscover,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                
                // --- SECTION 1: ONLINE NOW (Stories Style) ---
                if (onlineProfiles.isNotEmpty()) {
                    Row(modifier = Modifier.padding(horizontal = discoverHorizontalPadding, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = Color(0xFF4ADE80), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.live_online_now), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = discoverHorizontalPadding)) {
                        items(onlineProfiles) { profile ->
                            OnlineProfileAvatar(profile = profile, onClick = { onOpenUserProfile(profile) })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // --- SECTION 2: LIVE STREAMS (includes ongoing PK battles for viewers to join) ---
                Column(modifier = Modifier.padding(horizontal = discoverHorizontalPadding, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Podcasts, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.live_streams_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    if (uiState.activeLivePkBanners.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.live_streams_pk_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                if (liveStreamsRowProfiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.LiveTv,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.Gray.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (audioPartyDiscoverEmpty) stringResource(R.string.live_audio_party_filter_empty)
                                else stringResource(R.string.live_no_one_live),
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = discoverHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(liveStreamsRowProfiles, key = { index, p -> p.composeLazyKey(index) }) { _, profile ->
                            LiveProfileCard(
                                profile = profile,
                                pkBannerText = uiState.activeLivePkBanners[profile.id],
                                modifier = Modifier.width(liveCardWidth),
                                onClick = { onWatchLive(profile) }
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(24.dp))

                // --- SECTION 3: NEW ARRIVALS ---
                Row(modifier = Modifier.padding(horizontal = discoverHorizontalPadding, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NewReleases, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.live_new_arrivals), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Column(modifier = Modifier.padding(horizontal = discoverHorizontalPadding)) {
                    val chunkedNew = newProfiles.chunked(2)
                    chunkedNew.forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            rowItems.forEach { profile ->
                                NewProfileCard(
                                    profile = profile,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onOpenUserProfile(profile) }
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Bottom padding for navigation bar overlap
                Spacer(Modifier.height(100.dp))
                }
            }
            }
        }
    }
}

@Composable
fun ProfilePhotoOrPlaceholder(
    photoUrl: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderTint: Color = Color.White.copy(alpha = 0.45f),
) {
    val url = photoUrl?.trim().orEmpty()
    Box(
        modifier = modifier.background(Color(0xFF2A2A35)),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = placeholderTint,
                modifier = Modifier.fillMaxSize(0.45f),
            )
        }
    }
}

@Composable
fun OnlineProfileAvatar(profile: UserProfile, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp)
            .width(72.dp)
    ) {
        Box {
            ProfilePhotoOrPlaceholder(
                photoUrl = profile.photoUrl,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color(0xFF4ADE80), CircleShape),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.BottomEnd)
                    .background(Color(0xFF4ADE80), CircleShape)
                    .border(2.dp, Color.Black, CircleShape)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = profile.name,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun NewProfileCard(profile: UserProfile, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .height(200.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ProfilePhotoOrPlaceholder(
                photoUrl = profile.photoUrl,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = 200f
                        )
                    )
            )
            Surface(
                color = Color(0xFFFFD700),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.live_new_badge),
                    color = Color.Black, 
                    fontSize = 10.sp, 
                    fontWeight = FontWeight.Bold, 
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(profile.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                Text("${profile.age} • ${profile.city}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LiveStreamingView(
    uiState: DatingUiState,
    isGuestPreview: Boolean = false,
    onGuestRestricted: () -> Unit = {},
    onEndLive: () -> Unit,
    onGoBattle: () -> Unit,
    onSendComment: (String) -> Unit,
    onStartPk: (durationSeconds: Int) -> Unit,
    onInviteFollowerPk: () -> Unit = {},
    onRetryPk: (durationSeconds: Int) -> Unit,
    onCancelPk: () -> Unit,
    onPkBattleTimerFinished: () -> Unit = {},
    onPkRematchChallenge: () -> Unit = {},
    onPkFindAnotherOpponent: () -> Unit = {},
    onPkOpponentPeerDisconnected: () -> Unit = {},
    onRecordStreamingMinutes: (Int) -> Unit = {},
    onHostStreamReady: () -> Unit = {},
    onKickLiveChatter: (streamDocId: String, targetUserId: String) -> Unit = { _, _ -> },
    onBlockLiveChatter: (streamDocId: String, hostUid: String, targetUserId: String) -> Unit = { _, _, _ -> },
    onFollowLiveChatter: (targetUserId: String, displayNameHint: String) -> Unit = { _, _ -> },
    onLikeLiveChatter: (targetUserId: String, displayNameHint: String) -> Unit = { _, _ -> },
    onPrivateGiftToChatter: (UserProfile, Gift, Int) -> Unit = { _, _, _ -> },
    /** While hosting live PK in Compose, gift to host or guest battler by Firebase uid. */
    onSendPkBattlerGift: (String, Gift, Int) -> Unit = { _, _, _ -> },
    onVideoCallChatter: (UserProfile) -> Unit = {},
    onMessageChatter: (UserProfile) -> Unit = {},
    onTopUpCoins: () -> Unit = {},
    onBuyDiamonds: (CoinPackage) -> Unit = {},
    /** Follow/unfollow from host “watching now” sheet. */
    onFollow: (UserProfile) -> Unit = {},
    /** PK: tap opponent name/pill to open their profile (Home sheet). */
    onOpenPkOpponentProfile: (UserProfile) -> Unit = {},
    onProcessLuckyGifts: suspend (String, Int, Int) -> LuckyGiftsOutcome,
    onLuckyGiftVisual: (Gift, String, Int, String?) -> Unit = { _, _, _, _ -> },
    onAudioPartySeatCountChange: (Int) -> Unit = {},
    onAudioPartyClaimSeat: (String, Int) -> Unit = { _, _ -> },
    onAudioPartyLeaveSeat: (String, Int) -> Unit = { _, _ -> },
    onAudioPartyHostSeatAction: (String, AudioPartyHostSeatAction) -> Unit = { _, _ -> },
    onAudioPartySetMyMic: (String, Boolean) -> Unit = { _, _ -> },
    onAudioPartyMergeNowPlaying: (String, String) -> Unit = { _, _ -> },
    onAudioPartySendEmoji: (String) -> Unit = { },
    onAudioPartyRoomBackgroundChange: (String) -> Unit = {},
    onGetAgoraToken: suspend (channelName: String, isPublisher: Boolean) -> String?,
    onHostRespondAudioPartyVideoCall: (String, Boolean) -> Unit = { _, _ -> },
    /** While hosting solo / audio-party live: send gifts that animate in-room (same as viewer gifts). */
    onSendHostStreamGift: (Gift, Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val hostDeviceSupportsSnapAr = remember(context.applicationContext) {
        isDeviceCapableOfAR(context.applicationContext)
    }
    val pkFailToast = stringResource(R.string.live_pk_failed_toast)
    val defaultPkSearchLabel = stringResource(R.string.live_pk_short)
    val videoStreamPermissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    )
    val audioPartyMicPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val hostStreamMediaReady =
        if (uiState.isAudioPartyLive) {
            audioPartyMicPermission.status == PermissionStatus.Granted
        } else {
            videoStreamPermissions.allPermissionsGranted
        }
    var launchedStreamPermissionPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!launchedStreamPermissionPrompt) {
            launchedStreamPermissionPrompt = true
            if (uiState.isAudioPartyLive) {
                if (audioPartyMicPermission.status != PermissionStatus.Granted) {
                    audioPartyMicPermission.launchPermissionRequest()
                }
            } else if (!videoStreamPermissions.allPermissionsGranted) {
                videoStreamPermissions.launchMultiplePermissionRequest()
            }
        }
    }

    val localRenderer = remember { SurfaceViewRenderer(context) }
    val dummyRenderer = remember { SurfaceViewRenderer(context) }
    val pkChallengerRemoteRenderer = remember {
        SurfaceViewRenderer(context).also { v -> v.setZOrderMediaOverlay(false) }
    }
    val dummyPkChallengerLocal = remember { SurfaceViewRenderer(context) }
    val pkChallengerConn = remember { LiveHostPkChallengerConnHolder() }
    val hostPkScope = rememberCoroutineScope()
    var pkChallengerJoinGen by remember { mutableIntStateOf(0) }
    var webRTCManager by remember { mutableStateOf<WebRTCManager?>(null) }
    /** PK arena: co-host engine when using Agora instead of WebRTC challenger link. */
    var hostPkAgoraEngine by remember { mutableStateOf<com.zipper.datingapp.agora.AgoraManager?>(null) }
    /** Agora RTC for audio-party host (same channel as Firestore `live_streams` host uid). */
    var hostAudioPartyAgora by remember { mutableStateOf<com.zipper.datingapp.agora.AgoraManager?>(null) }
    var hostPkFlipNonce by remember { mutableIntStateOf(0) }

    /** True when the app is built with a non-blank [BuildConfig.AGORA_APP_ID]. */
    val liveUsesAgora = remember { BuildConfig.AGORA_APP_ID.isNotBlank() }

    /** Mirrors inline PK arena gate ([usePkSplitLayout]) needed before solo-WebRTC [LaunchedEffect]. */
    val liveHostPkSplitLayoutEarly =
        uiState.isPkSearching ||
            (
                !uiState.pkRoomId.isNullOrBlank() &&
                    !uiState.showHostPkEndedInterstitial &&
                    when (val s = uiState.pkBattleSession) {
                        null -> true
                        else -> !s.battleEnded && s.phase == PkBattlePhase.ACTIVE
                    }
            )

    val hostShowAgoraSoloLive =
        liveUsesAgora && !uiState.isAudioPartyLive && !liveHostPkSplitLayoutEarly

    /** Itzo-style audio party over Agora (when app id configured); otherwise WebRTC audio-only. */
    val hostShowAgoraAudioParty = liveUsesAgora && uiState.isAudioPartyLive

    val hostAudioPartyAgoraRef = rememberUpdatedState(hostAudioPartyAgora)
    val pickAudioPartyMixLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val mgr = hostAudioPartyAgoraRef.value
            if (mgr == null) return@rememberLauncherForActivityResult
            val path = copyStreamUriToTempAudioFile(context, uri)
            if (path == null) {
                Toast.makeText(
                    context,
                    context.getString(R.string.live_audio_party_bgm_copy_failed),
                    Toast.LENGTH_SHORT,
                ).show()
                return@rememberLauncherForActivityResult
            }
            val code = mgr.startAudioMixingFromFile(path, loopback = false, cycle = -1)
            if (code != 0) {
                Toast.makeText(
                    context,
                    context.getString(R.string.live_audio_party_bgm_start_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
    )

    LaunchedEffect(uiState.isLive, uiState.isAudioPartyLive, liveHostPkSplitLayoutEarly, liveUsesAgora) {
        val soloVideoRequiresAgoraConfig =
            uiState.isLive &&
                !uiState.isAudioPartyLive &&
                !liveHostPkSplitLayoutEarly &&
                !liveUsesAgora
        if (soloVideoRequiresAgoraConfig) {
            Toast.makeText(
                context,
                context.getString(R.string.live_video_requires_agora),
                Toast.LENGTH_LONG,
            ).show()
            onEndLive()
        }
    }

    /**
     * PK split layout and [LiveStreamActivity] PK/challenger flows still use WebRTC.
     * Solo camera live uses Agora when [liveUsesAgora]. Audio party uses Agora when [liveUsesAgora], else WebRTC audio-only.
     */
    var chatMessage by remember { mutableStateOf("") }
    var moderationTarget by remember { mutableStateOf<LiveStreamChatMessage?>(null) }
    val streamDocId = uiState.currentRoomId ?: uiState.currentUser?.id
    val streamHostUserId = uiState.currentUser?.id
    val hostAudioPartyCo = remember { LiveHostAudioPartyCoHolder() }
    val hostApCoLocal = remember {
        List(14) {
            SurfaceViewRenderer(context).also { v ->
                runCatching { v.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL) }
            }
        }
    }
    val hostApCoRemote = remember {
        List(14) {
            SurfaceViewRenderer(context).also { v ->
                runCatching { v.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL) }
            }
        }
    }
    var showArFiltersSheet by remember { mutableStateOf(false) }
    var showPkToolsSheet by remember { mutableStateOf(false) }
    var selectedFilterType by remember { mutableStateOf(FilterType.NONE) }
    var snapLenses by remember { mutableStateOf<List<LensesComponent.Lens>>(emptyList()) }
    var selectedSnapLensId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(hostDeviceSupportsSnapAr) {
        if (!hostDeviceSupportsSnapAr) selectedSnapLensId = null
    }
    var lastPkClickMs by remember { mutableStateOf(0L) }
    var showPkMatchOptions by remember { mutableStateOf(false) }
    var pkRoundDurationSec by remember { mutableIntStateOf(300) }
    val pkMatchSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var chatterSheetProfile by remember { mutableStateOf<UserProfile?>(null) }
    var showChatterGiftSheet by remember { mutableStateOf(false) }
    var chatterGiftTarget by remember { mutableStateOf<UserProfile?>(null) }
    var showEndLiveConfirm by remember { mutableStateOf(false) }
    /** Solo live: tap viewer count on nameplate to open audience list (PK hides counts; no sheet there). */
    var hostSoloViewerListSheet by remember { mutableStateOf(false) }
    var showHostApStreamMenu by remember { mutableStateOf(false) }
    var showHostApGiftSheet by remember { mutableStateOf(false) }
    var showHostApVideoCallRequestsSheet by remember { mutableStateOf(false) }
    var showHostApThemePicker by remember { mutableStateOf(false) }
    /** PK: tap host/challenger chip in bottom panel to open that side’s spectator list. */
    var hostPkBattlerViewerSheet by remember { mutableStateOf<Pair<List<String>, String>?>(null) }
    var hostStreamTimerTick by remember { mutableIntStateOf(0) }
    var showHostPkGiftSheet by remember { mutableStateOf(false) }
    var hostPkMicOn by remember { mutableStateOf(true) }
    var hostPkSpeakerOn by remember { mutableStateOf(true) }
    /** Derived: beauty is considered "on" for any non-NONE filter. */
    val hostBeautyOn = selectedFilterType != FilterType.NONE
    var sessionStartMs by remember { mutableLongStateOf(0L) }
    var sessionReported by remember { mutableStateOf(false) }
    var prevShowEndLiveConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(showEndLiveConfirm) {
        if (prevShowEndLiveConfirm && !showEndLiveConfirm) {
            delay(200)
            WebRTCManager.activeManager()?.refreshVideoSinkAttachments("live_host_end_confirm_dialog_closed")
        }
        prevShowEndLiveConfirm = showEndLiveConfirm
    }

    LaunchedEffect(uiState.isLive) {
        if (!uiState.isLive) return@LaunchedEffect
        while (isActive) {
            delay(1000)
            hostStreamTimerTick++
        }
    }

    LaunchedEffect(
        uiState.currentUser?.id,
        hostStreamMediaReady,
        uiState.isAudioPartyLive,
        liveHostPkSplitLayoutEarly,
        hostShowAgoraSoloLive,
        liveUsesAgora,
        uiState.soloLiveMediaHandoffNotBeforeElapsedMs,
    ) {
        val hostId = uiState.currentUser?.id?.trim().orEmpty()
        if (hostShowAgoraSoloLive) return@LaunchedEffect
        if (liveUsesAgora && uiState.isAudioPartyLive) return@LaunchedEffect
        if (liveUsesAgora && liveHostPkSplitLayoutEarly) return@LaunchedEffect
        if (!uiState.isAudioPartyLive && !liveHostPkSplitLayoutEarly && !liveUsesAgora) {
            return@LaunchedEffect
        }
        if (hostId.isEmpty() || webRTCManager != null) return@LaunchedEffect
        if (!hostStreamMediaReady) {
            Log.w(
                "LiveStreamingView",
                "WebRTC host start blocked until permissions granted (audioParty=${uiState.isAudioPartyLive})"
            )
            return@LaunchedEffect
        }
        val notBefore = uiState.soloLiveMediaHandoffNotBeforeElapsedMs
        if (notBefore > 0L) {
            val msWait = notBefore - SystemClock.elapsedRealtime()
            if (msWait > 0) delay(msWait)
        }
        Log.e("E2E_DIAG_LIVE", "Live Stream - isHost: true, exact RoomID: $hostId, ViewerID: null")
        try {
            webRTCManager = WebRTCManager(
                context = context,
                roomId = hostId,
                localView = localRenderer,
                remoteView = dummyRenderer,
                broadcastMode = true,
                audioOnly = uiState.isAudioPartyLive,
                useSnapCameraKitPipeline = hostDeviceSupportsSnapAr && !uiState.isAudioPartyLive,
            )
            webRTCManager?.startCall()
        } catch (e: Exception) {
            Log.e("CRASH_PK", "Fatal error launching Live Stream (host) roomId=$hostId", e)
            Toast.makeText(context, "Failed to start live stream. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(streamHostUserId, uiState.isAudioPartyLive, webRTCManager) {
        val hostId = streamHostUserId?.trim().orEmpty()
        if (hostId.isEmpty() || !uiState.isAudioPartyLive || webRTCManager == null) {
            hostAudioPartyCo.clear()
            return@LaunchedEffect
        }
        snapshotFlow {
            uiState.audioPartyStream.activeCoPublisherUids(hostId).sorted()
        }
            .distinctUntilChanged()
            .collectLatest { uids ->
                hostAudioPartyCo.clear()
                delay(400)
                val primary = webRTCManager ?: return@collectLatest
                for ((idx, coUid) in uids.withIndex()) {
                    if (idx >= hostApCoLocal.size) break
                    var started = false
                    for (attempt in 0 until 60) {
                        if (!isActive) return@collectLatest
                        val ex = primary.tryExportFactoryForSecondaryViewer()
                        if (ex != null) {
                            try {
                                val mgr = WebRTCManager(
                                    context = context,
                                    roomId = hostId,
                                    localView = hostApCoLocal[idx],
                                    remoteView = hostApCoRemote[idx],
                                    broadcastMode = true,
                                    viewerSignalingId = hostId,
                                    broadcastViewerReceiveAudio = true,
                                    broadcastLivePublishersSessionId = hostId,
                                    broadcastLivePublishersPublisherUid = coUid,
                                    audioOnly = true,
                                    injectedPeerConnectionFactory = ex.first,
                                    injectedEglBase = ex.second,
                                    publishGlobalMediaStreams = false,
                                    takeCurrentManagerSlot = false,
                                )
                                hostAudioPartyCo.coSubs.add(mgr)
                                mgr.joinCall()
                                mgr.setRemoteAudioHearEnabled(hostPkSpeakerOn)
                                started = true
                            } catch (e: Exception) {
                                Log.e("LiveScreen", "LiveStreamingView host audio_party co uid=$coUid", e)
                            }
                            break
                        }
                        delay(100)
                    }
                    if (!started) {
                        Log.w("LiveScreen", "LiveStreamingView host audio_party co not started uid=$coUid")
                    }
                }
                delay(650)
                hostAudioPartyCo.coSubs.forEach {
                    it.refreshVideoSinkAttachments("live_host_ap_co_post_join")
                }
            }
    }

    LaunchedEffect(hostPkSpeakerOn) {
        hostAudioPartyCo.coSubs.forEach { it.setRemoteAudioHearEnabled(hostPkSpeakerOn) }
    }

    LaunchedEffect(uiState.hostWebRtcPauseForPkNonce) {
        if (uiState.hostWebRtcPauseForPkNonce > 0) {
            webRTCManager?.pauseCamera()
        }
    }
    LaunchedEffect(uiState.hostWebRtcResumeAfterPkNonce) {
        if (uiState.hostWebRtcResumeAfterPkNonce > 0) {
            webRTCManager?.resumeCamera()
        }
    }

    /** Resume sheet after private call: keep broadcast capture paused until Continue / End live confirms. */
    LaunchedEffect(uiState.showPostPrivateCallResumeChoice, webRTCManager) {
        if (uiState.showPostPrivateCallResumeChoice) {
            webRTCManager?.let { mgr -> runCatching { mgr.pauseCamera() } }
        }
    }

    LaunchedEffect(showHostPkGiftSheet, uiState.pkBattleSession) {
        if (showHostPkGiftSheet) {
            val s = uiState.pkBattleSession
            if (s == null || s.hostUserId.isBlank() || s.guestUserId.isBlank()) {
                showHostPkGiftSheet = false
            }
        }
    }

    LaunchedEffect(webRTCManager) {
        if (webRTCManager != null && sessionStartMs == 0L) {
            sessionStartMs = System.currentTimeMillis()
        }
    }

    LaunchedEffect(webRTCManager) {
        if (webRTCManager != null) {
            delay(1200)
            onHostStreamReady()
        }
    }

    DisposableEffect(showArFiltersSheet, webRTCManager, hostDeviceSupportsSnapAr) {
        if (!showArFiltersSheet) {
            snapLenses = emptyList()
            return@DisposableEffect onDispose { }
        }
        val mgr = webRTCManager
        val gid = BuildConfig.SNAP_LENS_GROUP_ID.trim()
        if (mgr == null || !hostDeviceSupportsSnapAr || !BuildConfig.SNAP_CAMERA_KIT_CONFIGURED || gid.isEmpty()) {
            return@DisposableEffect onDispose { }
        }
        val sub = mgr.observeSnapLenses(gid) { snapLenses = it }
        onDispose { sub?.close() }
    }

    // Sync beauty and/or Snap portal lens with WebRTC
    LaunchedEffect(selectedFilterType, selectedSnapLensId, webRTCManager, hostDeviceSupportsSnapAr) {
        val mgr = webRTCManager ?: return@LaunchedEffect
        val gid = BuildConfig.SNAP_LENS_GROUP_ID.trim()
        try {
            if (hostDeviceSupportsSnapAr && BuildConfig.SNAP_CAMERA_KIT_CONFIGURED && selectedSnapLensId != null && gid.isNotEmpty()) {
                mgr.applySnapLensFromPortal(
                    groupId = gid,
                    lensId = selectedSnapLensId!!,
                    onLoading = {
                        Toast.makeText(
                            context,
                            context.getString(R.string.snap_lens_downloading_filter),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onError = {
                        Toast.makeText(
                            context,
                            context.getString(R.string.snap_lens_failed_filter),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            } else {
                mgr.setFilterType(selectedFilterType)
            }
        } catch (e: Throwable) {
            Log.e("FILTER_CRASH", "Filter failed", e)
            Toast.makeText(context, "Filter not supported on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    fun reportSessionOnce() {
        if (sessionReported || sessionStartMs == 0L) return
        sessionReported = true
        val mins = ((System.currentTimeMillis() - sessionStartMs) / 60_000L).toInt().coerceAtLeast(1)
        onRecordStreamingMinutes(mins)
    }

    DisposableEffect(Unit) {
        onDispose {
            reportSessionOnce()
            hostAudioPartyCo.clear()
            pkChallengerConn.mgr?.onDestroy()
            pkChallengerConn.mgr = null
            webRTCManager?.onDestroy()
        }
    }

    fun resolveChatterProfile(msg: LiveStreamChatMessage): UserProfile {
        val id = msg.senderId.trim()
        return uiState.profiles.find { it.id == id }
            ?: uiState.filteredProfiles.find { it.id == id }
            ?: UserProfile(id = id, name = msg.senderName.ifBlank { "User" }, profileNumber = "", photoUrl = "")
    }

    fun senderAvatarUrl(uid: String): String? =
        uiState.profiles.find { it.id == uid }?.photoUrl?.takeIf { it.isNotBlank() }
            ?: uiState.filteredProfiles.find { it.id == uid }?.photoUrl?.takeIf { it.isNotBlank() }

    // Two-tile PK arena only while searching, connecting (room set, session not mirrored yet), or an *active* round.
    // After a round ends (battleEnded / not ACTIVE), stay full-bleed solo even if pkRoomId lags until endPkSession clears it.
    val usePkSplitLayout = uiState.isPkSearching || (
        !uiState.pkRoomId.isNullOrBlank() &&
            !uiState.showHostPkEndedInterstitial &&
            when (val s = uiState.pkBattleSession) {
                null -> true
                else -> !s.battleEnded && s.phase == PkBattlePhase.ACTIVE
            }
        )
    val pkBattleUiActive = usePkSplitLayout && (uiState.pkBattleSession?.pkStartTimeMillis ?: 0L) > 0L
    /** Solo camera / mic (non–audio-party, non-PK-flow): Itzo-style backdrop + top/bottom chrome. */
    val itzoSoloLiveVideoChrome = !uiState.isAudioPartyLive && !usePkSplitLayout
    val pkSplitArenaFraction = if (pkBattleUiActive) 0.55f else 0.5f
    val pkSplitPanelFraction = if (pkBattleUiActive) 0.45f else 0.5f
    val pkBattleJustEndedHostSolo =
        uiState.pkBattleSession?.let { s ->
            s.battleEnded && !usePkSplitLayout
        } == true
    LaunchedEffect(pkBattleJustEndedHostSolo, uiState.pkBattleSession?.pkStartTimeMillis) {
        if (!pkBattleJustEndedHostSolo) return@LaunchedEffect
        delay(320)
        webRTCManager?.refreshVideoSinkAttachments("live_host_post_pk_battle_solo")
    }

    /** PK host (Compose live): post-result linger expired with no rematch/find tap → end live / return toward home. */
    LaunchedEffect(
        uiState.pkBattleSession?.battleEnded,
        uiState.pkBattleSession?.outcome,
        uiState.pkBattleSession?.resultPhaseEndsAtMillis,
        uiState.pkBattleSession?.pkStartTimeMillis,
        uiState.currentUser?.id,
    ) {
        val pk = uiState.pkBattleSession ?: return@LaunchedEffect
        val hostUid = uiState.currentUser?.id?.trim().orEmpty()
        if (!pk.battleEnded || pk.outcome == null || hostUid.isEmpty() || pk.hostUserId != hostUid) {
            return@LaunchedEffect
        }
        val ends = pk.resultPhaseEndsAtMillis
        if (ends <= 0L) return@LaunchedEffect
        delay((ends - System.currentTimeMillis()).coerceAtLeast(0L))
        onEndLive()
    }

    val arenaStatusFallback =
        if (uiState.isPkSearching) defaultPkSearchLabel else "Connecting…"
    val hostStreamIdForPk = streamDocId?.trim().orEmpty()
    val challengerUidForPk = uiState.activeCallPartner?.id?.trim().orEmpty()
    val lifecycleOwnerStreaming = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwnerStreaming, hostStreamIdForPk) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                webRTCManager?.refreshVideoSinkAttachments("live_host_streaming_resume")
                pkChallengerConn.mgr?.refreshVideoSinkAttachments("live_host_pk_challenger_resume")
                hostAudioPartyCo.coSubs.forEach {
                    it.refreshVideoSinkAttachments("live_host_ap_co_resume")
                }
            }
        }
        lifecycleOwnerStreaming.lifecycle.addObserver(observer)
        onDispose { lifecycleOwnerStreaming.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(hostStreamIdForPk, challengerUidForPk, uiState.isPkSearching, webRTCManager, liveUsesAgora) {
        if (liveUsesAgora) {
            return@DisposableEffect onDispose { }
        }
        val primary = webRTCManager
        val job = hostPkScope.launch {
            if (hostStreamIdForPk.isEmpty() ||
                challengerUidForPk.isEmpty() ||
                uiState.isPkSearching ||
                primary == null
            ) {
                return@launch
            }
            for (attempt in 0 until 80) {
                if (!isActive) return@launch
                val ex = primary.tryExportFactoryForSecondaryViewer()
                if (ex != null) {
                    try {
                        val guest = WebRTCManager(
                            context = context,
                            roomId = hostStreamIdForPk,
                            localView = dummyPkChallengerLocal,
                            remoteView = pkChallengerRemoteRenderer,
                            onPeerDisconnected = onPkOpponentPeerDisconnected,
                            broadcastMode = true,
                            viewerSignalingId = hostStreamIdForPk,
                            broadcastViewerReceiveAudio = false,
                            broadcastLivePublishersSessionId = hostStreamIdForPk,
                            broadcastLivePublishersPublisherUid = challengerUidForPk,
                            injectedPeerConnectionFactory = ex.first,
                            injectedEglBase = ex.second,
                            publishGlobalMediaStreams = false,
                            takeCurrentManagerSlot = false
                        )
                        pkChallengerConn.mgr = guest
                        guest.joinCall()
                        pkChallengerJoinGen++
                        Log.d(
                            "LiveStreamingView",
                            "PK challenger viewer live_publishers/$hostStreamIdForPk/$challengerUidForPk"
                        )
                    } catch (e: Exception) {
                        Log.e("LiveStreamingView", "PK challenger WebRTC", e)
                    }
                    return@launch
                }
                delay(100)
            }
            Log.w("LiveStreamingView", "PK challenger WebRTC factory export timeout")
        }
        onDispose {
            job.cancel()
            pkChallengerConn.mgr?.onDestroy()
            pkChallengerConn.mgr = null
            pkChallengerJoinGen = 0
        }
    }

    LaunchedEffect(pkChallengerJoinGen) {
        if (pkChallengerJoinGen > 0) {
            delay(650)
            pkChallengerConn.mgr?.refreshVideoSinkAttachments("live_host_pk_challenger_post_join")
        }
    }

    val soloLiveStage = !usePkSplitLayout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                when {
                    !soloLiveStage -> Modifier.background(Color.Black)
                    uiState.isAudioPartyLive -> Modifier
                    else -> Modifier.background(ItzoUiTokens.AppBackgroundDeep)
                },
            )
    ) {
        if (soloLiveStage) {
            when {
                itzoSoloLiveVideoChrome -> {
                    ItzoSoloLiveBackdrop(isHost = true, modifier = Modifier.fillMaxSize())
                }
                uiState.isAudioPartyLive -> {
                    AudioPartyFullBleedBackdrop(
                        roomBackgroundKey = uiState.audioPartyStream.roomBackgroundKey,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(ItzoUiTokens.AudioPartyStageVeil),
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ItzoUiTokens.stageBackgroundBrush()),
                    )
                }
            }
        }
        // Hidden 1px surface — keeps dummyRenderer (passed as remoteView to WebRTCManager)
        // attached to a window so WebRTC's post() target is always valid on the host side.
        AndroidView(
            factory = { dummyRenderer },
            modifier = Modifier
                .size(1.dp)
                .align(Alignment.TopStart)
        )
        // Host camera: solo = full screen; PK arena = same Row + overlays as spectator LiveWatcherView.
        if (usePkSplitLayout) {
            val hostDisplayName = uiState.currentUser?.name?.trim()?.ifBlank { null } ?: "You"
            val challengerDisplayName =
                uiState.activeCallPartner?.name?.trim()?.takeIf { it.isNotBlank() } ?: "Challenger"
            fun opponentProfileForPkTap(): UserProfile {
                val partner = uiState.activeCallPartner
                if (partner != null) return partner
                val guestUid = uiState.pkBattleSession?.guestUserId?.trim().orEmpty()
                    .ifBlank { challengerUidForPk }
                return UserProfile(
                    id = guestUid,
                    name = challengerDisplayName.trim().ifBlank { "Challenger" }
                )
            }
            val pkSession = uiState.pkBattleSession
            hostStreamTimerTick
            val pkEndOverlay =
                pkSession != null && pkSession.battleEnded && pkSession.outcome != null &&
                    (pkSession.resultPhaseEndsAtMillis <= 0L ||
                        System.currentTimeMillis() < pkSession.resultPhaseEndsAtMillis)
            val hostUid = uiState.currentUser?.id?.trim().orEmpty()
            val showHostPkRematch =
                pkEndOverlay &&
                    hostUid.isNotEmpty() &&
                    pkSession?.hostUserId == hostUid
            val pkMvpUrls =
                if (pkSession != null && pkEndOverlay) {
                    pkMvpSupporterPhotoUrls(
                        messages = uiState.liveStreamChatMessages,
                        hostUserId = pkSession.hostUserId,
                        senderPhotoLookup = { uid ->
                            uiState.profiles.find { it.id == uid }?.photoUrl?.takeIf { it.isNotBlank() }
                                ?: uiState.filteredProfiles.find { it.id == uid }?.photoUrl?.takeIf { it.isNotBlank() }
                        },
                        giftPriceLookup = { id ->
                            uiState.availableGifts.find { it.id == id }?.price?.coerceAtLeast(0) ?: 0
                        },
                    )
                } else {
                    emptyList()
                }
            val hostPillGender = uiState.currentUser?.gender.orEmpty()
            val challengerPillGender = uiState.activeCallPartner?.gender.orEmpty()
            val pkHudHostGender = pkSession?.hostUserId?.let { resolvePkBattlerGender(it, uiState) }.orEmpty()
            val pkHudGuestGender = pkSession?.guestUserId?.let { resolvePkBattlerGender(it, uiState) }.orEmpty()
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(pkSplitArenaFraction)
            ) {
                val pkHudNamesOnArena = pkSession?.let { it.pkStartTimeMillis > 0L && !pkEndOverlay } == true
                if (liveUsesAgora) {
                    val pkAgoraChannel =
                        uiState.pkRoomId?.trim().orEmpty().ifBlank { streamHostUserId.orEmpty() }
                    val oppForAgora =
                        challengerUidForPk.ifBlank { uiState.pkBattleSession?.guestUserId?.trim().orEmpty() }
                    if (pkAgoraChannel.isNotBlank()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .zIndex(0f),
                        ) {
                            AgoraPkBattlerSplit(
                                channelName = pkAgoraChannel,
                                myFirebaseUid = streamHostUserId.orEmpty(),
                                opponentFirebaseUid = oppForAgora,
                                onGetAgoraToken = onGetAgoraToken,
                                onAgoraEngineChange = { hostPkAgoraEngine = it },
                                onJoinedChannel = {
                                    if (sessionStartMs == 0L) {
                                        sessionStartMs = System.currentTimeMillis()
                                    }
                                },
                                onHostStreamReady = onHostStreamReady,
                                hostMicUnmuted = hostPkMicOn,
                                flipCameraNonce = hostPkFlipNonce,
                                modifier = Modifier.fillMaxSize(),
                            )
                            val pkGuestUidFromSessionA =
                                uiState.pkBattleSession?.guestUserId?.trim().orEmpty()
                            val challengerSlotFilledA =
                                challengerUidForPk.isNotBlank() || pkGuestUidFromSessionA.isNotBlank()
                            if (uiState.isPkSearching || !challengerSlotFilledA) {
                                Row(Modifier.fillMaxSize()) {
                                    Spacer(Modifier.weight(1f))
                                    Box(
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .background(Color(0xFF151515)),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(36.dp),
                                                color = Color(0xFFFFC107),
                                                strokeWidth = 3.dp,
                                            )
                                            Spacer(Modifier.height(12.dp))
                                            Text(
                                                text = uiState.pkStatusText.orEmpty().ifBlank { arenaStatusFallback },
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                            }
                            if (!pkHudNamesOnArena) {
                                Box(Modifier.fillMaxSize()) {
                                    PkSpectatorVideoNamePill(
                                        name = hostDisplayName,
                                        alignment = Alignment.TopStart,
                                        gender = hostPillGender,
                                        extraTopInset = 54.dp,
                                    )
                                    PkSpectatorVideoNamePill(
                                        name = challengerDisplayName,
                                        alignment = Alignment.TopEnd,
                                        gender = challengerPillGender,
                                        extraTopInset = 64.dp,
                                        onClick =
                                            { onOpenPkOpponentProfile(opponentProfileForPkTap()) },
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .zIndex(0f),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                AndroidView(
                    factory = { dummyPkChallengerLocal },
                    modifier = Modifier
                        .size(1.dp)
                        .align(Alignment.TopStart)
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(0f)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .zIndex(1f)
                    ) {
                        AndroidView(
                            factory = { localRenderer },
                            modifier = Modifier.fillMaxSize()
                        )
                        if (hostBeautyOn) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.White.copy(alpha = 0.09f))
                            )
                        }
                        if (!pkHudNamesOnArena) {
                            PkSpectatorVideoNamePill(
                                name = hostDisplayName,
                                alignment = Alignment.TopStart,
                                gender = hostPillGender,
                                extraTopInset = 54.dp,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .zIndex(1f)
                    ) {
                        AndroidView(
                            factory = { pkChallengerRemoteRenderer },
                            modifier = Modifier.fillMaxSize()
                        )
                        val pkGuestUidFromSession =
                            uiState.pkBattleSession?.guestUserId?.trim().orEmpty()
                        val challengerSlotFilled =
                            challengerUidForPk.isNotBlank() || pkGuestUidFromSession.isNotBlank()
                        if (uiState.isPkSearching || !challengerSlotFilled) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color(0xFF151515))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(36.dp),
                                        color = Color(0xFFFFC107),
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = uiState.pkStatusText.orEmpty().ifBlank { arenaStatusFallback },
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        if (!pkHudNamesOnArena) {
                            PkSpectatorVideoNamePill(
                                name = challengerDisplayName,
                                alignment = Alignment.TopEnd,
                                gender = challengerPillGender,
                                extraTopInset = 64.dp,
                                onClick = { onOpenPkOpponentProfile(opponentProfileForPkTap()) }
                            )
                        }
                    }
                }
                }
                pkSession?.let { pk ->
                    if (pk.pkStartTimeMillis > 0L) {
                        if (!pkEndOverlay) {
                            val myUidPk = uiState.currentUser?.id?.trim().orEmpty()
                            val partnerPk = uiState.activeCallPartner
                            fun pkNameForUid(uid: String): String {
                                if (uid.isBlank()) return "—"
                                if (uid == myUidPk) {
                                    return uiState.currentUser?.name?.trim()?.takeIf { it.isNotBlank() }
                                        ?: hostDisplayName
                                }
                                if (partnerPk != null && uid == partnerPk.id.trim()) {
                                    return partnerPk.name.trim().ifBlank { challengerDisplayName }
                                }
                                return uiState.profiles.find { it.id == uid }?.name?.trim()
                                    ?.takeIf { it.isNotBlank() }
                                    ?: uiState.filteredProfiles.find { it.id == uid }?.name?.trim()
                                        ?.takeIf { it.isNotBlank() }
                                    ?: challengerDisplayName
                            }
                            fun pkLevelForUid(uid: String): Int {
                                if (uid.isBlank()) return 1
                                if (uid == myUidPk) {
                                    return uiState.currentUser?.level?.coerceAtLeast(1) ?: 1
                                }
                                if (partnerPk != null && uid == partnerPk.id.trim()) {
                                    return partnerPk.level.coerceAtLeast(1)
                                }
                                return uiState.profiles.find { it.id == uid }?.level?.coerceAtLeast(1)
                                    ?: uiState.filteredProfiles.find { it.id == uid }?.level?.coerceAtLeast(1)
                                    ?: 1
                            }
                            val hostSideName = pkNameForUid(pk.hostUserId.trim())
                            val guestSideName = pkNameForUid(pk.guestUserId.trim())
                            val hostSideLevel = pkLevelForUid(pk.hostUserId.trim())
                            val guestSideLevel = pkLevelForUid(pk.guestUserId.trim())
                            // Left tile is always the live broadcaster (PK host stream); right is remote guest.
                            val hostOnLeftPk = true
                            PkArenaSpectatorBattleHud(
                                session = pk,
                                modifier = Modifier
                                    .matchParentSize()
                                    .zIndex(4f),
                                hostGender = pkHudHostGender,
                                guestGender = pkHudGuestGender,
                                onTimerFinished = onPkBattleTimerFinished,
                                hostDisplayName = hostSideName,
                                guestDisplayName = guestSideName,
                                hostLevel = hostSideLevel,
                                guestLevel = guestSideLevel,
                                hostOnLeft = hostOnLeftPk,
                                onHostProfileClick = if (
                                    myUidPk.isNotEmpty() && pk.hostUserId != myUidPk
                                ) {
                                    {
                                        val p = uiState.activeCallPartner?.takeIf { it.id == pk.hostUserId }
                                            ?: UserProfile(id = pk.hostUserId, name = hostSideName)
                                        onOpenPkOpponentProfile(p)
                                    }
                                } else {
                                    null
                                },
                                onGuestProfileClick = if (
                                    myUidPk.isNotEmpty() && pk.guestUserId != myUidPk
                                ) {
                                    {
                                        val p = uiState.activeCallPartner?.takeIf { it.id == pk.guestUserId }
                                            ?: UserProfile(id = pk.guestUserId, name = guestSideName)
                                        onOpenPkOpponentProfile(p)
                                    }
                                } else {
                                    null
                                },
                                liveChatMessages = uiState.liveStreamChatMessages,
                                liveChatSenderPhotoLookup = { uid ->
                                    uiState.profiles.find { it.id == uid }?.photoUrl?.takeIf { it.isNotBlank() }
                                        ?: uiState.filteredProfiles.find { it.id == uid }?.photoUrl?.takeIf {
                                            it.isNotBlank()
                                        }
                                },
                                giftPriceLookup = { id ->
                                    uiState.availableGifts.find { it.id == id }?.price?.coerceAtLeast(0) ?: 0
                                },
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .zIndex(8f)
                            ) {
                                PkArenaEndCard(
                                    session = pk,
                                    modifier = Modifier.fillMaxSize(),
                                    showRematchForHost = showHostPkRematch,
                                    showFindAnotherForHost = showHostPkRematch,
                                    rematchPending = uiState.pkRematchPending,
                                    rematchWaitingLabel = stringResource(R.string.pk_rematch_waiting_guest),
                                    onRematchChallenge = onPkRematchChallenge,
                                    onFindAnotherOpponent = onPkFindAnotherOpponent,
                                    mvpViewerPhotoUrls = pkMvpUrls
                                )
                            }
                        }
                    }
                }
                if (pkBattleUiActive) {
                    IconButton(
                        onClick = { showEndLiveConfirm = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 4.dp, end = 8.dp)
                            .zIndex(24f)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.live_leave_stream_cd),
                            tint = Color.White
                        )
                    }
                }
            }
        } else {
            if (hostShowAgoraSoloLive) {
                val hostFbUid = uiState.currentUser?.id?.trim().orEmpty()
                if (hostFbUid.isNotEmpty()) {
                    var agoraToken by remember(hostFbUid) { mutableStateOf<String?>(null) }
                    var isFetchingToken by remember(hostFbUid) { mutableStateOf(true) }
                    LaunchedEffect(hostFbUid, uiState.soloLiveMediaHandoffNotBeforeElapsedMs) {
                        val notBefore = uiState.soloLiveMediaHandoffNotBeforeElapsedMs
                        if (notBefore > 0L) {
                            val msWait = notBefore - SystemClock.elapsedRealtime()
                            if (msWait > 0) delay(msWait)
                        }
                        isFetchingToken = true
                        agoraToken =
                            try {
                                withTimeout(20_000L) {
                                    onGetAgoraToken(hostFbUid, true)
                                }
                            } catch (_: TimeoutCancellationException) {
                                Log.w("LiveScreen", "getAgoraToken host timeout channel=$hostFbUid")
                                null
                            } finally {
                                isFetchingToken = false
                            }
                    }
                    if (isFetchingToken) {
                        Box(
                            modifier = Modifier.align(Alignment.Center).fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        AgoraLiveScreen(
                            channelId = hostFbUid,
                            firebaseUid = hostFbUid,
                            isBroadcaster = true,
                            token = agoraToken,
                            localVideoScale = AgoraVideoScaleMode.Hidden,
                            remoteVideoScale = AgoraVideoScaleMode.Hidden,
                            modifier = Modifier.align(Alignment.Center).fillMaxSize(),
                            onJoinedChannel = {
                                if (sessionStartMs == 0L) {
                                    sessionStartMs = System.currentTimeMillis()
                                }
                            },
                            onHostStreamReady = onHostStreamReady,
                        )
                    }
                }
            } else if (hostShowAgoraAudioParty) {
                val hostFbUid = uiState.currentUser?.id?.trim().orEmpty()
                if (hostFbUid.isNotEmpty()) {
                    var agoraApToken by remember(hostFbUid) { mutableStateOf<String?>(null) }
                    var fetchingAp by remember(hostFbUid) { mutableStateOf(true) }
                    LaunchedEffect(hostFbUid) {
                        fetchingAp = true
                        agoraApToken =
                            try {
                                withTimeout(20_000L) {
                                    onGetAgoraToken(hostFbUid, true)
                                }
                            } catch (_: TimeoutCancellationException) {
                                Log.w("LiveScreen", "getAgoraToken audio party host timeout channel=$hostFbUid")
                                null
                            } finally {
                                fetchingAp = false
                            }
                    }
                    if (fetchingAp) {
                        Box(
                            modifier = Modifier.align(Alignment.Center).fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (agoraApToken != null) {
                        AgoraAudioPartyRtcLayer(
                            channelId = hostFbUid,
                            firebaseUid = hostFbUid,
                            joinAsBroadcaster = true,
                            micPermissionGranted = audioPartyMicPermission.status == PermissionStatus.Granted,
                            token = agoraApToken,
                            signalHostStreamReady = true,
                            onEngineReady = { hostAudioPartyAgora = it },
                            onJoinedChannel = {
                                if (sessionStartMs == 0L) {
                                    sessionStartMs = System.currentTimeMillis()
                                }
                            },
                            onHostStreamReady = onHostStreamReady,
                            modifier = Modifier.align(Alignment.TopStart),
                        )
                    }
                }
            } else if (uiState.isAudioPartyLive) {
                AndroidView(
                    factory = { localRenderer },
                    modifier = Modifier
                        .size(1.dp)
                        .align(Alignment.TopStart)
                )
            } else {
                AndroidView(
                    factory = { localRenderer },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize()
                )
                if (hostBeautyOn) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.09f))
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .then(
                    if (usePkSplitLayout) {
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(pkSplitPanelFraction)
                    } else {
                        Modifier.fillMaxSize()
                    }
                )
                /** PK neon chat applies its own [navigationBarsPadding] on the composer; outer pad here would combine oddly with weighted fills. */
                .then(if (!pkBattleUiActive) Modifier.navigationBarsPadding() else Modifier)
                .imePadding()
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = if (itzoSoloLiveVideoChrome || uiState.isAudioPartyLive) 0.dp else 8.dp,
                )
        ) {
            if (!pkBattleUiActive) {
                val hostStreamDurationLabel = run {
                    hostStreamTimerTick
                    val startedMs = when {
                        uiState.watchingLiveStreamStartedAtMillis > 0L -> uiState.watchingLiveStreamStartedAtMillis
                        sessionStartMs > 0L -> sessionStartMs
                        else -> 0L
                    }
                    if (startedMs <= 0L) ""
                    else {
                        val elapsed = ((System.currentTimeMillis() - startedMs) / 1000L).toInt().coerceAtLeast(0)
                        stringResource(R.string.live_stream_duration_chip, formatLiveStreamDurationClock(elapsed))
                    }
                }
                if (itzoSoloLiveVideoChrome) {
                    val me = uiState.currentUser
                    ItzoSoloLiveHostTopChrome(
                        modifier = Modifier.statusBarsPadding(),
                        photoUrl = me?.photoUrl,
                        displayName = me?.name?.trim()?.ifBlank { "You" } ?: "You",
                        profileNumber = me?.profileNumber.orEmpty(),
                        viewerCount = uiState.liveStreamViewerCount,
                        viewerUserIds = uiState.liveAudienceViewerUserIds,
                        lookupPhotoUrl = { uid -> senderAvatarUrl(uid) },
                        beansBalance = me?.beans?.takeIf { it >= 0 } ?: uiState.beans,
                        streamTimerLabel = hostStreamDurationLabel,
                        onHostPillClick = {},
                        onViewersClick = { hostSoloViewerListSheet = true },
                        onWalletClick = { onTopUpCoins() },
                        onCloseClick = { showEndLiveConfirm = true },
                    )
                } else if (uiState.isAudioPartyLive) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val meAp = uiState.currentUser
                        ItzoSoloLiveHostTopChrome(
                            modifier = Modifier.statusBarsPadding(),
                            photoUrl = meAp?.photoUrl,
                            displayName = meAp?.name?.trim()?.ifBlank { "You" } ?: "You",
                            profileNumber = meAp?.profileNumber.orEmpty(),
                            viewerCount = uiState.liveStreamViewerCount,
                            viewerUserIds = uiState.liveAudienceViewerUserIds,
                            lookupPhotoUrl = { uid -> senderAvatarUrl(uid) },
                            beansBalance = meAp?.beans?.takeIf { it >= 0 } ?: uiState.beans,
                            streamTimerLabel = hostStreamDurationLabel,
                            onHostPillClick = {},
                            onViewersClick = { hostSoloViewerListSheet = true },
                            onWalletClick = { onTopUpCoins() },
                            onCloseClick = { showEndLiveConfirm = true },
                            onStreamMenuClick = { showHostApStreamMenu = true },
                        )
                        if (!liveUsesAgora) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                color = Color(0xFF2A1F35),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(
                                    stringResource(R.string.live_audio_party_webrtc_notice),
                                    modifier = Modifier.padding(12.dp),
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Surface(color = ItzoUiTokens.LiveHostNameplateScrim, shape = RoundedCornerShape(20.dp)) {
                            Row(modifier = Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(modifier = Modifier.size(32.dp), shape = CircleShape, color = Color.Gray) {
                                    AsyncImage(model = uiState.currentUser?.photoUrl, contentDescription = null, contentScale = ContentScale.Crop)
                                }
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(uiState.currentUser?.name ?: "You", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        if (uiState.currentUser?.hasBlueTick == true) {
                                            Spacer(Modifier.width(4.dp))
                                            VerifiedBadge(size = 12.dp)
                                        }
                                    }
                                    uiState.currentUser?.let { current ->
                                        Spacer(Modifier.height(2.dp))
                                        GenderAgeBadge(
                                            gender = current.genderText,
                                            age = current.currentAge
                                        )
                                    }
                                    Text(
                                        stringResource(R.string.live_viewers_count, uiState.liveStreamViewerCount),
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { hostSoloViewerListSheet = true }
                                    )
                                    if (hostStreamDurationLabel.isNotBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Text(
                                                "●",
                                                color = Color(0xFF4ADE80),
                                                fontSize = 8.sp,
                                                modifier = Modifier.padding(end = 4.dp)
                                            )
                                            Text(
                                                text = hostStreamDurationLabel,
                                                color = Color.White.copy(alpha = 0.85f),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = { showEndLiveConfirm = true },
                            modifier = if (uiState.isAudioPartyLive) {
                                Modifier
                                    .background(Color.Black.copy(alpha = 0.38f), CircleShape)
                            } else {
                                Modifier
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }

            if (!hostStreamMediaReady) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = Color(0xFFB71C1C).copy(alpha = 0.92f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (uiState.isAudioPartyLive) {
                                stringResource(R.string.live_host_permission_audio_only)
                            } else {
                                stringResource(R.string.live_host_permission_video)
                            },
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                if (uiState.isAudioPartyLive) {
                                    audioPartyMicPermission.launchPermissionRequest()
                                } else {
                                    videoStreamPermissions.launchMultiplePermissionRequest()
                                }
                            }
                        ) {
                            Text("Allow", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val myUidForMod = FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotEmpty() }
                // Streamer: `type=gift` rows on this stream are turned into uiState.playingGift in DatingViewModel.
                moderationTarget?.let { target ->
                    AlertDialog(
                        onDismissRequest = { moderationTarget = null },
                        title = {
                            Text(stringResource(R.string.live_manage_user_title, target.senderName.ifBlank { target.senderId }))
                        },
                        text = {
                            Column {
                                TextButton(
                                    onClick = {
                                        val sid = streamDocId
                                        if (!sid.isNullOrBlank()) onKickLiveChatter(sid, target.senderId)
                                        moderationTarget = null
                                    }
                                ) {
                                    Text(stringResource(R.string.live_action_kick))
                                }
                                TextButton(
                                    onClick = {
                                        val sid = streamDocId
                                        val host = streamHostUserId
                                        if (!sid.isNullOrBlank() && !host.isNullOrBlank()) {
                                            onBlockLiveChatter(sid, host, target.senderId)
                                        }
                                        moderationTarget = null
                                    }
                                ) {
                                    Text(stringResource(R.string.live_action_block))
                                }
                                TextButton(
                                    onClick = {
                                        onFollowLiveChatter(target.senderId, target.senderName)
                                        moderationTarget = null
                                    }
                                ) {
                                    Text(stringResource(R.string.live_action_follow_profile))
                                }
                                TextButton(
                                    onClick = {
                                        onLikeLiveChatter(target.senderId, target.senderName)
                                        moderationTarget = null
                                    }
                                ) {
                                    Text(stringResource(R.string.live_action_like_profile))
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { moderationTarget = null }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                    )
                }
                if (pkBattleUiActive) {
                    val pkHostChipLabel =
                        stringResource(R.string.live_pk_spectators_chip_host, uiState.pkHostViewerCount)
                    val pkGuestChipLabel =
                        stringResource(R.string.live_pk_spectators_chip_challenger, uiState.pkGuestViewerCount)
                    WebRtcOverlayBottomChat(
                        messages = uiState.liveStreamChatMessages,
                        onSendMessage = onSendComment,
                        endCallLabel = stringResource(R.string.call_end_stream),
                        onEndCall = null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        isMicOn = hostPkMicOn,
                        isSpeakerOn = hostPkSpeakerOn,
                        onToggleMic = { enabled ->
                            hostPkMicOn = enabled
                            hostPkAgoraEngine?.muteLocalAudioStream(!enabled)
                            webRTCManager?.setAudioEnabled(enabled)
                        },
                        onToggleSpeaker = { enabled ->
                            hostPkSpeakerOn = enabled
                            runCatching {
                                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                WebRTCManager.applyLivePlaybackSpeakerMode(
                                    am,
                                    preferLoudSpeaker = enabled,
                                )
                            }
                        },
                        pkNeonBattleChrome = true,
                        pkNeonStreamerSide = true,
                        audiencePanelFillHeight = true,
                        isVideoCall = true,
                        onFlipCamera = {
                            if (hostPkAgoraEngine != null) {
                                hostPkFlipNonce++
                            } else {
                                runCatching { webRTCManager?.switchCamera() }
                                    .onFailure { Log.w("LiveStreamingView", "switchCamera failed", it) }
                            }
                        },
                        flipCameraEnabled = webRTCManager != null || hostPkAgoraEngine != null,
                        onOpenGiftSheet = { showHostPkGiftSheet = true },
                        giftActionInProgress = uiState.isGiftTransactionProcessing,
                        liveStreamDocIdForModeration = streamDocId,
                        streamHostUserIdForModeration = streamHostUserId,
                        currentUserIdForModeration = myUidForMod,
                        onKickLiveChatter = { tid ->
                            val sid = streamDocId
                            if (!sid.isNullOrBlank()) onKickLiveChatter(sid, tid)
                        },
                        onBlockLiveChatter = { tid ->
                            val sid = streamDocId
                            val host = streamHostUserId
                            if (!sid.isNullOrBlank() && !host.isNullOrBlank()) {
                                onBlockLiveChatter(sid, host, tid)
                            }
                        },
                        onFollowLiveChatter = onFollowLiveChatter,
                        onLikeLiveChatter = onLikeLiveChatter,
                        senderPhotoLookup = { uid -> senderAvatarUrl(uid) },
                        onGiftRowClick = { m ->
                            if (m.senderId.isNotBlank() && m.senderId != streamHostUserId) {
                                chatterSheetProfile = resolveChatterProfile(m)
                            }
                        },
                        pkStreamerPanelViewersLabel = null,
                        onPkStreamerPanelClose = null,
                        pkBottomViewerChipLabel = null,
                        onPkBottomViewerChipClick = null,
                        pkHostSpectatorsLabel = pkHostChipLabel,
                        pkGuestSpectatorsLabel = pkGuestChipLabel,
                        onPkHostSpectatorsClick = {
                            hostPkBattlerViewerSheet = uiState.pkHostViewerUserIds to pkHostChipLabel
                        },
                        onPkGuestSpectatorsClick = {
                            hostPkBattlerViewerSheet = uiState.pkGuestViewerUserIds to pkGuestChipLabel
                        },
                        pkAllSpectatorsSummaryLine = null,
                        onPkAllSpectatorsClick = null,
                        onOpenDiamondShop = null,
                        onOpenArFilters = null,
                        onOpenPkToolsMenu = { showPkToolsSheet = true },
                        showComplianceBanner = true,
                        skipNavigationBarsInset = false,
                    )
                } else {
                val hostSoloChatScroll = rememberScrollState()
                val audioPartyHostStageScroll = rememberScrollState()
                var soloHostStagingHistoryMode by remember { mutableStateOf(false) }
                var soloHostHistoryBaselineCount by remember { mutableIntStateOf(0) }
                val soloHostLiveChatCountRef =
                    rememberUpdatedState(uiState.liveStreamChatMessages.size)
                val soloHostStagingHistoryRef = rememberUpdatedState(soloHostStagingHistoryMode)
                val openSoloHostStagingHistory = rememberUpdatedState({
                    soloHostHistoryBaselineCount = soloHostLiveChatCountRef.value
                    soloHostStagingHistoryMode = true
                })
                val soloHostChatJumpScope = rememberCoroutineScope()
                val revealSoloHostStagingOverscroll = remember {
                    object : NestedScrollConnection {
                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            if (!soloHostStagingHistoryRef.value && kotlin.math.abs(available.y) > 18f) {
                                openSoloHostStagingHistory.value.invoke()
                            }
                            return Offset.Zero
                        }
                    }
                }
                val hostMessagesNewestFirst = remember(uiState.liveStreamChatMessages) {
                    uiState.liveStreamChatMessages.asReversed()
                }
                /** Solo host: no TTL eviction — staging shows last [LIVE_CHAT_STAGING_VISIBLE_MESSAGES] only;
                 *  TTL caused bubbles to disappear after a few seconds (viewer path differs). */
                val hostMessagesToShow = remember(hostMessagesNewestFirst) {
                    hostMessagesNewestFirst
                }
                val hostMessagesChronological = remember(hostMessagesToShow) {
                    hostMessagesToShow.asReversed()
                }
                val hostStagingDisplayedMessages = remember(
                    hostMessagesChronological,
                    soloHostStagingHistoryMode,
                ) {
                    if (soloHostStagingHistoryMode) hostMessagesChronological
                    else hostMessagesChronological.takeLast(LIVE_CHAT_STAGING_VISIBLE_MESSAGES)
                }
                LaunchedEffect(hostSoloChatScroll) {
                    snapshotFlow {
                        hostSoloChatScroll.maxValue > 0 && hostSoloChatScroll.value < hostSoloChatScroll.maxValue - 8
                    }
                        .distinctUntilChanged()
                        .collect { scrolledAway ->
                            if (scrolledAway) {
                                openSoloHostStagingHistory.value.invoke()
                            } else {
                                val countAtSchedule = hostMessagesToShow.size
                                // 7-second grace period before snapping back to newest messages
                                delay(7_000L)
                                // Layout/list growth during delay can falsely read “at bottom”; keep history if chat grew.
                                if (hostMessagesToShow.size > countAtSchedule) return@collect
                                val stillAway =
                                    hostSoloChatScroll.maxValue > 0 && hostSoloChatScroll.value < hostSoloChatScroll.maxValue - 8
                                if (!stillAway) {
                                    soloHostStagingHistoryMode = false
                                    soloHostHistoryBaselineCount = soloHostLiveChatCountRef.value
                                    hostSoloChatScroll.scrollTo(hostSoloChatScroll.maxValue)
                                }
                            }
                        }
                }
                val hostSoloFeedKey = hostMessagesNewestFirst.firstOrNull()?.let { m ->
                    "${m.id}_${m.timestamp}_${m.text.length}"
                }.orEmpty()
                LaunchedEffect(hostSoloFeedKey, hostMessagesToShow.size, soloHostStagingHistoryMode) {
                    if (soloHostStagingHistoryMode) return@LaunchedEffect
                    val atBottom =
                        hostSoloChatScroll.maxValue <= 0 ||
                            hostSoloChatScroll.value >= hostSoloChatScroll.maxValue - 8
                    if (atBottom && hostSoloFeedKey.isNotBlank()) {
                        rollerAnimateScrollToLatest(hostSoloChatScroll)
                    }
                }
                val historyStripMaxH =
                    liveChatHistoryStripMaxHeight(LocalConfiguration.current.screenHeightDp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        if (uiState.isAudioPartyLive) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(audioPartyHostStageScroll),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    val me = uiState.currentUser
                                    AudioPartyStageLayout(
                                        seatCount = uiState.audioPartySeatCount,
                                        stream = uiState.audioPartyStream,
                                        hostUserId = streamHostUserId.orEmpty(),
                                        myUserId = me?.id.orEmpty(),
                                        isHost = true,
                                        title = "",
                                        subtitle = "",
                                        hostDisplayName = me?.name.orEmpty(),
                                        hostPhotoUrl = me?.photoUrl.orEmpty(),
                                        hostProfileNumber = me?.profileNumber.orEmpty(),
                                        hostBeans = me?.beans ?: uiState.beans,
                                        hostHasBlueTick = me?.hasBlueTick == true,
                                        showSeatCountControls = false,
                                        onSeatCountSelected = onAudioPartySeatCountChange,
                                        onHostSeatAction = { action ->
                                            val h = streamHostUserId.orEmpty()
                                            if (h.isNotEmpty()) onAudioPartyHostSeatAction(h, action)
                                        },
                                        onMergeNowPlaying = onAudioPartyMergeNowPlaying,
                                        onSendStageEmoji = onAudioPartySendEmoji,
                                    )
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        if (!soloHostStagingHistoryMode &&
                            hostMessagesToShow.size > LIVE_CHAT_STAGING_VISIBLE_MESSAGES
                        ) {
                            LiveChatOlderMessagesCue(onOpenHistory = { openSoloHostStagingHistory.value.invoke() })
                        }
                        val hostStagingMaxHeight =
                            if (soloHostStagingHistoryMode) historyStripMaxH else liveChatStagingViewportMaxHeight()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .nestedScroll(revealSoloHostStagingOverscroll)
                        ) {
                            if (soloHostStagingHistoryMode &&
                                hostMessagesToShow.size > soloHostHistoryBaselineCount
                            ) {
                                LiveChatNewMessagesCue(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 6.dp, bottom = 6.dp)
                                        .zIndex(1f),
                                    onJumpToLatest = {
                                        soloHostStagingHistoryMode = false
                                        soloHostHistoryBaselineCount = hostMessagesToShow.size
                                        soloHostChatJumpScope.launch {
                                            rollerAnimateScrollToLatest(hostSoloChatScroll)
                                        }
                                    },
                                )
                            }
                            // Top-down order: chronological list is oldest→newest so the newest line sits just above the footer.
                            // heightIn before verticalScroll caps viewport height; short transcripts shrink (no empty band).
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = hostStagingMaxHeight)
                                    .verticalScroll(hostSoloChatScroll)
                                    .padding(horizontal = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                horizontalAlignment = Alignment.Start,
                            ) {
                        if (uiState.isAudioPartyLive) {
                            AudioPartyWelcomeBanner(
                                text = stringResource(R.string.live_audio_party_room_welcome),
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                            AudioPartyGiftFlyline(
                                messages = uiState.liveStreamChatMessages,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        hostStagingDisplayedMessages.forEach { m ->
                            if (uiState.isAudioPartyLive && m.type == "gift") return@forEach
                            key(m.id.ifBlank { "${m.timestamp}_${m.senderId}" }) {
                        val canMod = !streamDocId.isNullOrBlank() &&
                            !streamHostUserId.isNullOrBlank() &&
                            !m.isLiveSystemMessage() &&
                            m.type != "gift" &&
                            m.senderId.isNotBlank() &&
                            m.senderId != streamHostUserId
                        when {
                            m.isLiveSystemMessage() -> {
                                val isJoin = m.text.contains("joined", ignoreCase = true)
                                Surface(
                                    color = if (isJoin) Color(0xFF4ADE80).copy(alpha = 0.18f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .padding(start = 6.dp, top = 0.dp, end = 6.dp, bottom = 0.dp)
                                        .then(
                                            if (isJoin && m.senderId.isNotBlank() && m.senderId != streamHostUserId)
                                                Modifier.clickable { moderationTarget = m }
                                            else Modifier
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isJoin) {
                                            Icon(
                                                Icons.Default.Login,
                                                contentDescription = null,
                                                tint = Color(0xFF4ADE80),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            LiveChatLevelBadge(level = resolveChatterProfile(m).level)
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = m.senderName.ifBlank { "Someone" },
                                                color = Color(0xFF4ADE80),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Text(
                                                text = " joined",
                                                color = Color.White.copy(alpha = 0.7f),
                                                fontSize = 12.sp
                                            )
                                        } else {
                                            Text(
                                                text = m.text,
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 11.sp,
                                                fontStyle = FontStyle.Italic,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            m.type == "gift" -> {
                                LiveStreamGiftChatRow(
                                    message = m,
                                    senderPhotoUrl = senderAvatarUrl(m.senderId),
                                    modifier = Modifier.padding(top = 2.dp, bottom = 0.dp),
                                    senderDisplayLevel = resolveChatterProfile(m).level.coerceAtLeast(1),
                                    onClick = {
                                        if (m.senderId.isNotBlank() && m.senderId != streamHostUserId) {
                                            chatterSheetProfile = resolveChatterProfile(m)
                                        }
                                    }
                                )
                            }
                            else -> {
                                if (uiState.isAudioPartyLive) {
                                    LiveChatRowAudioParty(
                                        message = m,
                                        isMine = !streamHostUserId.isNullOrBlank() && m.senderId == streamHostUserId,
                                        senderLevel = resolveChatterProfile(m).level.coerceAtLeast(1),
                                        modifier = Modifier.padding(top = 2.dp, bottom = 0.dp),
                                        onClick = {
                                            if (canMod) moderationTarget = m
                                            else if (m.senderId.isNotBlank() && m.senderId != streamHostUserId) {
                                                chatterSheetProfile = resolveChatterProfile(m)
                                            }
                                        },
                                    )
                                } else {
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .padding(top = 2.dp, bottom = 0.dp)
                                            .then(
                                                if (canMod) Modifier.clickable { moderationTarget = m }
                                                else Modifier.clickable {
                                                    if (m.senderId.isNotBlank() && m.senderId != streamHostUserId) {
                                                        chatterSheetProfile = resolveChatterProfile(m)
                                                    }
                                                }
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            LiveChatLevelBadge(level = resolveChatterProfile(m).level)
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "${m.senderName}: ${m.text}",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                            }
                        }
                        }
                        }
                        if (itzoSoloLiveVideoChrome) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (!uiState.isAudioPartyLive) {
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(end = 6.dp),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        ItzoSoloHostSideActionRail(
                                            isPkSearching = uiState.isPkSearching,
                                            isPkRetry = uiState.pkRetryAvailable,
                                            onPkActionClick = {
                                                val now = SystemClock.elapsedRealtime()
                                                if (now - lastPkClickMs >= 1000L) {
                                                    lastPkClickMs = now
                                                    try {
                                                        when {
                                                            uiState.isPkSearching -> onCancelPk()
                                                            uiState.pkRetryAvailable -> onRetryPk(pkRoundDurationSec)
                                                            else -> showPkMatchOptions = true
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("CRASH_PK", "Fatal error launching PK Battle", e)
                                                        Toast.makeText(context, pkFailToast, Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            },
                                            onPkToolsMenu = { showPkToolsSheet = true },
                                            onGamePlaceholderClick = {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.itzo_solo_party_games_toast),
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            },
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    LiveStreamComplianceBanner(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 2.dp),
                                        variant = LiveStreamComplianceBannerVariant.DockedAboveComposer,
                                    )
                                }
                                ItzoSoloHostBottomActionRow(
                                    chatMessage = chatMessage,
                                    onChatMessageChange = { chatMessage = it },
                                    onSendChat = {
                                        if (chatMessage.isNotBlank()) {
                                            onSendComment(chatMessage)
                                            chatMessage = ""
                                        }
                                    },
                                    hostPkMicOn = hostPkMicOn,
                                    onToggleHostMic = {
                                        hostPkMicOn = !hostPkMicOn
                                        webRTCManager?.setAudioEnabled(hostPkMicOn)
                                        hostAudioPartyAgora?.muteLocalAudioStream(!hostPkMicOn)
                                    },
                                    canToggleMic = webRTCManager != null || hostAudioPartyAgora != null,
                                    onFlipCamera = {
                                        runCatching { webRTCManager?.switchCamera() }
                                            .onFailure { Log.w("LiveStreamingView", "switchCamera failed", it) }
                                    },
                                    canFlipCamera = webRTCManager != null,
                                    onVideoPlaceholderClick = {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.itzo_solo_extra_video_rooms_toast),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    },
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (!uiState.isAudioPartyLive) {
                                        LiveStreamComplianceBanner(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 2.dp),
                                            variant = LiveStreamComplianceBannerVariant.DockedAboveComposer,
                                        )
                                    }
                                    OutlinedTextField(
                                        value = chatMessage,
                                        onValueChange = { chatMessage = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp, max = 56.dp),
                                        placeholder = {
                                            Box(
                                                modifier = Modifier.fillMaxWidth(),
                                                contentAlignment = Alignment.CenterStart,
                                            ) {
                                                Text(
                                                    stringResource(R.string.live_chat_hint),
                                                    color = Color.White.copy(alpha = 0.6f),
                                                    fontSize = 15.sp,
                                                    lineHeight = 20.sp,
                                                )
                                            }
                                        },
                                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp, lineHeight = 20.sp),
                                        shape = RoundedCornerShape(26.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color.Black.copy(alpha = 0.4f),
                                            unfocusedContainerColor = Color.Black.copy(alpha = 0.4f),
                                            focusedBorderColor = Color.White.copy(alpha = 0.5f),
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                        ),
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                if (chatMessage.isNotBlank()) {
                                                    onSendComment(chatMessage)
                                                    chatMessage = ""
                                                }
                                            }) {
                                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.live_send), tint = Color.White, modifier = Modifier.size(20.dp))
                                            }
                                        },
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                        keyboardActions = KeyboardActions(onSend = {
                                            if (chatMessage.isNotBlank()) {
                                                onSendComment(chatMessage)
                                                chatMessage = ""
                                            }
                                        })
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                if (uiState.isAudioPartyLive) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 2.dp),
                                    ) {
                                        IconButton(
                                            onClick = {
                                                hostPkMicOn = !hostPkMicOn
                                                webRTCManager?.setAudioEnabled(hostPkMicOn)
                                                hostAudioPartyAgora?.muteLocalAudioStream(!hostPkMicOn)
                                            },
                                            enabled = webRTCManager != null || hostAudioPartyAgora != null,
                                            modifier = Modifier
                                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                                .size(48.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (hostPkMicOn) Icons.Default.Mic else Icons.Default.MicOff,
                                                contentDescription = stringResource(
                                                    if (hostPkMicOn) {
                                                        R.string.live_host_mic_on_cd
                                                    } else {
                                                        R.string.live_host_mic_muted_cd
                                                    },
                                                ),
                                                tint = if (hostPkMicOn) Color.White else Color(0xFFFF5252),
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = { showHostApGiftSheet = true },
                                            modifier = Modifier
                                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                                .size(48.dp),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_audio_party_gift_rich),
                                                contentDescription = stringResource(R.string.live_send_gift_action),
                                                tint = Color(0xFFFFE082),
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = { showHostApStreamMenu = true },
                                            modifier = Modifier
                                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                                .size(48.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Apps,
                                                contentDescription = stringResource(R.string.live_audio_party_stream_menu_cd),
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                        if (hostShowAgoraAudioParty) {
                                            IconButton(
                                                onClick = {
                                                    pickAudioPartyMixLauncher.launch(arrayOf("audio/*"))
                                                },
                                                enabled = hostAudioPartyAgora != null,
                                                modifier = Modifier
                                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                                    .size(48.dp),
                                            ) {
                                                Icon(
                                                    Icons.Filled.MusicNote,
                                                    contentDescription = stringResource(R.string.live_audio_party_bgm_pick_cd),
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                            IconButton(
                                                onClick = { hostAudioPartyAgora?.stopAudioMixing() },
                                                enabled = hostAudioPartyAgora != null,
                                                modifier = Modifier
                                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                                    .size(48.dp),
                                            ) {
                                                Icon(
                                                    Icons.Default.Stop,
                                                    contentDescription = stringResource(R.string.live_audio_party_bgm_stop_cd),
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = {
                                            hostPkMicOn = !hostPkMicOn
                                            webRTCManager?.setAudioEnabled(hostPkMicOn)
                                            hostAudioPartyAgora?.muteLocalAudioStream(!hostPkMicOn)
                                        },
                                        enabled = webRTCManager != null || hostAudioPartyAgora != null,
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                            .size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (hostPkMicOn) Icons.Default.Mic else Icons.Default.MicOff,
                                            contentDescription = stringResource(
                                                if (hostPkMicOn) {
                                                    R.string.live_host_mic_on_cd
                                                } else {
                                                    R.string.live_host_mic_muted_cd
                                                }
                                            ),
                                            tint = if (hostPkMicOn) Color.White else Color(0xFFFF5252),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    if (hostShowAgoraAudioParty) {
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = {
                                                    pickAudioPartyMixLauncher.launch(arrayOf("audio/*"))
                                                },
                                                enabled = hostAudioPartyAgora != null,
                                                modifier = Modifier
                                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                                    .size(48.dp),
                                            ) {
                                                Icon(
                                                    Icons.Filled.MusicNote,
                                                    contentDescription = stringResource(R.string.live_audio_party_bgm_pick_cd),
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                            IconButton(
                                                onClick = { hostAudioPartyAgora?.stopAudioMixing() },
                                                enabled = hostAudioPartyAgora != null,
                                                modifier = Modifier
                                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                                    .size(48.dp),
                                            ) {
                                                Icon(
                                                    Icons.Default.Stop,
                                                    contentDescription = stringResource(R.string.live_audio_party_bgm_stop_cd),
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                        }
                                    }
                                    if (!uiState.isAudioPartyLive) {
                                        Spacer(Modifier.height(8.dp))
                                        val isSearching = uiState.isPkSearching
                                        val isRetry = uiState.pkRetryAvailable
                                        val pkColor = when {
                                            isSearching -> Color(0xFF6D4C41)
                                            isRetry -> Color(0xFFFF9800)
                                            else -> Color(0xFFFFC107)
                                        }
                                        Button(
                                            enabled = true,
                                            onClick = {
                                                val now = SystemClock.elapsedRealtime()
                                                if (now - lastPkClickMs < 1000L) return@Button
                                                lastPkClickMs = now
                                                try {
                                                    when {
                                                        isSearching -> onCancelPk()
                                                        isRetry -> onRetryPk(pkRoundDurationSec)
                                                        else -> showPkMatchOptions = true
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("CRASH_PK", "Fatal error launching PK Battle", e)
                                                    Toast.makeText(context, pkFailToast, Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = pkColor),
                                            shape = CircleShape,
                                            modifier = Modifier.size(56.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            when {
                                                isSearching -> CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp,
                                                    color = Color.White
                                                )
                                                isRetry -> Text(stringResource(R.string.live_pk_retry), fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 10.sp)
                                                else -> Text(stringResource(R.string.live_pk_short), fontWeight = FontWeight.Bold, color = Color.Black)
                                            }
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        IconButton(
                                            onClick = {
                                                runCatching { webRTCManager?.switchCamera() }
                                                    .onFailure { Log.w("LiveStreamingView", "switchCamera failed", it) }
                                            },
                                            enabled = webRTCManager != null,
                                            modifier = Modifier
                                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                                .size(48.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.FlipCameraAndroid,
                                                contentDescription = "Flip camera",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }

        if (showPkMatchOptions) {
            ModalBottomSheet(
                onDismissRequest = { showPkMatchOptions = false },
                sheetState = pkMatchSheetState,
                containerColor = Color(0xFF1B1B24),
                contentColor = Color.White,
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.pk_matchmaking_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.pk_round_length_label),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = pkRoundDurationSec == 180,
                            onClick = { pkRoundDurationSec = 180 },
                            label = { Text(stringResource(R.string.pk_duration_3_min)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = pkRoundDurationSec == 300,
                            onClick = { pkRoundDurationSec = 300 },
                            label = { Text(stringResource(R.string.pk_duration_5_min)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            showPkMatchOptions = false
                            onStartPk(pkRoundDurationSec)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            stringResource(R.string.pk_match_randomly),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            showPkMatchOptions = false
                            onInviteFollowerPk()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
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

        if (showArFiltersSheet) {
            ArFilterBottomSheet(
                selectedFilter = selectedFilterType,
                snapLenses = snapLenses,
                selectedSnapLensId = selectedSnapLensId,
                snapCameraKitUiEnabled = hostDeviceSupportsSnapAr,
                onDismiss = { showArFiltersSheet = false },
                onFilterSelected = { filter ->
                    showArFiltersSheet = false
                    selectedSnapLensId = null
                    selectedFilterType = filter
                },
                onSnapLensSelected = { lens ->
                    showArFiltersSheet = false
                    if (lens != null) {
                        selectedSnapLensId = lens.id
                        selectedFilterType = FilterType.NONE
                    } else {
                        selectedSnapLensId = null
                    }
                },
            )
        }

        if (showPkToolsSheet) {
            PkToolsMenuSheet(
                onDismiss = { showPkToolsSheet = false },
                onDiamondStore = { onTopUpCoins() },
                onFaceFilters = null,
            )
        }

        if (showHostPkGiftSheet) {
            val pkS = uiState.pkBattleSession
            if (pkS != null) {
                val hostFallbackPk = stringResource(R.string.live_pk_battler_host_fallback)
                val guestFallbackPk = stringResource(R.string.live_pk_battler_guest_fallback)
                val hostUidPk = pkS.hostUserId.trim()
                val guestUidPk = pkS.guestUserId.trim()
                val hostNPk = when {
                    hostUidPk == uiState.currentUser?.id -> uiState.currentUser?.name?.trim().orEmpty()
                    hostUidPk == uiState.activeCallPartner?.id -> uiState.activeCallPartner?.name?.trim().orEmpty()
                    else -> ""
                }.ifBlank { hostFallbackPk }
                val guestNPk = when {
                    guestUidPk == uiState.currentUser?.id -> uiState.currentUser?.name?.trim().orEmpty()
                    guestUidPk == uiState.activeCallPartner?.id -> uiState.activeCallPartner?.name?.trim().orEmpty()
                    else -> ""
                }.ifBlank { guestFallbackPk }
                val recipsPk = listOf(hostUidPk to hostNPk, guestUidPk to guestNPk)
                    .filter { it.first.isNotEmpty() }
                    .distinctBy { it.first }
                if (recipsPk.isNotEmpty()) {
                    GiftSelectionSheet(
                        gifts = uiState.availableGifts,
                        coinBalance = uiState.coinBalance,
                        isProcessing = uiState.isGiftTransactionProcessing,
                        onTopUpCoins = onTopUpCoins,
                        onDismiss = { showHostPkGiftSheet = false },
                        onSend = { _, _ -> },
                        pkGiftScoreHint = stringResource(R.string.live_pk_gift_counts_for_score),
                        pkBattlerRecipients = recipsPk,
                        onSendGiftToPkBattler = { uid, gift, count ->
                            onSendPkBattlerGift(uid, gift, count)
                            showHostPkGiftSheet = false
                        },
                        onLuckySend = onProcessLuckyGifts,
                        onLuckyGiftVisual = onLuckyGiftVisual,
                    )
                }
            }
        }

        chatterSheetProfile?.let { chatter ->
            LiveChatterActionSheet(
                profile = chatter,
                onDismiss = { chatterSheetProfile = null },
                onVideoCall = {
                    onVideoCallChatter(chatter)
                    chatterSheetProfile = null
                },
                onMessage = {
                    onMessageChatter(chatter)
                    chatterSheetProfile = null
                },
                onPickGift = {
                    chatterGiftTarget = chatter
                    chatterSheetProfile = null
                    showChatterGiftSheet = true
                }
            )
        }
        if (showChatterGiftSheet && chatterGiftTarget != null) {
            GiftSelectionSheet(
                gifts = uiState.availableGifts,
                coinBalance = uiState.coinBalance,
                isProcessing = uiState.isGiftTransactionProcessing,
                onTopUpCoins = onTopUpCoins,
                onDismiss = {
                    showChatterGiftSheet = false
                    chatterGiftTarget = null
                },
                onSend = { gift, count ->
                    onPrivateGiftToChatter(chatterGiftTarget!!, gift, count)
                    showChatterGiftSheet = false
                    chatterGiftTarget = null
                },
                luckyGiftReceiverId = chatterGiftTarget?.id,
                onLuckySend = onProcessLuckyGifts,
                onLuckyGiftVisual = onLuckyGiftVisual,
                itzoAudioPartyGiftPresentation = uiState.isAudioPartyLive,
            )
        }
        if (showHostApGiftSheet && uiState.isAudioPartyLive) {
            val luckyId = streamHostUserId.orEmpty()
            GiftSelectionSheet(
                gifts = uiState.availableGifts,
                coinBalance = uiState.coinBalance,
                isProcessing = uiState.isGiftTransactionProcessing,
                onTopUpCoins = onTopUpCoins,
                onDismiss = { showHostApGiftSheet = false },
                onSend = { gift, count ->
                    onSendHostStreamGift(gift, count)
                    showHostApGiftSheet = false
                },
                luckyGiftReceiverId = luckyId.takeIf { it.isNotEmpty() },
                onLuckySend = onProcessLuckyGifts,
                onLuckyGiftVisual = onLuckyGiftVisual,
                itzoAudioPartyGiftPresentation = true,
            )
        }

        val hostSoloSheetUid = FirebaseAuth.getInstance().currentUser?.uid?.trim().orEmpty()
        if (hostSoloViewerListSheet) {
            LiveWatchingViewersSheet(
                viewerUids = uiState.liveAudienceViewerUserIds,
                uiState = uiState,
                currentUserId = hostSoloSheetUid,
                sheetTitle = null,
                onDismiss = { hostSoloViewerListSheet = false },
                onOpenViewerProfile = { p ->
                    hostSoloViewerListSheet = false
                    onOpenPkOpponentProfile(p)
                },
                onFollowViewer = { p ->
                    if (isGuestPreview) onGuestRestricted()
                    else onFollow(p)
                }
            )
        }
        hostPkBattlerViewerSheet?.let { (uids, title) ->
            LiveWatchingViewersSheet(
                viewerUids = uids,
                uiState = uiState,
                currentUserId = hostSoloSheetUid,
                sheetTitle = title,
                onDismiss = { hostPkBattlerViewerSheet = null },
                onOpenViewerProfile = { p ->
                    hostPkBattlerViewerSheet = null
                    onOpenPkOpponentProfile(p)
                },
                onFollowViewer = { p ->
                    if (isGuestPreview) onGuestRestricted()
                    else onFollow(p)
                }
            )
        }

        if (showHostApStreamMenu && uiState.isAudioPartyLive) {
            AudioPartyHostStreamMenuBottomSheet(
                onDismiss = { showHostApStreamMenu = false },
                onPickTheme = {
                    showHostApStreamMenu = false
                    showHostApThemePicker = true
                },
                onLiveUsers = {
                    showHostApStreamMenu = false
                    hostSoloViewerListSheet = true
                },
                onVideoCallRequests = {
                    showHostApStreamMenu = false
                    showHostApVideoCallRequestsSheet = true
                },
                onSeatBlock = {
                    showHostApStreamMenu = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.live_audio_party_host_seat_controls_hint),
                        Toast.LENGTH_LONG,
                    ).show()
                },
                onExit = {
                    showHostApStreamMenu = false
                    showEndLiveConfirm = true
                },
            )
        }
        if (showHostApThemePicker && uiState.isAudioPartyLive) {
            AudioPartyRoomBackgroundPickerSheet(
                currentKey = uiState.audioPartyStream.roomBackgroundKey,
                onDismiss = { showHostApThemePicker = false },
                onPick = { key ->
                    showHostApThemePicker = false
                    onAudioPartyRoomBackgroundChange(key)
                },
            )
        }
        if (showHostApVideoCallRequestsSheet && uiState.isAudioPartyLive) {
            AudioPartyHostVideoCallRequestsSheet(
                requests = uiState.audioPartyVideoCallRequests,
                onDismiss = { showHostApVideoCallRequestsSheet = false },
                onRespond = { from, accept ->
                    showHostApVideoCallRequestsSheet = false
                    onHostRespondAudioPartyVideoCall(from, accept)
                },
            )
        }

        if (showEndLiveConfirm) {
            FuturisticConfirmCloseDialog(
                onDismissRequest = { showEndLiveConfirm = false },
                onConfirm = {
                    showEndLiveConfirm = false
                    reportSessionOnce()
                    onEndLive()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveChatterActionSheet(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onVideoCall: () -> Unit,
    onMessage: () -> Unit,
    onPickGift: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B1B24),
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = profile.photoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(12.dp))
            Text(
                profile.name.ifBlank { stringResource(R.string.live_chat_sender_default) },
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onVideoCall,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(ItzoUiTokens.livePrimaryCtaBrush(), RoundedCornerShape(14.dp)),
            ) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.live_video_call_action), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onMessage,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.live_message), color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onPickGift,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.live_send_gift_action), color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LiveProfileCard(
    profile: UserProfile,
    modifier: Modifier = Modifier,
    pkBannerText: String? = null,
    onClick: () -> Unit
) {
    val audioPartyListCard =
        profile.isLive && profile.liveStreamAudioOnly && pkBannerText.isNullOrBlank()
    Card(
        modifier = modifier
            .aspectRatio(0.625f)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors =
            if (audioPartyListCard) {
                CardDefaults.cardColors(containerColor = Color(0xFFFFDDFF))
            } else {
                CardDefaults.cardColors()
            },
    ) {
        if (audioPartyListCard) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color(0xFFFFE8F8),
                                        Color(0xFFFFDDFF),
                                        Color(0xFFE8D4FF),
                                    ),
                            ),
                        )
                        .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(color = Color(0xFFE91E63), shape = RoundedCornerShape(4.dp)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color.White))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "LIVE",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Card(
                        shape = CircleShape,
                        modifier = Modifier.size(88.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        ProfilePhotoOrPlaceholder(
                            photoUrl = profile.photoUrl,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            placeholderTint = ItzoUiTokens.AudioPartyOnRoomText.copy(alpha = 0.5f),
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = ItzoUiTokens.FrameTeal.copy(alpha = 0.75f),
                        modifier = Modifier.size(28.dp),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            profile.name,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(R.string.live_audio_party),
                            color = ItzoUiTokens.FrameTeal.copy(alpha = 0.95f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = ItzoUiTokens.AudioPartyOnRoomText,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.live_viewers_count, profile.viewerCount),
                                color = ItzoUiTokens.AudioPartyOnRoomText.copy(alpha = 0.88f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = profile.photoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                startY = 300f
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    val showLiveOrPkPill = profile.isLive || !pkBannerText.isNullOrBlank()
                    if (showLiveOrPkPill) {
                        Surface(
                            color = if (!pkBannerText.isNullOrBlank()) Color(0xFFFF6F00) else Color.Red,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (!pkBannerText.isNullOrBlank()) {
                                        stringResource(R.string.live_pk_battle_short).uppercase(Locale.getDefault())
                                    } else {
                                        "LIVE"
                                    },
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (!pkBannerText.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = Color(0xFFFFC107),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = pkBannerText,
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Alignment.TopEnd.let { Modifier.align(it).padding(8.dp) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${profile.viewerCount}", color = Color.White, fontSize = 10.sp)
                    }
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            profile.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(6.dp))
                        GenderAgeBadge(
                            gender = profile.genderText,
                            age = profile.currentAge
                        )
                        if (profile.hasBlueTick) {
                            Spacer(Modifier.width(4.dp))
                            VerifiedBadge(size = 12.dp)
                        }
                    }
                    Text(
                        profile.city,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveChatLevelBadge(level: Int) {
    val lv = level.coerceAtLeast(1)
    Surface(
        modifier = Modifier.shadow(
            6.dp,
            RoundedCornerShape(5.dp),
            spotColor = Color(0xFFFFE082),
            ambientColor = Color(0x66FFD700)
        ),
        shape = RoundedCornerShape(5.dp),
        color = Color(0xFF3E1C5C).copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.65f))
    ) {
        Text(
            text = "[Lv. $lv]",
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            color = Color(0xFFFFE082),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun PkSpectatorVideoNamePill(
    name: String,
    alignment: Alignment,
    gender: String = "",
    /** Clears gamified PK badge / bottom HUD overlap when pills are bottom-aligned. */
    extraBottomInset: Dp = 56.dp,
    /** Clears top HUD overlap when pills are top-aligned. */
    extraTopInset: Dp = 56.dp,
    onClick: (() -> Unit)? = null,
) {
    val accentBrush = namePillAccentBrush(gender)
    val isTop = alignment == Alignment.TopStart || alignment == Alignment.TopEnd
    val isBottom = alignment == Alignment.BottomStart || alignment == Alignment.BottomEnd
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (alignment == Alignment.BottomStart || alignment == Alignment.TopStart) 8.dp else 0.dp,
                end = if (alignment == Alignment.BottomEnd || alignment == Alignment.TopEnd) 8.dp else 0.dp,
                bottom = if (isBottom) 10.dp + extraBottomInset else 0.dp,
                top = if (isTop) 10.dp + extraTopInset else 0.dp
            ),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .then(
                    if (accentBrush != null) Modifier.background(accentBrush)
                    else Modifier.background(Color.Black.copy(alpha = 0.54f))
                )
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = name,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WatcherLivePanelHeader(
    viewersLabel: String,
    isSpeakerOn: Boolean,
    onToggleSpeaker: () -> Unit,
    onCloseRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = viewersLabel,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleSpeaker,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = LIVE_WATCHER_LUXURY_GLASS_ALPHA))
                ) {
                    Icon(
                        imageVector = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (isSpeakerOn) "Speaker on" else "Speaker off",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(
                    onClick = onCloseRequest,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.live_leave_stream_cd),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/** Over live video: streamer nameplate (solo) + top-end speaker + close; PK chips optional. */
@Suppress("UNUSED_PARAMETER")
@Composable
private fun WatcherVideoTopChrome(
    partner: UserProfile,
    viewersLabel: String,
    streamDurationLabel: String,
    onViewersClick: () -> Unit,
    showViewerCount: Boolean = true,
    isSpeakerOn: Boolean,
    onToggleSpeaker: () -> Unit,
    hearsStreamerVoice: Boolean,
    onToggleHearStreamer: () -> Unit,
    onCloseRequest: () -> Unit,
    onOpenHostProfile: () -> Unit,
    isFollowingHost: Boolean,
    onToggleFollowHost: () -> Unit,
    isGuestPreview: Boolean = false,
    onGuestRestricted: () -> Unit = {},
    extraTopInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
    pkHostSpectatorsChip: String? = null,
    pkGuestSpectatorsChip: String? = null,
    onPkHostSpectatorsClick: (() -> Unit)? = null,
    onPkGuestSpectatorsClick: (() -> Unit)? = null,
    allSpectatorsLine: String? = null,
    onAllSpectatorsClick: (() -> Unit)? = null,
    /** Pastel audio-party room: secondary labels use on-room colors so they stay readable. */
    audioPartyLightRoom: Boolean = false,
) {
    val secondaryOnRoom = ItzoUiTokens.AudioPartyOnRoomMuted
    val iconOnRoom = ItzoUiTokens.AudioPartyOnRoomText
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = extraTopInset)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            // Solo / PK single-tile: same as PK tiles — host identity opens profile sheet; viewer list is separate.
            Surface(
                color = ItzoUiTokens.LiveHostNameplateScrim,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.clickable(onClick = onOpenHostProfile)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(modifier = Modifier.size(32.dp), shape = CircleShape, color = Color.Gray) {
                        AsyncImage(
                            model = partner.photoUrl.takeIf { it.isNotBlank() },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                partner.name.ifBlank { stringResource(R.string.live_pk_battler_host_fallback) },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (partner.hasBlueTick) {
                                Spacer(Modifier.width(4.dp))
                                VerifiedBadge(size = 12.dp)
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            GenderAgeBadge(
                                gender = partner.genderText,
                                age = partner.currentAge
                            )
                            LiveChatLevelBadge(level = partner.level.coerceAtLeast(1))
                        }
                        if (streamDurationLabel.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    "●",
                                    color = Color(0xFF4ADE80),
                                    fontSize = 8.sp,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                Text(
                                    text = streamDurationLabel,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            if (showViewerCount) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onViewersClick)
                ) {
                    Icon(
                        imageVector = Icons.Filled.List,
                        contentDescription = null,
                        tint = if (audioPartyLightRoom) iconOnRoom else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = viewersLabel,
                        color = if (audioPartyLightRoom) secondaryOnRoom else Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (pkHostSpectatorsChip != null || pkGuestSpectatorsChip != null) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pkHostSpectatorsChip?.let { chip ->
                        Surface(
                            color = if (audioPartyLightRoom) {
                                Color.White.copy(alpha = 0.72f)
                            } else {
                                Color.Black.copy(alpha = 0.45f)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable { onPkHostSpectatorsClick?.invoke() }
                        ) {
                            Text(
                                text = chip,
                                color = if (audioPartyLightRoom) {
                                    ItzoUiTokens.AudioPartyOnRoomText.copy(alpha = 0.92f)
                                } else {
                                    Color.White.copy(alpha = 0.92f)
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                    pkGuestSpectatorsChip?.let { chip ->
                        Surface(
                            color = if (audioPartyLightRoom) {
                                Color.White.copy(alpha = 0.72f)
                            } else {
                                Color.Black.copy(alpha = 0.45f)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable { onPkGuestSpectatorsClick?.invoke() }
                        ) {
                            Text(
                                text = chip,
                                color = if (audioPartyLightRoom) {
                                    ItzoUiTokens.AudioPartyOnRoomText.copy(alpha = 0.92f)
                                } else {
                                    Color.White.copy(alpha = 0.92f)
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
            if (allSpectatorsLine != null && onAllSpectatorsClick != null) {
                Text(
                    text = allSpectatorsLine,
                    color = if (audioPartyLightRoom) secondaryOnRoom else Color.White.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onAllSpectatorsClick)
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleHearStreamer,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (audioPartyLightRoom) Color.White.copy(alpha = 0.85f)
                        else Color.White.copy(alpha = LIVE_WATCHER_LUXURY_GLASS_ALPHA)
                    )
            ) {
                Icon(
                    imageVector = if (hearsStreamerVoice) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = stringResource(
                        if (hearsStreamerVoice) R.string.live_watcher_mute_streamer_cd
                        else R.string.live_watcher_unmute_streamer_cd
                    ),
                    tint = if (audioPartyLightRoom) iconOnRoom else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(
                onClick = onToggleSpeaker,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (audioPartyLightRoom) Color.White.copy(alpha = 0.85f)
                        else Color.White.copy(alpha = LIVE_WATCHER_LUXURY_GLASS_ALPHA)
                    )
            ) {
                Icon(
                    imageVector = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = if (isSpeakerOn) "Speaker on" else "Speaker off",
                    tint = if (audioPartyLightRoom) iconOnRoom else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(
                onClick = onCloseRequest,
                modifier = Modifier
                    .size(40.dp)
                    .shadow(10.dp, CircleShape, spotColor = Color(0xFFFF2222).copy(alpha = 0.7f))
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFFF4444), Color(0xFFCC0000))
                        )
                    )
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.live_leave_stream_cd),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** PK battle viewer: stream duration (left) + speaker + close (right). */
@Composable
private fun PkWatcherTopRightChrome(
    streamDurationLabel: String,
    isSpeakerOn: Boolean,
    onToggleSpeaker: () -> Unit,
    hearsStreamerVoice: Boolean,
    onToggleHearStreamer: () -> Unit,
    onCloseRequest: () -> Unit,
    extraTopInset: Dp,
    /** When false, speaker is shown next to the viewer chip in the bottom panel (PK dual watch). */
    showSpeakerInTopChrome: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 4.dp + extraTopInset, start = 12.dp, end = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (streamDurationLabel.isNotBlank()) {
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "●",
                        color = Color(0xFF4ADE80),
                        fontSize = 8.sp,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = streamDurationLabel,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleHearStreamer,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = LIVE_WATCHER_LUXURY_GLASS_ALPHA))
            ) {
                Icon(
                    imageVector = if (hearsStreamerVoice) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = stringResource(
                        if (hearsStreamerVoice) R.string.live_watcher_mute_streamer_cd
                        else R.string.live_watcher_unmute_streamer_cd
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (showSpeakerInTopChrome) {
                IconButton(
                    onClick = onToggleSpeaker,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = LIVE_WATCHER_LUXURY_GLASS_ALPHA))
                ) {
                    Icon(
                        imageVector = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (isSpeakerOn) "Speaker on" else "Speaker off",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            IconButton(
                onClick = onCloseRequest,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.52f))
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.live_leave_stream_cd),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveWatcherSpectatorChatChrome(
    modifier: Modifier,
    uiState: DatingUiState,
    partner: UserProfile,
    chatMessage: String,
    onChatMessageChange: (String) -> Unit,
    likeCount: Int,
    onLikeIncrement: () -> Unit,
    likeAnimTrigger: Int,
    isGuestPreview: Boolean,
    scope: CoroutineScope,
    giftShake: Animatable<Float, AnimationVector1D>,
    onGuestRestricted: () -> Unit,
    onSendComment: (String) -> Unit,
    onLikeLive: (UserProfile) -> Unit,
    onPickChatterFromMessage: (LiveStreamChatMessage) -> Unit,
    onOpenGiftSheetToHost: () -> Unit,
    onOpenMessageSheet: () -> Unit = {},
    onOpenDiamondShop: () -> Unit = {},
    /** Shown above the message field, left-aligned (PK spectator); speaker sits to its right. */
    viewersChipLabel: String = "",
    onViewersChipClick: () -> Unit = {},
    pkHostSpectatorsChip: String = "",
    pkGuestSpectatorsChip: String = "",
    onPkHostSpectatorsChipClick: () -> Unit = {},
    onPkGuestSpectatorsChipClick: () -> Unit = {},
    pkAllViewersLine: String = "",
    onPkAllViewersClick: () -> Unit = {},
    pkSpectatorSpeakerOn: Boolean = true,
    onPkSpectatorSpeakerToggle: () -> Unit = {},
    pkSpectatorHearsStreamer: Boolean = true,
    onPkSpectatorHearToggle: () -> Unit = {},
    showStreamControlHeader: Boolean = false,
    streamViewersLabel: String = "",
    streamSpeakerOn: Boolean = true,
    onStreamSpeakerToggle: () -> Unit = {},
    onStreamCloseRequest: () -> Unit = {},
) {
    fun resolveChatterProfile(msg: LiveStreamChatMessage): UserProfile {
        val id = msg.senderId.trim()
        return uiState.profiles.find { it.id == id }
            ?: uiState.filteredProfiles.find { it.id == id }
            ?: UserProfile(id = id, name = msg.senderName.ifBlank { "User" }, profileNumber = "", photoUrl = "")
    }
    fun senderAvatarUrl(uid: String): String? =
        uiState.profiles.find { it.id == uid }?.photoUrl?.takeIf { it.isNotBlank() }
            ?: uiState.filteredProfiles.find { it.id == uid }?.photoUrl?.takeIf { it.isNotBlank() }

    /** Bottom inset for the PK spectator action rail — matches composer row [bottom] padding.
     *  Parent [LiveWatcherChatPane] already applies [navigationBarsPadding]; do not add nav again here. */
    val pkSpectatorFabRailBottomInset = 12.dp
    val pkNeonPink = Color(0xFFFF2BE7)
    val pkNeonBlue = Color(0xFF00F5FF)
    val pkNeonGold = Color(0xFFFFD700)
    val likeHeartScale = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(likeAnimTrigger) {
        if (likeAnimTrigger > 0) {
            likeHeartScale.snapTo(1.5f)
            likeHeartScale.animateTo(
                1f,
                androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 600f)
            )
        }
    }
    val spectatorChatListState = rememberLazyListState()
    val spectatorHistoryStripMaxH =
        liveChatHistoryStripMaxHeight(LocalConfiguration.current.screenHeightDp)
    var pkSpectatorStagingHistoryMode by remember { mutableStateOf(false) }
    var spectatorHistoryBaselineCount by remember { mutableIntStateOf(0) }
    val spectatorLiveChatCountRef = rememberUpdatedState(uiState.liveStreamChatMessages.size)
    val spectatorStagingHistoryRef = rememberUpdatedState(pkSpectatorStagingHistoryMode)
    val openSpectatorStagingHistory = rememberUpdatedState({
        spectatorHistoryBaselineCount = spectatorLiveChatCountRef.value
        pkSpectatorStagingHistoryMode = true
    })
    val spectatorJumpScope = rememberCoroutineScope()
    val revealSpectatorStagingScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!spectatorStagingHistoryRef.value && kotlin.math.abs(available.y) > 18f) {
                    openSpectatorStagingHistory.value.invoke()
                }
                return Offset.Zero
            }
        }
    }
    val spectatorMessagesNewestFirst = remember(uiState.liveStreamChatMessages) {
        uiState.liveStreamChatMessages.asReversed()
    }
    /** PK spectator Live tab: full feed (no TTL); staging uses last [LIVE_CHAT_STAGING_VISIBLE_MESSAGES]. */
    val spectatorMessagesToShow = remember(spectatorMessagesNewestFirst) {
        spectatorMessagesNewestFirst
    }
    val spectatorStagingDisplayedMessages = remember(
        spectatorMessagesToShow,
        pkSpectatorStagingHistoryMode,
    ) {
        if (pkSpectatorStagingHistoryMode) spectatorMessagesToShow
        else spectatorMessagesToShow.take(LIVE_CHAT_STAGING_VISIBLE_MESSAGES)
    }
    LaunchedEffect(spectatorChatListState) {
        snapshotFlow {
            spectatorChatListState.firstVisibleItemIndex > 0 ||
                spectatorChatListState.firstVisibleItemScrollOffset > 8
        }
            .distinctUntilChanged()
            .collect { scrolledAway ->
                if (scrolledAway) {
                    openSpectatorStagingHistory.value.invoke()
                } else {
                    val countAtSchedule = spectatorMessagesToShow.size
                    delay(550)
                    if (spectatorMessagesToShow.size > countAtSchedule) return@collect
                    val stillAway =
                        spectatorChatListState.firstVisibleItemIndex > 0 ||
                            spectatorChatListState.firstVisibleItemScrollOffset > 8
                    if (!stillAway) {
                        pkSpectatorStagingHistoryMode = false
                        spectatorHistoryBaselineCount = spectatorLiveChatCountRef.value
                        spectatorChatListState.scrollToItem(0)
                    }
                }
            }
    }
    val spectatorFeedNewestKey = spectatorMessagesNewestFirst.firstOrNull()?.let { m ->
        "${m.id}_${m.timestamp}_${m.text.length}"
    }.orEmpty()
    LaunchedEffect(spectatorFeedNewestKey, spectatorMessagesToShow.size, pkSpectatorStagingHistoryMode) {
        if (pkSpectatorStagingHistoryMode) return@LaunchedEffect
        val atLiveEdge =
            spectatorChatListState.firstVisibleItemIndex == 0 &&
                spectatorChatListState.firstVisibleItemScrollOffset < 16
        if (atLiveEdge && spectatorFeedNewestKey.isNotBlank()) {
            repeat(6) { pass ->
                delay(if (pass == 0) 48L else 22L)
                spectatorChatListState.scrollToItem(0)
            }
        }
    }
    Box(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            if (showStreamControlHeader) {
                WatcherLivePanelHeader(
                    viewersLabel = streamViewersLabel,
                    isSpeakerOn = streamSpeakerOn,
                    onToggleSpeaker = onStreamSpeakerToggle,
                    onCloseRequest = onStreamCloseRequest,
                    modifier = Modifier.fillMaxWidth()
                )
            }
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    if (!pkSpectatorStagingHistoryMode &&
                        spectatorMessagesToShow.size > LIVE_CHAT_STAGING_VISIBLE_MESSAGES
                    ) {
                        LiveChatOlderMessagesCue(onOpenHistory = { openSpectatorStagingHistory.value.invoke() })
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RectangleShape)
                    ) {
                        if (pkSpectatorStagingHistoryMode &&
                            spectatorMessagesToShow.size > spectatorHistoryBaselineCount
                        ) {
                            LiveChatNewMessagesCue(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(
                                        end = 8.dp + PkSpectatorActionStripOuterEnd + 12.dp,
                                        bottom = 8.dp,
                                    )
                                    .zIndex(1f),
                                onJumpToLatest = {
                                    pkSpectatorStagingHistoryMode = false
                                    spectatorHistoryBaselineCount = spectatorMessagesToShow.size
                                    spectatorJumpScope.launch {
                                        repeat(6) { pass ->
                                            delay(if (pass == 0) 48L else 22L)
                                            spectatorChatListState.scrollToItem(0)
                                        }
                                    }
                                },
                            )
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomStart)
                                .heightIn(
                                    max = if (pkSpectatorStagingHistoryMode) {
                                        spectatorHistoryStripMaxH
                                    } else {
                                        liveChatStagingViewportMaxHeight()
                                    },
                                )
                                .clip(RectangleShape)
                                .nestedScroll(revealSpectatorStagingScroll)
                                // Parent pane already applies navigation bar insets; avoid double-counting.
                                .padding(
                                    start = 8.dp,
                                    top = 4.dp,
                                    end = 8.dp + PkSpectatorActionStripOuterEnd + 12.dp,
                                    bottom = 8.dp,
                                ),
                            state = spectatorChatListState,
                            reverseLayout = true,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.Start,
                        ) {
                    items(
                        items = spectatorStagingDisplayedMessages,
                        key = { m -> m.id.ifBlank { "${m.timestamp}_${m.senderId}" } },
                    ) { m ->
                    when {
                        m.isLiveSystemMessage() -> {
                            val isJoin = m.text.contains("joined", ignoreCase = true)
                            Surface(
                                color = if (isJoin) {
                                    Color(0xFF4ADE80).copy(alpha = 0.22f)
                                } else {
                                    Color.Black.copy(alpha = 0.3f)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isJoin) {
                                        Icon(
                                            Icons.Default.Login,
                                            contentDescription = null,
                                            tint = Color(0xFF4ADE80),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        LiveChatLevelBadge(level = resolveChatterProfile(m).level)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = m.senderName.ifBlank { "Someone" },
                                            color = Color(0xFF4ADE80),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Text(
                                            text = " joined",
                                            color = Color.White.copy(alpha = 0.75f),
                                            fontSize = 12.sp
                                        )
                                    } else {
                                        Text(
                                            text = m.text,
                                            color = Color.White.copy(alpha = 0.65f),
                                            fontSize = 11.sp,
                                            fontStyle = FontStyle.Italic,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                        m.type == "gift" -> {
                            Surface(
                                color = Color.Black.copy(alpha = 0.32f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                LiveStreamGiftChatRow(
                                    message = m,
                                    senderPhotoUrl = senderAvatarUrl(m.senderId),
                                    modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp),
                                    senderDisplayLevel = resolveChatterProfile(m).level.coerceAtLeast(1),
                                    onClick = {
                                        if (m.senderId.isNotBlank() && m.senderId != partner.id) {
                                            onPickChatterFromMessage(m)
                                        }
                                    }
                                )
                            }
                        }
                        else -> {
                            Surface(
                                color = Color.Black.copy(alpha = 0.32f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                                    .clickable {
                                        if (m.senderId.isNotBlank() && m.senderId != partner.id) {
                                            onPickChatterFromMessage(m)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LiveChatLevelBadge(level = resolveChatterProfile(m).level)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "${m.senderName}: ${m.text}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier.weight(1f).fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
                        }
                    }
                }
            LiveStreamComplianceBanner(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 8.dp,
                        end = 8.dp + PkSpectatorActionStripOuterEnd + 12.dp,
                        top = 2.dp,
                        bottom = 4.dp,
                    ),
                variant = LiveStreamComplianceBannerVariant.DockedAboveComposer,
            )
            val dualPkChips = pkHostSpectatorsChip.isNotBlank() && pkGuestSpectatorsChip.isNotBlank()
            if (dualPkChips) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp + PkSpectatorActionStripOuterEnd, bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Black.copy(alpha = 0.38f),
                            border = BorderStroke(1.dp, pkNeonBlue.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onPkHostSpectatorsChipClick() }
                        ) {
                            Text(
                                text = pkHostSpectatorsChip,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Black.copy(alpha = 0.38f),
                            border = BorderStroke(1.dp, pkNeonPink.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onPkGuestSpectatorsChipClick() }
                        ) {
                            Text(
                                text = pkGuestSpectatorsChip,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    if (pkAllViewersLine.isNotBlank()) {
                        Text(
                            text = pkAllViewersLine,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = 4.dp, top = 6.dp, end = 8.dp + PkSpectatorActionStripOuterEnd)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPkAllViewersClick() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            } else if (viewersChipLabel.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp + PkSpectatorActionStripOuterEnd, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.38f),
                        border = BorderStroke(1.dp, pkNeonBlue.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onViewersChipClick() }
                    ) {
                        Text(
                            text = viewersChipLabel,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp + PkSpectatorActionStripOuterEnd, top = 6.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.horizontalGradient(listOf(pkNeonBlue, pkNeonPink)))
                        .padding(2.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val pkSpectatorChatInteraction = remember { MutableInteractionSource() }
                    val pkSpectatorChatTextStyle = remember {
                        TextStyle(
                            color = Color.White,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Start,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        )
                    }
                    val pkSpectatorChatFieldColors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0D0718).copy(alpha = 0.96f),
                        unfocusedContainerColor = Color(0xFF0D0718).copy(alpha = 0.96f),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = pkNeonPink,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    )
                    val pkSpectatorChatShape = RoundedCornerShape(24.dp)
                    BasicTextField(
                        value = chatMessage,
                        onValueChange = onChatMessageChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                        singleLine = true,
                        textStyle = pkSpectatorChatTextStyle,
                        interactionSource = pkSpectatorChatInteraction,
                        cursorBrush = SolidColor(pkNeonPink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (chatMessage.isNotBlank()) {
                                    onSendComment(chatMessage)
                                    onChatMessageChange("")
                                }
                            },
                        ),
                        decorationBox = { innerTextField ->
                            OutlinedTextFieldDefaults.DecorationBox(
                                value = chatMessage,
                                innerTextField = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        innerTextField()
                                    }
                                },
                                enabled = true,
                                singleLine = true,
                                visualTransformation = VisualTransformation.None,
                                interactionSource = pkSpectatorChatInteraction,
                                colors = pkSpectatorChatFieldColors,
                                contentPadding = OutlinedTextFieldDefaults.contentPadding(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 8.dp,
                                    bottom = 8.dp,
                                ),
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.pk_type_message_hint),
                                        style = pkSpectatorChatTextStyle.copy(
                                            color = Color.White.copy(alpha = 0.45f),
                                            textAlign = TextAlign.Start,
                                        ),
                                    )
                                },
                                trailingIcon = {
                                    TextButton(
                                        onClick = {
                                            if (chatMessage.isNotBlank()) {
                                                onSendComment(chatMessage)
                                                onChatMessageChange("")
                                            }
                                        },
                                    ) {
                                        Text(
                                            stringResource(R.string.live_send),
                                            color = pkNeonPink,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                        )
                                    }
                                },
                                container = {
                                    OutlinedTextFieldDefaults.Container(
                                        enabled = true,
                                        isError = false,
                                        interactionSource = pkSpectatorChatInteraction,
                                        colors = pkSpectatorChatFieldColors,
                                        shape = pkSpectatorChatShape,
                                    )
                                },
                            )
                        },
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .width(PkSpectatorActionStripOuterEnd)
                .padding(end = 6.dp, bottom = pkSpectatorFabRailBottomInset)
                .zIndex(8f),
            verticalArrangement = Arrangement.spacedBy(PkSpectatorFabSpacing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Bottom-aligned column (last = closest to nav): chat … gift … diamond … speaker … like.
            PkSpectatorTrayFab(
                onClick = onOpenMessageSheet,
                enabled = true,
                accessibilityLabel = stringResource(R.string.live_message),
                containerLayer = Modifier
                    .background(Color(0xFF1A1030).copy(alpha = 0.92f))
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(PkSpectatorTrayIconSize)
                )
            }
            PkSpectatorTrayFab(
                onClick = {
                    if (uiState.isGiftTransactionProcessing) return@PkSpectatorTrayFab
                    if (isGuestPreview) {
                        scope.launch { runLockedShake(giftShake); onGuestRestricted() }
                    } else {
                        onOpenGiftSheetToHost()
                    }
                },
                enabled = !uiState.isGiftTransactionProcessing,
                accessibilityLabel = stringResource(R.string.live_send_gift_action),
                containerLayer = Modifier
                    .background(
                        Brush.radialGradient(
                            colors = listOf(pkNeonGold, Color(0xFFFF9100).copy(alpha = 0.9f))
                        )
                    )
                    .border(1.dp, Color(0xFF5D4037).copy(alpha = 0.55f), CircleShape)
                    .graphicsLayer { alpha = if (isGuestPreview) 0.45f else 1f }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isGiftTransactionProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.Black.copy(alpha = 0.85f)
                        )
                    } else {
                        Icon(
                            Icons.Default.CardGiftcard,
                            contentDescription = null,
                            tint = Color.Black.copy(alpha = 0.92f),
                            modifier = Modifier.size(PkSpectatorTrayIconSize)
                        )
                    }
                    if (isGuestPreview) {
                        PremiumLockBadge(Modifier.align(Alignment.TopEnd))
                    }
                }
            }
            PkSpectatorTrayFab(
                onClick = onOpenDiamondShop,
                enabled = true,
                accessibilityLabel = stringResource(R.string.call_diamond_shop_cd),
                containerLayer = Modifier
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF42A5F5), Color(0xFF1565C0))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.42f), CircleShape)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_diamond),
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(PkSpectatorTrayIconSize)
                )
            }
            PkSpectatorTrayFab(
                onClick = onPkSpectatorHearToggle,
                enabled = true,
                accessibilityLabel = stringResource(
                    if (pkSpectatorHearsStreamer) R.string.live_watcher_mute_streamer_cd
                    else R.string.live_watcher_unmute_streamer_cd
                ),
                containerLayer = Modifier
                    .background(
                        if (pkSpectatorHearsStreamer) {
                            Color(0xFF334155).copy(alpha = 0.88f)
                        } else {
                            Color(0xFFEF4444).copy(alpha = 0.55f)
                        }
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            ) {
                Icon(
                    imageVector = if (pkSpectatorHearsStreamer) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(PkSpectatorTrayIconSize)
                )
            }
            PkSpectatorTrayFab(
                onClick = onPkSpectatorSpeakerToggle,
                enabled = true,
                accessibilityLabel = if (pkSpectatorSpeakerOn) "Speaker on" else "Speaker off",
                containerLayer = Modifier
                    .background(
                        if (pkSpectatorSpeakerOn) {
                            pkNeonBlue.copy(alpha = 0.38f)
                        } else {
                            Color(0xFF1E293B).copy(alpha = 0.92f)
                        }
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            ) {
                Icon(
                    imageVector = if (pkSpectatorSpeakerOn) {
                        Icons.AutoMirrored.Filled.VolumeUp
                    } else {
                        Icons.AutoMirrored.Filled.VolumeOff
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(PkSpectatorTrayIconSize)
                )
            }
            PkSpectatorTrayFab(
                onClick = {
                    onLikeIncrement()
                    onLikeLive(partner)
                },
                enabled = true,
                accessibilityLabel = stringResource(R.string.profile_sheet_like),
                containerLayer = Modifier
                    .background(Color(0xFF301028).copy(alpha = 0.94f))
                    .border(1.dp, pkNeonPink.copy(alpha = 0.75f), CircleShape)
                    .graphicsLayer {
                        scaleX = likeHeartScale.value
                        scaleY = likeHeartScale.value
                    }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_heart),
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(PkSpectatorTrayIconSize)
                    )
                    if (likeCount > 0) {
                        Text(
                            text = "$likeCount",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 2.dp, bottom = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveWatcherStreamChatMessageRow(
    m: LiveStreamChatMessage,
    partner: UserProfile,
    resolveChatterProfile: (LiveStreamChatMessage) -> UserProfile,
    senderAvatarUrl: (String) -> String?,
    onPickChatterFromMessage: (LiveStreamChatMessage) -> Unit,
    audioPartyStyled: Boolean = false,
    viewerUserId: String = "",
) {
    when {
        m.isLiveSystemMessage() -> {
            val isJoin = m.text.contains("joined", ignoreCase = true)
            Surface(
                color = if (isJoin) Color(0xFF4ADE80).copy(alpha = 0.18f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(start = 6.dp, top = 0.dp, end = 6.dp, bottom = 0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isJoin) {
                        Icon(
                            Icons.Default.Login,
                            contentDescription = null,
                            tint = Color(0xFF4ADE80),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        LiveChatLevelBadge(level = resolveChatterProfile(m).level)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = m.senderName.ifBlank { "Someone" },
                            color = Color(0xFF4ADE80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = " joined",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            text = m.text,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        m.type == "gift" -> {
            LiveStreamGiftChatRow(
                message = m,
                senderPhotoUrl = senderAvatarUrl(m.senderId),
                modifier = Modifier.padding(top = 2.dp, bottom = 0.dp),
                senderDisplayLevel = resolveChatterProfile(m).level.coerceAtLeast(1),
                onClick = {
                    if (m.senderId.isNotBlank() && m.senderId != partner.id) {
                        onPickChatterFromMessage(m)
                    }
                }
            )
        }
        else -> {
            if (audioPartyStyled && viewerUserId.isNotBlank()) {
                LiveChatRowAudioParty(
                    message = m,
                    isMine = m.senderId == viewerUserId,
                    senderLevel = resolveChatterProfile(m).level.coerceAtLeast(1),
                    modifier = Modifier.padding(top = 2.dp, bottom = 0.dp),
                    onClick = {
                        if (m.senderId.isNotBlank() && m.senderId != partner.id) {
                            onPickChatterFromMessage(m)
                        }
                    },
                )
            } else {
                Surface(
                    color = Color.Black.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .padding(top = 2.dp, bottom = 0.dp)
                        .clickable {
                            if (m.senderId.isNotBlank() && m.senderId != partner.id) {
                                onPickChatterFromMessage(m)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LiveChatLevelBadge(level = resolveChatterProfile(m).level)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${m.senderName}: ${m.text}",
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveWatcherChatPane(
    modifier: Modifier,
    uiState: DatingUiState,
    partner: UserProfile,
    chatMessage: String,
    onChatMessageChange: (String) -> Unit,
    likeCount: Int,
    onLikeIncrement: () -> Unit,
    likeAnimTrigger: Int,
    isGuestPreview: Boolean,
    scope: CoroutineScope,
    videoShake: Animatable<Float, AnimationVector1D>,
    giftShake: Animatable<Float, AnimationVector1D>,
    onGuestRestricted: () -> Unit,
    onSendComment: (String) -> Unit,
    onCall: (UserProfile) -> Unit,
    onMessage: (UserProfile) -> Unit,
    onLikeLive: (UserProfile) -> Unit,
    onPickChatterFromMessage: (LiveStreamChatMessage) -> Unit,
    onOpenGiftSheetToHost: () -> Unit,
    onOpenMessageSheet: () -> Unit = {},
    onOpenDiamondShop: () -> Unit = {},
    pkSpectatorViewersLabel: String = "",
    onPkSpectatorViewersClick: () -> Unit = {},
    pkHostSpectatorsChip: String = "",
    pkGuestSpectatorsChip: String = "",
    onPkHostSpectatorsChipClick: () -> Unit = {},
    onPkGuestSpectatorsChipClick: () -> Unit = {},
    pkAllViewersLine: String = "",
    onPkAllViewersClick: () -> Unit = {},
    pkSpectatorSpeakerOn: Boolean = true,
    onPkSpectatorSpeakerToggle: () -> Unit = {},
    pkSpectatorHearsStreamer: Boolean = true,
    onPkSpectatorHearToggle: () -> Unit = {},
    /** Solo watcher: cap chat list height and hug bottom so the bar sits above the gesture area. */
    compactBottomOverlay: Boolean = false,
    showDockedComplianceBanner: Boolean = true,
    spectatorPkBattleChrome: Boolean = false,
    showStreamControlHeader: Boolean = false,
    streamViewersLabel: String = "",
    streamSpeakerOn: Boolean = true,
    onStreamSpeakerToggle: () -> Unit = {},
    onStreamCloseRequest: () -> Unit = {},
    audioPartyChatChrome: Boolean = false,
    viewerUserId: String = "",
    onAudioPartyOpenRoomMenu: () -> Unit = {},
) {
    if (spectatorPkBattleChrome) {
        LiveWatcherSpectatorChatChrome(
            modifier = modifier,
            uiState = uiState,
            partner = partner,
            chatMessage = chatMessage,
            onChatMessageChange = onChatMessageChange,
            likeCount = likeCount,
            onLikeIncrement = onLikeIncrement,
            likeAnimTrigger = likeAnimTrigger,
            isGuestPreview = isGuestPreview,
            scope = scope,
            giftShake = giftShake,
            onGuestRestricted = onGuestRestricted,
            onSendComment = onSendComment,
            onLikeLive = onLikeLive,
            onPickChatterFromMessage = onPickChatterFromMessage,
            onOpenGiftSheetToHost = onOpenGiftSheetToHost,
            onOpenMessageSheet = onOpenMessageSheet,
            onOpenDiamondShop = onOpenDiamondShop,
            viewersChipLabel = pkSpectatorViewersLabel,
            onViewersChipClick = onPkSpectatorViewersClick,
            pkHostSpectatorsChip = pkHostSpectatorsChip,
            pkGuestSpectatorsChip = pkGuestSpectatorsChip,
            onPkHostSpectatorsChipClick = onPkHostSpectatorsChipClick,
            onPkGuestSpectatorsChipClick = onPkGuestSpectatorsChipClick,
            pkAllViewersLine = pkAllViewersLine,
            onPkAllViewersClick = onPkAllViewersClick,
            pkSpectatorSpeakerOn = pkSpectatorSpeakerOn,
            onPkSpectatorSpeakerToggle = onPkSpectatorSpeakerToggle,
            pkSpectatorHearsStreamer = pkSpectatorHearsStreamer,
            onPkSpectatorHearToggle = onPkSpectatorHearToggle,
            showStreamControlHeader = showStreamControlHeader,
            streamViewersLabel = streamViewersLabel,
            streamSpeakerOn = streamSpeakerOn,
            onStreamSpeakerToggle = onStreamSpeakerToggle,
            onStreamCloseRequest = onStreamCloseRequest,
        )
    } else {
    fun resolveChatterProfile(msg: LiveStreamChatMessage): UserProfile {
        val id = msg.senderId.trim()
        return uiState.profiles.find { it.id == id }
            ?: uiState.filteredProfiles.find { it.id == id }
            ?: UserProfile(id = id, name = msg.senderName.ifBlank { "User" }, profileNumber = "", photoUrl = "")
    }
    fun senderAvatarUrl(uid: String): String? =
        uiState.profiles.find { it.id == uid }?.photoUrl?.takeIf { it.isNotBlank() }
            ?: uiState.filteredProfiles.find { it.id == uid }?.photoUrl?.takeIf { it.isNotBlank() }

    val watcherHistoryStripMaxH =
        liveChatHistoryStripMaxHeight(LocalConfiguration.current.screenHeightDp)
    val liveChatListPadding = if (compactBottomOverlay) {
        PaddingValues(start = 8.dp, top = 0.dp, end = 8.dp, bottom = 0.dp)
    } else {
        paddingWithNavigationBars(bottomExtra = 4.dp)
    }
    /** Staging transcript sits flush above the docked compliance row; omit nav-bar bottom inset here (pane/footer handles it). */
    val stagingScrollPadding = PaddingValues(
        start = liveChatListPadding.calculateStartPadding(LocalLayoutDirection.current),
        top = liveChatListPadding.calculateTopPadding(),
        end = liveChatListPadding.calculateEndPadding(LocalLayoutDirection.current),
        bottom = 0.dp,
    )
    val paneModifier = modifier
    val compactScrollState = rememberScrollState()
    val expandedChatScrollState = rememberScrollState()
    val chatAllReversed = remember(uiState.liveStreamChatMessages) {
        uiState.liveStreamChatMessages.asReversed()
    }
    var overlayHistoryMode by remember { mutableStateOf(false) }
    var watcherHistoryBaselineCount by remember { mutableIntStateOf(0) }
    val historyModeRef = rememberUpdatedState(overlayHistoryMode)
    val watcherLiveChatCountRef = rememberUpdatedState(uiState.liveStreamChatMessages.size)
    val openHistoryRef = rememberUpdatedState({
        watcherHistoryBaselineCount = watcherLiveChatCountRef.value
        overlayHistoryMode = true
    })
    val revealOlderOnOverscroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!historyModeRef.value && kotlin.math.abs(available.y) > 18f) {
                    openHistoryRef.value.invoke()
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(compactBottomOverlay) {
        overlayHistoryMode = false
    }
    /** Solo watch uses full feed + row cap via [watcherStagingDisplayedMessages] (same as host). PK spectators use [LiveWatcherSpectatorChatChrome]. */
    val messagesToShow = chatAllReversed
    val watcherStagingDisplayedMessages = remember(messagesToShow, overlayHistoryMode) {
        val chronological = messagesToShow.asReversed()
        if (overlayHistoryMode) chronological
        else chronological.takeLast(LIVE_CHAT_STAGING_VISIBLE_MESSAGES)
    }
    LaunchedEffect(compactBottomOverlay, compactScrollState) {
        if (!compactBottomOverlay) return@LaunchedEffect
        snapshotFlow {
            compactScrollState.maxValue > 0 && compactScrollState.value < compactScrollState.maxValue - 8
        }
            .distinctUntilChanged()
            .collect { scrolledAwayFromLiveEdge ->
                if (scrolledAwayFromLiveEdge) {
                    openHistoryRef.value.invoke()
                } else {
                    val countAtSchedule = messagesToShow.size
                    delay(550)
                    if (messagesToShow.size > countAtSchedule) return@collect
                    val stillAway =
                        compactScrollState.maxValue > 0 && compactScrollState.value < compactScrollState.maxValue - 8
                    if (!stillAway) {
                        overlayHistoryMode = false
                        watcherHistoryBaselineCount = watcherLiveChatCountRef.value
                        compactScrollState.scrollTo(compactScrollState.maxValue)
                    }
                }
            }
    }
    LaunchedEffect(compactBottomOverlay, expandedChatScrollState) {
        if (compactBottomOverlay) return@LaunchedEffect
        snapshotFlow {
            expandedChatScrollState.maxValue > 0 && expandedChatScrollState.value < expandedChatScrollState.maxValue - 8
        }
            .distinctUntilChanged()
            .collect { scrolledAway ->
                if (scrolledAway) {
                    openHistoryRef.value.invoke()
                } else {
                    val countAtSchedule = messagesToShow.size
                    delay(550)
                    if (messagesToShow.size > countAtSchedule) return@collect
                    val stillAway =
                        expandedChatScrollState.maxValue > 0 &&
                            expandedChatScrollState.value < expandedChatScrollState.maxValue - 8
                    if (!stillAway) {
                        overlayHistoryMode = false
                        watcherHistoryBaselineCount = watcherLiveChatCountRef.value
                        expandedChatScrollState.scrollTo(expandedChatScrollState.maxValue)
                    }
                }
            }
    }
    val watcherSoloFeedKey = chatAllReversed.firstOrNull()?.let { m ->
        "${m.id}_${m.timestamp}_${m.text.length}"
    }.orEmpty()
    LaunchedEffect(watcherSoloFeedKey, messagesToShow.size, overlayHistoryMode, compactBottomOverlay) {
        if (overlayHistoryMode) return@LaunchedEffect
        if (compactBottomOverlay) {
            val atBottom =
                compactScrollState.maxValue <= 0 ||
                    compactScrollState.value >= compactScrollState.maxValue - 8
            if (atBottom && watcherSoloFeedKey.isNotBlank()) {
                rollerAnimateScrollToLatest(compactScrollState)
            }
        } else {
            val atBottom =
                expandedChatScrollState.maxValue <= 0 ||
                    expandedChatScrollState.value >= expandedChatScrollState.maxValue - 8
            if (atBottom && watcherSoloFeedKey.isNotBlank()) {
                rollerAnimateScrollToLatest(expandedChatScrollState)
            }
        }
    }
    Column(paneModifier) {
        if (showStreamControlHeader) {
            WatcherLivePanelHeader(
                viewersLabel = streamViewersLabel,
                isSpeakerOn = streamSpeakerOn,
                onToggleSpeaker = onStreamSpeakerToggle,
                onCloseRequest = onStreamCloseRequest,
                modifier = Modifier.fillMaxWidth()
            )
        }
        val expandedChatListModifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            if (compactBottomOverlay) {
                // Solo watch (compact): bottom-packed stack; staging height hugs rows up to cap so bubbles sit on the yellow slab.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        val layoutDirectionCompact = LocalLayoutDirection.current
                        if (!overlayHistoryMode &&
                            messagesToShow.size > LIVE_CHAT_STAGING_VISIBLE_MESSAGES
                        ) {
                            LiveChatOlderMessagesCue(onOpenHistory = { openHistoryRef.value.invoke() })
                        }
                        if (audioPartyChatChrome) {
                            AudioPartyWelcomeBanner(
                                text = stringResource(R.string.live_audio_party_room_welcome),
                                modifier =
                                    Modifier
                                        .padding(
                                            start = liveChatListPadding.calculateStartPadding(layoutDirectionCompact),
                                            end = liveChatListPadding.calculateEndPadding(layoutDirectionCompact),
                                        )
                                        .padding(bottom = 2.dp),
                            )
                            AudioPartyGiftFlyline(
                                messages = uiState.liveStreamChatMessages,
                                modifier =
                                    Modifier
                                        .padding(
                                            start = liveChatListPadding.calculateStartPadding(layoutDirectionCompact),
                                            end = liveChatListPadding.calculateEndPadding(layoutDirectionCompact),
                                        )
                                        .padding(bottom = 4.dp),
                            )
                        }
                        val watcherCompactStagingMax =
                            if (overlayHistoryMode) watcherHistoryStripMaxH else liveChatStagingViewportMaxHeight()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .nestedScroll(revealOlderOnOverscroll)
                        ) {
                            if (overlayHistoryMode &&
                                uiState.liveStreamChatMessages.size > watcherHistoryBaselineCount
                            ) {
                                LiveChatNewMessagesCue(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 6.dp, bottom = 6.dp)
                                        .zIndex(1f),
                                    onJumpToLatest = {
                                        overlayHistoryMode = false
                                        watcherHistoryBaselineCount = uiState.liveStreamChatMessages.size
                                        scope.launch {
                                            rollerAnimateScrollToLatest(compactScrollState)
                                        }
                                    },
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = watcherCompactStagingMax)
                                    .verticalScroll(compactScrollState)
                                    .padding(stagingScrollPadding),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                watcherStagingDisplayedMessages.forEach { m ->
                                    if (audioPartyChatChrome && m.type == "gift") return@forEach
                                    key(m.id.ifBlank { "${m.timestamp}_${m.senderId}" }) {
                                        LiveWatcherStreamChatMessageRow(
                                            m = m,
                                            partner = partner,
                                            resolveChatterProfile = { resolveChatterProfile(it) },
                                            senderAvatarUrl = { senderAvatarUrl(it) },
                                            onPickChatterFromMessage = onPickChatterFromMessage,
                                            audioPartyStyled = audioPartyChatChrome,
                                            viewerUserId = viewerUserId,
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = liveChatListPadding.calculateStartPadding(layoutDirectionCompact),
                                    end = liveChatListPadding.calculateEndPadding(layoutDirectionCompact),
                                ),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                if (showDockedComplianceBanner) {
                                    LiveStreamComplianceBanner(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 2.dp),
                                        variant = LiveStreamComplianceBannerVariant.DockedAboveComposer,
                                    )
                                }
                                OutlinedTextField(
                                    value = chatMessage,
                                    onValueChange = onChatMessageChange,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp, max = 56.dp),
                                    placeholder = {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            Text(
                                                stringResource(R.string.live_chat_with_hint, partner.name),
                                                color = Color.White.copy(alpha = 0.6f),
                                                fontSize = 15.sp,
                                                lineHeight = 20.sp,
                                            )
                                        }
                                    },
                                    leadingIcon =
                                        if (audioPartyChatChrome) {
                                            {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.Chat,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.65f),
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp, lineHeight = 20.sp),
                                    shape = RoundedCornerShape(26.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.Black.copy(alpha = 0.4f),
                                        unfocusedContainerColor = Color.Black.copy(alpha = 0.4f),
                                        focusedBorderColor = Color.White.copy(alpha = 0.5f),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                    ),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            if (chatMessage.isNotBlank()) {
                                                onSendComment(chatMessage)
                                                onChatMessageChange("")
                                            }
                                        }) {
                                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.live_send), tint = Color.White, modifier = Modifier.size(20.dp))
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = {
                                        if (chatMessage.isNotBlank()) {
                                            onSendComment(chatMessage)
                                            onChatMessageChange("")
                                        }
                                    })
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            if (audioPartyChatChrome) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (isGuestPreview) {
                                                scope.launch { runLockedShake(giftShake); onGuestRestricted() }
                                            } else {
                                                onOpenGiftSheetToHost()
                                            }
                                        },
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                            .size(48.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_audio_party_gift_rich),
                                            contentDescription = stringResource(R.string.live_send_gift_action),
                                            tint = Color(0xFFFFE082),
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = onAudioPartyOpenRoomMenu,
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                            .size(48.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Apps,
                                            contentDescription = stringResource(R.string.live_audio_party_audience_menu_cd),
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    IconButton(onClick = onOpenMessageSheet, modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)) {
                                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = stringResource(R.string.live_message), tint = Color.White)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    IconButton(
                                        onClick = onOpenDiamondShop,
                                        modifier = Modifier
                                            .background(
                                                Brush.linearGradient(listOf(Color(0xFF42A5F5), Color(0xFF1565C0))),
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_diamond),
                                            contentDescription = stringResource(R.string.call_diamond_shop_cd),
                                            tint = Color(0xFF00E5FF)
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    IconButton(
                                        onClick = {
                                            if (isGuestPreview) {
                                                scope.launch { runLockedShake(videoShake); onGuestRestricted() }
                                            } else onCall(partner)
                                        },
                                        modifier = Modifier
                                            .graphicsLayer { translationX = videoShake.value }
                                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                    ) {
                                        Box {
                                            Icon(
                                                Icons.Default.Videocam,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = if (isGuestPreview) 0.45f else 1f)
                                            )
                                            if (isGuestPreview) {
                                                PremiumLockBadge(Modifier.align(Alignment.TopEnd))
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    Button(
                                        onClick = {
                                            if (uiState.isGiftTransactionProcessing) return@Button
                                            if (isGuestPreview) {
                                                scope.launch { runLockedShake(giftShake); onGuestRestricted() }
                                            } else {
                                                onOpenGiftSheetToHost()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .graphicsLayer { alpha = if (isGuestPreview) 0.45f else 1f },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Box {
                                            if (uiState.isGiftTransactionProcessing) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp,
                                                    color = Color.Black
                                                )
                                            } else {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color.Black)
                                                    Text(stringResource(R.string.live_gift), color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            if (isGuestPreview) {
                                                PremiumLockBadge(Modifier.align(Alignment.TopEnd))
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    val likeHeartScale = remember { androidx.compose.animation.core.Animatable(1f) }
                                    LaunchedEffect(likeAnimTrigger) {
                                        if (likeAnimTrigger > 0) {
                                            likeHeartScale.snapTo(1.5f)
                                            likeHeartScale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 600f))
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            onLikeIncrement()
                                            onLikeLive(partner)
                                        },
                                        modifier = Modifier
                                            .graphicsLayer { scaleX = likeHeartScale.value; scaleY = likeHeartScale.value }
                                            .background(ItzoUiTokens.PkBarRight.copy(alpha = 0.85f), CircleShape)
                                            .size(48.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_heart),
                                                contentDescription = "Like",
                                                tint = Color(0xFFFF5252),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            if (likeCount > 0) {
                                                Text("$likeCount", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = expandedChatListModifier
                        .nestedScroll(revealOlderOnOverscroll),
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                    if (!overlayHistoryMode &&
                        messagesToShow.size > LIVE_CHAT_STAGING_VISIBLE_MESSAGES
                    ) {
                        LiveChatOlderMessagesCue(onOpenHistory = { openHistoryRef.value.invoke() })
                    }
                    if (audioPartyChatChrome) {
                        AudioPartyWelcomeBanner(
                            text = stringResource(R.string.live_audio_party_room_welcome),
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 2.dp),
                        )
                        AudioPartyGiftFlyline(
                            messages = uiState.liveStreamChatMessages,
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                        )
                    }
                    val watcherExpandedStagingMax =
                        if (overlayHistoryMode) watcherHistoryStripMaxH else liveChatStagingViewportMaxHeight()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                            if (overlayHistoryMode &&
                                uiState.liveStreamChatMessages.size > watcherHistoryBaselineCount
                            ) {
                                LiveChatNewMessagesCue(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 6.dp, bottom = 6.dp)
                                        .zIndex(1f),
                                    onJumpToLatest = {
                                        overlayHistoryMode = false
                                        watcherHistoryBaselineCount = uiState.liveStreamChatMessages.size
                                        scope.launch {
                                            rollerAnimateScrollToLatest(expandedChatScrollState)
                                        }
                                    },
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = watcherExpandedStagingMax)
                                    .verticalScroll(expandedChatScrollState)
                                    .padding(stagingScrollPadding),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                if (messagesToShow.isEmpty()) {
                                    Text(
                                        stringResource(R.string.call_overlay_chat_empty),
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(8.dp),
                                    )
                                } else {
                                    watcherStagingDisplayedMessages.forEach { m ->
                                        if (audioPartyChatChrome && m.type == "gift") return@forEach
                                        key(m.id.ifBlank { "${m.timestamp}_${m.senderId}" }) {
                                            LiveWatcherStreamChatMessageRow(
                                                m = m,
                                                partner = partner,
                                                resolveChatterProfile = { resolveChatterProfile(it) },
                                                senderAvatarUrl = { senderAvatarUrl(it) },
                                                onPickChatterFromMessage = onPickChatterFromMessage,
                                                audioPartyStyled = audioPartyChatChrome,
                                                viewerUserId = viewerUserId,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        val layoutDirection = LocalLayoutDirection.current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = liveChatListPadding.calculateStartPadding(layoutDirection),
                                    end = liveChatListPadding.calculateEndPadding(layoutDirection),
                                ),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (showDockedComplianceBanner) {
                            LiveStreamComplianceBanner(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 2.dp),
                                variant = LiveStreamComplianceBannerVariant.DockedAboveComposer,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        OutlinedTextField(
                            value = chatMessage,
                            onValueChange = onChatMessageChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp, max = 52.dp),
                            placeholder = {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Text(
                                        stringResource(R.string.live_chat_with_hint, partner.name),
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 15.sp,
                                        lineHeight = 20.sp,
                                    )
                                }
                            },
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp, lineHeight = 20.sp),
                            shape = RoundedCornerShape(26.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Black.copy(alpha = 0.28f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.28f),
                                focusedBorderColor = Color.White.copy(alpha = 0.5f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                            ),
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (chatMessage.isNotBlank()) {
                                        onSendComment(chatMessage)
                                        onChatMessageChange("")
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.live_send), tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (chatMessage.isNotBlank()) {
                                    onSendComment(chatMessage)
                                    onChatMessageChange("")
                                }
                            })
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IconButton(onClick = onOpenMessageSheet, modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = stringResource(R.string.live_message), tint = Color.White)
                        }
                        Spacer(Modifier.height(8.dp))
                        IconButton(
                            onClick = onOpenDiamondShop,
                            modifier = Modifier
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF42A5F5), Color(0xFF1565C0))),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_diamond),
                                contentDescription = stringResource(R.string.call_diamond_shop_cd),
                                tint = Color(0xFF00E5FF)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        IconButton(
                            onClick = {
                                if (isGuestPreview) {
                                    scope.launch { runLockedShake(videoShake); onGuestRestricted() }
                                } else onCall(partner)
                            },
                            modifier = Modifier
                                .graphicsLayer { translationX = videoShake.value }
                                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Box {
                                Icon(
                                    Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = if (isGuestPreview) 0.45f else 1f)
                                )
                                if (isGuestPreview) {
                                    PremiumLockBadge(Modifier.align(Alignment.TopEnd))
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (uiState.isGiftTransactionProcessing) return@Button
                                if (isGuestPreview) {
                                    scope.launch { runLockedShake(giftShake); onGuestRestricted() }
                                } else {
                                    onOpenGiftSheetToHost()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(56.dp)
                                .graphicsLayer { alpha = if (isGuestPreview) 0.45f else 1f },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Box {
                                if (uiState.isGiftTransactionProcessing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.Black
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color.Black)
                                        Text(stringResource(R.string.live_gift), color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (isGuestPreview) {
                                    PremiumLockBadge(Modifier.align(Alignment.TopEnd))
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        val likeHeartScale = remember { androidx.compose.animation.core.Animatable(1f) }
                        LaunchedEffect(likeAnimTrigger) {
                            if (likeAnimTrigger > 0) {
                                likeHeartScale.snapTo(1.5f)
                                likeHeartScale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 600f))
                            }
                        }
                        IconButton(
                            onClick = {
                                onLikeIncrement()
                                onLikeLive(partner)
                            },
                            modifier = Modifier
                                .graphicsLayer { scaleX = likeHeartScale.value; scaleY = likeHeartScale.value }
                                .background(ItzoUiTokens.PkBarRight.copy(alpha = 0.85f), CircleShape)
                                .size(48.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_heart),
                                    contentDescription = "Like",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(20.dp)
                                )
                                if (likeCount > 0) {
                                    Text("$likeCount", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                    }
                }
            }
        }
    }
    }
}

/** Subtle host identity under the spectator name pill (PK watch). */
@Composable
private fun PkHostStreamMetadataPill(
    host: UserProfile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.38f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            modifier = Modifier.size(22.dp),
            shape = CircleShape,
            color = Color(0xFF37474F)
        ) {
            if (host.photoUrl.isNotBlank()) {
                AsyncImage(
                    model = host.photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = host.name.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Text(
            text = host.name.ifBlank { stringResource(R.string.live_pk_battler_host_fallback) },
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = WatcherTopChromeTextShadow)
        )
    }
}

@Composable
private fun RowScope.PkWatcherDualVideoSide(
    surfaceRenderer: SurfaceViewRenderer,
    showNamesOnArena: Boolean,
    name: String,
    gender: String,
    namePillAlignment: Alignment,
    onNameClick: () -> Unit,
    isHostTile: Boolean = false,
    hostIdentity: UserProfile? = null,
    onHostIdentityClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .zIndex(1f)
    ) {
        AndroidView(
            factory = { surfaceRenderer },
            modifier = Modifier.fillMaxSize()
        )
        when {
            !showNamesOnArena && isHostTile && hostIdentity != null -> {
                Column(Modifier.fillMaxWidth()) {
                    PkSpectatorVideoNamePill(
                        name = name,
                        alignment = namePillAlignment,
                        gender = gender,
                        onClick = onNameClick
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = if (namePillAlignment == Alignment.TopStart) 8.dp else 0.dp,
                                end = if (namePillAlignment == Alignment.TopEnd) 8.dp else 0.dp,
                            )
                    ) {
                        PkHostStreamMetadataPill(
                            host = hostIdentity,
                            onClick = { onHostIdentityClick?.invoke() },
                            modifier = Modifier.align(
                                if (namePillAlignment == Alignment.TopStart) {
                                    Alignment.CenterStart
                                } else {
                                    Alignment.CenterEnd
                                }
                            )
                        )
                    }
                }
            }
            !showNamesOnArena -> {
                PkSpectatorVideoNamePill(
                    name = name,
                    alignment = namePillAlignment,
                    gender = gender,
                    onClick = onNameClick
                )
            }
            isHostTile && hostIdentity != null -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, top = 52.dp)
                ) {
                    PkHostStreamMetadataPill(
                        host = hostIdentity,
                        onClick = { onHostIdentityClick?.invoke() }
                    )
                }
            }
            else -> Unit
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LiveWatcherView(
    partner: UserProfile,
    uiState: DatingUiState,
    onStopWatching: () -> Unit,
    onSendGift: (Gift, Int) -> Unit,
    onSendPkBattlerGift: (String, Gift, Int) -> Unit = { _, _, _ -> },
    onPkBattleTimerFinished: () -> Unit = {},
    onFollow: (UserProfile) -> Unit,
    onCall: (UserProfile) -> Unit,
    onMessage: (UserProfile) -> Unit,
    onSendComment: (String) -> Unit,
    onTopUpCoins: () -> Unit,
    onLikeLive: (UserProfile) -> Unit = {},
    isGuestPreview: Boolean = false,
    onGuestRestricted: () -> Unit = {},
    onPrivateGiftToChatter: (UserProfile, Gift, Int) -> Unit = { _, _, _ -> },
    onVideoCallChatter: (UserProfile) -> Unit = {},
    onMessageChatter: (UserProfile) -> Unit = {},
    onSendDmWhileWatching: (UserProfile, String) -> Unit = { _, _ -> },
    onBuyDiamonds: (CoinPackage) -> Unit = {},
    /** Stops the stream and opens the given user's profile (host or PK guest). */
    onOpenStreamUserProfile: (UserProfile) -> Unit = {},
    onStartDmObservationForPartner: (UserProfile) -> Unit = {},
    onClearDmThreadFocus: () -> Unit = {},
    onChatSendMessage: (String, String) -> Unit = { _, _ -> },
    onChatSendImageMessage: (String, Uri) -> Unit = { _, _ -> },
    onChatConsumeImageUploadError: () -> Unit = {},
    onChatSendGiftMessage: (String, Gift) -> Unit = { _, _ -> },
    onChatSendGift: (Gift, Int) -> Unit = { _, _ -> },
    onChatStartCall: (Boolean, String) -> Unit = { _, _ -> },
    onLiveProfileGiftTargetChanged: (String?) -> Unit = {},
    onLikeProfile: (UserProfile) -> Unit = {},
    onGiftToProfile: (UserProfile, Gift, Int) -> Unit = { _, _, _ -> },
    onWatchLive: (UserProfile) -> Unit = {},
    onConfirmPkWatchPerspective: (streamHostId: String, sessionKey: String, rootingForHost: Boolean) -> Unit = { _, _, _ -> },
    /** Swipe on video area: `+1` next live, `-1` previous (wraps). */
    onSwipeToAdjacentLive: (Int) -> Unit = {},
    onBlockUserFromProfile: (UserProfile) -> Unit = {},
    onReportUserFromProfile: (UserProfile, String) -> Unit = { _, _ -> },
    onProcessLuckyGifts: suspend (String, Int, Int) -> LuckyGiftsOutcome,
    onLuckyGiftVisual: (Gift, String, Int, String?) -> Unit = { _, _, _, _ -> },
    onAudioPartyClaimSeat: (String, Int) -> Unit = { _, _ -> },
    onAudioPartyLeaveSeat: (String, Int) -> Unit = { _, _ -> },
    onAudioPartyHostSeatAction: (String, AudioPartyHostSeatAction) -> Unit = { _, _ -> },
    onAudioPartySetMyMic: (String, Boolean) -> Unit = { _, _ -> },
    onAudioPartyMergeNowPlaying: (String, String) -> Unit = { _, _ -> },
    onAudioPartySendEmoji: (String) -> Unit = { },
    onGetAgoraToken: suspend (channelName: String, isPublisher: Boolean) -> String?,
    onRequestAudioPartyVideoCallWithHost: () -> Unit = {},
) {
    val context = LocalContext.current
    var isSpeakerOn by remember { mutableStateOf(true) }
    var hearsStreamerVoice by remember { mutableStateOf(true) }
    var likeCount by remember { mutableIntStateOf(0) }
    var likeAnimTrigger by remember { mutableIntStateOf(0) }
    val toggleWatcherSpeaker: () -> Unit = {
        isSpeakerOn = !isSpeakerOn
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        WebRTCManager.applyLivePlaybackSpeakerMode(am, preferLoudSpeaker = isSpeakerOn)
    }
    val toggleHearStreamer: () -> Unit = {
        hearsStreamerVoice = !hearsStreamerVoice
    }
    val seatMicPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val remoteRenderer = remember {
        SurfaceViewRenderer(context).also { v ->
            // Ensure the viewer's remote video surface is in the default (background) layer
            // so it fills the full screen behind the Compose overlay content.
            v.setZOrderMediaOverlay(false)
            runCatching { v.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL) }
        }
    }
    val dummyRenderer = remember {
        SurfaceViewRenderer(context).also { v ->
            runCatching { v.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL) }
        }
    }
    val dummyPkGuest = remember {
        SurfaceViewRenderer(context).also { v ->
            runCatching { v.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL) }
        }
    }
    val remotePkGuestRenderer = remember {
        SurfaceViewRenderer(context).also { v ->
            v.setZOrderMediaOverlay(false)
            runCatching { v.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL) }
        }
    }
    val apCoLocal = remember {
        List(14) {
            SurfaceViewRenderer(context).also { v ->
                runCatching { v.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL) }
            }
        }
    }
    val apCoRemote = remember {
        List(14) {
            SurfaceViewRenderer(context).also { v ->
                runCatching { v.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL) }
            }
        }
    }
    val seatPubLocal = remember {
        SurfaceViewRenderer(context).also { v ->
            runCatching { v.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL) }
        }
    }
    val seatPubRemote = remember {
        SurfaceViewRenderer(context).also { v ->
            runCatching { v.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL) }
        }
    }
    var webRTCManager by remember { mutableStateOf<WebRTCManager?>(null) }
    var watcherAudioPartyAgora by remember { mutableStateOf<com.zipper.datingapp.agora.AgoraManager?>(null) }
    val pkGuestUid = uiState.watchingPkGuestUid.trim()
    val pkWatchSession = uiState.pkBattleSession
        ?: uiState.watchingPkLingerSession?.takeIf { it.battleEnded && it.pkStartTimeMillis > 0L }
    /**
     * Keep dual tiles through the post-result linger window so [PkArenaEndCard] and
     * [LiveWatcherSpectatorChatChrome] stay on-screen (they require [showPkDualVideo]).
     */
    val pkSpectatorResultLingerActive = pkWatchSession?.let { session ->
        session.battleEnded &&
            session.outcome != null &&
            (session.resultPhaseEndsAtMillis <= 0L ||
                System.currentTimeMillis() < session.resultPhaseEndsAtMillis)
    } ?: false
    /** True while PK scoring / tug is live — false once [PkBattleSessionState.battleEnded]. */
    val pkBattleInProgress = uiState.pkBattleSession?.let { session ->
        !session.battleEnded && session.phase == PkBattlePhase.ACTIVE
    } ?: (uiState.watchingPkActive && pkGuestUid.isNotBlank() && pkGuestUid != partner.id)
    val showPkDualVideo =
        (pkBattleInProgress || pkSpectatorResultLingerActive) &&
            uiState.watchingPkActive &&
            pkGuestUid.isNotBlank() &&
            pkGuestUid != partner.id
    /** Same RTDB / Firestore room id battlers use for Agora (when non-blank, PK video is Agora in-app). */
    val pkWatchAgoraChannel =
        uiState.pkRoomId?.trim().orEmpty().ifBlank { uiState.webRtcSessionRoomId?.trim().orEmpty() }
    val pkSpectatorUsesAgora =
        BuildConfig.AGORA_APP_ID.isNotBlank() &&
            showPkDualVideo &&
            pkWatchAgoraChannel.isNotBlank()
    /** Solo watch + Firestore `audioOnlyStream`; WebRTC recv omits video when true. */
    val soloAudioPartyWatch = !showPkDualVideo && uiState.watchingLiveStreamIsAudioOnly
    /** Agora multi-mic room for audio party (same channel name as host uid). */
    val watcherUsesAgoraAudioParty = soloAudioPartyWatch && BuildConfig.AGORA_APP_ID.isNotBlank()
    LaunchedEffect(soloAudioPartyWatch) {
        if (soloAudioPartyWatch && seatMicPermission.status != PermissionStatus.Granted) {
            seatMicPermission.launchPermissionRequest()
        }
    }
    val watcherConn = remember { LiveWatcherConnHolder() }
    var pkGuestJoinGeneration by remember(partner.id) { mutableIntStateOf(0) }
    val pkWatchActiveTimer = uiState.pkBattleSession != null && uiState.watchingPkActive
    val onPkHudTimerDone: () -> Unit =
        if (pkWatchActiveTimer) onPkBattleTimerFinished else ({ })
    val pkHudVisible = pkWatchSession != null && pkWatchSession.pkStartTimeMillis > 0L &&
        (pkWatchActiveTimer || uiState.watchingPkLingerSession != null)
    val viewersLabel = stringResource(R.string.live_viewers_count, uiState.liveStreamViewerCount)
    val watcherShowViewerCount = pkWatchSession == null
    val pkAudienceChipsActive = pkHudVisible
    val pkHostSpectatorsChipText = if (pkAudienceChipsActive) {
        stringResource(R.string.live_pk_spectators_chip_host, uiState.pkHostViewerCount)
    } else {
        ""
    }
    val pkGuestSpectatorsChipText = if (pkAudienceChipsActive) {
        stringResource(R.string.live_pk_spectators_chip_challenger, uiState.pkGuestViewerCount)
    } else {
        ""
    }

    fun resolveWatchingGuestProfile(): UserProfile {
        val uid = pkGuestUid.trim()
        val fallbackName = uiState.watchingPkGuestDisplayName.trim().ifBlank { "Guest" }
        if (uid.isBlank()) return UserProfile(id = "", name = fallbackName)
        return uiState.profiles.find { it.id == uid }
            ?: uiState.filteredProfiles.find { it.id == uid }
            ?: UserProfile(id = uid, name = fallbackName)
    }

    var chatMessage by remember { mutableStateOf("") }
    var showGiftSheet by remember { mutableStateOf(false) }
    /** null = gift to the live host ([partner]); non-null = private gift to a chatter. */
    var giftRecipientProfile by remember { mutableStateOf<UserProfile?>(null) }
    var chatterSheetProfile by remember { mutableStateOf<UserProfile?>(null) }
    var showChatterGiftPick by remember { mutableStateOf(false) }
    var chatterGiftTarget by remember { mutableStateOf<UserProfile?>(null) }
    val videoShake = remember { Animatable(0f) }
    val giftShake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var showLeaveStreamConfirm by remember { mutableStateOf(false) }
    var dmOverlayPage by remember { mutableStateOf(LiveWatcherDmOverlayPage.Hidden) }
    /** Which user the full DM dialog is showing (host or someone opened from the viewer list). */
    var dmChatOverlayPartner by remember { mutableStateOf(partner) }
    var viewersProfilePopup by remember { mutableStateOf<UserProfile?>(null) }
    var showDiamondShop by remember { mutableStateOf(false) }
    /** PK spectator: host vs challenger audience list (Firestore uid lists). */
    var pkBattlerViewerSheet by remember { mutableStateOf<Pair<List<String>, String>?>(null) }
    /** Solo live: full audience list (mirrors host nameplate tap / PK chips). */
    var soloWatcherViewerListSheet by remember { mutableStateOf(false) }
    var showWatcherApMenu by remember { mutableStateOf(false) }
    var showWatcherApPurchase by remember { mutableStateOf(false) }
    var watcherApPurchaseSeat by remember { mutableIntStateOf(1) }
    var watcherApPurchasePrice by remember { mutableIntStateOf(0) }
    /** Horizontal mirror for both PK tiles (spectator-only; not the host camera). */
    var streamTimerTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(partner.id) {
        while (isActive) {
            delay(1000)
            streamTimerTick++
        }
    }
    val streamDurationLabel = run {
        streamTimerTick // drive recomposition every second
        val startedMs = uiState.watchingLiveStreamStartedAtMillis
        if (startedMs <= 0L) ""
        else {
            val elapsed = ((System.currentTimeMillis() - startedMs) / 1000L).toInt().coerceAtLeast(0)
            stringResource(R.string.live_stream_duration_chip, formatLiveStreamDurationClock(elapsed))
        }
    }
    val pkPerspectiveSessionKey = remember(partner.id, pkGuestUid, pkWatchSession?.pkStartTimeMillis) {
        "${partner.id}|$pkGuestUid|${pkWatchSession?.pkStartTimeMillis ?: 0L}"
    }
    var pkPerspectiveChosenKey by remember { mutableStateOf<String?>(null) }
    val showPkPerspectiveDialog = showPkDualVideo &&
        pkGuestUid.isNotBlank() &&
        pkWatchSession != null &&
        pkPerspectiveChosenKey != pkPerspectiveSessionKey

    val swipeOverlayEnabled = remember(
        showGiftSheet,
        showLeaveStreamConfirm,
        showDiamondShop,
        dmOverlayPage,
        viewersProfilePopup,
        showChatterGiftPick,
        chatterSheetProfile,
        showPkPerspectiveDialog,
        pkBattlerViewerSheet,
        soloWatcherViewerListSheet,
    ) {
        !showGiftSheet && !showLeaveStreamConfirm && !showDiamondShop &&
            dmOverlayPage == LiveWatcherDmOverlayPage.Hidden &&
            viewersProfilePopup == null && !showChatterGiftPick && chatterSheetProfile == null &&
            !showPkPerspectiveDialog &&
            pkBattlerViewerSheet == null &&
            !soloWatcherViewerListSheet
    }
    val swipeMod = rememberLiveWatcherSwipeModifier(swipeOverlayEnabled, partner.id, onSwipeToAdjacentLive)
    val liveSwipeContentDescription = stringResource(R.string.live_swipe_change_stream_cd)

    fun resolveChatterProfile(msg: LiveStreamChatMessage): UserProfile {
        val id = msg.senderId.trim()
        return uiState.profiles.find { it.id == id }
            ?: uiState.filteredProfiles.find { it.id == id }
            ?: UserProfile(id = id, name = msg.senderName.ifBlank { "User" }, profileNumber = "", photoUrl = "")
    }

    val viewerUid = FirebaseAuth.getInstance().currentUser?.uid?.trim().orEmpty()

    var watcherLockedWebRtc by remember(partner.id, uiState.liveWatchSessionNonce) { mutableStateOf(false) }

    val watcherAgoraSoloEligible =
        BuildConfig.AGORA_APP_ID.isNotBlank() &&
            !showPkDualVideo &&
            !soloAudioPartyWatch &&
            !uiState.watchingLiveStreamIsAudioOnly

    val watcherShowAgoraSoloVideo = watcherAgoraSoloEligible && !watcherLockedWebRtc

    val mySeatAudioPublishing = remember(viewerUid, uiState.audioPartyStream) {
        if (viewerUid.isEmpty()) false
        else uiState.audioPartyStream.seats.values.any {
            it.userId == viewerUid && it.publishingAudio && !it.mutedByHost
        }
    }
    var watcherApAgoraToken by remember(partner.id, viewerUid) { mutableStateOf<String?>(null) }
    var watcherApAgoraTokenLoading by remember { mutableStateOf(false) }
    LaunchedEffect(
        partner.id,
        viewerUid,
        watcherUsesAgoraAudioParty,
        mySeatAudioPublishing,
        seatMicPermission.status,
    ) {
        if (!watcherUsesAgoraAudioParty || viewerUid.isEmpty()) {
            watcherApAgoraToken = null
            return@LaunchedEffect
        }
        val needPub = mySeatAudioPublishing && seatMicPermission.status == PermissionStatus.Granted
        watcherApAgoraTokenLoading = true
        watcherApAgoraToken =
            try {
                withTimeout(20_000L) {
                    onGetAgoraToken(partner.id.trim(), needPub)
                }
            } catch (_: Exception) {
                null
            } finally {
                watcherApAgoraTokenLoading = false
            }
    }
    val isFollowingHost = remember(partner.id, uiState.currentUser?.followingIds, uiState.followedProfiles) {
        val ids = uiState.currentUser?.followingIds.orEmpty().toSet()
        partner.id in ids || uiState.followedProfiles.any { it.id == partner.id }
    }
    LaunchedEffect(partner.id) {
        dmChatOverlayPartner = partner
        dmOverlayPage = LiveWatcherDmOverlayPage.Hidden
    }
    fun closeDmOverlay() {
        dmOverlayPage = LiveWatcherDmOverlayPage.Hidden
        onClearDmThreadFocus()
    }
    val openHostFullChat: () -> Unit = {
        dmChatOverlayPartner = partner
        onStartDmObservationForPartner(partner)
        dmOverlayPage = LiveWatcherDmOverlayPage.Thread
    }
    LaunchedEffect(viewersProfilePopup?.id) {
        onLiveProfileGiftTargetChanged(viewersProfilePopup?.id)
    }
    fun resolveThreadPartner(seed: UserProfile): UserProfile {
        val id = seed.id
        return uiState.profiles.find { it.id == id }
            ?: uiState.filteredProfiles.find { it.id == id }
            ?: uiState.chatPartnerProfile?.takeIf { it.id == id }
            ?: uiState.dmThreadProfiles[id]
            ?: seed
    }
    val openStreamProfilePeek: (UserProfile) -> Unit = { seed ->
        viewersProfilePopup = resolveThreadPartner(seed)
    }
    val dmThreadResolved = remember(
        dmChatOverlayPartner.id,
        uiState.profiles,
        uiState.filteredProfiles,
        uiState.chatPartnerProfile,
        uiState.dmThreadProfiles,
        dmChatOverlayPartner
    ) {
        resolveThreadPartner(dmChatOverlayPartner)
    }
    val resolvedViewerPopupProfile = remember(
        viewersProfilePopup?.id,
        uiState.profiles,
        uiState.filteredProfiles,
        uiState.chatPartnerProfile,
        uiState.dmThreadProfiles,
        viewersProfilePopup
    ) {
        val seed = viewersProfilePopup ?: return@remember null
        val id = seed.id
        uiState.profiles.find { it.id == id }
            ?: uiState.filteredProfiles.find { it.id == id }
            ?: uiState.chatPartnerProfile?.takeIf { it.id == id }
            ?: uiState.dmThreadProfiles[id]
            ?: seed
    }
    val pkBattleForWebRtc = pkWatchSession
    DisposableEffect(
        partner.id,
        viewerUid,
        showPkDualVideo,
        pkSpectatorUsesAgora,
        uiState.watchingLiveStreamIsAudioOnly,
        pkBattleForWebRtc?.hostUserId,
        pkBattleForWebRtc?.guestUserId,
        uiState.liveWatchSessionNonce,
    ) {
        if (viewerUid.isEmpty()) {
            Log.w("LiveScreen", "LiveWatcherView: no signed-in uid; WebRTC broadcast skipped")
            return@DisposableEffect onDispose { }
        }
        if (pkSpectatorUsesAgora) {
            return@DisposableEffect onDispose { }
        }
        if (watcherUsesAgoraAudioParty) {
            return@DisposableEffect onDispose { }
        }
        isSpeakerOn = true
        hearsStreamerVoice = true
        Log.e(
            "E2E_DIAG_LIVE",
            "Live Stream - isHost: false, watched=${partner.id}, ViewerID=$viewerUid dual=$showPkDualVideo pkRoot=${pkBattleForWebRtc?.hostUserId}"
        )
        webRTCManager = null
        val job = scope.launch {
            try {
                if (!showPkDualVideo) {
                    val streamerUid = partner.id.trim()
                    if (streamerUid.isEmpty()) {
                        Log.e("LiveScreen", "Solo viewer: streamer uid blank; WebRTC skipped")
                        return@launch
                    }
                    val soloVideoWatch =
                        !soloAudioPartyWatch && !uiState.watchingLiveStreamIsAudioOnly
                    if (soloVideoWatch && BuildConfig.AGORA_APP_ID.isBlank()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.live_video_requires_agora),
                            Toast.LENGTH_LONG,
                        ).show()
                        onStopWatching()
                        return@launch
                    }
                    if (watcherAgoraSoloEligible && !watcherLockedWebRtc) {
                        return@launch
                    }
                    watcherLockedWebRtc = true
                    // Host solo live uses rooms/{host Firebase uid}/… — [streamerUid] must match the host’s WebRTC roomId.
                    Log.i(
                        "LiveScreen",
                        "Solo viewer WebRTC: roomId=$streamerUid (must match host rooms/{streamerUid}) " +
                            "viewerSignalingId=$viewerUid remoteSurface=${remoteRenderer.hashCode()}"
                    )
                    val solo = WebRTCManager(
                        context = context,
                        roomId = streamerUid,
                        localView = dummyRenderer,
                        remoteView = remoteRenderer,
                        broadcastMode = true,
                        viewerSignalingId = viewerUid,
                        audioOnly = uiState.watchingLiveStreamIsAudioOnly,
                    )
                    watcherConn.host = solo
                    webRTCManager = solo
                    solo.joinCall()
                    return@launch
                }
                watcherLockedWebRtc = true
                val session = pkBattleForWebRtc ?: return@launch
                val root = session.hostUserId.trim()
                val hostRole = session.hostUserId.trim()
                val guestRole = session.guestUserId.trim()
                if (root.isBlank() || hostRole.isBlank() || guestRole.isBlank()) return@launch
                val audioOnHostTile = partner.id == hostRole
                val audioOnGuestTile = partner.id == guestRole
                val hostTile = WebRTCManager(
                    context = context,
                    roomId = root,
                    localView = dummyRenderer,
                    remoteView = remoteRenderer,
                    broadcastMode = true,
                    viewerSignalingId = viewerUid,
                    broadcastViewerReceiveAudio = audioOnHostTile,
                    broadcastLivePublishersSessionId = root,
                    broadcastLivePublishersPublisherUid = hostRole,
                )
                watcherConn.host = hostTile
                webRTCManager = hostTile
                hostTile.joinCall()
                repeat(100) {
                    if (!isActive) return@launch
                    val ex = hostTile.tryExportFactoryForSecondaryViewer()
                    if (ex != null) {
                        val guestTile = WebRTCManager(
                            context = context,
                            roomId = root,
                            localView = dummyPkGuest,
                            remoteView = remotePkGuestRenderer,
                            broadcastMode = true,
                            viewerSignalingId = viewerUid,
                            broadcastViewerReceiveAudio = audioOnGuestTile,
                            broadcastLivePublishersSessionId = root,
                            broadcastLivePublishersPublisherUid = guestRole,
                            injectedPeerConnectionFactory = ex.first,
                            injectedEglBase = ex.second,
                            publishGlobalMediaStreams = false,
                            takeCurrentManagerSlot = false
                        )
                        watcherConn.guest = guestTile
                        guestTile.joinCall()
                        runCatching { guestTile.refreshVideoSinkAttachments("pk_watcher_guest_joined") }
                        pkGuestJoinGeneration++
                        Log.d(
                            "LiveScreen",
                            "LiveWatcher PK tiles live_publishers/$root host=$hostRole guest=$guestRole audioFollows=${partner.id}"
                        )
                        return@launch
                    }
                    delay(120)
                }
                Log.w("LiveScreen", "LiveWatcher: PK second WebRTC link not started (factory not ready)")
            } catch (e: Exception) {
                Log.e("CRASH_PK", "LiveWatcherView WebRTC hostId=${partner.id}", e)
                Toast.makeText(context, "Failed to join live stream. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
        onDispose {
            job.cancel()
            watcherConn.clearAudioPartyCoSubs()
            watcherConn.disposeSeatPublisher()
            watcherConn.guest?.onDestroy()
            watcherConn.host?.onDestroy()
            watcherConn.guest = null
            watcherConn.host = null
            webRTCManager = null
            pkGuestJoinGeneration = 0
        }
    }

    LaunchedEffect(partner.id, viewerUid, soloAudioPartyWatch, uiState.liveWatchSessionNonce) {
        if (!soloAudioPartyWatch || viewerUid.isEmpty()) {
            watcherConn.clearAudioPartyCoSubs()
            return@LaunchedEffect
        }
        if (watcherUsesAgoraAudioParty) {
            watcherConn.clearAudioPartyCoSubs()
            return@LaunchedEffect
        }
        snapshotFlow {
            uiState.audioPartyStream.activeCoPublisherUids(partner.id, excludeUid = viewerUid).sorted()
        }
            .distinctUntilChanged()
            .collectLatest { uids ->
                watcherConn.clearAudioPartyCoSubs()
                delay(400)
                val primary = watcherConn.host ?: return@collectLatest
                for ((idx, coUid) in uids.withIndex()) {
                    if (idx >= apCoLocal.size) break
                    var started = false
                    for (attempt in 0 until 60) {
                        if (!isActive) return@collectLatest
                        val ex = primary.tryExportFactoryForSecondaryViewer()
                        if (ex != null) {
                            try {
                                val mgr = WebRTCManager(
                                    context = context,
                                    roomId = partner.id.trim(),
                                    localView = apCoLocal[idx],
                                    remoteView = apCoRemote[idx],
                                    broadcastMode = true,
                                    viewerSignalingId = viewerUid,
                                    broadcastViewerReceiveAudio = true,
                                    broadcastLivePublishersSessionId = partner.id.trim(),
                                    broadcastLivePublishersPublisherUid = coUid,
                                    audioOnly = true,
                                    injectedPeerConnectionFactory = ex.first,
                                    injectedEglBase = ex.second,
                                    publishGlobalMediaStreams = false,
                                    takeCurrentManagerSlot = false,
                                )
                                watcherConn.audioPartyCoSubs.add(mgr)
                                mgr.joinCall()
                                started = true
                            } catch (e: Exception) {
                                Log.e("LiveScreen", "LiveWatcher audio_party co uid=$coUid", e)
                            }
                            break
                        }
                        delay(100)
                    }
                    if (!started) {
                        Log.w("LiveScreen", "LiveWatcher audio_party co not started uid=$coUid")
                    }
                }
            }
    }

    LaunchedEffect(partner.id, viewerUid, soloAudioPartyWatch, mySeatAudioPublishing, webRTCManager, seatMicPermission.status) {
        if (watcherUsesAgoraAudioParty) {
            watcherConn.disposeSeatPublisher()
            return@LaunchedEffect
        }
        if (!soloAudioPartyWatch || viewerUid.isEmpty() || !mySeatAudioPublishing || webRTCManager == null) {
            watcherConn.disposeSeatPublisher()
            return@LaunchedEffect
        }
        if (seatMicPermission.status != PermissionStatus.Granted) return@LaunchedEffect
        delay(300)
        try {
            watcherConn.disposeSeatPublisher()
            val pub = WebRTCManager(
                context = context,
                roomId = partner.id.trim(),
                localView = seatPubLocal,
                remoteView = seatPubRemote,
                broadcastMode = true,
                viewerSignalingId = partner.id.trim(),
                broadcastViewerReceiveAudio = false,
                broadcastLivePublishersSessionId = partner.id.trim(),
                broadcastLivePublishersPublisherUid = viewerUid,
                audioOnly = true,
            )
            watcherConn.seatPublisher = pub
            pub.joinCall()
        } catch (e: Exception) {
            Log.e("LiveScreen", "LiveWatcher audio_party seat publish", e)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, partner.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                watcherConn.host?.refreshVideoSinkAttachments("watcher_on_resume_host")
                watcherConn.guest?.refreshVideoSinkAttachments("watcher_on_resume_pk_guest")
                watcherConn.audioPartyCoSubs.forEach {
                    it.refreshVideoSinkAttachments("watcher_on_resume_ap_co")
                }
                watcherConn.seatPublisher?.refreshVideoSinkAttachments("watcher_on_resume_seat_pub")
                WebRTCManager.activeManager()?.refreshVideoSinkAttachments("watcher_active_manager_on_resume")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(webRTCManager, pkGuestJoinGeneration) {
        delay(if (showPkDualVideo) 900 else 650)
        watcherConn.host?.refreshVideoSinkAttachments("watcher_post_join_stabilize_host")
        watcherConn.guest?.refreshVideoSinkAttachments("watcher_post_join_stabilize_pk_guest")
        watcherConn.audioPartyCoSubs.forEach {
            it.refreshVideoSinkAttachments("watcher_post_join_stabilize_ap_co")
        }
        watcherConn.seatPublisher?.refreshVideoSinkAttachments("watcher_post_join_stabilize_seat_pub")
    }

    LaunchedEffect(
        hearsStreamerVoice,
        webRTCManager,
        pkGuestJoinGeneration,
        watcherAudioPartyAgora,
        watcherUsesAgoraAudioParty,
    ) {
        watcherConn.host?.setRemoteAudioHearEnabled(hearsStreamerVoice)
        watcherConn.guest?.setRemoteAudioHearEnabled(hearsStreamerVoice)
        watcherConn.audioPartyCoSubs.forEach { it.setRemoteAudioHearEnabled(hearsStreamerVoice) }
        watcherConn.seatPublisher?.setRemoteAudioHearEnabled(hearsStreamerVoice)
        if (watcherUsesAgoraAudioParty) {
            watcherAudioPartyAgora?.muteAllRemoteAudioStreams(!hearsStreamerVoice)
        }
    }

    val itzoSoloWatcherChrome = !showPkDualVideo && !soloAudioPartyWatch
    fun watcherAudiencePhotoLookup(uid: String): String? =
        uiState.profiles.find { it.id == uid }?.photoUrl?.takeIf { it.isNotBlank() }
            ?: uiState.filteredProfiles.find { it.id == uid }?.photoUrl?.takeIf { it.isNotBlank() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                when {
                    showPkDualVideo -> Modifier.background(Color.Black)
                    soloAudioPartyWatch -> Modifier
                    itzoSoloWatcherChrome -> Modifier.background(Color(0xFF0F0F0F))
                    else -> Modifier.background(ItzoUiTokens.AppBackgroundDeep)
                },
            )
    ) {
        if (!showPkDualVideo) {
            when {
                itzoSoloWatcherChrome -> {
                    ItzoSoloLiveBackdrop(isHost = false, modifier = Modifier.fillMaxSize())
                }
                soloAudioPartyWatch -> {
                    AudioPartyFullBleedBackdrop(
                        roomBackgroundKey = uiState.audioPartyStream.roomBackgroundKey,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(ItzoUiTokens.AudioPartyStageVeil),
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ItzoUiTokens.stageBackgroundBrush()),
                    )
                }
            }
        }
        if (watcherUsesAgoraAudioParty && viewerUid.isNotEmpty() && !watcherApAgoraTokenLoading && watcherApAgoraToken != null) {
            AgoraAudioPartyRtcLayer(
                channelId = partner.id.trim(),
                firebaseUid = viewerUid,
                joinAsBroadcaster = mySeatAudioPublishing,
                micPermissionGranted = seatMicPermission.status == PermissionStatus.Granted,
                token = watcherApAgoraToken,
                signalHostStreamReady = false,
                onEngineReady = { watcherAudioPartyAgora = it },
                onJoinedChannel = {},
                onHostStreamReady = {},
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
        // Hidden 1px surfaces — post() targets for WebRTC init (host + optional PK guest links).
        AndroidView(
            factory = { dummyRenderer },
            modifier = Modifier
                .size(1.dp)
                .align(Alignment.TopStart)
        )
        if (showPkDualVideo && !pkSpectatorUsesAgora) {
            AndroidView(
                factory = { dummyPkGuest },
                modifier = Modifier
                    .size(1.dp)
                    .align(Alignment.TopStart)
                    .offset(2.dp, 0.dp)
            )
        }
        if (showPkDualVideo) {
            val challengerDisplayName =
                uiState.watchingPkGuestDisplayName.trim().ifBlank { "Challenger" }
            val watcherChallengerGender = resolvePkBattlerGender(pkGuestUid, uiState)
            Column(modifier = Modifier.fillMaxSize()) {
                val pkSessionForHud = pkWatchSession!!
                streamTimerTick
                val pkEndOverlay =
                    pkHudVisible && pkSessionForHud.battleEnded && pkSessionForHud.outcome != null &&
                        (pkSessionForHud.resultPhaseEndsAtMillis <= 0L ||
                            System.currentTimeMillis() < pkSessionForHud.resultPhaseEndsAtMillis)
                val pkSpectatorHostGender = resolvePkBattlerGender(pkSessionForHud.hostUserId, uiState)
                val pkSpectatorGuestGender = resolvePkBattlerGender(pkSessionForHud.guestUserId, uiState)
                val pkWatcherNamesOnArena = pkHudVisible && !pkEndOverlay
                val guestProfileLevel =
                    uiState.profiles.find { it.id == pkGuestUid }?.level
                        ?: uiState.filteredProfiles.find { it.id == pkGuestUid }?.level
                        ?: 1
                Box(
                    modifier = Modifier
                        .weight(55f)
                        .fillMaxWidth()
                        .clip(RectangleShape)
                ) {
                    if (pkSpectatorUsesAgora) {
                        AgoraPkSpectatorSplit(
                            channelName = pkWatchAgoraChannel,
                            myFirebaseUid = viewerUid,
                            hostFirebaseUid = pkSessionForHud.hostUserId.trim(),
                            guestFirebaseUid = pkSessionForHud.guestUserId.trim(),
                            hostOnLeft = uiState.pkWatchAudienceHostOnLeft,
                            onGetAgoraToken = onGetAgoraToken,
                            hearsRemoteAudio = hearsStreamerVoice,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .zIndex(0f),
                        )
                    } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(0f)
                    ) {
                        val hostLeft = uiState.pkWatchAudienceHostOnLeft
                        if (hostLeft) {
                            PkWatcherDualVideoSide(
                                surfaceRenderer = remoteRenderer,
                                showNamesOnArena = pkWatcherNamesOnArena,
                                name = partner.name.ifBlank { "Host" },
                                gender = partner.gender,
                                namePillAlignment = Alignment.TopStart,
                                onNameClick = { openStreamProfilePeek(partner) },
                                isHostTile = true,
                                hostIdentity = partner,
                                onHostIdentityClick = { openStreamProfilePeek(partner) }
                            )
                            PkWatcherDualVideoSide(
                                surfaceRenderer = remotePkGuestRenderer,
                                showNamesOnArena = pkWatcherNamesOnArena,
                                name = challengerDisplayName,
                                gender = watcherChallengerGender,
                                namePillAlignment = Alignment.TopEnd,
                                onNameClick = { openStreamProfilePeek(resolveWatchingGuestProfile()) }
                            )
                        } else {
                            PkWatcherDualVideoSide(
                                surfaceRenderer = remotePkGuestRenderer,
                                showNamesOnArena = pkWatcherNamesOnArena,
                                name = challengerDisplayName,
                                gender = watcherChallengerGender,
                                namePillAlignment = Alignment.TopStart,
                                onNameClick = { openStreamProfilePeek(resolveWatchingGuestProfile()) }
                            )
                            PkWatcherDualVideoSide(
                                surfaceRenderer = remoteRenderer,
                                showNamesOnArena = pkWatcherNamesOnArena,
                                name = partner.name.ifBlank { "Host" },
                                gender = partner.gender,
                                namePillAlignment = Alignment.TopEnd,
                                onNameClick = { openStreamProfilePeek(partner) },
                                isHostTile = true,
                                hostIdentity = partner,
                                onHostIdentityClick = { openStreamProfilePeek(partner) }
                            )
                        }
                    }
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .zIndex(2.5f)
                            .then(swipeMod)
                            .semantics { contentDescription = liveSwipeContentDescription }
                    )
                    if (pkHudVisible && !pkEndOverlay) {
                        PkArenaSpectatorBattleHud(
                            session = pkSessionForHud,
                            modifier = Modifier
                                .matchParentSize()
                                .zIndex(4f),
                            hostGender = pkSpectatorHostGender,
                            guestGender = pkSpectatorGuestGender,
                            onTimerFinished = onPkHudTimerDone,
                            hostDisplayName = partner.name.ifBlank { "Host" },
                            guestDisplayName = challengerDisplayName,
                            hostLevel = partner.level.coerceAtLeast(1),
                            guestLevel = guestProfileLevel.coerceAtLeast(1),
                            hostOnLeft = uiState.pkWatchAudienceHostOnLeft,
                            onHostProfileClick = { openStreamProfilePeek(partner) },
                            onGuestProfileClick = { openStreamProfilePeek(resolveWatchingGuestProfile()) },
                            liveChatMessages = uiState.liveStreamChatMessages,
                            liveChatSenderPhotoLookup = { uid ->
                                uiState.profiles.find { it.id == uid }?.photoUrl?.takeIf { it.isNotBlank() }
                                    ?: uiState.filteredProfiles.find { it.id == uid }?.photoUrl?.takeIf {
                                        it.isNotBlank()
                                    }
                            },
                            giftPriceLookup = { id ->
                                uiState.availableGifts.find { it.id == id }?.price?.coerceAtLeast(0) ?: 0
                            },
                        )
                    }
                    if (pkEndOverlay) {
                        val spectMvp = pkMvpSupporterPhotoUrls(
                            messages = uiState.liveStreamChatMessages,
                            hostUserId = pkSessionForHud.hostUserId,
                            senderPhotoLookup = { uid ->
                                uiState.profiles.find { it.id == uid }?.photoUrl?.takeIf { it.isNotBlank() }
                                    ?: uiState.filteredProfiles.find { it.id == uid }?.photoUrl?.takeIf {
                                        it.isNotBlank()
                                    }
                            },
                            giftPriceLookup = { id ->
                                uiState.availableGifts.find { it.id == id }?.price?.coerceAtLeast(0) ?: 0
                            },
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .zIndex(8f)
                        ) {
                            PkArenaEndCard(
                                session = pkSessionForHud,
                                modifier = Modifier.fillMaxSize(),
                                mvpViewerPhotoUrls = spectMvp
                            )
                        }
                    }
                    val pkTopChromeInset =
                        if (pkHudVisible && !pkEndOverlay) 52.dp else 0.dp
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .zIndex(6f)
                    ) {
                        PkWatcherTopRightChrome(
                            streamDurationLabel = streamDurationLabel,
                            isSpeakerOn = isSpeakerOn,
                            onToggleSpeaker = toggleWatcherSpeaker,
                            hearsStreamerVoice = hearsStreamerVoice,
                            onToggleHearStreamer = toggleHearStreamer,
                            onCloseRequest = { showLeaveStreamConfirm = true },
                            extraTopInset = pkTopChromeInset,
                            showSpeakerInTopChrome = false,
                        )
                    }
                }
                LiveWatcherChatPane(
                    modifier = Modifier
                        .weight(45f)
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 10.dp)
                        .padding(top = 8.dp),
                    uiState = uiState,
                    partner = partner,
                    chatMessage = chatMessage,
                    onChatMessageChange = { chatMessage = it },
                    likeCount = likeCount,
                    onLikeIncrement = {
                        likeCount++
                        likeAnimTrigger++
                    },
                    likeAnimTrigger = likeAnimTrigger,
                    isGuestPreview = isGuestPreview,
                    scope = scope,
                    videoShake = videoShake,
                    giftShake = giftShake,
                    onGuestRestricted = onGuestRestricted,
                    onSendComment = onSendComment,
                    onCall = onCall,
                    onMessage = onMessage,
                    onLikeLive = onLikeLive,
                    onPickChatterFromMessage = { m ->
                        if (m.senderId.isNotBlank() && m.senderId != partner.id) {
                            chatterSheetProfile = resolveChatterProfile(m)
                        }
                    },
                    onOpenGiftSheetToHost = {
                        giftRecipientProfile = null
                        showGiftSheet = true
                    },
                    onOpenMessageSheet = openHostFullChat,
                    onOpenDiamondShop = { showDiamondShop = true },
                    pkSpectatorViewersLabel = "",
                    onPkSpectatorViewersClick = {},
                    pkHostSpectatorsChip = pkHostSpectatorsChipText,
                    pkGuestSpectatorsChip = pkGuestSpectatorsChipText,
                    onPkHostSpectatorsChipClick = {
                        if (pkAudienceChipsActive) {
                            pkBattlerViewerSheet = uiState.pkHostViewerUserIds to pkHostSpectatorsChipText
                        }
                    },
                    onPkGuestSpectatorsChipClick = {
                        if (pkAudienceChipsActive) {
                            pkBattlerViewerSheet = uiState.pkGuestViewerUserIds to pkGuestSpectatorsChipText
                        }
                    },
                    pkAllViewersLine = "",
                    onPkAllViewersClick = {},
                    pkSpectatorSpeakerOn = isSpeakerOn,
                    onPkSpectatorSpeakerToggle = toggleWatcherSpeaker,
                    pkSpectatorHearsStreamer = hearsStreamerVoice,
                    onPkSpectatorHearToggle = toggleHearStreamer,
                    spectatorPkBattleChrome = true,
                    showStreamControlHeader = false,
                )
            }
        } else {
            val hideSoloVideoForBusy = uiState.watchingHostPrivateCallBusy && !uiState.watchingPkActive
            if (soloAudioPartyWatch) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 36.dp)
                        .zIndex(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AudioPartyStageLayout(
                        seatCount = uiState.watchingLiveAudioPartySeatCount,
                        stream = uiState.audioPartyStream,
                        hostUserId = partner.id,
                        myUserId = viewerUid,
                        isHost = false,
                        title = "",
                        subtitle = "",
                        hostDisplayName = partner.name.ifBlank { stringResource(R.string.live_pk_battler_host_fallback) },
                        hostPhotoUrl = partner.photoUrl.orEmpty(),
                        hostProfileNumber = partner.profileNumber.orEmpty(),
                        hostBeans = partner.beans,
                        hostHasBlueTick = partner.hasBlueTick,
                        showSeatCountControls = false,
                        onSeatCountSelected = { },
                        onClaimSeat = { idx -> onAudioPartyClaimSeat(partner.id, idx) },
                        onLeaveSeat = { idx -> onAudioPartyLeaveSeat(partner.id, idx) },
                        onToggleMySeatMic = { on -> onAudioPartySetMyMic(partner.id, on) },
                        onSendStageEmoji = onAudioPartySendEmoji,
                    )
                }
                if (hideSoloVideoForBusy) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(1.5f)
                            .background(Color.Black)
                    )
                }
                AndroidView(
                    factory = { remoteRenderer },
                    update = { v ->
                        v.visibility = View.VISIBLE
                        v.setZOrderMediaOverlay(false)
                        runCatching { v.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL) }
                        v.requestLayout()
                    },
                    modifier = Modifier
                        .size(1.dp)
                        .align(Alignment.TopStart)
                        .zIndex(0f)
                        .graphicsLayer { alpha = if (hideSoloVideoForBusy) 0f else 1f }
                )
            } else {
                if (hideSoloVideoForBusy) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(0f)
                            .background(Color.Black)
                    )
                }
                if (watcherShowAgoraSoloVideo && viewerUid.isNotEmpty()) {
                    val watchChannelId = partner.id.trim()
                    var agoraToken by remember(watchChannelId, viewerUid) { mutableStateOf<String?>(null) }
                    var isFetchingToken by remember(watchChannelId, viewerUid) { mutableStateOf(true) }
                    LaunchedEffect(watchChannelId, viewerUid) {
                        isFetchingToken = true
                        agoraToken =
                            try {
                                withTimeout(20_000L) {
                                    onGetAgoraToken(watchChannelId, false)
                                }
                            } catch (_: TimeoutCancellationException) {
                                Log.w("LiveScreen", "getAgoraToken watcher timeout channel=$watchChannelId")
                                null
                            } finally {
                                isFetchingToken = false
                            }
                    }
                    if (isFetchingToken) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .zIndex(0f)
                                    .graphicsLayer { alpha = if (hideSoloVideoForBusy) 0f else 1f },
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        AgoraLiveScreen(
                            channelId = watchChannelId,
                            firebaseUid = viewerUid,
                            isBroadcaster = false,
                            token = agoraToken,
                            localVideoScale = AgoraVideoScaleMode.Hidden,
                            remoteVideoScale = AgoraVideoScaleMode.Hidden,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .zIndex(0f)
                                    .graphicsLayer { alpha = if (hideSoloVideoForBusy) 0f else 1f },
                        )
                    }
                } else {
                    AndroidView(
                        factory = { remoteRenderer },
                        update = { v ->
                            v.visibility = View.VISIBLE
                            v.setZOrderMediaOverlay(false)
                            runCatching { v.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL) }
                            v.requestLayout()
                        },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .zIndex(0f)
                                .graphicsLayer { alpha = if (hideSoloVideoForBusy) 0f else 1f },
                    )
                }
            }
            if (!soloAudioPartyWatch) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(3f)
                        .then(swipeMod)
                        .semantics { contentDescription = liveSwipeContentDescription }
                )
            }
            if (uiState.watchingHostPrivateCallBusy && !uiState.watchingPkActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(5f)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.live_host_busy_overlay),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
            if (pkHudVisible) {
                val sessSingle = pkWatchSession!!
                val guestLvSingle =
                    uiState.profiles.find { it.id == pkGuestUid }?.level
                        ?: uiState.filteredProfiles.find { it.id == pkGuestUid }?.level ?: 1
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.42f)
                        .zIndex(4f)
                ) {
                    PkArenaHud(
                        session = sessSingle,
                        modifier = Modifier.fillMaxSize(),
                        hostGender = resolvePkBattlerGender(sessSingle.hostUserId, uiState),
                        guestGender = resolvePkBattlerGender(sessSingle.guestUserId, uiState),
                        onTimerFinished = onPkHudTimerDone,
                        hostDisplayName = partner.name.ifBlank { stringResource(R.string.live_pk_battler_host_fallback) },
                        guestDisplayName = uiState.watchingPkGuestDisplayName.trim()
                            .ifBlank { stringResource(R.string.live_pk_battler_guest_fallback) },
                        hostLevel = partner.level.coerceAtLeast(1),
                        guestLevel = guestLvSingle.coerceAtLeast(1),
                        hostOnLeft = uiState.pkWatchAudienceHostOnLeft,
                        onHostProfileClick = { openStreamProfilePeek(partner) },
                        onGuestProfileClick = { openStreamProfilePeek(resolveWatchingGuestProfile()) },
                        liveChatMessages = uiState.liveStreamChatMessages,
                        liveChatSenderPhotoLookup = { uid ->
                            uiState.profiles.find { it.id == uid }?.photoUrl?.takeIf { it.isNotBlank() }
                                ?: uiState.filteredProfiles.find { it.id == uid }?.photoUrl?.takeIf {
                                    it.isNotBlank()
                                }
                        },
                        giftPriceLookup = { id ->
                            uiState.availableGifts.find { it.id == id }?.price?.coerceAtLeast(0) ?: 0
                        },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(6f)
            ) {
                when {
                    itzoSoloWatcherChrome || soloAudioPartyWatch -> {
                        ItzoSoloLiveWatcherTopChrome(
                            partner = partner,
                            viewerCount = uiState.liveStreamViewerCount,
                            viewerUserIds = uiState.liveAudienceViewerUserIds,
                            lookupPhotoUrl = { uid -> watcherAudiencePhotoLookup(uid) },
                            streamTimerLabel = streamDurationLabel,
                            onOpenHostProfile = { openStreamProfilePeek(partner) },
                            onViewersClick = { soloWatcherViewerListSheet = true },
                            onCloseClick = { showLeaveStreamConfirm = true },
                            isSpeakerOn = isSpeakerOn,
                            onToggleSpeaker = toggleWatcherSpeaker,
                            hearsStreamerVoice = hearsStreamerVoice,
                            onToggleHearStreamer = toggleHearStreamer,
                            onStreamMenuClick =
                                if (soloAudioPartyWatch) {
                                    { showWatcherApMenu = true }
                                } else {
                                    null
                                },
                            modifier =
                                Modifier
                                    .align(Alignment.TopCenter)
                                    .statusBarsPadding()
                                    .padding(top = if (pkHudVisible) 112.dp else 0.dp),
                        )
                    }
                    else -> {
                        WatcherVideoTopChrome(
                            partner = partner,
                            viewersLabel = viewersLabel,
                            streamDurationLabel = streamDurationLabel,
                            onViewersClick = { soloWatcherViewerListSheet = true },
                            showViewerCount = watcherShowViewerCount,
                            isSpeakerOn = isSpeakerOn,
                            onToggleSpeaker = toggleWatcherSpeaker,
                            hearsStreamerVoice = hearsStreamerVoice,
                            onToggleHearStreamer = toggleHearStreamer,
                            onCloseRequest = { showLeaveStreamConfirm = true },
                            onOpenHostProfile = { openStreamProfilePeek(partner) },
                            isFollowingHost = isFollowingHost,
                            onToggleFollowHost = { onFollow(partner) },
                            isGuestPreview = isGuestPreview,
                            onGuestRestricted = onGuestRestricted,
                            extraTopInset = if (pkHudVisible) 112.dp else 0.dp,
                            modifier = Modifier.align(Alignment.TopCenter),
                            pkHostSpectatorsChip = if (pkAudienceChipsActive && !showPkDualVideo) {
                                pkHostSpectatorsChipText
                            } else {
                                null
                            },
                            pkGuestSpectatorsChip = if (pkAudienceChipsActive && !showPkDualVideo) {
                                pkGuestSpectatorsChipText
                            } else {
                                null
                            },
                            onPkHostSpectatorsClick = if (pkAudienceChipsActive && !showPkDualVideo) {
                                { pkBattlerViewerSheet = uiState.pkHostViewerUserIds to pkHostSpectatorsChipText }
                            } else {
                                null
                            },
                            onPkGuestSpectatorsClick = if (pkAudienceChipsActive && !showPkDualVideo) {
                                { pkBattlerViewerSheet = uiState.pkGuestViewerUserIds to pkGuestSpectatorsChipText }
                            } else {
                                null
                            },
                            allSpectatorsLine = null,
                            onAllSpectatorsClick = null,
                            audioPartyLightRoom = false,
                        )
                    }
                }
            }
        }

        if (!showPkDualVideo) {
            LiveWatcherChatPane(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .background(Color.Transparent)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp)
                    .zIndex(5f),
                uiState = uiState,
                partner = partner,
                chatMessage = chatMessage,
                onChatMessageChange = { chatMessage = it },
                likeCount = likeCount,
                onLikeIncrement = {
                    likeCount++
                    likeAnimTrigger++
                },
                likeAnimTrigger = likeAnimTrigger,
                isGuestPreview = isGuestPreview,
                scope = scope,
                videoShake = videoShake,
                giftShake = giftShake,
                onGuestRestricted = onGuestRestricted,
                onSendComment = onSendComment,
                onCall = onCall,
                onMessage = onMessage,
                onLikeLive = onLikeLive,
                onPickChatterFromMessage = { m ->
                    if (m.senderId.isNotBlank() && m.senderId != partner.id) {
                        chatterSheetProfile = resolveChatterProfile(m)
                    }
                },
                    onOpenGiftSheetToHost = {
                        giftRecipientProfile = null
                        showGiftSheet = true
                    },
                    onOpenMessageSheet = openHostFullChat,
                    onOpenDiamondShop = { showDiamondShop = true },
                    compactBottomOverlay = true,
                    showDockedComplianceBanner = !uiState.watchingLiveStreamIsAudioOnly,
                    showStreamControlHeader = false,
                    audioPartyChatChrome = soloAudioPartyWatch,
                    viewerUserId = viewerUid,
                    onAudioPartyOpenRoomMenu = { showWatcherApMenu = true },
                )
        }

        if (viewerUid.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sign in to watch live video",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        if (showGiftSheet) {
            val pkWatchRecipients =
                if (giftRecipientProfile == null && uiState.watchingPkActive) {
                    val hostName = partner.name.trim().ifBlank { "Host" }
                    if (uiState.watchingPkGuestUid.isNotBlank()) {
                        val guestName = uiState.watchingPkGuestDisplayName.trim().ifBlank { "Guest" }
                        val hostPair = partner.id to hostName
                        val guestPair = uiState.watchingPkGuestUid to guestName
                        if (uiState.pkWatchAudienceHostOnLeft) listOf(hostPair, guestPair)
                        else listOf(guestPair, hostPair)
                    } else {
                        listOf(partner.id to hostName)
                    }
                } else null
            GiftSelectionSheet(
                gifts = uiState.availableGifts,
                coinBalance = uiState.coinBalance,
                isProcessing = uiState.isGiftTransactionProcessing,
                pkGiftScoreHint = if (!pkWatchRecipients.isNullOrEmpty()) {
                    stringResource(R.string.live_pk_gift_counts_for_score)
                } else null,
                onTopUpCoins = onTopUpCoins,
                onDismiss = {
                    showGiftSheet = false
                    giftRecipientProfile = null
                },
                onSend = { gift, count ->
                    val target = giftRecipientProfile
                    if (target == null) onSendGift(gift, count)
                    else onPrivateGiftToChatter(target, gift, count)
                    showGiftSheet = false
                    giftRecipientProfile = null
                },
                pkBattlerRecipients = pkWatchRecipients,
                onSendGiftToPkBattler = if (pkWatchRecipients != null) { uid, gift, count ->
                    onSendPkBattlerGift(uid, gift, count)
                    showGiftSheet = false
                    giftRecipientProfile = null
                } else null,
                luckyGiftReceiverId = when {
                    pkWatchRecipients != null -> null
                    else -> giftRecipientProfile?.id?.trim()?.takeIf { it.isNotBlank() } ?: partner.id
                },
                onLuckySend = onProcessLuckyGifts,
                onLuckyGiftVisual = onLuckyGiftVisual,
                itzoAudioPartyGiftPresentation = soloAudioPartyWatch,
            )
        }

        chatterSheetProfile?.let { chatter ->
            LiveChatterActionSheet(
                profile = chatter,
                onDismiss = { chatterSheetProfile = null },
                onVideoCall = {
                    if (isGuestPreview) {
                        scope.launch { runLockedShake(videoShake); onGuestRestricted() }
                        chatterSheetProfile = null
                    } else {
                        onVideoCallChatter(chatter)
                        chatterSheetProfile = null
                    }
                },
                onMessage = {
                    onMessageChatter(chatter)
                    chatterSheetProfile = null
                },
                onPickGift = {
                    if (isGuestPreview) scope.launch { runLockedShake(giftShake); onGuestRestricted() }
                    else {
                        chatterGiftTarget = chatter
                        chatterSheetProfile = null
                        showChatterGiftPick = true
                    }
                }
            )
        }
        if (showChatterGiftPick && chatterGiftTarget != null) {
            GiftSelectionSheet(
                gifts = uiState.availableGifts,
                coinBalance = uiState.coinBalance,
                isProcessing = uiState.isGiftTransactionProcessing,
                onTopUpCoins = onTopUpCoins,
                onDismiss = {
                    showChatterGiftPick = false
                    chatterGiftTarget = null
                },
                onSend = { gift, count ->
                    onPrivateGiftToChatter(chatterGiftTarget!!, gift, count)
                    showChatterGiftPick = false
                    chatterGiftTarget = null
                },
                luckyGiftReceiverId = chatterGiftTarget?.id,
                onLuckySend = onProcessLuckyGifts,
                onLuckyGiftVisual = onLuckyGiftVisual,
            )
        }

        if (dmOverlayPage != LiveWatcherDmOverlayPage.Hidden) {
            BackHandler {
                when (dmOverlayPage) {
                    LiveWatcherDmOverlayPage.Thread -> {
                        onClearDmThreadFocus()
                        dmOverlayPage = LiveWatcherDmOverlayPage.Inbox
                    }
                    LiveWatcherDmOverlayPage.Inbox -> closeDmOverlay()
                    LiveWatcherDmOverlayPage.Hidden -> Unit
                }
            }
            Dialog(
                onDismissRequest = { closeDmOverlay() },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (dmOverlayPage) {
                        LiveWatcherDmOverlayPage.Inbox -> {
                            LiveWatcherDmInboxOverlay(
                                uiState = uiState,
                                onCloseAll = { closeDmOverlay() },
                                onOpenThread = { u ->
                                    dmChatOverlayPartner = u
                                    onStartDmObservationForPartner(u)
                                    dmOverlayPage = LiveWatcherDmOverlayPage.Thread
                                }
                            )
                        }
                        LiveWatcherDmOverlayPage.Thread -> {
                            ChatDetailScreen(
                                partner = dmThreadResolved,
                                chatPartnerProfile = uiState.chatPartnerProfile?.takeIf { it.id == dmThreadResolved.id },
                                messages = uiState.messages[dmThreadResolved.id] ?: emptyList(),
                                currentUserId = uiState.currentUser?.id ?: viewerUid.ifEmpty { "me" },
                                availableGifts = uiState.availableGifts,
                                onBack = {
                                    onClearDmThreadFocus()
                                    dmOverlayPage = LiveWatcherDmOverlayPage.Inbox
                                },
                                onSendMessage = { text -> onChatSendMessage(dmThreadResolved.id, text) },
                                onSendImageMessage = { uri -> onChatSendImageMessage(dmThreadResolved.id, uri) },
                                isGuestPreview = isGuestPreview,
                                onGuestRestricted = onGuestRestricted,
                                isImageUploading = (uiState.pendingImageUploads[dmThreadResolved.id] ?: 0) > 0,
                                uploadError = uiState.imageUploadError,
                                onConsumeUploadError = onChatConsumeImageUploadError,
                                onStartCall = { isVideo -> onChatStartCall(isVideo, dmThreadResolved.id) },
                                onSendGiftMessage = { gift -> onChatSendGiftMessage(dmThreadResolved.id, gift) },
                                onSendGift = onChatSendGift,
                                isGiftTransactionProcessing = uiState.isGiftTransactionProcessing,
                                coinBalance = uiState.coinBalance,
                                onTopUpCoins = onTopUpCoins,
                                onProcessLuckyGifts = onProcessLuckyGifts,
                                onLuckyGiftVisual = onLuckyGiftVisual,
                            )
                        }
                        LiveWatcherDmOverlayPage.Hidden -> Unit
                    }
                }
            }
        }
        if (resolvedViewerPopupProfile != null) {
            val p = resolvedViewerPopupProfile
            val followingIds = uiState.currentUser?.followingIds.orEmpty().toSet()
            val isFollowed = p.id in followingIds || uiState.followedProfiles.any { it.id == p.id }
            val isLiked = p.id in uiState.likedProfileIds
            BackHandler {
                viewersProfilePopup = null
            }
            Dialog(
                onDismissRequest = { viewersProfilePopup = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
                    val showSafetyMenuWatch = !isGuestPreview &&
                        uiState.currentUser?.isGuest != true &&
                        !uiState.currentUser?.id.isNullOrBlank() &&
                        p.id != uiState.currentUser?.id
                    ProfileDetailScreen(
                        profile = p,
                        isGuestPreview = isGuestPreview,
                        onGuestRestricted = onGuestRestricted,
                        isFollowed = isFollowed,
                        isLiked = isLiked,
                        recentSentGifts = uiState.profileDetailSentGifts,
                        recentReceivedGifts = uiState.profileDetailReceivedGifts,
                        onDismiss = { viewersProfilePopup = null },
                        onFollow = { onFollow(p) },
                        onLike = {
                            if (!isGuestPreview) onLikeProfile(p) else onGuestRestricted()
                        },
                        onVideoCall = {
                            viewersProfilePopup = null
                            onCall(p)
                        },
                        onMessage = {
                            viewersProfilePopup = null
                            dmChatOverlayPartner = p
                            onStartDmObservationForPartner(p)
                            dmOverlayPage = LiveWatcherDmOverlayPage.Thread
                        },
                        onWatchLive = {
                            viewersProfilePopup = null
                            if (p.id != partner.id) {
                                onStopWatching()
                                onWatchLive(p)
                            }
                        },
                        availableGifts = uiState.availableGifts,
                        coinBalance = uiState.coinBalance,
                        isGiftTransactionProcessing = uiState.isGiftTransactionProcessing,
                        onTopUpCoins = onTopUpCoins,
                        onSendGiftToProfile = { gift, count -> onGiftToProfile(p, gift, count) },
                        onProcessLuckyGifts = onProcessLuckyGifts,
                        onLuckyGiftVisual = onLuckyGiftVisual,
                        showSafetyOverflowMenu = showSafetyMenuWatch,
                        signedInViewerUid = uiState.currentUser?.id.orEmpty(),
                        onBlockUser = if (showSafetyMenuWatch) {
                            {
                                onBlockUserFromProfile(p)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.profile_blocked_toast),
                                    Toast.LENGTH_SHORT
                                ).show()
                                viewersProfilePopup = null
                            }
                        } else null,
                        onReportUser = if (showSafetyMenuWatch) {
                            { reason -> onReportUserFromProfile(p, reason) }
                        } else null,
                    )
                }
            }
        }
        if (showDiamondShop) {
            DiamondShopPopup(
                uiState = uiState,
                onDismiss = { showDiamondShop = false },
                onBuyDiamonds = onBuyDiamonds
            )
        }

        if (showPkPerspectiveDialog) {
            AlertDialog(
                onDismissRequest = {
                    onConfirmPkWatchPerspective(partner.id, pkPerspectiveSessionKey, true)
                    pkPerspectiveChosenKey = pkPerspectiveSessionKey
                },
                title = { Text(stringResource(R.string.pk_watch_pick_title)) },
                text = { Text(stringResource(R.string.pk_watch_pick_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onConfirmPkWatchPerspective(partner.id, pkPerspectiveSessionKey, true)
                            pkPerspectiveChosenKey = pkPerspectiveSessionKey
                        }
                    ) {
                        Text(stringResource(R.string.pk_watch_pick_host))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            onConfirmPkWatchPerspective(partner.id, pkPerspectiveSessionKey, false)
                            pkPerspectiveChosenKey = pkPerspectiveSessionKey
                        }
                    ) {
                        Text(stringResource(R.string.pk_watch_pick_challenger))
                    }
                }
            )
        }

        pkBattlerViewerSheet?.let { (uids, title) ->
            LiveWatchingViewersSheet(
                viewerUids = uids,
                uiState = uiState,
                currentUserId = viewerUid,
                sheetTitle = title,
                onDismiss = { pkBattlerViewerSheet = null },
                onOpenViewerProfile = { p ->
                    pkBattlerViewerSheet = null
                    openStreamProfilePeek(p)
                    onOpenStreamUserProfile(p)
                },
                onFollowViewer = { p ->
                    if (isGuestPreview) onGuestRestricted()
                    else onFollow(p)
                }
            )
        }

        if (soloWatcherViewerListSheet) {
            LiveWatchingViewersSheet(
                viewerUids = uiState.liveAudienceViewerUserIds,
                uiState = uiState,
                currentUserId = viewerUid,
                sheetTitle = null,
                onDismiss = { soloWatcherViewerListSheet = false },
                onOpenViewerProfile = { p ->
                    soloWatcherViewerListSheet = false
                    openStreamProfilePeek(p)
                    onOpenStreamUserProfile(p)
                },
                onFollowViewer = { p ->
                    if (isGuestPreview) onGuestRestricted()
                    else onFollow(p)
                }
            )
        }

        if (showWatcherApMenu && soloAudioPartyWatch) {
            AudioPartyAudienceStreamMenuBottomSheet(
                onDismiss = { showWatcherApMenu = false },
                onJoinCall = {
                    showWatcherApMenu = false
                    val ap = uiState.audioPartyStream
                    val open =
                        ap.guestSeatIndexRange().firstOrNull { idx ->
                            val s = ap.seats[idx] ?: AudioPartySeat(index = idx)
                            s.userId.isBlank() && !s.locked && s.priceCoins <= 0
                        }
                            ?: ap.guestSeatIndexRange().firstOrNull { idx ->
                                val s = ap.seats[idx] ?: AudioPartySeat(index = idx)
                                s.userId.isBlank() && !s.locked
                            }
                    if (open != null) {
                        onAudioPartyClaimSeat(partner.id, open)
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.live_audio_party_join_no_open_seat),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onChangeSeat = {
                    showWatcherApMenu = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.live_audio_party_change_seat_hint),
                        Toast.LENGTH_LONG,
                    ).show()
                },
                onPurchaseSeat = {
                    showWatcherApMenu = false
                    val ap = uiState.audioPartyStream
                    val paid =
                        ap.guestSeatIndexRange().mapNotNull { idx ->
                            val s = ap.seats[idx] ?: AudioPartySeat(index = idx)
                            if (s.userId.isBlank() && s.priceCoins > 0) idx to s.priceCoins else null
                        }.minByOrNull { it.first }
                    if (paid != null) {
                        watcherApPurchaseSeat = paid.first
                        watcherApPurchasePrice = paid.second
                        showWatcherApPurchase = true
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.live_audio_party_no_paid_seat),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onReport = {
                    showWatcherApMenu = false
                    onReportUserFromProfile(partner, "live_audio_party")
                },
                onRequestVideoWithHost = {
                    showWatcherApMenu = false
                    onRequestAudioPartyVideoCallWithHost()
                },
            )
        }
        if (showWatcherApPurchase && soloAudioPartyWatch) {
            val balHint =
                context.getString(R.string.profile_sheet_diamond_balance_value, uiState.coinBalance)
            AudioPartyConfirmPurchaseSeatDialog(
                seatIndex = watcherApPurchaseSeat,
                priceCoins = watcherApPurchasePrice,
                balanceHint = "Your balance: $balHint",
                onDismiss = { showWatcherApPurchase = false },
                onBuy = {
                    showWatcherApPurchase = false
                    onAudioPartyClaimSeat(partner.id, watcherApPurchaseSeat)
                },
            )
        }

        if (showLeaveStreamConfirm) {
            FuturisticConfirmCloseDialog(
                onDismissRequest = { showLeaveStreamConfirm = false },
                onConfirm = {
                    showLeaveStreamConfirm = false
                    onStopWatching()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveWatcherDmInboxOverlay(
    uiState: DatingUiState,
    onCloseAll: () -> Unit,
    onOpenThread: (UserProfile) -> Unit,
) {
    val chattedUsers = remember(
        uiState.profiles,
        uiState.filteredProfiles,
        uiState.messages,
        uiState.dmThreadProfiles,
    ) {
        val threadIds = uiState.messages.keys.filter { it.isNotBlank() }.toMutableSet()
        threadIds.map { pid ->
            uiState.profiles.find { it.id == pid }
                ?: uiState.filteredProfiles.find { it.id == pid }
                ?: uiState.dmThreadProfiles[pid]
                ?: UserProfile(id = pid, name = "User", profileNumber = "", photoUrl = "")
        }
            .distinctBy { it.id }
            .sortedByDescending { uiState.messages[it.id]?.lastOrNull()?.timestamp ?: 0L }
    }
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.messages_tab_chats),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCloseAll) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.profile_sheet_back_cd)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        if (chattedUsers.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.call_overlay_chat_empty),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = paddingWithNavigationBars(bottomExtra = 8.dp)
            ) {
                itemsIndexed(chattedUsers, key = { index, c -> c.composeLazyKey(index) }) { _, cached ->
                    val user = uiState.profiles.find { it.id == cached.id } ?: cached
                    ListItem(
                        headlineContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    user.name,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (user.hasBlueTick) {
                                    Spacer(Modifier.width(4.dp))
                                    VerifiedBadge(size = 12.dp)
                                }
                                Spacer(Modifier.width(6.dp))
                                GenderAgeBadge(gender = user.genderText, age = user.currentAge)
                            }
                        },
                        supportingContent = {
                            val lastMsg = uiState.messages[user.id]?.lastOrNull()
                            Text(
                                text = if (lastMsg?.type == "gift") "Sent a gift" else lastMsg?.text.orEmpty(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        },
                        leadingContent = {
                            Box {
                                Surface(
                                    modifier = Modifier.size(56.dp).clip(CircleShape),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    if (user.photoUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = user.photoUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                user.name.take(1),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.titleLarge
                                            )
                                        }
                                    }
                                }
                                if (user.isOnline) {
                                    Surface(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .align(Alignment.BottomEnd),
                                        shape = CircleShape,
                                        color = Color(0xFF4ADE80),
                                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.background)
                                    ) {}
                                }
                            }
                        },
                        trailingContent = {
                            val lastMsg = uiState.messages[user.id]?.lastOrNull()
                            if (lastMsg != null) {
                                Text(
                                    text = timeFmt.format(Date(lastMsg.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onOpenThread(user) }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveWatchingViewersSheet(
    viewerUids: List<String>,
    uiState: DatingUiState,
    currentUserId: String,
    onDismiss: () -> Unit,
    onOpenViewerProfile: (UserProfile) -> Unit,
    onFollowViewer: (UserProfile) -> Unit,
    /** When null, uses [R.string.live_viewers_sheet_title]. */
    sheetTitle: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val firebaseService = remember { FirebaseService() }
    var resolved by remember { mutableStateOf<Map<String, UserProfile>>(emptyMap()) }
    LaunchedEffect(viewerUids.joinToString()) {
        val map = mutableMapOf<String, UserProfile>()
        viewerUids.forEach { uid ->
            uiState.profiles.find { it.id == uid }?.let { map[uid] = it }
                ?: uiState.filteredProfiles.find { it.id == uid }?.let { map[uid] = it }
        }
        resolved = map
        viewerUids.filter { it !in map }.forEach { uid ->
            val remote = withContext(Dispatchers.IO) {
                runCatching { firebaseService.getUserProfile(uid) }.getOrNull()
            }
            if (remote != null) {
                resolved = resolved + (uid to remote)
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            sheetTitle ?: stringResource(R.string.live_viewers_sheet_title),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (viewerUids.isEmpty()) {
            Text(
                stringResource(R.string.live_viewers_sheet_empty),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .heightIn(max = 420.dp)
            ) {
                items(viewerUids, key = { it }) { uid ->
                    val p = resolved[uid] ?: UserProfile(
                        id = uid,
                        profileNumber = "",
                        name = "",
                        photoUrl = ""
                    )
                    val followingIds = uiState.currentUser?.followingIds.orEmpty().toSet()
                    val isFollowing = p.id in followingIds || uiState.followedProfiles.any { it.id == p.id }
                    val isSelf = currentUserId.isNotBlank() && p.id == currentUserId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpenViewerProfile(p) }
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(modifier = Modifier.size(44.dp), shape = CircleShape, color = Color.LightGray) {
                                if (p.photoUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = p.photoUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    p.name.ifBlank { stringResource(R.string.live_pk_battler_guest_fallback) },
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    p.profileNumber.ifBlank { uid },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (!isSelf) {
                            OutlinedButton(
                                onClick = { onFollowViewer(p) },
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    if (isFollowing) stringResource(R.string.profile_sheet_unfollow)
                                    else stringResource(R.string.live_follow),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PremiumLockBadge(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.Lock,
        contentDescription = "Premium feature",
        tint = PremiumUiTokens.LockBadgeGoldTint.copy(alpha = PremiumUiTokens.LockBadgeAlpha),
        modifier = modifier
            .padding(end = PremiumUiTokens.LockBadgeOffsetX, top = PremiumUiTokens.LockBadgeOffsetY)
            .size(PremiumUiTokens.LockBadgeSize)
    )
}

private suspend fun runLockedShake(anim: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>) {
    val duration = 40
    anim.animateTo(-10f, tween(duration))
    anim.animateTo(10f, tween(duration))
    anim.animateTo(-6f, tween(duration))
    anim.animateTo(6f, tween(duration))
    anim.animateTo(0f, tween(duration))
}
