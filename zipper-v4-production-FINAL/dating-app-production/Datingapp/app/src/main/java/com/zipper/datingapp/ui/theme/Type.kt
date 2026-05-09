package com.zipper.datingapp.ui.theme

import androidx.compose.material3.Typography as M3Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Custom type scale — uses the system / Roboto font family (no Play-downloadable
 * fonts) but applies refined weights and letter-spacing for a modern feel.
 */
val Typography = M3Typography(
    displayLarge   = TextStyle(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
    displayMedium  = TextStyle(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    displaySmall   = TextStyle(fontWeight = FontWeight.Bold,      letterSpacing = (-0.25).sp),
    headlineLarge  = TextStyle(fontWeight = FontWeight.Bold,      letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold,      letterSpacing = (-0.25).sp),
    headlineSmall  = TextStyle(fontWeight = FontWeight.SemiBold),
    titleLarge     = TextStyle(fontWeight = FontWeight.SemiBold),
    titleMedium    = TextStyle(fontWeight = FontWeight.SemiBold,  letterSpacing = 0.1.sp),
    titleSmall     = TextStyle(fontWeight = FontWeight.Medium,    letterSpacing = 0.1.sp),
    bodyLarge      = TextStyle(fontWeight = FontWeight.Normal,    letterSpacing = 0.15.sp),
    bodyMedium     = TextStyle(fontWeight = FontWeight.Normal,    letterSpacing = 0.25.sp),
    bodySmall      = TextStyle(fontWeight = FontWeight.Normal,    letterSpacing = 0.4.sp),
    labelLarge     = TextStyle(fontWeight = FontWeight.SemiBold,  letterSpacing = 0.1.sp),
    labelMedium    = TextStyle(fontWeight = FontWeight.Medium,    letterSpacing = 0.5.sp),
    labelSmall     = TextStyle(fontWeight = FontWeight.Medium,    letterSpacing = 0.5.sp),
)
