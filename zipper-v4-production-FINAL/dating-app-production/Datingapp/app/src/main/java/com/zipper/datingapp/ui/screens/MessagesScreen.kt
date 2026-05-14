package com.zipper.datingapp.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.zipper.datingapp.ui.theme.AppWindowWidthClass
import com.zipper.datingapp.ui.theme.BrandCyan
import com.zipper.datingapp.ui.theme.BrandPink
import com.zipper.datingapp.ui.theme.AppSurfaceVar
import com.zipper.datingapp.ui.theme.LocalWindowWidthClass
import com.zipper.datingapp.ui.theme.OutlineDefault
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.zipper.datingapp.R
import com.zipper.datingapp.ui.paddingWithNavigationBars
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.zipper.datingapp.data.CrmAnnouncement
import com.zipper.datingapp.data.Gift
import com.zipper.datingapp.data.GiftCatalog
import com.zipper.datingapp.data.Message
import com.zipper.datingapp.data.UserProfile
import com.zipper.datingapp.data.composeLazyKey
import com.zipper.datingapp.data.LuckyGiftsOutcome
import com.zipper.datingapp.ui.DatingUiState
import com.zipper.datingapp.ui.components.GenderAgeBadge
import com.zipper.datingapp.ui.components.GiftSelectionSheet
import com.zipper.datingapp.ui.components.VerifiedBadge
import com.zipper.datingapp.ui.theme.PremiumUiTokens
import com.zipper.datingapp.util.LastSeenFormatter
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    uiState: DatingUiState,
    isGuestPreview: Boolean = false,
    onGuestRestricted: () -> Unit = {},
    /** Pull recent DM docs when this screen is shown so new threads appear without a cold restart. */
    onEnsureInboxFresh: () -> Unit = {},
    onSendMessage: (String, String) -> Unit,
    onSendImageMessage: (String, Uri) -> Unit,
    onConsumeImageUploadError: () -> Unit,
    onSendGiftMessage: (String, Gift) -> Unit,
    onSendGift: (Gift, Int) -> Unit,
    onTopUpCoins: () -> Unit = {},
    onStartCall: (Boolean, String) -> Unit,
    onSelectPartner: (String) -> Unit = {},
    onClearMessageThreadFocus: () -> Unit = {},
    onProcessLuckyGifts: suspend (String, Int, Int) -> LuckyGiftsOutcome,
    onLuckyGiftVisual: (Gift, String, Int, String?) -> Unit = { _, _, _, _ -> },
) {
    var selectedPartner by remember { mutableStateOf<UserProfile?>(null) }
    val windowWidthClass = LocalWindowWidthClass.current
    val isExpanded = windowWidthClass == AppWindowWidthClass.Expanded

    LaunchedEffect(Unit) {
        onEnsureInboxFresh()
    }

    // On expanded (two-pane), there is no full-screen chat to go "back" from.
    BackHandler(enabled = selectedPartner != null && !isExpanded) {
        selectedPartner = null
        onClearMessageThreadFocus()
    }

    LaunchedEffect(selectedPartner) {
        selectedPartner?.let { onSelectPartner(it.id) }
    }

    val focusId = uiState.selectedMessagePartnerId
    LaunchedEffect(
        focusId,
        uiState.chatPartnerProfile,
        uiState.profiles,
        uiState.filteredProfiles,
        uiState.dmThreadProfiles
    ) {
        val id = focusId?.trim().orEmpty()
        if (id.isEmpty()) return@LaunchedEffect
        val fromFeed = uiState.filteredProfiles.find { it.id == id }
            ?: uiState.profiles.find { it.id == id }
        val resolved = uiState.chatPartnerProfile?.takeIf { it.id == id }
        val fromDm = uiState.dmThreadProfiles[id]
        selectedPartner = fromFeed ?: resolved ?: fromDm ?: UserProfile(
            id = id,
            name = "…",
            profileNumber = "",
            photoUrl = ""
        )
    }

    // Always compute inbox list so it can be shown in both single- and two-pane layouts.
    val chattedUsers = remember(
        uiState.profiles,
        uiState.filteredProfiles,
        uiState.messages,
        uiState.dmThreadProfiles,
        uiState.selectedMessagePartnerId
    ) {
        val threadIds = uiState.messages.keys.filter { it.isNotBlank() }.toMutableSet()
        uiState.selectedMessagePartnerId?.trim()?.takeIf { it.isNotEmpty() }?.let { threadIds.add(it) }
        threadIds.map { pid ->
            uiState.profiles.find { it.id == pid }
                ?: uiState.filteredProfiles.find { it.id == pid }
                ?: uiState.dmThreadProfiles[pid]
                ?: UserProfile(id = pid, name = "User", profileNumber = "", photoUrl = "")
        }
            .distinctBy { it.id }
            .sortedByDescending { uiState.messages[it.id]?.lastOrNull()?.timestamp ?: 0L }
    }

    var inboxTabIndex by remember { mutableIntStateOf(0) }

    // Helper: resolve the live partner profile.
    val resolvedPartner: UserProfile? = selectedPartner?.let { sp ->
        uiState.profiles.find { it.id == sp.id }
            ?: uiState.filteredProfiles.find { it.id == sp.id }
            ?: uiState.chatPartnerProfile?.takeIf { it.id == sp.id }
            ?: uiState.dmThreadProfiles[sp.id]
            ?: sp
    }

    if (isExpanded) {
        // ── Two-pane layout (tablets / foldables) ───────────────────────────
        Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Left pane: inbox list
            Column(modifier = Modifier.width(360.dp).fillMaxHeight()) {
                Text(
                    text = "Messages",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TabRow(
                    selectedTabIndex = inboxTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = BrandPink,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[inboxTabIndex]),
                            height = 2.dp,
                            color = BrandPink,
                        )
                    },
                    divider = { HorizontalDivider(color = OutlineDefault) },
                ) {
                    Tab(
                        selected = inboxTabIndex == 0,
                        onClick = { inboxTabIndex = 0 },
                        text = {
                            Text(
                                stringResource(R.string.messages_tab_chats),
                                color = if (inboxTabIndex == 0) BrandPink else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    )
                    Tab(
                        selected = inboxTabIndex == 1,
                        onClick = { inboxTabIndex = 1 },
                        text = {
                            Text(
                                stringResource(R.string.messages_tab_announcements),
                                color = if (inboxTabIndex == 1) BrandPink else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.Campaign,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (inboxTabIndex == 1) BrandPink else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    )
                }
                when (inboxTabIndex) {
                    0 -> {
                        if (chattedUsers.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No messages yet. Start a conversation!",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }
                        } else {
                            LazyColumn(
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
                                                GenderAgeBadge(
                                                    gender = user.genderText,
                                                    age = user.currentAge
                                                )
                                            }
                                        },
                                        supportingContent = {
                                            val lastMsg = uiState.messages[user.id]?.lastOrNull()
                                            Text(
                                                text = if (lastMsg?.type == "gift") "Sent a gift" else lastMsg?.text ?: "",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                            )
                                        },
                                        leadingContent = {
                                            Box {
                                            Surface(
                                                modifier = Modifier.size(56.dp).clip(CircleShape),
                                                color = AppSurfaceVar,
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        user.name.take(1),
                                                        color = BrandPink,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                    )
                                                }
                                            }
                                                if (user.isOnline) {
                                                    Surface(
                                                        modifier = Modifier
                                                            .size(12.dp)
                                                            .align(Alignment.BottomEnd),
                                                        shape = CircleShape,
                                                        color = BrandCyan,
                                                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.background)
                                                    ) {}
                                                }
                                            }
                                        },
                                        trailingContent = {
                                            val lastMsg = uiState.messages[user.id]?.lastOrNull()
                                            if (lastMsg != null) {
                                                val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                                                Text(
                                                    text = sdf.format(Date(lastMsg.timestamp)),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                                )
                                            }
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.clickable { selectedPartner = user }
                                    )
                                    HorizontalDivider(
                                        color = OutlineDefault,
                                        modifier = Modifier.padding(start = 86.dp, end = 16.dp),
                                        thickness = 0.5.dp,
                                    )
                                }
                            }
                        }
                    }
                    1 -> CrmAnnouncementsTab(announcements = uiState.crmAnnouncements)
                }
            }
            // ── Right pane: chat detail or empty state ───────────────────────
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                thickness = 1.dp,
                color = OutlineDefault,
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val rp = resolvedPartner
                if (rp != null) {
                    ChatDetailScreen(
                        partner = rp,
                        chatPartnerProfile = uiState.chatPartnerProfile?.takeIf { it.id == rp.id },
                        messages = uiState.messages[rp.id] ?: emptyList(),
                        currentUserId = uiState.currentUser?.id ?: "me",
                        availableGifts = uiState.availableGifts,
                        onBack = { /* no-op in two-pane; list stays visible */ },
                        onSendMessage = { text -> onSendMessage(rp.id, text) },
                        onSendImageMessage = { uri -> onSendImageMessage(rp.id, uri) },
                        isGuestPreview = isGuestPreview,
                        onGuestRestricted = onGuestRestricted,
                        isImageUploading = (uiState.pendingImageUploads[rp.id] ?: 0) > 0,
                        uploadError = uiState.imageUploadError,
                        onConsumeUploadError = onConsumeImageUploadError,
                        onStartCall = { isVideo -> onStartCall(isVideo, rp.id) },
                        onSendGiftMessage = { gift -> onSendGiftMessage(rp.id, gift) },
                        onSendGift = onSendGift,
                        isGiftTransactionProcessing = uiState.isGiftTransactionProcessing,
                        coinBalance = uiState.coinBalance,
                        onTopUpCoins = onTopUpCoins,
                        onProcessLuckyGifts = onProcessLuckyGifts,
                        onLuckyGiftVisual = onLuckyGiftVisual,
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Select a conversation to start chatting",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }   // Row (expanded two-pane)
    } else {
        // ── Single-pane layout (phones / medium) ────────────────────────────
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (selectedPartner == null) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Messages",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    TabRow(
                        selectedTabIndex = inboxTabIndex,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = BrandPink,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[inboxTabIndex]),
                                height = 2.dp,
                                color = BrandPink,
                            )
                        },
                        divider = { HorizontalDivider(color = OutlineDefault) },
                    ) {
                        Tab(
                            selected = inboxTabIndex == 0,
                            onClick = { inboxTabIndex = 0 },
                            text = {
                                Text(
                                    stringResource(R.string.messages_tab_chats),
                                    color = if (inboxTabIndex == 0) BrandPink
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        )
                        Tab(
                            selected = inboxTabIndex == 1,
                            onClick = { inboxTabIndex = 1 },
                            text = {
                                Text(
                                    stringResource(R.string.messages_tab_announcements),
                                    color = if (inboxTabIndex == 1) BrandPink
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            icon = {
                                Icon(
                                    Icons.Default.Campaign,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (inboxTabIndex == 1) BrandPink
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        )
                    }
                    when (inboxTabIndex) {
                        0 -> {
                            if (chattedUsers.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No messages yet. Start a conversation!",
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = paddingWithNavigationBars(bottomExtra = 8.dp)
                                ) {
                                    itemsIndexed(
                                        chattedUsers,
                                        key = { index, c -> c.composeLazyKey(index) }
                                    ) { _, cached ->
                                        val user = uiState.profiles.find { it.id == cached.id }
                                            ?: cached
                                        ListItem(
                                            headlineContent = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Text(
                                                        user.name,
                                                        color = MaterialTheme.colorScheme.onBackground,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                    if (user.hasBlueTick) {
                                                        Spacer(Modifier.width(4.dp))
                                                        VerifiedBadge(size = 12.dp)
                                                    }
                                                    Spacer(Modifier.width(6.dp))
                                                    GenderAgeBadge(
                                                        gender = user.genderText,
                                                        age = user.currentAge,
                                                    )
                                                }
                                            },
                                            supportingContent = {
                                                val lastMsg = uiState.messages[user.id]?.lastOrNull()
                                                Text(
                                                    text = if (lastMsg?.type == "gift") "Sent a gift"
                                                           else lastMsg?.text ?: "",
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onBackground
                                                        .copy(alpha = 0.6f),
                                                )
                                            },
                                            leadingContent = {
                                                Box {
                                                    Surface(
                                                        modifier = Modifier.size(56.dp).clip(CircleShape),
                                                        color = AppSurfaceVar,
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                user.name.take(1),
                                                                color = BrandPink,
                                                                style = MaterialTheme.typography.titleMedium,
                                                                fontWeight = FontWeight.Bold,
                                                            )
                                                        }
                                                    }
                                                    if (user.isOnline) {
                                                        Surface(
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .align(Alignment.BottomEnd),
                                                            shape = CircleShape,
                                                            color = BrandCyan,
                                                            border = BorderStroke(
                                                                2.dp,
                                                                MaterialTheme.colorScheme.background,
                                                            ),
                                                        ) {}
                                                    }
                                                }
                                            },
                                            trailingContent = {
                                                val lastMsg = uiState.messages[user.id]?.lastOrNull()
                                                if (lastMsg != null) {
                                                    val sdf = SimpleDateFormat(
                                                        "h:mm a",
                                                        Locale.getDefault(),
                                                    )
                                                    Text(
                                                        text = sdf.format(Date(lastMsg.timestamp)),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onBackground
                                                            .copy(alpha = 0.5f),
                                                    )
                                                }
                                            },
                                            colors = ListItemDefaults.colors(
                                                containerColor = Color.Transparent
                                            ),
                                            modifier = Modifier.clickable { selectedPartner = user },
                                        )
                                        HorizontalDivider(
                                            color = OutlineDefault,
                                            modifier = Modifier.padding(start = 86.dp, end = 16.dp),
                                            thickness = 0.5.dp,
                                        )
                                    }
                                }
                            }
                        }
                        1 -> CrmAnnouncementsTab(announcements = uiState.crmAnnouncements)
                    }
                }
            } else {
                val sp = resolvedPartner ?: selectedPartner!!
                ChatDetailScreen(
                    partner = sp,
                    chatPartnerProfile = uiState.chatPartnerProfile?.takeIf { it.id == sp.id },
                    messages = uiState.messages[sp.id] ?: emptyList(),
                    currentUserId = uiState.currentUser?.id ?: "me",
                    availableGifts = uiState.availableGifts,
                    onBack = {
                        selectedPartner = null
                        onClearMessageThreadFocus()
                    },
                    onSendMessage = { text -> onSendMessage(sp.id, text) },
                    onSendImageMessage = { uri -> onSendImageMessage(sp.id, uri) },
                    isGuestPreview = isGuestPreview,
                    onGuestRestricted = onGuestRestricted,
                    isImageUploading = (uiState.pendingImageUploads[sp.id] ?: 0) > 0,
                    uploadError = uiState.imageUploadError,
                    onConsumeUploadError = onConsumeImageUploadError,
                    onStartCall = { isVideo -> onStartCall(isVideo, sp.id) },
                    onSendGiftMessage = { gift -> onSendGiftMessage(sp.id, gift) },
                    onSendGift = onSendGift,
                    isGiftTransactionProcessing = uiState.isGiftTransactionProcessing,
                    coinBalance = uiState.coinBalance,
                    onTopUpCoins = onTopUpCoins,
                    onProcessLuckyGifts = onProcessLuckyGifts,
                    onLuckyGiftVisual = onLuckyGiftVisual,
                )
            }
        }
    }
}

