package com.zipper.datingapp.webrtc

import com.google.firebase.database.*
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class SignalingClient(private val roomId: String, private val listener: SignalingInterface) {

    private val database = FirebaseDatabase.getInstance().reference.child("rooms").child(roomId)

    fun sendOffer(sdp: SessionDescription) {
        database.child("offer").setValue(mapOf(
            "type" to sdp.type.canonicalForm(),
            "description" to sdp.description
        ))
    }

    fun sendAnswer(sdp: SessionDescription) {
        database.child("answer").setValue(mapOf(
            "type" to sdp.type.canonicalForm(),
            "description" to sdp.description
        ))
    }

    fun sendIceCandidate(candidate: IceCandidate, isStreamer: Boolean) {
        val path = if (isStreamer) "streamerCandidates" else "viewerCandidates"
        database.child(path).push().setValue(mapOf(
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "sdp" to candidate.sdp
        ))
    }

    fun listen(isStreamer: Boolean) {
        // Listen for Offer (if viewer)
        if (!isStreamer) {
            database.child("offer").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val type = snapshot.child("type").getValue(String::class.java)
                        val description = snapshot.child("description").getValue(String::class.java)
                        if (type != null && description != null) {
                            listener.onOfferReceived(SessionDescription(
                                SessionDescription.Type.fromCanonicalForm(type),
                                description
                            ))
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }

        // Listen for Answer (if streamer)
        if (isStreamer) {
            database.child("answer").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val type = snapshot.child("type").getValue(String::class.java)
                        val description = snapshot.child("description").getValue(String::class.java)
                        if (type != null && description != null) {
                            listener.onAnswerReceived(SessionDescription(
                                SessionDescription.Type.fromCanonicalForm(type),
                                description
                            ))
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }

        // Listen for ICE Candidates
        val remotePath = if (isStreamer) "viewerCandidates" else "streamerCandidates"
        database.child(remotePath).addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val sdpMid = snapshot.child("sdpMid").getValue(String::class.java)
                val sdpMLineIndex = snapshot.child("sdpMLineIndex").getValue(Int::class.java)
                val sdp = snapshot.child("sdp").getValue(String::class.java)
                if (sdpMid != null && sdpMLineIndex != null && sdp != null) {
                    listener.onIceCandidateReceived(IceCandidate(sdpMid, sdpMLineIndex, sdp))
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun cleanUp() {
        database.removeValue()
    }

    interface SignalingInterface {
        fun onOfferReceived(description: SessionDescription)
        fun onAnswerReceived(description: SessionDescription)
        fun onIceCandidateReceived(candidate: IceCandidate)
    }
}
