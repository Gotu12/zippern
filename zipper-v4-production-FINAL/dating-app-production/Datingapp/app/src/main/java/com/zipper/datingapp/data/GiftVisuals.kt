package com.zipper.datingapp.data

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.zipper.datingapp.R

/**
 * Central gift → visuals. [Gift.iconRes] uses shared placeholders until per-asset drawables exist.
 * UI should use [effectiveIconRes] / [fallbackImageVector] for grids and overlays.
 */
object GiftCatalog {
    data class Entry(
        val canonicalName: String,
        @param:DrawableRes val iconRes: Int,
        val fallbackVector: ImageVector
    )

    private val byId: Map<String, Entry> = mapOf(
        "1" to Entry("Rose", R.drawable.ic_favorite, Icons.Filled.LocalFlorist),
        "2" to Entry("Fire", R.drawable.ic_favorite, Icons.Filled.LocalFireDepartment),
        "3" to Entry("Bouquet", R.drawable.ic_favorite, Icons.Filled.LocalFlorist),
        "4" to Entry("Cake", R.drawable.ic_favorite, Icons.Filled.Favorite),
        "5" to Entry("Diamond", R.drawable.ic_favorite, Icons.Filled.Diamond),
        "6" to Entry("Crown", R.drawable.ic_favorite, Icons.Filled.EmojiEvents),
        "7" to Entry("Rocket", R.drawable.ic_favorite, Icons.Filled.RocketLaunch),
        "8" to Entry("Lightning", R.drawable.ic_favorite, Icons.Filled.Bolt),
        "9" to Entry("Super Car", R.drawable.ic_favorite, Icons.Filled.DirectionsCar),
        "10" to Entry("Private Jet", R.drawable.ic_favorite, Icons.Filled.Flight),
        "11" to Entry("Castle", R.drawable.ic_favorite, Icons.Filled.Apartment)
    )

    private val byNormalizedName: Map<String, Entry> = byId.values.associateBy { entry ->
        entry.canonicalName.lowercase().filter { !it.isWhitespace() }
    }

    fun entryFor(gift: Gift): Entry? {
        val id = gift.id.trim()
        if (id.isNotEmpty() && byId.containsKey(id)) return byId[id]
        val key = gift.name.trim().lowercase().filter { !it.isWhitespace() }
        return byNormalizedName[key]
    }
}

/** Merges Firestore rows with catalog [iconRes] when missing. */
fun Gift.withCatalogDefaults(): Gift {
    val e = GiftCatalog.entryFor(this) ?: return this
    return copy(iconRes = if (iconRes != 0) iconRes else e.iconRes)
}

@DrawableRes
fun Gift.effectiveIconRes(): Int {
    if (iconRes != 0) return iconRes
    return GiftCatalog.entryFor(this)?.iconRes ?: R.drawable.ic_favorite
}

fun Gift.fallbackImageVector(): ImageVector {
    return GiftCatalog.entryFor(this)?.fallbackVector ?: Icons.Filled.CardGiftcard
}

val Gift.cost: Int get() = price

/**
 * Linear gradient for gift orb / grid cells — tiered neon / premium look.
 */
fun Gift.giftOrbGradient(): Brush {
    val id = id.trim()
    val compact = name.trim().lowercase().filter { !it.isWhitespace() }
    return when {
        id == "8" || "lightning" in compact || "bolt" in compact ->
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF00F5FF),
                    Color(0xFF2979FF),
                    Color(0xFF651FFF)
                )
            )
        id == "2" || "fire" in compact ->
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFF3D00),
                    Color(0xFFFFEA00),
                    Color(0xFFFF9100)
                )
            )
        id == "1" || id == "3" || "rose" in compact || "bouquet" in compact || "flower" in compact ->
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFF4081),
                    Color(0xFFE040FB),
                    Color(0xFFFF80AB)
                )
            )
        id == "5" || "diamond" in compact ->
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFECB3),
                    Color(0xFFFFFFFF),
                    Color(0xFFFFD740)
                )
            )
        category.contains("PREMIUM", ignoreCase = true) ->
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFF6B9D),
                    Color(0xFFFFD700),
                    Color(0xFFFF4081)
                )
            )
        else ->
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF536DFE),
                    Color(0xFF7C4DFF),
                    Color(0xFF448AFF)
                )
            )
    }
}

/**
 * Icon tint that reads clearly on [giftOrbGradient].
 */
fun Gift.giftIconTintOnOrb(): Color {
    val id = id.trim()
    val compact = name.trim().lowercase().filter { !it.isWhitespace() }
    return when {
        id == "5" || "diamond" in compact -> Color(0xFF4A148C)
        id == "8" || "lightning" in compact || "bolt" in compact -> Color.White
        id == "2" || "fire" in compact -> Color.White
        else -> Color.White
    }
}

/** Center / edge colors for radial “breathing aura” behind gift overlays. */
fun Gift.giftAuraColors(): Pair<Color, Color> {
    val id = id.trim()
    val compact = name.trim().lowercase().filter { !it.isWhitespace() }
    val inner = when {
        id == "8" || "lightning" in compact || "bolt" in compact -> Color(0xFF00E5FF)
        id == "2" || "fire" in compact -> Color(0xFFFF6E40)
        id == "1" || id == "3" || "rose" in compact || "bouquet" in compact -> Color(0xFFFF4081)
        id == "5" || "diamond" in compact -> Color(0xFFFFD54F)
        category.contains("PREMIUM", ignoreCase = true) -> Color(0xFFFF4081)
        else -> Color(0xFF7C4DFF)
    }
    return inner to Color.Transparent
}
