package com.zipper.datingapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.zipper.datingapp.ui.theme.AppContainerHi
import com.zipper.datingapp.ui.theme.AppResponsiveContentMaxWidth
import com.zipper.datingapp.ui.theme.AppWindowWidthClass
import com.zipper.datingapp.ui.theme.LocalWindowWidthClass
import com.zipper.datingapp.ui.theme.defaultHorizontalContentPadding
import com.zipper.datingapp.ui.components.FuturisticConfirmCloseDialog
import com.zipper.datingapp.ui.components.FuturisticConfirmCloseStyle
import com.zipper.datingapp.ui.theme.AppSurfaceVar
import com.zipper.datingapp.ui.theme.BrandCyan
import com.zipper.datingapp.ui.theme.BrandPink
import com.zipper.datingapp.ui.theme.BrandPurple
import com.zipper.datingapp.ui.theme.OutlineDefault
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zipper.datingapp.ui.paddingWithNavigationBars
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.zipper.datingapp.ui.util.ProfileImagePrefetch
import com.zipper.datingapp.ui.util.ProfileImageSpecs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import com.zipper.datingapp.webrtc.WebRTCManager
import org.webrtc.SurfaceViewRenderer
import android.widget.Toast
import com.zipper.datingapp.data.Gift
import com.zipper.datingapp.data.LuckyGiftsOutcome
import com.zipper.datingapp.data.SentGiftHistoryEntry
import com.zipper.datingapp.data.UserProfile
import com.zipper.datingapp.data.fallbackImageVector
import com.zipper.datingapp.data.giftIconTintOnOrb
import com.zipper.datingapp.data.giftOrbGradient
import com.zipper.datingapp.data.withCatalogDefaults
import com.zipper.datingapp.data.composeLazyKey
import com.zipper.datingapp.data.profileFrames
import com.zipper.datingapp.ui.components.GenderAgeBadge
import com.zipper.datingapp.ui.components.GiftSelectionSheet
import com.zipper.datingapp.ui.components.VerifiedBadge
import com.zipper.datingapp.R
import com.zipper.datingapp.ui.DatingUiState
import com.zipper.datingapp.ui.theme.PremiumUiTokens
import kotlinx.coroutines.launch
import kotlin.math.max

private const val HOME_FEED_ROUTE = "home_feed"
private const val USER_PROFILE_ROUTE = "user_profile/{userId}"

private fun DatingUiState.profileForNavUserId(userId: String): UserProfile {
    return sequenceOf(
        filteredProfiles,
        profiles,
        followedProfiles,
        followerProfiles,
        likedMeProfiles
    ).flatten().distinctBy { it.id }.firstOrNull { it.id == userId }
        ?: UserProfile(id = userId, name = "User")
}

private fun isProbablyVideoMediaUrl(url: String): Boolean {
    val u = url.lowercase()
    return u.contains(".mp4") || u.contains(".mov") || u.contains(".webm") || u.contains(".m4v") ||
        u.contains("video/")
}

