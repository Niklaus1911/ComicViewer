package com.comicviewer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BlueDarkColorScheme = darkColorScheme(
    primary = Sky80,
    secondary = Mint80,
    tertiary = Coral80
)

private val BlueLightColorScheme = lightColorScheme(
    primary = Sky40,
    secondary = Mint40,
    tertiary = Coral40
)

private val GreenDarkColorScheme = darkColorScheme(
    primary = Green80,
    secondary = GreenSecondary80,
    tertiary = GreenTertiary80
)

private val GreenLightColorScheme = lightColorScheme(
    primary = Green40,
    secondary = GreenSecondary40,
    tertiary = GreenTertiary40
)

private val RedDarkColorScheme = darkColorScheme(
    primary = Red80,
    secondary = RedSecondary80,
    tertiary = RedTertiary80
)

private val RedLightColorScheme = lightColorScheme(
    primary = Red40,
    secondary = RedSecondary40,
    tertiary = RedTertiary40
)

private val PurpleDarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleSecondary80,
    tertiary = PurpleTertiary80
)

private val PurpleLightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleSecondary40,
    tertiary = PurpleTertiary40
)

private val OrangeDarkColorScheme = darkColorScheme(
    primary = Orange80,
    secondary = OrangeSecondary80,
    tertiary = OrangeTertiary80
)

private val OrangeLightColorScheme = lightColorScheme(
    primary = Orange40,
    secondary = OrangeSecondary40,
    tertiary = OrangeTertiary40
)

@Composable
fun ComicViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    appColor: AppColor = AppColor.BLUE,
    themePreset: AppThemePreset = AppThemePreset.Default,
    amoledTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> themePreset.colorScheme(darkTheme, appColor)
    }
    val colorScheme = if (darkTheme && amoledTheme) {
        baseColorScheme.copy(
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White,
            surfaceVariant = Color.Black,
            onSurfaceVariant = Color(0xFFE0E0E0),
            surfaceContainer = Color.Black,
            surfaceContainerHigh = Color.Black,
            surfaceContainerHighest = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainerLowest = Color.Black,
        )
    } else {
        baseColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private fun AppColor.colorScheme(darkTheme: Boolean) = when (this) {
    AppColor.BLUE -> if (darkTheme) BlueDarkColorScheme else BlueLightColorScheme
    AppColor.GREEN -> if (darkTheme) GreenDarkColorScheme else GreenLightColorScheme
    AppColor.RED -> if (darkTheme) RedDarkColorScheme else RedLightColorScheme
    AppColor.PURPLE -> if (darkTheme) PurpleDarkColorScheme else PurpleLightColorScheme
    AppColor.ORANGE -> if (darkTheme) OrangeDarkColorScheme else OrangeLightColorScheme
}

private val InkDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB7C8FF),
    secondary = Color(0xFF8BD8C9),
    tertiary = Color(0xFFFFBD7D),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0D1117),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF263242),
    onSurfaceVariant = Color(0xFFC8D3E0),
    surfaceContainerLowest = Color(0xFF090C10),
    surfaceContainerLow = Color(0xFF111821),
    surfaceContainer = Color(0xFF151D28),
    surfaceContainerHigh = Color(0xFF1C2633),
    surfaceContainerHighest = Color(0xFF243044),
    outlineVariant = Color(0xFF3A4656),
)

private val InkLightColorScheme = lightColorScheme(
    primary = Color(0xFF285EA8),
    secondary = Color(0xFF0F6C5F),
    tertiary = Color(0xFF8A4D00),
    background = Color(0xFFF7FAFF),
    onBackground = Color(0xFF171C22),
    surface = Color(0xFFF7FAFF),
    onSurface = Color(0xFF171C22),
    surfaceVariant = Color(0xFFDCE7F3),
    onSurfaceVariant = Color(0xFF3F4854),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F5FB),
    surfaceContainer = Color(0xFFEAF0F8),
    surfaceContainerHigh = Color(0xFFE3EAF3),
    surfaceContainerHighest = Color(0xFFDCE4EE),
    outlineVariant = Color(0xFFBFC8D4),
)

private val PaperDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE6C85C),
    secondary = Color(0xFFA3D5C8),
    tertiary = Color(0xFFFFB4A9),
    background = Color(0xFF17130C),
    onBackground = Color(0xFFF0E6D2),
    surface = Color(0xFF17130C),
    onSurface = Color(0xFFF0E6D2),
    surfaceVariant = Color(0xFF4B4233),
    onSurfaceVariant = Color(0xFFD7C8AF),
    surfaceContainerLowest = Color(0xFF100D08),
    surfaceContainerLow = Color(0xFF201A10),
    surfaceContainer = Color(0xFF261F14),
    surfaceContainerHigh = Color(0xFF30281A),
    surfaceContainerHighest = Color(0xFF3A3121),
    outlineVariant = Color(0xFF675B48),
)

