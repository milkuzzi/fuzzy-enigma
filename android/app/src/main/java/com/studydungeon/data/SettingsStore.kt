package com.studydungeon.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.studydungeon.domain.TimerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/** Выбор темы оформления. */
enum class ThemeChoice {
    DARK,
    LIGHT;

    companion object {
        fun fromName(name: String?): ThemeChoice =
            entries.firstOrNull { it.name == name } ?: DARK
    }
}

/** Один день статистики: номер дня (epoch day) и число завершённых Помидорок. */
data class DayStat(val epochDay: Long, val count: Int)

/**
 * Сводка статистики для Интерфейса: счётчики, серии (streak) и историю по дням
 * и неделям для графиков.
 */
data class StatsSnapshot(
    val today: Int = 0,
    val total: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    /** Последние 7 дней (от старого к новому), включая сегодня. */
    val last7Days: List<DayStat> = emptyList(),
    /** Суммы по последним 4 неделям (от старой к новой). */
    val last4Weeks: List<Int> = emptyList()
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SettingsStore.STORE_NAME,
    produceMigrations = { ctx ->
        // Перенос ранее сохранённых значений из SharedPreferences (имена ключей
        // совпадают, поэтому совместимые значения копируются автоматически).
        listOf(SharedPreferencesMigration(ctx, SettingsStore.LEGACY_PREFS_NAME))
    }
)

/**
 * Типобезопасное хранилище настроек приложения и статистики поверх Jetpack
 * DataStore (Preferences). Пришло на смену прежнему `AppPreferences` на
 * SharedPreferences — значения мигрируют автоматически при первом обращении.
 *
 * Состояние Героя по-прежнему хранится отдельно в [HeroRepository]; здесь —
 * только настройки интерфейса/устройства и история статистики Помидорок.
 *
 * Чтения предоставляются как [Flow] (реактивно для Compose/ViewModel), записи —
 * как `suspend`-функции. Для немногочисленных синхронных потребителей в сервисе
 * (например, [com.studydungeon.service.SessionFeedback], вызываемый на границах
 * фаз) предусмотрены блокирующие снимки [soundEnabledBlocking]/[vibrationEnabledBlocking].
 */
