package com.comicviewer.ui.theme

import androidx.compose.ui.graphics.Color

val Sky80 = Color(0xFF9BD3F7)
val Mint80 = Color(0xFF93E6C1)
val Coral80 = Color(0xFFFFB4A8)

val Sky40 = Color(0xFF006A96)
val Mint40 = Color(0xFF006C4A)
val Coral40 = Color(0xFF9C4238)

val Green80 = Color(0xFF8EDDA3)
val GreenSecondary80 = Color(0xFFA9D9BE)
val GreenTertiary80 = Color(0xFFFFB59C)

val Green40 = Color(0xFF216D37)
val GreenSecondary40 = Color(0xFF4D6352)
val GreenTertiary40 = Color(0xFF9A4524)

val Red80 = Color(0xFFFFB4AB)
val RedSecondary80 = Color(0xFFE7BDB6)
val RedTertiary80 = Color(0xFFD9C58D)

val Red40 = Color(0xFFBA1A1A)
val RedSecondary40 = Color(0xFF775651)
val RedTertiary40 = Color(0xFF6F5D28)

val Purple80 = Color(0xFFD8B8FF)
val PurpleSecondary80 = Color(0xFFD1BDE0)
val PurpleTertiary80 = Color(0xFFF2B8C6)

val Purple40 = Color(0xFF6D3EB2)
val PurpleSecondary40 = Color(0xFF665A6F)
val PurpleTertiary40 = Color(0xFF81525D)

val Orange80 = Color(0xFFFFB77D)
val OrangeSecondary80 = Color(0xFFE7BFA7)
val OrangeTertiary80 = Color(0xFFC7CC8E)

val Orange40 = Color(0xFF8C5000)
val OrangeSecondary40 = Color(0xFF765845)
val OrangeTertiary40 = Color(0xFF5D641D)

enum class AppColor(
    val label: String,
    val swatch: Color,
) {
    BLUE("Blu", Sky40),
    GREEN("Verde", Green40),
    RED("Rosso", Red40),
    PURPLE("Viola", Purple40),
    ORANGE("Arancione", Orange40),
}

enum class AppThemePreset(
    val preferenceValue: String,
    val primarySwatch: Color,
    val secondarySwatch: Color,
    val tertiarySwatch: Color,
    val proOnly: Boolean,
) {
    Default("default", Sky40, Mint40, Coral40, proOnly = false),
    Ink("ink", Color(0xFF8FB6FF), Color(0xFF81D8C7), Color(0xFFFFB56B), proOnly = true),
    Paper("paper", Color(0xFF7A6400), Color(0xFF3F6E63), Color(0xFF9B4D45), proOnly = true),
    Neon("neon", Color(0xFF00D8FF), Color(0xFFFF5EC4), Color(0xFFD7F75B), proOnly = true),
    Aurora("aurora", Color(0xFF00A891), Color(0xFF7F6CE0), Color(0xFFC15B83), proOnly = true),
}
