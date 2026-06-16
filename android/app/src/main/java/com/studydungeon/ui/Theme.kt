package com.studydungeon.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Тема оформления Study Dungeon в стиле подземелья.
 *
 * Палитра всегда тёмная («подземелье»), независимо от системной темы: камень,
 * золото, пергамент. Цвета [androidx.compose.material3.ColorScheme] подобраны
 * так, чтобы стандартные компоненты Material3 (кнопки, индикаторы, чипы)
 * автоматически вписывались в стиль, а декоративные панели/иконки берут
 * акценты из [Dungeon].
 */
private val DungeonColors = darkColorScheme(
    primary = Dungeon.Gold,
    onPrimary = Color(0xFF231605),
    primaryContainer = Color(0xFF4A371C),
    onPrimaryContainer = Dungeon.GoldBright,
    secondary = Dungeon.Teal,
    onSecondary = Color(0xFF06201C),
    secondaryContainer = Color(0xFF1E3B36),
    onSecondaryContainer = Color(0xFFBFEDE4),
    background = Color(0xFF140F0A),
    onBackground = Dungeon.Parchment,
    surface = Color(0xFF2A1F15),
    onSurface = Dungeon.Parchment,
    surfaceVariant = Color(0xFF3C2D1E),
    onSurfaceVariant = Dungeon.ParchmentMuted,
    outline = Dungeon.GoldTrim,
    outlineVariant = Color(0xFF5C4427),
    error = Dungeon.Health,
    onError = Color(0xFF2A0A06),
    errorContainer = Color(0xFF4A1F18),
    onErrorContainer = Color(0xFFF3C9C0),
)

/**
 * Типографика: декоративный кириллический [Dungeon.DisplayFont] (Ruslan Display)
 * применяется к заголовочным стилям; тело/подписи остаются системным шрифтом
 * ради читабельности.
 */
private val DungeonTypography: Typography
    @Composable get() {
        val base = Typography()
        return base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = Dungeon.DisplayFont),
            displayMedium = base.displayMedium.copy(fontFamily = Dungeon.DisplayFont),
            displaySmall = base.displaySmall.copy(fontFamily = Dungeon.DisplayFont),
            headlineLarge = base.headlineLarge.copy(fontFamily = Dungeon.DisplayFont),
            headlineMedium = base.headlineMedium.copy(fontFamily = Dungeon.DisplayFont),
            headlineSmall = base.headlineSmall.copy(fontFamily = Dungeon.DisplayFont),
            titleLarge = base.titleLarge.copy(fontFamily = Dungeon.DisplayFont),
            titleMedium = base.titleMedium.copy(fontFamily = Dungeon.DisplayFont),
            titleSmall = base.titleSmall.copy(fontFamily = Dungeon.DisplayFont),
        )
    }

@Composable
fun StudyDungeonTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DungeonColors,
        typography = DungeonTypography,
        content = content,
    )
}
