package com.zipper.datingapp.data

import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Calendar

enum class UserRole {
    BOY, GIRL
}

enum class Gender {
    MALE, FEMALE
}

@Keep
enum class VerificationStatus {
    NOT_SUBMITTED, PENDING, APPROVED, REJECTED
}

/**
 * High-level 1:1 call UI state. Companion client flags (e.g. one-minute marketing teaser) live on
 * [com.zipper.datingapp.ui.DatingUiState.isFreeTeaserCall], not in this enum.
 */
enum class CallState {
    IDLE,
    /** Outgoing call — local ringback (see [com.zipper.datingapp.call.CallSoundManager]). */
    DIALING,
    /** Incoming call presented to callee. */
    RINGING,
    CONNECTING,
    ACTIVE,
    ENDED
}

/**
 * Admin / CRM broadcast shown under Messages → Announcement (`Firestore crm_announcements/{id}`).
 * CRM writes with Admin SDK or Console; app is read-only.
 */
@Keep
data class CrmAnnouncement(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val createdAtMs: Long = 0L,
    val imageUrl: String? = null
)

enum class AppLanguage(val label: String, val code: String) {
    ENGLISH("English", "en"),
    HINDI("हिन्दी", "hi"),
    GUJARATI("ગુજરાતી", "gu"),
    KONKANI("Konkani", "kok"),
    SPANISH("Español", "es"),
    FRENCH("Français", "fr")
}

