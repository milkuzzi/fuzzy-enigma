package com.studydungeon.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studydungeon.R

/**
 * Общий визуальный язык оформления в стиле подземелья (dungeon): палитра,
 * шрифты, текстуры и переиспользуемые элементы рамок/иконок.
 *
 * Здесь нет игровой логики — только презентационные константы и composables,
 * которыми пользуются экраны и панели. Цвета подобраны под пиксель-арт спрайты
 * в `res/drawable-nodpi` (камень, золото, кровь, опыт-кристалл).
 */
/**
 * Декоративная палитра подземелья. Существует в двух вариантах — тёмном
 * ([DarkDungeonPalette]) и светлом ([LightDungeonPalette]); активный вариант
 * выбирается темой и читается через [Dungeon].
 */
data class DungeonPalette(
    val gold: Color,
    val goldTrim: Color,
    val goldBright: Color,
    val health: Color,
    val xp: Color,
    val teal: Color,
    val parchment: Color,
    val parchmentMuted: Color,
    val stoneTop: Color,
    val stoneBottom: Color,
    val panelEdgeDark: Color,
    val screenBackground: Color
)

/** Тёмная палитра «подземелья» (по умолчанию). */
val DarkDungeonPalette = DungeonPalette(
    gold = Color(0xFFE0A33B),
    goldTrim = Color(0xFFC9A24B),
    goldBright = Color(0xFFF1CE7A),
    health = Color(0xFFC8412E),
    xp = Color(0xFF49B6E0),
    teal = Color(0xFF54BBA8),
    parchment = Color(0xFFEAD9B0),
    parchmentMuted = Color(0xFFB9A479),
    stoneTop = Color(0xFF36281A),
    stoneBottom = Color(0xFF1C140D),
    panelEdgeDark = Color(0xFF0C0805),
    screenBackground = Color(0xFF140F0A)
)

/** Светлая «пергаментная» палитра: тёмный текст на светлых панелях. */
val LightDungeonPalette = DungeonPalette(
    gold = Color(0xFFB9791B),
    goldTrim = Color(0xFFA9833A),
    goldBright = Color(0xFF8A5E12),
    health = Color(0xFFB5341F),
    xp = Color(0xFF2E7FA6),
    teal = Color(0xFF2E8C79),
    parchment = Color(0xFF3A2E1C),
    parchmentMuted = Color(0xFF6F5C3C),
    stoneTop = Color(0xFFF6ECD3),
    stoneBottom = Color(0xFFE7D6B2),
    panelEdgeDark = Color(0xFFBFA468),
    screenBackground = Color(0xFFF3E7CC)
)

object Dungeon {
    /**
     * Активная палитра. Переключается темой ([StudyDungeonTheme]) до композиции
     * содержимого, поэтому акцентные цвета (включая используемые в DrawScope)
     * читаются как обычные свойства без CompositionLocal.
     */
    @Volatile
    var palette: DungeonPalette = DarkDungeonPalette

    // Акцентные цвета (согласованы со спрайтами), берутся из активной палитры.
    val Gold get() = palette.gold
    val GoldTrim get() = palette.goldTrim
    val GoldBright get() = palette.goldBright
    val Health get() = palette.health
    val Xp get() = palette.xp
    val Teal get() = palette.teal
    val Parchment get() = palette.parchment
    val ParchmentMuted get() = palette.parchmentMuted

    // Поверхности панелей.
    val StoneTop get() = palette.stoneTop
    val StoneBottom get() = palette.stoneBottom
    val PanelEdgeDark get() = palette.panelEdgeDark
    val ScreenBackground get() = palette.screenBackground

    // Шрифты с поддержкой кириллицы (Ruslan Display, Press Start 2P) и
    // латинский декоративный (MedievalSharp) для названия приложения.
    val DisplayFont = FontFamily(Font(R.font.ruslandisplay))
    val PixelFont = FontFamily(Font(R.font.pressstart2p))
    val TitleFont = FontFamily(Font(R.font.medievalsharp))
}

/** Скруглённая «каменная» рамка панели с золотым кантом. */
private val PanelShape = RoundedCornerShape(14.dp)

/**
 * Каменная панель подземелья: тёмный каменный градиент с двойным золотым
 * кантом. Заменяет [androidx.compose.material3.Card] во всех секциях UI, сохраняя
 * прежний контракт (вызывающий код передаёт [modifier] и наполнение).
 */
@Composable
fun DungeonPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val goldEdge = Brush.verticalGradient(
        listOf(Dungeon.GoldBright, Dungeon.GoldTrim, Color(0xFF7A5C28))
    )
    val stone = Brush.verticalGradient(listOf(Dungeon.StoneTop, Dungeon.StoneBottom))
    Box(
        modifier = modifier
            .clip(PanelShape)
            .background(Dungeon.PanelEdgeDark)
            .padding(2.dp)
            .clip(PanelShape)
            .border(2.dp, goldEdge, PanelShape)
            .background(stone)
    ) {
        // Внутри панели текст/иконки без явного цвета должны быть светлыми
        // (пергамент) на тёмном камне, а не чёрными по умолчанию.
        CompositionLocalProvider(LocalContentColor provides Dungeon.Parchment) {
            content()
        }
    }
}

/** Пиксель-арт иконка-спрайт из ресурсов фиксированного размера [size]. */
@Composable
fun PixelIcon(
    resId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    Image(
        painter = painterResource(id = resId),
        contentDescription = contentDescription,
        modifier = modifier.size(size)
    )
}

/**
 * Заголовочный баннер приложения: герой-спрайт под названием не размещаем —
 * портрет живёт в панели характеристик. Здесь крупное латинское название
 * «Study Dungeon» декоративным шрифтом и кириллический подзаголовок.
 */
@Composable
fun TitleBanner(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelIcon(
                resId = R.drawable.ic_hourglass,
                contentDescription = null,
                size = 28.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Study Dungeon",
                fontFamily = Dungeon.TitleFont,
                fontSize = 40.sp,
                color = Dungeon.GoldBright,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.width(10.dp))
            PixelIcon(
                resId = R.drawable.ic_potion,
                contentDescription = null,
                size = 28.dp
            )
        }
        Text(
            text = stringResource(R.string.app_subtitle),
            fontFamily = Dungeon.DisplayFont,
            style = MaterialTheme.typography.titleSmall,
            color = Dungeon.ParchmentMuted
        )
    }
}

/**
 * Полноэкранный фон-подземелье: тайл каменной стены с тёмной вуалью (scrim)
 * для читаемости контента поверх него.
 */
@Composable
fun DungeonBackground(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Image(
            painter = painterResource(id = R.drawable.bg_dungeon_wall),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        val scrim = if (Dungeon.palette == LightDungeonPalette) {
            // Светлая тема: пергаментная вуаль вместо тёмной.
            listOf(Color(0xF2F3E7CC), Color(0xE6EFE0C2), Color(0xF7F3E7CC))
        } else {
            listOf(Color(0xCC0B0805), Color(0x99110B07), Color(0xD60B0805))
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(scrim))
        )
    }
}
