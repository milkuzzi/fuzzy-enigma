package com.studydungeon.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.studydungeon.data.AppPreferences
import com.studydungeon.domain.Hero
import com.studydungeon.domain.RunState
import com.studydungeon.domain.ShopCatalog
import com.studydungeon.domain.ShopItem
import com.studydungeon.domain.TimerState
import com.studydungeon.R

/**
 * Элементы управления сессией и сворачиваемые секции настроек, магазина и
 * инвентаря (задача 12.2). Эти composables размещаются ниже панели характеристик
 * и Таймера в [StudyDungeonScreen], не затрагивая их код.
 *
 * Все элементы stateless относительно доменного состояния: значение [UiState] и
 * колбэки приходят сверху, что сохраняет однонаправленный поток данных и
 * пригодность для preview/тестов.
 *
 * Requirements: 5.6, 5.7, 6.1, 6.2, 6.3, 14.3, 14.4
 */

// region Допустимые диапазоны ввода (R6.1–6.3)

/** Диапазон длительности Фазы_Работы в минутах (R6.1). */
val WORK_MINUTES_RANGE: IntRange = 1..60

/** Диапазон длительности Фазы_Отдыха в минутах (R6.2). */
val BREAK_MINUTES_RANGE: IntRange = 1..30

/** Диапазон количества Помидорок в Серии (R6.3). */
val POMODORO_COUNT_RANGE: IntRange = 1..10

// endregion

// region Controls (R14.3)

/**
 * Элементы управления сессией: запуск, пауза и отказ (сдача) от сессии (R14.3),
 * а также запуск новой Серии после завершения предыдущей (R8.4).
 *
 * Доступность кнопок зависит от состояния выполнения Таймера:
 * - «Старт» доступен, когда Таймер не выполняется ([RunState.RUNNING] выключен);
 * - «Пауза» доступна только во время выполнения;
 * - «Сдаться» доступна, когда сессия идёт или на паузе;
 * - «Новая серия» доступна, когда Таймер остановлен.
 *
 * Requirements: 14.3, 8.4
 */
@Composable
fun SessionControls(
    timer: TimerState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onGiveUp: () -> Unit,
    onStartNewSeries: () -> Unit,
    modifier: Modifier = Modifier
) {
    val running = timer.runState == RunState.RUNNING
    val paused = timer.runState == RunState.PAUSED
    val stopped = timer.runState == RunState.STOPPED

    var showGiveUpConfirm by remember { mutableStateOf(false) }
    var showNewSeriesConfirm by remember { mutableStateOf(false) }

    if (showGiveUpConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_give_up_title),
            text = stringResource(R.string.confirm_give_up_text),
            onConfirm = {
                showGiveUpConfirm = false
                onGiveUp()
            },
            onDismiss = { showGiveUpConfirm = false }
        )
    }
    if (showNewSeriesConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_new_series_title),
            text = stringResource(R.string.confirm_new_series_text),
            onConfirm = {
                showNewSeriesConfirm = false
                onStartNewSeries()
            },
            onDismiss = { showNewSeriesConfirm = false }
        )
    }

    DungeonPanel(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStart,
                    enabled = !running,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(if (paused) R.string.action_resume else R.string.action_start))
                }
                FilledTonalButton(
                    onClick = onPause,
                    enabled = running,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.action_pause))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showGiveUpConfirm = true },
                    enabled = running || paused,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.action_give_up))
                }
                OutlinedButton(
                    onClick = { showNewSeriesConfirm = true },
                    enabled = stopped,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.action_new_series))
                }
            }
        }
    }
}

// endregion

// region Collapsible section scaffold (R14.4)

/**
 * Каркас сворачиваемой секции (R14.4): кликабельный заголовок со стрелкой и
 * раскрывающееся содержимое. Сама секция отрисовывается вызывающим кодом только
 * вне Режима_Фокуса (R13.6).
 *
 * @param title заголовок секции.
 * @param expanded развёрнута ли секция (намеренная видимость пользователя).
 * @param onToggle колбэк переключения видимости секции.
 * @param content содержимое секции, отображаемое при [expanded].
 *
 * Requirements: 14.4
 */
@Composable
fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    DungeonPanel(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Dungeon.GoldBright
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.titleMedium,
                    color = Dungeon.GoldTrim
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    content()
                }
            }
        }
    }
}

// endregion

// region Settings section (R6.1–6.3)

