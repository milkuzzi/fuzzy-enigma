package com.studydungeon.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studydungeon.domain.Debuff
import com.studydungeon.domain.Hero
import com.studydungeon.domain.Phase
import com.studydungeon.domain.PomodoroEngine
import com.studydungeon.domain.RunState
import com.studydungeon.domain.TimerState
import com.studydungeon.R

/**
 * Панель характеристик Героя и отображение Таймера для Compose-UI.
 *
 * Содержит чистые презентационные composables, отображающие доменное состояние
 * ([Hero], [TimerState]) ровно так, как оно хранится (R1.3), без побочной игровой
 * логики. Все игровые правила остаются в доменном слое и ViewModel.
 *
 * Requirements: 1.3, 7.2, 8.2, 14.1, 14.2, 14.5, 14.6
 */

// region Hero stats panel

/**
 * Панель характеристик Героя (R14.1).
 *
 * Отображает имя и уровень, индикатор Опыта (XP), индикатор Здоровья (HP),
 * количество Золота и — при активном Дебаффе — индикатор Дебаффа (R14.6).
 * Значения берутся напрямую из [hero] и отображаются без преобразований (R1.3).
 *
 * Requirements: 1.3, 14.1, 14.6
 */
@Composable
fun HeroStatsPanel(
    hero: Hero,
    modifier: Modifier = Modifier
) {
    DungeonPanel(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Портрет Героя в каменной рамке + имя и уровень.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF15100A))
                        .border(2.dp, Dungeon.GoldTrim, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    PixelIcon(
                        resId = heroPortraitForLevel(hero.level),
                        contentDescription = "Портрет Героя",
                        size = 70.dp
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hero.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Dungeon.GoldBright
                    )
                    Text(
                        text = "Уровень ${hero.level}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Dungeon.Parchment
                    )
                    Text(
                        text = heroRankForLevel(hero.level),
                        style = MaterialTheme.typography.labelMedium,
                        color = Dungeon.GoldTrim
                    )
                }
            }

            // Индикатор Опыта (R14.1).
            StatBar(
                label = "Опыт",
                valueText = "${hero.currentXp} / ${hero.xpToNext}",
                progress = ratio(hero.currentXp, hero.xpToNext),
                color = Dungeon.Xp,
                iconRes = R.drawable.ic_xp
            )

            // Индикатор Здоровья (R14.1).
            StatBar(
                label = "Здоровье",
                valueText = "${hero.currentHp} / ${hero.maxHp}",
                progress = ratio(hero.currentHp, hero.maxHp),
                color = HealthColor,
                iconRes = R.drawable.ic_hp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelIcon(
                        resId = R.drawable.ic_gold,
                        contentDescription = "Золото",
                        size = 22.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${hero.gold}",
                        style = MaterialTheme.typography.titleSmall,
                        color = GoldColor
                    )
                }

                // Индикатор активного Дебаффа: виден ⇔ debuff != NONE (R14.6).
                if (hero.debuff != Debuff.NONE) {
                    DebuffIndicator(debuff = hero.debuff)
                }
            }
        }
    }
}

/**
 * Индикатор активного Дебаффа Героя (R14.6).
 *
 * Отображается вызывающим кодом только когда Дебафф отличается от
 * [Debuff.NONE].
 */
@Composable
fun DebuffIndicator(
    debuff: Debuff,
    modifier: Modifier = Modifier
) {
    AssistChip(
        onClick = {},
        enabled = false,
        modifier = modifier,
        label = { Text(text = debuffLabel(debuff)) },
        colors = AssistChipDefaults.assistChipColors(
            disabledLabelColor = MaterialTheme.colorScheme.onErrorContainer,
            disabledContainerColor = MaterialTheme.colorScheme.errorContainer
        )
    )
}

/** Одна строка-индикатор характеристики: подпись, числовое значение и прогресс-бар. */
@Composable
private fun StatBar(
    label: String,
    valueText: String,
    progress: Float,
    color: Color,
    iconRes: Int,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "stat-bar-$label"
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelIcon(resId = iconRes, contentDescription = null, size = 18.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = label, style = MaterialTheme.typography.labelLarge)
            }
            Text(text = valueText, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .border(1.5.dp, Dungeon.GoldTrim, RoundedCornerShape(7.dp)),
            color = color,
            trackColor = Color(0xFF14100A)
        )
    }
}

// endregion

// region Timer display

/**
 * Отображение Таймера (R14.2).
 *
 * Показывает метку текущей фазы, оставшееся время в формате "ММ:СС"
 * (через [PomodoroEngine.formatTime] — R7.2) и счётчик завершённых/общего числа
 * Помидорок Серии (R8.2). Поверх отображается анимация успеха при завершении
 * Помидорки (R14.5), управляемая флагом [showSuccess].
 *
 * Requirements: 7.2, 8.2, 14.2, 14.5
 */
