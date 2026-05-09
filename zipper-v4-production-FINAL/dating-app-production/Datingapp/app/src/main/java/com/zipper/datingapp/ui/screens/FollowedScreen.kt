package com.zipper.datingapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.zipper.datingapp.R
import com.zipper.datingapp.ui.paddingWithNavigationBars
import com.zipper.datingapp.data.UserProfile
import com.zipper.datingapp.data.composeLazyKey
import com.zipper.datingapp.ui.DatingUiState
import com.zipper.datingapp.ui.theme.AppResponsiveContentMaxWidth
import com.zipper.datingapp.ui.theme.LocalWindowWidthClass
import com.zipper.datingapp.ui.theme.defaultHorizontalContentPadding

@Composable
fun FollowedScreen(
    uiState: DatingUiState,
    onWatchLive: (UserProfile) -> Unit,
    onOpenProfile: (UserProfile) -> Unit = {},
    /** Loads full profile rows for the Likes tab (Firestore for IDs not in the discovery pool). */
    onRefreshLikedMeList: () -> Unit = {}
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val likesTabTitle = stringResource(R.string.followed_tab_likes)
    val tabs = listOf("Following", "Followers", likesTabTitle)
    val scheme = MaterialTheme.colorScheme
    val widthClass = LocalWindowWidthClass.current
    val hPad = widthClass.defaultHorizontalContentPadding()

    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex == 2) {
            onRefreshLikedMeList()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = AppResponsiveContentMaxWidth)
                .fillMaxSize()
        ) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = scheme.surfaceContainer,
            contentColor = scheme.onSurface,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = scheme.primary
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                val count = when (index) {
                    0 -> uiState.followedProfiles.size
                    1 -> uiState.followerProfiles.size
                    else -> uiState.likedMeProfiles.size
                }
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIndex == index) {
                                    scheme.onSurface
                                } else {
                                    scheme.onSurfaceVariant
                                }
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = if (selectedTabIndex == index) {
                                    scheme.primary
                                } else {
                                    scheme.surfaceVariant
                                },
                                shape = CircleShape
                            ) {
                                Text(
                                    text = count.toString(),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 10.sp,
                                    color = if (selectedTabIndex == index) scheme.onPrimary else scheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                )
            }
        }

        val currentProfiles = when (selectedTabIndex) {
            0 -> uiState.followedProfiles
            1 -> uiState.followerProfiles
            else -> uiState.likedMeProfiles
        }

        if (currentProfiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = scheme.onSurface.copy(alpha = 0.08f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        when (selectedTabIndex) {
                            0 -> "No followed profiles yet."
                            1 -> "No followers yet."
                            else -> stringResource(R.string.followed_likes_empty)
                        },
                        color = scheme.onSurface.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = paddingWithNavigationBars(bottomExtra = 100.dp)
            ) {
                itemsIndexed(currentProfiles, key = { index, p -> p.composeLazyKey(index) }) { _, profile ->
                    val live = uiState.profiles.find { it.id == profile.id }
                        ?: uiState.filteredProfiles.find { it.id == profile.id }
                        ?: profile
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable { onOpenProfile(live) }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = hPad, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                Surface(
                                    modifier = Modifier.size(60.dp),
                                    shape = CircleShape,
                                    color = scheme.surfaceVariant
                                ) {
                                    if (live.photoUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = live.photoUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                live.name.take(1),
                                                color = scheme.onSurface,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                if (live.isOnline) {
                                    Surface(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .align(Alignment.BottomEnd),
                                        shape = CircleShape,
                                        color = scheme.primary,
                                        border = androidx.compose.foundation.BorderStroke(2.dp, scheme.background)
                                    ) {}
                                }
                            }

                            Spacer(Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    live.name,
                                    color = scheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (profile.isLive) {
                                        Surface(color = scheme.error, shape = RoundedCornerShape(4.dp)) {
                                            Text(
                                                "LIVE",
                                                color = scheme.onError,
                                                fontSize = 8.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = if (live.isLive) "Streaming Now" else if (live.isOnline) "Active" else "Offline",
                                        color = if (live.isLive) scheme.error else scheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            if (live.isLive) {
                                Button(
                                    onClick = { onWatchLive(live) },
                                    colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Join Live", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = scheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = scheme.outline.copy(alpha = 0.12f), modifier = Modifier.padding(horizontal = hPad))
                }
            }
        }
        }
    }
}