/**
 * Секция настроек длительностей и числа Помидорок с клампингом диапазонов
 * (R6.1–6.3). Значения изменяются степперами, которые по построению не выходят
 * за границы диапазонов, и применяются кнопкой «Применить» через [onApply]
 * (длительности передаются в секундах). При выполняющемся Таймере применение
 * отклоняется на уровне ViewModel (R6.4).
 *
 * Requirements: 6.1, 6.2, 6.3
 */
@Composable
fun SettingsSectionContent(
    timer: TimerState,
    onApply: (workSec: Int, breakSec: Int, total: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Локальное состояние ввода инициализируется из Таймера и пересоздаётся при
    // внешнем изменении настроек (например, после применения/новой Серии).
    var workMinutes by remember(timer.workDurationSec) {
        mutableIntStateOf((timer.workDurationSec / 60).coerceIn(WORK_MINUTES_RANGE))
    }
    var breakMinutes by remember(timer.breakDurationSec) {
        mutableIntStateOf((timer.breakDurationSec / 60).coerceIn(BREAK_MINUTES_RANGE))
    }
    var pomodoros by remember(timer.totalPomodoros) {
        mutableIntStateOf(timer.totalPomodoros.coerceIn(POMODORO_COUNT_RANGE))
    }

    val context = LocalContext.current
    val appPrefs = remember { AppPreferences(context) }
    var soundEnabled by remember { mutableStateOf(appPrefs.soundEnabled) }
    var vibrationEnabled by remember { mutableStateOf(appPrefs.vibrationEnabled) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NumberStepper(
            label = stringResource(R.string.settings_work_minutes),
            value = workMinutes,
            range = WORK_MINUTES_RANGE,
            onValueChange = { workMinutes = it }
        )
        NumberStepper(
            label = stringResource(R.string.settings_break_minutes),
            value = breakMinutes,
            range = BREAK_MINUTES_RANGE,
            onValueChange = { breakMinutes = it }
        )
        NumberStepper(
            label = stringResource(R.string.settings_pomodoros),
            value = pomodoros,
            range = POMODORO_COUNT_RANGE,
            onValueChange = { pomodoros = it }
        )
        Button(
            onClick = {
                // Клампинг к границам диапазонов перед передачей в домен (R6.1–6.3).
                onApply(
                    workMinutes.coerceIn(WORK_MINUTES_RANGE) * 60,
                    breakMinutes.coerceIn(BREAK_MINUTES_RANGE) * 60,
                    pomodoros.coerceIn(POMODORO_COUNT_RANGE)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.action_apply))
        }

        HorizontalDivider(color = Dungeon.GoldTrim.copy(alpha = 0.3f))
        ToggleRow(
            label = stringResource(R.string.settings_sound),
            checked = soundEnabled,
            onCheckedChange = {
                soundEnabled = it
                appPrefs.soundEnabled = it
            }
        )
        ToggleRow(
            label = stringResource(R.string.settings_vibration),
            checked = vibrationEnabled,
            onCheckedChange = {
                vibrationEnabled = it
                appPrefs.vibrationEnabled = it
            }
        )
    }
}

/** Строка с подписью и переключателем в стиле подземелья. */
@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Dungeon.Parchment
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Dungeon.GoldBright,
                checkedTrackColor = Dungeon.Gold,
                uncheckedThumbColor = Dungeon.ParchmentMuted,
                uncheckedTrackColor = Dungeon.StoneTop
            )
        )
    }
}

/** Диалог подтверждения деструктивного действия (сдача / новая серия). */
@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Dungeon.StoneTop,
        titleContentColor = Dungeon.GoldBright,
        textContentColor = Dungeon.Parchment,
        title = { Text(text = title) },
        text = { Text(text = text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.confirm_ok), color = Dungeon.GoldBright)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.confirm_cancel), color = Dungeon.ParchmentMuted)
            }
        }
    )
}

/**
 * Степпер целочисленного значения с клампингом к [range]: кнопки «−»/«+»
 * увеличивают/уменьшают значение, не выходя за границы диапазона.
 */
