package com.zipper.datingapp.ui.screens

import android.Manifest
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zipper.datingapp.R
import com.zipper.datingapp.data.Battle
import com.zipper.datingapp.data.BattleResult
import com.zipper.datingapp.data.BattleStatus
import com.zipper.datingapp.data.GameState
import com.zipper.datingapp.data.GameType
import com.zipper.datingapp.data.Gift
import com.zipper.datingapp.data.fallbackImageVector
import com.zipper.datingapp.data.MysteryRewardType
import com.zipper.datingapp.data.SpinSector
import com.zipper.datingapp.ui.DatingUiState
import com.zipper.datingapp.ui.components.FuturisticConfirmCloseDialog
import com.zipper.datingapp.ui.components.FuturisticConfirmCloseStyle
import com.zipper.datingapp.ui.components.GiftSelectionSheet
import com.zipper.datingapp.webrtc.FilterType
import com.zipper.datingapp.webrtc.WebRTCManager
import org.webrtc.SurfaceViewRenderer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Z-order for Game Center / battle UI. Keep above live-stream PK/chat chrome (typically ≤8f) if surfaces merge.
 */
private const val Z_BATTLE_VIDEO_OVERLAY = 20f
private const val Z_BATTLE_RESULT = 30f
private const val Z_BATTLE_CLOSE = 40f

/** Bottom icon row (gift / chat / host controls) — reserve space so spin / mystery / emoji UIs stay tappable. */
private val BattleBottomChromeReserveDp = 96.dp