@Keep
@IgnoreExtraProperties
data class ProfileFrame(
    val id: String = "",
    val name: String = "",
    val imageUrl: String = "",
    val minLevel: Int = 0,
    val isPremium: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class UserProfile(
    val id: String = "",
    val profileNumber: String = "", // 5-digit unique ID
    val name: String = "",
    val age: Int = 0,
    val gender: String = "",
    val dateOfBirth: Long? = null,
    val role: UserRole = UserRole.BOY,
    val email: String = "",
    val mobile: String = "",
    val city: String = "",
    val distance: Int = 0,
    val bio: String = "",
    val interests: List<String> = emptyList(),
    /** UIDs this user follows (Firestore `arrayUnion` / `arrayRemove`). */
    val followingIds: List<String> = emptyList(),
    /** UIDs who follow this user. */
    val followerIds: List<String> = emptyList(),
    /** UIDs who liked this user's profile from discovery (Firestore `arrayUnion` / `arrayRemove`). */
    @get:Exclude
    val likedByUserIds: List<String> = emptyList(),
    /** UIDs this user has blocked (Firestore `blockedUserIds` array; live moderation can append). */
    val blockedUserIds: List<String> = emptyList(),
    val photoUrl: String = "",
    /** HTTPS URLs; persisted as a Firestore array on `users/{uid}` when saving profile. */
    val galleryPhotos: List<String> = emptyList(),
    /**
     * Image/video URLs from Moments (and similar) on `users/{uid}`. Parsed in [FirebaseService] from
     * fields such as `moments`, `profileMoments`, `momentsUrls`. Excluded from client writes so profile
     * edits never wipe server-managed moment lists.
     */
    @get:Exclude
    val momentsMediaUrls: List<String> = emptyList(),
    /** Decorative avatar frame id (e.g. Gold / Diamond / Neon presets in [profileFrames]). */
    val profileFrameId: String? = null,
    /** Mirrors [coins] for live-economy transactions; dual-written in Firestore. */
    val walletBalance: Int = 0,
    /** Host diamond tally from gifts; dual-written with [gems] where applicable. */
    val diamondsEarned: Int = 0,
    /** Accumulated live-streaming time (minutes); dual-updated with [totalLiveMinutes] for legacy rows. */
    val totalStreamingMinutes: Long = 0,
    
    // Verification System
    val isVerified: Boolean = false, // Face verified (Blue Tick)
    val isFaceVerified: Boolean = false,
    val faceVerificationStatus: VerificationStatus = VerificationStatus.NOT_SUBMITTED,
    val faceVerificationImageUrl: String? = null,
    
    val isFullyVerified: Boolean = false, // Video verified
    val videoVerificationStatus: VerificationStatus = VerificationStatus.NOT_SUBMITTED,
    val videoVerificationUrl: String? = null,
    
    val hasStarBadge: Boolean = false,
    val adminOverride: String = "none",
    val adminNote: String? = null,
    
    val coins: Int = 0,
    val gems: Int = 0,
    val beans: Int = 0,
    val level: Int = 1,
    /** Server-maintained; excluded from Firestore full-profile merges so client saves never wipe counts. */
    @get:Exclude
    val iLikeCount: Int = 0,
    @get:Exclude
    val likeMeCount: Int = 0,
    val earnings: Double = 0.0,
    val isLive: Boolean = false,
    val liveRoomId: String? = null,
    /**
     * When [isLive], mirrors `live_streams/{id}.audioOnlyStream` (batched fetch in [com.zipper.datingapp.ui.DatingViewModel]).
     * Used for discovery / profile PIP: audio-only recv and mic-style preview.
     */
    val liveStreamAudioOnly: Boolean = false,
    /** Firestore + presence pipeline; UI should rely on this for green dot. */
    val isOnline: Boolean = false,
    /** Epoch ms when the user last went offline (Firestore + presence); used for chat "Last seen …". */
    val lastSeen: Long = 0L,
    val viewerCount: Int = 0,
    val compatibility: Int = 0,
    val hasIntroVideo: Boolean = false,
    val rewardsPoints: Int = 0,
    /** Total 💎 spent sending gifts (male giver progression); incremented in gift transactions. */
    val giftDiamondsSpentTotal: Long = 0L,
    val isGuest: Boolean = false,
    val referralCode: String = "",
    
    // Custom Call Prices for Level 4+ Girls
    val customAudioPrice: Int? = null,
    val customVideoPrice: Int? = null,

    // Level tracking stats
    val totalLiveMinutes: Long = 0,
    val liveDaysStreak: Int = 0,
    val lastLiveDate: Long = 0,
    val registrationDate: Long = System.currentTimeMillis()
) {
    val currentAge: Int
        get() {
            val dob = dateOfBirth ?: return age
            val dobCal = Calendar.getInstance().apply { timeInMillis = dob }
            val now = Calendar.getInstance()
            var years = now.get(Calendar.YEAR) - dobCal.get(Calendar.YEAR)
            if (now.get(Calendar.DAY_OF_YEAR) < dobCal.get(Calendar.DAY_OF_YEAR)) {
                years--
            }
            return years.coerceAtLeast(0)
        }

    val genderText: String
        get() {
            val normalized = gender.trim().lowercase()
            return when (normalized) {
                "male", "female" -> normalized
                else -> "unknown"
            }
        }

    val crmGender: String
        get() = when (genderText) {
            "male", "female" -> genderText
            else -> ""
        }

    val hasBlueTick: Boolean
        get() = isVerified ||
            isFaceVerified ||
            faceVerificationStatus == VerificationStatus.APPROVED ||
            adminOverride.equals("force_verified", ignoreCase = true)

    /** True when the user may place/receive billed calls or go live (face flow complete on server). */
    val isCallVerificationApproved: Boolean
        get() = isFaceVerified ||
            hasBlueTick ||
            faceVerificationStatus == VerificationStatus.APPROVED

    /**
     * Effective level for sorting / UX: males from gift 💎 spent, females from beans earned,
     * others from legacy streaming + diamond host formula (capped with stored [level]).
     */
    val currentLevel: Int
        get() = when (genderText) {
            "male" ->
                com.zipper.datingapp.economy.VirtualEconomyMath.maleGiverLevelFromGiftSpend(giftDiamondsSpentTotal)
            "female" ->
                com.zipper.datingapp.economy.VirtualEconomyMath.femaleLevelFromBeans(beans.toLong())
            else ->
                computeHostLevel(totalLiveMinutes + totalStreamingMinutes, maxOf(diamondsEarned, gems))
                    .coerceAtMost(com.zipper.datingapp.economy.VirtualEconomyMath.MAX_APP_LEVEL)
        }

    /**
     * Level shown on profile cards; keeps Firestore [level] if ahead of computed (sync lag).
     */
    val displayProfileLevel: Int
        get() = maxOf(level.coerceAtLeast(1), currentLevel)
            .coerceAtMost(com.zipper.datingapp.economy.VirtualEconomyMath.MAX_APP_LEVEL)
}

/**
 * Stable unique key for Compose lazy lists when [UserProfile.id] may be blank or duplicated in bad data.
 */
fun UserProfile.composeLazyKey(index: Int): String {
    val uid = id.trim()
    if (uid.isNotEmpty()) return uid
    val num = profileNumber.trim()
    if (num.isNotEmpty()) return "pn_${num}_$index"
    return "row_$index"
}

@Keep
data class SentGiftHistoryEntry(
    val giftName: String = "",
    /** From `gift_transactions.giftId` when present; used for catalog icons. */
    val giftId: String = "",
    val totalCost: Int = 0,
    val count: Int = 1,
    val timestampMs: Long = 0L,
)

/**
 * Host-side level (1–99) computed from streaming activity and gift economy.
 *
 * Tier progression (cumulative points):
 *  Lv  1 –  3  Newcomer   : 1 pt per 5 h streamed, 1 pt per 500 diamonds
 *  Lv  4 –  6  Rising Star: 1 pt per 3 h streamed, 1 pt per 300 diamonds
 *  Lv  7 – 10  Star        : 1 pt per 2 h streamed, 1 pt per 200 diamonds
 *  Lv 11 – 20  Super Star  : 1 pt per 1 h streamed, 1 pt per 100 diamonds
 *  Lv 21 – 50  Elite       : 1 pt per 30 min,       1 pt per  50 diamonds
 *  Lv 51 – 99  Legend      : 1 pt per 15 min,       1 pt per  20 diamonds
 *
 * Calling code: [UserProfile.currentLevel] uses [totalLiveMinutes] + [totalStreamingMinutes]
 * as the minute input, and max([diamondsEarned], [gems]) as the diamond input.
 */
fun computeHostLevel(totalMinutes: Long, diamonds: Int): Int {
    val mins = totalMinutes.coerceAtLeast(0L)
    val dias = diamonds.coerceAtLeast(0).toLong()

    // Accumulate raw score using the most generous tier available (always legend rates at max)
    val streamScore = when {
        mins < 300L   -> mins / 300L          // < 5 h → newcomer rate
        mins < 1800L  -> mins / 180L          // < 30 h → rising star rate
        mins < 7200L  -> mins / 120L          // < 120 h → star rate
        mins < 30000L -> mins / 60L           // < 500 h → super star rate
        mins < 90000L -> mins / 30L           // < 1500 h → elite rate
        else          -> mins / 15L           // ≥ 1500 h → legend rate
    }
    val diamondScore = when {
        dias < 2000L   -> dias / 500L
        dias < 10000L  -> dias / 300L
        dias < 40000L  -> dias / 200L
        dias < 200000L -> dias / 100L
        dias < 750000L -> dias / 50L
        else           -> dias / 20L
    }

    return (1L + streamScore + diamondScore).coerceIn(1L, com.zipper.datingapp.economy.VirtualEconomyMath.MAX_APP_LEVEL.toLong()).toInt()
}

/** Threshold points required to reach a given level (for progress-bar calculations). */
fun levelThresholdPoints(level: Int): Long = when {
    level <= 1  -> 0L
    level <= 3  -> (level - 1).toLong() * 10
    level <= 6  -> 20L + (level - 3).toLong() * 20
    level <= 10 -> 80L + (level - 6).toLong() * 40
    level <= 20 -> 240L + (level - 10).toLong() * 80
    level <= 50 -> 1040L + (level - 20).toLong() * 120
    else        -> 4640L + (level - 50).toLong() * 200
}

/** Current combined score for progress-bar display (mirrors [computeHostLevel] accumulation). */
fun currentLevelScore(totalMinutes: Long, diamonds: Int): Long {
    val mins = totalMinutes.coerceAtLeast(0L)
    val dias = diamonds.coerceAtLeast(0).toLong()
    val s = when {
        mins < 300L   -> mins / 300L
        mins < 1800L  -> mins / 180L
        mins < 7200L  -> mins / 120L
        mins < 30000L -> mins / 60L
        mins < 90000L -> mins / 30L
        else          -> mins / 15L
    }
    val d = when {
        dias < 2000L   -> dias / 500L
        dias < 10000L  -> dias / 300L
        dias < 40000L  -> dias / 200L
        dias < 200000L -> dias / 100L
        dias < 750000L -> dias / 50L
        else           -> dias / 20L
    }
    return s + d
}

/** Human-readable name for each level tier. */
fun levelTierName(level: Int): String = when {
    level <= 3  -> "Newcomer"
    level <= 6  -> "Rising Star"
    level <= 10 -> "Star"
    level <= 20 -> "Super Star"
    level <= 50 -> "Elite"
    else        -> "Legend"
}

@Keep
@IgnoreExtraProperties
data class Message(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    /** Deterministic DM thread key: [dmChatId] of the two participant UIDs (Firestore query + rules). */
    val chatId: String = "",
    val text: String = "",
    val type: String = "text",
    val giftId: String = "",
    val giftImageUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val aiSuggested: Boolean = false,
    val sentiment: String = "NEUTRAL"
)

/** Firestore path: `live_streams/{streamId}/messages/{docId}` (public live chat). */
@Keep
@IgnoreExtraProperties
data class LiveStreamChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    /** `"text"` (default), `"gift"`, or `"system"` (join notices, moderation hints). */
    val type: String = "text",
    /** Mirrors [type] == `"system"` when older clients omit [type]. */
    val isSystemMessage: Boolean = false,
    val giftId: String = "",
    val giftImageUrl: String? = null,
    /** CRM / catalog: MP4 URL so receivers can play [GiftOverlay] without local catalog entry. */
    val giftVideoUrl: String? = null,
    /** CRM / catalog: separate MP3 / audio when not embedded in [giftVideoUrl]. */
    val giftSoundUrl: String? = null,
    /** CRM: [Gift.displayDurationSeconds] for overlay timing (0–12; 0 = receiver uses default min). */
    val giftDisplayDurationSeconds: Int = 0,
    val giftName: String = "",
    /** PK / call-room gifts: UID of the streamer who received this gift (tug-of-war attribution). */
    val giftRecipientId: String = "",
    val giftCount: Int = 1
) {
    fun isLiveSystemMessage(): Boolean = type == "system" || isSystemMessage
}

