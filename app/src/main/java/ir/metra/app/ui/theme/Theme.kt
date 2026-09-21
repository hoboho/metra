package ir.metra.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ir.metra.app.R

/**
 * Metra's visual identity.
 *
 * A restrained, professional palette: deep green as the working colour (used for
 * Metra-calculated figures), amber reserved for amounts the *user* recorded, so
 * the two categories stay visually distinct everywhere in the app.
 */

private val GreenPrimary = Color(0xFF176B5B)
private val GreenContainer = Color(0xFFD8F2E9)
private val GreenOnContainer = Color(0xFF00382E)
private val AmberSecondary = Color(0xFF8B5E16)
private val AmberContainer = Color(0xFFFFE8C2)
private val AmberOnContainer = Color(0xFF2D1C00)
private val RedTertiary = Color(0xFFB63B35)

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = GreenContainer,
    onPrimaryContainer = GreenOnContainer,
    secondary = AmberSecondary,
    onSecondary = Color.White,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberOnContainer,
    tertiary = RedTertiary,
    onTertiary = Color.White,
    background = Color(0xFFF7F9F7),
    onBackground = Color(0xFF17201D),
    surface = Color(0xFFFFFEFC),
    onSurface = Color(0xFF17201D),
    surfaceVariant = Color(0xFFE4EAE5),
    onSurfaceVariant = Color(0xFF4B5650),
    outline = Color(0xFF728078),
    outlineVariant = Color(0xFFD1D9D3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DE1CA),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF075443),
    onPrimaryContainer = Color(0xFFD8F2E9),
    secondary = Color(0xFFF8C982),
    onSecondary = Color(0xFF493000),
    secondaryContainer = Color(0xFF674500),
    onSecondaryContainer = AmberContainer,
    tertiary = Color(0xFFFFB4AB),
    onTertiary = Color(0xFF690005),
    background = Color(0xFF101614),
    onBackground = Color(0xFFDEE7E1),
    surface = Color(0xFF171E1B),
    onSurface = Color(0xFFDEE7E1),
    surfaceVariant = Color(0xFF414B46),
    onSurfaceVariant = Color(0xFFC0CAC3),
    outline = Color(0xFF89958E),
    outlineVariant = Color(0xFF414B46),
)

/**
 * Vazirmatn, the app's Persian typeface (SIL Open Font License).
 *
 * Bundled rather than relying on a system font so Persian shaping, digit forms
 * and metrics are identical across devices.
 */
val VazirmatnFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private val MetraTypography = Typography(
    displayLarge = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 50.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Bold, fontSize = 29.sp, lineHeight = 38.sp, letterSpacing = (-0.35).sp),
    headlineMedium = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 34.sp, letterSpacing = (-0.25).sp),
    headlineSmall = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

/**
 * Generous corner radii give the whole app the soft, curved look of the
 * mockups. Because components read these from the theme, one change here
 * rounds every card, button, chip and field consistently.
 */
private val MetraShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * METRA is Persian-only, so the layout direction is pinned to RTL here rather
 * than inherited from the device locale. Without this, an English-locale phone
 * renders Persian text in a left-to-right layout: the text reads correctly but
 * every icon, navigation rail and padding sits on the wrong side.
 */
@Composable
fun MetraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = MetraTypography,
            shapes = MetraShapes,
            content = content,
        )
    }
}
