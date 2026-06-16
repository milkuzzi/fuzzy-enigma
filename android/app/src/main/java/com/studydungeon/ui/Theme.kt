package com.studydungeon.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import com.studydungeon.data.ThemeChoice

/**
 * Тёмная палитра подземелья: камень, золото, пергамент. Цвета
 * [androidx.compose.material3.ColorScheme] подобраны так, чтобы стандартные
 * компоненты Material3 автоматически вписывались в стиль.
 */
private val DungeonDarkColors = darkColorScheme(
    primary = Color(0xFFE0A33B),
    onPrimary = Color(0xFF231605),
    primaryContainer = Color(0xFF4A371C),
    onPrimaryContainer = Color(0xFFF1CE7A),
    secondary = Color(0xFF54BBA8),
    onSecondary = Color(0xFF06201C),
    secondaryContainer = Color(0xFF1E3B36),
    onSecondaryContainer = Color(0xFFBFEDE4),
    background = Color(0xFF140F0A),
    onBackground = Color(0xFFEAD9B0),
    surface = Color(0xFF2A1F15),
    onSurface = Color(0xFFEAD9B0),
    surfaceVariant = Color(0xFF3C2D1E),
    onSurfaceVariant = Color(0xFFB9A479),
    outline = Color(0xFFC9A24B),
    outlineVariant = Color(0xFF5C4427),
    error = Color(0xFFC8412E),
    onError = Color(0xFF2A0A06),
    errorContainer = Color(0xFF4A1F18),
    onErrorContainer = Color(0xFFF3C9C0),
)

/**
 * Светлая «пергаментная» палитра: тёплый светлый фон, тёмно-коричневый текст,
 * приглушённое золото. Те же акценты, что и в тёмной теме, но с инверсией
 * светлоты для читаемости при дневном освещении.
 */
private val DungeonLightColors = lightColorScheme(
    primary = Color(0xFF8A5E12),
    onPrimary = Color(0xFFFFF6E2),
    primaryContainer = Color(0xFFEAD3A1),
    onPrimaryContainer = Color(0xFF3A2806),
    secondary = Color(0xFF2E8C79),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBFE7DD),
    onSecondaryContainer = Color(0xFF0C2E28),
    background = Color(0xFFF3E7CC),
    onBackground = Color(0xFF3A2E1C),
    surface = Color(0xFFF6ECD3),
    onSurface = Color(0xFF3A2E1C),
    surfaceVariant = Color(0xFFE7D6B2),
    onSurfaceVariant = Color(0xFF6F5C3C),
    outline = Color(0xFFA9833A),
    outlineVariant = Color(0xFFBFA468),
    error = Color(0xFFB5341F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF6D6CE),
    onErrorContainer = Color(0xFF410B04),
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

/**
 * Корневая тема приложения. Выбор темы ([ThemeChoice]) переключает как
 * [androidx.compose.material3.ColorScheme] (для стандартных компонентов
 * Material3), так и декоративную палитру [Dungeon] (для каменных/пергаментных
 * панелей и пиксель-акцентов).
 *
 * Декоративная палитра — глобальная (читается в том числе в DrawScope), поэтому
 * содержимое оборачивается в [key], чтобы при смене темы поддерево
 * перекомпоновалось с новыми цветами.
 */
@Composable
fun StudyDungeonTheme(
    theme: ThemeChoice = ThemeChoice.DARK,
    content: @Composable () -> Unit,
) {
    Dungeon.palette = if (theme == ThemeChoice.LIGHT) LightDungeonPalette else DarkDungeonPalette
    val colors = if (theme == ThemeChoice.LIGHT) DungeonLightColors else DungeonDarkColors
    key(theme) {
        MaterialTheme(
            colorScheme = colors,
            typography = DungeonTypography,
            content = content,
        )
    }
}