/** Live tab: main discover vs dedicated audio-party lobby (Itzo-style `fragment_audio`). */
@Keep
enum class LiveDiscoverSurface {
    MAIN,
    AUDIO_PARTY_LOBBY,
}

/** Filters the horizontal live carousel on discover. */
@Keep
enum class LiveDiscoverLiveFilter {
    ALL,
    AUDIO_PARTY_ONLY,
}

/** Firestore `streams/{hostUid}/video_call_requests/{fromUserId}`. */
@Keep
data class AudioPartyVideoCallRequest(
    val fromUserId: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val requestedAtMs: Long = 0L,
    /** `pending`, `accepted`, `dismissed`. */
    val status: String = "pending",
)

/** Snapshot of `live_streams/{streamId}` for audience UI. */
data class LiveStreamRoomInfo(
    val viewerCount: Int = 0,
    /** Wall-clock ms when the host started this live session (Firestore); 0 if unknown. */
    val streamStartedAtMillis: Long = 0L,
    /** Host-moderation: viewers whose UIDs are listed here must leave immediately. */
    val kickedUsers: List<String> = emptyList(),
    /** PK battle economy (optional; older docs omit — defaults apply). */
    val pkHostBeans: Long = 0L,
    val pkGuestBeans: Long = 0L,
    val pkStartedAtMillis: Long = 0L,
    /**
     * Canonical PK host battler uid (same on both `live_streams/{host}` and mirrored `live_streams/{guest}`).
     * When absent, clients infer host from the stream document id for legacy docs.
     */
    val pkBattleHostUid: String = "",
    /** Canonical PK guest/challenger battler uid. */
    val pkBattleGuestUid: String = "",
    /** PK guest Firebase uid (stream doc id is the live host). */
    val pkGuestUserId: String = "",
    val pkHostDisplayName: String = "",
    val pkGuestDisplayName: String = "",
    /** Round length for PK HUD / strict cutoff (seconds); default 300 when omitted on older docs. */
    val pkDurationSeconds: Int = 300,
    /** When false, [FirebaseService.sendGift] must not apply live PK bean increments (late gifts). */
    val pkScoringOpen: Boolean = true,
    /**
     * Spectators who picked the host vs challenger for this PK (incremented on choice, decremented on leave).
     * Stored on `live_streams/{hostUid}`; total room viewers remain in [viewerCount].
     */
    val pkHostViewerCount: Int = 0,
    val pkGuestViewerCount: Int = 0,
    /** Firebase uids of spectators counted toward [pkHostViewerCount] (arrayUnion/arrayRemove in Firestore). */
    val pkHostViewerUserIds: List<String> = emptyList(),
    /** Firebase uids of spectators counted toward [pkGuestViewerCount]. */
    val pkGuestViewerUserIds: List<String> = emptyList(),
    /** Solo host answered a private call; watchers show busy overlay + system chat. */
    val hostPrivateCallBusy: Boolean = false,
    /**
     * Host is broadcasting mic-only (no camera); mirrors Firestore `audioOnlyStream`.
     * Viewers should use [audioOnly] WebRTC and a non-video stage UI when not in PK.
     */
    val audioOnlyStream: Boolean = false,
    /**
     * Audio-party stage layout: 6, 10, or 14 “seats” (host + `streams/{id}` guest slots).
     * Mirrors Firestore `audioPartySeatCount`; when [audioOnlyStream] is false, treated as unused (default 10).
     */
    val audioPartySeatCount: Int = 10,
    /** False when the Firestore `live_streams/{id}` document is missing (host ended stream or deleted doc). */
    val streamActive: Boolean = true,
    /**
     * Solo audio-party: former host doc is briefly updated so clients can follow `live_streams/{uid}`
     * and `streams/{uid}` for [audioPartyHandoffToUid] after a host transfer.
     */
    val audioPartyHandoffToUid: String = "",
    /**
     * After PK scoring closes ([pkScoringOpen] false), wall-clock ms when the shared results/rematch window ends.
     * Written by [com.zipper.datingapp.service.FirebaseService.closePkBattleScoring]; spectators align on this field.
     */
    val pkResultPhaseEndsAtMillis: Long = 0L,
)

