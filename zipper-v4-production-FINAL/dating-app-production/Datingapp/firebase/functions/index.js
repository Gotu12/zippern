/* eslint-disable */
/**
 * Zipper FCM — mirrors [CallFirebaseMessagingService] `message.data["type"]` values.
 *
 * | Export                  | Trigger                          | FCM type        |
 * |-------------------------|----------------------------------|-----------------|
 * | notifyIncomingCall      | RTDB call_invites/{receiverId}   | incoming_call   |
 * | notifyNewMessage        | Firestore messages/{id} create   | message         |
 * | notifyMissedCall        | Firestore calls/{roomId} update  | missed_call     |
 * | notifyLiveStreamGift    | Firestore transactions/{id}      | gift_received   |
 * | notifyUserSocialAndLive | Firestore users/{id} update      | live_start,     |
 * |                         |                                  | new_follower,   |
 * |                         |                                  | profile_liked   |
 * | processLuckyGifts       | HTTPS callable                   | (no FCM)        |
 * | getAgoraToken           | HTTPS callable                   | (no FCM)        |
 *
 * RTDB triggers use RTDB_REGION. Firestore triggers must use the same region as your
 * Firestore database (change FS_REGION if deploy fails).
 *
 * Env config: firebase-functions v7+ — use AGORA_APP_ID / AGORA_APP_CERTIFICATE (and TURN_*)
 * on each callable’s runtime; `functions.config()` is not supported.
 */
const crypto = require("crypto");
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const { RtcTokenBuilder, RtcRole } = require("agora-token");

admin.initializeApp();

const { FieldValue } = require("firebase-admin/firestore");
const db = admin.firestore();
const messaging = admin.messaging();

/** Default RTDB region (must match your Realtime Database). */
const RTDB_REGION = "us-central1";
/** Must match Firestore database location. */
const FS_REGION = "us-central1";

/** Max "X is live" pushes per event (cost / timeout guard). */
const LIVE_START_MAX_FOLLOWERS = 200;

function asStringList(v) {
  if (!v) return [];
  if (!Array.isArray(v)) return [];
  return v.map((x) => String(x || "").trim()).filter(Boolean);
}

function newIdsInArray(beforeArr, afterArr) {
  const before = new Set(asStringList(beforeArr));
  return asStringList(afterArr).filter((id) => !before.has(id));
}

async function displayNameForUser(uid) {
  if (!uid) return "Someone";
  const snap = await db.collection("users").doc(uid).get();
  if (!snap.exists) return "Someone";
  const u = snap.data() || {};
  return String(u.name || u.displayName || u.username || "Someone").slice(0, 120);
}

async function fcmTokenForUser(uid) {
  const snap = await db.collection("users").doc(uid).get();
  if (!snap.exists) return "";
  const u = snap.data() || {};
  return String(u.fcmToken || u.pushToken || "").trim();
}

async function sendDataToToken(token, data, androidPriority = "high") {
  if (!token) return;
  const payload = {
    token,
    data,
    android: { priority: androidPriority },
  };
  await messaging.send(payload);
}

async function sendDataToUser(uid, data, androidPriority = "high") {
  const token = await fcmTokenForUser(uid);
  if (!token) {
    console.warn("sendDataToUser: no fcmToken uid=", uid);
    return;
  }
  await sendDataToToken(token, data, androidPriority);
}

// ── Incoming call (RTDB) ─────────────────────────────────────────────────────

