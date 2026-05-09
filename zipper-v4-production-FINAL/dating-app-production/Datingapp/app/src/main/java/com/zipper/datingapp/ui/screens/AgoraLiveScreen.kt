package com.zipper.datingapp.ui.screens

import android.Manifest
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.zipper.datingapp.agora.AgoraManager
import io.agora.rtc2.Constants
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.delay

enum class AgoraVideoScaleMode {
    Hidden,
    Fit,
    ;

    fun toAgoraRenderMode(): Int =
        when (this) {
            Hidden -> VideoCanvas.RENDER_MODE_HIDDEN
            Fit -> VideoCanvas.RENDER_MODE_FIT
        }
}

/**
 * Solo live video via Agora ([TextureView] inside [AndroidView]). Requests camera/mic when needed.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AgoraLiveScreen(
    channelId: String,
    /** Signed-in Firebase uid used to derive Agora integer uid. */
    firebaseUid: String,
    isBroadcaster: Boolean,
    token: String?,
    localVideoScale: AgoraVideoScaleMode,
    remoteVideoScale: AgoraVideoScaleMode,
    modifier: Modifier = Modifier,
    /** Fires once when the channel join handshake succeeds (for timers / analytics parity). */
    onJoinedChannel: () -> Unit = {},
    /** Broadcaster: mirrors WebRTC host timing (~1.2s after session starts). */
    onHostStreamReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val manager = remember { AgoraManager(context) }
    val latestReady by rememberUpdatedState(onHostStreamReady)
    val latestJoined by rememberUpdatedState(onJoinedChannel)

    val mediaPermissions =
        rememberMultiplePermissionsState(
            listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
        )
    var launchedPermissionPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!launchedPermissionPrompt) {
            launchedPermissionPrompt = true
            if (!mediaPermissions.allPermissionsGranted) {
                mediaPermissions.launchMultiplePermissionRequest()
            }
        }
    }

    DisposableEffect(manager) {
        onDispose { manager.destroy() }
    }

    val mediaReady = mediaPermissions.allPermissionsGranted
    val uid = remember(firebaseUid) { manager.agoraUidFromFirebaseUid(firebaseUid.trim()) }

    val joinOk by manager.joinChannelSuccess.collectAsStateWithLifecycle(initialValue = false)
    val remoteUid by manager.remoteUid.collectAsStateWithLifecycle(initialValue = null)

    val videoTexture =
        remember {
            TextureView(context.applicationContext).apply {
                isOpaque = false
            }
        }

    var joinedChannel by remember(channelId, firebaseUid, isBroadcaster) { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { videoTexture },
            modifier = Modifier.fillMaxSize(),
        )

        LaunchedEffect(mediaReady, channelId, uid, isBroadcaster, token, joinedChannel) {
            if (!mediaReady || channelId.isBlank() || firebaseUid.isBlank() || joinedChannel) {
                return@LaunchedEffect
            }
            if (isBroadcaster) {
                manager.bindLocalVideo(
                    videoTexture,
                    localVideoScale.toAgoraRenderMode(),
                    Constants.VIDEO_MIRROR_MODE_ENABLED,
                )
                val code =
                    manager.joinAsBroadcaster(
                        channelId = channelId.trim(),
                        uid = uid,
                        token = token,
                        startPreviewBeforeJoin = true,
                    )
                if (code != 0) {
                    manager.startPreviewIfNeeded()
                }
            } else {
                manager.joinAsAudience(
                    channelId = channelId.trim(),
                    uid = uid,
                    token = token,
                )
            }
            joinedChannel = true
        }

        LaunchedEffect(remoteUid, mediaReady, channelId, isBroadcaster, joinedChannel) {
            if (isBroadcaster || !mediaReady || channelId.isBlank() || !joinedChannel) {
                return@LaunchedEffect
            }
            val ru = remoteUid ?: return@LaunchedEffect
            manager.bindRemoteVideo(
                videoTexture,
                ru,
                remoteVideoScale.toAgoraRenderMode(),
                Constants.VIDEO_MIRROR_MODE_DISABLED,
            )
        }

        LaunchedEffect(localVideoScale, remoteVideoScale, isBroadcaster, remoteUid, mediaReady, joinedChannel) {
            if (!mediaReady || !joinedChannel) return@LaunchedEffect
            if (isBroadcaster) {
                manager.bindLocalVideo(
                    videoTexture,
                    localVideoScale.toAgoraRenderMode(),
                    Constants.VIDEO_MIRROR_MODE_ENABLED,
                )
            } else {
                val ru = remoteUid ?: return@LaunchedEffect
                manager.bindRemoteVideo(
                    videoTexture,
                    ru,
                    remoteVideoScale.toAgoraRenderMode(),
                    Constants.VIDEO_MIRROR_MODE_DISABLED,
                )
            }
        }

        LaunchedEffect(joinOk) {
            if (!joinOk) return@LaunchedEffect
            latestJoined()
        }

        LaunchedEffect(joinOk, isBroadcaster) {
            if (!joinOk || !isBroadcaster) return@LaunchedEffect
            delay(1200)
            latestReady()
        }

        if (!mediaReady) {
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                TextButton(
                    onClick = { mediaPermissions.launchMultiplePermissionRequest() },
                    modifier = Modifier.padding(8.dp),
                ) {
                    Text(
                        text = "Camera and microphone are required for live video.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}