/** Client PK session state (ViewModel + optional Firestore mirror on [LiveStreamRoomInfo]). */
@Keep
enum class PkBattleOutcome {
    VICTORY,
    DEFEAT,
    DRAW,
    /** Spectator / audience: host team won the tug-of-war. */
    HOST_WINS,
    /** Spectator / audience: guest team won the tug-of-war. */
    GUEST_WINS,
}

/**
 * Client PK round lifecycle. Only [ACTIVE] accepts score merges and live gift PK increments locally.
 */
@Keep
enum class PkBattlePhase {
    ACTIVE,
    RESOLVING,
    FINISHED
}

/** Shared wall-clock window after PK ends for rematch / find opponent / continue solo (Firestore + local HUD). */
const val PK_POST_RESULT_LINGER_MS = 30_000L

/**
 * Signaling payload for PK rematch / invite flows (persisted or bundled where needed).
 */
@Keep
data class PkBattleRequest(
    val challengerUserId: String = "",
    val hostStreamId: String = "",
    val durationSeconds: Int = 300
)

@Keep
data class PkBattleSessionState(
    val pkStartTimeMillis: Long = 0L,
    val hostBeansEarned: Long = 0L,
    val guestBeansEarned: Long = 0L,
    val hostUserId: String = "",
    val guestUserId: String = "",
    val battleEnded: Boolean = false,
    /** Filled when [battleEnded]; from local user's perspective. */
    val outcome: PkBattleOutcome? = null,
    /** Host share of tug bar in [0,1]; frozen when [battleEnded] so animation stops. */
    val frozenTugRatio: Float? = null,
    /** PK round duration (seconds); default 300 (5 min). */
    val durationSeconds: Int = 300,
    val phase: PkBattlePhase = PkBattlePhase.ACTIVE,
    /** Wall-clock ms when [battleEnded] results/actions UI should auto-dismiss; synced via Firestore for spectators. */
    val resultPhaseEndsAtMillis: Long = 0L,
)

