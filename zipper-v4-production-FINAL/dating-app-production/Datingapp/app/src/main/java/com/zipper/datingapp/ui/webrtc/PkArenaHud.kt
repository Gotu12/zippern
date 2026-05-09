package com.zipper.datingapp.ui.webrtc

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zipper.datingapp.ui.theme.ItzoUiTokens
import com.zipper.datingapp.R
import androidx.compose.ui.res.stringResource
import java.text.NumberFormat
import java.util.Locale
import com.zipper.datingapp.data.LiveStreamChatMessage
import com.zipper.datingapp.data.PkBattleOutcome
import com.zipper.datingapp.data.PkBattlePhase
import com.zipper.datingapp.data.PkBattleSessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class PkGenderAccent { Female, Male, Default }

private fun normalizePkGender(raw: String): PkGenderAccent {
    val g = raw.trim().lowercase()
    return when (g) {
        "female", "f" -> PkGenderAccent.Female
        "male", "m" -> PkGenderAccent.Male
        else -> PkGenderAccent.Default
    }
}

private fun pkGenderAccentColor(gender: String, fallback: Color): Color =
    when (normalizePkGender(gender)) {
        PkGenderAccent.Female -> Color(0xFFFF6B6B)
        PkGenderAccent.Male -> Color(0xFF4FACFE)
        PkGenderAccent.Default -> fallback
    }

private fun tugRatio(host: Long, guest: Long): Float {
    val t = host + guest
    return if (t <= 0L) 0.5f else host.toFloat() / t.toFloat()
}

private fun formatPkBeans(n: Long): String =
    NumberFormat.getNumberInstance(Locale.US).format(n.coerceAtLeast(0L))

private fun pkGenderSymbol(gender: String): String =
    when (normalizePkGender(gender)) {
        PkGenderAccent.Female -> "♀"
        PkGenderAccent.Male -> "♂"
        else -> ""
    }

private val NeonPink = ItzoUiTokens.NeonAccent

/** Itzo `pk_item_layout` + [PkProgressBar]: left / right bar segments (`p_color` / `k_color`). */
private val ItzoPkBarLeft = ItzoUiTokens.PkBarLeft
private val ItzoPkBarRight = ItzoUiTokens.PkBarRight
/** Itzo PK video frame paddings (`#00E6CB` / `#9E74F7`). */
private val ItzoPkFrameLeft = ItzoUiTokens.FrameTeal
private val ItzoPkFrameRight = ItzoUiTokens.FrameViolet
/** Itzo primary accent (`app_color`). */
private val ItzoAppAccent = ItzoUiTokens.AppAccent

@Composable
private fun PkHostCornerTag(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(8.dp), spotColor = ItzoAppAccent)
            .clip(RoundedCornerShape(8.dp))
            .background(ItzoAppAccent)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            stringResource(R.string.pk_corner_host),
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.4.sp
        )
    }
}

