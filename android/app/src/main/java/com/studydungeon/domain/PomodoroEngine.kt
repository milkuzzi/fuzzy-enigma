package com.studydungeon.domain

/**
 * Результат попытки применить новые настройки к Таймеру.
 *
 * Делает отклонение наблюдаемым (Requirement 6.4 / Property 12): при попытке
 * изменить настройки во время выполнения Таймера возвращается [Rejected] с
 * неизменным состоянием, что позволяет вызывающему коду (ViewModel)
 * уведомить пользователя о невозможности менять настройки во время работы.
 *
 * @property state итоговое состояние Таймера (неизменное при отклонении).
 */
sealed interface ApplySettingsResult {
    val state: TimerState

    /** Настройки применены: длительности и оставшееся время обновлены. */
    data class Applied(override val state: TimerState) : ApplySettingsResult

    /** Настройки отклонены, так как Таймер находится в состоянии выполнения. */
    data class Rejected(override val state: TimerState) : ApplySettingsResult
}

/**
 * Результат одного тика Таймера ([PomodoroEngine.tick]).
 *
 * Делает событие смены фазы/завершения Серии наблюдаемым для вызывающего кода
 * (ViewModel/Service): по типу результата решается, начислять ли награду,
 * показывать ли уведомление о переходе к отдыху/работе или о завершении Серии.
 *
 * @property state итоговое состояние Таймера после тика.
 */
sealed interface TickResult {
    val state: TimerState

    /** Фаза продолжается: обновлено только оставшееся время. */
    data class Continue(override val state: TimerState) : TickResult

    /** Фаза_Работы завершена: Помидорка засчитана, выполнен переход к Фазе_Отдыха. */
    data class WorkCompleted(override val state: TimerState) : TickResult

    /** Фаза_Отдыха завершена: выполнен переход к Фазе_Работы. */
    data class BreakCompleted(override val state: TimerState) : TickResult

    /** Серия завершена: засчитана последняя Помидорка (completedPomodoros >= total). */
    data class SeriesCompleted(override val state: TimerState) : TickResult
}

/**
 * Машина состояний Pomodoro-таймера в виде чистых функций.
 *
 * Перенос логики `PomodoroTimer` из оригинального `pomodoro.py` без побочных
 * эффектов и потоков: каждая операция принимает текущее [TimerState] и
 * возвращает новое. Расчёт времени основан на «настенных» часах через
 * [TimerState.phaseEndEpochMs], а не на тиках потока, что обеспечивает
 * достоверность отсчёта в фоне.
 *
 * Логика тика, переключения фаз, серии и провала добавляется отдельно
 * (задача 5.7) и не нарушает поведение реализованных здесь операций.
 */
object PomodoroEngine {

    /**
     * Применяет новые настройки длительностей и числа Помидорок.
     *
     * Если Таймер выполняется ([RunState.RUNNING]), изменение отклоняется и
     * длительности Фазы_Работы и Фазы_Отдыха остаются прежними
     * (Requirement 6.4). Иначе устанавливаются заданные длительности и число
     * Помидорок, оставшееся время приравнивается к длительности Фазы_Работы,
     * а фаза сбрасывается в [Phase.WORK] (Requirement 6.5).
     *
     * @param state текущее состояние Таймера.
     * @param workSec длительность Фазы_Работы в секундах.
     * @param breakSec длительность Фазы_Отдыха в секундах.
     * @param total общее число Помидорок в Серии.
     * @return [ApplySettingsResult.Applied] при успехе либо
     *   [ApplySettingsResult.Rejected] с неизменным состоянием при выполнении.
     *
     * Requirements: 6.4, 6.5
     */
    fun applySettings(
        state: TimerState,
        workSec: Int,
        breakSec: Int,
        total: Int
    ): ApplySettingsResult {
        if (state.runState == RunState.RUNNING) {
            return ApplySettingsResult.Rejected(state)
        }
        val configured = state.copy(
            phase = Phase.WORK,
            runState = RunState.STOPPED,
            workDurationSec = workSec,
            breakDurationSec = breakSec,
            secondsLeft = workSec,
            phaseEndEpochMs = null,
            completedPomodoros = 0,
            totalPomodoros = total
        )
        return ApplySettingsResult.Applied(configured)
    }

