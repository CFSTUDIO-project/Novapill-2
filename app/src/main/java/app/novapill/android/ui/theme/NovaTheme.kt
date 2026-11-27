package app.novapill.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** One palette for the pill and the app: pure black, white text tiers, a single crimson accent. */
object NovaColors {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Crimson = Color(0xFFE5173F)
    val CrimsonDeep = Color(0xFF7A0F25)
    val Danger = Color(0xFFFF4256)

    /** Permission wizard: yellow = not enabled yet, green = enabled. */
    val Yellow = Color(0xFFF5B83D)
    val Green = Color(0xFF35C46B)

    val Text1 = Color(0xFFFFFFFF)
    val Text2 = Color(0xB8FFFFFF)
    val Text3 = Color(0x80FFFFFF)
    val Text4 = Color(0x4DFFFFFF)
    val Hairline = Color(0x1FFFFFFF)

    val Surface1 = Color(0xFF0B0B0D)
    val Surface2 = Color(0xFF141417)
    val Surface3 = Color(0xFF1D1D22)
}

/** Type scale. Numbers that tick (timers, clocks) use tabular figures so digits never jitter. */
object NovaType {
    private val family = FontFamily.Default
    private const val TABULAR = "tnum"

    val Display = TextStyle(fontFamily = family, fontSize = 44.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.4f).sp, color = NovaColors.Text1)
    val Headline = TextStyle(fontFamily = family, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6f).sp, color = NovaColors.Text1)
    val Title = TextStyle(fontFamily = family, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2f).sp, color = NovaColors.Text1)
    val Body = TextStyle(fontFamily = family, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, color = NovaColors.Text2)
    val BodyStrong = TextStyle(fontFamily = family, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, color = NovaColors.Text1)
    val Caption = TextStyle(fontFamily = family, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, color = NovaColors.Text3)
    val Label = TextStyle(fontFamily = family, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2f.sp, color = NovaColors.Text3)
    val Button = TextStyle(fontFamily = family, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2f.sp, color = NovaColors.Text1)
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal, color = NovaColors.Text2)

    val NumberBig = TextStyle(fontFamily = family, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8f).sp, fontFeatureSettings = TABULAR, color = NovaColors.Text1)
    val NumberMid = TextStyle(fontFamily = family, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR, color = NovaColors.Text1)
    val NumberSmall = TextStyle(fontFamily = family, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR, color = NovaColors.Text3)
}
