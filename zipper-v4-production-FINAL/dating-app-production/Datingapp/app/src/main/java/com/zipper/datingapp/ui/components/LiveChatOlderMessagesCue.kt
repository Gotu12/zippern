package com.zipper.datingapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zipper.datingapp.R

/** Circular chip above live chat strip — expands history (replaces full-width “Older messages · tap” row). */
@Composable
fun LiveChatOlderMessagesCue(onOpenHistory: () -> Unit) {
    val cd = stringResource(R.string.live_chat_older_messages_cd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onOpenHistory),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.ExpandLess,
                contentDescription = cd,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
