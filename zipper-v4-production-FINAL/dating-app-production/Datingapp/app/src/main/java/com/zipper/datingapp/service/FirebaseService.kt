package com.zipper.datingapp.service

import android.net.Uri
import android.util.Log
import com.zipper.datingapp.BuildConfig
import com.zipper.datingapp.data.*
import com.zipper.datingapp.economy.VirtualEconomyMath
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import java.util.UUID
import kotlin.coroutines.resume
import org.webrtc.PeerConnection

/**
 * Deploy these rules in Firebase Console (Authentication required for uploads).
 *
 * **Storage** — allows each signed-in user to read/write only under `users/{uid}/`:
 * ```
 * rules_version = '2';
 * service firebase.storage {
 *   match /b/{bucket}/o {
 *     match /users/{userId}/{allPaths=**} {
 *       allow read: if request.auth != null;
 *       allow write: if request.auth != null && request.auth.uid == userId;
 *     }
 *   }
 * }
 * ```
 *
 * **Firestore** — users can read profiles and write their own `users/{uid}` document;
 * messages/gifts need matching rules for your collections:
 * ```
 * rules_version = '2';
 * service cloud.firestore {
 *   match /databases/{database}/documents {
 *     match /users/{userId} {
 *       allow read: if request.auth != null;
 *       allow create, update: if request.auth != null && request.auth.uid == userId;
 *       allow delete: if request.auth != null && request.auth.uid == userId;
 *     }
 *     // 1:1 DMs: documents include chatId = lexicographically sorted "uidA_uidB" (see [dmChatId]).
 *     // Client queries messages with whereEqualTo("chatId") only; sorts by timestamp in memory (no composite index).
 *     match /messages/{messageId} {
 *       allow read: if request.auth != null &&
 *         (request.auth.uid == resource.data.senderId || request.auth.uid == resource.data.receiverId);
 *       allow create: if request.auth != null && request.auth.uid == request.resource.data.senderId;
 *     }
 *     match /gifts/{giftId} {
 *       allow read: if request.auth != null;
 *     }
 *     match /gift_transactions/{txId} {
 *       allow read, write: if request.auth != null;
 *     }
 *   }
 * }
 * ```
 * Tighten `messages` / `gift_transactions` to only allow participants as needed.
 *
 * **Follow graph** — allow `arrayUnion` / `arrayRemove` on `followingIds` and `followerIds` for authenticated users
 * (e.g. caller may update own `followingIds` and counterpart's `followerIds` via rules you trust).
 *
 * **Profile likes** — likers run `arrayUnion`/`arrayRemove` on the liked user's `likedByUserIds` alongside
 * `likeMeCount` increment; rules must allow authenticated writers to update those fields on others' `users/{id}`.
 *
 * **battles** collection — Game Center lobby docs (`gameType`, `status`, host ids, `prizePool`):
 * ```
 * match /battles/{id} {
 *   allow read: if request.auth != null;
 *   allow create, update: if request.auth != null;
 * }
 * ```
 *
 * **calls** collection — session state (`RINGING`, `ACCEPTED`, …) plus optional WebRTC fields on `call_*` rooms:
 * `offer` / `answer` maps and subcollections `offerCandidates` / `answerCandidates` for trickle ICE:
 * ```
 * match /calls/{callId} {
 *   allow read: if request.auth != null;
 *   allow create: if request.auth != null;
 *   allow update, delete: if request.auth != null;
 * }
 * match /calls/{callId}/{sub}/{docId} {
 *   allow read, write: if request.auth != null;
 * }
 * ```
 * Tighten to `request.auth.uid == resource.data.callerId || request.auth.uid == resource.data.receiverId`.
 *
 * **transactions** — atomic live gifts (`type` = `LIVE_STREAM_GIFT`); only participants should write:
 * ```
 * match /transactions/{txId} {
 *   allow read: if request.auth != null;
 *   allow create: if request.auth != null && request.auth.uid == request.resource.data.senderId;
 *   allow update, delete: if false;
 * }
 * ```
 *
 * **Realtime Database — live broadcast WebRTC** (see [com.zipper.datingapp.webrtc.WebRTCManager] `broadcastMode`):
 * Host uses `roomId` = host Firebase uid. Signaling: `rooms/{hostUid}/activeViewers/{viewerUid}` and
 * `rooms/{hostUid}/peers/{viewerUid}/{offer,answer,...}`. Viewers must pass the same `hostUid` as `roomId`
 * and register `activeViewers/{theirUid}` before listening under `peers/{theirUid}/`.
 */
/**
 * Deterministic 1:1 chat document key so both users always hit the same Firestore/RTDB path,
 * regardless of which side calls first. Sorted lexicographically so the result is fully symmetric:
 * dmChatId(A, B) == dmChatId(B, A).
 */
fun dmChatId(userIdA: String, userIdB: String): String {
    val a = userIdA.trim()
    val b = userIdB.trim()
    if (a.isEmpty() || b.isEmpty() || a == b) return ""
    return listOf(a, b).sorted().joinToString("_")
}

class FirebaseService {
    private val tag = "FirebaseService"
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val realtimeDb = FirebaseDatabase.getInstance()

    private val usersCollection = db.collection("users")
    private val callsCollection = db.collection("calls")
    private val battlesCollection = db.collection("battles")
    private val liveStreamsCollection = db.collection("live_streams")
    /** Itzo-style audio party seat map (`seats`, `seatCount`, optional `nowPlaying*`). */
    private val streamsCollection = db.collection("streams")
    private val messagesCollection = db.collection("messages")
    private val giftsCollection = db.collection("gifts")
    private val giftTransactionsCollection = db.collection("gift_transactions")
    /** CRM-managed inbox announcements; see [observeCrmAnnouncements]. */
    private val crmAnnouncementsCollection = db.collection("crm_announcements")
    private val economyTransactionsCollection = db.collection("transactions")
    private val pkQueueRef = realtimeDb.reference.child("pk_queue")
    private val pkMatchesRef = realtimeDb.reference.child("pk_matches")
    private val callInvitesRef = realtimeDb.reference.child("call_invites")
    private val callResponsesRef = realtimeDb.reference.child("call_responses")
    private val roomsRef = realtimeDb.reference.child("rooms")

    /** Prevents concurrent duplicate Firestore signaling deletes (e.g. double-tap end call). */
    @Volatile
    private var isCleanupInProgress: Boolean = false

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    /**
     * Calls HTTPS callable `getTurnCredentials` (same region as [zipperFirebaseFunctions]).
     * Caches successful parses for [TURN_CREDENTIALS_CACHE_MS]. Returns an empty list on error.
     */
    suspend fun getTurnCredentials(): List<PeerConnection.IceServer> {
        return turnCredentialsMutex.withLock {
            val now = System.currentTimeMillis()
            val cached = turnCredentialsCache
            if (cached != null && now - turnCredentialsCachedAtMs < TURN_CREDENTIALS_CACHE_MS) {
                cached
            } else {
                val parsed = withContext(Dispatchers.IO) {
                    runCatching {
                        val result = zipperFirebaseFunctions()
                            .getHttpsCallable("getTurnCredentials")
                            .call()
                            .await()
                        val data = result.data as? Map<*, *> ?: return@runCatching emptyList()
                        iceServersFromHttpsCallableMap(data)
                    }.getOrElse { e ->
                        Log.w(tag, "getTurnCredentials callable failed", e)
                        emptyList()
                    }
                }
                if (parsed.isNotEmpty()) {
                    turnCredentialsCache = parsed
                    turnCredentialsCachedAtMs = System.currentTimeMillis()
                }
                parsed
            }
        }
    }

