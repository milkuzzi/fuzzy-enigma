package com.studydungeon.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.studydungeon.R
import com.studydungeon.data.FileHeroRepository
import com.studydungeon.data.HeroRepository
import com.studydungeon.data.SettingsStore
import com.studydungeon.data.ThemeChoice
import com.studydungeon.domain.ApplySettingsResult
import com.studydungeon.domain.Hero
import com.studydungeon.domain.HeroEngine
import com.studydungeon.domain.Phase
import com.studydungeon.domain.PomodoroEngine
import com.studydungeon.domain.RunState
import com.studydungeon.domain.SessionEngine
import com.studydungeon.domain.ShopCatalog
import com.studydungeon.domain.ShopItem
import com.studydungeon.domain.TimerState
import com.studydungeon.lock.LockMode
import com.studydungeon.lock.LockPermissions
import com.studydungeon.lock.LockPreferences
import com.studydungeon.service.FocusModeController
import com.studydungeon.service.LockEnforcementService
import com.studydungeon.service.NotificationController
import com.studydungeon.service.TimerForegroundService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Презентационный слой, связывающий доменную логику, persistence и платформенные
 * сервисы в единый однонаправленный поток данных (UDF) для Compose-UI.
 *
 * ViewModel — единственный «дирижёр» приложения: он
 * - экспонирует неизменяемый [UiState] (Герой + состояние Таймера + флаги
 *   видимости секций + флаг Режима_Фокуса) через [uiState];
 * - применяет настройки Таймера с отклонением во время выполнения
 *   ([applySettings] / R6.4);
 * - управляет жизненным циклом сессии: старт ([startSession] / R7.1), пауза
 *   ([pauseSession] / R7.5), сдача ([giveUp] / R9.1, R9.2), запуск новой Серии
 *   ([startNewSeries] / R8.4);
 * - наблюдает [TimerForegroundService.timerState] и начисляет награду за каждую
 *   успешно завершённую Помидорку атомарно ([SessionEngine.applyWorkSuccessReward]
 *   / R7.6, R8.1);
 * - обрабатывает покупки ([purchase] / R5);
 * - сохраняет состояние Героя после каждой мутации ([updateHero] / R10.1);
 * - активирует/деактивирует Режим_Фокуса и тем самым скрывает/восстанавливает
 *   секции настроек/магазина/инвентаря (R13.6);
 * - трактует принудительный выход из Закрепления_Экрана как провал сессии
 *   ([handleForcedExit] / R9.4, R13.10), а принудительное закрытие Приложения —
 *   как штраф [HeroEngine.forceQuitPenalty] ([onForceQuit] / R9.3).
 *
 * Доменная логика остаётся чистой: ViewModel лишь оркеструет вызовы движков и
 * платформенных контроллеров, не дублируя игровых правил.
 *
 * Платформенно-зависимые зависимости (контекст Приложения, репозиторий,
 * [NotificationController]) создаются из [Application]. [FocusModeController]
 * привязан к жизненному циклу `Activity`, поэтому передаётся отдельно через
 * [bindFocusController].
 *
 * Requirements: 6.4, 7.1, 7.5, 7.6, 8.1, 8.4, 9.1, 9.2, 9.3, 9.4, 10.1, 13.6, 13.10
 */
