package com.inmoair.xrcompanion.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val XRTypography = Typography(
    headlineLarge  = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold,   color = TextPrimary),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
    titleLarge     = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
    titleMedium    = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium,  color = TextPrimary),
    bodyLarge      = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal,  color = TextPrimary),
    bodyMedium     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal,  color = TextSecondary),
    labelLarge     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
    labelMedium    = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium,  color = TextSecondary),
)
