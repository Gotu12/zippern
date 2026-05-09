package com.zipper.datingapp.webrtc

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

/**
 * Fan-out PK **host** camera + mic to live spectators on
 * `live_publishers/{liveSessionId}/{hostPublisherUid}/`, same layout as [PkGuestSpectatorFanout].
 * Keeps solo viewers on `rooms/{hostUid}` receiving video when the host moves signaling to `pk_room_*`.
 */
private open class HostFanoutSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}

class PkHostSpectatorFanout(
    private val liveSessionId: String,
    private val hostPublisherUid: String,
    private val localVideoTrack: VideoTrack,
    private val localAudioTrack: AudioTrack,
    private val factory: PeerConnectionFactory
) {
    private val tag = "PkHostFanout"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val database: DatabaseReference = FirebaseDatabase.getInstance().reference
        .child("live_publishers")
        .child(liveSessionId.trim())
        .child(hostPublisherUid.trim())

    private data class Session(
        val viewerId: String,
        var peerConnection: PeerConnection?,
        var remoteDescriptionSet: Boolean,
        val pendingIce: MutableList<IceCandidate>,
        var answerListener: ValueEventListener?,
        var iceListener: ChildEventListener?,
        var iceRef: DatabaseReference?
    )

    private val sessions = LinkedHashMap<String, Session>()
    private var activeViewersRef: DatabaseReference? = null
    private var activeViewersListener: ChildEventListener? = null
    @Volatile private var stopped = false

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post { if (!stopped) runCatching { block() } }
    }

    fun start() {
        if (stopped) return
        Log.d(tag, "start live_publishers/$liveSessionId/$hostPublisherUid")
        activeViewersRef?.let { ref -> activeViewersListener?.let { ref.removeEventListener(it) } }
        val ref = database.child("activeViewers")
        activeViewersRef = ref
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val viewerId = snapshot.key?.trim().orEmpty()
                if (viewerId.isEmpty() || viewerId == hostPublisherUid) return
                onMain { spawnForViewer(viewerId) }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val viewerId = snapshot.key?.trim().orEmpty() ?: return
                onMain { tearDown(viewerId) }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e(tag, "activeViewers cancelled", error.toException())
            }
        }
        activeViewersListener = listener
        ref.addChildEventListener(listener)
    }

    fun stop() {
        stopped = true
        activeViewersListener?.let { l -> activeViewersRef?.removeEventListener(l) }
        activeViewersListener = null
        activeViewersRef = null
        synchronized(sessions) { sessions.keys.toList() }.forEach { tearDown(it) }
        Log.d(tag, "stopped live_publishers/$liveSessionId/$hostPublisherUid")
    }

    private fun spawnForViewer(viewerId: String) {
        synchronized(sessions) {
            if (sessions.containsKey(viewerId)) return
        }
        val peerRef = database.child("peers").child(viewerId)
        val session = Session(
            viewerId = viewerId,
            peerConnection = null,
            remoteDescriptionSet = false,
            pendingIce = mutableListOf(),
            answerListener = null,
            iceListener = null,
            iceRef = null
        )
        synchronized(sessions) { sessions[viewerId] = session }

        val rtcConfig = PeerConnection.RTCConfiguration(IceServerCatalog.currentOrStun()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        val pc = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                onMain {
                    runCatching {
                        peerRef.child("streamerCandidates").push().setValue(
                            mapOf(
                                "sdpMid" to candidate.sdpMid,
                                "sdpMLineIndex" to candidate.sdpMLineIndex,
                                "sdp" to candidate.sdp
                            )
                        )
                    }.onFailure { Log.e(tag, "ICE out viewer=$viewerId", it) }
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(tag, "viewer=$viewerId ICE=$state")
            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, p1: Array<out MediaStream>?) {}
        }) ?: run {
            synchronized(sessions) { sessions.remove(viewerId) }
            Log.e(tag, "createPeerConnection null viewer=$viewerId")
            return
        }

        session.peerConnection = pc
        runCatching {
            pc.addTrack(localVideoTrack, listOf("pk_host_main"))
            pc.addTrack(localAudioTrack, listOf("pk_host_main"))
        }.onFailure { Log.e(tag, "addTrack viewer=$viewerId", it) }

        val iceInRef = peerRef.child("viewerCandidates")
        val iceListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, p1: String?) {
                try {
                    val sdp = snapshot.child("sdp").getValue(String::class.java) ?: return
                    val mid = snapshot.child("sdpMid").getValue(String::class.java) ?: return
                    val idx = snapshot.child("sdpMLineIndex").getValue(Int::class.java) ?: return
                    val candidate = IceCandidate(mid, idx, sdp)
                    onMain {
                        val cur = synchronized(sessions) { sessions[viewerId] } ?: return@onMain
                        val conn = cur.peerConnection ?: return@onMain
                        if (cur.remoteDescriptionSet) {
                            conn.addIceCandidate(candidate)
                        } else {
                            cur.pendingIce.add(candidate)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "ICE in malformed viewer=$viewerId", e)
                }
            }

            override fun onChildChanged(p0: DataSnapshot, p1: String?) {}
            override fun onChildRemoved(p0: DataSnapshot) {}
            override fun onChildMoved(p0: DataSnapshot, p1: String?) {}
            override fun onCancelled(p0: DatabaseError) {}
        }
        iceInRef.addChildEventListener(iceListener)
        session.iceListener = iceListener
        session.iceRef = iceInRef

        val ansListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onMain {
                    val cur = synchronized(sessions) { sessions[viewerId] } ?: return@onMain
                    if (!snapshot.exists() || cur.remoteDescriptionSet) return@onMain
                    val desc = snapshot.child("description").getValue(String::class.java) ?: return@onMain
                    val type = snapshot.child("type").getValue(String::class.java) ?: return@onMain
                    val remote = SessionDescription(SessionDescription.Type.fromCanonicalForm(type), desc)
                    val conn = cur.peerConnection ?: return@onMain
                    conn.setRemoteDescription(object : HostFanoutSdpObserver() {
                        override fun onSetSuccess() {
                            onMain {
                                val slot = synchronized(sessions) { sessions[viewerId] } ?: return@onMain
                                slot.remoteDescriptionSet = true
                                slot.pendingIce.forEach { c -> slot.peerConnection?.addIceCandidate(c) }
                                slot.pendingIce.clear()
                            }
                        }

                        override fun onSetFailure(p0: String?) {
                            Log.e(tag, "setRemote ANSWER failed viewer=$viewerId: $p0")
                        }
                    }, remote)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        peerRef.child("answer").addValueEventListener(ansListener)
        session.answerListener = ansListener

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }
        try {
            pc.createOffer(object : HostFanoutSdpObserver() {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    onMain {
                        val s = sdp ?: return@onMain
                        val conn = synchronized(sessions) { sessions[viewerId]?.peerConnection } ?: return@onMain
                        conn.setLocalDescription(object : HostFanoutSdpObserver() {
                            override fun onSetSuccess() {
                                onMain {
                                    runCatching {
                                        peerRef.child("offer").setValue(
                                            mapOf(
                                                "type" to s.type.canonicalForm(),
                                                "description" to s.description
                                            )
                                        )
                                    }.onFailure { Log.e(tag, "offer write viewer=$viewerId", it) }
                                }
                            }

                            override fun onSetFailure(p0: String?) {
                                Log.e(tag, "setLocal OFFER failed viewer=$viewerId: $p0")
                            }
                        }, s)
                    }
                }

                override fun onCreateFailure(p0: String?) {
                    Log.e(tag, "createOffer failed viewer=$viewerId: $p0")
                }
            }, constraints)
        } catch (e: Exception) {
            Log.e(tag, "createOffer invoke viewer=$viewerId", e)
        }
    }

    private fun tearDown(viewerId: String) {
        val session = synchronized(sessions) { sessions.remove(viewerId) } ?: return
        session.answerListener?.let { database.child("peers").child(viewerId).child("answer").removeEventListener(it) }
        session.iceListener?.let { l -> session.iceRef?.removeEventListener(l) }
        runCatching { session.peerConnection?.dispose() }
    }
}
