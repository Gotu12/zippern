# Scaling and performance (incremental)

This document tracks client- and backend-oriented work for higher daily active usage (e.g. ~10k DAU). Implement in priority order after profiling.

## Firestore

- Paginate chat and long history; avoid unbounded snapshot listeners on inactive screens.
- Add composite indexes for new filtered queries; monitor read/write counts in console.
- Consider TTL or scheduled jobs for stale live/presence docs (ops).

## Client (Android)

- Scope Firestore listeners to the active route; cancel in `DisposableEffect` / `onStop` patterns already used for live.
- Coil: enforce size limits on large remote images; reuse memory/disk cache for gift thumbnails where possible.
- Reduce recomposition churn in hot paths (e.g. live chat snapshot application): prefer stable list keys and diff-friendly updates.
- Audit `LazyColumn` item keys on home and live feeds for stable identities.

## Release build

- Enable R8/minify for release after a full regression pass; keep WebRTC, Firebase, and reflection rules in `proguard-rules.pro`.

## Backend / CDN

- Rate-limit sensitive writes; serve gift media from a CDN with cache headers.
- If feed aggregation read costs grow, consider Cloud Functions or precomputed feed documents.
