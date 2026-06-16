package com.studydungeon.domain

/**
 * Фаза цикла Помодоро: работа или отдых.
 *
 * Паритет с флагом `is_work_phase` из оригинального `PomodoroTimer`.
 */
enum class Phase { WORK, BREAK }

/**
 * Состояние выполнения таймера.
 *
 * Заменяет булев `is_running` оригинала тремя явными состояниями, что
 * необходимо для корректного различения «остановлен» и «на паузе».
 */
enum class RunState { STOPPED, RUNNING, PAUSED }

/**
 * Неизменяемое состояние таймера и прогресса серии Помодоро.
 *
 * Источником истины при [RunState.RUNNING] является [phaseEndEpochMs]:
 * оставшееся время вычисляется по «настенным» часам, а не по тикам, чтобы
 * отсчёт оставался достоверным при выключенном экране/свёрнутом приложении
 * (см. design: Domain — PomodoroEngine и TimerState).
 *
 * Значения по умолчанию для длительностей соответствуют конструктору
 * оригинального `PomodoroTimer(work_minutes=25, break_minutes=5)`.
 *
 * Requirements: 6.5, 7.1, 7.2, 7.5, 8.x, 11.1, 11.2
 */
data class TimerState(
    val phase: Phase = Phase.WORK,
    val runState: RunState = RunState.STOPPED,
    val workDurationSec: Int = DEFAULT_WORK_SEC,
    val breakDurationSec: Int = DEFAULT_BREAK_SEC,
    val secondsLeft: Int = DEFAULT_WORK_SEC,
    val phaseEndEpochMs: Long? = null,
    val completedPomodoros: Int = 0,
    val totalPomodoros: Int = 1
) {
    companion object {
        /** Длительность фазы работы по умолчанию — 25 минут (паритет с оригиналом). */
        const val DEFAULT_WORK_SEC: Int = 25 * 60

        /** Длительность фазы отдыха по умолчанию — 5 минут (паритет с оригиналом). */
        const val DEFAULT_BREAK_SEC: Int = 5 * 60
    }
}