exports.notifyIncomingCall = functions
  .region(RTDB_REGION)
  .database.ref("call_invites/{receiverId}")
  .onWrite(async (change, context) => {
    const after = change.after.val();
    if (!after || String(after.type || "") !== "incoming_call") {
      return null;
    }

    const before = change.before.exists() ? change.before.val() : null;
    const afterTs = Number(after.timestamp) || 0;
    const beforeTs = before && before.timestamp != null ? Number(before.timestamp) : 0;
    if (afterTs <= beforeTs) {
      return null;
    }

    const receiverId = String(context.params.receiverId || "").trim();
    if (!receiverId) {
      return null;
    }

    const userSnap = await db.collection("users").doc(receiverId).get();
    if (!userSnap.exists) {
      console.warn("notifyIncomingCall: no user doc", receiverId);
      return null;
    }

    const u = userSnap.data() || {};
    const token = String(u.fcmToken || u.pushToken || "").trim();
    if (!token) {
      console.warn("notifyIncomingCall: no fcmToken for receiver", receiverId);
      return null;
    }

    const roomId = String(after.roomId || "").trim();
    const callerId = String(after.callerId || "").trim();
    const callerName = String(after.callerName || "Someone").slice(0, 120);
    const rawVideo = after.isVideoCall;
    const isVideo =
      rawVideo === true ||
      rawVideo === "true" ||
      rawVideo === 1 ||
      rawVideo === "1";

    const message = {
      token,
      data: {
        type: "incoming_call",
        callerId,
        callerName,
        receiverId,
        roomId,
        isVideoCall: isVideo ? "true" : "false",
      },
      android: {
        priority: "high",
      },
    };

    try {
      await messaging.send(message);
      console.log("notifyIncomingCall: FCM delivered room=", roomId, "receiver=", receiverId);
    } catch (err) {
      console.error("notifyIncomingCall: FCM send failed", err);
    }
    return null;
  });

// ── DM message (Firestore) ───────────────────────────────────────────────────

exports.notifyNewMessage = functions
  .region(FS_REGION)
  .firestore.document("messages/{messageId}")
  .onCreate(async (snap, context) => {
    const d = snap.data() || {};
    const senderId = String(d.senderId || "").trim();
    const receiverId = String(d.receiverId || "").trim();
    if (!senderId || !receiverId || senderId === receiverId) {
      return null;
    }

    const msgType = String(d.type || "text").toLowerCase();
    let preview = String(d.text || "").trim();
    if (msgType === "gift") {
      preview = preview || "Sent you a gift";
    }
    if (!preview) {
      preview = "New message";
    }
    if (preview.length > 240) {
      preview = preview.slice(0, 237) + "...";
    }

    const receiverSnap = await db.collection("users").doc(receiverId).get();
    if (!receiverSnap.exists) {
      console.warn("notifyNewMessage: no user doc for receiver", receiverId);
      return null;
    }
    const ru = receiverSnap.data() || {};
    const token = String(ru.fcmToken || ru.pushToken || "").trim();
    if (!token) {
      console.warn("notifyNewMessage: no fcmToken for receiver", receiverId);
      return null;
    }

    const senderName = await displayNameForUser(senderId);

    const message = {
      token,
      data: {
        type: "message",
        senderId,
        senderName,
        preview,
      },
      android: {
        priority: "high",
      },
    };

    try {
      await messaging.send(message);
      console.log("notifyNewMessage: FCM delivered to receiver=", receiverId, "from=", senderId);
    } catch (err) {
      console.error("notifyNewMessage: FCM send failed", err);
    }
    return null;
  });

// ── Missed call: calls/{roomId} status → MISSED ──────────────────────────────

exports.notifyMissedCall = functions
  .region(FS_REGION)
  .firestore.document("calls/{roomId}")
  .onUpdate(async (change, context) => {
    const before = change.before.exists ? change.before.data() : {};
    const after = change.after.data() || {};
    const status = String(after.status || "").toUpperCase();
    if (status !== "MISSED") {
      return null;
    }
    const prev = String(before.status || "").toUpperCase();
    if (prev === "MISSED") {
      return null;
    }

    const receiverId = String(after.receiverId || "").trim();
    const callerId = String(after.callerId || "").trim();
    if (!receiverId || !callerId) {
      return null;
    }

    let callerName = String(after.callerName || "").trim();
    if (!callerName) {
      callerName = await displayNameForUser(callerId);
    }

    try {
      await sendDataToUser(
        receiverId,
        {
          type: "missed_call",
          callerName: callerName.slice(0, 120),
          callerId,
        },
        "high"
      );
      console.log("notifyMissedCall: FCM to receiver=", receiverId);
    } catch (err) {
      console.error("notifyMissedCall: FCM failed", err);
    }
    return null;
  });

// ── Live economy gift (not DM — avoids duplicate with notifyNewMessage) ──────