@Composable
private fun PkArenaBattlerNamePill(
    modifier: Modifier = Modifier,
    level: Int,
    displayName: String,
    gender: String,
    borderBrush: Brush,
    onClick: (() -> Unit)? = null,
) {
    val lv = level.coerceAtLeast(1)
    val sym = pkGenderSymbol(gender)
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .shadow(6.dp, RoundedCornerShape(18.dp), spotColor = NeonPink.copy(alpha = 0.5f))
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, borderBrush, RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.52f))
                .then(
                    if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
                )
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lv. $lv",
                color = Color(0xFFE879F9),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = displayName.ifBlank { "—" },
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp)
            )
            if (sym.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = sym,
                    color = pkGenderAccentColor(gender, Color.White),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private data class PkGiftAgg(val score: Long, val firstSeen: Long)

/**
 * Top gift senders to a PK battler, ranked by catalog diamond [Gift.price] × [LiveStreamChatMessage.giftCount]
 * when [giftPriceLookup] returns &gt; 0; otherwise by [giftCount] alone.
 */
fun pkRankedSupporterPhotoUrlsForRecipient(
    messages: List<LiveStreamChatMessage>,
    recipientUserId: String,
    senderPhotoLookup: (String) -> String?,
    giftPriceLookup: (String) -> Int = { 0 },
    maxCount: Int = 3,
): List<String> {
    val rid = recipientUserId.trim()
    if (rid.isEmpty()) return emptyList()
    val totals = mutableMapOf<String, PkGiftAgg>()
    for (m in messages) {
        if (m.type != "gift" || m.senderId.isBlank()) continue
        if (m.giftRecipientId.trim() != rid) continue
        val price = giftPriceLookup(m.giftId).coerceAtLeast(0)
        val delta = if (price > 0) {
            price.toLong() * m.giftCount.coerceAtLeast(1)
        } else {
            m.giftCount.coerceAtLeast(1).toLong()
        }
        val cur = totals[m.senderId]
        if (cur == null) {
            totals[m.senderId] = PkGiftAgg(delta, m.timestamp)
        } else {
            totals[m.senderId] = PkGiftAgg(cur.score + delta, minOf(cur.firstSeen, m.timestamp))
        }
    }
    return totals.entries
        .sortedWith(
            compareByDescending<Map.Entry<String, PkGiftAgg>> { it.value.score }
                .thenBy { it.value.firstSeen }
        )
        .take(maxCount)
        .mapNotNull { e -> senderPhotoLookup(e.key)?.trim()?.takeIf { it.isNotEmpty() } }
}

fun pkMvpSupporterPhotoUrls(
    messages: List<LiveStreamChatMessage>,
    hostUserId: String,
    senderPhotoLookup: (String) -> String?,
    giftPriceLookup: (String) -> Int = { 0 },
): List<String> = pkRankedSupporterPhotoUrlsForRecipient(
    messages = messages,
    recipientUserId = hostUserId,
    senderPhotoLookup = senderPhotoLookup,
    giftPriceLookup = giftPriceLookup,
    maxCount = 3,
)

private val PkRankGold = Color(0xFFFFD700)
private val PkRankSilver = Color(0xFFC0C0C0)
private val PkRankBronze = Color(0xFFCD7F32)

@Composable
private fun PkVsCenterBadge(modifier: Modifier = Modifier) {
    val cd = stringResource(R.string.pk_vs_badge_cd)
    Box(
        modifier
            .size(44.dp)
            .semantics { contentDescription = cd }
            .clip(CircleShape)
            .border(
                2.dp,
                Brush.linearGradient(listOf(ItzoPkBarLeft, ItzoPkBarRight)),
                CircleShape
            )
            .background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.pk_vs_badge),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun PkRankedSupporterBubble(
    photoUrl: String?,
    rank: Int,
) {
    val borderColor = when (rank) {
        1 -> PkRankGold
        2 -> PkRankSilver
        else -> PkRankBronze
    }
    val size = 28.dp
    Box(
        modifier = Modifier.size(34.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl.isNullOrBlank()) {
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .border(2.dp, borderColor, CircleShape)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
private fun PkItzoStyleLeaderboardRow(
    leftRankedUrls: List<String>,
    rightRankedUrls: List<String>,
    modifier: Modifier = Modifier,
) {
    val rowCd = stringResource(R.string.pk_top_gifters_cd)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { contentDescription = rowCd },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0..2) {
                val url = leftRankedUrls.getOrNull(2 - i)
                PkRankedSupporterBubble(photoUrl = url, rank = 3 - i)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0..2) {
                val url = rightRankedUrls.getOrNull(i)
                PkRankedSupporterBubble(photoUrl = url, rank = i + 1)
            }
        }
    }
}

/**
 * PK battle HUD aligned with Itzo `pk_item_layout`: dual video frames (teal / violet borders), flat
 * purple/pink progress bar, PK + score rows, centered `mm : ss` timer pill (no bottom PK medallion).
 */
@Composable
fun PkArenaSpectatorBattleHud(
    session: PkBattleSessionState,
    modifier: Modifier = Modifier,
    hostColor: Color = ItzoPkFrameLeft,
    guestColor: Color = ItzoPkFrameRight,
    hostGender: String = "",
    guestGender: String = "",
    onTimerFinished: () -> Unit,
    hostDisplayName: String = "",
    guestDisplayName: String = "",
    hostLevel: Int = 1,
    guestLevel: Int = 1,
    hostOnLeft: Boolean = true,
    /** Spectator: tap battler pill to open profile. Omit for no-op. */
    onHostProfileClick: (() -> Unit)? = null,
    onGuestProfileClick: (() -> Unit)? = null,
    /** Extra lift added on top of the 32.dp base clearance when the HUD stacks over extra bottom UI (e.g. in-call overlay). */
    pkRoundBadgeBottomInset: Dp = 8.dp,
    liveChatMessages: List<LiveStreamChatMessage> = emptyList(),
    liveChatSenderPhotoLookup: (String) -> String? = { null },
    /** Catalog [Gift.price] by [Gift.id]; when zero, ranking uses [LiveStreamChatMessage.giftCount] only. */
    giftPriceLookup: (String) -> Int = { 0 },
) {
    fun leftBarRatio(hostShare: Float) =
        if (hostOnLeft) hostShare else (1f - hostShare)

    val hostShareLive = tugRatio(session.hostBeansEarned, session.guestBeansEarned)
    val liveLeftRatio = leftBarRatio(hostShareLive)
    val targetLeftRatio = session.frozenTugRatio?.let { leftBarRatio(it) } ?: liveLeftRatio
    val animatedRatio by animateFloatAsState(
        targetValue = targetLeftRatio.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 420),
        label = "pkTugRatio"
    )

    val leftBeans =
        if (hostOnLeft) session.hostBeansEarned else session.guestBeansEarned
    val rightBeans =
        if (hostOnLeft) session.guestBeansEarned else session.hostBeansEarned

    val leftFrameColor = hostColor
    val rightFrameColor = guestColor

    val roundMs = session.durationSeconds.coerceIn(60, 3600) * 1000L

    var remainingSec by remember(session.pkStartTimeMillis, session.battleEnded, session.durationSeconds) {
        mutableIntStateOf(0)
    }

    LaunchedEffect(session.pkStartTimeMillis, session.battleEnded, session.durationSeconds) {
        val start = session.pkStartTimeMillis
        if (session.battleEnded || start <= 0L) {
            remainingSec = 0
            return@LaunchedEffect
        }
        while (true) {
            val elapsed = System.currentTimeMillis() - start
            val leftSec = ((roundMs - elapsed) / 1000L).toInt().coerceAtLeast(0)
            remainingSec = leftSec
            if (leftSec <= 0) {
                onTimerFinished()
                break
            }
            val tick = if (leftSec <= 5) 80L else 250L
            delay(tick)
        }
    }

    val mm = remainingSec / 60
    val ss = remainingSec % 60
    val timeLabel = String.format(Locale.US, "%02d : %02d", mm, ss)

    val leftLevel = if (hostOnLeft) hostLevel else guestLevel
    val rightLevel = if (hostOnLeft) guestLevel else hostLevel
    val leftName = if (hostOnLeft) hostDisplayName else guestDisplayName
    val rightName = if (hostOnLeft) guestDisplayName else hostDisplayName
    val leftGender = if (hostOnLeft) hostGender else guestGender
    val rightGender = if (hostOnLeft) guestGender else hostGender
    val leftBrush = Brush.horizontalGradient(listOf(ItzoPkBarLeft, ItzoPkBarRight.copy(alpha = 0.85f)))
    val rightBrush = Brush.horizontalGradient(listOf(ItzoPkBarRight.copy(alpha = 0.85f), ItzoPkBarLeft))

    val leftRecipientUid =
        if (hostOnLeft) session.hostUserId.trim() else session.guestUserId.trim()
    val rightRecipientUid =
        if (hostOnLeft) session.guestUserId.trim() else session.hostUserId.trim()
    val leftSupporters = pkRankedSupporterPhotoUrlsForRecipient(
        liveChatMessages,
        leftRecipientUid,
        liveChatSenderPhotoLookup,
        giftPriceLookup,
        3,
    )
    val rightSupporters = pkRankedSupporterPhotoUrlsForRecipient(
        liveChatMessages,
        rightRecipientUid,
        liveChatSenderPhotoLookup,
        giftPriceLookup,
        3,
    )

    Box(
        modifier = modifier.graphicsLayer(clip = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp, bottom = 14.dp + pkRoundBadgeBottomInset)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .border(2.dp, leftFrameColor, RoundedCornerShape(12.dp))
                )
                if (hostOnLeft) {
                    PkHostCornerTag(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 10.dp, top = 8.dp)
                    )
                }
                PkArenaBattlerNamePill(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = if (!hostOnLeft) 56.dp else 0.dp, bottom = 4.dp),
                    level = leftLevel,
                    displayName = leftName,
                    gender = leftGender,
                    borderBrush = leftBrush,
                    onClick = if (hostOnLeft) onHostProfileClick else onGuestProfileClick,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .border(2.dp, rightFrameColor, RoundedCornerShape(12.dp))
                )
                if (!hostOnLeft) {
                    PkHostCornerTag(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 10.dp, top = 8.dp)
                    )
                }
                PkArenaBattlerNamePill(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = if (hostOnLeft) 56.dp else 0.dp, bottom = 4.dp),
                    level = rightLevel,
                    displayName = rightName,
                    gender = rightGender,
                    borderBrush = rightBrush,
                    onClick = if (hostOnLeft) onGuestProfileClick else onHostProfileClick,
                )
            }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(6f),
                contentAlignment = Alignment.Center,
            ) {
                PkVsCenterBadge()
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(10f)
                .statusBarsPadding()
                .padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .widthIn(min = 72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = timeLabel,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(animatedRatio.coerceIn(0.001f, 1f))
                            .fillMaxSize()
                            .background(ItzoPkBarLeft)
                    )
                    Box(
                        modifier = Modifier
                            .weight((1f - animatedRatio).coerceIn(0.001f, 1f))
                            .fillMaxSize()
                            .background(ItzoPkBarRight)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.live_pk_short),
                        color = ItzoPkBarLeft,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = formatPkBeans(leftBeans),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(
                            shadow = Shadow(color = Color.Gray.copy(alpha = 0.9f), blurRadius = 3f)
                        ),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = formatPkBeans(rightBeans),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        style = TextStyle(
                            shadow = Shadow(color = Color.Gray.copy(alpha = 0.9f), blurRadius = 3f)
                        ),
                    )
                    Text(
                        text = stringResource(R.string.live_pk_short),
                        color = ItzoPkBarRight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            PkItzoStyleLeaderboardRow(
                leftRankedUrls = leftSupporters,
                rightRankedUrls = rightSupporters,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

    }
}