private val PaperLightColorScheme = lightColorScheme(
    primary = Color(0xFF735F00),
    secondary = Color(0xFF426A60),
    tertiary = Color(0xFF954B43),
    background = Color(0xFFFFFBF1),
    onBackground = Color(0xFF211B11),
    surface = Color(0xFFFFFBF1),
    onSurface = Color(0xFF211B11),
    surfaceVariant = Color(0xFFECE0C9),
    onSurfaceVariant = Color(0xFF4D4638),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF2E1),
    surfaceContainer = Color(0xFFF4EBD9),
    surfaceContainerHigh = Color(0xFFEFE4D0),
    surfaceContainerHighest = Color(0xFFE8DEC9),
    outlineVariant = Color(0xFFCFC2AA),
)

private val NeonDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00D8FF),
    secondary = Color(0xFFFF73CD),
    tertiary = Color(0xFFD8F75D),
    background = Color(0xFF070A12),
    onBackground = Color(0xFFE8F4FF),
    surface = Color(0xFF070A12),
    onSurface = Color(0xFFE8F4FF),
    surfaceVariant = Color(0xFF233047),
    onSurfaceVariant = Color(0xFFCAD7EE),
    surfaceContainerLowest = Color(0xFF03050A),
    surfaceContainerLow = Color(0xFF0B1020),
    surfaceContainer = Color(0xFF10162A),
    surfaceContainerHigh = Color(0xFF171F35),
    surfaceContainerHighest = Color(0xFF1E2840),
    outlineVariant = Color(0xFF40506E),
)

private val NeonLightColorScheme = lightColorScheme(
    primary = Color(0xFF00677B),
    secondary = Color(0xFF9D1D76),
    tertiary = Color(0xFF586B00),
    background = Color(0xFFF8FAFF),
    onBackground = Color(0xFF161B24),
    surface = Color(0xFFF8FAFF),
    onSurface = Color(0xFF161B24),
    surfaceVariant = Color(0xFFDCE4F5),
    onSurfaceVariant = Color(0xFF414756),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F4FF),
    surfaceContainer = Color(0xFFEAEFFF),
    surfaceContainerHigh = Color(0xFFE3E9F8),
    surfaceContainerHighest = Color(0xFFDDE3F2),
    outlineVariant = Color(0xFFC0C7D8),
)

private val AuroraDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7ADCCD),
    secondary = Color(0xFFC8BFFF),
    tertiary = Color(0xFFFFB0CC),
    background = Color(0xFF0A1415),
    onBackground = Color(0xFFDCEDEA),
    surface = Color(0xFF0A1415),
    onSurface = Color(0xFFDCEDEA),
    surfaceVariant = Color(0xFF2D4444),
    onSurfaceVariant = Color(0xFFBDD2D0),
    surfaceContainerLowest = Color(0xFF050D0E),
    surfaceContainerLow = Color(0xFF102021),
    surfaceContainer = Color(0xFF142627),
    surfaceContainerHigh = Color(0xFF1B3032),
    surfaceContainerHighest = Color(0xFF233A3C),
    outlineVariant = Color(0xFF465D5E),
)

private val AuroraLightColorScheme = lightColorScheme(
    primary = Color(0xFF006C60),
    secondary = Color(0xFF6257A5),
    tertiary = Color(0xFF984B6C),
    background = Color(0xFFF4FBF8),
    onBackground = Color(0xFF18201F),
    surface = Color(0xFFF4FBF8),
    onSurface = Color(0xFF18201F),
    surfaceVariant = Color(0xFFD7E9E4),
    onSurfaceVariant = Color(0xFF3C4B49),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFECF5F2),
    surfaceContainer = Color(0xFFE6F0ED),
    surfaceContainerHigh = Color(0xFFE0EAE7),
    surfaceContainerHighest = Color(0xFFDAE4E1),
    outlineVariant = Color(0xFFBBCBC7),
)

private fun AppThemePreset.colorScheme(
    darkTheme: Boolean,
    appColor: AppColor,
) = when (this) {
    AppThemePreset.Default -> appColor.colorScheme(darkTheme)
    AppThemePreset.Ink -> if (darkTheme) InkDarkColorScheme else InkLightColorScheme
    AppThemePreset.Paper -> if (darkTheme) PaperDarkColorScheme else PaperLightColorScheme
    AppThemePreset.Neon -> if (darkTheme) NeonDarkColorScheme else NeonLightColorScheme
    AppThemePreset.Aurora -> if (darkTheme) AuroraDarkColorScheme else AuroraLightColorScheme
}
