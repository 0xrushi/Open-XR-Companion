package com.inmoair.xrcompanion.client.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val XRTypography = Typography(
    headlineLarge  = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge     = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium    = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge      = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelLarge     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium    = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)
