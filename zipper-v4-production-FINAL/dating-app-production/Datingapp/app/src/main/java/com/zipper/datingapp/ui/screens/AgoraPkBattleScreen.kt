package com.zipper.datingapp.ui.screens

import android.Manifest
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.zipper.datingapp.agora.AgoraManager
import io.agora.rtc2.Constants
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.delay

/**
 * PK battler (host in MainActivity or guest): one Agora [channelName] (typically `pkRoomId` / RTDB room id),
 * local preview on [leftTile] / remote opponent on [rightTile] ([TextureView]s).
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AgoraPkBattlerSplit(
    channelName: String,
    myFirebaseUid: String,
    opponentFirebaseUid: String,
    onGetAgoraToken: suspend (String, Boolean) -> String?,
    onAgoraEngineChange: (AgoraManager?) -> Unit,
    onJoinedChannel: () -> Unit = {},
    onHostStreamReady: () -> Unit = {},
    hostMicUnmuted: Boolean = true,
    flipCameraNonce: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val manager = remember { AgoraManager(context) }
    val myUid = remember(myFirebaseUid) { manager.agoraUidFromFirebaseUid(myFirebaseUid.trim()) }
    val opponentAgoraUid =
        remember(opponentFirebaseUid) {
            if (opponentFirebaseUid.isBlank()) -1 else manager.agoraUidFromFirebaseUid(opponentFirebaseUid.trim())
        }

    val leftTexture =
        remember {
            TextureView(context.applicationContext).apply { isOpaque = false }
        }
    val rightTexture =
        remember {
            TextureView(context.applicationContext).apply { isOpaque = false }
        }

    val perms =
        rememberMultiplePermissionsState(listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    var prompted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!prompted) {
            prompted = true
            if (!perms.allPermissionsGranted) {
                perms.launchMultiplePermissionRequest()
            }
        }
    }

    DisposableEffect(manager) {
        onAgoraEngineChange(manager)
        onDispose {
            onAgoraEngineChange(null)
            manager.destroy()
        }
    }

    var joinedChannel by remember(channelName, myFirebaseUid) { mutableStateOf(false) }
    val joinOk by manager.joinChannelSuccess.collectAsStateWithLifecycle(initialValue = false)

    LaunchedEffect(perms.allPermissionsGranted, channelName, myUid, joinedChannel) {
        if (!perms.allPermissionsGranted || channelName.isBlank() || joinedChannel) {
            return@LaunchedEffect
        }
        manager.bindLocalVideo(
            leftTexture,
            VideoCanvas.RENDER_MODE_HIDDEN,
            Constants.VIDEO_MIRROR_MODE_ENABLED,
        )
        val token =
            try {
                onGetAgoraToken(channelName.trim(), true)
            } catch (_: Exception) {
                null
            }
        val code =
            manager.joinAsCoHostBroadcaster(
                channelId = channelName.trim(),
                uid = myUid,
                token = token,
                startPreviewBeforeJoin = true,
            )
        if (code != 0) {
            manager.startPreviewIfNeeded()
        }
        joinedChannel = true
    }

    LaunchedEffect(joinOk) {
        if (joinOk) {
            onJoinedChannel()
        }
    }

    LaunchedEffect(joinOk) {
        if (!joinOk) return@LaunchedEffect
        delay(1200)
        onHostStreamReady()
    }

    LaunchedEffect(hostMicUnmuted) {
        manager.muteLocalAudioStream(!hostMicUnmuted)
    }

    LaunchedEffect(flipCameraNonce) {
        if (flipCameraNonce > 0) {
            manager.switchCamera()
        }
    }

    LaunchedEffect(opponentAgoraUid, joinOk) {
        if (opponentAgoraUid <= 0 || !joinOk) return@LaunchedEffect
        manager.bindRemoteVideo(
            rightTexture,
            opponentAgoraUid,
            VideoCanvas.RENDER_MODE_HIDDEN,
            Constants.VIDEO_MIRROR_MODE_DISABLED,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { leftTexture },
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
            )
            AndroidView(
                factory = { rightTexture },
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
            )
        }
        if (!perms.allPermissionsGranted) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * PK spectator: joins [channelName] as audience; binds host vs guest RemoteVideo by known Agora uids.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AgoraPkSpectatorSplit(
    channelName: String,
    myFirebaseUid: String,
    hostFirebaseUid: String,
    guestFirebaseUid: String,
    hostOnLeft: Boolean,
    onGetAgoraToken: suspend (String, Boolean) -> String?,
    hearsRemoteAudio: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val manager = remember { AgoraManager(context) }
    val myUid = remember(myFirebaseUid) { manager.agoraUidFromFirebaseUid(myFirebaseUid.trim()) }
    val hostA = remember(hostFirebaseUid) { manager.agoraUidFromFirebaseUid(hostFirebaseUid.trim()) }
    val guestA = remember(guestFirebaseUid) { manager.agoraUidFromFirebaseUid(guestFirebaseUid.trim()) }
    val leftUid = if (hostOnLeft) hostA else guestA
    val rightUid = if (hostOnLeft) guestA else hostA

    val leftTex =
        remember {
            TextureView(context.applicationContext).apply { isOpaque = false }
        }
    val rightTex =
        remember {
            TextureView(context.applicationContext).apply { isOpaque = false }
        }

    DisposableEffect(manager) {
        onDispose { manager.destroy() }
    }

    val perms =
        rememberMultiplePermissionsState(listOf(Manifest.permission.RECORD_AUDIO))
    var prompted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!prompted) {
            prompted = true
            if (!perms.allPermissionsGranted) {
                perms.launchMultiplePermissionRequest()
            }
        }
    }

    var joinedChannel by remember(channelName, myFirebaseUid) { mutableStateOf(false) }
    val remoteUids by manager.remoteUids.collectAsStateWithLifecycle(initialValue = emptySet())

    LaunchedEffect(perms.allPermissionsGranted, channelName, myUid, joinedChannel) {
        if (!perms.allPermissionsGranted || channelName.isBlank() || joinedChannel) {
            return@LaunchedEffect
        }
        val token =
            try {
                onGetAgoraToken(channelName.trim(), false)
            } catch (_: Exception) {
                null
            }
        manager.joinAsAudience(
            channelId = channelName.trim(),
            uid = myUid,
            token = token,
        )
        joinedChannel = true
    }

    LaunchedEffect(hearsRemoteAudio) {
        manager.muteAllRemoteAudioStreams(!hearsRemoteAudio)
    }

    LaunchedEffect(remoteUids, leftUid, rightUid, joinedChannel) {
        if (!joinedChannel) return@LaunchedEffect
        if (leftUid in remoteUids) {
            manager.bindRemoteVideo(
                leftTex,
                leftUid,
                VideoCanvas.RENDER_MODE_HIDDEN,
                Constants.VIDEO_MIRROR_MODE_DISABLED,
            )
        }
        if (rightUid in remoteUids) {
            manager.bindRemoteVideo(
                rightTex,
                rightUid,
                VideoCanvas.RENDER_MODE_HIDDEN,
                Constants.VIDEO_MIRROR_MODE_DISABLED,
            )
        }
    }

    Row(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { leftTex },
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        )
        AndroidView(
            factory = { rightTex },
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        )
    }
}
