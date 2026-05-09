package com.zipper.datingapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zipper.datingapp.R

enum class LiveStreamComplianceBannerVariant {
    /** Compact pill for overlays where the banner sits away from the composer strip. */
    Floating,
    /** Flat-bottom slab visually joined to the composer (same tone as chat field chrome). */
    DockedAboveComposer,
}

/**
 * Static compliance copy flush above the live chat composer (not inside message scroll).
 * Semi-transparent pill (Instagram Live–style) over video; border-only emphasis vs opaque slabs.
 */
@Composable
fun LiveStreamComplianceBanner(
    modifier: Modifier = Modifier,
    variant: LiveStreamComplianceBannerVariant = LiveStreamComplianceBannerVariant.Floating,
) {
    val shape: RoundedCornerShape
    val bgAlpha: Float
    val borderStroke: BorderStroke
    val verticalPad: Dp
    when (variant) {
        LiveStreamComplianceBannerVariant.Floating -> {
            shape = RoundedCornerShape(8.dp)
            bgAlpha = 0.42f
            borderStroke = BorderStroke(0.5.dp, Color(0xFFFBBF24).copy(alpha = 0.38f))
            verticalPad = 5.dp
        }
        LiveStreamComplianceBannerVariant.DockedAboveComposer -> {
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            bgAlpha = 0.4f
            borderStroke = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.32f))
            verticalPad = 6.dp
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = Color.Black.copy(alpha = bgAlpha),
        border = borderStroke,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = verticalPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Color(0xFFFBBF24),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.live_stream_compliance_warning),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 9.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }
    }
}