@Composable
fun LiveBattleScreen(
    uiState: DatingUiState,
    onEndBattle: () -> Unit,
    onVote: (Int) -> Unit,
    onSupport: (Int, Gift, Int) -> Unit,
    onStartSpin: () -> Unit,
    onSpinComplete: () -> Unit,
    onOpenBox: (Int) -> Unit,
    onEmojiGameTurn: (String) -> Unit,
    onSolveEmoji: (String) -> Unit,
    onClearGiftAnimation: () -> Unit,
    onTopUpCoins: () -> Unit,
    onProcessLuckyGifts: suspend (String, Int, Int) -> com.zipper.datingapp.data.LuckyGiftsOutcome,
    onLuckyGiftVisual: (Gift, String, Int, String?) -> Unit = { _, _, _, _ -> },
) {
    val battle = uiState.activeBattle ?: return
    var showGiftSheet by remember { mutableStateOf(false) }
    var showForfeitDialog by remember { mutableStateOf(false) }
    var showChatInput by remember { mutableStateOf(false) }
    var chatText by remember { mutableStateOf("") }
    BackHandler {
        when {
            showGiftSheet -> showGiftSheet = false
            showForfeitDialog -> showForfeitDialog = false
            showChatInput -> showChatInput = false
            else -> showForfeitDialog = true
        }
    }
    val context = LocalContext.current
    val currentUserId = uiState.currentUser?.id ?: ""

    // Host-side media controls (only relevant when currentUserId is a participant)
    val isParticipant = currentUserId == battle.girl1Id || currentUserId == battle.girl2Id
    var isMuted by remember { mutableStateOf(false) }
    var beautyFilterOn by remember { mutableStateOf(false) }

    var mediaPermissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val cam = result[Manifest.permission.CAMERA] == true
        val mic = result[Manifest.permission.RECORD_AUDIO] == true
        mediaPermissionsGranted = cam && mic
        if (!cam || !mic) {
            Log.e(
                "WebRTC",
                "LiveBattle/PK media permissions denied: camera=$cam microphone=$mic map=$result"
            )
        }
    }

    val localRenderer = remember { SurfaceViewRenderer(context) }
    val remoteRenderer = remember { SurfaceViewRenderer(context) }
    var webRTCManager by remember { mutableStateOf<WebRTCManager?>(null) }

    LaunchedEffect(battle.id) {
        val camOk = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val micOk = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (camOk && micOk) {
            mediaPermissionsGranted = true
        } else {
            Log.w("WebRTC", "LiveBattle: requesting CAMERA + RECORD_AUDIO before WebRTC init (battle=${battle.id})")
            mediaPermissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    LaunchedEffect(mediaPermissionsGranted, battle.id) {
        if (!mediaPermissionsGranted) return@LaunchedEffect
        webRTCManager?.onDestroy()
        webRTCManager = WebRTCManager(
            context = context,
            roomId = battle.id,
            localView = localRenderer,
            remoteView = remoteRenderer,
            broadcastMode = false,
            viewerSignalingId = null
        )
        if (uiState.currentUser?.id == battle.girl1Id) {
            webRTCManager?.startCall()
        } else {
            webRTCManager?.joinCall()
        }
    }

    // Sync host media controls with WebRTC
    LaunchedEffect(isMuted, webRTCManager) {
        webRTCManager?.setAudioEnabled(!isMuted)
    }
    LaunchedEffect(beautyFilterOn, webRTCManager) {
        webRTCManager?.setFilterType(if (beautyFilterOn) FilterType.BEAUTY else FilterType.NONE)
    }

    DisposableEffect(battle.id) {
        onDispose {
            webRTCManager?.onDestroy()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF09090F))) {
        Column(modifier = Modifier.fillMaxSize()) {
            BattleHeader(battle)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        AndroidView(
                            factory = { localRenderer },
                            modifier = Modifier.fillMaxSize()
                        )
                        BattleGirlOverlay(
                            name = battle.girl1Name,
                            score = battle.girl1Score,
                            votes = battle.girl1Votes,
                            isCurrentTurn = battle.currentTurnUserId == battle.girl1Id
                        )
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        AndroidView(
                            factory = { remoteRenderer },
                            modifier = Modifier.fillMaxSize()
                        )
                        BattleGirlOverlay(
                            name = battle.girl2Name,
                            score = battle.girl2Score,
                            votes = battle.girl2Votes,
                            isCurrentTurn = battle.currentTurnUserId == battle.girl2Id
                        )
                    }
                }
                val gs = uiState.battleGameState
                val showAudienceBar = battle.status == BattleStatus.LIVE &&
                    (gs == null || gs.phase.isBlank() || !gs.phase.equals("LOBBY", ignoreCase = true))
                if (showAudienceBar) {
                    BattleAudienceVideoOverlay(
                        gameState = gs,
                        battle = battle,
                        onVote = onVote,
                        onGiftClick = { showGiftSheet = true },
                        isActionInProgress = uiState.isBattleActionInProgress,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .zIndex(Z_BATTLE_VIDEO_OVERLAY)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.42f)
                    .background(Color(0xFF1F1F2E), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = BattleBottomChromeReserveDp)
                ) {
                    when (battle.gameType) {
                        GameType.LIVE_PK -> LivePKView(battle, onVote, onSupport, uiState)
                        GameType.SPIN -> SpinGameView(
                            battle = battle,
                            currentUserId = currentUserId,
                            isBattleActionInProgress = uiState.isBattleActionInProgress,
                            onStartSpin = onStartSpin,
                            onSpinComplete = onSpinComplete
                        )
                        GameType.MYSTERY -> MysteryBoxView(battle, onOpenBox)
                        GameType.KARAOKE -> KaraokeBattleView(battle, onVote)
                        GameType.EMOJI -> EmojiGameView(battle, onEmojiGameTurn, onSolveEmoji, uiState.currentUser?.id ?: "")
                        else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Game view not implemented", color = Color.White)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .zIndex(Z_BATTLE_VIDEO_OVERLAY)
                        .padding(bottom = 16.dp, start = 12.dp, end = 12.dp)
                ) {
                    // Chat input (shown when chat button tapped)
                    if (showChatInput) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.OutlinedTextField(
                                value = chatText,
                                onValueChange = { chatText = it },
                                modifier = Modifier.weight(1f).height(48.dp),
                                placeholder = { Text("Say something…", fontSize = 13.sp) },
                                singleLine = true,
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White.copy(alpha = 0.12f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                                    focusedBorderColor = Color(0xFFFF6B9D),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color(0xFFFF6B9D)
                                ),
                                shape = RoundedCornerShape(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    chatText = ""
                                    showChatInput = false
                                },
                                modifier = Modifier.background(Color(0xFFFF6B9D), CircleShape).size(40.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Gift / support
                        IconButton(
                            onClick = { showGiftSheet = true },
                            modifier = Modifier.background(Color(0xFFFFD700), CircleShape).size(48.dp)
                        ) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = "Tip", tint = Color.Black, modifier = Modifier.size(22.dp))
                        }

                        // Chat toggle
                        IconButton(
                            onClick = { showChatInput = !showChatInput },
                            modifier = Modifier
                                .background(if (showChatInput) Color(0xFFFF6B9D) else Color.White.copy(alpha = 0.1f), CircleShape)
                                .size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Chat", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        // Mute toggle (host-only)
                        if (isParticipant) {
                            IconButton(
                                onClick = {
                                    isMuted = !isMuted
                                },
                                modifier = Modifier
                                    .background(if (isMuted) Color.Red.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.1f), CircleShape)
                                    .size(48.dp)
                            ) {
                                Icon(
                                    if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Camera flip (host-only)
                            IconButton(
                                onClick = { WebRTCManager.activeManager()?.switchCamera() },
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                                    .size(48.dp)
                            ) {
                                Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Flip camera", tint = Color.White, modifier = Modifier.size(20.dp))
                            }

                            // Beauty filter toggle (host-only)
                            IconButton(
                                onClick = { beautyFilterOn = !beautyFilterOn },
                                modifier = Modifier
                                    .background(if (beautyFilterOn) Color(0xFFFF4081).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.1f), CircleShape)
                                    .size(48.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "Beauty filter", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
        
        if (uiState.battleResults != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(Z_BATTLE_RESULT)
            ) {
                BattleResultOverlay(uiState.battleResults, currentUserId, onEndBattle)
            }
        }

        IconButton(
            onClick = { showForfeitDialog = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .zIndex(Z_BATTLE_CLOSE)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
        }
    }

    // Forfeit confirmation — shown when the player taps the close button during a live battle
    if (showForfeitDialog) {
        FuturisticConfirmCloseDialog(
            onDismissRequest = { showForfeitDialog = false },
            onConfirm = {
                showForfeitDialog = false
                onEndBattle()
            },
            textContent = {
                Text(
                    stringResource(R.string.dialog_confirm_close_message) + "\n\n" +
                        stringResource(R.string.live_battle_forfeit_warning),
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
            },
            confirmStyle = FuturisticConfirmCloseStyle.Destructive,
        )
    }

    if (showGiftSheet) {
        GiftSelectionSheet(
            gifts = uiState.availableGifts,
            coinBalance = uiState.coinBalance,
            onTopUpCoins = onTopUpCoins,
            onDismiss = { showGiftSheet = false },
            onSend = { _, _ -> },
            pkGameBattleTeams = listOf(1 to battle.girl1Name, 2 to battle.girl2Name),
            pkGameBattleTeamUserIds = listOf(1 to battle.girl1Id, 2 to battle.girl2Id),
            onSendGiftToPkGameTeam = { team, gift, count ->
                onSupport(team, gift, count)
                showGiftSheet = false
            },
            onLuckySend = onProcessLuckyGifts,
            onLuckyGiftVisual = onLuckyGiftVisual,
        )
    }
}

/** Audience controls on top of the WebRTC strip: live tallies + vote / gift (gift uses [applyBattleGameInput] via [onSupport] in sheet). */
@Composable
private fun BattleAudienceVideoOverlay(
    gameState: GameState?,
    battle: Battle,
    onVote: (Int) -> Unit,
    onGiftClick: () -> Unit,
    isActionInProgress: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(
            stringResource(
                R.string.battle_live_votes,
                battle.girl1Name,
                battle.girl1Votes,
                battle.girl2Name,
                battle.girl2Votes
            ),
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            stringResource(
                R.string.battle_live_scores,
                battle.girl1Name,
                battle.girl1Score,
                battle.girl2Name,
                battle.girl2Score
            ),
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (gameState != null && gameState.version > 0L) {
            Text(
                stringResource(
                    R.string.call_overlay_sync,
                    gameState.version.toInt().coerceAtLeast(0),
                    gameState.phase
                ),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            Button(
                onClick = { onVote(1) },
                enabled = !isActionInProgress,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B9D)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    stringResource(R.string.battle_vote_name, battle.girl1Name.take(12)),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = { onVote(2) },
                enabled = !isActionInProgress,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC44DFF)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    stringResource(R.string.battle_vote_name, battle.girl2Name.take(12)),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onGiftClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(stringResource(R.string.battle_gift_support), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun BattleGirlOverlay(
    name: String,
    score: Int,
    votes: Int?,
    isCurrentTurn: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
        Text(
            name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        if (isCurrentTurn) {
            Surface(color = Color.Yellow, shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Text("MY TURN!", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }

        if (votes != null) {
            Surface(color = Color(0xFFFF6B9D), shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = "$votes VOTES",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Surface(color = Color(0xFFFFD700), shape = RoundedCornerShape(12.dp)) {
            Text(
                text = "$score PTS",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = Color.Black,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun KaraokeBattleView(
    battle: Battle,
    onVote: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Karaoke Duel", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Text("Sing your heart out!", color = Color.White.copy(alpha = 0.6f))
        
        Spacer(Modifier.height(24.dp))
        
        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(64.dp))
        
        Spacer(Modifier.weight(1f))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BattleTeamButton(battle.girl1Name, Color(0xFFFF6B9D), { onVote(1) }, Modifier.weight(1f))
            BattleTeamButton(battle.girl2Name, Color(0xFFC44DFF), { onVote(2) }, Modifier.weight(1f))
        }
    }
}

@Composable
fun LivePKView(battle: Battle, onVote: (Int) -> Unit, onSupport: (Int, Gift, Int) -> Unit, uiState: DatingUiState) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Support Your Team!", color = Color.White, style = MaterialTheme.typography.titleMedium)
        
        if (uiState.selectedTeam == 0) {
            Text(
                text = "Select a team to support with gifts!",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BattleTeamButton(battle.girl1Name, Color(0xFFFF6B9D), { onVote(1) }, Modifier.weight(1f))
            BattleTeamButton(battle.girl2Name, Color(0xFFC44DFF), { onVote(2) }, Modifier.weight(1f))
        }
        
        Spacer(Modifier.height(24.dp))
        
        Text("Quick Support", color = Color.White, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            val quickGifts = uiState.availableGifts.take(4)
            quickGifts.forEach { gift ->
                BattleGiftIcon(gift) { onSupport(uiState.selectedTeam.coerceAtLeast(1), it, 1) }
            }
        }
    }
}

@Composable
fun SpinGameView(
    battle: Battle,
    currentUserId: String,
    isBattleActionInProgress: Boolean,
    onStartSpin: () -> Unit,
    onSpinComplete: () -> Unit
) {
    val hasSpunThisRound = if (currentUserId == battle.girl1Id) battle.girl1HasSpun else battle.girl2HasSpun
    val isMyTurn = battle.currentTurnUserId.isBlank() || battle.currentTurnUserId == currentUserId
    var spinBreakUiTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(battle.spinBreakStartedAtMs) {
        val start = battle.spinBreakStartedAtMs
        if (start <= 0L) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            spinBreakUiTick++
            val left =
                (10 - (System.currentTimeMillis() - start) / 1_000L).toInt().coerceIn(0, 10)
            if (left <= 0) break
        }
    }
    val effectiveBreakLeft =
        remember(battle.spinBreakStartedAtMs, battle.spinBreakTimeLeft, spinBreakUiTick) {
            if (battle.spinBreakStartedAtMs > 0L) {
                (10 - (System.currentTimeMillis() - battle.spinBreakStartedAtMs) / 1_000L)
                    .toInt()
                    .coerceIn(0, 10)
            } else {
                battle.spinBreakTimeLeft
            }
        }
    // Button only lights up when: it is my turn, I haven't spun yet, the wheel is idle,
    // and the between-round break is over.
    val canSpin = isMyTurn && !hasSpunThisRound && !battle.isSpinning && effectiveBreakLeft == 0

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Spin & Win", color = Color.White, style = MaterialTheme.typography.titleLarge)
                when {
                    battle.isSpinning ->
                        Text("Spinning…", color = Color.Yellow, fontWeight = FontWeight.Bold)
                    effectiveBreakLeft > 0 ->
                        Text("Next Round In: ${effectiveBreakLeft}s", color = Color.White.copy(alpha = 0.7f))
                    hasSpunThisRound && !battle.isSpinning ->
                        Text("Waiting for opponent…", color = Color.White.copy(alpha = 0.6f))
                    !isMyTurn ->
                        Text("Opponent's turn", color = Color.White.copy(alpha = 0.6f))
                }
            }

            Button(
                onClick = onStartSpin,
                enabled = canSpin && !isBattleActionInProgress,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canSpin) Color.Yellow else Color.Gray,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.Gray.copy(alpha = 0.4f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f)
                ),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(if (battle.isSpinning) "Spinning…" else "SPIN")
            }
        }

        Spacer(Modifier.height(16.dp))

        LuckyWheel(
            sectors = battle.spinSectors,
            isSpinning = battle.isSpinning,
            targetIndex = battle.lastWinnerSectorIndex ?: 0,
            onSpinComplete = onSpinComplete,
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .aspectRatio(1f)
        )
    }
}

@Composable
fun MysteryBoxView(battle: Battle, onOpenBox: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Mystery Boxes", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Text("Pick a box to win rewards!", color = Color.White.copy(alpha = 0.6f))
        
        Spacer(Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            battle.mysteryBoxes.forEachIndexed { index, box ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp)
                        .background(if (box.isOpened) Color.White.copy(alpha = 0.1f) else Color(0xFFFFD700).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .border(1.dp, if (box.isOpened) Color.Transparent else Color(0xFFFFD700), RoundedCornerShape(12.dp))
                        .clickable(enabled = !box.isOpened) { onOpenBox(index) },
                    contentAlignment = Alignment.Center
                ) {
                    if (box.isOpened) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if(box.rewardType == MysteryRewardType.DIAMONDS) "💎" else "🟡", fontSize = 20.sp)
                            Text("${box.amount}", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color(0xFFFFD700))
                    }
                }
            }
        }
    }
}

@Composable
fun EmojiGameView(battle: Battle, onTurn: (String) -> Unit, onSolve: (String) -> Unit, currentUserId: String) {
    val opponentName = if (currentUserId == battle.girl1Id) battle.girl2Name else battle.girl1Name

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Emoji Challenge", color = Color.White, style = MaterialTheme.typography.titleLarge)
        
        val challenge = battle.currentEmojiChallenge
        if (challenge == null || challenge.emoji.isBlank()) {
            if (battle.currentTurnUserId == currentUserId || battle.currentTurnUserId.isBlank()) {
                Text("Your turn! Pick an emoji to challenge $opponentName", color = Color.Yellow)
                Spacer(Modifier.height(16.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.height(150.dp)) {
                    val emojis = listOf("🍎", "🐶", "⚽", "🚗", "🍕", "🎸", "🍦", "🚀", "🎮", "🏠",
                                        "🌹", "🐱", "🍌", "⭐", "🎀", "🐸", "🦄", "🍩", "🎃", "🌈")
                    items(emojis) { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 32.sp,
                            modifier = Modifier
                                .clickable { onTurn(emoji) }
                                .padding(8.dp)
                        )
                    }
                }
            } else {
                Text("Waiting for $opponentName to pick an emoji…", color = Color.White.copy(alpha = 0.6f))
            }
        } else {
            Text("Guess the Emoji!", color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.height(24.dp))
            Text(challenge.emoji, fontSize = 64.sp)
            Spacer(Modifier.height(32.dp))
            
            if (battle.currentTurnUserId != currentUserId) {
                // This player must guess
                challenge.options.chunked(2).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            Button(
                                onClick = { onSolve(option) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(option, color = Color.White)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            } else {
                Text("$opponentName is guessing…", color = Color.Yellow)
            }
        }
    }
}

@Composable
fun BattleHeader(battle: Battle) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(battle.gameType.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Surface(color = Color.Red, shape = RoundedCornerShape(4.dp)) {
                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Text("LIVE PK", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("${battle.duration}s", color = Color.White, fontSize = 14.sp)
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        Box(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.1f))) {
            val total = battle.girl1Score + battle.girl2Score
            val ratio = if (total == 0) 0.5f else battle.girl1Score.toFloat() / total
            val animatedRatio by animateFloatAsState(targetValue = ratio, animationSpec = spring())
            
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxHeight().weight(animatedRatio.coerceAtLeast(0.01f)).background(Brush.horizontalGradient(listOf(Color(0xFFFF6B9D), Color(0xFFFF1A70)))))
                Box(modifier = Modifier.fillMaxHeight().weight((1f - animatedRatio).coerceAtLeast(0.01f)).background(Brush.horizontalGradient(listOf(Color(0xFF8A2BE2), Color(0xFFC44DFF)))))
            }
            
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${battle.girl1Score}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("${battle.girl2Score}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BattleTeamButton(name: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Default.HowToVote, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("VOTE $name", fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BattleGiftIcon(gift: Gift, onClick: (Gift) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick(gift) }) {
        Box(modifier = Modifier.size(45.dp).background(Color.White.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
            if (gift.thumbnailUrl.isNotBlank()) {
                androidx.compose.foundation.Image(
                    painter = coil.compose.rememberAsyncImagePainter(gift.thumbnailUrl),
                    contentDescription = gift.name,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Icon(
                    imageVector = gift.fallbackImageVector(),
                    contentDescription = gift.name,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Text("${gift.price}", color = Color(0xFFFFD700), fontSize = 10.sp)
    }
}

@Composable
fun BattleResultOverlay(result: BattleResult, currentUserId: String, onDismiss: () -> Unit) {
    val isWinner = result.winnerId == currentUserId
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (isWinner) "VICTORY! 🏆" else "DEFEAT 💔",
                color = if (isWinner) Color.Yellow else Color.Red,
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(16.dp))
            Text("Winner: ${result.winnerName}", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "${result.girl1Score} pts  —  ${result.girl2Score} pts",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onDismiss) {
                Text("Back to Lobby")
            }
        }
    }
}

@Composable
fun LuckyWheel(
    sectors: List<SpinSector>,
    isSpinning: Boolean,
    targetIndex: Int,
    onSpinComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isSpinning) {
        if (isSpinning) {
            val totalRotation = 360f * 5 + (targetIndex * (360f / sectors.size.coerceAtLeast(1)))
            rotation.animateTo(
                targetValue = totalRotation,
                animationSpec = tween(durationMillis = 3000, easing = LinearOutSlowInEasing)
            )
            // Animation finished — notify the ViewModel to write SPIN_STOP and start the break
            onSpinComplete()
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().rotate(rotation.value)) {
            val sectorAngle = 360f / sectors.size.coerceAtLeast(1)
            sectors.forEachIndexed { index, sector ->
                drawArc(
                    color = when(index % 4) {
                        0 -> Color(0xFFFF6B9D)
                        1 -> Color(0xFFC44DFF)
                        2 -> Color(0xFFFFD700)
                        else -> Color(0xFF00E5FF)
                    },
                    startAngle = index * sectorAngle,
                    sweepAngle = sectorAngle,
                    useCenter = true,
                    size = size
                )
                
                val angleRad = (index * sectorAngle + sectorAngle / 2) * (PI / 180f).toFloat()
                val x = center.x + (size.width / 3) * cos(angleRad)
                val y = center.y + (size.width / 3) * sin(angleRad)
                
                drawContext.canvas.nativeCanvas.drawText(
                    sector.emoji,
                    x,
                    y,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 40f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }
        
        Icon(
            Icons.Default.Refresh, 
            contentDescription = null, 
            tint = Color.White, 
            modifier = Modifier.size(32.dp).rotate(180f).align(Alignment.TopCenter).offset(y = (-16).dp)
        )
    }
}
