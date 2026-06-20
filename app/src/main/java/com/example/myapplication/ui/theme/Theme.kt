package com.example.myapplication.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    appColor: AppColor = AppColor.BLUE,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> appColor.colorScheme(darkTheme)
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
