package com.zipper.datingapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zipper.datingapp.R

/** Pill above chat bottom edge — jump from history/staging read position to newest messages. */
@Composable
fun LiveChatNewMessagesCue(
    modifier: Modifier = Modifier,
    onJumpToLatest: () -> Unit,
) {
    val cd = stringResource(R.string.live_chat_new_messages_cd)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.48f))
            .clickable(onClick = onJumpToLatest)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = cd },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.live_chat_new_messages),
            color = Color.White.copy(alpha = 0.95f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(20.dp),
        )
    }
}