/**
 * Generic synchronized game payload stored on `battles/{battleId}` (field map [gameStatePayload])
 * with monotonic [version] updated only via [FirebaseService.applyBattleGameInput].
 */
@Keep
@IgnoreExtraProperties
data class GameState(
    val battleId: String = "",
    val version: Long = 0L,
    val phase: String = "LOBBY",
    val lastActorId: String = "",
    val updatedAtMs: Long = 0L,
    /** Arbitrary small game fields (scores, turn, etc.); keep values Firestore-serializable. */
    val data: Map<String, Any> = emptyMap()
)

@Keep
@IgnoreExtraProperties
data class Match(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val photoUrl: String = "",
    val lastMessage: String = "",
    val lastMessageTime: Long = 0,
    val unreadCount: Int = 0
)

@Keep
@IgnoreExtraProperties
data class Gift(
    val id: String = "",
    val name: String = "",
    val price: Int = 0,
    /** Local catalog / merged default; not required on Firestore documents. */
    @param:DrawableRes val iconRes: Int = 0,
    val thumbnailUrl: String = "",
    val videoUrl: String = "",
    val soundUrl: String = "",
    /**
     * Known categories: STANDARD, 3D_PREMIUM, CRM_PREMIUM, LUCKY.
     * Lucky tab gifts load from Firestore `crm_lucky_gifts`; premium CRM gifts from `crm_gifts`
     * (documents with `category: "LUCKY"` there are excluded from Premium but are not required for Lucky).
     */
    val category: String = "STANDARD",
    val points: Int = 0,
    /** True when sourced from CRM Firestore (`crm_gifts` or `crm_lucky_gifts`). */
    val isCrmGift: Boolean = false,
    /** Firestore document ID under `crm_gifts/{id}` or `crm_lucky_gifts/{id}`. */
    val crmDocId: String = "",
    /**
     * CRM: minimum on-screen seconds for [GiftOverlay] (0–12 in Firestore; 0 = app default ~12s for CRM).
     * Mirrored on live gift messages as [LiveStreamChatMessage.giftDisplayDurationSeconds].
     */
    val displayDurationSeconds: Int = 0
)