    /**
     * Запускает Таймер из текущего состояния.
     *
     * Переводит Таймер в [RunState.RUNNING] и фиксирует момент окончания фазы
     * как `nowMs + secondsLeft * 1000`, делая его источником истины для
     * дальнейшего отсчёта (Requirement 7.1).
     *
     * @param state текущее состояние Таймера.
     * @param nowMs текущее «настенное» время в epoch ms.
     * @return состояние со статусом выполнения и установленным [TimerState.phaseEndEpochMs].
     *
     * Requirements: 7.1
     */
    fun start(state: TimerState, nowMs: Long): TimerState =
        state.copy(
            runState = RunState.RUNNING,
            phaseEndEpochMs = nowMs + state.secondsLeft * 1000L
        )

    /**
     * Приостанавливает выполняющийся Таймер.
     *
     * Переводит Таймер в [RunState.PAUSED] и сохраняет оставшееся время,
     * вычисленное по «настенным» часам относительно [TimerState.phaseEndEpochMs]
     * (Requirement 7.5). Момент окончания фазы сбрасывается, так как при паузе
     * он перестаёт быть источником истины.
     *
     * @param state текущее состояние Таймера.
     * @param nowMs текущее «настенное» время в epoch ms.
     * @return состояние на паузе с сохранённым оставшимся временем.
     *
     * Requirements: 7.5
     */
    fun pause(state: TimerState, nowMs: Long): TimerState {
        val end = state.phaseEndEpochMs
        val remaining = if (end != null) {
            maxOf(0L, (end - nowMs) / 1000L).toInt()
        } else {
            state.secondsLeft
        }
        return state.copy(
            runState = RunState.PAUSED,
            secondsLeft = remaining,
            phaseEndEpochMs = null
        )
    }