@Composable
private fun CrmAnnouncementsTab(announcements: List<CrmAnnouncement>) {
    val scheme = MaterialTheme.colorScheme
    if (announcements.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.messages_announcements_empty),
                color = scheme.onBackground.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }
    LazyColumn(
        contentPadding = paddingWithNavigationBars(bottomExtra = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        itemsIndexed(announcements, key = { index, a ->
            a.id.trim().ifEmpty { "crm_ann_$index" }
        }) { _, item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Campaign,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = item.title.ifBlank { stringResource(R.string.messages_tab_announcements) },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (item.createdAtMs > 0L) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = dateFmt.format(Date(item.createdAtMs)),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    if (!item.imageUrl.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (item.body.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = item.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface.copy(alpha = 0.92f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    partner: UserProfile,
    /** Prefer Firestore-resolved profile for display name when [partner] is still a placeholder. */
    chatPartnerProfile: UserProfile? = null,
    messages: List<Message>,
    currentUserId: String,
    availableGifts: List<Gift>,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendImageMessage: (Uri) -> Unit,
    isGuestPreview: Boolean,
    onGuestRestricted: () -> Unit,
    isImageUploading: Boolean,
    uploadError: String?,
    onConsumeUploadError: () -> Unit,
    onSendGiftMessage: (Gift) -> Unit,
    onStartCall: (Boolean) -> Unit,
    onSendGift: (Gift, Int) -> Unit,
    isGiftTransactionProcessing: Boolean,
    coinBalance: Int,
    onTopUpCoins: () -> Unit,
    onProcessLuckyGifts: suspend (String, Int, Int) -> LuckyGiftsOutcome,
    onLuckyGiftVisual: (Gift, String, Int, String?) -> Unit = { _, _, _, _ -> },
) {
    val headerName = (chatPartnerProfile?.name?.takeIf { it.isNotBlank() }
        ?: partner.name.takeIf { it.isNotBlank() && it != "…" })
        ?: "Loading..."
    var textState by remember { mutableStateOf("") }
    var showGiftSheet by remember { mutableStateOf(false) }
    val videoShake = remember { Animatable(0f) }
    val giftShake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val chatListState = rememberLazyListState()
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var lastScrollInteractionMs by remember { mutableStateOf(0L) }
    var prevMessageCount by remember { mutableStateOf(messages.size) }
    val hasNewMessages = !autoScrollEnabled && messages.size > prevMessageCount

    // Detect when user scrolls away from bottom (index 0 = newest with reverseLayout=true)
    LaunchedEffect(chatListState) {
        snapshotFlow { chatListState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index > 0) {
                    autoScrollEnabled = false
                    lastScrollInteractionMs = System.currentTimeMillis()
                } else {
                    // User scrolled back to bottom: re-enable immediately
                    autoScrollEnabled = true
                }
            }
    }

    // Re-enable auto-scroll after 7 seconds of no scroll activity
    LaunchedEffect(chatListState) {
        while (true) {
            delay(1_000L)
            if (!autoScrollEnabled &&
                System.currentTimeMillis() - lastScrollInteractionMs > 7_000L
            ) {
                autoScrollEnabled = true
            }
        }
    }

    // Track message count for "new messages" indicator
    LaunchedEffect(messages.size) {
        if (autoScrollEnabled) prevMessageCount = messages.size
    }

    // Auto-scroll to newest only when auto-scroll is enabled
    LaunchedEffect(messages.size) {
        if (autoScrollEnabled && messages.isNotEmpty()) {
            chatListState.animateScrollToItem(0)
        }
    }
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onSendImageMessage(uri)
        }
    }
    LaunchedEffect(uploadError) {
        if (!uploadError.isNullOrBlank()) {
            snackbarHostState.showSnackbar(uploadError)
            onConsumeUploadError()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { 
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    headerName.take(1).ifBlank { "?" },
                                    fontSize = 14.sp
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    headerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (partner.hasBlueTick) {
                                    Spacer(Modifier.width(4.dp))
                                    VerifiedBadge(size = 14.dp)
                                }
                                Spacer(Modifier.width(6.dp))
                                GenderAgeBadge(
                                    gender = partner.genderText,
                                    age = partner.currentAge
                                )
                            }
                            Text(
                                text = if (partner.isOnline) {
                                    "Online"
                                } else {
                                    LastSeenFormatter.format(partner.lastSeen)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (partner.isOnline) BrandCyan else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Text("Back", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (isGuestPreview) {
                                scope.launch { runLockedShake(videoShake); onGuestRestricted() }
                            } else onStartCall(true)
                        },
                        modifier = Modifier.graphicsLayer { translationX = videoShake.value }
                    ) {
                        Box {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = if (isGuestPreview) 0.45f else 1f))
                            if (isGuestPreview) {
                                PremiumLockBadge(Modifier.align(Alignment.TopEnd))
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth().navigationBarsPadding().imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (isGuestPreview) {
                                onGuestRestricted()
                            } else {
                                pickImageLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        }
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Pick image", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            if (isGiftTransactionProcessing) return@IconButton
                            if (isGuestPreview) {
                                scope.launch { runLockedShake(giftShake); onGuestRestricted() }
                            } else showGiftSheet = true
                        },
                        modifier = Modifier.graphicsLayer { translationX = giftShake.value }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isGiftTransactionProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFFFD700)
                                )
                            } else {
                                Icon(
                                    Icons.Default.CardGiftcard,
                                    contentDescription = "Gifts",
                                    tint = Color(0xFFFFD700).copy(alpha = if (isGuestPreview) 0.45f else 1f)
                                )
                            }
                            if (isGuestPreview) {
                                PremiumLockBadge(Modifier.align(Alignment.TopEnd))
                            }
                        }
                    }
                    TextField(
                        value = textState,
                        onValueChange = { textState = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = BrandPink
                        )
                    )
                    IconButton(
                        onClick = {
                            if (textState.isNotBlank()) {
                                onSendMessage(textState)
                                textState = ""
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = BrandPink)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        if (isGuestPreview) {
                            scope.launch { runLockedShake(videoShake); onGuestRestricted() }
                        } else onStartCall(true)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .graphicsLayer { translationX = videoShake.value },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = BrandPink.copy(alpha = 0.38f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Video call", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                FilledTonalButton(
                    onClick = {
                        if (isGiftTransactionProcessing) return@FilledTonalButton
                        if (isGuestPreview) {
                            scope.launch { runLockedShake(giftShake); onGuestRestricted() }
                        } else showGiftSheet = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .graphicsLayer { translationX = giftShake.value },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFFFD700).copy(alpha = 0.28f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFFFFE082))
                    Spacer(Modifier.width(8.dp))
                    Text("Send gift", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            Box(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
            LazyColumn(
                state = chatListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                reverseLayout = true,
                contentPadding = paddingWithNavigationBars(bottomExtra = 8.dp)
            ) {
                if (isImageUploading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Surface(
                                color = BrandPink,
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = 16.dp,
                                    bottomEnd = 0.dp
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Sending photo...", color = Color.White)
                                }
                            }
                        }
                    }
                }
                items(messages.sortedByDescending { it.timestamp }) { msg ->
                    val isMe = msg.senderId == currentUserId
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                            Surface(
                                color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isMe) 16.dp else 0.dp,
                                    bottomEnd = if (isMe) 0.dp else 16.dp
                                )
                            ) {
                                if (msg.type == "gift") {
                                    GiftMessageBubble(
                                        msg = msg,
                                        isMe = isMe,
                                        availableGifts = availableGifts
                                    )
                                } else if (msg.type == "image") {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        if (msg.giftImageUrl.isRenderableImageUrl()) {
                                            SubcomposeAsyncImage(
                                                model = msg.giftImageUrl,
                                                contentDescription = "Photo",
                                                modifier = Modifier.size(180.dp).clip(RoundedCornerShape(12.dp)),
                                                contentScale = ContentScale.Crop,
                                                loading = {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        CircularProgressIndicator(
                                                            strokeWidth = 2.dp,
                                                            color = Color.White,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                },
                                                error = {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.BrokenImage,
                                                            contentDescription = "Failed to load image",
                                                            tint = Color.White.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(28.dp)
                                                        )
                                                    }
                                                }
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.BrokenImage,
                                                contentDescription = "Invalid image URL",
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = msg.text,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 12,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                            Text(
                                text = sdf.format(Date(msg.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            // "New messages" chip — visible when user scrolled up and new messages arrived
            if (hasNewMessages) {
                Surface(
                    onClick = {
                        scope.launch {
                            autoScrollEnabled = true
                            prevMessageCount = messages.size
                            chatListState.animateScrollToItem(0)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = BrandPink,
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        "New messages ↓",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            } // Box

            if (showGiftSheet) {
                GiftSelectionSheet(
                    gifts = availableGifts,
                    coinBalance = coinBalance,
                    isProcessing = isGiftTransactionProcessing,
                    onTopUpCoins = onTopUpCoins,
                    onDismiss = { showGiftSheet = false },
                    onSend = { gift, count ->
                        onSendGift(gift, count)
                        if (!isGiftTransactionProcessing) showGiftSheet = false
                    },
                    luckyGiftReceiverId = partner.id,
                    onLuckySend = onProcessLuckyGifts,
                    onLuckyGiftVisual = onLuckyGiftVisual,
                )
            }
        }
    }
}

/**
 * Premium gift bubble rendered inside a chat thread when [Message.type] == "gift".
 *
 * Resolves CRM/premium gifts via [availableGifts] first, then [GiftCatalog], then
 * [Message.text] (from [DatingViewModel.sendGiftMessage]). Uses [Icons.Default.CardGiftcard]
 * when there is no image URL and no catalog vector.
 */
private fun giftLookupKey(name: String): String =
    name.trim().lowercase().filter { !it.isWhitespace() }

@Composable
private fun GiftMessageBubble(msg: Message, isMe: Boolean, availableGifts: List<Gift>) {
    val resolvedGift = remember(msg.giftId, msg.text, availableGifts) {
        val gid = msg.giftId?.trim().orEmpty()
        if (gid.isNotEmpty()) {
            val byId = availableGifts.firstOrNull { it.id == gid }
            if (byId != null) return@remember byId
        }
        val nameKey = giftLookupKey(msg.text)
        if (nameKey.isEmpty()) null
        else availableGifts.firstOrNull { giftLookupKey(it.name) == nameKey }
    }
    val catalogEntry = remember(msg.giftId, msg.text, resolvedGift) {
        resolvedGift?.let { GiftCatalog.entryFor(it) }
            ?: GiftCatalog.entryFor(Gift(id = msg.giftId, name = msg.text))
    }
    val giftName = resolvedGift?.name?.trim()?.takeIf { it.isNotEmpty() }
        ?: catalogEntry?.canonicalName
        ?: msg.text.takeIf { it.isNotBlank() && it != "Sent a gift" }
        ?: "Gift"
    val giftEmoji = when {
        resolvedGift != null -> when {
            resolvedGift.category.contains("CRM", ignoreCase = true) -> "✨"
            resolvedGift.category.contains("PREMIUM", ignoreCase = true) -> "💎"
            else -> "🎁"
        }
        else -> when (giftName.lowercase()) {
            "rose"        -> "🌹"
            "fire"        -> "🔥"
            "bouquet"     -> "💐"
            "cake"        -> "🎂"
            "diamond"     -> "💎"
            "crown"       -> "👑"
            "rocket"      -> "🚀"
            "lightning"   -> "⚡"
            "super car"   -> "🏎️"
            "private jet" -> "✈️"
            "castle"      -> "🏰"
            else          -> "🎁"
        }
    }
    val imageUrlForDisplay = remember(msg.giftImageUrl, resolvedGift?.thumbnailUrl) {
        listOfNotNull(
            resolvedGift?.thumbnailUrl?.takeIf { it.isNotBlank() },
            msg.giftImageUrl?.takeIf { it.isNotBlank() }
        ).firstOrNull { it.isRenderableImageUrl() }
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFD700).copy(alpha = if (isMe) 0.14f else 0.10f)
        ),
        border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.45f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (imageUrlForDisplay != null) {
                AsyncImage(
                    model = imageUrlForDisplay,
                    contentDescription = giftName,
                    modifier = Modifier.size(64.dp)
                )
            } else {
                Icon(
                    imageVector = catalogEntry?.fallbackVector ?: Icons.Default.CardGiftcard,
                    contentDescription = giftName,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(52.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Sent a Virtual $giftName $giftEmoji",
                color = Color(0xFFFFD700),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun String?.isRenderableImageUrl(): Boolean {
    if (this.isNullOrBlank()) return false
    val uri = Uri.parse(this)
    val scheme = uri.scheme?.lowercase()
    return (scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank()
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