/**
 * Prefer non-empty MP4 / MP3 URLs from the live gift Firestore row over a catalog [Gift] so receivers
 * do not lose audio when `crm_gifts` is stale or omits [Gift.soundUrl].
 */
fun Gift.withMergedLiveGiftMessage(msg: LiveStreamChatMessage): Gift {
    val v = msg.giftVideoUrl?.trim().orEmpty()
    val s = msg.giftSoundUrl?.trim().orEmpty()
    val thumb = msg.giftImageUrl?.trim().orEmpty()
    val mergedVideo = v.ifEmpty { videoUrl }
    val mergedSound = s.ifEmpty { soundUrl }
    val mergedThumb = thumb.ifEmpty { thumbnailUrl }
    val mergedName = msg.giftName.takeIf { it.isNotBlank() }?.trim()
        ?: name.takeIf { it.isNotBlank() }
        ?: msg.text.trim().takeIf { it.isNotBlank() }
        ?: name
    val crmFromUrls = mergedVideo.isNotEmpty() || mergedSound.isNotEmpty()
    return copy(
        name = mergedName,
        thumbnailUrl = mergedThumb,
        videoUrl = mergedVideo,
        soundUrl = mergedSound,
        displayDurationSeconds = msg.giftDisplayDurationSeconds.coerceIn(0, 12),
        category = if (crmFromUrls) "CRM_PREMIUM" else category,
        isCrmGift = isCrmGift || crmFromUrls
    )
}

val availableGifts = listOf(
    Gift(id = "1", name = "Rose", price = 10, category = "STANDARD", points = 5),
    Gift(id = "2", name = "Fire", price = 25, category = "STANDARD", points = 10),
    Gift(id = "3", name = "Bouquet", price = 50, category = "STANDARD", points = 15),
    Gift(id = "4", name = "Cake", price = 100, category = "STANDARD", points = 20),
    Gift(id = "5", name = "Diamond", price = 250, category = "STANDARD", points = 30),
    Gift(id = "6", name = "Crown", price = 500, category = "3D_PREMIUM", points = 50),
    Gift(id = "7", name = "Rocket", price = 1000, category = "3D_PREMIUM", points = 100),
    Gift(id = "8", name = "Lightning", price = 150, category = "STANDARD"),
    Gift(id = "9", name = "Super Car", price = 5000, category = "3D_PREMIUM", points = 500),
    Gift(id = "10", name = "Private Jet", price = 10000, category = "3D_PREMIUM", points = 1000),
    Gift(id = "11", name = "Castle", price = 25000, category = "3D_PREMIUM", points = 2500)
)