    /**
     * Форматирует оставшееся время в строку "ММ:СС".
     *
     * Паритет с `_format_time` из `pomodoro.py`. Минуты могут превышать 99 при
     * больших значениях; форматирование дополняет минуты и секунды нулями до
     * двух разрядов.
     *
     * @param secondsLeft оставшееся время в секундах (неотрицательное).
     * @return строка вида "ММ:СС".
     *
     * Requirements: 7.2
     */
    fun formatTime(secondsLeft: Int): String {
        val minutes = secondsLeft / 60
        val seconds = secondsLeft % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    /**
     * Продвигает Таймер по «настенным» часам и обрабатывает достижение нуля.
     *
     * Оставшееся время вычисляется как
     * `max(0, (phaseEndEpochMs - nowMs) / 1000)`, где [TimerState.phaseEndEpochMs]
     * является источником истины отсчёта (Requirements 11.1, 11.2, 13.4 /
     * Property 28). Это обеспечивает достоверность при выключенном экране или
     * свёрнутом приложении: время не зависит от частоты тиков.
     *
     * Поведение при достижении нуля повторяет `_switch_phase` оригинала, но без
     * рекурсивного потока:
     * - В [Phase.WORK]: засчитывается Помидорка ([TimerState.completedPomodoros]
     *   + 1) и выполняется переход в [Phase.BREAK] с оставшимся временем,
     *   равным [TimerState.breakDurationSec]; новый [TimerState.phaseEndEpochMs]
     *   фиксируется как `nowMs + breakDurationSec * 1000`, чтобы отсчёт
     *   продолжился. Если число засчитанных Помидорок достигло
     *   [TimerState.totalPomodoros] — возвращается [TickResult.SeriesCompleted],
     *   иначе [TickResult.WorkCompleted] (Requirements 7.3, 8.1, 8.3).
     * - В [Phase.BREAK]: выполняется переход в [Phase.WORK] с оставшимся
     *   временем [TimerState.workDurationSec] и
     *   `phaseEndEpochMs = nowMs + workDurationSec * 1000`; возвращается
     *   [TickResult.BreakCompleted] (Requirement 7.4).
     *
     * Если оставшееся время ещё положительно, возвращается
     * [TickResult.Continue] с обновлённым [TimerState.secondsLeft]
     * (Requirement 7.2).
     *
     * @param state текущее состояние Таймера.
     * @param nowMs текущее «настенное» время в epoch ms.
     * @return [TickResult] в зависимости от того, продолжается ли фаза,
     *   завершилась ли Фаза_Работы/Фаза_Отдыха или завершилась вся Серия.
     *
     * Requirements: 7.2, 7.3, 7.4, 8.1, 8.3, 11.1, 11.2, 13.4
     */
    fun tick(state: TimerState, nowMs: Long): TickResult {
        val end = state.phaseEndEpochMs
        val secondsLeft = if (end != null) {
            maxOf(0L, (end - nowMs) / 1000L).toInt()
        } else {
            state.secondsLeft
        }

        if (secondsLeft > 0) {
            return TickResult.Continue(state.copy(secondsLeft = secondsLeft))
        }

        return when (state.phase) {
            Phase.WORK -> {
                val completed = state.completedPomodoros + 1
                val switched = state.copy(
                    phase = Phase.BREAK,
                    completedPomodoros = completed,
                    secondsLeft = state.breakDurationSec,
                    phaseEndEpochMs = nowMs + state.breakDurationSec * 1000L
                )
                if (completed >= state.totalPomodoros) {
                    TickResult.SeriesCompleted(switched)
                } else {
                    TickResult.WorkCompleted(switched)
                }
            }
            Phase.BREAK -> {
                val switched = state.copy(
                    phase = Phase.WORK,
                    secondsLeft = state.workDurationSec,
                    phaseEndEpochMs = nowMs + state.workDurationSec * 1000L
                )
                TickResult.BreakCompleted(switched)
            }
        }
    }

    /**
     * Обрабатывает провал (сдачу) текущей Сессии.
     *
     * Паритет с `fail`/`reset` оригинала: Таймер останавливается
     * ([RunState.STOPPED]), фаза сбрасывается в [Phase.WORK], оставшееся время
     * приравнивается к длительности Фазы_Работы, а [TimerState.phaseEndEpochMs]
     * сбрасывается, так как отсчёт более не идёт (Requirement 9.1 / Property 23).
     * Прогресс Серии ([TimerState.completedPomodoros]) сохраняется — его сброс
     * относится к запуску новой Серии ([startNewSeries]).
     *
     * @param state текущее состояние Таймера.
     * @return остановленное состояние в [Phase.WORK] с полным временем работы.
     *
     * Requirements: 9.1
     */
    fun fail(state: TimerState): TimerState =
        state.copy(
            runState = RunState.STOPPED,
            phase = Phase.WORK,
            secondsLeft = state.workDurationSec,
            phaseEndEpochMs = null
        )

    /**
     * Запускает новую Серию Помодоро.
     *
     * Сбрасывает счётчик засчитанных Помидорок [TimerState.completedPomodoros]
     * в ноль и приводит Таймер к началу Фазы_Работы: [Phase.WORK],
     * [RunState.STOPPED], оставшееся время равно [TimerState.workDurationSec],
     * [TimerState.phaseEndEpochMs] сброшен. Длительности и общее число Помидорок
     * сохраняются (Requirement 8.4 / Property 22).
     *
     * @param state текущее состояние Таймера.
     * @return состояние для новой Серии с нулевым прогрессом.
     *
     * Requirements: 8.4
     */
    fun startNewSeries(state: TimerState): TimerState =
        state.copy(
            phase = Phase.WORK,
            runState = RunState.STOPPED,
            secondsLeft = state.workDurationSec,
            phaseEndEpochMs = null,
            completedPomodoros = 0
        )

    /**
     * Признак того, что Серия уже находится в «свежем» начальном состоянии и
     * запускать новую Серию не имеет смысла: Таймер остановлен ([RunState.STOPPED]),
     * прогресс нулевой ([TimerState.completedPomodoros] == 0) и текущая фаза —
     * [Phase.WORK]. Используется UI/ViewModel, чтобы запретить повторный сброс
     * уже новой Серии.
     */
    fun isFreshSeries(state: TimerState): Boolean =
        state.runState == RunState.STOPPED &&
            state.completedPomodoros == 0 &&
            state.phase == Phase.WORK
}
