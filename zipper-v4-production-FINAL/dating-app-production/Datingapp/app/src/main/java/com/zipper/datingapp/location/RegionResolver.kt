package com.zipper.datingapp.location

import android.content.Context
import android.location.Geocoder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private const val TAG = "RegionResolver"

/**
 * Resolves a human-readable city/region label from coarse coordinates (no exact GPS stored).
 */
suspend fun resolveCityRegion(context: Context, latitude: Double, longitude: Double): String? {
    if (!Geocoder.isPresent()) return null
    return withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            val a = addresses?.firstOrNull() ?: return@withContext null
            sequenceOf(a.locality, a.subAdminArea, a.adminArea)
                .mapNotNull { it?.trim()?.takeIf { s -> s.isNotBlank() } }
                .firstOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "Geocoder failed", e)
            null
        }
    }
}
