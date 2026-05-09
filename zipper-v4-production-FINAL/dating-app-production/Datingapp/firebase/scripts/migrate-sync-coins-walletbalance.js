#!/usr/bin/env node
/**
 * One-off migration: align Firestore users/{uid} spendable diamonds.
 *
 * The Android app treats `coins` as authoritative, then falls back to `walletBalance`
 * (see FirebaseService.toUserProfileMerged). Live gift transactions dual-write both fields.
 * Legacy rows may have only one field updated — this script sets BOTH to the same canonical int.
 *
 * Canonical value per user:
 *   - If `coins` is set (number) → use floor(coins)
 *   - Else if `walletBalance` is set → use floor(walletBalance)
 *   - Else → skip document (no spendable fields)
 *
 * Run (from this directory):
 *   npm install
 *   export GOOGLE_APPLICATION_CREDENTIALS="/absolute/path/to/serviceAccount.json"
 *   DRY_RUN=1 node migrate-sync-coins-walletbalance.js    # preview counts only
 *   node migrate-sync-coins-walletbalance.js               # apply
 *
 * Requires a Firebase service account with Firestore read/write on the target project.
 */

const admin = require("firebase-admin");

function toInt(v) {
  if (v === undefined || v === null) return null;
  if (typeof v === "number" && Number.isFinite(v)) return Math.trunc(v);
  if (typeof v === "bigint") return Number(v);
  if (typeof v === "object" && typeof v.toNumber === "function") {
    try {
      return Math.trunc(v.toNumber());
    } catch (_) {
      /* fall through */
    }
  }
  const n = Number(v);
  return Number.isFinite(n) ? Math.trunc(n) : null;
}

/** Same priority as Android: coins first, then walletBalance. */
function effectiveSpendable(data) {
  const c = toInt(data.coins);
  const w = toInt(data.walletBalance);
  if (c !== null) return c;
  if (w !== null) return w;
  return null;
}

function fieldsMatchCanonical(data, eff) {
  const c = toInt(data.coins);
  const w = toInt(data.walletBalance);
  return c === eff && w === eff;
}

async function main() {
  const dryRun =
    process.env.DRY_RUN === "1" ||
    process.env.DRY_RUN === "true" ||
    process.env.DRY_RUN === "yes";

  if (!admin.apps.length) {
    admin.initializeApp();
  }

  const db = admin.firestore();
  const collectionPath = process.env.USERS_COLLECTION || "users";
  const pageSize = 300;
  const maxBatchOps = 450;

  let lastDoc = null;
  let wouldUpdate = 0;
  let skippedAligned = 0;
  let skippedNoFields = 0;
  let batchesCommitted = 0;

  let batch = db.batch();
  let batchOps = 0;

  async function commitBatch() {
    if (batchOps === 0) return;
    if (!dryRun) {
      await batch.commit();
      batchesCommitted++;
    }
    batch = db.batch();
    batchOps = 0;
  }

  for (;;) {
    let q = db
      .collection(collectionPath)
      .orderBy(admin.firestore.FieldPath.documentId())
      .limit(pageSize);
    if (lastDoc) {
      q = q.startAfter(lastDoc);
    }

    const snap = await q.get();
    if (snap.empty) break;

    for (const doc of snap.docs) {
      lastDoc = doc;
      const data = doc.data();
      const eff = effectiveSpendable(data);
      if (eff === null) {
        skippedNoFields++;
        continue;
      }
      if (fieldsMatchCanonical(data, eff)) {
        skippedAligned++;
        continue;
      }

      wouldUpdate++;
      if (dryRun) continue;

      batch.update(doc.ref, { coins: eff, walletBalance: eff });
      batchOps++;
      if (batchOps >= maxBatchOps) {
        await commitBatch();
      }
    }

    if (snap.size < pageSize) break;
  }

  await commitBatch();

  console.log(
    JSON.stringify(
      {
        dryRun,
        collection: collectionPath,
        documentsWouldUpdate: wouldUpdate,
        skippedAlreadyAligned: skippedAligned,
        skippedNoCoinsOrWallet: skippedNoFields,
        batchesCommitted: dryRun ? 0 : batchesCommitted,
      },
      null,
      2
    )
  );
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
