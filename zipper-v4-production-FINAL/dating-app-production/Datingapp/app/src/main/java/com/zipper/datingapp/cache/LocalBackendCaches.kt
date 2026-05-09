package com.zipper.datingapp.cache

import android.content.Context
import android.util.Log
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Best-effort clearing of **local client caches** tied to Firebase/backend data (not server-side CDN).
 *
 * Firestore [FirebaseFirestore.clearPersistence] requires active listeners stopped first — callers must
 * detach snapshot collectors before invoking [clearFirestorePersistenceBestEffort].
 */
@OptIn(ExperimentalCoilApi::class)
object LocalBackendCaches {
    private const val TAG = "LocalBackendCaches"

    suspend fun clearFirestorePersistenceBestEffort(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                FirebaseFirestore.getInstance().clearPersistence().await()
                Log.d(TAG, "Firestore disk persistence cleared")
                Unit
            }.onFailure { e ->
                Log.w(TAG, "clearFirestorePersistence failed (may still have active listeners or unsupported config)", e)
            }
        }

    suspend fun clearCoilCaches(context: Context): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val loader = Coil.imageLoader(context.applicationContext)
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
                Log.d(TAG, "Coil memory+disk caches cleared")
                Unit
            }.onFailure { e ->
                Log.w(TAG, "clearCoilCaches", e)
            }
        }
}