@Composable
fun TimerDisplay(
    timer: TimerState,
    showSuccess: Boolean,
    modifier: Modifier = Modifier
) {
    DungeonPanel(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Метка текущей фазы (R7.2, R14.2).
                Text(
                    text = phaseLabel(timer.phase),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (timer.phase == Phase.WORK) {
                        Dungeon.Gold
                    } else {
                        Dungeon.Teal
                    }
                )

                AnimatedHourglass(
                    running = timer.runState == RunState.RUNNING,
                    size = 48.dp
                )

                // Оставшееся время "ММ:СС" пиксельным шрифтом (R7.2, R14.2).
                Text(
                    text = PomodoroEngine.formatTime(timer.secondsLeft),
                    fontFamily = Dungeon.PixelFont,
                    fontSize = 40.sp,
                    color = Dungeon.GoldBright,
                    textAlign = TextAlign.Center
                )

                // Счётчик Помидорок Серии (R8.2, R14.2).
                Text(
                    text = "Помидорки: ${timer.completedPomodoros} / ${timer.totalPomodoros}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dungeon.Parchment
                )

                Text(
                    text = runStateLabel(timer.runState),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Анимация успеха при завершении Помидорки (R14.5).
            SuccessAnimation(
                visible = showSuccess,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/**
 * Анимация успеха, отображаемая при успешном завершении Помидорки (R14.5).
 *
 * Появляется с эффектом увеличения и затухания, управляется флагом [visible],
 * который вызывающий код ([StudyDungeonScreen]) поднимает при росте числа
 * завершённых Помидорок и автоматически сбрасывает.
 *
 * Requirements: 14.5
 */
@Composable
fun SuccessAnimation(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
            fadeIn(animationSpec = tween(300, easing = LinearEasing)),
        exit = scaleOut(animationSpec = tween(400, easing = FastOutSlowInEasing)) +
            fadeOut(animationSpec = tween(400, easing = LinearEasing))
    ) {
        DungeonPanel {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PixelIcon(
                    resId = R.drawable.ic_gold,
                    contentDescription = null,
                    size = 56.dp
                )
                Text(
                    text = "Помидорка завершена!",
                    style = MaterialTheme.typography.titleMedium,
                    color = Dungeon.GoldBright
                )
            }
        }
    }
}

/**
 * Анимированные песочные часы таймера: пока Таймер идёт ([running]),
 * спрайт периодически «переворачивается» (поворот на 360° с паузами),
 * имитируя пересыпание песка. На паузе/остановке часы стоят ровно.
 */
@Composable
fun AnimatedHourglass(
    running: Boolean,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp
) {
    val transition = rememberInfiniteTransition(label = "hourglass")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3200
                0f at 0
                0f at 900
                180f at 1900
                180f at 2300
                360f at 3200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "hourglassAngle"
    )
    PixelIcon(
        resId = R.drawable.ic_hourglass,
        contentDescription = null,
        size = size,
        modifier = modifier.graphicsLayer { rotationZ = if (running) angle else 0f }
    )
}

// endregion

// region Helpers (pure presentation mapping)

/**
 * Спрайт-портрет Героя в зависимости от Уровня: персонаж «прокачивается»
 * визуально — новичок → бывалый искатель → легендарный герой.
 */
fun heroPortraitForLevel(level: Int): Int = when {
    level >= 6 -> R.drawable.hero_portrait_t3
    level >= 3 -> R.drawable.hero_portrait_t2
    else -> R.drawable.hero_portrait
}

/** Звание Героя по Уровню (отображается под уровнем в панели характеристик). */
fun heroRankForLevel(level: Int): String = when {
    level >= 6 -> "Легендарный герой"
    level >= 3 -> "Бывалый искатель"
    else -> "Новичок"
}

/** Безопасное отношение для прогресс-бара в диапазоне [0f, 1f]. */
private fun ratio(value: Int, total: Int): Float {
    if (total <= 0) return 0f
    return (value.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

/** Русскоязычная метка фазы Таймера. */
private fun phaseLabel(phase: Phase): String = when (phase) {
    Phase.WORK -> "Фаза работы"
    Phase.BREAK -> "Фаза отдыха"
}

/** Русскоязычная метка состояния выполнения Таймера. */
private fun runStateLabel(runState: RunState): String = when (runState) {
    RunState.STOPPED -> "Остановлен"
    RunState.RUNNING -> "Идёт"
    RunState.PAUSED -> "Пауза"
}

/** Русскоязычная метка Дебаффа для индикатора (R14.6). */
private fun debuffLabel(debuff: Debuff): String = when (debuff) {
    Debuff.NONE -> ""
    Debuff.TIRED -> "Усталость"
    Debuff.DISTRACTED -> "Рассеянность"
    Debuff.WEAK -> "Слабость"
}

/** Цвет индикатора Здоровья. */
private val HealthColor = Dungeon.Health

/** Цвет подписи Золота. */
private val GoldColor = Dungeon.GoldBright

// endregion

// region Previews

@Preview(showBackground = true)
@Composable
private fun HeroStatsPanelPreview() {
    StudyDungeonTheme {
        HeroStatsPanel(
            hero = Hero(
                level = 3,
                currentXp = 40,
                xpToNext = 150,
                currentHp = 25,
                maxHp = 140,
                gold = 120,
                debuff = Debuff.DISTRACTED
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TimerDisplayPreview() {
    StudyDungeonTheme {
        TimerDisplay(
            timer = TimerState(
                phase = Phase.WORK,
                runState = RunState.RUNNING,
                secondsLeft = 1500,
                completedPomodoros = 1,
                totalPomodoros = 4
            ),
            showSuccess = false,
            modifier = Modifier.padding(16.dp)
        )
    }
}

// endregion