exports.notifyLiveStreamGift = functions
  .region(FS_REGION)
  .firestore.document("transactions/{txId}")
  .onCreate(async (snap, context) => {
    const d = snap.data() || {};
    if (String(d.type || "") !== "LIVE_STREAM_GIFT") {
      return null;
    }
    const senderId = String(d.senderId || "").trim();
    const hostId = String(d.hostId || "").trim();
    const giftName = String(d.giftName || "gift").slice(0, 120);
    if (!senderId || !hostId || senderId === hostId) {
      return null;
    }

    const senderName = await displayNameForUser(senderId);

    try {
      await sendDataToUser(
        hostId,
        {
          type: "gift_received",
          senderId,
          senderName,
          giftName,
        },
        "high"
      );
      console.log("notifyLiveStreamGift: FCM to host=", hostId);
    } catch (err) {
      console.error("notifyLiveStreamGift: FCM failed", err);
    }
    return null;
  });

// ── Live start + new follower + profile like (single users/{id} onUpdate) ─────

exports.notifyUserSocialAndLive = functions
  .region(FS_REGION)
  .firestore.document("users/{userId}")
  .onUpdate(async (change, context) => {
    const userId = String(context.params.userId || "").trim();
    if (!userId) {
      return null;
    }

    const before = change.before.data() || {};
    const after = change.after.data() || {};

    const tasks = [];

    // --- Go live: notify followers (array-contains this user's id in their followingIds) ---
    const wasLive = !!before.isLive;
    const nowLive = !!after.isLive;
    if (!wasLive && nowLive) {
      const hostName = String(after.name || after.displayName || after.username || "Someone").slice(
        0,
        120
      );
      tasks.push(
        (async () => {
          try {
            const q = await db
              .collection("users")
              .where("followingIds", "array-contains", userId)
              .limit(LIVE_START_MAX_FOLLOWERS)
              .get();

            const tokens = [];
            q.docs.forEach((doc) => {
              const u = doc.data() || {};
              const t = String(u.fcmToken || u.pushToken || "").trim();
              if (t) {
                tokens.push(t);
              }
            });

            if (tokens.length === 0) {
              console.log("notifyUserSocialAndLive: live_start no follower tokens host=", userId);
              return;
            }

            const data = {
              type: "live_start",
              hostId: userId,
              hostName,
            };

            const multicast = {
              tokens,
              data,
              android: { priority: "high" },
            };
            const resp = await messaging.sendEachForMulticast(multicast);
            console.log(
              "notifyUserSocialAndLive: live_start host=",
              userId,
              "success=",
              resp.successCount,
              "fail=",
              resp.failureCount
            );
          } catch (err) {
            console.error("notifyUserSocialAndLive: live_start failed", err);
          }
        })()
      );
    }

    // --- New followers: someone new appeared in this user's followerIds ---
    const newFollowerIds = newIdsInArray(before.followerIds, after.followerIds);
    for (const followerId of newFollowerIds) {
      tasks.push(
        (async () => {
          try {
            const followerName = await displayNameForUser(followerId);
            await sendDataToUser(
              userId,
              {
                type: "new_follower",
                followerId,
                followerName,
              },
              "high"
            );
            console.log("notifyUserSocialAndLive: new_follower celeb=", userId, "follower=", followerId);
          } catch (err) {
            console.error("notifyUserSocialAndLive: new_follower FCM failed", err);
          }
        })()
      );
    }

    // --- Profile likes: new uid in likedByUserIds ---
    const newLikerIds = newIdsInArray(before.likedByUserIds, after.likedByUserIds);
    for (const likerId of newLikerIds) {
      tasks.push(
        (async () => {
          try {
            const likerName = await displayNameForUser(likerId);
            await sendDataToUser(
              userId,
              {
                type: "profile_liked",
                likerId,
                likerName,
              },
              "high"
            );
            console.log("notifyUserSocialAndLive: profile_liked target=", userId, "liker=", likerId);
          } catch (err) {
            console.error("notifyUserSocialAndLive: profile_liked FCM failed", err);
          }
        })()
      );
    }

    await Promise.all(tasks);
    return null;
  });

// ── Lucky gifts (callable) — cashback odds + CRM price validation ────────────────────────────
// Matches [DatingViewModel.processLuckyGifts]: deducts `coins` / `walletBalance` (same as client gift transfers).
// Validates unit price against `crm_lucky_gifts` then `crm_gifts` (active).