class StudyDungeonViewModel(
    application: Application,
    private val repository: HeroRepository = FileHeroRepository.from(application),
    private val notificationController: NotificationController = NotificationController(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())

    /** Реактивное состояние Интерфейса для Compose. */
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /**
     * Однократные пользовательские сообщения (например, отклонение настроек во
     * время работы Таймера — R6.4, или инструкция по включению
     * Закрепления_Экрана — R13.7). UI показывает их как snackbar/toast.
     */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /**
     * [FocusModeController] привязан к `Activity`, поэтому инжектируется отдельно
     * через [bindFocusController]. До привязки операции Режима_Фокуса
     * пропускаются без сбоя (UI-флаг [UiState.focusMode] всё равно отражает
     * намерение скрыть секции — R13.6).
     */
    private var focusController: FocusModeController? = null

    /**
     * Число Помидорок, за которые уже начислена награда. Используется для
     * идемпотентного начисления награды за каждую вновь завершённую Помидорку
     * при наблюдении за состоянием службы (R7.6, R8.1) без двойного начисления.
     */
    private var rewardedPomodoros: Int = 0

    /** Настройки блокировки телефона (режим + список приложений). */
    private val lockPreferences = LockPreferences(application)

    /** Типобезопасные настройки приложения и статистика (DataStore). */
    private val settingsStore = SettingsStore(application)

    /** Выбранная тема оформления (наблюдается Activity для применения темы). */
    val themeChoice: StateFlow<ThemeChoice> =
        settingsStore.themeChoice.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeChoice.DARK)

    /** Признак завершённого онбординга (Activity показывает онбординг, если false). */
    val onboardingCompleted: StateFlow<Boolean?> =
        settingsStore.onboardingCompleted.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Загрузка сохранённого Героя (R10.2): при отсутствии/повреждении файла
        // репозиторий вернёт Героя по умолчанию (R10.3).
        viewModelScope.launch {
            val hero = repository.load()
            _uiState.update { it.copy(hero = hero) }
        }

        // Восстановление сохранённых длительностей Таймера (R10).
        viewModelScope.launch {
            val savedWork = settingsStore.workDurationSec.first()
            val savedBreak = settingsStore.breakDurationSec.first()
            val savedTotal = settingsStore.totalPomodoros.first()
            _uiState.update {
                it.copy(
                    timer = it.timer.copy(
                        workDurationSec = savedWork,
                        breakDurationSec = savedBreak,
                        secondsLeft = savedWork,
                        totalPomodoros = savedTotal
                    )
                )
            }
        }

        // Реактивное наблюдение за статистикой (счётчики, серии, история).
        viewModelScope.launch {
            settingsStore.stats.collect { snapshot ->
                _uiState.update {
                    it.copy(
                        todayPomodoros = snapshot.today,
                        totalPomodoros = snapshot.total,
                        stats = snapshot
                    )
                }
            }
        }

        // Наблюдение за состоянием Таймера из фоновой службы (источник истины
        // отсчёта по wall-clock). При каждом обновлении начисляем награду за
        // завершённые Помидорки и синхронизируем UI.
        viewModelScope.launch {
            TimerForegroundService.timerState.collect { ts ->
                if (ts != null) handleTimerUpdate(ts) else handleTimerStopped()
            }
        }
    }

    /**
     * Привязывает [FocusModeController] (создаётся `Activity`) и регистрирует
     * наблюдателей: принудительный выход из Закрепления_Экрана трактуется как
     * провал сессии (R9.4, R13.10), а недоступность закрепления показывает
     * инструкцию пользователю (R13.7).
     */
    fun bindFocusController(controller: FocusModeController) {
        focusController = controller
        controller.onLockTaskExitDetected { handleForcedExit() }
        controller.onLockTaskUnavailable { instruction -> emitMessage(instruction) }
    }

    /**
     * Регистрирует колбэк запроса разрешения `POST_NOTIFICATIONS` (R12.4).
     * Делегируется [NotificationController].
     */
    fun setNotificationPermissionRequester(requester: (() -> Unit)?) {
        notificationController.setPermissionRequester(requester)
    }

    /**
     * Применяет новые настройки длительностей и числа Помидорок.
     *
     * Делегирует [PomodoroEngine.applySettings]: во время выполнения Таймера
     * изменение отклоняется, длительности не меняются, пользователю отправляется
     * сообщение (R6.4). Иначе настройки применяются и счётчик награждённых
     * Помидорок сбрасывается.
     *
     * @param workSec длительность Фазы_Работы в секундах.
     * @param breakSec длительность Фазы_Отдыха в секундах.
     * @param total общее число Помидорок в Серии.
     *
     * Requirements: 6.4, 6.5
     */
    fun applySettings(workSec: Int, breakSec: Int, total: Int) {
        when (val result = PomodoroEngine.applySettings(_uiState.value.timer, workSec, breakSec, total)) {
            is ApplySettingsResult.Applied -> {
                _uiState.update { it.copy(timer = result.state) }
                rewardedPomodoros = 0
                // Сохраняем выбранные длительности между запусками (R10.1).
                viewModelScope.launch { settingsStore.saveTimerSettings(workSec, breakSec, total) }
            }
            is ApplySettingsResult.Rejected ->
                emitMessage("Нельзя менять настройки во время работы таймера")
        }
    }

    /**
     * Запускает сессию: переводит Таймер в выполнение через фоновую службу
     * (R7.1), активирует Режим_Фокуса с Длительностью_Блокировки, равной
     * длительности Фазы_Работы (R6.6, R13.1), и скрывает секции (R13.6).
     *
     * Requirements: 7.1, 6.6, 13.1, 13.6
     */
    fun startSession() {
        val timer = _uiState.value.timer
        rewardedPomodoros = timer.completedPomodoros

        TimerForegroundService.start(
            getApplication(),
            timer.workDurationSec,
            timer.breakDurationSec,
            timer.totalPomodoros
        )

        // Сильная блокировка телефона (оверлей/блокировка приложений), если
        // выбрана и выданы разрешения. Если она активна — Screen Pinning не
        // используем (pinScreen = false).
        val strongLockActive = startPhoneLockIfEnabled(timer)

        // Длительность_Блокировки = длительность Фазы_Работы (R6.6, R13.1).
        focusController?.activate(
            SessionEngine.lockDurationSec(timer),
            pinScreen = !strongLockActive
        )

        _uiState.update {
            it.activateFocusMode().copy(timer = it.timer.copy(runState = RunState.RUNNING))
        }
    }

    /**
     * Приостанавливает сессию (R7.5): останавливает тикер службы и деактивирует
     * Режим_Фокуса, снимая Закрепление_Экрана и восстанавливая видимость секций
     * (R13.9, R13.6).
     *
     * Requirements: 7.5
     */
    fun pauseSession() {
        TimerForegroundService.pause(getApplication())
        stopPhoneLock()
        focusController?.deactivate()
        _uiState.update {
            it.deactivateFocusMode().copy(timer = it.timer.copy(runState = RunState.PAUSED))
        }
    }

    /**
     * Обрабатывает сдачу во время сессии (R9.1): останавливает Таймер, сбрасывает
     * его в Фазу_Работы и наносит Герою штраф провала сессии — 15 урона (R9.2).
     * Деактивирует Режим_Фокуса (R13.9, R13.6).
     *
     * Requirements: 9.1, 9.2, 13.6
     */
    fun giveUp() {
        TimerForegroundService.stop(getApplication())
        stopPhoneLock()
        focusController?.deactivate()
        // R9.2: провал сессии наносит 15 урона (с сохранением — R10.1).
        updateHero(SessionEngine.applySessionFailPenalty(_uiState.value.hero))
        _uiState.update {
            it.deactivateFocusMode().copy(timer = PomodoroEngine.fail(it.timer))
        }
    }

    /**
     * Запускает новую Серию после завершения предыдущей: обнуляет счётчик
     * завершённых Помидорок (R8.4) и счётчик награждённых Помидорок.
     *
     * Requirements: 8.4
     */
    fun startNewSeries() {
        // Запрет повторного сброса уже «свежей» Серии (нулевой прогресс, стоп).
        if (PomodoroEngine.isFreshSeries(_uiState.value.timer)) {
            emitMessage(getApplication<Application>().getString(R.string.series_already_new))
            return
        }
        _uiState.update { it.copy(timer = PomodoroEngine.startNewSeries(it.timer)) }
        rewardedPomodoros = 0
    }

    /**
     * Использует предмет Инвентаря по индексу [index]: применяет его эффект и
     * расходует одну единицу предмета ([ShopCatalog.useItem]). Сохраняет
     * состояние Героя (R10.1).
     */
    fun useItem(index: Int) {
        val hero = _uiState.value.hero
        val updated = ShopCatalog.useItem(hero, index)
        if (updated != hero) updateHero(updated)
    }

    /** Сохраняет выбранную пользователем тему оформления. */
    fun setThemeChoice(choice: ThemeChoice) {
        viewModelScope.launch { settingsStore.setThemeChoice(choice) }
    }

    /** Отмечает онбординг как завершённый (после первого запуска). */
    fun completeOnboarding() {
        viewModelScope.launch { settingsStore.setOnboardingCompleted(true) }
    }

    /**
     * Покупка предмета Магазина. При успехе применяет эффект предмета и
     * сохраняет состояние Героя (R10.1); при недостатке Золота уведомляет
     * пользователя.
     *
     * Requirements: 5.1, 5.2, 5.3, 5.4, 10.1
     */
    fun purchase(item: ShopItem) {
        val result = ShopCatalog.purchase(_uiState.value.hero, item)
        if (result.success) {
            updateHero(result.hero)
        } else {
            emitMessage("Недостаточно золота для покупки: ${item.displayName}")
        }
    }

    /**
     * Переключает (сворачивает/разворачивает) намеренную видимость секции
     * (R14.4). В Режиме_Фокуса секции всё равно скрыты на экране (R13.6).
     *
     * Requirements: 14.4
     */
    fun toggleSection(section: Section) {
        _uiState.update { it.toggleSection(section) }
    }

    /**
     * Уведомляет ViewModel о возврате `Activity` на передний план. Проверяет
     * принудительный выход из Закрепления_Экрана: при обнаружении сработает
     * зарегистрированный в [bindFocusController] колбэк [handleForcedExit]
     * (R9.4, R13.10).
     */
    fun onActivityResumed() {
        focusController?.checkForLockTaskExit()
    }

    /**
     * Уведомляет ViewModel об уходе Приложения в фон. Во время активного
     * Режима_Фокуса при выполняющемся Таймере отправляет напоминание о возврате
     * к учёбе с оставшимся временем и текущим Здоровьем Героя (R13.11).
     *
     * Requirements: 13.11
     */
    fun onAppBackgrounded() {
        val state = _uiState.value
        if (state.focusMode && state.timer.runState == RunState.RUNNING) {
            notificationController.notifyReturnReminder(
                state.timer.secondsLeft,
                state.hero.currentHp
            )
        }
    }

    /**
     * Применяет штраф за принудительное закрытие Приложения во время выполнения
     * Таймера: Здоровье `max(0, hp-50)`, Опыт `max(0, xp-30)`, дебафф WEAK
     * (R9.3). Сохранение выполняется синхронно, так как это терминальное
     * действие — [viewModelScope] может быть отменён до завершения асинхронной
     * записи.
     *
     * Вызывается из `Activity.onTaskRemoved`/`onDestroy` при активной сессии.
     *
     * Requirements: 9.3
     */
    fun onForceQuit() {
        if (_uiState.value.timer.runState != RunState.RUNNING) return
        val mutated = HeroEngine.forceQuitPenalty(_uiState.value.hero)
        _uiState.update { it.copy(hero = mutated) }
        // Терминальное действие: гарантируем запись до завершения процесса.
        runBlocking { repository.save(mutated) }
        stopPhoneLock()
        TimerForegroundService.stop(getApplication())
    }

    /**
     * Запускает сильную блокировку телефона через [LockEnforcementService],
     * если пользователь выбрал режим (не [LockMode.NONE]), это Фаза_Работы
     * и выданы необходимые разрешения. Возвращает true, если блокировка
     * запущена (тогда Screen Pinning не нужен).
     */
    private fun startPhoneLockIfEnabled(timer: TimerState): Boolean {
        val mode = lockPreferences.mode
        if (mode == LockMode.NONE) return false
        if (timer.phase != Phase.WORK) return false
        val context = getApplication<Application>()
        if (!LockPermissions.hasPermissionsFor(context, mode)) {
            emitMessage("Для блокировки телефона выдайте разрешения в разделе «Блокировка» настроек.")
            return false
        }
        val endMs = System.currentTimeMillis() + timer.secondsLeft * 1000L
        LockEnforcementService.start(context, mode, endMs)
        return true
    }

    /** Останавливает сильную блокировку телефона, если она была активна. */
    private fun stopPhoneLock() {
        LockEnforcementService.stop(getApplication())
    }

    /**
     * Обрабатывает обновление состояния Таймера из фоновой службы.
     *
     * Начисляет награду за каждую вновь завершённую Помидорку атомарно
     * ([SessionEngine.applyWorkSuccessReward] / R7.6, R8.1) — идемпотентно
     * относительно конфляции [StateFlow] (начисляем ровно за разницу
     * `completedPomodoros - rewardedPomodoros`). При завершении Фазы_Работы и
     * переходе в Фазу_Отдыха автоматически деактивирует Режим_Фокуса (R13.8).
     */
    private fun handleTimerUpdate(ts: TimerState) {
        if (ts.completedPomodoros > rewardedPomodoros) {
            var hero = _uiState.value.hero
            while (rewardedPomodoros < ts.completedPomodoros) {
                hero = SessionEngine.applyWorkSuccessReward(hero) // R7.6, R8.1 (атомарно)
                rewardedPomodoros++
                // Учёт в истории статистики; UI обновится через наблюдение stats.
                viewModelScope.launch { settingsStore.recordCompletedPomodoro() }
            }
            updateHero(hero) // R10.1
        }

        val wasFocus = _uiState.value.focusMode
        _uiState.update { it.copy(timer = ts) }

        // R13.8: при достижении нулём Фазы_Работы Режим_Фокуса деактивируется.
        if (wasFocus && ts.phase == Phase.BREAK) {
            stopPhoneLock()
            focusController?.deactivate()
            _uiState.update { it.deactivateFocusMode() }
        }
    }

    /**
     * Обрабатывает остановку фоновой службы (завершение Серии или штатный стоп):
     * деактивирует Режим_Фокуса и приводит локальное состояние Таймера к
     * остановленному.
     */
    private fun handleTimerStopped() {
        if (_uiState.value.focusMode) {
            stopPhoneLock()
            focusController?.deactivate()
        }
        _uiState.update {
            val stopped = it.timer.copy(runState = RunState.STOPPED, phaseEndEpochMs = null)
            it.deactivateFocusMode().copy(timer = stopped)
        }
    }

    /**
     * Обрабатывает принудительный выход из Закрепления_Экрана во время активного
     * Режима_Фокуса как провал сессии: останавливает Таймер, наносит штраф 15
     * урона (R9.2), деактивирует Режим_Фокуса и сбрасывает Таймер в Фазу_Работы
     * (R9.4, R13.10). [FocusModeController] к этому моменту уже снял удержание
     * экрана и собственное состояние блокировки.
     *
     * Requirements: 9.4, 13.10
     */
    private fun handleForcedExit() {
        TimerForegroundService.stop(getApplication())
        stopPhoneLock()
        updateHero(SessionEngine.applySessionFailPenalty(_uiState.value.hero)) // R9.2
        _uiState.update {
            it.deactivateFocusMode().copy(timer = PomodoroEngine.fail(it.timer))
        }
    }

    /**
     * Применяет новое состояние Героя к UI и сохраняет его в хранилище после
     * мутации (R10.1).
     */
    private fun updateHero(hero: Hero) {
        _uiState.update { it.copy(hero = hero) }
        viewModelScope.launch { repository.save(hero) } // R10.1
    }

    private fun emitMessage(message: String) {
        _messages.tryEmit(message)
    }

    /**
     * Фабрика для создания [StudyDungeonViewModel] с зависимостями из
     * [Application] (контекст для службы, репозиторий, контроллер уведомлений).
     */
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StudyDungeonViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            @Suppress("UNCHECKED_CAST")
            return StudyDungeonViewModel(application) as T
        }
    }
}
