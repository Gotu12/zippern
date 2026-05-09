package com.zipper.datingapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zipper.datingapp.R
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zipper.datingapp.data.LiveStreamChatMessage
import com.zipper.datingapp.data.AudioPartyVideoCallRequest
import com.zipper.datingapp.ui.theme.ItzoUiTokens

/** Matches Itzo seat picker options + optional 17-seat grid. */
val AudioPartySeatCountOptions = listOf(6, 10, 14, 17)

/** Keys merged to Firestore `streams/{host}.roomBackgroundKey`. */
object AudioPartyRoomBackgroundPresets {
    const val DEFAULT = "default"
    const val PINK = "pink"
    const val VIOLET = "violet"
    const val TEAL = "teal"
    val ALL = listOf(DEFAULT, PINK, VIOLET, TEAL)
}

/**
 * Soft “floral / bloom” wash on the right side of the room (Itzo-style reference), drawn without assets.
 */
@Composable
fun AudioPartyFloralDecorOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun blob(cx: Float, cy: Float, r: Float, c: Color) {
            drawCircle(color = c, radius = r, center = Offset(cx, cy))
        }
        blob(w * 0.88f, h * 0.12f, w * 0.30f, Color(0xFFFFFBF0).copy(alpha = 0.07f))
        blob(w * 0.94f, h * 0.34f, w * 0.22f, Color(0xFFFFF8F5).copy(alpha = 0.065f))
        blob(w * 0.80f, h * 0.52f, w * 0.26f, Color(0xFFFFE4EE).copy(alpha = 0.08f))
        blob(w * 0.91f, h * 0.74f, w * 0.24f, Color(0xFFFFFBF0).copy(alpha = 0.055f))
        blob(w * 0.86f, h * 0.26f, w * 0.16f, Color(0xFFFFC0CB).copy(alpha = 0.10f))
        blob(w * 0.93f, h * 0.61f, w * 0.14f, Color(0xFFF8BBD9).copy(alpha = 0.09f))
    }
}

@Composable
fun AudioPartyWelcomeBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        color = Color.White.copy(alpha = 0.92f),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    )
}

fun brushForAudioPartyRoomKey(key: String): Brush =
    when (key.trim().lowercase()) {
        AudioPartyRoomBackgroundPresets.PINK ->
            Brush.verticalGradient(
                listOf(Color(0xFFFFF0F5), Color(0xFFFFE3EE), Color(0xFFFFC8DD)),
            )
        AudioPartyRoomBackgroundPresets.VIOLET ->
            Brush.verticalGradient(
                listOf(Color(0xFFF3E5F5), Color(0xFFE1BEE7), Color(0xFFCE93D8)),
            )
        AudioPartyRoomBackgroundPresets.TEAL ->
            Brush.verticalGradient(
                listOf(Color(0xFFE0F7F7), Color(0xFFB2EBF2), Color(0xFF80DEEA)),
            )
        else -> ItzoUiTokens.audioPartyFullBleedRoomBrush()
    }

/**
 * Full-bleed backdrop: preset gradient + tiled floral wash (Itzo-style).
 */
@Composable
fun AudioPartyFullBleedBackdrop(
    roomBackgroundKey: String,
    modifier: Modifier = Modifier,
) {
    val brush = remember(roomBackgroundKey) { brushForAudioPartyRoomKey(roomBackgroundKey) }
    Box(modifier = modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(brush),
        )
        Image(
            painter = painterResource(R.drawable.audio_party_floral_wash),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd,
            alpha = 0.34f,
        )
    }
}

@Composable
fun AudioPartySeatGridPreview(seatCount: Int, modifier: Modifier = Modifier) {
    val n = when (seatCount) {
        6, 10, 14, 17 -> seatCount
        else -> 10
    }
    val guests = (n - 1).coerceAtLeast(1)
    val cols = 5
    val rows = (guests + cols - 1) / cols
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.22f))
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$n seats",
            color = ItzoUiTokens.AudioPartyOnRoomText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .background(ItzoUiTokens.AppAccent.copy(alpha = 0.35f), CircleShape)
                        .border(1.dp, ItzoUiTokens.AppAccent, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Box(
                modifier =
                    Modifier
                        .size(22.dp)
                        .background(Color(0xFFB74BFF).copy(alpha = 0.4f), CircleShape),
            )
        }
        Spacer(Modifier.height(8.dp))
        for (r in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (c in 0 until cols) {
                    val idx = r * cols + c
                    if (idx < guests) {
                        Box(
                            modifier =
                                Modifier
                                    .size(14.dp)
                                    .background(Color.White.copy(alpha = 0.5f), CircleShape)
                                    .border(1.dp, GoldSeatPreview, CircleShape),
                        )
                    } else {
                        Spacer(Modifier.size(14.dp))
                    }
                }
            }
            if (r < rows - 1) Spacer(Modifier.height(4.dp))
        }
    }
}

private val GoldSeatPreview = Color(0xFFD4AF37)

@Composable
fun AudioPartyGoLivePickerDialog(
    seatDraft: Int,
    onSeatDraftChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 520.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    stringResource(R.string.live_audio_party_start_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.live_audio_party_start_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(16.dp))
                AudioPartySeatGridPreview(seatCount = seatDraft, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.live_audio_party_seat_layout_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioPartySeatCountOptions.forEach { n ->
                        FilterChip(
                            selected = seatDraft == n,
                            onClick = { onSeatDraftChange(n) },
                            label = { Text("$n") },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onConfirm) {
                        Text(stringResource(R.string.live_audio_party_start_confirm))
                    }
                }
            }
        }
    }
}