function isFemaleGender(g) {
  const x = String(g || "")
    .trim()
    .toLowerCase();
  if (!x) return false;
  if (["female", "woman", "girl", "girls", "f"].includes(x)) return true;
  return x.startsWith("female");
}

function isMaleGender(g) {
  const x = String(g || "")
    .trim()
    .toLowerCase();
  if (!x) return false;
  if (["male", "man", "m", "boy"].includes(x)) return true;
  return x.startsWith("male");
}

function femaleLevelFromBeans(beans) {
  const b = Math.max(0, Math.floor(Number(beans) || 0));
  const MAX_APP_LEVEL = 11;
  const FEMALE_LEVEL_4_BEANS = 500000;
  const FEMALE_LEVEL_11_BEANS = 1000000000;
  if (b >= FEMALE_LEVEL_11_BEANS) return MAX_APP_LEVEL;
  if (b >= FEMALE_LEVEL_4_BEANS) {
    const span = FEMALE_LEVEL_11_BEANS - FEMALE_LEVEL_4_BEANS;
    const prog = Math.min(1, Math.max(0, (b - FEMALE_LEVEL_4_BEANS) / span));
    const extra = Math.min(7, Math.max(0, Math.floor(prog * 7)));
    return Math.min(10, Math.max(4, 4 + extra));
  }
  if (b <= 0) return 1;
  if (b < 166667) return 1;
  if (b < 333334) return 2;
  return 3;
}

function maleGiverLevelFromGiftSpend(s) {
  const total = Math.max(0, Math.floor(Number(s) || 0));
  const MAX_APP_LEVEL = 11;
  const L4 = 1000000;
  const L11 = 1000000000;
  if (total >= L11) return MAX_APP_LEVEL;
  if (total >= L4) {
    const span = L11 - L4;
    const prog = Math.min(1, Math.max(0, (total - L4) / span));
    const extra = Math.min(6, Math.max(0, Math.floor(prog * 6)));
    return Math.min(10, Math.max(4, 4 + extra));
  }
  if (total <= 0) return 1;
  if (total < 333334) return 1;
  if (total < 666667) return 2;
  return 3;
}

/** Lucky / CRM-premium callable path: 5% of 💎 to receiver beans (all genders); level from beans only for unambiguous female (matches app / Firebase receiver credit rules). */
function giftReceiverBeansEarnedCrmPremium(totalDiamondCost) {
  const t = Math.floor(Number(totalDiamondCost) || 0);
  if (t <= 0) return 0;
  return Math.floor((t * 5) / 100);
}

function docIsActive(d) {
  const v = d.active;
  if (v === false || v === "false" || v === 0 || v === "0") return false;
  return true;
}

function docPriceMatches(data, priceInt) {
  const raw = data.price;
  const pr = typeof raw === "number" ? raw : Number(raw);
  if (!Number.isFinite(pr)) return false;
  return Math.floor(pr + 1e-9) === priceInt;
}

async function findActiveCrmGiftByPrice(priceInt) {
  const p = Math.floor(Number(priceInt) || 0);
  if (p <= 0) return null;

  async function scanCollection(collName) {
    let snap = await db.collection(collName).where("active", "==", true).get();
    let docs = snap.docs;
    if (docs.length === 0) {
      snap = await db.collection(collName).limit(400).get();
      docs = snap.docs;
    }
    for (const doc of docs) {
      const data = doc.data();
      if (!docIsActive(data)) continue;
      if (!docPriceMatches(data, p)) continue;
      return { doc, collection: collName };
    }
    return null;
  }

  for (const coll of ["crm_lucky_gifts", "crm_gifts"]) {
    const hit = await scanCollection(coll);
    if (hit) return hit;
  }
  return null;
}

/** Read spendable 💎 like the Android client: coins, else walletBalance, else legacy diamondBalance. */
function readSpendableDiamonds(data) {
  const d = data || {};
  const raw = d.coins != null ? d.coins : d.walletBalance != null ? d.walletBalance : d.diamondBalance;
  const n = typeof raw === "number" ? raw : Number(raw);
  return Number.isFinite(n) ? n : 0;
}

