package org.localar.navigator.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object PinKitaColors {
    // 1. Primary Brand Colors
    val DeepTeal = Color(0xFF0F4C4C)
    val ForestGreen = Color(0xFF2F6B45)
    val PineGreen = Color(0xFF3E7C59)
    val Sage = Color(0xFF9EB89A)
    val AmberAccent = Color(0xFFD89B2B)

    // 2. Secondary / Support Colors
    val MistGreen = Color(0xFFC9D8C1)
    val Moss = Color(0xFF6F8F68)
    val SoftOlive = Color(0xFF8A9B6E)
    val LakeTeal = Color(0xFF5B8D87)

    // 3. Neutral UI Colors
    val BackgroundWarm = Color(0xFFF7F4EC)
    val SurfaceWarm = Color(0xFFFFFDF8)
    val BorderWarm = Color(0xFFDDD8CC)
    val SoftGray = Color(0xFFB9B5AA)
    val SlateText = Color(0xFF4E5B57)
    val TextDark = Color(0xFF16302C)

    // 4. Semantic Colors
    val Success = Color(0xFF4C8B5F)
    val Warning = Color(0xFFD89B2B)
    val Error = Color(0xFFC65A4B)
    val Info = Color(0xFF4E8C89)

    // 5. Gradient Brushes
    val ForestGradient = Brush.linearGradient(listOf(DeepTeal, ForestGreen))
    val NatureBlend = Brush.linearGradient(listOf(PineGreen, Sage))
    val TrailAccent = Brush.linearGradient(listOf(ForestGreen, AmberAccent))
}