val profileFrames = listOf(
    ProfileFrame("gold", "Gold", "", 1, false),
    ProfileFrame("diamond", "Diamond", "", 1, false),
    ProfileFrame("neon", "Neon", "", 1, false),
    ProfileFrame("f1", "Basic Blue", "", 1, false),
    ProfileFrame("f2", "Soft Pink", "", 1, false),
    ProfileFrame("f3", "Leafy Green", "", 1, false),
    ProfileFrame("p1", "Royal Gold", "", 3, true),
    ProfileFrame("p2", "Neon Purple", "", 3, true),
    ProfileFrame("p3", "Diamond Shine", "", 3, true),
    ProfileFrame("p4", "Fire Aura", "", 4, true),
    ProfileFrame("p5", "Ocean Waves", "", 4, true),
    ProfileFrame("p6", "Starry Night", "", 5, true),
    ProfileFrame("p7", "Dragon Soul", "", 5, true),
    ProfileFrame("p8", "Cherry Blossom", "", 6, true),
    ProfileFrame("p9", "Cyber Tech", "", 6, true),
    ProfileFrame("p10", "Godly Glow", "", 6, true)
)

@Keep
enum class GameType(val label: String, val icon: String) {
    LIVE_PK("Live Battle", "⚡"),
    QUIZ("Quiz Battle", "❓"),
    SPIN("Spin & Win", "🎡"),
    MYSTERY("Mystery Box", "🎁"),
    KARAOKE("Karaoke Battle", "🎤"),
    DANCING("Dancing Challenge", "💃"),
    EMOJI("Emoji Guess", "🤔")
}

@Keep
enum class BattleStatus {
    /** Host created a lobby; audience can see and tap to join. */
    WAITING,
    STARTING_SOON,
    LIVE,
    FINISHED
}

@Keep
@IgnoreExtraProperties
data class SpinSector(
    val name: String = "",
    val emoji: String = "🎁",
    val value: Int = 0
)

@Keep
enum class MysteryRewardType {
    DIAMONDS, BEANS
}