@Composable
fun NumberStepper(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onValueChange((value - 1).coerceIn(range)) },
                enabled = value > range.first,
                modifier = Modifier.size(40.dp)
            ) {
                Text(text = "−", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(40.dp)
            )
            IconButton(
                onClick = { onValueChange((value + 1).coerceIn(range)) },
                enabled = value < range.last,
                modifier = Modifier.size(40.dp)
            ) {
                Text(text = "+", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

// endregion

// region Shop section (R5.6)

/**
 * Содержимое секции Магазина: каталог доступных предметов с названием и
 * стоимостью в Золоте (R5.6) и кнопкой покупки. Кнопка покупки активна, когда
 * Золота достаточно; недостаток Золота уведомляется на уровне ViewModel (R5.2).
 *
 * Requirements: 5.6
 */
@Composable
fun ShopSectionContent(
    hero: Hero,
    onPurchase: (ShopItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.shop_effect_hint),
            style = MaterialTheme.typography.labelMedium,
            color = Dungeon.ParchmentMuted
        )
        ShopCatalog.items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = Dungeon.GoldTrim.copy(alpha = 0.3f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelIcon(
                    resId = shopItemIcon(item.id),
                    contentDescription = item.displayName,
                    size = 40.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Dungeon.Parchment
                    )
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Dungeon.ParchmentMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelIcon(
                            resId = R.drawable.ic_gold,
                            contentDescription = null,
                            size = 14.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.shop_cost, item.cost),
                            style = MaterialTheme.typography.labelMedium,
                            color = Dungeon.GoldTrim
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onPurchase(item) },
                    enabled = hero.gold >= item.cost
                ) {
                    Text(text = stringResource(R.string.action_buy))
                }
            }
        }
    }
}

// endregion

// region Inventory section (R5.7)

/**
 * Содержимое секции Инвентаря: список названий предметов Героя (R5.7). При
 * пустом Инвентаре показывается поясняющая надпись.
 *
 * Requirements: 5.7
 */
@Composable
fun InventorySectionContent(
    hero: Hero,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.inventory_hint),
            style = MaterialTheme.typography.labelMedium,
            color = Dungeon.ParchmentMuted
        )
        if (hero.inventory.isEmpty()) {
            Text(
                text = stringResource(R.string.inventory_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = Dungeon.ParchmentMuted
            )
        } else {
            hero.inventory.forEach { itemName ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelIcon(
                        resId = R.drawable.ic_scroll,
                        contentDescription = null,
                        size = 18.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = itemName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Dungeon.Parchment
                    )
                }
            }
        }
    }
}

// endregion

// region Statistics

/**
 * Панель дневной статистики: число завершённых Помидорок сегодня и за всё время.
 * Данные приходят сверху из [UiState], панель остаётся stateless.
 */
@Composable
fun StatisticsPanel(
    today: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    DungeonPanel(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.section_stats),
                style = MaterialTheme.typography.titleMedium,
                color = Dungeon.GoldBright
            )
            StatRow(label = stringResource(R.string.stats_today), value = today)
            StatRow(label = stringResource(R.string.stats_total), value = total)
        }
    }
}

@Composable
private fun StatRow(label: String, value: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelIcon(
                resId = R.drawable.ic_hourglass,
                contentDescription = null,
                size = 18.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Dungeon.Parchment
            )
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = Dungeon.GoldBright
        )
    }
}

// endregion

/**
 * Сопоставление идентификатора товара Магазина с пиксель-арт спрайтом.
 * По умолчанию (неизвестный id) используется свиток.
 */
private fun shopItemIcon(itemId: String): Int = when (itemId) {
    "potion" -> R.drawable.ic_potion
    "scroll" -> R.drawable.ic_scroll
    else -> R.drawable.ic_scroll
}

// endregion

// region Previews

@Preview(showBackground = true)
@Composable
private fun SessionControlsPreview() {
    StudyDungeonTheme {
        SessionControls(
            timer = TimerState(runState = RunState.RUNNING),
            onStart = {},
            onPause = {},
            onGiveUp = {},
            onStartNewSeries = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsSectionPreview() {
    StudyDungeonTheme {
        CollapsibleSection(title = "Настройки", expanded = true, onToggle = {}) {
            SettingsSectionContent(timer = TimerState(), onApply = { _, _, _ -> })
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ShopSectionPreview() {
    StudyDungeonTheme {
        CollapsibleSection(title = "Магазин", expanded = true, onToggle = {}) {
            ShopSectionContent(hero = Hero(gold = 30), onPurchase = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun InventorySectionPreview() {
    StudyDungeonTheme {
        CollapsibleSection(title = "Инвентарь", expanded = true, onToggle = {}) {
            InventorySectionContent(hero = Hero(inventory = listOf("Малое зелье")))
        }
    }
}

// endregion