/**
 * Bulk lucky cashback roll (production odds):
 * 5% → 100% cashback, 10% → 50%, 25% → 10%, else 0.
 * @returns {{ cashbackAmount: number, finalDeduction: number }}
 */
function computeLuckyCashback(totalCost) {
  const t = Math.floor(Number(totalCost) || 0);
  if (t <= 0) return { cashbackAmount: 0, finalDeduction: 0 };
  let cashbackPercentage = 0;
  const roll = Math.floor(Math.random() * 100) + 1;
  if (roll <= 5) cashbackPercentage = 1.0;
  else if (roll <= 15) cashbackPercentage = 0.5;
  else if (roll <= 40) cashbackPercentage = 0.1;
  const cashbackAmount = Math.floor(t * cashbackPercentage);
  const finalDeduction = t - cashbackAmount;
  return { cashbackAmount, finalDeduction };
}

exports.processLuckyGifts = functions.region(FS_REGION).https.onCall(async (data, context) => {
  const fail = (msg) => ({ success: false, error: msg });

  try {
    if (!context.auth) {
      throw new functions.https.HttpsError("unauthenticated", "Sign in required");
    }
    const senderId = String(context.auth.uid || "").trim();
    const receiverId = String(data.receiverId || "").trim();
    const giftPrice = Number(data.giftPrice);
    const quantity = Number(data.quantity);

    if (!receiverId || receiverId === senderId) {
      return fail("Invalid recipient");
    }
    if (!Number.isFinite(giftPrice) || !Number.isFinite(quantity)) {
      return fail("Invalid amount");
    }
    const priceInt = Math.floor(giftPrice);
    const qtyInt = Math.floor(quantity);
    if (priceInt <= 0 || qtyInt <= 0 || qtyInt > 10000) {
      return fail("Invalid amount");
    }
    const totalCost = priceInt * qtyInt;
    if (!Number.isSafeInteger(totalCost) || totalCost <= 0) {
      return fail("Invalid amount");
    }

    const hit = await findActiveCrmGiftByPrice(priceInt);
    if (!hit) {
      return fail("Invalid gift price");
    }

    const giftName = String(hit.doc.data().name || "Lucky gift").slice(0, 120);
    const giftDocId = hit.doc.id;

    const txId = `lucky_${senderId}_${receiverId}_${Date.now()}_${crypto.randomBytes(8).toString("hex")}`;
    const userGiftDocId = crypto.randomBytes(16).toString("hex");

    let diamondsWonOut = 0;

    await db.runTransaction(async (tx) => {
      const txRef = db.collection("gift_transactions").doc(txId);
      const existing = await tx.get(txRef);
      if (existing.exists) {
        throw new Error("Duplicate transaction");
      }

      const senderRef = db.collection("users").doc(senderId);
      const receiverRef = db.collection("users").doc(receiverId);
      const senderSnap = await tx.get(senderRef);
      const receiverSnap = await tx.get(receiverRef);

      if (!senderSnap.exists) throw new Error("Sender profile missing");
      if (!receiverSnap.exists) throw new Error("Receiver profile missing");

      const sData = senderSnap.data() || {};
      const priorBalance = readSpendableDiamonds(sData);
      if (priorBalance < totalCost) {
        throw new Error("Insufficient balance");
      }

      const { cashbackAmount, finalDeduction } = computeLuckyCashback(totalCost);
      diamondsWonOut = cashbackAmount;
      const neg = -finalDeduction;

      const hasCoinsWallet =
        sData.coins != null || sData.walletBalance != null;
      const senderUpdates = {};
      if (hasCoinsWallet) {
        senderUpdates.coins = FieldValue.increment(neg);
        senderUpdates.walletBalance = FieldValue.increment(neg);
      } else if (sData.diamondBalance != null) {
        senderUpdates.diamondBalance = FieldValue.increment(neg);
      } else {
        senderUpdates.coins = FieldValue.increment(neg);
        senderUpdates.walletBalance = FieldValue.increment(neg);
      }

      const senderGender = sData.gender || sData.genderText || "";
      if (isMaleGender(senderGender) && !isFemaleGender(senderGender)) {
        const prevSpentRaw = sData.giftDiamondsSpentTotal || 0;
        const prevSpent = typeof prevSpentRaw === "number" ? prevSpentRaw : Number(prevSpentRaw);
        const newSpent = Math.floor((Number.isFinite(prevSpent) ? prevSpent : 0) + totalCost);
        senderUpdates.giftDiamondsSpentTotal = FieldValue.increment(totalCost);
        senderUpdates.level = maleGiverLevelFromGiftSpend(newSpent);
      }

      tx.update(senderRef, senderUpdates);

      const rData = receiverSnap.data() || {};
      const receiverGender = rData.gender || rData.genderText || "";
      const beansEarned = giftReceiverBeansEarnedCrmPremium(totalCost);
      if (beansEarned > 0) {
        const prevBeansRaw = rData.beans || 0;
        const prevBeans = typeof prevBeansRaw === "number" ? prevBeansRaw : Number(prevBeansRaw);
        const prevB = Number.isFinite(prevBeans) ? prevBeans : 0;
        const luckyRx = { beans: FieldValue.increment(beansEarned) };
        if (isFemaleGender(receiverGender) && !isMaleGender(receiverGender)) {
          luckyRx.level = femaleLevelFromBeans(prevB + beansEarned);
        }
        tx.update(receiverRef, luckyRx);
      }

      tx.set(txRef, {
        transactionId: txId,
        senderId,
        receiverId,
        giftId: giftDocId,
        giftName,
        giftPrice: priceInt,
        count: qtyInt,
        totalCost,
        cashbackDiamonds: cashbackAmount,
        netDiamondDeduction: finalDeduction,
        type: "LUCKY_GIFT",
        luckySourceCollection: hit.collection,
        serverTime: FieldValue.serverTimestamp(),
      });

      const giftRecordRef = db.collection("UserGifts").doc(userGiftDocId);
      tx.set(giftRecordRef, {
        senderId,
        receiverId,
        giftPrice: priceInt,
        quantity: qtyInt,
        totalDiamondValue: totalCost,
        cashbackDiamonds: cashbackAmount,
        netDiamondDeduction: finalDeduction,
        luckySourceCollection: hit.collection,
        timestamp: FieldValue.serverTimestamp(),
        isLuckyGift: true,
      });
    });

    return { success: true, diamondsWon: diamondsWonOut, transactionId: txId };
  } catch (e) {
    console.error("processLuckyGifts", e);
    if (e instanceof functions.https.HttpsError) throw e;
    const m = String(e.message || "");
    if (m.includes("Insufficient")) return fail("Not enough diamonds");
    if (m.includes("Sender profile missing") || m.includes("Receiver profile missing")) {
      return fail("Invalid recipient");
    }
    if (m.includes("Duplicate transaction")) return fail("Please try again");
    return fail("Could not process lucky gifts");
  }
});