class SettingsStore(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    // region Настройки таймера

    val workDurationSec: Flow<Int> =
        dataStore.data.map { it[KEY_WORK_SEC] ?: TimerState.DEFAULT_WORK_SEC }

    val breakDurationSec: Flow<Int> =
        dataStore.data.map { it[KEY_BREAK_SEC] ?: TimerState.DEFAULT_BREAK_SEC }

    val totalPomodoros: Flow<Int> =
        dataStore.data.map { it[KEY_TOTAL] ?: 1 }

    suspend fun saveTimerSettings(workSec: Int, breakSec: Int, total: Int) {
        dataStore.edit {
            it[KEY_WORK_SEC] = workSec
            it[KEY_BREAK_SEC] = breakSec
            it[KEY_TOTAL] = total
        }
    }

    // endregion

    // region Обратная связь (звук/вибрация)

    val soundEnabled: Flow<Boolean> =
        dataStore.data.map { it[KEY_SOUND] ?: true }

    val vibrationEnabled: Flow<Boolean> =
        dataStore.data.map { it[KEY_VIBRATION] ?: true }

    suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SOUND] = enabled }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_VIBRATION] = enabled }
    }

    /** Синхронный снимок для сервиса обратной связи (вызывается на смене фаз). */
    fun soundEnabledBlocking(): Boolean = runBlocking { soundEnabled.first() }

    /** Синхронный снимок для сервиса обратной связи (вызывается на смене фаз). */
    fun vibrationEnabledBlocking(): Boolean = runBlocking { vibrationEnabled.first() }

    // endregion

    // region Тема и онбординг

    val themeChoice: Flow<ThemeChoice> =
        dataStore.data.map { ThemeChoice.fromName(it[KEY_THEME]) }

    suspend fun setThemeChoice(choice: ThemeChoice) {
        dataStore.edit { it[KEY_THEME] = choice.name }
    }

    val onboardingCompleted: Flow<Boolean> =
        dataStore.data.map { it[KEY_ONBOARDED] ?: false }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[KEY_ONBOARDED] = completed }
    }

    // endregion

    // region Статистика и серии

    val stats: Flow<StatsSnapshot> =
        dataStore.data.map { deriveStats(it) }

    /** Учитывает одну завершённую Помидорку в истории статистики (по дням). */
    suspend fun recordCompletedPomodoro() {
        dataStore.edit { prefs ->
            // Одноразовый перенос прежнего суммарного счётчика в «базу» истории.
            if (prefs[KEY_STATS_SEEDED] != true) {
                prefs[KEY_STATS_BASELINE] = prefs[KEY_LEGACY_TOTAL] ?: 0
                prefs[KEY_STATS_SEEDED] = true
            }
            val today = currentEpochDay()
            val history = parseHistory(prefs[KEY_STATS_HISTORY]).toMutableMap()
            history[today] = (history[today] ?: 0) + 1
            prefs[KEY_STATS_HISTORY] = encodeHistory(history)
        }
    }

    private fun deriveStats(prefs: Preferences): StatsSnapshot {
        val baseline = if (prefs[KEY_STATS_SEEDED] == true) {
            prefs[KEY_STATS_BASELINE] ?: 0
        } else {
            // До первой записи используем прежний суммарный счётчик как базу.
            prefs[KEY_LEGACY_TOTAL] ?: 0
        }
        val history = parseHistory(prefs[KEY_STATS_HISTORY])
        val today = currentEpochDay()

        val last7Days = (6 downTo 0).map { offset ->
            val day = today - offset
            DayStat(day, history[day] ?: 0)
        }

        val last4Weeks = (3 downTo 0).map { weekOffset ->
            val weekEnd = today - weekOffset * 7L
            (0..6).sumOf { d -> history[weekEnd - d] ?: 0 }
        }

        return StatsSnapshot(
            today = history[today] ?: 0,
            total = baseline + history.values.sum(),
            currentStreak = currentStreak(history, today),
            bestStreak = bestStreak(history),
            last7Days = last7Days,
            last4Weeks = last4Weeks
        )
    }

    private fun currentStreak(history: Map<Long, Int>, today: Long): Int {
        // Серия считается от сегодня (или вчера, если сегодня ещё нет помидорок).
        var day = if ((history[today] ?: 0) > 0) today else today - 1
        var streak = 0
        while ((history[day] ?: 0) > 0) {
            streak++
            day--
        }
        return streak
    }

    private fun bestStreak(history: Map<Long, Int>): Int {
        val activeDays = history.filterValues { it > 0 }.keys.sorted()
        if (activeDays.isEmpty()) return 0
        var best = 1
        var current = 1
        for (i in 1 until activeDays.size) {
            if (activeDays[i] == activeDays[i - 1] + 1) {
                current++
            } else {
                current = 1
            }
            if (current > best) best = current
        }
        return best
    }

    // endregion

    private fun currentEpochDay(): Long =
        TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())

    private fun parseHistory(raw: String?): Map<Long, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString(historySerializer, raw) }.getOrDefault(emptyMap())
    }

    private fun encodeHistory(history: Map<Long, Int>): String =
        json.encodeToString(historySerializer, history)

    companion object {
        const val STORE_NAME = "study_dungeon_settings"
        const val LEGACY_PREFS_NAME = "study_dungeon_app"

        private val KEY_WORK_SEC = intPreferencesKey("work_sec")
        private val KEY_BREAK_SEC = intPreferencesKey("break_sec")
        private val KEY_TOTAL = intPreferencesKey("total_pomodoros")
        private val KEY_SOUND = booleanPreferencesKey("sound_enabled")
        private val KEY_VIBRATION = booleanPreferencesKey("vibration_enabled")
        private val KEY_THEME = stringPreferencesKey("theme_choice")
        private val KEY_ONBOARDED = booleanPreferencesKey("onboarding_completed")

        private val KEY_STATS_HISTORY = stringPreferencesKey("stats_history")
        private val KEY_STATS_BASELINE = intPreferencesKey("stats_baseline")
        private val KEY_STATS_SEEDED = booleanPreferencesKey("stats_seeded")

        /** Прежний суммарный счётчик из SharedPreferences (для переноса). */
        private val KEY_LEGACY_TOTAL = intPreferencesKey("stat_total")

        private val json = Json { ignoreUnknownKeys = true }
        private val historySerializer = MapSerializer(Long.serializer(), Int.serializer())
    }
}
