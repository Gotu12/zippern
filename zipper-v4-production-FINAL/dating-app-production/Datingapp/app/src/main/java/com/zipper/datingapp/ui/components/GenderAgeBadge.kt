package com.zipper.datingapp.ui.components

import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Female
import androidx.compose.material.icons.outlined.Male
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Male / female tints stay readable on dark and light surfaces (not theme primary). */
private val MaleIconBlue = Color(0xFF2196F3)
private val FemaleIconPink = Color(0xFFE91E63)
private val UnknownMuted = Color(0xFF94A3B8)

@Composable
fun GenderAgeBadge(
    gender: String,
    age: Int,
    modifier: Modifier = Modifier
) {
    // Male = blue [Icons.Outlined.Male]; Female = pink [Icons.Outlined.Female]. ROOT for stable lowercase.
    val g = gender.trim().lowercase(Locale.ROOT)
    val (icon, tint, contentDescription) = when (g) {
        "female", "f", "woman", "girl" -> Triple(Icons.Outlined.Female, FemaleIconPink, "Female")
        "male", "m", "man", "boy" -> Triple(Icons.Outlined.Male, MaleIconBlue, "Male")
        else -> Triple(null, UnknownMuted, null)
    }
    val bgColor = tint.copy(alpha = 0.28f)
    val borderColor = Color.White.copy(alpha = 0.22f)
    val shape = RoundedCornerShape(percent = 50)

    Row(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
        } else {
            Text(
                text = "•",
                color = UnknownMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = age.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}