@Composable
fun PkArenaEndCard(
    session: PkBattleSessionState,
    modifier: Modifier = Modifier,
    showRematchForHost: Boolean = false,
    showFindAnotherForHost: Boolean = false,
    rematchPending: Boolean = false,
    rematchWaitingLabel: String = "Waiting for guest…",
    onRematchChallenge: () -> Unit = {},
    onFindAnotherOpponent: () -> Unit = {},
    showChallengerPostPkActions: Boolean = false,
    challengerContinueLabel: String = "",
    challengerFindAnotherLabel: String = "",
    onChallengerContinueSolo: () -> Unit = {},
    onChallengerFindAnother: () -> Unit = {},
    mvpViewerPhotoUrls: List<String> = emptyList()
) {
    val outcome = session.outcome ?: return
    val title = when (outcome) {
        PkBattleOutcome.VICTORY -> "VICTORY"
        PkBattleOutcome.DEFEAT -> "DEFEAT"
        PkBattleOutcome.DRAW -> "DRAW"
        PkBattleOutcome.HOST_WINS -> "HOST WINS"
        PkBattleOutcome.GUEST_WINS -> "GUEST WINS"
    }
    val titleColor = when (outcome) {
        PkBattleOutcome.VICTORY -> Color(0xFF4CAF50)
        PkBattleOutcome.DEFEAT -> Color(0xFFFF5252)
        PkBattleOutcome.DRAW -> Color(0xFFFFEB3B)
        PkBattleOutcome.HOST_WINS -> Color(0xFF2196F3)
        PkBattleOutcome.GUEST_WINS -> Color(0xFFE53935)
    }
    val glow = rememberInfiniteTransition(label = "pkEndGlow")
    val pulse by glow.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pkEndPulse"
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF6A1B9A).copy(alpha = 0.35f),
                        Color.Black.copy(alpha = 0.72f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(24.dp)
                .scale(pulse)
                .shadow(24.dp, RoundedCornerShape(20.dp), spotColor = titleColor, ambientColor = Color(0xFFE040FB))
                .clip(RoundedCornerShape(20.dp))
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        listOf(titleColor, Color(0xFF00E5FF), titleColor)
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .background(Color(0xFF120822).copy(alpha = 0.94f))
                .padding(horizontal = 22.dp, vertical = 20.dp)
        ) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                style = TextStyle(
                    shadow = Shadow(
                        color = titleColor.copy(alpha = 0.85f),
                        blurRadius = 18f
                    )
                )
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Host beans: ${session.hostBeansEarned}\nGuest beans: ${session.guestBeansEarned}",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            if (mvpViewerPhotoUrls.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "MVP supporters",
                    color = Color(0xFFFFE082),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (url in mvpViewerPhotoUrls) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .border(
                                    2.dp,
                                    Brush.horizontalGradient(listOf(Color(0xFFFF4081), Color(0xFF00E5FF))),
                                    CircleShape
                                )
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.3f))
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
            if (session.resultPhaseEndsAtMillis > 0L) {
                var tick by remember(session.resultPhaseEndsAtMillis) { mutableIntStateOf(0) }
                LaunchedEffect(session.resultPhaseEndsAtMillis) {
                    tick = 0
                    while (isActive && System.currentTimeMillis() < session.resultPhaseEndsAtMillis) {
                        delay(1000)
                        tick++
                    }
                }
                val remainSec = remember(session.resultPhaseEndsAtMillis, tick) {
                    ((session.resultPhaseEndsAtMillis - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L).toInt()
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.pk_post_result_countdown, remainSec.coerceAtLeast(0)),
                    color = Color(0xFFFFE082),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            if (showRematchForHost || showFindAnotherForHost) {
                Spacer(Modifier.height(18.dp))
                if (rematchPending) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFFFE082)
                        )
                        Text(
                            text = rematchWaitingLabel,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (showRematchForHost) {
                            Button(
                                onClick = onRematchChallenge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D00)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.pk_rematch_challenge_button),
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                        if (showFindAnotherForHost) {
                            OutlinedButton(
                                onClick = onFindAnotherOpponent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.pk_find_another_opponent),
                                    color = Color(0xFFFFE082),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
            if (showChallengerPostPkActions &&
                challengerContinueLabel.isNotBlank() &&
                challengerFindAnotherLabel.isNotBlank()
            ) {
                Spacer(Modifier.height(18.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onChallengerContinueSolo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D00)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = challengerContinueLabel,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                    OutlinedButton(
                        onClick = onChallengerFindAnother,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = challengerFindAnotherLabel,
                            color = Color(0xFFFFE082),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PkArenaHud(
    session: PkBattleSessionState,
    modifier: Modifier = Modifier,
    hostColor: Color = ItzoPkFrameLeft,
    guestColor: Color = ItzoPkFrameRight,
    hostGender: String = "",
    guestGender: String = "",
    onTimerFinished: () -> Unit,
    liveChatMessages: List<LiveStreamChatMessage> = emptyList(),
    liveChatSenderPhotoLookup: (String) -> String? = { null },
    showRematchForHost: Boolean = false,
    showFindAnotherForHost: Boolean = false,
    rematchPending: Boolean = false,
    rematchWaitingLabel: String = "Waiting for guest…",
    onRematchChallenge: () -> Unit = {},
    onFindAnotherOpponent: () -> Unit = {},
    hostDisplayName: String = "",
    guestDisplayName: String = "",
    hostLevel: Int = 1,
    guestLevel: Int = 1,
    hostOnLeft: Boolean = true,
    onHostProfileClick: (() -> Unit)? = null,
    onGuestProfileClick: (() -> Unit)? = null,
    pkRoundBadgeBottomInset: Dp = 8.dp,
    /** When true (e.g. PK host shows [PkArenaEndCard] in the audience Compose layer), hide the in-HUD end card to avoid duplicate UI. */
    suppressPostPkEndCard: Boolean = false,
    giftPriceLookup: (String) -> Int = { 0 },
) {
    val mvps = remember(session.hostUserId, liveChatMessages, giftPriceLookup) {
        pkMvpSupporterPhotoUrls(
            liveChatMessages,
            session.hostUserId,
            liveChatSenderPhotoLookup,
            giftPriceLookup,
        )
    }
    var pkClockTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(session.battleEnded, session.resultPhaseEndsAtMillis, session.outcome) {
        val ends = session.resultPhaseEndsAtMillis
        if (!session.battleEnded || session.outcome == null || ends <= 0L) return@LaunchedEffect
        pkClockTick = 0
        while (isActive && System.currentTimeMillis() < ends) {
            delay(500)
            pkClockTick++
        }
    }
    pkClockTick // anchor recompositions while countdown runs
    val nowMs = System.currentTimeMillis()
    val showResultsOverlay = !suppressPostPkEndCard &&
        session.battleEnded &&
        session.outcome != null &&
        (session.resultPhaseEndsAtMillis <= 0L || nowMs < session.resultPhaseEndsAtMillis)
    val showTimerHud =
        !session.battleEnded && session.phase == PkBattlePhase.ACTIVE

    Box(modifier = modifier.fillMaxSize()) {
        if (showTimerHud) {
            PkArenaSpectatorBattleHud(
                session = session,
                modifier = Modifier.fillMaxSize(),
                hostColor = hostColor,
                guestColor = guestColor,
                hostGender = hostGender,
                guestGender = guestGender,
                onTimerFinished = onTimerFinished,
                hostDisplayName = hostDisplayName,
                guestDisplayName = guestDisplayName,
                hostLevel = hostLevel,
                guestLevel = guestLevel,
                hostOnLeft = hostOnLeft,
                onHostProfileClick = onHostProfileClick,
                onGuestProfileClick = onGuestProfileClick,
                pkRoundBadgeBottomInset = pkRoundBadgeBottomInset,
                liveChatMessages = liveChatMessages,
                liveChatSenderPhotoLookup = liveChatSenderPhotoLookup,
                giftPriceLookup = giftPriceLookup,
            )
        }
        if (showResultsOverlay) {
            PkArenaEndCard(
                session = session,
                modifier = Modifier.fillMaxSize(),
                showRematchForHost = showRematchForHost,
                showFindAnotherForHost = showFindAnotherForHost,
                rematchPending = rematchPending,
                rematchWaitingLabel = rematchWaitingLabel,
                onRematchChallenge = onRematchChallenge,
                onFindAnotherOpponent = onFindAnotherOpponent,
                mvpViewerPhotoUrls = mvps
            )
        }
    }
}