/** Matches Kotlin/JVM [String.hashCode] for Agora uid parity with the Android client. */
function kotlinStringHashCode(str) {
  let h = 0;
  for (let i = 0; i < str.length; i++) {
    h = (Math.imul(31, h) + str.charCodeAt(i)) | 0;
  }
  return h;
}

/** Mirrors [com.zipper.datingapp.agora.AgoraManager.fromFirebaseUid]. */
function agoraUidFromFirebaseUid(uid) {
  const s = String(uid || "").trim();
  if (!s) return 1;
  let h = kotlinStringHashCode(s);
  if (h === 0) h = 1;
  return h & 0x7fffffff;
}

/**
 * Agora RTC token for live channels (solo host/audience, PK co-host, 1:1).
 *
 * **PK:** All battlers and spectators use the **same** `channelName` (e.g. match `roomId` / `pkRoomId`).
 * Each signed-in user calls with their own derived `uid` and `isPublisher: true` for battlers,
 * `isPublisher: false` for spectators — same channel, multiple publishers allowed by role.
 *
 * firebase-functions v7+ does not support `functions.config()` — set **environment variables**
 * on the function (Google Cloud Console → Cloud Functions → getAgoraToken → Edit → Runtime env):
 *   `AGORA_APP_ID`, `AGORA_APP_CERTIFICATE`
 * (or bind the cert from Secret Manager as `AGORA_APP_CERTIFICATE`.)
 *
 * Uses agora-token v2: tokenExpire / privilegeExpire are **seconds from now**, not Unix timestamps.
 */
