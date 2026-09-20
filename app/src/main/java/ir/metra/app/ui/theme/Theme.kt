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

private val GreenPrimary = Color(0xFF0F4C3A)
private val GreenContainer = Color(0xFFB8E6D2)
private val GreenOnContainer = Color(0xFF00211A)
private val AmberSecondary = Color(0xFF8A5A00)
private val AmberContainer = Color(0xFFFFDDB0)
private val AmberOnContainer = Color(0xFF2C1A00)
private val RedTertiary = Color(0xFFB3261E)

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
    background = Color(0xFFFBFDF9),
    onBackground = Color(0xFF161C19),
    surface = Color(0xFFFBFDF9),
    onSurface = Color(0xFF161C19),
    surfaceVariant = Color(0xFFE1E7E1),
    onSurfaceVariant = Color(0xFF434945),
    outline = Color(0xFF737975),
    outlineVariant = Color(0xFFC3C7C1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD6B8),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF00513F),
    onPrimaryContainer = GreenContainer,
    secondary = Color(0xFFF2BE77),
    onSecondary = Color(0xFF472D00),
    secondaryContainer = Color(0xFF654100),
    onSecondaryContainer = AmberContainer,
    tertiary = Color(0xFFFFB4AB),
    onTertiary = Color(0xFF690005),
    background = Color(0xFF111412),
    onBackground = Color(0xFFE1E4E0),
    surface = Color(0xFF111412),
    onSurface = Color(0xFFE1E4E0),
    surfaceVariant = Color(0xFF434945),
    onSurfaceVariant = Color(0xFFC3C7C1),
    outline = Color(0xFF8D938D),
    outlineVariant = Color(0xFF434945),
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
    displayLarge = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 48.sp),
    headlineLarge = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
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
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
