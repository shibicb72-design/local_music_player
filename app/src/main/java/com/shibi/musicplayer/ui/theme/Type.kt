package com.shibi.musicplayer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.shibi.musicplayer.R

@OptIn(ExperimentalTextApi::class)
val Jakarta = FontFamily(
    Font(
        R.font.plus_jakarta_sans,
        FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.plus_jakarta_sans,
        FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.plus_jakarta_sans,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
    Font(
        R.font.plus_jakarta_sans,
        FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800)),
    ),
)

@OptIn(ExperimentalTextApi::class)
val NotoTamil = FontFamily(
    Font(
        R.font.noto_sans_tamil,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
    Font(
        R.font.noto_sans_tamil,
        FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
)

val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Jakarta),
        displayMedium = displayMedium.copy(fontFamily = Jakarta),
        displaySmall = displaySmall.copy(fontFamily = Jakarta),
        headlineLarge = headlineLarge.copy(
            fontFamily = Jakarta,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
        ),
        headlineMedium = headlineMedium.copy(
            fontFamily = Jakarta,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
        ),
        headlineSmall = headlineSmall.copy(
            fontFamily = Jakarta,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
        ),
        titleLarge = titleLarge.copy(
            fontFamily = Jakarta,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
        ),
        titleMedium = titleMedium.copy(
            fontFamily = Jakarta,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        ),
        titleSmall = titleSmall.copy(
            fontFamily = Jakarta,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        ),
    )
}