exports.getAgoraToken = functions.region(FS_REGION).https.onCall((data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in required");
  }
  const channelName = String(data.channelName || "").trim();
  const uidRaw = data.uid;
  const uid = typeof uidRaw === "number" ? Math.trunc(uidRaw) : parseInt(String(uidRaw || ""), 10);
  const rawPub = data.isPublisher;
  const isPublisher =
    rawPub === true ||
    rawPub === "true" ||
    rawPub === 1 ||
    rawPub === "1";

  if (!channelName) {
    throw new functions.https.HttpsError("invalid-argument", "channelName required");
  }
  if (!Number.isFinite(uid) || uid <= 0) {
    throw new functions.https.HttpsError("invalid-argument", "uid required");
  }

  const expected = agoraUidFromFirebaseUid(context.auth.uid);
  if (uid !== expected) {
    throw new functions.https.HttpsError("permission-denied", "uid mismatch");
  }

  const appId = String(process.env.AGORA_APP_ID || "").trim();
  const certificate = String(process.env.AGORA_APP_CERTIFICATE || "").trim();
  if (!appId || !certificate) {
    console.error(
      "getAgoraToken: set AGORA_APP_ID and AGORA_APP_CERTIFICATE on the Cloud Function runtime (v7+ — functions.config removed)",
    );
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Agora is not configured",
    );
  }

  const role = isPublisher ? RtcRole.PUBLISHER : RtcRole.SUBSCRIBER;
  const ttlSec = 24 * 3600;
  const token = RtcTokenBuilder.buildTokenWithUid(
    appId,
    certificate,
    channelName,
    uid,
    role,
    ttlSec,
    ttlSec,
  );
  return { token };
});

/**
 * TURN ICE for WebRTC:
 * - Coturn: set `TURN_SECRET` (+ optional `TURN_HOST`, default YOUR_SERVER_IP) on the function runtime.
 * - Metered-style: `TURN_USERNAME` + `TURN_CREDENTIAL` (env).
 * (v7+ — former `functions.config().turn` / firebase functions:config:set removed; use env vars.)
 */
exports.getTurnCredentials = functions.region(FS_REGION).https.onCall((data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in required");
  }
  const uid = String(context.auth.uid || "").trim();
  if (!uid) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in required");
  }

  const secret = String(process.env.TURN_SECRET || "").trim();
  if (secret) {
    const host = String(process.env.TURN_HOST || "YOUR_SERVER_IP").trim() || "YOUR_SERVER_IP";
    const ttlSec = 24 * 60 * 60;
    const expiryUnix = Math.floor(Date.now() / 1000) + ttlSec;
    const username = `${expiryUnix}:${uid}`;
    const credential = crypto
      .createHmac("sha1", secret)
      .update(username)
      .digest("base64");

    return {
      iceServers: [
        { urls: "stun:stun.l.google.com:19302" },
        {
          urls: [
            `turn:${host}:3478?transport=udp`,
            `turn:${host}:3478?transport=tcp`,
          ],
          username,
          credential,
        },
      ],
    };
  }

  const meteredUser = String(process.env.TURN_USERNAME || "").trim();
  const meteredCred = String(process.env.TURN_CREDENTIAL || "").trim();
  if (meteredUser && meteredCred) {
    return {
      iceServers: [
        { urls: "stun:stun.metered.ca:80" },
        {
          urls: [
            "turn:global.relay.metered.ca:80",
            "turn:global.relay.metered.ca:80?transport=tcp",
            "turns:global.relay.metered.ca:443?transport=tcp",
            "turn:global.relay.metered.ca:3478",
          ],
          username: meteredUser,
          credential: meteredCred,
        },
      ],
    };
  }

  console.error(
    "getTurnCredentials: set TURN_SECRET (+ optional TURN_HOST) or TURN_USERNAME + TURN_CREDENTIAL on the function runtime",
  );
  throw new functions.https.HttpsError(
    "failed-precondition",
    "TURN server is not configured",
  );
});
