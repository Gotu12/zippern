package com.zipper.datingapp.ui.util

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.zipper.datingapp.data.UserProfile

/**
 * Prefetches upcoming grid profile photos into Coil disk/memory cache without displaying them.
 */
object ProfileImagePrefetch {

    private const val DEFAULT_AHEAD = 5

    fun prefetchDeckAhead(
        context: Context,
        profiles: List<UserProfile>,
        anchorVisibleIndex: Int,
        aheadCount: Int = DEFAULT_AHEAD,
    ) {
        if (profiles.isEmpty()) return
        val loader = context.imageLoader
        val size = ProfileImageSpecs.DECK_THUMBNAIL_SIZE
        val start = (anchorVisibleIndex + 1).coerceAtLeast(0)
        val end = (start + aheadCount).coerceAtMost(profiles.size)
        for (i in start until end) {
            val url = profiles[i].photoUrl.trim()
            if (url.isEmpty()) continue
            val req = ImageRequest.Builder(context)
                .data(url)
                .size(size)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build()
            loader.enqueue(req)
        }
    }
}
