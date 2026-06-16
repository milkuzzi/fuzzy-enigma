package com.studydungeon.data

import android.content.Context
import com.studydungeon.domain.TimerState
import java.util.concurrent.TimeUnit

/**
 * Лёгкое хранилище пользовательских настроек приложения (не игровая логика):
 * сохранённые длительности Таймера, переключатели звука/вибрации на смену фаз и
 * простая дневная статистика завершённых Помидорок.
 *
 * Состояние Героя живёт отдельно в [HeroRepository]; здесь — только настройки
 * интерфейса/устройства в [android.content.SharedPreferences]. Класс
 * используется и UI-слоем, и [com.studydungeon.service.SessionFeedback] в
 * сервисе, поэтому держит контекст приложения.
 */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Сохранённая длительность Фазы_Работы (сек). По умолчанию — 25 минут. */
    var workDurationSec: Int
        get() = prefs.getInt(KEY_WORK_SEC, TimerState.DEFAULT_WORK_SEC)
        set(value) = prefs.edit().putInt(KEY_WORK_SEC, value).apply()

    /** Сохранённая длительность Фазы_Отдыха (сек). По умолчанию — 5 минут. */
    var breakDurationSec: Int
        get() = prefs.getInt(KEY_BREAK_SEC, TimerState.DEFAULT_BREAK_SEC)
        set(value) = prefs.edit().putInt(KEY_BREAK_SEC, value).apply()

    /** Сохранённое число Помидорок в Серии. */
    var totalPomodoros: Int
        get() = prefs.getInt(KEY_TOTAL, 1)
        set(value) = prefs.edit().putInt(KEY_TOTAL, value).apply()

    /** Проигрывать ли звук на смене фаз/завершении серии. */
    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    /** Вибрировать ли на смене фаз/завершении серии. */
    var vibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION, value).apply()

    /** Сохраняет длительности и число Помидорок одной транзакцией. */
    fun saveTimerSettings(workSec: Int, breakSec: Int, total: Int) {
        prefs.edit()
            .putInt(KEY_WORK_SEC, workSec)
            .putInt(KEY_BREAK_SEC, breakSec)
            .putInt(KEY_TOTAL, total)
            .apply()
    }

    /** Всего завершённых Помидорок за всё время. */
    val totalCompletedPomodoros: Int
        get() = prefs.getInt(KEY_STAT_TOTAL, 0)

    /** Завершённых Помидорок сегодня (сбрасывается при смене дня). */
    val todayCompletedPomodoros: Int
        get() = if (prefs.getLong(KEY_STAT_DAY, 0L) == currentDay()) {
            prefs.getInt(KEY_STAT_TODAY, 0)
        } else {
            0
        }

    /**
     * Учитывает одну завершённую Помидорку в статистике. При наступлении нового
     * дня дневной счётчик начинается заново.
     */
    fun recordCompletedPomodoro() {
        val today = currentDay()
        val sameDay = prefs.getLong(KEY_STAT_DAY, 0L) == today
        val todayCount = if (sameDay) prefs.getInt(KEY_STAT_TODAY, 0) else 0
        prefs.edit()
            .putInt(KEY_STAT_TOTAL, prefs.getInt(KEY_STAT_TOTAL, 0) + 1)
            .putInt(KEY_STAT_TODAY, todayCount + 1)
            .putLong(KEY_STAT_DAY, today)
            .apply()
    }

    private fun currentDay(): Long =
        TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())

    private companion object {
        const val PREFS_NAME = "study_dungeon_app"
        const val KEY_WORK_SEC = "work_sec"
        const val KEY_BREAK_SEC = "break_sec"
        const val KEY_TOTAL = "total_pomodoros"
        const val KEY_SOUND = "sound_enabled"
        const val KEY_VIBRATION = "vibration_enabled"
        const val KEY_STAT_TOTAL = "stat_total"
        const val KEY_STAT_TODAY = "stat_today"
        const val KEY_STAT_DAY = "stat_day"
    }
}
