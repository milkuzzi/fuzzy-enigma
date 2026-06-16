package com.studydungeon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.studydungeon.domain.Debuff
import com.studydungeon.domain.Hero
import com.studydungeon.domain.Phase
import com.studydungeon.domain.RunState
import com.studydungeon.domain.TimerState
import kotlinx.coroutines.delay

/**
 * Корневой экран приложения Study Dungeon.
 *
 * Здесь собирается весь мобильный Интерфейс: панель характеристик Героя
 * ([HeroStatsPanel]) и отображение Таймера ([TimerDisplay]) реализованы в этой
 * задаче (12.1). Элементы управления, сворачиваемые секции настроек, магазина и
 * инвентаря добавляются отдельной задачей 12.2 в отмеченное ниже место, чтобы не
 * затрагивать код характеристик/таймера.
 *
 * Экран принимает уже считанное [UiState] и колбэки, оставаясь stateless и
 * пригодным для preview/тестов; вариант с биндингом к
 * [StudyDungeonViewModel] оборачивает эту функцию.
 *
 * Requirements: 1.3, 7.2, 8.2, 14.1, 14.2, 14.5, 14.6
 */
@Composable
fun StudyDungeonScreen(
    uiState: UiState,
    modifier: Modifier = Modifier,
    onStart: () -> Unit = {},
    onPause: () -> Unit = {},
    onGiveUp: () -> Unit = {},
    onStartNewSeries: () -> Unit = {},
    onApplySettings: (workSec: Int, breakSec: Int, total: Int) -> Unit = { _, _, _ -> },
    onPurchase: (com.studydungeon.domain.ShopItem) -> Unit = {},
    onToggleSection: (Section) -> Unit = {}
) {
    // Локальное состояние анимации успеха: поднимается при росте числа
    // завершённых Помидорок и автоматически сбрасывается (R14.5).
    var showSuccess by remember { mutableStateOf(false) }
    val completed = uiState.timer.completedPomodoros

    LaunchedEffect(completed) {
        if (completed > 0) {
            showSuccess = true
            delay(SUCCESS_ANIMATION_MS)
            showSuccess = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Заголовочный баннер приложения в стиле подземелья.
        TitleBanner()

        // Панель характеристик Героя (R1.3, R14.1, R14.6).
        HeroStatsPanel(hero = uiState.hero)

        // Отображение Таймера: фаза, "ММ:СС", счётчик Помидорок, анимация успеха
        // (R7.2, R8.2, R14.2, R14.5).
        TimerDisplay(
            timer = uiState.timer,
            showSuccess = showSuccess
        )

        // Элементы управления сессией: старт/пауза/сдача и запуск новой Серии
        // (R14.3, R8.4).
        SessionControls(
            timer = uiState.timer,
            onStart = onStart,
            onPause = onPause,
            onGiveUp = onGiveUp,
            onStartNewSeries = onStartNewSeries
        )

        // Сворачиваемые секции настроек, магазина и инвентаря (R14.4). В
        // Режиме_Фокуса секции скрыты целиком (R13.6): видимость берётся из
        // uiState, переключение — через onToggleSection.
        if (!uiState.focusMode) {
            CollapsibleSection(
                title = "Настройки",
                expanded = uiState.isSectionVisible(Section.SETTINGS),
                onToggle = { onToggleSection(Section.SETTINGS) }
            ) {
                SettingsSectionContent(
                    timer = uiState.timer,
                    onApply = onApplySettings
                )
            }

            CollapsibleSection(
                title = "Магазин",
                expanded = uiState.isSectionVisible(Section.SHOP),
                onToggle = { onToggleSection(Section.SHOP) }
            ) {
                ShopSectionContent(
                    hero = uiState.hero,
                    onPurchase = onPurchase
                )
            }

            CollapsibleSection(
                title = "Инвентарь",
                expanded = uiState.isSectionVisible(Section.INVENTORY),
                onToggle = { onToggleSection(Section.INVENTORY) }
            ) {
                InventorySectionContent(hero = uiState.hero)
            }
        }
    }
}

/** Длительность показа анимации успеха завершения Помидорки (мс). */
private const val SUCCESS_ANIMATION_MS = 2000L

/**
 * Обёртка [StudyDungeonScreen] с биндингом к [StudyDungeonViewModel].
 *
 * Собирает реактивный [UiState] и подписывается на однократные сообщения
 * ([StudyDungeonViewModel.messages]), показывая их через переданный
 * [SnackbarHostState] (R6.4, R13.7). Все действия пользователя проксируются в
 * соответствующие методы ViewModel, сохраняя однонаправленный поток данных.
 */
@Composable
fun StudyDungeonScreen(
    viewModel: StudyDungeonViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    StudyDungeonScreen(
        uiState = uiState,
        modifier = modifier,
        onStart = viewModel::startSession,
        onPause = viewModel::pauseSession,
        onGiveUp = viewModel::giveUp,
        onStartNewSeries = viewModel::startNewSeries,
        onApplySettings = viewModel::applySettings,
        onPurchase = viewModel::purchase,
        onToggleSection = viewModel::toggleSection
    )
}

@Preview(showBackground = true)
@Composable
private fun StudyDungeonScreenPreview() {
    StudyDungeonTheme {
        StudyDungeonScreen(
            uiState = UiState(
                hero = Hero(
                    level = 2,
                    currentXp = 30,
                    xpToNext = 150,
                    currentHp = 80,
                    maxHp = 120,
                    gold = 70,
                    debuff = Debuff.TIRED
                ),
                timer = TimerState(
                    phase = Phase.WORK,
                    runState = RunState.RUNNING,
                    secondsLeft = 845,
                    completedPomodoros = 1,
                    totalPomodoros = 4
                )
            )
        )
    }
}