private fun String.isHttpProfileMediaUrl(): Boolean {
    if (isBlank()) return false
    val uri = android.net.Uri.parse(this)
    val scheme = uri.scheme?.lowercase()
    return (scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank()
}

/** Avatar for profile detail: use main photo, else first still from gallery/moments (HTTPS only — works across devices). */
private fun UserProfile.detailHeaderImageUrl(): String? {
    val ordered = buildList {
        add(photoUrl)
        galleryPhotos.forEach { add(it) }
        momentsMediaUrls.forEach { add(it) }
    }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    return ordered.firstOrNull { it.isHttpProfileMediaUrl() && !isProbablyVideoMediaUrl(it) }
        ?: ordered.firstOrNull { it.isHttpProfileMediaUrl() }
}

/** Latest still image for a live poster: last non-video moment, else last gallery, else avatar. */
private fun UserProfile.livePreviewPosterUrl(): String {
    val fromMoments = momentsMediaUrls.asReversed().firstOrNull { it.isNotBlank() && !isProbablyVideoMediaUrl(it) }
    if (!fromMoments.isNullOrBlank()) return fromMoments.trim()
    val fromGallery = galleryPhotos.asReversed().firstOrNull { it.isNotBlank() }
    if (!fromGallery.isNullOrBlank()) return fromGallery.trim()
    return photoUrl.trim()
}

private val ProfileLivePipWidth = 118.dp
private val ProfileLivePipHeight = 158.dp

/** Picture-in-picture style live preview: WebRTC when signed in, else poster; tap opens full live. */
@Composable
private fun ProfileLiveStreamPip(
    profile: UserProfile,
    streamHostId: String,
    viewerUid: String,
    onOpenLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val poster = remember(profile.id, profile.momentsMediaUrls, profile.galleryPhotos, profile.photoUrl) {
        profile.livePreviewPosterUrl()
    }
    val audioOnlyPreview = profile.liveStreamAudioOnly
    val scope = rememberCoroutineScope()
    val dummyRenderer = remember(streamHostId) { SurfaceViewRenderer(context) }
    val remoteRenderer = remember(streamHostId) { SurfaceViewRenderer(context) }

    Card(
        modifier = modifier
            .width(ProfileLivePipWidth)
            .height(ProfileLivePipHeight)
            .clickable(onClick = onOpenLive),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = AppContainerHi)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (viewerUid.isNotBlank()) {
                DisposableEffect(streamHostId, viewerUid, audioOnlyPreview) {
                    var mgr: WebRTCManager? = null
                    val job = scope.launch {
                        try {
                            val m = WebRTCManager(
                                context = context,
                                roomId = streamHostId,
                                localView = dummyRenderer,
                                remoteView = remoteRenderer,
                                broadcastMode = true,
                                viewerSignalingId = viewerUid,
                                broadcastViewerReceiveAudio = audioOnlyPreview,
                                audioOnly = audioOnlyPreview,
                            )
                            mgr = m
                            m.joinCall()
                            if (audioOnlyPreview) {
                                m.setRemoteAudioHearEnabled(false)
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("ProfileLivePip", "join preview failed room=$streamHostId", e)
                        }
                    }
                    onDispose {
                        job.cancel()
                        mgr?.onDestroy()
                        mgr = null
                    }
                }
                if (audioOnlyPreview) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(AppSurfaceVar, AppContainerHi)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.88f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    AndroidView(
                        factory = { remoteRenderer },
                        modifier = Modifier
                            .size(1.dp)
                            .align(Alignment.TopStart)
                    )
                } else {
                    AndroidView(
                        factory = { remoteRenderer },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else if (poster.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(poster)
                        .size(ProfileImageSpecs.PIP_POSTER_SIZE)
                        .crossfade(200)
                        .build(),
                    contentDescription = stringResource(R.string.profile_live_pip_cd),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(AppSurfaceVar, AppContainerHi)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        profile.name.take(1).uppercase(),
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.75f)
                        )
                    )
            )
            Surface(
                color = Color.Red,
                shape = RoundedCornerShape(5.dp),
                modifier = Modifier
                    .padding(6.dp)
                    .align(Alignment.TopStart)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(Color.White, CircleShape)
                    )
                    Text(
                        text = "LIVE",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Text(
                text = stringResource(R.string.profile_live_tap_to_watch),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun HomeScreen(
    uiState: DatingUiState,
    isGuestPreview: Boolean = false,
    onGuestRestricted: () -> Unit = {},
    onReset: () -> Unit,
    onVideoCall: (UserProfile) -> Unit,
    onMessage: (UserProfile) -> Unit,
    onWatchLive: (UserProfile) -> Unit,
    onFollow: (UserProfile) -> Unit,
    onLike: (UserProfile) -> Unit,
    onApplyFilter: (IntRange?, Int?, String?, Int) -> Unit,
    onClearFilter: () -> Unit,
    onSearch: (String) -> Unit,
    onConsumedOpenProfileRequest: () -> Unit = {},
    /** Fires when the profile detail screen opens (non-null user id) or closes (null). */
    onProfileSheetTargetChanged: (String?) -> Unit = {},
    onGiftToProfile: (UserProfile, Gift, Int) -> Unit = { _, _, _ -> },
    onTopUpCoins: () -> Unit = {},
    onProcessLuckyGifts: suspend (String, Int, Int) -> LuckyGiftsOutcome,
    onLuckyGiftVisual: (Gift, String, Int, String?) -> Unit = { _, _, _, _ -> },
    /** Arms the one-shot random "free call" marketing hook (~20–30s later) when the home feed is visible. */
    onArmMarketingFreeCallHook: () -> Unit = {},
    onBlockProfile: (UserProfile) -> Unit = {},
    onReportProfile: (UserProfile, String) -> Unit = { _, _ -> },
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    BackHandler(enabled = navController.previousBackStackEntry != null) {
        navController.popBackStack()
    }

    LaunchedEffect(Unit) {
        onArmMarketingFreeCallHook()
    }

    var profileNavBootstrap by remember { mutableStateOf<UserProfile?>(null) }

    LaunchedEffect(uiState.openProfileSheetForUserId, uiState.openProfileSheetHintProfile?.id) {
        val id = uiState.openProfileSheetForUserId ?: return@LaunchedEffect
        val hint = uiState.openProfileSheetHintProfile?.takeIf { it.id == id }
        val merged = hint
            ?: sequenceOf(
                uiState.filteredProfiles,
                uiState.profiles,
                uiState.followedProfiles,
                uiState.followerProfiles,
                uiState.likedMeProfiles
            ).flatten().distinctBy { it.id }.firstOrNull { it.id == id }
        profileNavBootstrap = merged
        onConsumedOpenProfileRequest()
        navController.navigate("user_profile/$id") {
            launchSingleTop = true
        }
    }

    LaunchedEffect(
        uiState.profiles,
        uiState.filteredProfiles,
        uiState.likedMeProfiles,
        profileNavBootstrap?.id
    ) {
        val id = profileNavBootstrap?.id ?: return@LaunchedEffect
        val live = uiState.profiles.find { it.id == id }
            ?: uiState.filteredProfiles.find { it.id == id }
            ?: uiState.likedMeProfiles.find { it.id == id }
        if (live != null) profileNavBootstrap = live
    }

    LaunchedEffect(navBackStackEntry?.destination?.route) {
        if (navBackStackEntry?.destination?.route == HOME_FEED_ROUTE) {
            profileNavBootstrap = null
        }
    }

    LaunchedEffect(
        navBackStackEntry?.destination?.route,
        navBackStackEntry?.arguments?.getString("userId")
    ) {
        val uid = navBackStackEntry?.arguments?.getString("userId")
        onProfileSheetTargetChanged(uid)
    }

    var showFilterSheet by remember { mutableStateOf(false) }
    var showLeaderboard by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val sortedProfiles = remember(uiState.filteredProfiles) {
        uiState.filteredProfiles.sortedWith(
            compareByDescending<UserProfile> { it.isOnline }.thenByDescending { it.isLive }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = HOME_FEED_ROUTE,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(HOME_FEED_ROUTE) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
        // ── TOP BAR ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leaderboard trophy button
            Surface(
                onClick = { showLeaderboard = true },
                shape = CircleShape,
                color = Color(0xFFFFD700).copy(alpha = 0.13f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = "Leaderboard",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Search bar
            Surface(
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = BrandPink,
                        modifier = Modifier.size(17.dp)
                    )
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            onSearch(it)
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Search by User ID…",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    fontSize = 13.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = ""; onSearch("") },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            // Filter button
            Surface(
                onClick = { showFilterSheet = true },
                shape = CircleShape,
                color = if (uiState.isFilterActive)
                    BrandPink.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Filter",
                        tint = if (uiState.isFilterActive) BrandPink
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            // Refresh button
            Surface(
                onClick = onReset,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (uiState.isFilterActive) {
            Surface(
                color = BrandPink.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClearFilter() }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FilterListOff, contentDescription = null, tint = BrandPink, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Filters Active • Tap to clear", color = BrandPink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (uiState.searchResult != null) {
            Box(modifier = Modifier.padding(16.dp)) {
                ProfileGridItem(
                    profile = uiState.searchResult!!,
                    isLiked = uiState.searchResult!!.id in uiState.likedProfileIds,
                    isGuestPreview = isGuestPreview,
                    onGuestRestricted = onGuestRestricted,
                    onLike = { uiState.searchResult?.let(onLike) },
                    onVideoCall = { uiState.searchResult?.let(onVideoCall) },
                    onClick = {
                        uiState.searchResult?.let { navController.navigate("user_profile/${it.id}") }
                    }
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Text("SEARCH RESULT", color = Color.Yellow, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else if (uiState.filteredProfiles.isEmpty() && uiState.isFilterActive) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PersonSearch,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No profiles match your filters",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    TextButton(onClick = onClearFilter) {
                        Text("Clear All Filters", color = BrandPink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            val homeGridContext = LocalContext.current
            val gridState = rememberLazyGridState()
            LaunchedEffect(sortedProfiles) {
                snapshotFlow {
                    gridState.layoutInfo.visibleItemsInfo.minOfOrNull { it.index } ?: 0
                }.distinctUntilChanged().collectLatest { anchor ->
                    ProfileImagePrefetch.prefetchDeckAhead(homeGridContext, sortedProfiles, anchor)
                }
            }
            val widthClass = LocalWindowWidthClass.current
            val gridColumns = when (widthClass) {
                AppWindowWidthClass.Expanded -> 4
                AppWindowWidthClass.Medium   -> 3
                else                         -> 2
            }
            val gridHPad = widthClass.defaultHorizontalContentPadding()
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(gridColumns),
                modifier = Modifier
                    .widthIn(max = AppResponsiveContentMaxWidth)
                    .fillMaxSize()
                    .padding(horizontal = gridHPad),
                contentPadding = paddingWithNavigationBars(topExtra = 8.dp, bottomExtra = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(sortedProfiles, key = { index, p -> p.composeLazyKey(index) }) { _, profile ->
                    ProfileGridItem(
                        profile = profile,
                        isLiked = profile.id in uiState.likedProfileIds,
                        isGuestPreview = isGuestPreview,
                        onGuestRestricted = onGuestRestricted,
                        onLike = { onLike(profile) },
                        onVideoCall = { onVideoCall(profile) },
                        onClick = { navController.navigate("user_profile/${profile.id}") }
                    )
                }
            }
            }   // Box(contentAlignment = Alignment.TopCenter)
        }
                }
            }
            composable(
                route = USER_PROFILE_ROUTE,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { entry ->
                val userId = entry.arguments?.getString("userId") ?: return@composable
                val liveSelected = profileNavBootstrap?.takeIf { it.id == userId }
                    ?: uiState.profileForNavUserId(userId)
                val followingIds = uiState.currentUser?.followingIds.orEmpty().toSet()
                val isFollowed = liveSelected.id in followingIds ||
                    uiState.followedProfiles.any { it.id == liveSelected.id }
                val isLiked = liveSelected.id in uiState.likedProfileIds
                val popProfile: () -> Unit = { navController.popBackStack() }
                BackHandler(onBack = popProfile)
                val homeCtx = LocalContext.current
                val showSafetyMenu = !isGuestPreview &&
                    uiState.currentUser?.isGuest != true &&
                    !uiState.currentUser?.id.isNullOrBlank() &&
                    liveSelected.id != uiState.currentUser?.id
                val viewerUidForPip = uiState.currentUser?.id.orEmpty()
                ProfileDetailScreen(
                    profile = liveSelected,
                    isGuestPreview = isGuestPreview,
                    onGuestRestricted = onGuestRestricted,
                    isFollowed = isFollowed,
                    isLiked = isLiked,
                    recentSentGifts = uiState.profileDetailSentGifts,
                    recentReceivedGifts = uiState.profileDetailReceivedGifts,
                    onDismiss = popProfile,
                    onFollow = { onFollow(liveSelected) },
                    onLike = {
                        if (!isGuestPreview) onLike(liveSelected) else onGuestRestricted()
                    },
                    onVideoCall = {
                        popProfile()
                        onVideoCall(liveSelected)
                    },
                    onMessage = {
                        popProfile()
                        onMessage(liveSelected)
                    },
                    onWatchLive = {
                        popProfile()
                        onWatchLive(liveSelected)
                    },
                    availableGifts = uiState.availableGifts,
                    coinBalance = uiState.coinBalance,
                    isGiftTransactionProcessing = uiState.isGiftTransactionProcessing,
                    onTopUpCoins = onTopUpCoins,
                    onSendGiftToProfile = { gift, count ->
                        onGiftToProfile(liveSelected, gift, count)
                    },
                    onProcessLuckyGifts = onProcessLuckyGifts,
                    onLuckyGiftVisual = onLuckyGiftVisual,
                    showSafetyOverflowMenu = showSafetyMenu,
                    signedInViewerUid = viewerUidForPip,
                    onBlockUser = if (showSafetyMenu) {
                        {
                            onBlockProfile(liveSelected)
                            Toast.makeText(
                                homeCtx,
                                homeCtx.getString(R.string.profile_blocked_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                            popProfile()
                        }
                    } else null,
                    onReportUser = if (showSafetyMenu) {
                        { reason ->
                            onReportProfile(liveSelected, reason)
                        }
                    } else null,
                )
            }
        }

        if (showLeaderboard) {
            LeaderboardDialog(
                topProfiles = uiState.topRankedProfiles,
                onDismiss = { showLeaderboard = false },
                onProfileClick = { profile ->
                    showLeaderboard = false
                    navController.navigate("user_profile/${profile.id}")
                }
            )
        }

        if (showFilterSheet) {
            FilterBottomSheet(
                uiState = uiState,
                onDismiss = { showFilterSheet = false },
                onApply = { age, level, city, cost ->
                    onApplyFilter(age, level, city, cost)
                    showFilterSheet = false
                }
            )
        }
    }
}

@Composable
fun LeaderboardDialog(
    topProfiles: List<UserProfile>,
    onDismiss: () -> Unit,
    onProfileClick: (UserProfile) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Stars, contentDescription = null, tint = Color(0xFFFFD700))
                Spacer(Modifier.width(8.dp))
                Text("Global Ranking", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            val context = LocalContext.current
            val leaderboardMaxH = (LocalConfiguration.current.screenHeightDp * 0.42f).dp
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = leaderboardMaxH)
            ) {
                itemsIndexed(topProfiles) { index, profile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProfileClick(profile) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "#${index + 1}", 
                            modifier = Modifier.width(32.dp),
                            color = when(index) {
                                0 -> Color(0xFFFFD700)
                                1 -> Color(0xFFC0C0C0)
                                2 -> Color(0xFFCD7F32)
                                else -> Color.Gray
                            },
                            fontWeight = FontWeight.Bold
                        )
                        Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = Color.LightGray) {
                            if (profile.photoUrl.isEmpty()) {
                                Box(contentAlignment = Alignment.Center) { Text(profile.name.take(1)) }
                            } else {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(profile.photoUrl)
                                        .size(Size(128, 128))
                                        .crossfade(200)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                profile.name,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("ID: ${profile.profileNumber}", fontSize = 10.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Surface(color = BrandPurple, shape = RoundedCornerShape(8.dp)) {
                            Text("Lv.${profile.level}", color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    uiState: DatingUiState,
    onDismiss: () -> Unit,
    onApply: (IntRange?, Int?, String?, Int) -> Unit
) {
    var ageRange by remember { mutableStateOf(18f..50f) }
    var minLevel by remember { mutableStateOf(1f) }
    var city by remember { mutableStateOf("") }
    
    val filterCost = 50 

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Advanced Filters", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Each filtered search costs $filterCost 💎", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            
            Spacer(Modifier.height(32.dp))
            
            Text("Age Range: ${ageRange.start.toInt()} - ${ageRange.endInclusive.toInt()}", fontWeight = FontWeight.Medium)
            RangeSlider(
                value = ageRange,
                onValueChange = { ageRange = it },
                valueRange = 18f..60f,
                colors = SliderDefaults.colors(
                    activeTrackColor = BrandPink,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    thumbColor = MaterialTheme.colorScheme.primary
                )
            )
            
            Spacer(Modifier.height(24.dp))
            
            Text("Minimum Level: ${minLevel.toInt()}", fontWeight = FontWeight.Medium)
            Slider(
                value = minLevel,
                onValueChange = { minLevel = it },
                valueRange = 1f..20f,
                steps = 19,
                colors = SliderDefaults.colors(
                    activeTrackColor = BrandPurple,
                    thumbColor = MaterialTheme.colorScheme.primary
                )
            )
            
            Spacer(Modifier.height(24.dp))
            
            Text("City", fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                placeholder = { Text("Enter city name...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandPink,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                )
            )
            
            Spacer(Modifier.height(40.dp))
            
            Button(
                onClick = { 
                    onApply(
                        ageRange.start.toInt()..ageRange.endInclusive.toInt(),
                        minLevel.toInt(),
                        if (city.isBlank()) null else city,
                        filterCost
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandPink,
                    disabledContainerColor = BrandPink.copy(alpha = 0.3f)
                ),
                enabled = uiState.coinBalance >= filterCost
            ) {
                Icon(Icons.Default.Diamond, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Apply Filters ($filterCost 💎)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ProfileGridItem(
    profile: UserProfile,
    isLiked: Boolean = false,
    isGuestPreview: Boolean,
    onGuestRestricted: () -> Unit,
    onLike: () -> Unit = {},
    onVideoCall: () -> Unit,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val videoShake = remember { Animatable(0f) }
    val likeScale = remember { Animatable(1f) }
    val context = LocalContext.current

    val onlinePulse = rememberInfiniteTransition(label = "onlinePulse").animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = Modifier
            .aspectRatio(0.72f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = AppContainerHi)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ── Photo ──────────────────────────────────────────────────────────
            if (profile.photoUrl.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(AppSurfaceVar, AppContainerHi)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profile.name.take(1).uppercase(),
                        color = Color.White.copy(alpha = 0.25f),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(profile.photoUrl)
                        .size(ProfileImageSpecs.DECK_THUMBNAIL_SIZE)
                        .crossfade(200)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // ── Premium frame border ───────────────────────────────────────────
            profile.profileFrameId?.let { frameId ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 2.5.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    getHomeFrameColor(frameId),
                                    getHomeFrameColor(frameId).copy(alpha = 0.5f),
                                    getHomeFrameColor(frameId)
                                )
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                )
            }

            // ── Deep gradient overlay ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.38f to Color.Transparent,
                            0.72f to Color.Black.copy(alpha = 0.55f),
                            1f to Color.Black.copy(alpha = 0.93f)
                        )
                    )
            )

            // ── Top-left: LIVE badge / online pulse ────────────────────────────
            Row(
                modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (profile.isLive) {
                    Surface(
                        color = Color.Red,
                        shape = RoundedCornerShape(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(Color.White, CircleShape)
                            )
                            Text("LIVE", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        }
                    }
                } else if (profile.isOnline) {
                    Surface(
                        modifier = Modifier.size(10.dp),
                        shape = CircleShape,
                        color = BrandCyan.copy(alpha = onlinePulse.value),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.5f))
                    ) {}
                    Text(
                        "Online",
                        color = BrandCyan,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ── Top-right: compatibility badge ────────────────────────────────
            Surface(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopEnd),
                color = BrandPink,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "❤ ${profile.compatibility}%",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp
                )
            }

            // ── Bottom info panel ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 9.dp, end = 9.dp, bottom = 8.dp, top = 4.dp)
            ) {
                // Name row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = profile.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (profile.hasBlueTick) {
                        Spacer(Modifier.width(3.dp))
                        VerifiedBadge(size = 12.dp)
                    }
                    Spacer(Modifier.width(3.dp))
                    GenderAgeBadge(gender = profile.genderText, age = profile.currentAge)
                }

                Spacer(Modifier.height(2.dp))

                // City row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "${profile.age} • ${profile.city}",
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 10.sp
                    )
                }

                Spacer(Modifier.height(7.dp))

                // ── Action row: Like + call chips ─────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // ❤ Like button — standalone pill, always visible
                    Surface(
                        color = if (isLiked)
                            Brush.linearGradient(listOf(BrandPink, Color(0xFFFF3366))).let { Color(0xFFFF3366) }
                        else Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .graphicsLayer { scaleX = likeScale.value; scaleY = likeScale.value }
                            .clickable {
                                if (isGuestPreview) {
                                    onGuestRestricted()
                                } else {
                                    scope.launch {
                                        likeScale.animateTo(1.25f, tween(80))
                                        likeScale.animateTo(1f, tween(120))
                                    }
                                    onLike()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(vertical = 6.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (isLiked) Color.White else BrandPink,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = if (isLiked) "Liked" else "Like",
                                color = if (isLiked) Color.White else BrandPink,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (profile.isOnline) {
                        // Video chip
                        Surface(
                            color = BrandPink.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { translationX = videoShake.value }
                                .clickable {
                                    if (isGuestPreview) {
                                        scope.launch { runLockedShake(videoShake); onGuestRestricted() }
                                    } else onVideoCall()
                                }
                        ) {
                            Box {
                                Row(
                                    modifier = Modifier
                                        .padding(vertical = 6.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.Videocam,
                                        contentDescription = "Video",
                                        tint = BrandPink,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                if (isGuestPreview) PremiumLockBadge(Modifier.align(Alignment.TopEnd))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun resolveGiftForHistoryEntry(entry: SentGiftHistoryEntry, catalog: List<Gift>): Gift {
    val id = entry.giftId.trim()
    if (id.isNotEmpty()) {
        catalog.firstOrNull { it.id == id }?.let { return it }
        catalog.firstOrNull { it.crmDocId == id }?.let { return it }
    }
    val norm = entry.giftName.trim().lowercase().filter { !it.isWhitespace() }
    if (norm.isNotEmpty()) {
        catalog.firstOrNull { g ->
            g.name.trim().lowercase().filter { !it.isWhitespace() } == norm
        }?.let { return it }
    }
    return Gift(id = entry.giftId, name = entry.giftName).withCatalogDefaults()
}

/** Same orb + vector icon treatment as [GiftSelectionSheet], for light profile surfaces. */
@Composable
private fun ProfileGiftHistoryShopTile(
    gift: Gift,
    entry: SentGiftHistoryEntry,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val orbBrush = remember(gift.id, gift.name, gift.category) { gift.giftOrbGradient() }
    val iconTint = remember(gift.id, gift.name) { gift.giftIconTintOnOrb() }
    val rimBrush = remember {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.9f),
                Color(0xFFFFD700).copy(alpha = 0.65f),
                Color.White.copy(alpha = 0.45f)
            )
        )
    }
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = Color.Transparent,
            shadowElevation = 6.dp,
            tonalElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.dp, rimBrush, CircleShape)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(orbBrush)
                    .drawBehind {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.35f),
                                    Color.Transparent
                                ),
                                center = c,
                                radius = size.minDimension * 0.45f
                            ),
                            radius = size.minDimension * 0.48f,
                            center = c
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (gift.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(gift.thumbnailUrl)
                            .size(Size(128, 128))
                            .crossfade(200)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = gift.fallbackImageVector(),
                        contentDescription = gift.name,
                        tint = iconTint,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = gift.name.ifBlank { entry.giftName },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 11.sp
        )
        Text(
            text = "× ${entry.count}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${entry.totalCost}",
                color = Color(0xFFB8860B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Filled.Diamond,
                contentDescription = null,
                tint = Color(0xFFB8860B),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun ProfileGiftHistoryShopGrid(
    entries: List<SentGiftHistoryEntry>,
    availableGifts: List<Gift>,
    modifier: Modifier = Modifier
) {
    val sliced = entries.take(12)
    if (sliced.isEmpty()) return
    val cols = 3
    val rowCount = (sliced.size + cols - 1) / cols
    val cellHeight = 122.dp
    val gap = 8.dp
    val gridHeight = cellHeight * rowCount + gap * (rowCount - 1).coerceAtLeast(0)
    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        modifier = modifier.height(gridHeight),
        userScrollEnabled = false,
        verticalArrangement = Arrangement.spacedBy(gap),
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        items(
            count = sliced.size,
            key = { i -> "${sliced[i].timestampMs}_${sliced[i].giftName}_${sliced[i].totalCost}_$i" }
        ) { i ->
            val entry = sliced[i]
            val gift = resolveGiftForHistoryEntry(entry, availableGifts)
            ProfileGiftHistoryShopTile(
                gift = gift,
                entry = entry,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Full-screen profile from NavHost. [onDismiss] must pop the profile route (e.g. [androidx.navigation.NavController.popBackStack]).
 * A [androidx.activity.compose.BackHandler] is also registered on the route in [HomeScreen] so system back never falls through to the Activity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailScreen(
    profile: UserProfile,
    isGuestPreview: Boolean,
    onGuestRestricted: () -> Unit,
    isFollowed: Boolean,
    isLiked: Boolean,
    recentSentGifts: List<SentGiftHistoryEntry> = emptyList(),
    recentReceivedGifts: List<SentGiftHistoryEntry> = emptyList(),
    onDismiss: () -> Unit,
    onFollow: () -> Unit,
    onLike: () -> Unit,
    onVideoCall: () -> Unit,
    onMessage: () -> Unit,
    onWatchLive: () -> Unit,
    availableGifts: List<Gift> = emptyList(),
    coinBalance: Int = 0,
    isGiftTransactionProcessing: Boolean = false,
    onTopUpCoins: () -> Unit = {},
    onSendGiftToProfile: (Gift, Int) -> Unit = { _, _ -> },
    onProcessLuckyGifts: suspend (String, Int, Int) -> LuckyGiftsOutcome,
    onLuckyGiftVisual: (Gift, String, Int, String?) -> Unit = { _, _, _, _ -> },
    showSafetyOverflowMenu: Boolean = false,
    signedInViewerUid: String = "",
    onBlockUser: (() -> Unit)? = null,
    onReportUser: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showProfileGiftSheet by remember { mutableStateOf(false) }
    var overflowMenuOpen by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReasonText by remember { mutableStateOf("") }
    var lightboxUrl by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = lightboxUrl != null) { lightboxUrl = null }
    val videoShake = remember { Animatable(0f) }
    val giftProfileShake = remember { Animatable(0f) }
    val likeShake = remember { Animatable(0f) }
    val scrollState = rememberScrollState()
    val galleryCols = 3
    val mergedGalleryAndMoments = remember(profile.galleryPhotos, profile.momentsMediaUrls) {
        (profile.galleryPhotos.asSequence() + profile.momentsMediaUrls.asSequence())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }
    val gridRowCount = max(1, (mergedGalleryAndMoments.size + galleryCols - 1) / galleryCols)
    val galleryCell = 108.dp
    val galleryGap = 8.dp
    val galleryGridHeight = galleryCell * gridRowCount + galleryGap * (gridRowCount - 1).coerceAtLeast(0)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = profile.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.profile_sheet_back_cd)
                        )
                    }
                },
                actions = {
                    if (showSafetyOverflowMenu && (onBlockUser != null || onReportUser != null)) {
                        Box {
                            IconButton(onClick = { overflowMenuOpen = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.profile_more_menu_cd)
                                )
                            }
                            DropdownMenu(
                                expanded = overflowMenuOpen,
                                onDismissRequest = { overflowMenuOpen = false }
                            ) {
                                if (onBlockUser != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.profile_action_block)) },
                                        onClick = {
                                            overflowMenuOpen = false
                                            showBlockConfirm = true
                                        }
                                    )
                                }
                                if (onReportUser != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.profile_action_report)) },
                                        onClick = {
                                            overflowMenuOpen = false
                                            reportReasonText = ""
                                            showReportDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val headerImageUrl = remember(profile.id, profile.photoUrl, profile.galleryPhotos, profile.momentsMediaUrls) {
                profile.detailHeaderImageUrl()
            }
            @Composable
            fun ProfileHeaderAvatarOnly(modifier: Modifier = Modifier) {
                Box(contentAlignment = Alignment.Center, modifier = modifier) {
                    Surface(
                        modifier = Modifier.size(100.dp),
                        shape = CircleShape,
                        color = AppContainerHi
                    ) {
                        if (headerImageUrl.isNullOrBlank()) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(profile.name.take(1), style = MaterialTheme.typography.displayMedium)
                            }
                        } else {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(headerImageUrl)
                                    .size(ProfileImageSpecs.PROFILE_HEADER_AVATAR_SIZE)
                                    .crossfade(300)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                loading = {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                error = {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text(profile.name.take(1), style = MaterialTheme.typography.displayMedium)
                                    }
                                }
                            )
                        }
                    }
                    profile.profileFrameId?.let { frameId ->
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .border(
                                    width = 4.dp,
                                    color = getHomeFrameColor(frameId),
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
            if (profile.isLive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 12.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    ProfileHeaderAvatarOnly()
                    Spacer(Modifier.width(14.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.profile_sheet_live_now),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        ProfileLiveStreamPip(
                            profile = profile,
                            streamHostId = profile.id,
                            viewerUid = signedInViewerUid,
                            onOpenLive = onWatchLive,
                        )
                    }
                }
            } else {
                ProfileHeaderAvatarOnly(Modifier.padding(top = 16.dp))
            }
            
            Spacer(Modifier.height(16.dp))
            
            val nameWithId = buildString {
                append(profile.name)
                val pid = profile.profileNumber.trim()
                if (pid.isNotEmpty()) {
                    append("  ")
                    append(stringResource(R.string.profile_sheet_id_suffix, pid))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = nameWithId,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                GenderAgeBadge(
                    gender = profile.genderText,
                    age = profile.currentAge
                )
                if (profile.hasBlueTick) {
                    Spacer(Modifier.width(4.dp))
                    VerifiedBadge(size = 16.dp)
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.profile_sheet_level, profile.displayProfileLevel),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            if (profile.genderText == "male") {
                Text(
                    text = stringResource(R.string.profile_sheet_level_male_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.live_followers_count, profile.followerIds.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFollowed) {
                    OutlinedButton(
                        onClick = onFollow,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f).height(40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.profile_sheet_unfollow), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onFollow,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f).height(40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.profile_sheet_follow), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = {
                        if (isGuestPreview) {
                            scope.launch { runLockedShake(likeShake); onGuestRestricted() }
                        } else onLike()
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f).height(40.dp).graphicsLayer { translationX = likeShake.value },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Icon(
                        if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (isLiked) stringResource(R.string.profile_sheet_liked) else stringResource(R.string.profile_sheet_like),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${profile.city} • ${profile.distance} km away",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                text = profile.bio.ifEmpty { "No bio available." },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.profile_sheet_recent_gifts),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(8.dp))
            if (recentSentGifts.isEmpty()) {
                Text(
                    text = stringResource(R.string.profile_sheet_no_gifts_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(horizontal = 24.dp)
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 0.dp
                ) {
                    ProfileGiftHistoryShopGrid(
                        entries = recentSentGifts,
                        availableGifts = availableGifts,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.profile_sheet_received_gifts),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(8.dp))
            if (recentReceivedGifts.isEmpty()) {
                Text(
                    text = stringResource(R.string.profile_sheet_no_received_gifts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(horizontal = 24.dp)
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 0.dp
                ) {
                    ProfileGiftHistoryShopGrid(
                        entries = recentReceivedGifts,
                        availableGifts = availableGifts,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    )
                }
            }

            if (mergedGalleryAndMoments.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.profile_sheet_photos_and_moments),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start).padding(start = 24.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(galleryCols),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 12.dp)
                        .height(galleryGridHeight),
                    horizontalArrangement = Arrangement.spacedBy(galleryGap),
                    verticalArrangement = Arrangement.spacedBy(galleryGap),
                    userScrollEnabled = false,
                ) {
                    itemsIndexed(mergedGalleryAndMoments, key = { index, url -> "g_${index}_${url.hashCode()}" }) { _, mediaUrl ->
                        Card(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { lightboxUrl = mediaUrl },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(mediaUrl)
                                        .size(Size(512, 512))
                                        .crossfade(200)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (isProbablyVideoMediaUrl(mediaUrl)) {
                                    Icon(
                                        Icons.Default.PlayCircle,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.92f),
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(40.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        if (isGuestPreview) {
                            scope.launch { runLockedShake(giftProfileShake); onGuestRestricted() }
                        } else showProfileGiftSheet = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .graphicsLayer { translationX = giftProfileShake.value },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Icon(
                        Icons.Default.CardGiftcard,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.live_send_gift_action), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onMessage,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Message", fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = {
                        if (isGuestPreview) {
                            scope.launch { runLockedShake(videoShake); onGuestRestricted() }
                        } else onVideoCall()
                    },
                    modifier = Modifier.weight(1f).height(56.dp).graphicsLayer { translationX = videoShake.value },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPink),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Video Call (${profile.customVideoPrice ?: 1500} 💎/min)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            if (showProfileGiftSheet) {
                GiftSelectionSheet(
                    gifts = availableGifts,
                    coinBalance = coinBalance,
                    isProcessing = isGiftTransactionProcessing,
                    onTopUpCoins = onTopUpCoins,
                    onDismiss = { showProfileGiftSheet = false },
                    onSend = { gift, count ->
                        onSendGiftToProfile(gift, count)
                        showProfileGiftSheet = false
                    },
                    luckyGiftReceiverId = profile.id,
                    onLuckySend = onProcessLuckyGifts,
                    onLuckyGiftVisual = onLuckyGiftVisual,
                )
            }
        }

        if (showBlockConfirm) {
            FuturisticConfirmCloseDialog(
                onDismissRequest = { showBlockConfirm = false },
                onConfirm = {
                    showBlockConfirm = false
                    onBlockUser?.invoke()
                },
                titleContent = {
                    Text(
                        text = stringResource(R.string.profile_block_confirm_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    )
                },
                textContent = {
                    Text(
                        text = stringResource(R.string.profile_block_confirm_message),
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                    )
                },
                confirmStyle = FuturisticConfirmCloseStyle.Destructive,
                confirmLabelResId = R.string.profile_block_confirm,
            )
        }
        if (showReportDialog) {
            AlertDialog(
                onDismissRequest = { showReportDialog = false },
                title = { Text(stringResource(R.string.profile_report_title)) },
                text = {
                    Column(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = reportReasonText,
                            onValueChange = { reportReasonText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.profile_report_hint)) },
                            maxLines = 5,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showReportDialog = false
                            onReportUser?.invoke(reportReasonText)
                            Toast.makeText(
                                context,
                                context.getString(R.string.profile_report_sent),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Text(stringResource(R.string.profile_report_submit))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReportDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        val enlarged = lightboxUrl
        if (enlarged != null) {
            Dialog(
                onDismissRequest = { lightboxUrl = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    BackHandler { lightboxUrl = null }
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(enlarged)
                            .size(Size(1440, 2560))
                            .crossfade(200)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(
                        onClick = { lightboxUrl = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.profile_sheet_photo_lightbox_cd),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

fun getHomeFrameColor(frameId: String): Color {
    return when (frameId) {
        "gold" -> Color(0xFFFFD700)
        "diamond" -> Color(0xFFB9F2FF)
        "neon" -> Color(0xFF39FF14)
        else -> when {
            frameId.startsWith("f") -> Color(0xFF4FC3F7)
            frameId.startsWith("p") -> Color(0xFFFFD700)
            else -> Color.Gray
        }
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
