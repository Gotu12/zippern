package com.zipper.datingapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Standard “blue tick” color (readable on dark UI). */
private val BlueTickColor = Color(0xFF1D9BF0)

@Composable
fun VerifiedBadge(
    showLabel: Boolean = false,
    size: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    val horizontalPadding = size * 0.35f
    val verticalPadding = size * 0.14f
    Row(
        modifier = modifier
            .background(
                color = BlueTickColor.copy(alpha = 0.18f),
                shape = RoundedCornerShape(50)
            )
            .padding(
                horizontal = maxOf(horizontalPadding, 4.dp),
                vertical = maxOf(verticalPadding, 2.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Verified,
            contentDescription = "Verified",
            tint = BlueTickColor,
            modifier = Modifier
                .size(size)
                .padding(end = if (showLabel) size * 0.15f else 0.dp)
        )
        if (showLabel) {
            Text(
                text = "Blue Tick",
                style = MaterialTheme.typography.labelSmall,
                color = BlueTickColor
            )
        }
    }
}
