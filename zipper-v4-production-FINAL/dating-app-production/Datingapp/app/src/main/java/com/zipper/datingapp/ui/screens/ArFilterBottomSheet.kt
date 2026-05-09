package com.zipper.datingapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snap.camerakit.lenses.LensesComponent
import com.zipper.datingapp.BuildConfig
import com.zipper.datingapp.webrtc.FilterType

private val beautyFilters = listOf(
    FilterType.NONE,
    FilterType.BEAUTY,
    FilterType.SMOOTH,
    FilterType.BRIGHT,
    FilterType.GLOW,
    FilterType.FAIR,
)

/** Gradient swatch shown in each beauty filter thumbnail. */
private fun beautyGradient(type: FilterType): Brush = when (type) {
    FilterType.NONE   -> Brush.radialGradient(listOf(Color.White.copy(0.35f), Color.White.copy(0.12f)))
    FilterType.BEAUTY -> Brush.linearGradient(listOf(Color(0xFFFFD9A0), Color(0xFFFFB347)))
    FilterType.SMOOTH -> Brush.linearGradient(listOf(Color(0xFFE0C8FF), Color(0xFF9C6FD6)))
    FilterType.BRIGHT -> Brush.linearGradient(listOf(Color(0xFFFFFFCC), Color(0xFFFFE066)))
    FilterType.GLOW   -> Brush.linearGradient(listOf(Color(0xFFFF9A9A), Color(0xFFFF4B4B)))
    FilterType.FAIR   -> Brush.linearGradient(listOf(Color(0xFFB8E4FF), Color(0xFF5BC8FF)))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArFilterBottomSheet(
    selectedFilter: FilterType = FilterType.NONE,
    /** Lenses from [LensesComponent.Repository.QueryCriteria.Available] for your portal group. */
    snapLenses: List<LensesComponent.Lens> = emptyList(),
    selectedSnapLensId: String? = null,
    /** When false (e.g. low-RAM or under-3GB host), hide Snap portal lenses; beauty row stays. */
    snapCameraKitUiEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onFilterSelected: (FilterType) -> Unit,
    /** Pass `null` for Snap "Off" (clear portal lens). */
    onSnapLensSelected: (LensesComponent.Lens?) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1B1B24),
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.25f)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = if (snapCameraKitUiEnabled && BuildConfig.SNAP_CAMERA_KIT_CONFIGURED) {
                    "Face filters · Snap Camera Kit"
                } else {
                    "Face Filters"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )

            // Beauty filters
            Spacer(Modifier.height(18.dp))
            Text(
                text = "BEAUTY",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.50f),
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(beautyFilters) { filter ->
                    val isSelected = filter == selectedFilter
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onFilterSelected(filter) },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(beautyGradient(filter))
                                .then(
                                    if (isSelected) Modifier.border(2.5.dp, Color(0xFFFF4081), CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (filter == FilterType.NONE) {
                                Text("✕", color = Color.White.copy(0.7f), fontSize = 20.sp)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = filter.displayName,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFFFF4081) else Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            // Snap Camera Kit: all lenses in the lens group (see `observe` + `Available` in Camera Kit docs).
            if (snapCameraKitUiEnabled && BuildConfig.SNAP_CAMERA_KIT_CONFIGURED) {
                Spacer(Modifier.height(22.dp))
                Text(
                    text = "SNAP LENSES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.50f),
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    item {
                        val offSelected = selectedSnapLensId == null
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                onSnapLensSelected(null)
                            },
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(beautyGradient(FilterType.NONE))
                                    .then(
                                        if (offSelected) {
                                            Modifier.border(2.5.dp, Color(0xFFFF4081), CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("✕", color = Color.White.copy(0.7f), fontSize = 20.sp)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Off",
                                fontSize = 11.sp,
                                fontWeight = if (offSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (offSelected) Color(0xFFFF4081) else Color.White.copy(alpha = 0.85f),
                            )
                        }
                    }
                    items(snapLenses, key = { it.id }) { lens ->
                        val isSelected = selectedSnapLensId == lens.id
                        val label = lens.name?.trim()?.take(14)?.ifBlank { null }
                            ?: lens.id.take(8)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onSnapLensSelected(lens) },
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF6C63FF), Color(0xFFFF4081)),
                                        ),
                                    )
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.5.dp, Color(0xFFFF4081), CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFFFF4081) else Color.White.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(0.06f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = if (snapCameraKitUiEnabled && BuildConfig.SNAP_CAMERA_KIT_CONFIGURED) {
                        "Add snap.cameraKitApiToken and snap.lensGroupId in local.properties. " +
                            "Staging builds may show a Camera Kit watermark until Snap approves production."
                    } else {
                        "Beauty filters use on-device face detection — best in good lighting."
                    },
                    fontSize = 12.sp,
                    color = Color.White.copy(0.45f),
                )
            }
        }
    }
}