@Keep
@IgnoreExtraProperties
data class MysteryBoxContent(
    val id: Int = 0,
    val rewardType: MysteryRewardType = MysteryRewardType.DIAMONDS,
    val amount: Int = 0,
    val isOpened: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class EmojiChallenge(
    val emoji: String = "",
    val rightName: String = "",
    val options: List<String> = emptyList(),
    val rewardAmount: Int = 0,
    val isSolved: Boolean = false,
    val solvedBy: String? = null
)

@Keep
@IgnoreExtraProperties
data class Battle(
    val id: String = "",
    val gameType: GameType = GameType.LIVE_PK,
    /** Host UID (mirrors [girl1Id] for hosted lobbies). */
    val hostId: String = "",
    val girl1Id: String = "",
    val girl2Id: String = "",
    val girl1Name: String = "",
    val girl2Name: String = "",
    val girl1Score: Int = 0,
    val girl2Score: Int = 0,
    val girl1Votes: Int = 0,
    val girl2Votes: Int = 0,
    val girl1GiftsValue: Int = 0,
    val girl2GiftsValue: Int = 0,
    val status: BattleStatus = BattleStatus.STARTING_SOON,
    val startTime: Long = 0,
    val duration: Int = 5,
    val prizePool: Int = 1000,
    val viewerCount: Int = 0,
    val supportersG1: List<String> = emptyList(),
    val supportersG2: List<String> = emptyList(),
    val spinSectors: List<SpinSector> = emptyList(),
    val lastWinnerSectorIndex: Int? = null,
    val isSpinning: Boolean = false,
    val mysteryBoxes: List<MysteryBoxContent> = emptyList(),
    
    // Turn-based emoji game fields
    val currentEmojiChallenge: EmojiChallenge? = null,
    val currentTurnUserId: String = "",
    val emojiGameTurnCount: Int = 0,
    
    // Spin loop fields
    val spinBreakTimeLeft: Int = 0,
    val spinBettingTimeLeft: Int = 0,
    val isWaitingForGirlToUpdate: Boolean = false,
    /** True once a player has completed their spin in the current round. */
    val girl1HasSpun: Boolean = false,
    val girl2HasSpun: Boolean = false,
    /** Server-epoch ms when the post-spin break started (0 = not in break). */
    val spinBreakStartedAtMs: Long = 0L
)

@Keep
@IgnoreExtraProperties
data class QuizQuestion(
    val question: String = "",
    val options: List<String> = emptyList(),
    val correctAnswerIndex: Int = 0
)

@Keep
@IgnoreExtraProperties
data class BattleResult(
    val winnerId: String = "",
    val winnerName: String = "",
    val girl1Score: Int = 0,
    val girl2Score: Int = 0,
    val rewardsEarned: Double = 0.0,
    val winnerVotes: Int = 0
)

@Keep
@IgnoreExtraProperties
data class CoinPackage(
    val id: String = "",
    /** Base diamonds credited before bonus. */
    val coins: Int = 0,
    val priceInr: Int = 0,
    /** Extra diamonds on top of [coins]; UI shows [totalDiamonds] = coins + bonus. */
    val bonus: Int = 0,
    val isPopular: Boolean = false
) {
    val totalDiamonds: Int
        get() = (coins.toLong() + bonus.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/** In-app diamond top-up catalog (INR); tiers from product spec. */
val coinPackages = listOf(
    CoinPackage("1", 4100, 100, 50),
    CoinPackage("2", 8200, 200, 300),
    CoinPackage("3", 16470, 390, 300),
    CoinPackage("4", 33370, 790, 600, isPopular = true),
    CoinPackage("5", 66650, 1550, 1200),
    CoinPackage("6", 168090, 3900, 2200),
    CoinPackage("7", 342000, 7900, 3200),
    CoinPackage("8", 515151, 11900, 5000),
    CoinPackage("9", 1017315, 23500, 9635)
)

@Keep
@IgnoreExtraProperties
data class ResellAgent(
    val country: String = "",
    val flag: String = "",
    val resellerName: String = "",
    val phone: String = "",
    val whatsappLink: String = "",
    val customMessage: String = "",
    val order: Long = 0,
    val status: String = "active"
)

val resellAgents = listOf(
    ResellAgent("India", "🇮🇳", "Official Reseller IN", "91XXXXXXXXXX", "https://wa.me/91XXXXXXXXXX", "Hi, I want to buy diamonds", 1, "active"),
    ResellAgent("Global", "🌐", "Global Support", "XXXXXXXXXXXX", "https://wa.me/XXXXXXXXXXXX", "Hi, I want to buy diamonds", 2, "active")
)

val discoverProfiles = listOf(
    UserProfile(
        id = "user_101",
        profileNumber = "12345",
        name = "Sarah",
        age = 24,
        gender = "female",
        role = UserRole.GIRL,
        city = "Mumbai",
        distance = 3,
        bio = "Searching for the best cold brew in the city ☕",
        interests = listOf("Coffee", "Photography", "Travel"),
        photoUrl = "https://i.pravatar.cc/150?img=1",
        isVerified = true,
        compatibility = 92,
        isOnline = true,
        isLive = true,
        level = 5,
        videoVerificationStatus = VerificationStatus.APPROVED
    ),
    UserProfile(
        id = "user_102",
        profileNumber = "23456",
        name = "Arjun",
        age = 28,
        gender = "male",
        role = UserRole.BOY,
        city = "Bangalore",
        distance = 12,
        bio = "Work hard, trek harder. Mountain lover 🏔️",
        interests = listOf("Hiking", "Fitness", "Coding"),
        photoUrl = "https://i.pravatar.cc/150?img=12",
        isVerified = false,
        compatibility = 78,
        isOnline = true,
        level = 3
    ),
    UserProfile(
        id = "user_103",
        profileNumber = "34567",
        name = "Priya",
        age = 22,
        gender = "female",
        role = UserRole.GIRL,
        city = "Delhi",
        distance = 5,
        bio = "Classical dancer and weekend baker 🧁",
        interests = listOf("Dancing", "Baking", "History"),
        photoUrl = "https://i.pravatar.cc/150?img=5",
        isVerified = true,
        compatibility = 88,
        isOnline = false,
        isLive = false,
        level = 4
    )
)