@Composable
fun AudioPartyGiftFlyline(
    messages: List<LiveStreamChatMessage>,
    modifier: Modifier = Modifier,
) {
    val gifts = remember(messages) {
        messages.asReversed().filter { it.type == "gift" }.take(12).asReversed()
    }
    if (gifts.isEmpty()) return
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gifts.forEach { m ->
            val label =
                m.giftName.ifBlank {
                    m.text.substringBefore("×").trim().ifBlank { m.text }
                }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFFFFDDFF).copy(alpha = 0.92f),
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🎁", fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${m.senderName.ifBlank { "Someone" }} · $label",
                        color = ItzoUiTokens.AudioPartyOnRoomText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun LiveChatRowAudioParty(
    message: LiveStreamChatMessage,
    isMine: Boolean,
    senderLevel: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bubble: Brush =
        if (isMine) {
            Brush.horizontalGradient(
                listOf(Color(0xFF7C4DFF).copy(alpha = 0.5f), Color(0xFFE040FB).copy(alpha = 0.48f)),
            )
        } else {
            Brush.horizontalGradient(
                listOf(Color(0xE6281520), Color(0xD91A1024)),
            )
        }
    val textColor = Color.White
    val labelColor =
        if (isMine) Color.White.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.78f)
    Surface(
        modifier =
            modifier
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
        color = Color.Transparent,
    ) {
        Box(
            modifier =
                Modifier.background(bubble, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AudioPartyMiniLevelBadge(level = senderLevel)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = message.senderName.ifBlank { "Someone" },
                        color = labelColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = message.text,
                    color = textColor,
                    fontSize = 12.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AudioPartyMiniLevelBadge(level: Int) {
    val lv = level.coerceAtLeast(1)
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = Color(0xFF3E2760).copy(alpha = 0.94f),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "⬥",
                fontSize = 8.sp,
                color = Color(0xFFFFD54F),
                modifier = Modifier.padding(end = 3.dp),
            )
            Text(
                text = "$lv",
                color = Color(0xFFFFE082),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPartyRoomBackgroundPickerSheet(
    currentKey: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B0F18),
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.25f)) },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp),
        ) {
            Text("Room theme", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            AudioPartyRoomBackgroundPresets.ALL.forEach { key ->
                val label =
                    when (key) {
                        AudioPartyRoomBackgroundPresets.DEFAULT -> "Default party"
                        AudioPartyRoomBackgroundPresets.PINK -> "Pink glow"
                        AudioPartyRoomBackgroundPresets.VIOLET -> "Violet night"
                        AudioPartyRoomBackgroundPresets.TEAL -> "Teal pulse"
                        else -> key
                    }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onPick(key)
                                onDismiss()
                            }
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(brushForAudioPartyRoomKey(key)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                    if (key == currentKey.trim().lowercase()) {
                        Text("✓", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPartyHostVideoCallRequestsSheet(
    requests: List<AudioPartyVideoCallRequest>,
    onDismiss: () -> Unit,
    onRespond: (fromUserId: String, accept: Boolean) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B0F18),
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.25f)) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                stringResource(R.string.live_audio_party_video_requests_sheet_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(12.dp))
            if (requests.isEmpty()) {
                Text(
                    stringResource(R.string.live_audio_party_video_request_empty),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 14.sp,
                )
            } else {
                requests.forEach { req ->
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                req.displayName.ifBlank { req.fromUserId },
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                        TextButton(onClick = { onRespond(req.fromUserId, false) }) {
                            Text(stringResource(R.string.live_audio_party_video_request_decline))
                        }
                        TextButton(onClick = { onRespond(req.fromUserId, true) }) {
                            Text(stringResource(R.string.live_audio_party_video_request_accept))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPartyHostStreamMenuBottomSheet(
    onDismiss: () -> Unit,
    onPickTheme: () -> Unit,
    onLiveUsers: () -> Unit,
    onVideoCallRequests: () -> Unit,
    onSeatBlock: () -> Unit,
    onExit: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B0F18),
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.25f)) },
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Stream menu — theme / seats", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            menuRow("Room theme & backdrop") { onPickTheme() }
            menuRow("Live users") { onLiveUsers() }
            menuRow("Video call requests") { onVideoCallRequests() }
            menuRow("Block / unblock seat") { onSeatBlock() }
            menuRow("Leave stream", danger = true) { onExit() }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPartyAudienceStreamMenuBottomSheet(
    onDismiss: () -> Unit,
    onJoinCall: () -> Unit,
    onChangeSeat: () -> Unit,
    onPurchaseSeat: () -> Unit,
    onRequestVideoWithHost: () -> Unit = {},
    onReport: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B0F18),
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.25f)) },
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Audio room menu", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            menuRow("Join voice (open seats)") { onJoinCall() }
            menuRow("Change seat") { onChangeSeat() }
            menuRow("Purchase seat (diamonds)") { onPurchaseSeat() }
            menuRow(stringResource(R.string.live_audio_party_request_video_with_host)) { onRequestVideoWithHost() }
            menuRow("Report") { onReport() }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun menuRow(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text = label,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp, horizontal = 4.dp),
        color =
            if (danger) Color(0xFFFF8A80) else Color.White,
        fontSize = 16.sp,
    )
}

@Composable
fun AudioPartyConfirmPurchaseSeatDialog(
    seatIndex: Int,
    priceCoins: Int,
    balanceHint: String,
    onDismiss: () -> Unit,
    onBuy: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seat $seatIndex") },
        text = {
            Column {
                Text("This seat costs $priceCoins diamonds. $balanceHint")
            }
        },
        confirmButton = {
            TextButton(onClick = onBuy) { Text("Pay & sit") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
