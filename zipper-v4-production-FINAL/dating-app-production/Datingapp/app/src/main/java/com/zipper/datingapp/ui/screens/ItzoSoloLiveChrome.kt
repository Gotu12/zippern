package com.zipper.datingapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zipper.datingapp.R
import com.zipper.datingapp.data.UserProfile

private val ItzoHostPillPurple = Color(0xFF1F0331)
private val ItzoAppBg = Color(0xFF0F0F0F)
private val ItzoViewerChipScrim = Color(0xA7000000)

/** Full-screen bitmap backdrop: host uses audio_bg art, watcher uses live_bg art. */
@Composable
fun ItzoSoloLiveBackdrop(isHost: Boolean, modifier: Modifier = Modifier) {
    val res =
        if (isHost) {
            R.drawable.itzo_solo_host_live_bg
        } else {
            R.drawable.itzo_solo_watcher_live_bg
        }
    Box(modifier.background(ItzoAppBg)) {
        Image(
            painter = painterResource(res),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
fun ItzoSoloLiveHostTopChrome(
    photoUrl: String?,
    displayName: String,
    profileNumber: String,
    viewerCount: Int,
    viewerUserIds: List<String>,
    lookupPhotoUrl: (String) -> String?,
    beansBalance: Int,
    streamTimerLabel: String,
    onHostPillClick: () -> Unit,
    onViewersClick: () -> Unit,
    onWalletClick: () -> Unit,
    onCloseClick: () -> Unit,
    /** Itzo-style audio room overflow: theme, seats, etc. */
    onStreamMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HostIdentityPill(
                onClick = onHostPillClick,
                modifier = Modifier.widthIn(max = 118.dp),
                photoUrl = photoUrl,
                displayName = displayName,
                profileNumber = profileNumber,
            )
            Spacer(Modifier.width(8.dp))
            LazyRow(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(viewerUserIds.distinct().take(24)) { uid ->
                    AsyncImage(
                        model = lookupPhotoUrl(uid).orEmpty().takeIf { it.isNotBlank() },
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            ViewerCountChip(viewerCount = viewerCount, onClick = onViewersClick)
            if (onStreamMenuClick != null) {
                IconButton(onClick = onStreamMenuClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.live_audio_party_stream_menu_cd),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            IconButton(onClick = onCloseClick, modifier = Modifier.size(40.dp)) {
                Image(
                    painter = painterResource(R.drawable.itzo_close),
                    contentDescription = stringResource(R.string.live_leave_stream_cd),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, top = 6.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(ItzoHostPillPurple)
                        .clickable(onClick = onWalletClick)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.itzo_d_wallet),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = beansBalance.toString(),
                    color = Color.White,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.width(6.dp))
                Image(
                    painter = painterResource(R.drawable.itzo_ic_next),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            }
            if (streamTimerLabel.isNotBlank()) {
                Text(
                    text = streamTimerLabel,
                    color = Color.White,
                    fontSize = 13.sp,
                    style =
                        TextStyle(
                            shadow =
                                Shadow(
                                    color = Color(0xFF808080),
                                    offset = Offset(2f, 2f),
                                    blurRadius = 2f,
                                ),
                        ),
                )
            }
        }
    }
}

@Composable
fun ItzoSoloLiveWatcherTopChrome(
    partner: UserProfile,
    viewerCount: Int,
    viewerUserIds: List<String>,
    lookupPhotoUrl: (String) -> String?,
    streamTimerLabel: String,
    onOpenHostProfile: () -> Unit,
    onViewersClick: () -> Unit,
    onCloseClick: () -> Unit,
    isSpeakerOn: Boolean,
    onToggleSpeaker: () -> Unit,
    hearsStreamerVoice: Boolean,
    onToggleHearStreamer: () -> Unit,
    onStreamMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HostIdentityPill(
                onClick = onOpenHostProfile,
                modifier = Modifier.widthIn(max = 118.dp),
                photoUrl = partner.photoUrl.takeIf { it.isNotBlank() },
                displayName =
                    partner.name.ifBlank { stringResource(R.string.live_pk_battler_host_fallback) },
                profileNumber = partner.profileNumber,
            )
            Spacer(Modifier.width(8.dp))
            LazyRow(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(viewerUserIds.distinct().take(24)) { uid ->
                    AsyncImage(
                        model = lookupPhotoUrl(uid).orEmpty().takeIf { it.isNotBlank() },
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            ViewerCountChip(viewerCount = viewerCount, onClick = onViewersClick)
            IconButton(
                onClick = onToggleHearStreamer,
                modifier =
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
            ) {
                Icon(
                    imageVector = if (hearsStreamerVoice) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription =
                        stringResource(
                            if (hearsStreamerVoice) {
                                R.string.live_watcher_unmute_streamer_cd
                            } else {
                                R.string.live_watcher_mute_streamer_cd
                            },
                        ),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = onToggleSpeaker,
                modifier =
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
            ) {
                Icon(
                    imageVector = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = if (isSpeakerOn) "Speaker on" else "Speaker off",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (onStreamMenuClick != null) {
                IconButton(onClick = onStreamMenuClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.live_audio_party_audience_menu_cd),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            IconButton(onClick = onCloseClick, modifier = Modifier.size(40.dp)) {
                Image(
                    painter = painterResource(R.drawable.itzo_close),
                    contentDescription = stringResource(R.string.live_leave_stream_cd),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (streamTimerLabel.isNotBlank()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, top = 6.dp, end = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = streamTimerLabel,
                    color = Color.White,
                    fontSize = 13.sp,
                    style =
                        TextStyle(
                            shadow =
                                Shadow(
                                    color = Color(0xFF808080),
                                    offset = Offset(2f, 2f),
                                    blurRadius = 2f,
                                ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun HostIdentityPill(
    onClick: () -> Unit,
    photoUrl: String?,
    displayName: String,
    profileNumber: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(20.dp))
                .background(ItzoHostPillPurple)
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = photoUrl?.takeIf { it.isNotBlank() },
                contentDescription = null,
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.widthIn(max = 72.dp)) {
            Text(
                text = displayName.ifBlank { "—" },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    stringResource(R.string.itzo_live_profile_id_line, profileNumber.ifBlank { "—" }),
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ViewerCountChip(viewerCount: Int, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(ItzoViewerChipScrim)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.itzo_eye),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = viewerCount.toString(),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Game / PK / stream menu — docked above compliance + composer on solo video live. */
@Composable
fun ItzoSoloHostSideActionRail(
    isPkSearching: Boolean,
    isPkRetry: Boolean,
    onPkActionClick: () -> Unit,
    onPkToolsMenu: () -> Unit,
    onGamePlaceholderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionSpaced = 8.dp
    val circleBtn =
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(actionSpaced),
    ) {
        IconButton(
            onClick = onGamePlaceholderClick,
            modifier = circleBtn,
        ) {
            Image(
                painter = painterResource(R.drawable.itzo_game_icon),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
            )
        }
        IconButton(
            onClick = onPkActionClick,
            modifier = circleBtn,
        ) {
            when {
                isPkSearching ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                isPkRetry ->
                    Text(
                        text = stringResource(R.string.live_pk_retry),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                else ->
                    Image(
                        painter = painterResource(R.drawable.itzo_pk_icon),
                        contentDescription = stringResource(R.string.live_pk_short),
                        modifier = Modifier.size(26.dp),
                    )
            }
        }
        IconButton(
            onClick = onPkToolsMenu,
            modifier = circleBtn,
        ) {
            Image(
                painter = painterResource(R.drawable.itzo_stream_menu),
                contentDescription = stringResource(R.string.live_stream_menu_cd),
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** Host bottom: message capsule + mic / flip / video (solo video live). */
@Composable
fun ItzoSoloHostBottomActionRow(
    chatMessage: String,
    onChatMessageChange: (String) -> Unit,
    onSendChat: () -> Unit,
    hostPkMicOn: Boolean,
    onToggleHostMic: () -> Unit,
    canToggleMic: Boolean,
    onFlipCamera: () -> Unit,
    canFlipCamera: Boolean,
    onVideoPlaceholderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionSpaced = 8.dp
    val circleBtn =
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(ItzoViewerChipScrim)
                    .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.itzo_message_icon),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Box(
                modifier =
                    Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(Color.White),
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = chatMessage,
                onValueChange = onChatMessageChange,
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 24.dp, max = 44.dp),
                singleLine = true,
                textStyle =
                    TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                    ),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions =
                    KeyboardActions(
                        onSend = { onSendChat() },
                    ),
                decorationBox = { inner ->
                    // Inner field must fill width and sit above the hint so typed glyphs measure and draw reliably.
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
                    ) {
                        if (chatMessage.isEmpty()) {
                            Text(
                                text = stringResource(R.string.live_chat_hint),
                                color = Color.White.copy(alpha = 0.62f),
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.CenterStart),
                            )
                        }
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.CenterStart)
                                    .zIndex(1f),
                        ) {
                            inner()
                        }
                    }
                },
            )
            IconButton(onClick = onSendChat, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.live_send),
                    tint = Color(0xFFFF4081),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(actionSpaced))
        Row(
            horizontalArrangement = Arrangement.spacedBy(actionSpaced),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(
                onClick = onToggleHostMic,
                enabled = canToggleMic,
                modifier = circleBtn,
            ) {
                Icon(
                    imageVector = if (hostPkMicOn) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription =
                        stringResource(
                            if (hostPkMicOn) R.string.live_host_mic_on_cd else R.string.live_host_mic_muted_cd,
                        ),
                    tint = if (hostPkMicOn) Color.White else Color(0xFFFF5252),
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(
                onClick = onFlipCamera,
                enabled = canFlipCamera,
                modifier = circleBtn,
            ) {
                Icon(
                    Icons.Default.FlipCameraAndroid,
                    contentDescription = stringResource(R.string.call_flip_camera),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(
                onClick = onVideoPlaceholderClick,
                modifier = circleBtn,
            ) {
                Image(
                    painter = painterResource(R.drawable.itzo_video_iconn),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}