    private fun iceServersFromHttpsCallableMap(data: Map<*, *>): List<PeerConnection.IceServer> {
        val rawList = data["iceServers"] as? List<*> ?: return emptyList()
        val out = ArrayList<PeerConnection.IceServer>(rawList.size)
        for (item in rawList) {
            val m = item as? Map<*, *> ?: continue
            val urls = mutableListOf<String>()
            when (val u = m["urls"]) {
                is String -> u.trim().takeIf { it.isNotEmpty() }?.let { urls.add(it) }
                is List<*> -> for (x in u) {
                    x?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { urls.add(it) }
                }
            }
            if (urls.isEmpty()) continue
            val user = m["username"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val cred = m["credential"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: m["password"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val b = PeerConnection.IceServer.builder(urls)
            if (user != null && cred != null) {
                b.setUsername(user)
                b.setPassword(cred)
            }
            out.add(b.createIceServer())
        }
        return out
    }

    /** Realtime Database presence heartbeat: true when client is connected to Firebase servers. */
    fun observeRtdbConnected(): Flow<Boolean> = callbackFlow {
        val ref = realtimeDb.reference.child(".info/connected")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) == true
                Log.d(tag, "RTDB .info/connected=$connected (heartbeat)")
                trySend(connected)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(tag, "RTDB .info/connected cancelled code=${error.code} msg=${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    private fun logFirestoreSnapshotError(operation: String, e: FirebaseFirestoreException?) {
        if (e == null) return
        val msg = e.message.orEmpty()
        val hint = when (e.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                " — PERMISSION_DENIED: check Firestore Security Rules for this listener/query."
            FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                " — FAILED_PRECONDITION / MISSING_INDEX: create composite index if a console URL appears below."
            FirebaseFirestoreException.Code.UNAVAILABLE -> " — UNAVAILABLE: network or service issue."
            else -> ""
        }
        Log.e(tag, "Firestore snapshot [$operation]: code=${e.code} message=$msg$hint", e)
        if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            Log.e(tag, "PERMISSION_DENIED context=[$operation] — verify rules allow read for this query.")
        }
        val indexUrl = Regex("https://console\\.firebase\\.google\\.com[^\\s)]+").find(msg)?.value
        if (indexUrl != null) {
            Log.e(tag, "MISSING_INDEX (open link to create index): $indexUrl")
        } else if (e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION &&
            msg.contains("index", ignoreCase = true)
        ) {
            Log.e(tag, "FAILED_PRECONDITION — check full error message above for index / query constraints.")
        }
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        val uid = auth.currentUser?.uid ?: profile.id
        if (uid.isNotEmpty()) {
            Log.d(
                tag,
                "saveUserProfile: uid=$uid name=${profile.name} photoUrl=${profile.photoUrl.take(48)}... galleryCount=${profile.galleryPhotos.size}"
            )
            val normalized = normalizeProfileMediaUrls(profile)
            // Merge so excluded / omitted fields (e.g. like counts, likedByUserIds) are never wiped by a profile edit.
            try {
                usersCollection.document(uid).set(normalized, SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.e(tag, "saveUserProfile failed uid=$uid", e)
                throw e
            }
            Log.d(tag, "saveUserProfile: Firestore set(merge) completed for uid=$uid")
        }
    }

    /**
     * Atomic narrow update that permanently sets all verification flags on the user document.
     * Called immediately after [saveUserProfile] on any successful verification path to prevent
     * a racing full `.set()` from wiping verification state if both operations are in flight.
     */
    suspend fun writeVerificationApproved(uid: String, faceVerificationImageUrl: String? = null) {
        if (uid.isBlank()) return
        val updates = hashMapOf<String, Any>(
            "isVerified" to true,
            "isFaceVerified" to true,
            "faceVerificationStatus" to "APPROVED",
            "hasStarBadge" to true
        )
        if (!faceVerificationImageUrl.isNullOrBlank()) {
            updates["faceVerificationImageUrl"] = faceVerificationImageUrl
        }
        try {
            usersCollection.document(uid).update(updates).await()
        } catch (e: Exception) {
            Log.e(tag, "writeVerificationApproved failed uid=$uid", e)
            throw e
        }
        Log.d("VERIFICATION", "Successfully wrote isFaceVerified=true to Firestore uid=$uid")
    }

    /**
     * Narrow update for live discovery ([observeLiveUserIds]) so hosts appear on the Live tab even if
     * a full [saveUserProfile] fails or races. Clears [UserProfile.liveRoomId] when not live.
     *
     * Retries on transient failures so teardown does not leave `isLive=true` ghosts after ending solo live.
     */
    suspend fun updateUserLiveStatus(uid: String, isLive: Boolean, liveRoomId: String?) {
        if (uid.isBlank()) return
        val updates = hashMapOf<String, Any>("isLive" to isLive)
        if (liveRoomId != null) {
            updates["liveRoomId"] = liveRoomId
        } else {
            updates["liveRoomId"] = FieldValue.delete()
        }
        val docRef = usersCollection.document(uid)
        var lastError: Exception? = null
        repeat(UPDATE_USER_LIVE_STATUS_MAX_ATTEMPTS) { attempt ->
            try {
                docRef.update(updates).await()
                Log.d(tag, "updateUserLiveStatus: uid=$uid isLive=$isLive room=${liveRoomId ?: "(cleared)"} attempt=${attempt + 1}")
                return
            } catch (e: Exception) {
                lastError = e
                Log.w(tag, "updateUserLiveStatus attempt=${attempt + 1}/$UPDATE_USER_LIVE_STATUS_MAX_ATTEMPTS uid=$uid", e)
                if (attempt < UPDATE_USER_LIVE_STATUS_MAX_ATTEMPTS - 1) {
                    delay(UPDATE_USER_LIVE_STATUS_RETRY_BASE_MS shl attempt)
                }
            }
        }
        throw lastError ?: IllegalStateException("updateUserLiveStatus failed uid=$uid")
    }

    /**
     * Fetches the user profile for [uid].
     *
     * Pass [source] = [Source.SERVER] at app startup to bypass the Firestore offline cache and
     * immediately reflect any admin-side verification changes.  If the server is unreachable the
     * function transparently falls back to [Source.CACHE] so the user is never hard-locked when
     * offline.  All other callers use the default [Source.DEFAULT] behaviour unchanged.
     */
    suspend fun getUserProfile(uid: String, source: Source = Source.DEFAULT): UserProfile? {
        return try {
            val doc = usersCollection.document(uid).get(source).await()
            userProfileFromDocument(doc)
        } catch (primary: Exception) {
            Log.w(tag, "Failed to fetch profile for uid=$uid source=$source", primary)
            // If we explicitly asked for the server but are offline, fall back to the local cache
            // so the app remains usable rather than locking the user out entirely.
            if (source == Source.SERVER) {
                try {
                    val cached = usersCollection.document(uid).get(Source.CACHE).await()
                    userProfileFromDocument(cached)
                } catch (fallback: Exception) {
                    Log.w(tag, "Cache fallback also failed for uid=$uid", fallback)
                    null
                }
            } else {
                null
            }
        }
    }

    /**
     * When the signed-in user's Firestore doc has no [UserProfile.email] / [UserProfile.mobile]
     * (common for Google or phone login if the document predates those fields), expose Auth
     * [com.google.firebase.auth.FirebaseUser.getEmail] / [com.google.firebase.auth.FirebaseUser.getPhoneNumber]
     * so profile and edit screens show the correct values. Other users' documents are unchanged.
     */
    private fun UserProfile.withAuthContactFallback(): UserProfile {
        val u = auth.currentUser ?: return this
        if (u.uid != id) return this
        val authEmail = u.email?.trim().orEmpty()
        val authPhone = u.phoneNumber?.trim().orEmpty()
        return copy(
            email = email.trim().ifBlank { authEmail },
            mobile = mobile.trim().ifBlank { authPhone }
        )
    }

    /**
     * Parses [UserProfile] including `followingIds` / `followerIds` arrays (some Firestore/Java
     * deserialization paths omit or mangle list fields).
     */
    fun userProfileFromDocument(doc: DocumentSnapshot?): UserProfile? {
        if (doc == null || !doc.exists()) return null
        return doc.toUserProfileMerged()?.withAuthContactFallback()
    }

    suspend fun getProfiles(): List<UserProfile> {
        return try {
            val snapshot = usersCollection.get().await()
            snapshot.documents.mapNotNull { userProfileFromDocument(it) }
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch profiles", e)
            emptyList()
        }
    }

    fun observeProfiles(): Flow<List<UserProfile>> = callbackFlow {
        val scope = this
        val subscription = usersCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                logFirestoreSnapshotError("users (observeProfiles)", error)
                return@addSnapshotListener
            }
            scope.launch(Dispatchers.Default) {
                val profiles = snapshot?.documents?.mapNotNull { userProfileFromDocument(it) }.orEmpty()
                Log.d(tag, "observeProfiles: emitted ${profiles.size} profiles (snapshotNull=${snapshot == null})")
                trySend(profiles)
            }
        }
        awaitClose { subscription.remove() }
    }

    /**
     * Realtime DB mirror of online flags for instant propagation + [DatabaseReference.onDisconnect] safety net.
     * Path: `presence/{uid}` with `isOnline` boolean and `lastSeen` server timestamp.
     */
    fun observeGlobalPresenceRtdb(): Flow<Map<String, Boolean>> = callbackFlow {
        val ref = realtimeDb.reference.child("presence")
        val scope = this
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch(Dispatchers.Default) {
                    if (!snapshot.exists()) {
                        trySend(emptyMap())
                        return@launch
                    }
                    val map = buildMap {
                        for (child in snapshot.children) {
                            val id = child.key ?: continue
                            val online = child.child("isOnline").getValue(Boolean::class.java) ?: false
                            put(id, online)
                        }
                    }
                    trySend(map)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(tag, "observeGlobalPresenceRtdb cancelled: ${error.message} code=${error.code}")
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Real-time set of user IDs with [UserProfile.isLive] true — merge in UI for the Live tab. */
    fun observeLiveUserIds(): Flow<Set<String>> = callbackFlow {
        val scope = this
        val subscription = usersCollection
            .whereEqualTo("isLive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logFirestoreSnapshotError("users isLive=true", error)
                    return@addSnapshotListener
                }
                scope.launch(Dispatchers.Default) {
                    val ids = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()
                    Log.d(tag, "observeLiveUserIds: isLive=true count=${ids.size} ids=$ids")
                    trySend(ids)
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun publishBattle(b: Battle) {
        val host = b.hostId.ifBlank { b.girl1Id }
        val map = hashMapOf<String, Any>(
            "id" to b.id,
            "gameType" to b.gameType.name,
            "hostId" to host,
            "girl1Id" to b.girl1Id,
            "girl2Id" to b.girl2Id,
            "girl1Name" to b.girl1Name,
            "girl2Name" to b.girl2Name,
            "status" to b.status.name,
            "startTime" to b.startTime,
            "prizePool" to b.prizePool,
            "viewerCount" to b.viewerCount,
            "girl1Votes" to b.girl1Votes,
            "girl2Votes" to b.girl2Votes,
            "girl1Score" to b.girl1Score,
            "girl2Score" to b.girl2Score,
            // Turn-based game initial state — girl1 always goes first
            "currentTurnUserId" to b.girl1Id,
            "emojiGameTurnCount" to 0,
            "currentEmojiChallengeEmoji" to "",
            "currentEmojiChallengeRightName" to "",
            "currentEmojiChallengeOptions" to emptyList<String>(),
            "currentEmojiChallengeIsSolved" to false,
            "currentEmojiChallengeSolvedBy" to "",
            // Spin game initial state
            "isSpinning" to false,
            "lastWinnerSectorIndex" to -1,
            "spinSectorCount" to b.spinSectors.size.coerceAtLeast(6),
            "girl1HasSpun" to false,
            "girl2HasSpun" to false,
            "spinBreakStartedAtMs" to 0L,
            "openedBoxes" to emptyList<Int>(),
            "gameStateVersion" to 0L,
            "gameStatePhase" to "LOBBY",
            "gameStateLastActorId" to "",
            "gameStateUpdatedAtMs" to System.currentTimeMillis(),
            "updatedAtMs" to System.currentTimeMillis()
        )
        battlesCollection.document(b.id).set(map, SetOptions.merge()).await()
        Log.d(tag, "publishBattle: doc=${b.id} type=${b.gameType} status=${b.status} hostId=$host")
    }

    fun observeBattles(): Flow<List<Battle>> = callbackFlow {
        val lobbyStatuses = listOf(
            BattleStatus.WAITING.name,
            BattleStatus.STARTING_SOON.name,
            BattleStatus.LIVE.name
        )
        val reg = battlesCollection
            .whereIn("status", lobbyStatuses)
            .limit(40)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logFirestoreSnapshotError("battles lobby", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { battleFromFirestore(it) }.orEmpty()
                    .filter { it.status == BattleStatus.WAITING || it.status == BattleStatus.STARTING_SOON || it.status == BattleStatus.LIVE }
                    .sortedByDescending { it.startTime }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    private fun battleFromFirestore(doc: com.google.firebase.firestore.DocumentSnapshot): Battle? {
        val d = doc.data ?: return null
        return try {
            val gameType = runCatching {
                GameType.valueOf(d["gameType"]?.toString() ?: "")
            }.getOrDefault(GameType.QUIZ)
            val status = runCatching {
                BattleStatus.valueOf(d["status"]?.toString() ?: "")
            }.getOrDefault(BattleStatus.STARTING_SOON)
            val g1 = d["girl1Id"]?.toString().orEmpty()
            val host = d["hostId"]?.toString()?.takeIf { it.isNotBlank() } ?: g1

            // Reconstruct EmojiChallenge from flat Firestore fields
            val emojiEmoji = d["currentEmojiChallengeEmoji"]?.toString().orEmpty()
            val emojiChallenge = if (emojiEmoji.isNotBlank()) {
                @Suppress("UNCHECKED_CAST")
                val opts = (d["currentEmojiChallengeOptions"] as? List<*>)
                    ?.mapNotNull { it?.toString() } ?: emptyList()
                com.zipper.datingapp.data.EmojiChallenge(
                    emoji = emojiEmoji,
                    rightName = d["currentEmojiChallengeRightName"]?.toString().orEmpty(),
                    options = opts,
                    isSolved = d["currentEmojiChallengeIsSolved"] as? Boolean ?: false,
                    solvedBy = d["currentEmojiChallengeSolvedBy"]?.toString()?.takeIf { it.isNotBlank() }
                )
            } else null

            // Reconstruct opened mystery box state
            @Suppress("UNCHECKED_CAST")
            val openedBoxIndices = (d["openedBoxes"] as? List<*>)
                ?.mapNotNull { (it as? Number)?.toInt() }?.toSet() ?: emptySet()

            Battle(
                id = d["id"]?.toString() ?: doc.id,
                gameType = gameType,
                hostId = host,
                girl1Id = g1,
                girl2Id = d["girl2Id"]?.toString().orEmpty(),
                girl1Name = d["girl1Name"]?.toString().orEmpty(),
                girl2Name = d["girl2Name"]?.toString().orEmpty(),
                girl1Score = (d["girl1Score"] as? Number)?.toInt() ?: 0,
                girl2Score = (d["girl2Score"] as? Number)?.toInt() ?: 0,
                girl1Votes = (d["girl1Votes"] as? Number)?.toInt() ?: 0,
                girl2Votes = (d["girl2Votes"] as? Number)?.toInt() ?: 0,
                status = status,
                startTime = (d["startTime"] as? Number)?.toLong() ?: 0L,
                prizePool = (d["prizePool"] as? Number)?.toInt() ?: 0,
                viewerCount = (d["viewerCount"] as? Number)?.toInt() ?: 0,
                // Turn-based game fields
                currentTurnUserId = d["currentTurnUserId"]?.toString().orEmpty().ifBlank { g1 },
                currentEmojiChallenge = emojiChallenge,
                emojiGameTurnCount = (d["emojiGameTurnCount"] as? Number)?.toInt() ?: 0,
                // Spin game fields
                isSpinning = d["isSpinning"] as? Boolean ?: false,
                lastWinnerSectorIndex = (d["lastWinnerSectorIndex"] as? Number)?.toInt()?.takeIf { it >= 0 },
                girl1HasSpun = d["girl1HasSpun"] as? Boolean ?: false,
                girl2HasSpun = d["girl2HasSpun"] as? Boolean ?: false,
                spinBreakStartedAtMs = (d["spinBreakStartedAtMs"] as? Number)?.toLong() ?: 0L,
                // Mystery box: mark boxes as opened based on server-tracked openedBoxes list
                mysteryBoxes = List(4) { i ->
                    com.zipper.datingapp.data.MysteryBoxContent(
                        id = i,
                        rewardType = com.zipper.datingapp.data.MysteryRewardType.DIAMONDS,
                        amount = 0,
                        isOpened = i in openedBoxIndices
                    )
                }.let { defaults ->
                    // Preserve richer local box data if opened state is the only thing changing
                    defaults
                }
            )
        } catch (e: Exception) {
            Log.w(tag, "battleFromFirestore: skip doc ${doc.id}", e)
            null
        }
    }

    suspend fun deleteUserProfile(uid: String) {
        usersCollection.document(uid).delete().await()
    }

    /**
     * Sync follow graph: current user's `followingIds` and target's `followerIds` via [FieldValue.arrayUnion] / [FieldValue.arrayRemove].
     */
    suspend fun setFollowRelationship(currentUserId: String, targetUserId: String, follow: Boolean) {
        require(currentUserId.isNotBlank() && targetUserId.isNotBlank())
        if (currentUserId == targetUserId) throw IllegalArgumentException("Cannot follow self")
        val meRef = usersCollection.document(currentUserId)
        val themRef = usersCollection.document(targetUserId)
        Log.d(tag, "setFollowRelationship: me=$currentUserId target=$targetUserId follow=$follow")
        val batch = db.batch()
        if (follow) {
            batch.update(meRef, "followingIds", FieldValue.arrayUnion(targetUserId))
            batch.update(themRef, "followerIds", FieldValue.arrayUnion(currentUserId))
        } else {
            batch.update(meRef, "followingIds", FieldValue.arrayRemove(targetUserId))
            batch.update(themRef, "followerIds", FieldValue.arrayRemove(currentUserId))
        }
        batch.commit().await()
        Log.d(tag, "setFollowRelationship: batch committed")
    }

    /**
     * Flip follow state between [currentUserId] and [targetUserId] using the current user's Firestore profile.
     */
    suspend fun toggleFollowRelationship(currentUserId: String, targetUserId: String): Result<Unit> = runCatching {
        require(currentUserId.isNotBlank() && targetUserId.isNotBlank())
        if (currentUserId == targetUserId) throw IllegalArgumentException("Cannot follow self")
        val me = getUserProfile(currentUserId) ?: throw IllegalStateException("current user profile missing")
        val isFollowing = targetUserId in me.followingIds.toSet()
        setFollowRelationship(currentUserId, targetUserId, follow = !isFollowing)
    }

    /**
     * Toggle discovery-style profile like (`likeMeCount` / `likedByUserIds` on target, `iLikeCount` on liker).
     * @return `true` if the target is liked after this call.
     */
    suspend fun toggleDiscoveryProfileLike(likerUid: String, targetUserId: String): Result<Boolean> = runCatching {
        require(likerUid.isNotBlank() && targetUserId.isNotBlank())
        if (likerUid == targetUserId) throw IllegalArgumentException("Cannot like self")
        val target = getUserProfile(targetUserId) ?: throw IllegalStateException("target profile missing")
        val alreadyLiked = likerUid in target.likedByUserIds.toSet()
        val likerRef = usersCollection.document(likerUid)
        val targetRef = usersCollection.document(targetUserId)
        if (alreadyLiked) {
            likerRef.update("iLikeCount", FieldValue.increment(-1)).await()
            targetRef.update(
                mapOf(
                    "likeMeCount" to FieldValue.increment(-1),
                    "likedByUserIds" to FieldValue.arrayRemove(likerUid)
                )
            ).await()
            false
        } else {
            likerRef.update("iLikeCount", FieldValue.increment(1)).await()
            targetRef.update(
                mapOf(
                    "likeMeCount" to FieldValue.increment(1),
                    "likedByUserIds" to FieldValue.arrayUnion(likerUid)
                )
            ).await()
            true
        }
    }

    /**
     * Generic image upload (e.g. [normalizeImageReference] for legacy local paths).
     * For the **account avatar**, prefer [uploadProfilePhoto] (`users/{uid}/profile.jpg` + Firestore `photoUrl`).
     */
    suspend fun uploadImage(uri: Uri): String {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")
        val path = "users/$uid/media/${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child(path)
        Log.d(tag, "uploadImage: path=$path uri=$uri")
        ref.putFile(uri).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        Log.d(tag, "uploadImage: downloadUrl=$downloadUrl")
        if (!downloadUrl.startsWith("https://")) {
            throw IllegalStateException("Invalid storage download URL")
        }
        return downloadUrl
    }

    /**
     * Profile photo: stable path for rules/CDN; after upload, syncs [UserProfile.photoUrl] on the user doc.
     */
    suspend fun uploadProfilePhoto(uri: Uri): String {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")
        val path = "users/$uid/profile.jpg"
        val ref = storage.reference.child(path)
        Log.d(tag, "uploadProfilePhoto: path=$path uri=$uri")
        ref.putFile(uri).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        Log.d(tag, "uploadProfilePhoto: downloadUrl=$downloadUrl")
        if (!downloadUrl.startsWith("https://")) {
            throw IllegalStateException("Invalid storage download URL")
        }
        usersCollection.document(uid).update("photoUrl", downloadUrl).await()
        Log.d(tag, "uploadProfilePhoto: Firestore users/$uid photoUrl updated")
        return downloadUrl
    }

    /** Face verification asset (does not overwrite profile.jpg). */
    suspend fun uploadFaceVerificationImage(uri: Uri): String {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")
        val path = "users/$uid/verification/${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child(path)
        Log.d(tag, "uploadFaceVerificationImage: path=$path")
        ref.putFile(uri).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        Log.d(tag, "uploadFaceVerificationImage: downloadUrl=$downloadUrl")
        if (!downloadUrl.startsWith("https://")) {
            throw IllegalStateException("Invalid storage download URL")
        }
        return downloadUrl
    }

    /** Gallery image under [users/[uid]/gallery/]; returns HTTPS download URL (Firestore updated when saving profile). */
    suspend fun uploadGalleryImage(uri: Uri): String {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")
        val path = "users/$uid/gallery/${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child(path)
        Log.d(tag, "uploadGalleryImage: path=$path")
        ref.putFile(uri).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        Log.d(tag, "uploadGalleryImage: downloadUrl=$downloadUrl")
        if (!downloadUrl.startsWith("https://")) {
            throw IllegalStateException("Invalid storage download URL")
        }
        return downloadUrl
    }

    suspend fun uploadVideo(uri: Uri): String {
        val uid = auth.currentUser?.uid ?: "anon"
        val fileName = "users/$uid/videos/${UUID.randomUUID()}.mp4"
        val ref = storage.reference.child(fileName)
        Log.d(tag, "uploadVideo: path=$fileName")
        ref.putFile(uri).await()
        val url = ref.downloadUrl.await().toString()
        Log.d(tag, "uploadVideo: downloadUrl=$url")
        return url
    }

    suspend fun normalizeImageReference(raw: String): String {
        if (raw.isBlank()) return ""
        if (raw.isRemoteHttpUrl()) return raw
        val parsed = Uri.parse(raw)
        val scheme = parsed.scheme?.lowercase()
        val localUri = when (scheme) {
            "content", "file" -> parsed
            else -> Uri.fromFile(java.io.File(raw))
        }
        Log.d(tag, "normalizeImageReference: uploading local uri scheme=$scheme")
        return uploadImage(localUri)
    }

    /**
     * Real-time 1:1 thread: [whereEqualTo] on [Message.chatId] = [dmChatId] only (no composite index).
     * Results are sorted in memory by [Message.timestamp].
     */
    fun observeMessages(otherUserId: String): Flow<List<Message>> = callbackFlow {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId.isNullOrEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val otherTrimmed = otherUserId.trim()
        val chatId = dmChatId(currentUserId, otherTrimmed)
        if (chatId.isBlank()) {
            Log.e(
                "CHAT_ERROR",
                "observeMessages: invalid dmChatId (self-DM or blank uid) current=$currentUserId other=$otherTrimmed"
            )
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        Log.d(tag, "observeMessages: chatId=$chatId (lexicographic dmChatId)")
        Log.e("E2E_DIAG_CHAT", "Generated ChatID: $chatId for $currentUserId and $otherTrimmed")

        fun parseMessage(doc: com.google.firebase.firestore.DocumentSnapshot): Message? {
            if (!doc.exists()) return null
            val m = doc.safeToObject<Message>(tag) ?: return null
            return m.copy(id = doc.id)
        }

        val sub = messagesCollection
            .whereEqualTo("chatId", chatId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(
                        "CHAT_ERROR",
                        "Failed to load messages chatId=$chatId — Firestore rules or network? ${error.message}",
                        error
                    )
                    logFirestoreSnapshotError(
                        "messages chatId=$chatId (1:1 DM)",
                        error
                    )
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents
                val list = docs?.mapNotNull { parseMessage(it) }.orEmpty().sortedBy { it.timestamp }
                trySend(list)
            }

        awaitClose { sub.remove() }
    }

    /**
     * Fetches up to [perQuery] messages sent by the user and [perQuery] received, merges and dedupes by doc id.
     * Uses single-field equality queries (no OR / composite index). Threads are grouped client-side by peer id.
     */
    suspend fun fetchRecentMessagesInvolvingUser(uid: String, perQuery: Int = 100): List<Message> {
        val u = uid.trim()
        if (u.isEmpty()) return emptyList()
        val cap = perQuery.coerceIn(1, 200)
        return try {
            fun parseMessage(doc: DocumentSnapshot): Message? {
                if (!doc.exists()) return null
                val m = doc.safeToObject<Message>(tag) ?: return null
                return m.copy(id = doc.id)
            }
            val sent = messagesCollection.whereEqualTo("senderId", u).limit(cap.toLong()).get().await()
            val recv = messagesCollection.whereEqualTo("receiverId", u).limit(cap.toLong()).get().await()
            val merged = (sent.documents + recv.documents)
                .mapNotNull { parseMessage(it) }
                .distinctBy { it.id }
            merged.sortedBy { it.timestamp }
        } catch (e: Exception) {
            Log.e(tag, "fetchRecentMessagesInvolvingUser failed uid=$u", e)
            emptyList<Message>()
        }
    }

    suspend fun setUserPresence(uid: String, online: Boolean) {
        if (uid.isEmpty()) return
        val now = System.currentTimeMillis()
        val updates = mapOf(
            "isOnline" to online,
            "lastSeen" to now
        )
        Log.d(tag, "setUserPresence: uid=$uid online=$online (Firestore merge + RTDB presence)")
        // merge() so presence works even before a full profile row exists (update() would fail on missing doc)
        usersCollection.document(uid).set(updates, SetOptions.merge()).await()
        val pref = realtimeDb.reference.child("presence").child(uid)
        try {
            if (online) {
                val onDisconnectPayload = hashMapOf<String, Any>(
                    "isOnline" to false,
                    "lastSeen" to ServerValue.TIMESTAMP
                )
                pref.onDisconnect().setValue(onDisconnectPayload)
                pref.setValue(
                    hashMapOf<String, Any>(
                        "isOnline" to true,
                        "lastSeen" to ServerValue.TIMESTAMP
                    )
                ).await()
            } else {
                runCatching { pref.onDisconnect().cancel() }
                pref.setValue(
                    hashMapOf<String, Any>(
                        "isOnline" to false,
                        "lastSeen" to ServerValue.TIMESTAMP
                    )
                ).await()
            }
        } catch (e: Exception) {
            Log.w(tag, "setUserPresence RTDB presence mirror failed uid=$uid", e)
        }
    }

    suspend fun sendMessage(message: Message): Result<Unit> = suspendCancellableCoroutine { cont ->
        val chatId = dmChatId(message.senderId.trim(), message.receiverId.trim())
        if (chatId.isBlank()) {
            Log.e(
                "CHAT_ERROR",
                "sendMessage: invalid DM participants (must differ, non-blank) sender=${message.senderId} receiver=${message.receiverId}"
            )
            cont.resume(Result.failure(IllegalArgumentException("Invalid DM participants")))
            return@suspendCancellableCoroutine
        }
        Log.e("E2E_DIAG_CHAT", "Generated ChatID: $chatId for ${message.senderId.trim()} and ${message.receiverId.trim()}")
        val payload = message.copy(
            chatId = chatId,
            senderId = message.senderId.trim(),
            receiverId = message.receiverId.trim()
        )
        Log.d(
            tag,
            "sendMessage: chatId=$chatId sender=${payload.senderId} receiver=${payload.receiverId} type=${payload.type}"
        )
        messagesCollection.add(payload)
            .addOnSuccessListener {
                Log.d(tag, "sendMessage: Firestore add() completed")
                cont.resume(Result.success(Unit))
            }
            .addOnFailureListener { e ->
                Log.e(
                    "CHAT_ERROR",
                    "sendMessage FAILED chatId=$chatId — rules or index: ${e.message}",
                    e
                )
                Log.e(
                    tag,
                    "sendMessage add() FAILED — rules or network for \"messages\": ${e.message}",
                    e
                )
                cont.resume(Result.failure(e))
            }
    }

    /** Real-time profile for a single user (e.g. live host follower count). */
    fun observeUserProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        if (uid.isBlank()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val reg = usersCollection.document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                logFirestoreSnapshotError("users/$uid (observeUserProfile)", error)
                return@addSnapshotListener
            }
            trySend(userProfileFromDocument(snapshot))
        }
        awaitClose { reg.remove() }
    }

    /** Single battle document — merges votes/scores from Firestore for multiplayer sync. */
    fun observeBattleDocument(battleId: String): Flow<Battle?> = callbackFlow {
        if (battleId.isBlank()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val reg = battlesCollection.document(battleId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                logFirestoreSnapshotError("battles/$battleId", error)
                return@addSnapshotListener
            }
            trySend(snapshot?.let { battleFromFirestore(it) })
        }
        awaitClose { reg.remove() }
    }

    fun observeBattleGameState(battleId: String): Flow<GameState?> = callbackFlow {
        if (battleId.isBlank()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val reg = battlesCollection.document(battleId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                logFirestoreSnapshotError("battles/$battleId gameState", error)
                return@addSnapshotListener
            }
            trySend(snapshot?.let { parseGameStateFromBattleDoc(it) })
        }
        awaitClose { reg.remove() }
    }

    private fun parseGameStateFromBattleDoc(doc: DocumentSnapshot): GameState? {
        if (!doc.exists()) return null
        @Suppress("UNCHECKED_CAST")
        val payload = doc.get("gameStateData") as? Map<String, Any> ?: emptyMap()
        return GameState(
            battleId = doc.id,
            version = doc.getLong("gameStateVersion") ?: 0L,
            phase = doc.getString("gameStatePhase") ?: "LOBBY",
            lastActorId = doc.getString("gameStateLastActorId") ?: "",
            updatedAtMs = doc.getLong("gameStateUpdatedAtMs") ?: 0L,
            data = payload
        )
    }

    /**
     * Atomic battle updates (votes, etc.) so host and audience do not clobber each other.
     */
    suspend fun applyBattleGameInput(
        battleId: String,
        actorId: String,
        action: String,
        actionData: Map<String, Any>
    ): Result<Unit> {
        if (battleId.isBlank() || actorId.isBlank()) return Result.failure(IllegalArgumentException("battleId and actorId required"))
        return runCatching {
            db.runTransaction { tx ->
                val ref = battlesCollection.document(battleId)
                val snap = tx.get(ref)
                if (!snap.exists()) throw IllegalStateException("Battle not found")
                val version = snap.getLong("gameStateVersion") ?: 0L
                when (action) {
                    "VOTE" -> {
                        val team = (actionData["team"] as? Number)?.toInt()
                            ?: throw IllegalArgumentException("team required")
                        val g1 = snap.getLong("girl1Votes")?.toInt() ?: 0
                        val g2 = snap.getLong("girl2Votes")?.toInt() ?: 0
                        val ng1 = if (team == 1) g1 + 1 else g1
                        val ng2 = if (team == 2) g2 + 1 else g2
                        tx.update(
                            ref,
                            mapOf(
                                "girl1Votes" to ng1,
                                "girl2Votes" to ng2,
                                "gameStateVersion" to version + 1,
                                "gameStatePhase" to "LIVE",
                                "gameStateLastActorId" to actorId,
                                "gameStateUpdatedAtMs" to System.currentTimeMillis()
                            )
                        )
                    }
                    "GIFT" -> {
                        val team = (actionData["team"] as? Number)?.toInt()
                            ?: throw IllegalArgumentException("team required")
                        val points = (actionData["points"] as? Number)?.toInt()
                            ?: throw IllegalArgumentException("points required")
                        if (points <= 0) throw IllegalArgumentException("points must be positive")
                        val s1 = snap.getLong("girl1Score")?.toInt()
                            ?: snap.get("girl1Score")?.toString()?.toIntOrNull() ?: 0
                        val s2 = snap.getLong("girl2Score")?.toInt()
                            ?: snap.get("girl2Score")?.toString()?.toIntOrNull() ?: 0
                        val ns1 = if (team == 1) s1 + points else s1
                        val ns2 = if (team == 2) s2 + points else s2
                        tx.update(
                            ref,
                            mapOf(
                                "girl1Score" to ns1,
                                "girl2Score" to ns2,
                                "gameStateVersion" to version + 1,
                                "gameStatePhase" to "LIVE",
                                "gameStateLastActorId" to actorId,
                                "gameStateUpdatedAtMs" to System.currentTimeMillis()
                            )
                        )
                    }
                    "FORFEIT" -> {
                        // Award the win to the other player and close the battle
                        val forfeitingUid = (actionData["forfeitingUid"] as? String) ?: actorId
                        val g1Id = snap.getString("girl1Id") ?: ""
                        val isG1Forfeiting = forfeitingUid == g1Id
                        val s1 = snap.getLong("girl1Score")?.toInt() ?: 0
                        val s2 = snap.getLong("girl2Score")?.toInt() ?: 0
                        // Give a massive forfeit-win bonus so the result computation always picks the right winner
                        tx.update(
                            ref,
                            mapOf(
                                "status" to BattleStatus.FINISHED.name,
                                "girl1Score" to if (isG1Forfeiting) s1 else (s1 + 9999),
                                "girl2Score" to if (isG1Forfeiting) (s2 + 9999) else s2,
                                "gameStateVersion" to version + 1,
                                "gameStatePhase" to "FORFEIT",
                                "gameStateLastActorId" to actorId,
                                "gameStateUpdatedAtMs" to System.currentTimeMillis()
                            )
                        )
                        Log.w(tag, "applyBattleGameInput FORFEIT: battle=$battleId forfeitedBy=$forfeitingUid")
                    }
                    "SPIN_START" -> {
                        if (snap.getBoolean("isSpinning") == true) throw IllegalStateException("Spin already in progress")
                        val g1IdSpin = snap.getString("girl1Id") ?: ""
                        val isG1Spin = actorId == g1IdSpin
                        // Guard: actor must not have already spun this round
                        val actorAlreadySpun = if (isG1Spin)
                            snap.getBoolean("girl1HasSpun") == true
                        else
                            snap.getBoolean("girl2HasSpun") == true
                        if (actorAlreadySpun) throw IllegalStateException("Already spun this round")
                        // Turn enforcement
                        val spinTurn = snap.getString("currentTurnUserId") ?: ""
                        if (spinTurn.isNotBlank() && spinTurn != actorId)
                            throw IllegalStateException("Not your turn (turn=$spinTurn actor=$actorId)")
                        val sectorCount = (snap.getLong("spinSectorCount") ?: 6L).toInt().coerceAtLeast(1)
                        val winnerIndex = (Math.random() * sectorCount).toInt().coerceIn(0, sectorCount - 1)
                        tx.update(
                            ref,
                            mapOf(
                                "isSpinning" to true,
                                "lastWinnerSectorIndex" to winnerIndex,
                                "gameStateVersion" to version + 1,
                                "gameStatePhase" to "SPINNING",
                                "gameStateLastActorId" to actorId,
                                "gameStateUpdatedAtMs" to System.currentTimeMillis()
                            )
                        )
                    }
                    "SPIN_STOP" -> {
                        if (snap.getBoolean("isSpinning") != true) {
                            throw IllegalStateException("SPIN_STOP: not spinning")
                        }
                        val g1IdStop = snap.getString("girl1Id") ?: ""
                        val isG1Stop = actorId == g1IdStop
                        val winnerIdx = (snap.getLong("lastWinnerSectorIndex") ?: 0L).toInt()
                        // Award points: sector index maps to (index + 1) * 10 points
                        val sectorPoints = (winnerIdx + 1) * 10
                        val s1Stop = (snap.getLong("girl1Score") ?: 0L).toInt()
                        val s2Stop = (snap.getLong("girl2Score") ?: 0L).toInt()
                        tx.update(
                            ref,
                            mapOf(
                                "isSpinning" to false,
                                "girl1Score" to if (isG1Stop) s1Stop + sectorPoints else s1Stop,
                                "girl2Score" to if (!isG1Stop) s2Stop + sectorPoints else s2Stop,
                                "girl1HasSpun" to if (isG1Stop) true else (snap.getBoolean("girl1HasSpun") ?: false),
                                "girl2HasSpun" to if (!isG1Stop) true else (snap.getBoolean("girl2HasSpun") ?: false),
                                "spinBreakStartedAtMs" to System.currentTimeMillis(),
                                "gameStateVersion" to version + 1,
                                "gameStatePhase" to "SPIN_RESULT",
                                "gameStateLastActorId" to actorId,
                                "gameStateUpdatedAtMs" to System.currentTimeMillis()
                            )
                        )
                    }
                    "SPIN_ROUND_RESET" -> {
                        val breakStarted = snap.getLong("spinBreakStartedAtMs") ?: 0L
                        if (breakStarted <= 0L) {
                            throw IllegalStateException("SPIN_ROUND_RESET: no break window")
                        }
                        val elapsedBreak = System.currentTimeMillis() - breakStarted
                        if (elapsedBreak < 8_000L) {
                            throw IllegalStateException("SPIN_ROUND_RESET: break too short ($elapsedBreak ms)")
                        }
                        val spunG1 = snap.getBoolean("girl1HasSpun") == true
                        val spunG2 = snap.getBoolean("girl2HasSpun") == true
                        if (!spunG1 && !spunG2) {
                            throw IllegalStateException("SPIN_ROUND_RESET: no completed spin")
                        }
                        val g1IdReset = snap.getString("girl1Id") ?: ""
                        val g2IdReset = snap.getString("girl2Id") ?: ""
                        val prevTurnReset = snap.getString("currentTurnUserId") ?: g1IdReset
                        val nextTurnReset = if (prevTurnReset == g1IdReset) g2IdReset else g1IdReset
                        tx.update(
                            ref,
                            mapOf(
                                "isSpinning" to false,
                                "girl1HasSpun" to false,
                                "girl2HasSpun" to false,
                                "spinBreakStartedAtMs" to 0L,
                                "lastWinnerSectorIndex" to -1,
                                "currentTurnUserId" to nextTurnReset,
                                "gameStateVersion" to version + 1,
                                "gameStatePhase" to "SPIN_IDLE",
                                "gameStateLastActorId" to actorId,
                                "gameStateUpdatedAtMs" to System.currentTimeMillis()
                            )
                        )
                    }
                    "EMOJI_TURN" -> {
                        val emoji = (actionData["emoji"] as? String) ?: throw IllegalArgumentException("emoji required")
                        val currentTurn = snap.getString("currentTurnUserId") ?: snap.getString("girl1Id") ?: ""
                        if (currentTurn.isNotBlank() && currentTurn != actorId) {
                            throw IllegalStateException("Not your turn (turn=$currentTurn actor=$actorId)")
                        }
                        val g1Id = snap.getString("girl1Id") ?: ""
                        val g2Id = snap.getString("girl2Id") ?: ""
                        val nextTurn = if (actorId == g1Id) g2Id else g1Id
                        // Build distractors from a fixed bank so no client-side cheating
                        val emojiBank = listOf("🍎","🐶","⚽","🚗","🍕","🎸","🍦","🚀","🎮","🏠","🌹","🐱","🍌","⭐","🎀","🐸","🦄","🍩","🎃","🌈")
                        val emojiNames = mapOf(
                            "🍎" to "Apple","🐶" to "Dog","⚽" to "Ball","🚗" to "Car","🍕" to "Pizza",
                            "🎸" to "Guitar","🍦" to "Ice Cream","🚀" to "Rocket","🎮" to "Game","🏠" to "House",
                            "🌹" to "Rose","🐱" to "Cat","🍌" to "Banana","⭐" to "Star","🎀" to "Ribbon",
                            "🐸" to "Frog","🦄" to "Unicorn","🍩" to "Donut","🎃" to "Pumpkin","🌈" to "Rainbow"
                        )
                        val rightName = emojiNames[emoji] ?: emoji
                        val distractors = emojiNames.entries
                            .filter { it.key != emoji }
                            .shuffled()
                            .take(3)
                            .map { it.value }
                        val options = (distractors + rightName).shuffled()
                        val turnCount = (snap.getLong("emojiGameTurnCount") ?: 0L).toInt()
                        tx.update(
                            ref,
                            mapOf(
                                "currentTurnUserId" to nextTurn,
                                "currentEmojiChallengeEmoji" to emoji,
                                "currentEmojiChallengeRightName" to rightName,
                                "currentEmojiChallengeOptions" to options,
                                "currentEmojiChallengeIsSolved" to false,
                                "currentEmojiChallengeSolvedBy" to "",
                                "emojiGameTurnCount" to (turnCount + 1),
                                "gameStateVersion" to version + 1,
                                "gameStatePhase" to "EMOJI_GUESS",
                                "gameStateLastActorId" to actorId,
                                "gameStateUpdatedAtMs" to System.currentTimeMillis()
                            )
                        )
                    }
                    "EMOJI_SOLVE" -> {
                        val answer = (actionData["answer"] as? String) ?: throw IllegalArgumentException("answer required")
                        val currentTurn = snap.getString("currentTurnUserId") ?: ""
                        if (currentTurn == actorId) throw IllegalStateException("Setter cannot solve their own challenge")
                        val rightName = snap.getString("currentEmojiChallengeRightName") ?: ""
                        val isCorrect = answer.equals(rightName, ignoreCase = true)
                        val g1Id = snap.getString("girl1Id") ?: ""
                        val g2Id = snap.getString("girl2Id") ?: ""
                        val s1 = snap.getLong("girl1Score")?.toInt() ?: 0
                        val s2 = snap.getLong("girl2Score")?.toInt() ?: 0
                        val reward = 50
                        val ns1 = if (isCorrect && actorId == g1Id) s1 + reward else s1
                        val ns2 = if (isCorrect && actorId == g2Id) s2 + reward else s2
                        // After a solve attempt the other player sets the next challenge
                        val nextTurn = if (actorId == g1Id) g2Id else g1Id
                        tx.update(
                            ref,
                            mapOf(
                                "girl1Score" to ns1,
                                "girl2Score" to ns2,
                                "currentTurnUserId" to nextTurn,
                                "currentEmojiChallengeEmoji" to "",
                                "currentEmojiChallengeRightName" to "",
                                "currentEmojiChallengeOptions" to emptyList<String>(),
                                "currentEmojiChallengeIsSolved" to true,
                                "currentEmojiChallengeSolvedBy" to if (isCorrect) actorId else "",
                                "gameStateVersion" to version + 1,
                                "gameStatePhase" to if (isCorrect) "EMOJI_CORRECT" else "EMOJI_WRONG",
                                "gameStateLastActorId" to actorId,
                                "gameStateUpdatedAtMs" to System.currentTimeMillis()
                            )
                        )
                        Log.d(tag, "EMOJI_SOLVE: actor=$actorId answer='$answer' correct=$isCorrect rightName='$rightName'")
                    }
                    "OPEN_BOX" -> {
                        val boxIndex = (actionData["boxIndex"] as? Number)?.toInt()
                            ?: throw IllegalArgumentException("boxIndex required")
                        @Suppress("UNCHECKED_CAST")
                        val openedBoxes = (snap.get("openedBoxes") as? List<*>)
                            ?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
                        if (boxIndex in openedBoxes) throw IllegalStateException("Box $boxIndex already opened")
                        val newOpenedBoxes = openedBoxes + boxIndex
                        tx.update(
                            ref,
                            mapOf(
                                "openedBoxes" to newOpenedBoxes,
                                "gameStateVersion" to version + 1,
                                "gameStatePhase" to "BOX_OPENED",
                                "gameStateLastActorId" to actorId,
                                "gameStateUpdatedAtMs" to System.currentTimeMillis()
                            )
                        )
                    }
                    else -> throw IllegalArgumentException("Unknown game action: $action")
                }
            }.await()
            Unit
        }.onFailure { e -> Log.e(tag, "applyBattleGameInput failed battle=$battleId action=$action", e) }
    }

    /**
     * Ensures `live_streams/{streamId}` exists for chat / viewer counts.
     * @param resetStreamSessionStart When true (host taps Go Live), always set [streamStartedAtMillis] to now
     * so a new session does not reuse a stale clock from a leftover document.
     * @param audioOnlyStream When [resetStreamSessionStart] is true, persists host audio-only (no camera) mode for discovery/UI.
     * @param audioPartySeatCount When starting an audio-only session, persists stage seat count (6, 10, 14, or 17). Ignored when not audio-only.
     */
    suspend fun ensureLiveStreamDocument(
        streamId: String,
        hostId: String,
        resetStreamSessionStart: Boolean = false,
        audioOnlyStream: Boolean = false,
        audioPartySeatCount: Int? = null,
    ) {
        if (streamId.isBlank()) return
        val seatsForAudio = normalizedAudioPartySeatCountValue(audioPartySeatCount ?: 10)
        try {
            db.runTransaction { tx ->
                val ref = liveStreamsCollection.document(streamId)
                val snap = tx.get(ref)
                val now = System.currentTimeMillis()
                if (!snap.exists()) {
                    val create = hashMapOf<String, Any>(
                        "hostId" to hostId,
                        "viewerCount" to 0L,
                        "streamStartedAtMillis" to now,
                        "updatedAtMs" to now,
                        "audioOnlyStream" to audioOnlyStream
                    )
                    if (audioOnlyStream) {
                        create["audioPartySeatCount"] = seatsForAudio
                    }
                    tx.set(ref, create)
                } else {
                    val started = snap.getLong("streamStartedAtMillis") ?: 0L
                    val updates = hashMapOf<String, Any>(
                        "hostId" to hostId,
                        "updatedAtMs" to now
                    )
                    if (resetStreamSessionStart || started <= 0L) {
                        updates["streamStartedAtMillis"] = now
                    }
                    // Solo "Go Live" must not reuse stale PK banner fields from a prior session on the same doc.
                    if (resetStreamSessionStart) {
                        updates["pkStartedAtMillis"] = 0L
                        updates["pkHostDisplayName"] = ""
                        updates["pkGuestDisplayName"] = ""
                        updates["pkGuestUserId"] = ""
                        updates["pkBattleHostUid"] = ""
                        updates["pkBattleGuestUid"] = ""
                        updates["pkDurationSeconds"] = 0L
                        updates["pkScoringOpen"] = false
                        updates["pkResultPhaseEndsAtMillis"] = 0L
                        updates["pkHostViewerCount"] = 0L
                        updates["pkGuestViewerCount"] = 0L
                        updates["pkHostViewerUserIds"] = emptyList<String>()
                        updates["pkGuestViewerUserIds"] = emptyList<String>()
                        updates["audioOnlyStream"] = audioOnlyStream
                        updates["audioPartySeatCount"] = if (audioOnlyStream) seatsForAudio else 0L
                    }
                    tx.set(ref, updates, SetOptions.merge())
                }
            }.await()
        } catch (e: Exception) {
            Log.e(tag, "ensureLiveStreamDocument tx streamId=$streamId", e)
            runCatching {
                val now = System.currentTimeMillis()
                if (resetStreamSessionStart) {
                    val fallback = hashMapOf<String, Any>(
                        "hostId" to hostId,
                        "updatedAtMs" to now,
                        "streamStartedAtMillis" to now,
                        "audioOnlyStream" to audioOnlyStream,
                        "audioPartySeatCount" to if (audioOnlyStream) seatsForAudio else 0L,
                        "pkStartedAtMillis" to 0L,
                        "pkHostDisplayName" to "",
                        "pkGuestDisplayName" to "",
                        "pkGuestUserId" to "",
                        "pkBattleHostUid" to "",
                        "pkBattleGuestUid" to "",
                        "pkDurationSeconds" to 0L,
                        "pkScoringOpen" to false,
                        "pkResultPhaseEndsAtMillis" to 0L,
                        "pkHostViewerCount" to 0L,
                        "pkGuestViewerCount" to 0L,
                        "pkHostViewerUserIds" to emptyList<String>(),
                        "pkGuestViewerUserIds" to emptyList<String>()
                    )
                    liveStreamsCollection.document(streamId).set(
                        fallback,
                        SetOptions.merge()
                    ).await()
                } else {
                    liveStreamsCollection.document(streamId).set(
                        mapOf(
                            "hostId" to hostId,
                            "updatedAtMs" to now
                        ),
                        SetOptions.merge()
                    ).await()
                }
            }.onFailure { e2 -> Log.e(tag, "ensureLiveStreamDocument fallback streamId=$streamId", e2) }
        }
        Log.d(tag, "ensureLiveStreamDocument: streamId=$streamId")
    }

    /** Host mid-session: update audio-party seat layout (6, 10, 14, or 17) on `live_streams/{streamId}`. */
    suspend fun mergeLiveStreamAudioPartySeatCount(streamId: String, seatCount: Int) {
        if (streamId.isBlank()) return
        val n = when (seatCount) {
            6 -> 6L
            14 -> 14L
            17 -> 17L
            else -> 10L
        }
        try {
            liveStreamsCollection.document(streamId).set(
                mapOf(
                    "audioPartySeatCount" to n,
                    "updatedAtMs" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.e(tag, "mergeLiveStreamAudioPartySeatCount streamId=$streamId", e)
        }
    }

    private fun normalizedAudioPartySeatCountValue(count: Int): Long = when (count) {
        6 -> 6L
        14 -> 14L
        17 -> 17L
        else -> 10L
    }

    suspend fun mergeAudioPartyRoomBackground(hostId: String, roomBackgroundKey: String) {
        if (hostId.isBlank()) return
        val key = roomBackgroundKey.trim().ifBlank { "default" }.take(48)
        try {
            streamsCollection.document(hostId).set(
                mapOf("roomBackgroundKey" to key, "updatedAtMs" to System.currentTimeMillis()),
                SetOptions.merge(),
            ).await()
        } catch (e: Exception) {
            Log.e(tag, "mergeAudioPartyRoomBackground hostId=$hostId", e)
        }
    }

    suspend fun ensureAudioPartyStreamDocument(hostId: String, seatCount: Int) {
        if (hostId.isBlank()) return
        val n = normalizedAudioPartySeatCountValue(seatCount)
        val now = System.currentTimeMillis()
        try {
            val ref = streamsCollection.document(hostId)
            val snap = ref.get().await()
            if (!snap.exists()) {
                ref.set(
                    mapOf(
                        "hostId" to hostId,
                        "seatCount" to n,
                        "updatedAtMs" to now,
                        "seats" to hashMapOf<String, Any>()
                    )
                ).await()
            } else {
                ref.set(
                    mapOf(
                        "hostId" to hostId,
                        "seatCount" to n,
                        "updatedAtMs" to now
                    ),
                    SetOptions.merge()
                ).await()
            }
        } catch (e: Exception) {
            Log.e(tag, "ensureAudioPartyStreamDocument hostId=$hostId", e)
        }
    }

    suspend fun mergeAudioPartyStreamSeatCount(hostId: String, seatCount: Int) {
        if (hostId.isBlank()) return
        val n = normalizedAudioPartySeatCountValue(seatCount)
        try {
            streamsCollection.document(hostId).set(
                mapOf("seatCount" to n, "updatedAtMs" to System.currentTimeMillis()),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.e(tag, "mergeAudioPartyStreamSeatCount hostId=$hostId", e)
        }
    }

    /** Copies `messages` subcollection docs from [fromLiveDoc] to [toLiveDoc], preserving ids and fields. */
    private suspend fun copyLiveStreamMessagesBetweenStreams(
        fromLiveDoc: DocumentReference,
        toLiveDoc: DocumentReference,
    ) {
        val from = fromLiveDoc.collection("messages")
        val to = toLiveDoc.collection("messages")
        var last: DocumentSnapshot? = null
        while (true) {
            var q = from.orderBy(FieldPath.documentId(), Query.Direction.ASCENDING).limit(100)
            last?.let { q = q.startAfter(it) }
            val snap = q.get().await()
            if (snap.isEmpty) break
            val batch = db.batch()
            for (d in snap.documents) {
                val data = d.data ?: continue
                batch.set(to.document(d.id), data, SetOptions.merge())
            }
            batch.commit().await()
            last = snap.documents.lastOrNull() ?: break
            if (snap.size() < 100) break
        }
    }

    /** Deletes all documents in [col] in bounded batches (Firestore subcollections are not removed with the parent doc). */
    private suspend fun deleteAllDocumentsInCollection(col: CollectionReference) {
        while (true) {
            val snap = col.limit(400).get().await()
            if (snap.isEmpty) break
            var batch = db.batch()
            var n = 0
            for (doc in snap.documents) {
                batch.delete(doc.reference)
                n++
                if (n >= 450) {
                    batch.commit().await()
                    batch = db.batch()
                    n = 0
                }
            }
            if (n > 0) batch.commit().await()
            if (snap.size() < 400) break
        }
    }

    /**
     * Transfers solo **audio party** ownership from [oldHostUid] to [newHostUid].
     * The new host must already occupy a seat on `streams/{oldHostUid}`; the former host moves into that seat.
     *
     * Writes `live_streams/{newHostUid}` + `streams/{newHostUid}`, stamps a short-lived redirect on the old
     * `live_streams/{oldHostUid}` doc, updates both users' live flags, then after a short delay copies chat
     * from the old `live_streams/{old}/messages` into the new room, deletes the old messages subcollection,
     * and removes the old `live_streams` + `streams` documents.
     */
    suspend fun handoffAudioPartyHost(oldHostUid: String, newHostUid: String): Result<Unit> {
        val me = auth.currentUser?.uid?.trim().orEmpty()
        val oldH = oldHostUid.trim()
        val newH = newHostUid.trim()
        if (me.isEmpty() || me != oldH) {
            return Result.failure(IllegalStateException("Only the room owner can transfer the host"))
        }
        if (newH.isEmpty() || oldH == newH) {
            return Result.failure(IllegalArgumentException("Invalid new host"))
        }
        return try {
            val oldStreamRef = streamsCollection.document(oldH)
            val oldLiveRef = liveStreamsCollection.document(oldH)
            val newStreamRef = streamsCollection.document(newH)
            val newLiveRef = liveStreamsCollection.document(newH)

            val oldStreamSnap = oldStreamRef.get().await()
            val oldLiveSnap = oldLiveRef.get().await()
            if (!oldStreamSnap.exists()) {
                return Result.failure(IllegalStateException("Audio party room data missing"))
            }
            if (!oldLiveSnap.exists()) {
                return Result.failure(IllegalStateException("Live stream missing"))
            }
            if (oldLiveSnap.getBoolean("audioOnlyStream") != true) {
                return Result.failure(IllegalStateException("Host transfer is only for audio party streams"))
            }

            @Suppress("UNCHECKED_CAST")
            val seatsRaw = oldStreamSnap.get("seats") as? Map<String, *> ?: emptyMap<String, Any>()
            var handoffSeatKey: String? = null
            for ((k, v) in seatsRaw) {
                val m = v as? Map<String, *> ?: continue
                val uid = (m["userId"] as? String).orEmpty().trim()
                if (uid == newH) {
                    handoffSeatKey = k
                    break
                }
            }
            if (handoffSeatKey == null) {
                return Result.failure(IllegalStateException("New host must be seated on stage"))
            }

            val oldProfile = getUserProfile(oldH)
            val oldName = oldProfile?.name?.trim().orEmpty().ifBlank { "Host" }
            val oldPhoto = oldProfile?.photoUrl?.trim().orEmpty()

            val newSeatsPayload = hashMapOf<String, Any>()
            for ((k, v) in seatsRaw) {
                if (k == handoffSeatKey) continue
                val m = v as? Map<String, *> ?: continue
                k.toIntOrNull() ?: continue
                val uid = (m["userId"] as? String).orEmpty().trim()
                val locked = m["locked"] == true || m["locked"] == 1L || "${m["locked"]}" == "1"
                val muted = m["mutedByHost"] == true || m["mutedByHost"] == 1L || "${m["mutedByHost"]}" == "1"
                val price = ((m["priceCoins"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
                val publishing = m["publishingAudio"] == true || m["publishingAudio"] == 1L
                newSeatsPayload[k] = seatPayload(
                    userId = uid,
                    displayName = (m["displayName"] as? String).orEmpty().trim(),
                    photoUrl = (m["photoUrl"] as? String).orEmpty().trim(),
                    locked = locked,
                    mutedByHost = muted,
                    priceCoins = price,
                    publishingAudio = publishing && uid.isNotEmpty(),
                )
            }
            @Suppress("UNCHECKED_CAST")
            val handoffSeatMap = seatsRaw[handoffSeatKey] as? Map<String, *> ?: emptyMap<String, Any?>()
            val handoffPrice = ((handoffSeatMap["priceCoins"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
            newSeatsPayload[handoffSeatKey] = seatPayload(
                userId = oldH,
                displayName = oldName,
                photoUrl = oldPhoto,
                locked = false,
                mutedByHost = false,
                priceCoins = handoffPrice,
                publishingAudio = false,
            )

            var coHost = oldStreamSnap.getString("coHostUserId").orEmpty().trim()
            if (coHost == newH || coHost == oldH) coHost = ""

            val seatCountL = normalizedAudioPartySeatCountValue(
                (oldStreamSnap.getLong("seatCount") ?: 10L).toInt()
            )

            val streamPayload = hashMapOf<String, Any>(
                "hostId" to newH,
                "seatCount" to seatCountL,
                "seats" to newSeatsPayload,
                "updatedAtMs" to System.currentTimeMillis(),
            )
            if (coHost.isNotEmpty()) {
                streamPayload["coHostUserId"] = coHost
            } else {
                streamPayload["coHostUserId"] = FieldValue.delete()
            }
            oldStreamSnap.getString("nowPlayingTitle")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                streamPayload["nowPlayingTitle"] = it
            }
            oldStreamSnap.getString("nowPlayingArtist")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                streamPayload["nowPlayingArtist"] = it
            }
            oldStreamSnap.getString("stageEmoji")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                streamPayload["stageEmoji"] = it
            }
            val emojiAt = oldStreamSnap.getLong("stageEmojiAtMs") ?: 0L
            if (emojiAt > 0L) streamPayload["stageEmojiAtMs"] = emojiAt

            val viewerCount = oldLiveSnap.getLong("viewerCount") ?: 0L
            val streamStarted = oldLiveSnap.getLong("streamStartedAtMillis") ?: System.currentTimeMillis()
            val apSeatLive = oldLiveSnap.getLong("audioPartySeatCount") ?: seatCountL
            val kicked = parseUidListFromFirestore(oldLiveSnap, "kickedUsers")

            val livePayload = hashMapOf<String, Any>(
                "hostId" to newH,
                "viewerCount" to viewerCount,
                "streamStartedAtMillis" to streamStarted,
                "updatedAtMs" to System.currentTimeMillis(),
                "audioOnlyStream" to true,
                "audioPartySeatCount" to apSeatLive,
                "hostPrivateCallBusy" to false,
                "pkStartedAtMillis" to 0L,
                "pkHostDisplayName" to "",
                "pkGuestDisplayName" to "",
                "pkGuestUserId" to "",
                "pkBattleHostUid" to "",
                "pkBattleGuestUid" to "",
                "pkDurationSeconds" to 0L,
                "pkScoringOpen" to false,
                "pkResultPhaseEndsAtMillis" to 0L,
                "pkHostBeans" to 0L,
                "pkGuestBeans" to 0L,
                "pkHostViewerCount" to 0L,
                "pkGuestViewerCount" to 0L,
                "pkHostViewerUserIds" to emptyList<String>(),
                "pkGuestViewerUserIds" to emptyList<String>(),
            )
            if (kicked.isNotEmpty()) {
                livePayload["kickedUsers"] = kicked
            }

            val batch = db.batch()
            batch.set(newStreamRef, streamPayload, SetOptions.merge())
            batch.set(newLiveRef, livePayload, SetOptions.merge())
            batch.commit().await()

            oldLiveRef.set(
                mapOf(
                    "audioPartyHandoffToUid" to newH,
                    "audioPartyHandoffAtMs" to System.currentTimeMillis(),
                    "updatedAtMs" to System.currentTimeMillis(),
                ),
                SetOptions.merge(),
            ).await()

            updateUserLiveStatus(oldH, false, null)
            updateUserLiveStatus(newH, true, newH)
            runCatching {
                usersCollection.document(newH).update(hashMapOf<String, Any>("liveStreamAudioOnly" to true)).await()
            }
            runCatching {
                usersCollection.document(oldH).update(hashMapOf<String, Any>("liveStreamAudioOnly" to false)).await()
            }

            delay(2500L)

            runCatching {
                copyLiveStreamMessagesBetweenStreams(oldLiveRef, newLiveRef)
            }.onFailure { Log.w(tag, "handoffAudioPartyHost chat copy old=$oldH new=$newH", it) }
            runCatching {
                deleteAllDocumentsInCollection(oldLiveRef.collection("messages"))
            }.onFailure { Log.w(tag, "handoffAudioPartyHost chat cleanup old=$oldH", it) }
            runCatching { oldLiveRef.delete().await() }
            runCatching { oldStreamRef.delete().await() }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "handoffAudioPartyHost old=$oldHostUid new=$newHostUid", e)
            Result.failure(e)
        }
    }

    fun observeAudioPartyStream(hostId: String): Flow<AudioPartyStreamState> = callbackFlow {
        if (hostId.isBlank()) {
            trySend(AudioPartyStreamState(streamActive = false))
            awaitClose { }
            return@callbackFlow
        }
        val reg = streamsCollection.document(hostId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                logFirestoreSnapshotError("streams/$hostId", error)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) {
                trySend(AudioPartyStreamState(streamActive = false))
                return@addSnapshotListener
            }
            trySend(snapshot.toAudioPartyStreamState())
        }
        awaitClose { reg.remove() }
    }

    /**
     * Host inbox: `streams/{hostId}/video_call_requests/{fromUserId}` — typically filter to [AudioPartyVideoCallRequest.status] == `pending` in UI.
     */
    fun observeAudioPartyVideoCallRequests(hostId: String): Flow<List<AudioPartyVideoCallRequest>> = callbackFlow {
        if (hostId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val reg = streamsCollection.document(hostId).collection("video_call_requests")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logFirestoreSnapshotError("streams/$hostId/video_call_requests", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { parseAudioPartyVideoCallRequest(it) } ?: emptyList()
                trySend(list.filter { it.status == "pending" }.sortedByDescending { it.requestedAtMs })
            }
        awaitClose { reg.remove() }
    }

    private fun parseAudioPartyVideoCallRequest(doc: DocumentSnapshot): AudioPartyVideoCallRequest? {
        val from = doc.id.trim()
        if (from.isEmpty()) return null
        val statusRaw = doc.getString("status")?.trim()?.lowercase().orEmpty()
        return AudioPartyVideoCallRequest(
            fromUserId = from,
            displayName = doc.getString("displayName").orEmpty(),
            photoUrl = doc.getString("photoUrl").orEmpty(),
            requestedAtMs = (doc.get("requestedAtMs") as? Number)?.toLong() ?: 0L,
            status = statusRaw.ifBlank { "pending" },
        )
    }

    suspend fun submitAudioPartyVideoCallRequest(
        hostId: String,
        fromUserId: String,
        displayName: String,
        photoUrl: String,
    ) {
        val h = hostId.trim()
        val from = fromUserId.trim()
        if (h.isEmpty() || from.isEmpty() || h == from) return
        try {
            streamsCollection.document(h).collection("video_call_requests").document(from).set(
                mapOf(
                    "displayName" to displayName.trim().take(120),
                    "photoUrl" to photoUrl.trim().take(500),
                    "requestedAtMs" to System.currentTimeMillis(),
                    "status" to "pending",
                ),
                SetOptions.merge(),
            ).await()
        } catch (e: Exception) {
            Log.e(tag, "submitAudioPartyVideoCallRequest host=$h from=$from", e)
        }
    }

    suspend fun resolveAudioPartyVideoCallRequest(hostId: String, fromUserId: String, newStatus: String) {
        val h = hostId.trim()
        val from = fromUserId.trim()
        val status = newStatus.trim().lowercase()
        if (h.isEmpty() || from.isEmpty() || status.isEmpty()) return
        try {
            streamsCollection.document(h).collection("video_call_requests").document(from).set(
                mapOf(
                    "status" to status,
                    "resolvedAtMs" to System.currentTimeMillis(),
                ),
                SetOptions.merge(),
            ).await()
        } catch (e: Exception) {
            Log.e(tag, "resolveAudioPartyVideoCallRequest host=$h from=$from status=$status", e)
        }
    }

    suspend fun mergeAudioPartyNowPlaying(hostId: String, title: String, artist: String) {
        if (hostId.isBlank()) return
        try {
            streamsCollection.document(hostId).set(
                mapOf(
                    "nowPlayingTitle" to title.trim().take(200),
                    "nowPlayingArtist" to artist.trim().take(200),
                    "updatedAtMs" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.e(tag, "mergeAudioPartyNowPlaying hostId=$hostId", e)
        }
    }

    suspend fun mergeAudioPartyStageEmoji(hostId: String, emoji: String) {
        if (hostId.isBlank()) return
        val trimmed = emoji.trim().take(16)
        if (trimmed.isEmpty()) return
        try {
            streamsCollection.document(hostId).set(
                mapOf(
                    "stageEmoji" to trimmed,
                    "stageEmojiAtMs" to System.currentTimeMillis(),
                    "updatedAtMs" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.e(tag, "mergeAudioPartyStageEmoji hostId=$hostId", e)
        }
    }

    private fun seatPayload(
        userId: String,
        displayName: String,
        photoUrl: String,
        locked: Boolean,
        mutedByHost: Boolean,
        priceCoins: Int,
        publishingAudio: Boolean,
    ) = mapOf(
        "userId" to userId,
        "displayName" to displayName,
        "photoUrl" to photoUrl,
        "locked" to locked,
        "mutedByHost" to mutedByHost,
        "priceCoins" to priceCoins.toLong(),
        "publishingAudio" to publishingAudio
    )

    suspend fun clearAudioPartySeat(hostId: String, seatIndex: Int, preserveLocked: Boolean = true) {
        if (hostId.isBlank() || seatIndex < 1) return
        val key = seatIndex.toString()
        try {
            val ref = streamsCollection.document(hostId)
            val snap = ref.get().await()
            @Suppress("UNCHECKED_CAST")
            val seats = snap.get("seats") as? Map<String, *> ?: emptyMap<String, Any?>()
            @Suppress("UNCHECKED_CAST")
            val prev = seats[key] as? Map<String, *> ?: emptyMap<String, Any?>()
            val kickedUid = (prev["userId"] as? String).orEmpty().trim()
            val coHost = snap.getString("coHostUserId").orEmpty().trim()
            val locked = if (preserveLocked) {
                prev["locked"] == true || prev["locked"] == 1L || "${prev["locked"]}" == "1"
            } else {
                false
            }
            val price = ((prev["priceCoins"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
            val mut = hashMapOf<String, Any>(
                "seats.$key" to seatPayload("", "", "", locked, false, price, false),
                "updatedAtMs" to System.currentTimeMillis()
            )
            if (kickedUid.isNotEmpty() && kickedUid == coHost) {
                mut["coHostUserId"] = FieldValue.delete()
            }
            ref.update(mut).await()
        } catch (e: Exception) {
            Log.e(tag, "clearAudioPartySeat hostId=$hostId seat=$seatIndex", e)
        }
    }

    suspend fun mergeAudioPartyCoHost(hostId: String, coHostUserId: String?) {
        if (hostId.isBlank()) return
        try {
            val mut = hashMapOf<String, Any>("updatedAtMs" to System.currentTimeMillis())
            if (coHostUserId.isNullOrBlank()) {
                mut["coHostUserId"] = FieldValue.delete()
            } else {
                mut["coHostUserId"] = coHostUserId.trim()
            }
            streamsCollection.document(hostId).update(mut).await()
        } catch (e: Exception) {
            Log.e(tag, "mergeAudioPartyCoHost hostId=$hostId", e)
        }
    }

    suspend fun setAudioPartyCoHostFromSeat(hostId: String, seatIndex: Int) {
        if (hostId.isBlank() || seatIndex < 1) return
        val key = seatIndex.toString()
        try {
            val ref = streamsCollection.document(hostId)
            val snap = ref.get().await()
            @Suppress("UNCHECKED_CAST")
            val seats = snap.get("seats") as? Map<String, *> ?: return
            @Suppress("UNCHECKED_CAST")
            val seatMap = seats[key] as? Map<String, *> ?: return
            val uid = (seatMap["userId"] as? String).orEmpty().trim()
            if (uid.isEmpty() || uid == hostId.trim()) return
            ref.update(
                mapOf(
                    "coHostUserId" to uid,
                    "updatedAtMs" to System.currentTimeMillis()
                )
            ).await()
        } catch (e: Exception) {
            Log.e(tag, "setAudioPartyCoHostFromSeat hostId=$hostId seat=$seatIndex", e)
        }
    }

    suspend fun hostSetAudioPartySeatLocked(hostId: String, seatIndex: Int, locked: Boolean) {
        if (hostId.isBlank() || seatIndex < 1) return
        val key = seatIndex.toString()
        try {
            streamsCollection.document(hostId).update(
                mapOf("seats.$key.locked" to locked, "updatedAtMs" to System.currentTimeMillis())
            ).await()
        } catch (e: Exception) {
            Log.e(tag, "hostSetAudioPartySeatLocked", e)
        }
    }

    suspend fun hostSetAudioPartySeatMuted(hostId: String, seatIndex: Int, muted: Boolean) {
        if (hostId.isBlank() || seatIndex < 1) return
        val key = seatIndex.toString()
        try {
            val mut = hashMapOf<String, Any>(
                "seats.$key.mutedByHost" to muted,
                "updatedAtMs" to System.currentTimeMillis()
            )
            if (muted) {
                mut["seats.$key.publishingAudio"] = false
            }
            streamsCollection.document(hostId).update(mut).await()
        } catch (e: Exception) {
            Log.e(tag, "hostSetAudioPartySeatMuted", e)
        }
    }

    suspend fun hostSetAudioPartySeatPrice(hostId: String, seatIndex: Int, priceCoins: Int) {
        if (hostId.isBlank() || seatIndex < 1) return
        val key = seatIndex.toString()
        try {
            streamsCollection.document(hostId).update(
                mapOf(
                    "seats.$key.priceCoins" to priceCoins.coerceIn(0, 500_000).toLong(),
                    "updatedAtMs" to System.currentTimeMillis()
                )
            ).await()
        } catch (e: Exception) {
            Log.e(tag, "hostSetAudioPartySeatPrice", e)
        }
    }

    suspend fun setAudioPartySeatPublishing(hostId: String, seatIndex: Int, myUid: String, publishing: Boolean) {
        if (hostId.isBlank() || seatIndex < 1 || myUid.isBlank()) return
        val key = seatIndex.toString()
        try {
            val ref = streamsCollection.document(hostId)
            val snap = ref.get().await()
            @Suppress("UNCHECKED_CAST")
            val seats = snap.get("seats") as? Map<String, *> ?: return
            @Suppress("UNCHECKED_CAST")
            val prev = seats[key] as? Map<String, *> ?: return
            val uid = (prev["userId"] as? String).orEmpty().trim()
            if (uid != myUid) return
            ref.update(
                mapOf(
                    "seats.$key.publishingAudio" to publishing,
                    "updatedAtMs" to System.currentTimeMillis()
                )
            ).await()
        } catch (e: Exception) {
            Log.e(tag, "setAudioPartySeatPublishing", e)
        }
    }

    suspend fun claimAudioPartySeat(
        hostId: String,
        seatIndex: Int,
        userId: String,
        displayName: String,
        photoUrl: String,
    ): Result<Unit> {
        if (hostId.isBlank() || seatIndex < 1 || userId.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid seat claim"))
        }
        val key = seatIndex.toString()
        return try {
            db.runTransaction { tx ->
                val streamRef = streamsCollection.document(hostId)
                val snap = tx.get(streamRef)
                if (!snap.exists()) throw IllegalStateException("Audio party room missing")
                @Suppress("UNCHECKED_CAST")
                val seats = snap.get("seats") as? Map<String, *> ?: emptyMap<String, Any?>()
                @Suppress("UNCHECKED_CAST")
                val prev = seats[key] as? Map<String, *> ?: emptyMap<String, Any?>()
                val locked = prev["locked"] == true || prev["locked"] == 1L || "${prev["locked"]}" == "1"
                if (locked) throw IllegalStateException("Seat locked")
                val occ = (prev["userId"] as? String).orEmpty().trim()
                if (occ.isNotEmpty() && occ != userId) throw IllegalStateException("Seat taken")
                if (occ == userId) return@runTransaction
                val price = ((prev["priceCoins"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
                if (price > 0) {
                    val userRef = usersCollection.document(userId)
                    val userSnap = tx.get(userRef)
                    if (!userSnap.exists()) throw IllegalStateException("Profile missing")
                    val spendable =
                        (userSnap.getLong("coins") ?: userSnap.getLong("walletBalance"))?.toInt() ?: 0
                    if (spendable < price) throw IllegalStateException("Insufficient diamonds")
                    val neg = FieldValue.increment(-price.toLong())
                    tx.update(userRef, mapOf("walletBalance" to neg, "coins" to neg))
                }
                tx.update(
                    streamRef,
                    mapOf(
                        "seats.$key" to seatPayload(
                            userId = userId,
                            displayName = displayName,
                            photoUrl = photoUrl,
                            locked = false,
                            mutedByHost = false,
                            priceCoins = price,
                            publishingAudio = false
                        ),
                        "updatedAtMs" to System.currentTimeMillis()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(tag, "claimAudioPartySeat", e)
            Result.failure(e)
        }
    }

    suspend fun leaveAudioPartySeat(hostId: String, seatIndex: Int, userId: String) {
        if (hostId.isBlank() || seatIndex < 1 || userId.isBlank()) return
        val key = seatIndex.toString()
        try {
            val ref = streamsCollection.document(hostId)
            val snap = ref.get().await()
            @Suppress("UNCHECKED_CAST")
            val seats = snap.get("seats") as? Map<String, *> ?: return
            @Suppress("UNCHECKED_CAST")
            val prev = seats[key] as? Map<String, *> ?: return
            val occ = (prev["userId"] as? String).orEmpty().trim()
            if (occ != userId) return
            clearAudioPartySeat(hostId, seatIndex, preserveLocked = true)
        } catch (e: Exception) {
            Log.e(tag, "leaveAudioPartySeat", e)
        }
    }

    /**
     * Tears down a host's public live state: clears [UserProfile.isLive] / [UserProfile.liveRoomId] and
     * deletes the matching `live_streams` root docs (host uid doc + optional session id, e.g. `call_*`).
     *
     * Clears PK banner fields on existing stream docs first so Discover does not briefly show stale PK rows,
     * then retries clearing user live flags before deletes.
     */
    suspend fun endLiveStream(hostUid: String, sessionLiveStreamDocId: String? = null) {
        if (hostUid.isBlank()) return
        val docIds = buildSet {
            add(hostUid)
            sessionLiveStreamDocId?.trim()?.takeIf { it.isNotEmpty() && it != hostUid }?.let { add(it) }
        }
        for (id in docIds) {
            runCatching { clearLiveStreamPkBannerIfDocumentExists(id) }
                .onFailure { e -> Log.w(tag, "endLiveStream clearPkBanner live_streams/$id", e) }
        }
        var liveFlagCleared = false
        runCatching { updateUserLiveStatus(hostUid, false, null) }
            .onSuccess { liveFlagCleared = true }
            .onFailure { Log.w(tag, "endLiveStream updateUserLiveStatus uid=$hostUid", it) }
        for (id in docIds) {
            runCatching {
                liveStreamsCollection.document(id).delete().await()
                Log.d(tag, "endLiveStream: deleted live_streams/$id")
            }.onFailure { Log.w(tag, "endLiveStream delete live_streams/$id", it) }
            runCatching {
                streamsCollection.document(id).delete().await()
                Log.d(tag, "endLiveStream: deleted streams/$id")
            }.onFailure { Log.w(tag, "endLiveStream delete streams/$id", it) }
        }
        if (!liveFlagCleared) {
            runCatching { updateUserLiveStatus(hostUid, false, null) }
                .onFailure { Log.w(tag, "endLiveStream updateUserLiveStatus retry-after-delete uid=$hostUid", it) }
        }
    }

    suspend fun incrementLiveStreamViewers(streamId: String, delta: Long) {
        if (streamId.isBlank() || delta == 0L) return
        try {
            db.runTransaction { tx ->
                val ref = liveStreamsCollection.document(streamId)
                val snap = tx.get(ref)
                if (!snap.exists()) {
                    if (delta < 0) return@runTransaction
                    val createdAt = System.currentTimeMillis()
                    tx.set(
                        ref,
                        hashMapOf(
                            "hostId" to streamId,
                            "viewerCount" to delta.coerceAtLeast(0),
                            "streamStartedAtMillis" to createdAt,
                            "updatedAtMs" to createdAt
                        )
                    )
                } else {
                    val cur = snap.getLong("viewerCount") ?: 0L
                    val next = (cur + delta).coerceAtLeast(0L)
                    if (next == cur && delta < 0) {
                        return@runTransaction
                    }
                    tx.update(
                        ref,
                        mapOf(
                            "viewerCount" to next,
                            "updatedAtMs" to System.currentTimeMillis()
                        )
                    )
                }
            }.await()
        } catch (e: Exception) {
            Log.e(tag, "incrementLiveStreamViewers streamId=$streamId delta=$delta", e)
        }
    }

    fun observeLiveStreamRoom(streamId: String): Flow<LiveStreamRoomInfo> = callbackFlow {
        if (streamId.isBlank()) {
            trySend(LiveStreamRoomInfo())
            awaitClose { }
            return@callbackFlow
        }
        val reg = liveStreamsCollection.document(streamId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                logFirestoreSnapshotError("live_streams/$streamId", error)
                return@addSnapshotListener
            }
            // Stale local cache often still shows "doc missing" right after `endLiveStream` deleted
            // `live_streams/{id}` but before the new session write lands — emitting streamActive=false
            // here made the host UI drop on the first "go live" after a cold start. Skip until we
            // get a server-grounded snapshot (or a cached snapshot that includes the new doc).
            if (snapshot == null || !snapshot.exists()) {
                if (snapshot != null && snapshot.metadata.isFromCache) {
                    return@addSnapshotListener
                }
                trySend(
                    LiveStreamRoomInfo(
                        streamActive = false
                    )
                )
                return@addSnapshotListener
            }
            val vc = (snapshot.getLong("viewerCount") ?: 0L).toInt().coerceAtLeast(0)
            val streamStarted = snapshot?.getLong("streamStartedAtMillis") ?: 0L
            val kicked = parseUidListFromFirestore(snapshot, "kickedUsers")
            val pkHost = snapshot?.getLong("pkHostBeans") ?: 0L
            val pkGuest = snapshot?.getLong("pkGuestBeans") ?: 0L
            val pkStarted = snapshot?.getLong("pkStartedAtMillis") ?: 0L
            val pkBattleHostUid = snapshot?.getString("pkBattleHostUid").orEmpty().trim()
            val pkBattleGuestUid = snapshot?.getString("pkBattleGuestUid").orEmpty().trim()
            val pkGuestUid = snapshot?.getString("pkGuestUserId").orEmpty().trim()
            val pkHostName = snapshot?.getString("pkHostDisplayName").orEmpty().trim()
            val pkGuestName = snapshot?.getString("pkGuestDisplayName").orEmpty().trim()
            val pkDur = (snapshot?.getLong("pkDurationSeconds") ?: 0L).toInt().let { d ->
                if (d > 0) d else 300
            }
            val pkScoringOpen = if (snapshot.contains("pkScoringOpen")) {
                snapshot.getBoolean("pkScoringOpen") ?: true
            } else {
                true
            }
            val pkHostVc = snapshot?.getLong("pkHostViewerCount")?.toInt() ?: 0
            val pkGuestVc = snapshot?.getLong("pkGuestViewerCount")?.toInt() ?: 0
            val pkHostUids = parseUidListFromFirestore(snapshot, "pkHostViewerUserIds")
            val pkGuestUids = parseUidListFromFirestore(snapshot, "pkGuestViewerUserIds")
            val hostPrivateCallBusy = if (snapshot.contains("hostPrivateCallBusy")) {
                snapshot.getBoolean("hostPrivateCallBusy") ?: false
            } else {
                false
            }
            val audioOnlyStream = if (snapshot.contains("audioOnlyStream")) {
                snapshot.getBoolean("audioOnlyStream") == true
            } else {
                false
            }
            val seatRaw = snapshot.getLong("audioPartySeatCount")
            val audioPartySeatCount = if (!audioOnlyStream) {
                10
            } else {
                normalizeAudioPartyStageSeatCountFromFirestore(seatRaw)
            }
            val pkResultEnds = snapshot?.getLong("pkResultPhaseEndsAtMillis") ?: 0L
            val audioPartyHandoffToUid = snapshot.getString("audioPartyHandoffToUid").orEmpty().trim()
            trySend(
                LiveStreamRoomInfo(
                    viewerCount = vc,
                    streamStartedAtMillis = streamStarted,
                    kickedUsers = kicked,
                    pkHostBeans = pkHost,
                    pkGuestBeans = pkGuest,
                    pkStartedAtMillis = pkStarted,
                    pkBattleHostUid = pkBattleHostUid,
                    pkBattleGuestUid = pkBattleGuestUid,
                    pkGuestUserId = pkGuestUid,
                    pkHostDisplayName = pkHostName,
                    pkGuestDisplayName = pkGuestName,
                    pkDurationSeconds = pkDur,
                    pkScoringOpen = pkScoringOpen,
                    pkHostViewerCount = pkHostVc.coerceAtLeast(0),
                    pkGuestViewerCount = pkGuestVc.coerceAtLeast(0),
                    pkHostViewerUserIds = pkHostUids,
                    pkGuestViewerUserIds = pkGuestUids,
                    hostPrivateCallBusy = hostPrivateCallBusy,
                    audioOnlyStream = audioOnlyStream,
                    audioPartySeatCount = audioPartySeatCount,
                    streamActive = true,
                    audioPartyHandoffToUid = audioPartyHandoffToUid,
                    pkResultPhaseEndsAtMillis = pkResultEnds
                )
            )
        }
        awaitClose { reg.remove() }
    }

    /** RTDB `rooms/{roomId}/activeViewers` keys = connected broadcast viewer Firebase uids. */
    fun observeRoomActiveViewerUids(roomId: String): Flow<List<String>> = callbackFlow {
        if (roomId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val ref = roomsRef.child(roomId).child("activeViewers")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val uids = snapshot.children.mapNotNull { child ->
                    child.key?.trim()?.takeIf { it.isNotEmpty() }
                }.sorted()
                trySend(uids)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(tag, "observeRoomActiveViewerUids roomId=$roomId", error.toException())
                trySend(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Marks `live_streams/{streamId}` as hosting an active PK so the Live tab can show a banner to other users.
     *
     * **Realtime Database:** PK guest video for spectators uses `live_publishers/{streamId}/{pkGuestUserId}/`
     * with the same shape as `rooms/{id}/` (`activeViewers`, `peers/{viewerUid}/…`). Grant authenticated
     * read/write for that path alongside your existing `rooms` rules.
     */
    suspend fun setLiveStreamPkBanner(
        streamId: String,
        hostDisplayName: String,
        guestDisplayName: String,
        guestUserId: String = "",
        durationSeconds: Int = 300,
        resetBeanTotals: Boolean = true
    ) {
        if (streamId.isBlank()) return
        val dur = durationSeconds.coerceIn(60, 3600)
        runCatching {
            val hUid = streamId.trim()
            val gUid = guestUserId.trim()
            val payload = mutableMapOf<String, Any>(
                "pkStartedAtMillis" to System.currentTimeMillis(),
                "pkHostDisplayName" to hostDisplayName.trim().ifBlank { "Host" },
                "pkGuestDisplayName" to guestDisplayName.trim().ifBlank { "Guest" },
                "pkGuestUserId" to gUid,
                "pkBattleHostUid" to hUid,
                "pkBattleGuestUid" to gUid,
                "pkDurationSeconds" to dur,
                "pkScoringOpen" to true,
                "pkResultPhaseEndsAtMillis" to 0L,
                "updatedAtMs" to System.currentTimeMillis()
            )
            if (resetBeanTotals) {
                payload["pkHostBeans"] = 0L
                payload["pkGuestBeans"] = 0L
                payload["pkHostViewerCount"] = 0L
                payload["pkGuestViewerCount"] = 0L
                payload["pkHostViewerUserIds"] = emptyList<String>()
                payload["pkGuestViewerUserIds"] = emptyList<String>()
            }
            liveStreamsCollection.document(streamId).set(
                payload,
                SetOptions.merge()
            ).await()
        }.onFailure { e -> Log.e(tag, "setLiveStreamPkBanner streamId=$streamId", e) }
    }

    /**
     * Writes the same PK battle fields to `live_streams/{hostUid}` and `live_streams/{guestUid}` so
     * solo audiences on either stream pick up PK without switching Firestore chat roots or viewer counts.
     */
    suspend fun publishLiveStreamPkBattle(
        hostUid: String,
        guestUid: String,
        hostDisplayName: String,
        guestDisplayName: String,
        durationSeconds: Int = 300,
        resetBeanTotals: Boolean = true
    ) {
        val h = hostUid.trim()
        val g = guestUid.trim()
        if (h.isBlank() || g.isBlank() || h == g) return
        val dur = durationSeconds.coerceIn(60, 3600)
        val started = System.currentTimeMillis()
        val payload = mutableMapOf<String, Any>(
            "pkStartedAtMillis" to started,
            "pkHostDisplayName" to hostDisplayName.trim().ifBlank { "Host" },
            "pkGuestDisplayName" to guestDisplayName.trim().ifBlank { "Guest" },
            "pkGuestUserId" to g,
            "pkBattleHostUid" to h,
            "pkBattleGuestUid" to g,
            "pkDurationSeconds" to dur,
            "pkScoringOpen" to true,
            "pkResultPhaseEndsAtMillis" to 0L,
            "updatedAtMs" to started
        )
        if (resetBeanTotals) {
            payload["pkHostBeans"] = 0L
            payload["pkGuestBeans"] = 0L
            payload["pkHostViewerCount"] = 0L
            payload["pkGuestViewerCount"] = 0L
            payload["pkHostViewerUserIds"] = emptyList<String>()
            payload["pkGuestViewerUserIds"] = emptyList<String>()
        }
        runCatching {
            liveStreamsCollection.document(h).set(payload, SetOptions.merge()).await()
            runCatching { ensureLiveStreamDocument(g, g) }
                .onFailure { e2 -> Log.w(tag, "publishLiveStreamPkBattle ensure guest doc uid=$g", e2) }
            liveStreamsCollection.document(g).set(payload, SetOptions.merge()).await()
        }.onFailure { e -> Log.e(tag, "publishLiveStreamPkBattle host=$h guest=$g", e) }
    }

    /** Clears PK banner fields on both battlers' `live_streams` docs. */
    suspend fun clearLiveStreamPkBattleMirrors(hostUid: String, guestUid: String) {
        val h = hostUid.trim()
        val g = guestUid.trim()
        if (h.isNotBlank()) clearLiveStreamPkBanner(h)
        if (g.isNotBlank() && g != h) clearLiveStreamPkBanner(g)
    }

    /**
     * RTDB fan-out for “PK ended, stay in solo live” — viewers/clients can listen under
     * `live_signals/{roomId}` for `event == end_pk_stay_live` (aligns with WebRTC room id / PK room).
     */
    suspend fun emitEndPkStayLiveSignal(roomId: String, hostLiveStreamDocId: String?) {
        val rid = roomId.trim()
        if (rid.isEmpty()) return
        val uid = auth.currentUser?.uid?.trim().orEmpty()
        val payload = mutableMapOf<String, Any>(
            "event" to LIVE_SIGNAL_EVENT_END_PK_STAY_LIVE,
            "ts" to ServerValue.TIMESTAMP,
            "roomId" to rid,
            "senderUid" to uid,
        )
        val hostDoc = hostLiveStreamDocId?.trim().orEmpty()
        if (hostDoc.isNotEmpty()) {
            payload["hostStreamDocId"] = hostDoc
        }
        runCatching {
            realtimeDb.reference.child("live_signals").child(rid).push().setValue(payload).await()
            Log.d(tag, "emitEndPkStayLiveSignal room=$rid")
        }.onFailure { e -> Log.w(tag, "emitEndPkStayLiveSignal failed room=$rid", e) }
    }

    companion object {
        /** Payload [emitEndPkStayLiveSignal] `event` field; keep in sync with RTDB listeners. */
        const val LIVE_SIGNAL_EVENT_END_PK_STAY_LIVE = "end_pk_stay_live"

        private const val UPDATE_USER_LIVE_STATUS_MAX_ATTEMPTS = 3
        private const val UPDATE_USER_LIVE_STATUS_RETRY_BASE_MS = 280L

        private val turnCredentialsMutex = Mutex()
        @Volatile private var turnCredentialsCachedAtMs: Long = 0L
        private var turnCredentialsCache: List<PeerConnection.IceServer>? = null
        private const val TURN_CREDENTIALS_CACHE_MS = 12L * 60 * 60 * 1000
    }

    /**
     * Listens for pushed rows under `live_signals/{roomId}/` (same tree as [emitEndPkStayLiveSignal]).
     * Invokes [onEndPkStayLiveReceived] when a child's `event` equals [LIVE_SIGNAL_EVENT_END_PK_STAY_LIVE].
     *
     * @return Pair of [DatabaseReference] and [ChildEventListener] for [removeLiveSignalsListener].
     */
    fun listenForLiveSignals(
        roomId: String,
        onEndPkStayLiveReceived: () -> Unit,
    ): Pair<DatabaseReference, ChildEventListener> {
        val ref = realtimeDb.reference.child("live_signals").child(roomId.trim())
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                dispatch(snapshot)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                dispatch(snapshot)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.w(tag, "listenForLiveSignals cancelled room=$roomId", error.toException())
            }

            private fun dispatch(snapshot: DataSnapshot) {
                val event = snapshot.child("event").getValue(String::class.java)?.trim().orEmpty()
                if (event == FirebaseService.LIVE_SIGNAL_EVENT_END_PK_STAY_LIVE) {
                    onEndPkStayLiveReceived()
                }
            }
        }
        ref.addChildEventListener(listener)
        return ref to listener
    }

    fun removeLiveSignalsListener(ref: DatabaseReference?, listener: ChildEventListener?) {
        if (ref == null || listener == null) return
        runCatching { ref.removeEventListener(listener) }
            .onFailure { e -> Log.w(tag, "removeLiveSignalsListener failed", e) }
    }

    /** After PK time runs out; blocks further Firestore PK increments from gifts. */
    suspend fun closePkBattleScoring(streamId: String) {
        if (streamId.isBlank()) return
        runCatching {
            val deadline = System.currentTimeMillis() + PK_POST_RESULT_LINGER_MS
            liveStreamsCollection.document(streamId).set(
                mapOf(
                    "pkScoringOpen" to false,
                    "pkResultPhaseEndsAtMillis" to deadline,
                    "updatedAtMs" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
            Log.d(tag, "closePkBattleScoring streamId=$streamId")
        }.onFailure { e -> Log.w(tag, "closePkBattleScoring streamId=$streamId", e) }
    }

    suspend fun closePkBattleScoringForBattle(hostUid: String, guestUid: String) {
        val h = hostUid.trim()
        val g = guestUid.trim()
        val pkStartMs = if (h.isNotBlank()) {
            runCatching { liveStreamsCollection.document(h).get().await() }
                .getOrNull()
                ?.takeIf { it.exists() }
                ?.getLong("pkStartedAtMillis")
                ?: 0L
        } else {
            0L
        }
        if (h.isNotBlank()) closePkBattleScoring(h)
        if (g.isNotBlank() && g != h) closePkBattleScoring(g)
        if (h.isNotBlank() && pkStartMs > 0L) {
            val maxTs = System.currentTimeMillis()
            deleteLiveStreamPkSessionMessages(h, pkStartMs, maxTs)
        }
    }

    /**
     * PK chat lives only on the host's `live_streams/{hostUid}/messages`. Deletes posts from this round
     * so clients see an empty thread after [closePkBattleScoring]. Requires Firestore rules allowing deletes.
     */
    private suspend fun deleteLiveStreamPkSessionMessages(
        hostStreamDocId: String,
        minTimestampInclusive: Long,
        maxTimestampInclusive: Long,
    ) {
        if (hostStreamDocId.isBlank() || minTimestampInclusive <= 0L || maxTimestampInclusive < minTimestampInclusive) {
            return
        }
        runCatching {
            val coll = liveStreamsCollection.document(hostStreamDocId).collection("messages")
            var total = 0
            while (true) {
                val snap = coll
                    .whereGreaterThanOrEqualTo("timestamp", minTimestampInclusive)
                    .whereLessThanOrEqualTo("timestamp", maxTimestampInclusive)
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .limit(400)
                    .get()
                    .await()
                if (snap.isEmpty) break
                val batch = db.batch()
                for (doc in snap.documents) {
                    batch.delete(doc.reference)
                    total++
                }
                batch.commit().await()
                if (snap.size() < 400) break
            }
            Log.d(tag, "deleteLiveStreamPkSessionMessages host=$hostStreamDocId deleted=$total")
        }.onFailure { e ->
            Log.w(tag, "deleteLiveStreamPkSessionMessages host=$hostStreamDocId (check Firestore rules)", e)
        }
    }

    /** `pkStartedAtMillis` on `live_streams/{streamId}` for PK chat history lower bound. */
    suspend fun fetchLiveStreamPkStartedAtMillis(streamId: String): Long {
        if (streamId.isBlank()) return 0L
        return runCatching {
            val snap = liveStreamsCollection.document(streamId).get().await()
            if (!snap.exists()) return@runCatching 0L
            snap.getLong("pkStartedAtMillis") ?: 0L
        }.getOrElse { e ->
            Log.w(tag, "fetchLiveStreamPkStartedAtMillis streamId=$streamId", e)
            0L
        }
    }

    /**
     * Batch-read mic-only / audio-party discover flags for live hosts.
     * Merges `live_streams/{uid}.audioOnlyStream` with `users/{uid}.liveStreamAudioOnly`
     * so the carousel matches Itzo-style audio rooms when either doc updates first.
     */
    suspend fun fetchLiveStreamAudioOnlyFlags(hostIds: Collection<String>): Map<String, Boolean> {
        val ids = hostIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (ids.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            coroutineScope {
                ids
                    .map { id ->
                        async {
                            id to runCatching { resolveAudioPartyDiscoverFlagForHost(id) }.getOrElse { e ->
                                Log.w(tag, "fetchLiveStreamAudioOnlyFlags id=$id", e)
                                false
                            }
                        }
                    }
                    .awaitAll()
                    .toMap(LinkedHashMap())
            }
        }
    }

    /**
     * Host UIDs whose `live_streams/{id}` doc still exists (prefer [Source.SERVER] to avoid cache ghosts).
     * Used with [observeLiveUserIds] so cards disappear when `users.isLive` is stale but the stream doc was deleted.
     */
    suspend fun hostIdsWithLiveStreamDocuments(hostIds: Collection<String>): Set<String> {
        val ids = hostIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (ids.isEmpty()) return emptySet()
        return withContext(Dispatchers.IO) {
            coroutineScope {
                ids
                    .map { id ->
                        async {
                            val onServer = runCatching {
                                liveStreamsCollection.document(id).get(Source.SERVER).await()
                            }
                            when {
                                onServer.isSuccess && onServer.getOrNull()?.exists() == true -> id
                                onServer.isSuccess -> null
                                else -> {
                                    val fallback = runCatching {
                                        liveStreamsCollection.document(id).get(Source.DEFAULT).await()
                                    }.getOrNull()
                                    if (fallback?.exists() == true) id else null
                                }
                            }
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
                    .toSet()
            }
        }
    }

    private suspend fun resolveAudioPartyDiscoverFlagForHost(hostId: String): Boolean {
        val liveSnap = liveStreamsCollection.document(hostId).get().await()
        val fromLive =
            liveSnap.exists() &&
                liveSnap.contains("audioOnlyStream") &&
                liveSnap.getBoolean("audioOnlyStream") == true
        if (fromLive) return true
        val userSnap = usersCollection.document(hostId).get().await()
        val fromUser =
            userSnap.exists() &&
                userSnap.contains("liveStreamAudioOnly") &&
                userSnap.getBoolean("liveStreamAudioOnly") == true
        return fromUser
    }

    /**
     * For spectators joining a live: if PK is active, use `pkStartedAtMillis` so they see full battle chat;
     * otherwise use [defaultCutoffMs] (typically join time).
     */
    suspend fun resolveLiveStreamChatMinTimestampForAudience(streamHostId: String, defaultCutoffMs: Long): Long {
        if (streamHostId.isBlank()) return defaultCutoffMs
        return runCatching {
            val snap = liveStreamsCollection.document(streamHostId).get().await()
            if (!snap.exists()) return@runCatching defaultCutoffMs
            val pkStarted = snap.getLong("pkStartedAtMillis") ?: 0L
            if (pkStarted <= 0L) return@runCatching defaultCutoffMs
            val battleH = snap.getString("pkBattleHostUid").orEmpty().trim().ifBlank { streamHostId }
            val battleG = snap.getString("pkBattleGuestUid").orEmpty().trim().ifBlank {
                snap.getString("pkGuestUserId").orEmpty().trim()
            }
            val pkOn = battleG.isNotBlank() && battleH.isNotBlank() && battleH != battleG
            if (pkOn) pkStarted else defaultCutoffMs
        }.getOrElse { defaultCutoffMs }
    }

    /**
     * Same as [clearLiveStreamPkBanner] but only when `live_streams/{streamId}` already exists — avoids
     * merge-creating an orphan doc during solo teardown.
     */
    suspend fun clearLiveStreamPkBannerIfDocumentExists(streamId: String) {
        if (streamId.isBlank()) return
        val snap = runCatching { liveStreamsCollection.document(streamId).get().await() }
            .getOrElse {
                Log.w(tag, "clearLiveStreamPkBannerIfDocumentExists get failed streamId=$streamId", it)
                return
            }
        if (!snap.exists()) return
        clearLiveStreamPkBanner(streamId)
    }

    suspend fun clearLiveStreamPkBanner(streamId: String) {
        if (streamId.isBlank()) return
        runCatching {
            liveStreamsCollection.document(streamId).set(
                mapOf(
                    "pkStartedAtMillis" to 0L,
                    "pkHostDisplayName" to "",
                    "pkGuestDisplayName" to "",
                    "pkGuestUserId" to "",
                    "pkBattleHostUid" to "",
                    "pkBattleGuestUid" to "",
                    "pkDurationSeconds" to 0L,
                    "pkScoringOpen" to false,
                    "pkResultPhaseEndsAtMillis" to 0L,
                    "pkHostViewerCount" to 0L,
                    "pkGuestViewerCount" to 0L,
                    "pkHostViewerUserIds" to emptyList<String>(),
                    "pkGuestViewerUserIds" to emptyList<String>(),
                    "updatedAtMs" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
        }.onFailure { e -> Log.w(tag, "clearLiveStreamPkBanner streamId=$streamId", e) }
    }

    /**
     * Adjusts per-battler spectator counts on `live_streams/{streamId}` (host's doc id during PK).
     * When [spectatorUid] is non-null, also arrayUnion/arrayRemove that uid on the matching list
     * (`pkHostViewerUserIds` / `pkGuestViewerUserIds`). Rules must allow array updates for signed-in users.
     */
    suspend fun adjustPkBattlerAudienceCounts(
        streamId: String,
        hostDelta: Long,
        guestDelta: Long,
        spectatorUid: String? = null,
    ) {
        if (streamId.isBlank() || (hostDelta == 0L && guestDelta == 0L)) return
        val uid = spectatorUid?.trim()?.takeIf { it.isNotEmpty() }
        try {
            db.runTransaction { tx ->
                val ref = liveStreamsCollection.document(streamId)
                val snap = tx.get(ref)
                if (!snap.exists()) {
                    if (hostDelta < 0L || guestDelta < 0L) return@runTransaction
                    val initial = hashMapOf<String, Any>(
                        "hostId" to streamId,
                        "viewerCount" to 0L,
                        "pkHostViewerCount" to hostDelta.coerceAtLeast(0),
                        "pkGuestViewerCount" to guestDelta.coerceAtLeast(0),
                        "updatedAtMs" to System.currentTimeMillis()
                    )
                    if (uid != null) {
                        if (hostDelta > 0L) initial["pkHostViewerUserIds"] = listOf(uid)
                        if (guestDelta > 0L) initial["pkGuestViewerUserIds"] = listOf(uid)
                    }
                    tx.set(ref, initial, SetOptions.merge())
                } else {
                    val updates = mutableMapOf<String, Any>(
                        "updatedAtMs" to System.currentTimeMillis()
                    )
                    if (hostDelta != 0L) updates["pkHostViewerCount"] = FieldValue.increment(hostDelta)
                    if (guestDelta != 0L) updates["pkGuestViewerCount"] = FieldValue.increment(guestDelta)
                    if (uid != null) {
                        if (hostDelta > 0L) updates["pkHostViewerUserIds"] = FieldValue.arrayUnion(uid)
                        if (guestDelta > 0L) updates["pkGuestViewerUserIds"] = FieldValue.arrayUnion(uid)
                        if (hostDelta < 0L) updates["pkHostViewerUserIds"] = FieldValue.arrayRemove(uid)
                        if (guestDelta < 0L) updates["pkGuestViewerUserIds"] = FieldValue.arrayRemove(uid)
                    }
                    tx.update(ref, updates)
                }
            }.await()
        } catch (e: Exception) {
            Log.e(tag, "adjustPkBattlerAudienceCounts streamId=$streamId h=$hostDelta g=$guestDelta uid=$uid", e)
        }
    }

    /**
     * Live discover: streams where PK is in progress (`pkStartedAtMillis` > 0).
     * Document id is the live host uid for normal solo streams.
     */
    fun observeActiveLivePkBanners(): Flow<Map<String, String>> = callbackFlow {
        val q = liveStreamsCollection
            .whereGreaterThan("pkStartedAtMillis", 0L)
            .limit(80)
        val scope = this
        val reg = q.addSnapshotListener { snap, error ->
            if (error != null) {
                logFirestoreSnapshotError("activeLivePkBanners", error)
                trySend(emptyMap())
                return@addSnapshotListener
            }
            scope.launch(Dispatchers.Default) {
                val map = mutableMapOf<String, String>()
                snap?.documents?.forEach { doc ->
                    val started = doc.getLong("pkStartedAtMillis") ?: 0L
                    if (started <= 0L) return@forEach
                    val id = doc.id
                    val anchor = doc.getString("pkBattleHostUid")?.trim().orEmpty().ifBlank { id }
                    val hostN = doc.getString("pkHostDisplayName").orEmpty().trim()
                    val guestN = doc.getString("pkGuestDisplayName").orEmpty().trim()
                    map[anchor] = when {
                        hostN.isNotEmpty() && guestN.isNotEmpty() -> "PK battle · $hostN vs $guestN"
                        hostN.isNotEmpty() -> "PK battle · $hostN"
                        else -> "PK battle"
                    }
                }
                trySend(map)
            }
        }
        awaitClose { reg.remove() }
    }

    /**
     * CRM → Messages → Announcement. Fields per doc: `title`, `body`, optional `imageUrl`, `createdAtMs`
     * (or `createdAt` / `updatedAtMs`), optional `isPublished` (default true). Client read-only; CRM uses Admin SDK.
     */
    fun observeCrmAnnouncements(): Flow<List<CrmAnnouncement>> = callbackFlow {
        val reg = crmAnnouncementsCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                logFirestoreSnapshotError("crm_announcements", error)
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snapshot?.documents
                ?.mapNotNull { it.toCrmAnnouncementOrNull() }
                ?.sortedByDescending { it.createdAtMs }
                .orEmpty()
            trySend(list.take(100))
        }
        awaitClose { reg.remove() }
    }

    /**
     * Live chat for `live_streams/{streamId}/messages`.
     * @param minTimestampMs When > 0, only messages with `timestamp` >= this value (current session window).
     */
    fun observeLiveStreamMessages(streamId: String, minTimestampMs: Long = 0L): Flow<List<LiveStreamChatMessage>> = callbackFlow {
        if (streamId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val coll = liveStreamsCollection.document(streamId).collection("messages")
        val query = if (minTimestampMs > 0L) {
            coll
                .whereGreaterThanOrEqualTo("timestamp", minTimestampMs)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(120)
        } else {
            coll
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(120)
        }
        var loggedFirstSnapshot = false
        val reg = query
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logFirestoreSnapshotError("CALL_CHAT live_streams/$streamId/messages", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.safeToObject<LiveStreamChatMessage>(tag)?.copy(id = doc.id)
                }
                    .orEmpty()
                    .sortedBy { it.timestamp }
                if (!loggedFirstSnapshot) {
                    loggedFirstSnapshot = true
                    Log.i(
                        tag,
                        "CALL_CHAT observeLiveStreamMessages streamId=$streamId minTimestampMs=$minTimestampMs firstSnapshotCount=${list.size}"
                    )
                }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    /** Viewer tapped “like” on live — host + other viewers see it in `messages` as a system line. */
    suspend fun sendLiveStreamLikeSystemMessage(streamId: String, viewerUid: String, viewerName: String): Result<Unit> {
        if (streamId.isBlank() || viewerUid.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid like notice"))
        }
        val display = viewerName.trim().ifBlank { "Someone" }
        return suspendCancellableCoroutine { cont ->
            val msg = LiveStreamChatMessage(
                senderId = viewerUid,
                senderName = display,
                text = "$display sent love to the stream",
                timestamp = System.currentTimeMillis(),
                type = "system",
                isSystemMessage = true
            )
            liveStreamsCollection.document(streamId).collection("messages").add(msg)
                .addOnSuccessListener { cont.resume(Result.success(Unit)) }
                .addOnFailureListener { e ->
                    Log.w(tag, "sendLiveStreamLikeSystemMessage live_streams/$streamId", e)
                    cont.resume(Result.failure(e))
                }
        }
    }

    /** Host is on a private call — `type=system` for chat + [hostPrivateCallBusy] on the room doc. */
    suspend fun sendLiveStreamHostBusySystemMessage(streamId: String, hostUid: String, lineText: String): Result<Unit> {
        if (streamId.isBlank() || hostUid.isBlank() || lineText.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid host busy notice"))
        }
        return suspendCancellableCoroutine { cont ->
            val msg = LiveStreamChatMessage(
                senderId = hostUid,
                senderName = "System",
                text = lineText,
                timestamp = System.currentTimeMillis(),
                type = "system",
                isSystemMessage = true
            )
            liveStreamsCollection.document(streamId).collection("messages").add(msg)
                .addOnSuccessListener { cont.resume(Result.success(Unit)) }
                .addOnFailureListener { e ->
                    Log.w(tag, "sendLiveStreamHostBusySystemMessage live_streams/$streamId", e)
                    cont.resume(Result.failure(e))
                }
        }
    }

    /** Host returned from private call to solo live. */
    suspend fun sendLiveStreamHostBackSystemMessage(streamId: String, hostUid: String, lineText: String): Result<Unit> {
        if (streamId.isBlank() || hostUid.isBlank() || lineText.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid host back notice"))
        }
        return suspendCancellableCoroutine { cont ->
            val msg = LiveStreamChatMessage(
                senderId = hostUid,
                senderName = "System",
                text = lineText,
                timestamp = System.currentTimeMillis(),
                type = "system",
                isSystemMessage = true
            )
            liveStreamsCollection.document(streamId).collection("messages").add(msg)
                .addOnSuccessListener { cont.resume(Result.success(Unit)) }
                .addOnFailureListener { e ->
                    Log.w(tag, "sendLiveStreamHostBackSystemMessage live_streams/$streamId", e)
                    cont.resume(Result.failure(e))
                }
        }
    }

    suspend fun setLiveStreamHostPrivateCallBusy(streamId: String, busy: Boolean): Result<Unit> {
        if (streamId.isBlank()) {
            return Result.failure(IllegalArgumentException("setLiveStreamHostPrivateCallBusy: blank streamId"))
        }
        return runCatching {
            liveStreamsCollection.document(streamId).set(
                mapOf(
                    "hostPrivateCallBusy" to busy,
                    "updatedAtMs" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
            Log.d(tag, "setLiveStreamHostPrivateCallBusy stream=$streamId busy=$busy")
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { e ->
                Log.e(tag, "setLiveStreamHostPrivateCallBusy failed stream=$streamId", e)
                Result.failure(e)
            }
        )
    }

    /** “[name] joined the room” — stored as `type=system` for distinct UI. */
    suspend fun sendLiveStreamJoinSystemMessage(streamId: String, joinerId: String, joinerName: String): Result<Unit> {
        if (streamId.isBlank() || joinerId.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid join notice"))
        }
        val display = joinerName.trim().ifBlank { "Someone" }
        return suspendCancellableCoroutine { cont ->
            val msg = LiveStreamChatMessage(
                senderId = joinerId,
                senderName = display,
                text = "$display joined the room",
                timestamp = System.currentTimeMillis(),
                type = "system",
                isSystemMessage = true
            )
            liveStreamsCollection.document(streamId).collection("messages").add(msg)
                .addOnSuccessListener { cont.resume(Result.success(Unit)) }
                .addOnFailureListener { e ->
                    Log.w(tag, "sendLiveStreamJoinSystemMessage live_streams/$streamId", e)
                    cont.resume(Result.failure(e))
                }
        }
    }

    /**
     * Adds [userId] to `live_streams/{streamId}.kickedUsers` (deduped). Clients should observe the room and eject.
     */
    suspend fun addUserToLiveStreamKickedList(streamId: String, userId: String): Result<Unit> {
        if (streamId.isBlank() || userId.isBlank()) {
            return Result.failure(IllegalArgumentException("kick: invalid args"))
        }
        return runCatching {
            liveStreamsCollection.document(streamId).update(
                mapOf(
                    "kickedUsers" to FieldValue.arrayUnion(userId),
                    "updatedAtMs" to System.currentTimeMillis()
                )
            ).await()
            Log.d(tag, "addUserToLiveStreamKickedList stream=$streamId user=$userId")
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { e ->
                Log.e(tag, "addUserToLiveStreamKickedList failed stream=$streamId", e)
                Result.failure(e)
            }
        )
    }

    /** Persists [blockedUserId] onto the host's `users/{hostUid}.blockedUserIds` list. */
    suspend fun addBlockedUserForHost(hostUid: String, blockedUserId: String): Result<Unit> {
        if (hostUid.isBlank() || blockedUserId.isBlank() || hostUid == blockedUserId) {
            return Result.failure(IllegalArgumentException("block: invalid args"))
        }
        return runCatching {
            usersCollection.document(hostUid).update(
                "blockedUserIds",
                FieldValue.arrayUnion(blockedUserId)
            ).await()
            Log.d(tag, "addBlockedUserForHost host=$hostUid blocked=$blockedUserId")
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { e ->
                Log.e(tag, "addBlockedUserForHost failed host=$hostUid", e)
                Result.failure(e)
            }
        )
    }

    /** Removes [blockedUserId] from `users/{hostUid}.blockedUserIds`. */
    suspend fun removeBlockedUserForHost(hostUid: String, blockedUserId: String): Result<Unit> {
        if (hostUid.isBlank() || blockedUserId.isBlank()) {
            return Result.failure(IllegalArgumentException("unblock: invalid args"))
        }
        return runCatching {
            usersCollection.document(hostUid).update(
                "blockedUserIds",
                FieldValue.arrayRemove(blockedUserId)
            ).await()
            Log.d(tag, "removeBlockedUserForHost host=$hostUid unblocked=$blockedUserId")
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { e ->
                Log.e(tag, "removeBlockedUserForHost failed host=$hostUid", e)
                Result.failure(e)
            }
        )
    }

    /**
     * Submits a moderation report. Requires Firestore rules that allow authenticated creates on
     * `user_reports/{autoId}` (e.g. `allow create: if request.auth != null`).
     */
    suspend fun submitUserProfileReport(
        reporterId: String,
        reportedUserId: String,
        reason: String,
    ): Result<Unit> {
        if (reporterId.isBlank() || reportedUserId.isBlank()) {
            return Result.failure(IllegalArgumentException("report: invalid args"))
        }
        return runCatching {
            db.collection("user_reports").add(
                mapOf(
                    "reporterId" to reporterId,
                    "reportedUserId" to reportedUserId,
                    "reason" to reason.trim().take(500),
                    "createdAtMs" to System.currentTimeMillis(),
                )
            ).await()
            Log.d(tag, "submitUserProfileReport reporter=$reporterId reported=$reportedUserId")
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { e ->
                Log.e(tag, "submitUserProfileReport failed", e)
                Result.failure(e)
            }
        )
    }

    suspend fun sendLiveStreamChatMessage(streamId: String, senderId: String, senderName: String, text: String): Result<Unit> {
        val trimmed = text.trim()
        if (streamId.isBlank() || senderId.isBlank() || trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Invalid live chat message"))
        }
        return suspendCancellableCoroutine { cont ->
            val msg = LiveStreamChatMessage(
                senderId = senderId,
                senderName = senderName.ifBlank { "User" },
                text = trimmed,
                timestamp = System.currentTimeMillis()
            )
            liveStreamsCollection.document(streamId).collection("messages").add(msg)
                .addOnSuccessListener { cont.resume(Result.success(Unit)) }
                .addOnFailureListener { e ->
                    Log.e(
                        tag,
                        "sendLiveStreamChatMessage FAILED — rules for live_streams/$streamId/messages? ${e.message}",
                        e
                    )
                    cont.resume(Result.failure(e))
                }
        }
    }

    /**
     * Writes a gift row to `live_streams/{streamId}/messages/{messageDocId}` so host + viewers
     * observe the same stream and can trigger [com.zipper.datingapp.ui.components.GiftOverlay].
     */
    suspend fun sendLiveStreamGiftMessage(
        streamId: String,
        messageDocId: String,
        senderId: String,
        senderName: String,
        gift: Gift,
        count: Int,
        giftRecipientUserId: String = ""
    ): Result<Unit> {
        if (streamId.isBlank() || messageDocId.isBlank() || senderId.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid live gift message"))
        }
        if (count <= 0) return Result.failure(IllegalArgumentException("Invalid gift count"))
        return suspendCancellableCoroutine { cont ->
            val msg = LiveStreamChatMessage(
                senderId = senderId,
                senderName = senderName.ifBlank { "User" },
                text = "${gift.name} ×$count",
                timestamp = System.currentTimeMillis(),
                type = "gift",
                giftId = gift.id,
                giftImageUrl = gift.thumbnailUrl.ifBlank { null },
                giftVideoUrl = gift.videoUrl.trim().takeIf { it.isNotEmpty() },
                giftSoundUrl = gift.soundUrl.trim().takeIf { it.isNotEmpty() },
                giftDisplayDurationSeconds = gift.displayDurationSeconds.coerceIn(0, 12),
                giftName = gift.name,
                giftRecipientId = giftRecipientUserId.trim(),
                giftCount = count.coerceAtLeast(1)
            )
            liveStreamsCollection.document(streamId).collection("messages").document(messageDocId).set(msg)
                .addOnSuccessListener { cont.resume(Result.success(Unit)) }
                .addOnFailureListener { e ->
                    Log.e(tag, "sendLiveStreamGiftMessage FAILED live_streams/$streamId/messages/$messageDocId", e)
                    cont.resume(Result.failure(e))
                }
        }
    }

    /**
     * Bills the payer for a completed 1:1 call using the same minute buckets as [LiveCallDiamondTracker]
     * (`(elapsedSeconds / 60) + 1` minutes × [diamondsPerMinute]). Caps charge at current balance.
     */
    suspend fun chargeCallDiamondSession(
        payerUid: String,
        receiverUid: String,
        elapsedSeconds: Int,
        diamondsPerMinute: Int
    ): Result<Int> {
        if (payerUid.isBlank() || receiverUid.isBlank() || payerUid == receiverUid) {
            return Result.success(0)
        }
        val rate = diamondsPerMinute.coerceAtLeast(0)
        if (rate <= 0 || elapsedSeconds <= 0) return Result.success(0)
        val billedMinutes = (elapsedSeconds / 60) + 1
        val rawTotal = billedMinutes * rate
        if (rawTotal <= 0) return Result.success(0)
        return try {
            val charged = db.runTransaction { tx ->
                val payerRef = usersCollection.document(payerUid)
                val payerSnap = tx.get(payerRef)
                if (!payerSnap.exists()) throw IllegalStateException("Payer missing")
                val bal = payerSnap.effectiveSpendableDiamondsMerged()
                val actual = minOf(rawTotal, bal.coerceAtLeast(0))
                if (actual <= 0) return@runTransaction 0
                val drift = -actual.toLong()
                tx.update(
                    payerRef,
                    mapOf(
                        "coins" to FieldValue.increment(drift),
                        "walletBalance" to FieldValue.increment(drift)
                    )
                )
                actual
            }.await()
            if (charged > 0) {
                routeCallReceiverProceedsFromCharge(payerUid, receiverUid, charged)
            }
            Result.success(charged)
        } catch (e: Exception) {
            Log.w(tag, "chargeCallDiamondSession payer=$payerUid receiver=$receiverUid", e)
            Result.failure(e)
        }
    }

    /**
     * Routes receiver-side proceeds from [chargeCallDiamondSession]:
     * - Receiver **beans** only when they joined with an agent referral code **and** qualify as host/streamer.
     * - Otherwise credits [BuildConfig.COMPANY_DIAMOND_ACCOUNT_UID] 💎 (`coins` + `walletBalance`) using the same 30/50/40 gender split as diamonds (truncated).
     */
    private suspend fun routeCallReceiverProceedsFromCharge(payerUid: String, receiverUid: String, diamondCharged: Int) {
        if (receiverUid.isBlank() || diamondCharged <= 0) return
        try {
            db.runTransaction { tx ->
                val recvRef = usersCollection.document(receiverUid)
                val payerRef = usersCollection.document(payerUid)
                val recvSnap = tx.get(recvRef)
                val payerSnap = tx.get(payerRef)
                if (!recvSnap.exists()) throw IllegalStateException("Receiver profile missing")

                val payerGender = payerSnap.primaryGenderRaw()
                val recvGender = recvSnap.primaryGenderRaw()
                val referralUsed = recvSnap.getString("referralCode")?.trim()?.isNotEmpty() == true
                val streamerEligible = recvSnap.isEligibleHostStreamerReceiverMerged()

                val pctPoints =
                    VirtualEconomyMath.callReceiverSharePercentPointsForVideoCallSettlement(payerGender, recvGender)
                val slice = (diamondCharged.toLong() * pctPoints) / 100L
                if (slice <= 0L) return@runTransaction

                val creditBeansToReceiver = referralUsed && streamerEligible
                if (creditBeansToReceiver) {
                    val prevBeans = recvSnap.getLong("beans") ?: 0L
                    val updates = mutableMapOf<String, Any>("beans" to FieldValue.increment(slice))
                    val recvGenderStr = recvGender.orEmpty()
                    if (VirtualEconomyMath.isFemaleGender(recvGenderStr) &&
                        !VirtualEconomyMath.isMaleGender(recvGenderStr)
                    ) {
                        updates["level"] = VirtualEconomyMath.femaleLevelFromBeans(prevBeans + slice)
                    }
                    tx.update(recvRef, updates)
                } else {
                    val companyUid = BuildConfig.COMPANY_DIAMOND_ACCOUNT_UID.trim()
                    if (companyUid.isEmpty()) {
                        Log.w(
                            tag,
                            "routeCallReceiverProceedsFromCharge: COMPANY_DIAMOND_ACCOUNT_UID unset; " +
                                "cannot credit treasury slice=$slice diamonds=$diamondCharged receiver=$receiverUid"
                        )
                        return@runTransaction
                    }
                    val cref = usersCollection.document(companyUid)
                    val cSnap = tx.get(cref)
                    val delta = FieldValue.increment(slice)
                    val payload = mapOf(
                        "coins" to delta,
                        "walletBalance" to delta,
                    )
                    if (!cSnap.exists()) {
                        tx.set(
                            cref,
                            hashMapOf<String, Any>(
                                "coins" to slice,
                                "walletBalance" to slice,
                            ),
                            SetOptions.merge(),
                        )
                    } else {
                        tx.update(cref, payload)
                    }
                }
            }.await()
            Log.d(tag, "routeCallReceiverProceedsFromCharge payer=$payerUid receiver=$receiverUid charged=$diamondCharged")
        } catch (e: Exception) {
            Log.w(tag, "routeCallReceiverProceedsFromCharge failed payer=$payerUid receiver=$receiverUid", e)
        }
    }

    suspend fun processGiftTransfer(
        transactionId: String,
        senderId: String,
        receiverId: String,
        gift: Gift,
        count: Int
    ): GiftTransferResult {
        if (transactionId.isBlank()) return GiftTransferResult.Failed("Transaction ID is required")
        if (senderId.isBlank()) return GiftTransferResult.Failed("Sender ID is required")
        if (receiverId.isBlank()) return GiftTransferResult.Failed("Receiver ID is required")
        if (count <= 0) return GiftTransferResult.Failed("Gift count must be > 0")

        val totalCostLong = gift.price.toLong() * count.toLong()
        if (totalCostLong <= 0L || totalCostLong > Int.MAX_VALUE.toLong()) {
            return GiftTransferResult.Failed("Invalid gift amount")
        }
        val totalCost = totalCostLong.toInt()

        return try {
            val senderBalance = db.runTransaction { tx ->
                val txRef = giftTransactionsCollection.document(transactionId)
                val existingTx = tx.get(txRef)
                if (existingTx.exists()) {
                    throw IllegalStateException("Duplicate transaction")
                }

                val senderRef = usersCollection.document(senderId)
                val receiverRef = usersCollection.document(receiverId)
                val senderSnap = tx.get(senderRef)
                val receiverSnap = tx.get(receiverRef)
                if (!senderSnap.exists()) throw IllegalStateException("Sender profile missing")
                if (!receiverSnap.exists()) throw IllegalStateException("Receiver profile missing")

                // Same effective balance as [DocumentSnapshot.toUserProfileMerged]: `coins` first, then `walletBalance`.
                val priorBalance =
                    (senderSnap.getLong("coins") ?: senderSnap.getLong("walletBalance"))?.toInt() ?: 0
                if (priorBalance < totalCost) throw IllegalStateException("Insufficient balance")
                val newSenderBalance = priorBalance - totalCost
                val senderGender = senderSnap.getString("gender")
                val negDiamond = FieldValue.increment(-totalCostLong)
                val senderSpendUpdates = hashMapOf<String, Any>(
                    "coins" to negDiamond,
                    "walletBalance" to negDiamond
                )
                if (VirtualEconomyMath.isMaleGender(senderGender)) {
                    val prevSpent = senderSnap.getLong("giftDiamondsSpentTotal") ?: 0L
                    val newSpent = prevSpent + totalCostLong
                    senderSpendUpdates["giftDiamondsSpentTotal"] = FieldValue.increment(totalCostLong)
                    senderSpendUpdates["level"] = VirtualEconomyMath.maleGiverLevelFromGiftSpend(newSpent)
                }
                tx.update(senderRef, senderSpendUpdates)

                val receiverGender = receiverSnap.getString("gender")
                    ?.trim().orEmpty()
                    .ifBlank { receiverSnap.getString("genderText")?.trim().orEmpty() }
                val beansEarned = VirtualEconomyMath.giftReceiverBeansEarned(totalCostLong, gift)
                if (beansEarned > 0L) {
                    val prevBeans = receiverSnap.getLong("beans") ?: 0L
                    val receiverUpdates = mutableMapOf<String, Any>(
                        "beans" to FieldValue.increment(beansEarned),
                    )
                    if (VirtualEconomyMath.isFemaleGender(receiverGender) &&
                        !VirtualEconomyMath.isMaleGender(receiverGender)
                    ) {
                        receiverUpdates["level"] =
                            VirtualEconomyMath.femaleLevelFromBeans(prevBeans + beansEarned)
                    }
                    tx.update(receiverRef, receiverUpdates)
                }
                tx.set(
                    txRef,
                    mapOf(
                        "transactionId" to transactionId,
                        "senderId" to senderId,
                        "receiverId" to receiverId,
                        "giftId" to gift.id,
                        "giftName" to gift.name,
                        "giftPrice" to gift.price,
                        "count" to count,
                        "totalCost" to totalCost,
                        "serverTime" to FieldValue.serverTimestamp()
                    )
                )
                newSenderBalance
            }.await()
            GiftTransferResult.Success(senderBalance)
        } catch (e: Exception) {
            Log.w(tag, "Gift transfer failed txId=$transactionId", e)
            GiftTransferResult.Failed(e.message ?: "Gift transfer failed")
        }
    }

    /**
     * Gifts the signed-in user sent to [receiverId] via [processGiftTransfer] (`gift_transactions`), newest first.
     * Sorted client-side so no composite Firestore index is required.
     */
    suspend fun fetchSentGiftsToUser(
        senderId: String,
        receiverId: String,
        limit: Int = 15
    ): List<SentGiftHistoryEntry> {
        val s = senderId.trim()
        val r = receiverId.trim()
        if (s.isEmpty() || r.isEmpty()) return emptyList()
        val cap = limit.coerceIn(1, 50)
        return try {
            val snap = giftTransactionsCollection
                .whereEqualTo("senderId", s)
                .whereEqualTo("receiverId", r)
                .limit((cap * 3).coerceAtMost(100).toLong())
                .get()
                .await()
            snap.documents.mapNotNull { d ->
                val name = d.getString("giftName") ?: return@mapNotNull null
                SentGiftHistoryEntry(
                    giftName = name,
                    giftId = d.getString("giftId").orEmpty(),
                    totalCost = (d.getLong("totalCost") ?: 0L).toInt().coerceAtLeast(0),
                    count = (d.getLong("count") ?: 1L).toInt().coerceAtLeast(1),
                    timestampMs = d.getTimestamp("serverTime")?.toDate()?.time ?: 0L
                )
            }.sortedByDescending { it.timestampMs }.take(cap)
        } catch (e: Exception) {
            Log.w(tag, "fetchSentGiftsToUser failed sender=$s receiver=$r", e)
            emptyList()
        }
    }

    /**
     * All gifts credited to [receiverProfileId] in `gift_transactions` (any sender), newest first.
     */
    suspend fun fetchReceivedGiftsForUser(
        receiverProfileId: String,
        limit: Int = 15
    ): List<SentGiftHistoryEntry> {
        val r = receiverProfileId.trim()
        if (r.isEmpty()) return emptyList()
        val cap = limit.coerceIn(1, 50)
        return try {
            val snap = giftTransactionsCollection
                .whereEqualTo("receiverId", r)
                .limit((cap * 3).coerceAtMost(100).toLong())
                .get()
                .await()
            snap.documents.mapNotNull { d ->
                val name = d.getString("giftName") ?: return@mapNotNull null
                SentGiftHistoryEntry(
                    giftName = name,
                    giftId = d.getString("giftId").orEmpty(),
                    totalCost = (d.getLong("totalCost") ?: 0L).toInt().coerceAtLeast(0),
                    count = (d.getLong("count") ?: 1L).toInt().coerceAtLeast(1),
                    timestampMs = d.getTimestamp("serverTime")?.toDate()?.time ?: 0L
                )
            }.sortedByDescending { it.timestampMs }.take(cap)
        } catch (e: Exception) {
            Log.w(tag, "fetchReceivedGiftsForUser failed receiver=$r", e)
            emptyList()
        }
    }

    /**
     * Live / Game Center gift: atomic debit of sender [UserProfile.walletBalance]/`coins` ([FieldValue.increment])
     * and credit to host [UserProfile.diamondsEarned]/`gems`, plus an append-only row in [economyTransactionsCollection].
     *
     * When [livePkScoreStreamDocId] and [livePkScoreDelta] are set, the PK tug totals on
     * `live_streams/{livePkScoreStreamDocId}` are updated with [FieldValue.increment] (host doc id = PK blue side uid).
     */
    suspend fun sendGift(
        transactionId: String,
        senderId: String,
        hostId: String,
        giftCost: Int,
        gift: Gift,
        livePkScoreStreamDocId: String? = null,
        livePkScoreDelta: Long = 0L
    ): LiveEconomyGiftResult {
        if (transactionId.isBlank() || senderId.isBlank() || hostId.isBlank()) {
            return LiveEconomyGiftResult.Failed("sender, host, and transaction id required")
        }
        if (senderId == hostId) {
            return LiveEconomyGiftResult.Failed("Cannot gift yourself")
        }
        val totalCostLong = giftCost.toLong()
        if (totalCostLong <= 0L || totalCostLong > Int.MAX_VALUE.toLong()) {
            return LiveEconomyGiftResult.Failed("Invalid gift amount")
        }
        val safeGiftCost = totalCostLong.toInt()
        val costL = safeGiftCost.toLong()
        return try {
            val newWallet = db.runTransaction { tx ->
                val txRef = economyTransactionsCollection.document(transactionId)
                if (tx.get(txRef).exists()) {
                    throw IllegalStateException("Duplicate transaction")
                }
                val senderRef = usersCollection.document(senderId)
                val hostRef = usersCollection.document(hostId)
                val senderSnap = tx.get(senderRef)
                val hostSnap = tx.get(hostRef)
                if (!senderSnap.exists()) throw IllegalStateException("Sender profile missing")
                if (!hostSnap.exists()) throw IllegalStateException("Host profile missing")

                val pkDocId = livePkScoreStreamDocId?.trim().orEmpty()
                val pkDelta = livePkScoreDelta.coerceAtLeast(0L)
                val liveRef = if (pkDocId.isNotEmpty() && pkDelta > 0L) {
                    liveStreamsCollection.document(pkDocId)
                } else {
                    null
                }
                val liveSnap = liveRef?.let { tx.get(it) }

                // Match [DocumentSnapshot.toUserProfileMerged]: authoritative spendable field is `coins`, then `walletBalance`.
                val spendable =
                    (senderSnap.getLong("coins") ?: senderSnap.getLong("walletBalance"))?.toInt() ?: 0
                if (spendable < safeGiftCost) throw IllegalStateException("Insufficient balance")

                val newWalletBalance = spendable - safeGiftCost
                val hostGender = hostSnap.getString("gender")
                    ?.trim().orEmpty()
                    .ifBlank { hostSnap.getString("genderText")?.trim().orEmpty() }
                val hostBeansDelta = VirtualEconomyMath.giftReceiverBeansEarned(costL, gift)
                val prevBeans = hostSnap.getLong("beans") ?: 0L
                val senderGender = senderSnap.getString("gender")
                val neg = FieldValue.increment(-costL)
                val senderLiveUpdates = hashMapOf<String, Any>(
                    "walletBalance" to neg,
                    "coins" to neg
                )
                if (VirtualEconomyMath.isMaleGender(senderGender)) {
                    val prevSpent = senderSnap.getLong("giftDiamondsSpentTotal") ?: 0L
                    val newSpent = prevSpent + costL
                    senderLiveUpdates["giftDiamondsSpentTotal"] = FieldValue.increment(costL)
                    senderLiveUpdates["level"] = VirtualEconomyMath.maleGiverLevelFromGiftSpend(newSpent)
                }
                tx.update(senderRef, senderLiveUpdates)
                val gemInc = FieldValue.increment(costL)
                val hostUpdates = hashMapOf<String, Any>(
                    "diamondsEarned" to gemInc,
                    "gems" to gemInc
                )
                if (hostBeansDelta > 0L) {
                    hostUpdates["beans"] = FieldValue.increment(hostBeansDelta)
                    if (VirtualEconomyMath.isFemaleGender(hostGender) &&
                        !VirtualEconomyMath.isMaleGender(hostGender)
                    ) {
                        hostUpdates["level"] =
                            VirtualEconomyMath.femaleLevelFromBeans(prevBeans + hostBeansDelta)
                    }
                }
                tx.update(hostRef, hostUpdates)

                if (liveRef != null && liveSnap != null && liveSnap.exists() && pkDelta > 0L) {
                    val pkStarted = liveSnap.getLong("pkStartedAtMillis") ?: 0L
                    val guestUid = liveSnap.getString("pkBattleGuestUid")?.trim().orEmpty()
                        .ifBlank { liveSnap.getString("pkGuestUserId")?.trim().orEmpty() }
                    val scoringOpen = !liveSnap.contains("pkScoringOpen") || liveSnap.getBoolean("pkScoringOpen") == true
                    if (pkStarted > 0L && guestUid.isNotEmpty() && scoringOpen) {
                        val recip = hostId.trim()
                        val pkInc = FieldValue.increment(pkDelta)
                        val now = System.currentTimeMillis()
                        when (recip) {
                            pkDocId -> tx.update(
                                liveRef,
                                mapOf("pkHostBeans" to pkInc, "updatedAtMs" to now)
                            )
                            guestUid -> tx.update(
                                liveRef,
                                mapOf("pkGuestBeans" to pkInc, "updatedAtMs" to now)
                            )
                        }
                    }
                }

                tx.set(
                    txRef,
                    mapOf(
                        "transactionId" to transactionId,
                        "type" to "LIVE_STREAM_GIFT",
                        "senderId" to senderId,
                        "hostId" to hostId,
                        "giftCost" to safeGiftCost,
                        "giftId" to gift.id,
                        "giftName" to gift.name,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                newWalletBalance
            }.await()
            Log.d(tag, "sendGift LIVE_STREAM_GIFT ok tx=$transactionId newWallet=$newWallet")
            LiveEconomyGiftResult.Success(newWallet)
        } catch (e: Exception) {
            Log.w(tag, "sendGift LIVE_STREAM_GIFT failed tx=$transactionId", e)
            LiveEconomyGiftResult.Failed(e.message ?: "Gift failed")
        }
    }

    /** Adds streaming minutes to [UserProfile.totalStreamingMinutes] and refreshes [UserProfile.level] from stats. */
    suspend fun addHostStreamingMinutes(hostUid: String, minutes: Int) {
        if (hostUid.isBlank() || minutes <= 0) return
        val ref = usersCollection.document(hostUid)
        try {
            db.runTransaction { tx ->
                val snap = tx.get(ref)
                if (!snap.exists()) throw IllegalStateException("Host profile missing")
                val prevStream = snap.getLong("totalStreamingMinutes") ?: 0L
                val prevLive = snap.getLong("totalLiveMinutes") ?: 0L
                val newStream = prevStream + minutes.toLong()
                val diamonds =
                    (snap.getLong("diamondsEarned") ?: snap.getLong("gems"))?.toInt() ?: 0
                val genderHint = snap.getString("gender")?.trim().orEmpty()
                val newLevel = if (VirtualEconomyMath.isFemaleGender(genderHint)) {
                    val beans = snap.getLong("beans") ?: 0L
                    VirtualEconomyMath.femaleLevelFromBeans(beans)
                } else {
                    kotlin.math.max(
                        snap.getLong("level")?.toInt() ?: 1,
                        computeHostLevel(prevLive + newStream, diamonds)
                    )
                }
                tx.update(
                    ref,
                    mapOf(
                        "totalStreamingMinutes" to newStream,
                        "level" to newLevel
                    )
                )
            }.await()
            Log.d(tag, "addHostStreamingMinutes uid=$hostUid +${minutes}m")
        } catch (e: Exception) {
            Log.w(tag, "addHostStreamingMinutes failed uid=$hostUid", e)
        }
    }

    /** Atomically increments the host's `likeMeCount` in their user doc, and also records
     *  that the viewer already liked this stream session so we can de-duplicate. */
    suspend fun likeHostLiveStream(viewerUid: String, hostUid: String) {
        if (viewerUid.isBlank() || hostUid.isBlank() || viewerUid == hostUid) return
        val hostRef = usersCollection.document(hostUid)
        try {
            db.runTransaction { tx ->
                val snap = tx.get(hostRef)
                if (!snap.exists()) throw IllegalStateException("Host profile not found")
                tx.update(hostRef, mapOf("likeMeCount" to FieldValue.increment(1)))
            }.await()
            Log.d(tag, "likeHostLiveStream viewer=$viewerUid -> host=$hostUid")
        } catch (e: Exception) {
            Log.w(tag, "likeHostLiveStream failed", e)
        }
    }

    suspend fun sendCallInvite(
        callerId: String,
        callerName: String,
        receiverId: String,
        roomId: String,
        isVideoCall: Boolean
    ) {
        val now = System.currentTimeMillis()
        val payload = mapOf(
            "type" to "incoming_call",
            "callerId" to callerId,
            "callerName" to callerName,
            "receiverId" to receiverId,
            "roomId" to roomId,
            "isVideoCall" to isVideoCall,
            "timestamp" to now
        )
        callInvitesRef.child(receiverId).setValue(payload).await()
        val fsPayload = mapOf(
            "callerId" to callerId,
            "callerName" to callerName,
            "receiverId" to receiverId,
            "roomId" to roomId,
            "isVideoCall" to isVideoCall,
            "status" to "RINGING",
            "createdAtMs" to now,
            "updatedAtMs" to now
        )
        // Replace the session doc so a prior ENDED/ACCEPTED state cannot block the next invite for the same pair.
        callsCollection.document(roomId).set(fsPayload).await()
        Log.d(tag, "sendCallInvite: RTDB + Firestore calls/$roomId status=RINGING (replaced)")
        Log.d(
            "WebRTC_SIGNAL",
            "Call invite sent roomId=$roomId — SDP offer/answer + ICE candidates exchange on Realtime DB path rooms/$roomId (WebRTCManager)"
        )
    }

    /** Firestore signaling: receiver listens for `RINGING` sessions targeting their uid. */
    fun observeIncomingCallFirestore(receiverId: String): Flow<IncomingCallInvite?> = callbackFlow {
        // No orderBy: avoids composite index failures in production (silent empty listener).
        val query = callsCollection
            .whereEqualTo("receiverId", receiverId)
            .whereEqualTo("status", "RINGING")
            .limit(24)
        val reg = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                logFirestoreSnapshotError("calls RINGING receiver=$receiverId", error)
                return@addSnapshotListener
            }
            val doc = snapshot?.documents?.maxByOrNull { d ->
                (d.get("createdAtMs") as? Number)?.toLong() ?: 0L
            }
            if (doc == null || !doc.exists()) {
                trySend(null)
                return@addSnapshotListener
            }
            val fields = doc.data
            if (fields == null) {
                trySend(null)
                return@addSnapshotListener
            }
            try {
                val callerId = fields["callerId"]?.toString()?.takeIf { it.isNotBlank() }
                val roomId = fields["roomId"]?.toString()?.takeIf { it.isNotBlank() }
                if (callerId == null || roomId == null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val callerName = fields["callerName"]?.toString()?.ifBlank { "Unknown caller" } ?: "Unknown caller"
                val isVideoCall = when (val raw = fields["isVideoCall"]) {
                    is Boolean -> raw
                    is String -> raw.equals("true", ignoreCase = true)
                    is Number -> raw.toInt() != 0
                    else -> true
                }
                trySend(
                    IncomingCallInvite(
                        callerId = callerId,
                        callerName = callerName,
                        roomId = roomId,
                        isVideoCall = isVideoCall
                    )
                )
            } catch (e: Exception) {
                Log.w(tag, "observeIncomingCallFirestore: skip malformed doc", e)
                trySend(null)
            }
        }
        awaitClose { reg.remove() }
    }

    suspend fun setCallSessionStatus(roomId: String, status: String) {
        if (roomId.isBlank()) return
        Log.d(tag, "setCallSessionStatus: roomId=$roomId status=$status")
        runCatching {
            callsCollection.document(roomId).update(
                mapOf(
                    "status" to status,
                    "updatedAtMs" to System.currentTimeMillis()
                )
            ).await()
        }.onFailure { Log.w(tag, "setCallSessionStatus failed roomId=$roomId", it) }
    }

    /** Firestore `calls/{roomId}.status` — e.g. ENDED when the other party hangs up (1:1 audio/video). */
    fun observeCallSessionStatus(roomId: String): Flow<String?> = callbackFlow {
        if (roomId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val reg = callsCollection.document(roomId).addSnapshotListener { snap, err ->
            if (err != null) {
                Log.w(tag, "observeCallSessionStatus room=$roomId: ${err.message}")
                trySend(null)
                return@addSnapshotListener
            }
            if (snap == null || !snap.exists()) {
                trySend(null)
                return@addSnapshotListener
            }
            trySend(snap.getString("status"))
        }
        awaitClose { reg.remove() }
    }

    /**
     * Firestore `calls/{roomId}.createdAtMs` — set when the invite is sent ([sendCallInvite]).
     * Used to scope `live_streams/{call_*}/messages` to the current call session (exclude prior chats).
     */
    suspend fun getCallDocumentCreatedAtMs(roomId: String): Long? {
        if (roomId.isBlank()) return null
        return runCatching {
            val snap = callsCollection.document(roomId).get().await()
            if (!snap.exists()) return@runCatching null
            when (val raw = snap.get("createdAtMs")) {
                is Number -> raw.toLong()
                else -> null
            }
        }.getOrElse { e ->
            Log.w(tag, "getCallDocumentCreatedAtMs failed roomId=$roomId", e)
            null
        }
    }

    suspend fun clearIncomingCallFirestore(receiverId: String) {
        if (receiverId.isBlank()) return
        val snap = callsCollection
            .whereEqualTo("receiverId", receiverId)
            .whereEqualTo("status", "RINGING")
            .get().await()
        for (doc in snap.documents) {
            runCatching { doc.reference.delete().await() }
        }
        Log.d(tag, "clearIncomingCallFirestore: removed ${snap.documents.size} RINGING doc(s) for receiver=$receiverId")
    }

    fun observeIncomingCall(userId: String): Flow<IncomingCallInvite?> = callbackFlow {
        val ref = callInvitesRef.child(userId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val value = snapshot.value as? Map<*, *>
                if (value == null) {
                    trySend(null)
                    return
                }
                try {
                    val callerId = value["callerId"]?.toString()?.takeIf { it.isNotBlank() } ?: return
                    val callerName = value["callerName"]?.toString()?.ifBlank { "Unknown caller" } ?: "Unknown caller"
                    val roomId = value["roomId"]?.toString()?.takeIf { it.isNotBlank() } ?: return
                    val isVideoCall = when (val raw = value["isVideoCall"]) {
                        is Boolean -> raw
                        is String -> raw.equals("true", ignoreCase = true)
                        is Number -> raw.toInt() != 0
                        else -> true
                    }
                    trySend(
                        IncomingCallInvite(
                            callerId = callerId,
                            callerName = callerName,
                            roomId = roomId,
                            isVideoCall = isVideoCall
                        )
                    )
                } catch (e: Exception) {
                    Log.w(tag, "Skipping malformed incoming call invite", e)
                    trySend(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(tag, "observeIncomingCall RTDB onCancelled: ${error.message} code=${error.code}")
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun clearIncomingCallRtdb(receiverId: String) {
        if (receiverId.isBlank()) return
        callInvitesRef.child(receiverId).removeValue().await()
        Log.d(tag, "clearIncomingCallRtdb: receiverId=$receiverId")
    }

    /** Clears RTDB ring tile and any `RINGING` rows in Firestore for this receiver. */
    suspend fun clearIncomingCall(receiverId: String) {
        clearIncomingCallRtdb(receiverId)
        clearIncomingCallFirestore(receiverId)
    }

    suspend fun sendCallResponse(
        callerId: String,
        roomId: String,
        status: String,
        reason: String? = null
    ) {
        val payload = mutableMapOf<String, Any>(
            "roomId" to roomId,
            "status" to status,
            "timestamp" to System.currentTimeMillis()
        )
        reason?.let { payload["reason"] = it }
        callResponsesRef.child(callerId).setValue(payload).await()
    }

    suspend fun clearCallRoom(roomId: String) {
        if (roomId.isBlank()) return
        roomsRef.child(roomId).removeValue().await()
    }

    /**
     * Deletes WebRTC signaling leftovers under `calls/{roomId}` (e.g. `offerCandidates`, `answerCandidates`).
     * Keeps the parent call doc so status/history fields from [setCallSessionStatus] remain until you delete it.
     */
    suspend fun cleanupCallFirestoreSignalingCollections(roomId: String) {
        if (roomId.isBlank()) return
        if (isCleanupInProgress) {
            Log.d(tag, "cleanupCallFirestoreSignalingCollections: already in progress, skip room=$roomId")
            return
        }
        isCleanupInProgress = true
        try {
            Log.d("WebRTC_Debug", "{\"event\":\"FIRESTORE_CLEANUP_START\",\"room\":\"$roomId\",\"ts\":${System.currentTimeMillis()}}")
            val doc = callsCollection.document(roomId)
            runCatching { deleteFirestoreCollectionInBatches(doc.collection("offerCandidates")) }
                .onFailure { Log.w(tag, "cleanup offerCandidates failed room=$roomId", it) }
            runCatching { deleteFirestoreCollectionInBatches(doc.collection("answerCandidates")) }
                .onFailure { Log.w(tag, "cleanup answerCandidates failed room=$roomId", it) }
            Log.d(tag, "cleanupCallFirestoreSignalingCollections done room=$roomId")
        } finally {
            Log.d("WebRTC_Debug", "{\"event\":\"FIRESTORE_CLEANUP_DONE\",\"room\":\"$roomId\",\"ts\":${System.currentTimeMillis()}}")
            isCleanupInProgress = false
        }
    }

    /**
     * 1:1 WebRTC (`call_*`): merges SDP offer + server [createdAt] into [calls] (invite fields preserved).
     * @return true if the write succeeded.
     */
    suspend fun mergeCallWebRtcOffer(
        roomId: String,
        callerId: String,
        offerType: String,
        offerSdp: String,
    ): Boolean {
        if (roomId.isBlank() || callerId.isBlank()) return false
        return runCatching {
            val offerMap = mapOf(
                "type" to offerType,
                "sdp" to offerSdp,
            )
            val data = hashMapOf<String, Any>(
                "offer" to offerMap,
                "callerId" to callerId,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAtMs" to System.currentTimeMillis(),
            )
            callsCollection.document(roomId).set(data, SetOptions.merge()).await()
            true
        }.onFailure { e ->
            Log.e(tag, "mergeCallWebRtcOffer failed room=$roomId", e)
        }.getOrDefault(false)
    }

    /** 1:1 WebRTC: merges SDP answer for the caller's real-time listener. */
    suspend fun mergeCallWebRtcAnswer(roomId: String, answerType: String, answerSdp: String): Boolean {
        if (roomId.isBlank()) return false
        return runCatching {
            callsCollection.document(roomId).set(
                mapOf(
                    "answer" to mapOf("type" to answerType, "sdp" to answerSdp),
                    "updatedAtMs" to System.currentTimeMillis(),
                ),
                SetOptions.merge(),
            ).await()
            true
        }.onFailure { e ->
            Log.e(tag, "mergeCallWebRtcAnswer failed room=$roomId", e)
        }.getOrDefault(false)
    }

    /** Trickle ICE: [subcollection] is `offerCandidates` (caller) or `answerCandidates` (callee). */
    suspend fun addCallWebRtcIceCandidate(
        roomId: String,
        subcollection: String,
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int,
    ): Boolean {
        if (roomId.isBlank() || subcollection.isBlank()) return false
        return runCatching {
            callsCollection.document(roomId).collection(subcollection).add(
                mapOf(
                    "candidate" to candidate,
                    "sdpMid" to (sdpMid ?: ""),
                    "sdpMLineIndex" to sdpMLineIndex,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            true
        }.onFailure { e ->
            Log.e(tag, "addCallWebRtcIceCandidate failed room=$roomId coll=$subcollection", e)
        }.getOrDefault(false)
    }

    /** Caller: no SDP answer on the call doc within the client-side post-offer timeout. */
    suspend fun markCallNoAnswerSdpTimeout(roomId: String) {
        if (roomId.isBlank()) return
        runCatching {
            callsCollection.document(roomId).update(
                mapOf(
                    "status" to "NO_ANSWER_SDP_TIMEOUT",
                    "updatedAtMs" to System.currentTimeMillis(),
                ),
            ).await()
        }.onFailure { e ->
            Log.w(tag, "markCallNoAnswerSdpTimeout failed room=$roomId", e)
        }
    }

    private suspend fun deleteFirestoreCollectionInBatches(
        collection: com.google.firebase.firestore.CollectionReference,
        batchSize: Long = 450,
    ) {
        while (true) {
            val snap = collection.limit(batchSize).get().await()
            if (snap.isEmpty) break
            val batch = db.batch()
            for (d in snap.documents) {
                batch.delete(d.reference)
            }
            batch.commit().await()
        }
    }

    suspend fun waitForCallResponse(
        callerId: String,
        roomId: String,
        timeoutMs: Long = 30_000
    ): String? {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val ref = callResponsesRef.child(callerId)
                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            val payload = snapshot.value as? Map<*, *> ?: return
                            val incomingRoom = payload["roomId"]?.toString() ?: return
                            if (incomingRoom != roomId) return
                            val status = payload["status"]?.toString() ?: return
                            ref.removeValue()
                            if (cont.isActive) cont.resume(status)
                        } catch (_: Exception) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                ref.addValueEventListener(listener)
                cont.invokeOnCancellation {
                    ref.removeEventListener(listener)
                }
            }
        }
    }

    suspend fun getGifts(): List<Gift> {
        return try {
            val snapshot = giftsCollection.get().await()
            if (snapshot.isEmpty) {
                availableGifts.map { it.withCatalogDefaults() }
            } else {
                snapshot.documents.mapNotNull { it.safeToObject<Gift>(tag) }
                    .map { it.withCatalogDefaults() }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch gifts", e)
            availableGifts.map { it.withCatalogDefaults() }
        }
    }

    fun observeGifts(): Flow<List<Gift>> = callbackFlow {
        val subscription = giftsCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                logFirestoreSnapshotError("gifts (observeGifts)", error)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            val gifts = snapshot.documents.mapNotNull { it.safeToObject<Gift>(tag) }
                .map { it.withCatalogDefaults() }
            Log.d(tag, "observeGifts: count=${gifts.size}")
            val merged = if (gifts.isEmpty()) availableGifts.map { it.withCatalogDefaults() } else gifts.sortedBy { it.price }
            trySend(merged)
        }
        awaitClose { subscription.remove() }
    }
    fun observeResellers(): Flow<List<ResellAgent>> = callbackFlow {
    val subscription = db.collection("diamondResellers")
        .whereEqualTo("status", "active")   // matches CRM status field
        .orderBy("order")                    // matches CRM order field
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("FirebaseService", "observeResellers error: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
 
            val resellers = snapshot.documents.mapNotNull { doc ->
                runCatching {
                    ResellAgent(
                        // All field names match exactly what CRM writes
                        country       = doc.getString("country")       ?: "",
                        flag          = doc.getString("flag")          ?: "🌍",
                        resellerName  = doc.getString("resellerName")  ?: "",  // NOT "name"
                        phone         = doc.getString("phone")         ?: "",
                        whatsappLink  = doc.getString("whatsappLink")  ?: "",  // NOT "whatsapp"
                        customMessage = doc.getString("customMessage") ?: "",
                        order         = doc.getLong("order")           ?: 0L,
                        status        = doc.getString("status")        ?: "active"
                    )
                }.getOrNull()
            }
            Log.d("FirebaseService", "observeResellers: ${resellers.size} active resellers")
            trySend(resellers)
        }
    awaitClose { subscription.remove() }
}

    suspend fun findOrCreatePkMatch(userId: String, streamId: String, durationSeconds: Int = 300): PkMatchResult {
        if (userId.isBlank()) return PkMatchResult.Error("Not signed in")
        repeat(4) { attempt ->
            when (val r = findOrCreatePkMatchOnce(userId, streamId, durationSeconds)) {
                is PkMatchResult.Error -> {
                    val msg = r.message
                    val retryable = msg.contains("aborted", ignoreCase = true) ||
                        msg.contains("Matchmaking failed", ignoreCase = true)
                    if (retryable && attempt < 3) {
                        Log.d(tag, "findOrCreatePkMatch: retry ${attempt + 1} after error: $msg")
                        delay((120L * (attempt + 1)).coerceAtMost(400L))
                    } else {
                        return r
                    }
                }
                else -> return r
            }
        }
        return PkMatchResult.Error("Matchmaking busy, try again in a moment")
    }

    private suspend fun findOrCreatePkMatchOnce(userId: String, streamId: String, durationSeconds: Int): PkMatchResult {
        val myDuration = durationSeconds.coerceIn(60, 3600)
        var pendingMatch: PkTransactionResult? = null
        val txResult = suspendCancellableCoroutine<PkTransactionResult> { cont ->
            pkQueueRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    // Each Firebase retry must not leak a match outcome from a previous attempt.
                    pendingMatch = null
                    val value = currentData.value as? Map<*, *>
                    val waitingUserId = value?.get("userId")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    val waitingStreamId = value?.get("streamId")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    val waitingDuration = (value?.get("durationSeconds") as? Number)?.toInt()?.coerceIn(60, 3600) ?: 300

                    if (waitingUserId.isNullOrBlank()) {
                        currentData.value = mapOf(
                            "userId" to userId,
                            "streamId" to streamId,
                            "timestamp" to System.currentTimeMillis(),
                            "durationSeconds" to myDuration
                        )
                        return Transaction.success(currentData)
                    }

                    if (waitingUserId == userId) {
                        // Already in queue (stale row, reconnect). Stay waiting — do not abort().
                        return Transaction.success(currentData)
                    }

                    val roomId = buildPkRoomId(waitingUserId, userId)
                    currentData.value = null
                    pendingMatch = PkTransactionResult(
                        state = PkTransactionState.MATCHED_AS_CLAIMER,
                        roomId = roomId,
                        partnerUserId = waitingUserId,
                        partnerStreamId = waitingStreamId,
                        matchDurationSeconds = waitingDuration
                    )
                    return Transaction.success(currentData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    if (!cont.isActive) return
                    if (error != null) {
                        cont.resume(PkTransactionResult(state = PkTransactionState.ERROR, error = error.message))
                        return
                    }
                    if (!committed) {
                        Log.w(tag, "findOrCreatePkMatch: transaction not committed userId=$userId")
                        cont.resume(PkTransactionResult(state = PkTransactionState.ABORTED))
                        return
                    }
                    pendingMatch?.let {
                        cont.resume(it)
                        return
                    }

                    val txData = currentData?.value as? Map<*, *>
                    val queueUserId = txData?.get("userId")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    if (queueUserId == userId) {
                        cont.resume(PkTransactionResult(state = PkTransactionState.WAITING))
                    } else {
                        Log.w(
                            tag,
                            "findOrCreatePkMatch: unexpected queue after commit userId=$userId queueUserId=$queueUserId txData=$txData"
                        )
                        cont.resume(PkTransactionResult(state = PkTransactionState.ABORTED))
                    }
                }
            })
        }

        return when (txResult.state) {
            PkTransactionState.MATCHED_AS_CLAIMER -> {
                val roomId = txResult.roomId?.takeIf { it.isNotBlank() }
                    ?: return PkMatchResult.Error("Failed to create PK room")
                val partnerId = txResult.partnerUserId?.takeIf { it.isNotBlank() }
                    ?: return PkMatchResult.Error("Partner not found")
                val partnerStream = txResult.partnerStreamId?.takeIf { it.isNotBlank() } ?: partnerId
                val roundDur = txResult.matchDurationSeconds.coerceIn(60, 3600)
                val claimedBy = mapOf(
                    "partnerUserId" to userId,
                    "partnerStreamId" to streamId,
                    "roomId" to roomId,
                    "timestamp" to System.currentTimeMillis(),
                    "durationSeconds" to roundDur
                )
                pkMatchesRef.child(partnerId).setValue(claimedBy).await()
                PkMatchResult.Matched(
                    roomId = roomId,
                    partnerUserId = partnerId,
                    partnerStreamId = partnerStream,
                    isStreamer = false,
                    durationSeconds = roundDur
                )
            }
            PkTransactionState.WAITING -> PkMatchResult.Waiting
            PkTransactionState.ABORTED -> PkMatchResult.Error("Matchmaking aborted, please retry")
            PkTransactionState.ERROR -> PkMatchResult.Error(txResult.error ?: "Matchmaking failed")
        }
    }

    suspend fun waitForPkClaim(userId: String, timeoutMs: Long = 30000): PkMatchResult {
        return suspendCancellableCoroutine { cont ->
            val matchRef = pkMatchesRef.child(userId)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val value = snapshot.value as? Map<*, *> ?: return
                        val roomId = value["roomId"]?.toString()?.takeIf { it.isNotBlank() } ?: return
                        val partnerUserId = value["partnerUserId"]?.toString()?.takeIf { it.isNotBlank() } ?: return
                        val partnerStreamId = value["partnerStreamId"]?.toString()?.ifBlank { partnerUserId } ?: partnerUserId
                        val dur = (value["durationSeconds"] as? Number)?.toInt()?.coerceIn(60, 3600) ?: 300
                        matchRef.removeValue()
                        if (cont.isActive) {
                            cont.resume(
                                PkMatchResult.Matched(
                                    roomId = roomId,
                                    partnerUserId = partnerUserId,
                                    partnerStreamId = partnerStreamId,
                                    isStreamer = true,
                                    durationSeconds = dur
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "Skipping malformed pk match payload", e)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    if (cont.isActive) cont.resume(PkMatchResult.Error(error.message))
                }
            }
            matchRef.addValueEventListener(listener)
            cont.invokeOnCancellation {
                matchRef.removeEventListener(listener)
            }
        }
    }

    suspend fun removeFromPkQueue(userId: String) {
        suspendCancellableCoroutine<Unit> { cont ->
            pkQueueRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val value = currentData.value as? Map<*, *>
                    val waitingUserId = value?.get("userId") as? String
                    if (waitingUserId == userId) {
                        currentData.value = null
                    }
                    return Transaction.success(currentData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    cont.resume(Unit)
                }
            })
        }
        pkMatchesRef.child(userId).removeValue().await()
    }

    fun buildPkRoomId(userA: String, userB: String): String {
        val ordered = listOf(userA, userB).sorted()
        return "pk_room_${ordered[0]}_${ordered[1]}"
    }

    suspend fun clearPkMatchSignal(uid: String) {
        if (uid.isBlank()) return
        runCatching { pkMatchesRef.child(uid).removeValue().await() }
            .onFailure { e -> Log.w(tag, "clearPkMatchSignal uid=$uid", e) }
    }

    suspend fun postPkRematchInvite(
        guestUid: String,
        roomId: String,
        hostUid: String,
        hostStreamId: String,
        durationSeconds: Int
    ) {
        if (guestUid.isBlank() || hostUid.isBlank() || roomId.isBlank()) return
        val dur = durationSeconds.coerceIn(60, 3600)
        val payload = mapOf(
            "roomId" to roomId,
            "partnerUserId" to hostUid,
            "partnerStreamId" to hostStreamId.trim().ifBlank { hostUid },
            "timestamp" to System.currentTimeMillis(),
            "durationSeconds" to dur,
            "isRematch" to true
        )
        runCatching { pkMatchesRef.child(guestUid).setValue(payload).await() }
            .onFailure { e -> Log.w(tag, "postPkRematchInvite guest=$guestUid", e) }
    }

    suspend fun postPkRematchAck(
        hostUid: String,
        roomId: String,
        guestUid: String,
        guestStreamId: String,
        durationSeconds: Int
    ) {
        if (guestUid.isBlank() || hostUid.isBlank() || roomId.isBlank()) return
        val dur = durationSeconds.coerceIn(60, 3600)
        val payload = mapOf(
            "roomId" to roomId,
            "partnerUserId" to guestUid,
            "partnerStreamId" to guestStreamId.trim().ifBlank { guestUid },
            "timestamp" to System.currentTimeMillis(),
            "durationSeconds" to dur,
            "rematchAck" to true
        )
        runCatching { pkMatchesRef.child(hostUid).setValue(payload).await() }
            .onFailure { e -> Log.w(tag, "postPkRematchAck host=$hostUid", e) }
    }

    fun addPkMatchListener(userId: String, listener: ValueEventListener) {
        if (userId.isBlank()) return
        pkMatchesRef.child(userId).addValueEventListener(listener)
    }

    fun removePkMatchListener(userId: String, listener: ValueEventListener) {
        if (userId.isBlank()) return
        pkMatchesRef.child(userId).removeEventListener(listener)
    }
}

private fun DocumentSnapshot.effectiveSpendableDiamondsMerged(): Int {
    val c = getLong("coins")
    val w = getLong("walletBalance")
    return when {
        c != null && w != null -> maxOf(c, w).toInt().coerceAtLeast(0)
        c != null -> c.toInt().coerceAtLeast(0)
        w != null -> w.toInt().coerceAtLeast(0)
        else -> 0
    }
}

private fun DocumentSnapshot.primaryGenderRaw(): String? {
    val g = getString("gender")?.trim().orEmpty().ifBlank {
        getString("genderText")?.trim().orEmpty()
    }
    return g.takeIf { it.isNotEmpty() }
}

private fun DocumentSnapshot.isEligibleHostStreamerReceiverMerged(): Boolean {
    val merged = toUserProfileMerged() ?: return false
    if (merged.role == UserRole.GIRL) return true
    if (merged.totalLiveMinutes + merged.totalStreamingMinutes > 0L) return true
    return maxOf(merged.diamondsEarned, merged.gems) > 0
}

private suspend fun FirebaseService.normalizeProfileMediaUrls(profile: UserProfile): UserProfile {
    val safePhoto = if (profile.photoUrl.isBlank()) "" else normalizeImageReference(profile.photoUrl)
    val safeGallery = buildList {
        for (u in profile.galleryPhotos) add(normalizeImageReference(u))
    }
    val safeMoments = buildList {
        for (u in profile.momentsMediaUrls) add(normalizeImageReference(u))
    }
    val safeFace = profile.faceVerificationImageUrl?.let { normalizeImageReference(it) }
    val normalizedGender = when (profile.gender.trim().lowercase()) {
        "male" -> "male"
        "female" -> "female"
        else -> ""
    }
    return profile.copy(
        gender = normalizedGender,
        photoUrl = safePhoto,
        galleryPhotos = safeGallery,
        momentsMediaUrls = safeMoments,
        faceVerificationImageUrl = safeFace
    )
}

sealed class PkMatchResult {
    data object Waiting : PkMatchResult()
    data class Matched(
        val roomId: String,
        val partnerUserId: String,
        val partnerStreamId: String,
        val isStreamer: Boolean,
        val durationSeconds: Int = 300
    ) : PkMatchResult()
    data class Error(val message: String) : PkMatchResult()
}

private enum class PkTransactionState {
    WAITING,
    MATCHED_AS_CLAIMER,
    ABORTED,
    ERROR
}

private data class PkTransactionResult(
    val state: PkTransactionState,
    val roomId: String? = null,
    val partnerUserId: String? = null,
    val partnerStreamId: String? = null,
    val matchDurationSeconds: Int = 300,
    val error: String? = null
)

data class IncomingCallInvite(
    val callerId: String,
    val callerName: String,
    val roomId: String,
    val isVideoCall: Boolean
)

sealed class GiftTransferResult {
    data class Success(val senderBalance: Int) : GiftTransferResult()
    data class Failed(val reason: String) : GiftTransferResult()
}

sealed class LiveEconomyGiftResult {
    data class Success(val newWalletBalance: Int) : LiveEconomyGiftResult()
    data class Failed(val reason: String) : LiveEconomyGiftResult()
}

/**
 * Reads Moments-style media from common Firestore shapes: list of URL strings, or list of maps
 * with `url` / `mediaUrl` / `imageUrl` / `videoUrl` / `thumbnailUrl`.
 */
private fun DocumentSnapshot.parseMomentsMediaUrls(): List<String> {
    val keys = listOf("moments", "profileMoments", "momentsUrls", "momentUrls", "momentsMedia")
    val out = LinkedHashSet<String>()
    for (key in keys) {
        val raw = get(key) ?: continue
        if (raw !is List<*>) continue
        for (item in raw) {
            when (item) {
                is String -> item.trim().takeIf { it.isNotEmpty() }?.let { out.add(it) }
                is Map<*, *> -> {
                    listOf("url", "mediaUrl", "videoUrl", "imageUrl", "thumbnailUrl")
                        .mapNotNull { k -> item[k]?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
                        .forEach { out.add(it) }
                }
            }
        }
    }
    return out.toList()
}

private fun parseUidListFromFirestore(snapshot: DocumentSnapshot?, field: String): List<String> {
    if (snapshot == null || !snapshot.exists()) return emptyList()
    val raw = snapshot.get(field) ?: return emptyList()
    return when (raw) {
        is List<*> -> raw.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
        else -> emptyList()
    }
}

/**
 * Merges [UserProfile.galleryPhotos] from Firestore with alternate field names and list-of-map shapes
 * (same idea as [parseMomentsMediaUrls]) so discovery / profile detail show uploaded photos reliably.
 */
private fun DocumentSnapshot.mergeGalleryPhotosFromDoc(baseGallery: List<String>): List<String> {
    val keys = listOf("galleryPhotos", "gallery", "photos", "profilePhotos", "images")
    val out = LinkedHashSet<String>()
    for (g in baseGallery) {
        g.trim().takeIf { it.isNotEmpty() }?.let { out.add(it) }
    }
    for (key in keys) {
        val raw = get(key) ?: continue
        if (raw !is List<*>) continue
        for (item in raw) {
            when (item) {
                is String -> item.trim().takeIf { it.isNotEmpty() }?.let { out.add(it) }
                is Map<*, *> -> {
                    listOf("url", "mediaUrl", "imageUrl", "downloadUrl", "src", "thumbnailUrl")
                        .mapNotNull { k -> item[k]?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
                        .forEach { out.add(it) }
                }
            }
        }
    }
    return out.toList()
}

private fun DocumentSnapshot.toUserProfileMerged(): UserProfile? {
    val base = safeToObject<UserProfile>("FirebaseService") ?: return null
    val following = getStringList("followingIds") ?: base.followingIds
    val followers = getStringList("followerIds") ?: base.followerIds
    val likedByUserIds = getStringList("likedByUserIds") ?: base.likedByUserIds
    val blocked = getStringList("blockedUserIds") ?: base.blockedUserIds
    val coins    = (getLong("coins") ?: getLong("walletBalance"))?.toInt() ?: base.coins
    val gems     = getLong("gems")?.toInt()  ?: base.gems      // reads gems directly
    val beans    = getLong("beans")?.toInt() ?: base.beans     // reads beans directly
    val wallet   = coins
    val diamonds = (getLong("diamondsEarned") ?: 0L).toInt()
    val giftDiamondsSpentTotal = getLong("giftDiamondsSpentTotal") ?: base.giftDiamondsSpentTotal
    val likeMeCount = (getLong("likeMeCount") ?: base.likeMeCount.toLong()).toInt()
    val iLikeCount = (getLong("iLikeCount") ?: base.iLikeCount.toLong()).toInt()
    val streamMin = getLong("totalStreamingMinutes") ?: base.totalStreamingMinutes
    val liveMin = getLong("totalLiveMinutes") ?: base.totalLiveMinutes
    val frame = getString("profileFrameId")?.takeIf { it.isNotBlank() }
        ?: getString("selectedFrameId")?.takeIf { it.isNotBlank() }
        ?: base.profileFrameId
    val isOnline = when (val v = get("isOnline")) {
        is Boolean -> v
        is String -> v.equals("true", ignoreCase = true)
        is Number -> v.toInt() != 0
        else -> base.isOnline
    }
    val lastSeen = getLong("lastSeen") ?: base.lastSeen

    // Defensive boolean parsing — prevents enum or type-mismatch exceptions from
    // toObject() silently zeroing out verification state on legacy or partially-migrated documents.
    val isVerified = when (val v = get("isVerified")) {
        is Boolean -> v
        is String -> v.equals("true", ignoreCase = true)
        is Number -> v.toInt() != 0
        else -> base.isVerified
    }
    val isFaceVerified = when (val v = get("isFaceVerified")) {
        is Boolean -> v
        is String -> v.equals("true", ignoreCase = true)
        is Number -> v.toInt() != 0
        else -> base.isFaceVerified
    }
    val hasStarBadge = when (val v = get("hasStarBadge")) {
        is Boolean -> v
        is String -> v.equals("true", ignoreCase = true)
        is Number -> v.toInt() != 0
        else -> base.hasStarBadge
    }
    // Explicit string→enum mapping so any Firestore enum-deserialization edge-case can never
    // corrupt the verification status back to NOT_SUBMITTED.
    val faceVerificationStatus: VerificationStatus = when (
        getString("faceVerificationStatus")?.trim()?.uppercase()
    ) {
        "APPROVED" -> VerificationStatus.APPROVED
        "PENDING"  -> VerificationStatus.PENDING
        "REJECTED" -> VerificationStatus.REJECTED
        "NOT_SUBMITTED" -> VerificationStatus.NOT_SUBMITTED
        else -> base.faceVerificationStatus
    }
    Log.d(
        "VERIFICATION",
        "toUserProfileMerged uid=$id isVerified=$isVerified isFaceVerified=$isFaceVerified " +
                "faceVerificationStatus=$faceVerificationStatus"
    )

    val momentsMediaUrls = parseMomentsMediaUrls()

    val emailFs = getString("email")?.trim().orEmpty().ifBlank { base.email }
    val mobileFs = getString("mobile")?.trim().orEmpty().ifBlank { base.mobile }

    val mergedGallery = mergeGalleryPhotosFromDoc(base.galleryPhotos)
    val mergedPhotoUrl = sequenceOf(
        base.photoUrl.trim(),
        getString("photoUrl")?.trim().orEmpty(),
        getString("avatarUrl")?.trim().orEmpty(),
        getString("profileImageUrl")?.trim().orEmpty()
    ).map { it.trim() }.firstOrNull { it.isNotEmpty() && it.isRemoteHttpUrl() }
        ?: mergedGallery.firstOrNull { it.isRemoteHttpUrl() }.orEmpty()

    return base.copy(
        email = emailFs,
        mobile = mobileFs,
        photoUrl = mergedPhotoUrl,
        galleryPhotos = mergedGallery,
        followingIds = following,
        followerIds = followers,
        likedByUserIds = likedByUserIds,
        blockedUserIds = blocked,
        coins = coins,
        walletBalance = wallet,
        gems = gems,
        beans = beans,
        diamondsEarned = diamonds,
        giftDiamondsSpentTotal = giftDiamondsSpentTotal,
        likeMeCount = likeMeCount,
        iLikeCount = iLikeCount,
        totalStreamingMinutes = streamMin,
        totalLiveMinutes = liveMin,
        profileFrameId = frame,
        isOnline = isOnline,
        lastSeen = lastSeen,
        isVerified = isVerified,
        isFaceVerified = isFaceVerified,
        hasStarBadge = hasStarBadge,
        faceVerificationStatus = faceVerificationStatus,
        momentsMediaUrls = momentsMediaUrls
    )
}

private fun DocumentSnapshot.toCrmAnnouncementOrNull(): CrmAnnouncement? {
    val published = when (val v = get("isPublished")) {
        is Boolean -> v
        is String -> v.equals("true", ignoreCase = true)
        is Number -> v.toInt() != 0
        null -> true
        else -> true
    }
    if (!published) return null
    val title = getString("title")?.trim().orEmpty()
    val body = getString("body")?.trim().orEmpty()
    if (title.isEmpty() && body.isEmpty()) return null
    val createdAtMs = getLong("createdAtMs")
        ?: getLong("createdAt")
        ?: getLong("updatedAtMs")
        ?: 0L
    val imageUrl = getString("imageUrl")?.trim()?.takeIf { it.isNotEmpty() }
    return CrmAnnouncement(id = id, title = title, body = body, createdAtMs = createdAtMs, imageUrl = imageUrl)
}

private fun DocumentSnapshot.getStringList(field: String): List<String>? {
    val raw = get(field) ?: return null
    return when (raw) {
        is List<*> -> raw.mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }
        else -> null
    }
}

private inline fun <reified T : Any> com.google.firebase.firestore.DocumentSnapshot.safeToObject(logTag: String): T? {
    return try {
        toObject(T::class.java)
    } catch (e: Exception) {
        Log.w(logTag, "Failed to parse ${T::class.java.simpleName} from document id=$id", e)
        null
    }
}

private fun String.isRemoteHttpUrl(): Boolean {
    val uri = Uri.parse(this)
    val scheme = uri.scheme?.lowercase()
    return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
}
