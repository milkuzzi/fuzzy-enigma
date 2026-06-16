package com.studydungeon.ui

import com.studydungeon.domain.Debuff
import com.studydungeon.domain.Hero
import com.studydungeon.domain.TimerState

/**
 * Сворачиваемая секция интерфейса, видимость которой может скрываться в
 * Режиме_Фокуса и переключаться пользователем.
 *
 * Requirements: 13.6, 14.4
 */
enum class Section { SETTINGS, SHOP, INVENTORY }

/**
 * Неизменяемый агрегат состояния интерфейса для Compose.
 *
 * Объединяет доменное состояние ([hero], [timer]) с состоянием представления:
 * флагом активности Режима_Фокуса ([focusMode]) и **намеренной** (выбранной
 * пользователем) видимостью сворачиваемых секций
 * ([settingsVisible], [shopVisible], [inventoryVisible]).
 *
 * Ключевой принцип: флаги видимости хранят именно выбор пользователя и
 * **никогда не мутируются** при активации/деактивации Режима_Фокуса. Видимая на
 * экране (эффективная) видимость вычисляется на лету функцией
 * [isSectionVisible] как `намерение И НЕ режим_фокуса`. Благодаря этому после
 * выхода из Режима_Фокуса видимость автоматически восстанавливается до
 * состояния, бывшего до активации (Property 29 / R13.6) — снимок/восстановление
 * не требуются.
 *
 * Все операции реализованы как чистые функции без зависимостей от Android SDK,
 * что обеспечивает паритет с дизайном и пригодность для property-тестирования.
 *
 * Requirements: 13.6, 14.4, 14.6
 */
data class UiState(
    val hero: Hero = Hero(),
    val timer: TimerState = TimerState(),
    val focusMode: Boolean = false,
    val settingsVisible: Boolean = true,
    val shopVisible: Boolean = true,
    val inventoryVisible: Boolean = true,
    val todayPomodoros: Int = 0,
    val totalPomodoros: Int = 0
) {
    /**
     * Активирует Режим_Фокуса. Намеренная видимость секций сохраняется без
     * изменений — это и обеспечивает её восстановление при деактивации.
     *
     * Requirements: 13.6
     */
    fun activateFocusMode(): UiState =
        if (focusMode) this else copy(focusMode = true)

    /**
     * Деактивирует Режим_Фокуса. Так как намеренная видимость не менялась при
     * активации, эффективная видимость секций восстанавливается до состояния,
     * бывшего до активации (Property 29 / R13.6).
     *
     * Requirements: 13.6
     */
    fun deactivateFocusMode(): UiState =
        if (!focusMode) this else copy(focusMode = false)

    /**
     * Переключает (сворачивает/разворачивает) намеренную видимость секции.
     * Двойной вызов с одной и той же секцией возвращает состояние видимости к
     * исходному (Property 30 / R14.4).
     *
     * Requirements: 14.4
     */
    fun toggleSection(section: Section): UiState = when (section) {
        Section.SETTINGS -> copy(settingsVisible = !settingsVisible)
        Section.SHOP -> copy(shopVisible = !shopVisible)
        Section.INVENTORY -> copy(inventoryVisible = !inventoryVisible)
    }

    /** Намеренная (выбранная пользователем) видимость секции, без учёта Режима_Фокуса. */
    fun isSectionIntendedVisible(section: Section): Boolean = when (section) {
        Section.SETTINGS -> settingsVisible
        Section.SHOP -> shopVisible
        Section.INVENTORY -> inventoryVisible
    }

    /**
     * Эффективная (отображаемая на экране) видимость секции.
     *
     * В Режиме_Фокуса секции настроек, магазина и инвентаря всегда невидимы
     * (Property 29 / R13.6). Вне Режима_Фокуса видимость равна намеренному
     * выбору пользователя.
     *
     * Requirements: 13.6
     */
    fun isSectionVisible(section: Section): Boolean =
        !focusMode && isSectionIntendedVisible(section)

    /**
     * Видимость индикатора активного Дебаффа: виден тогда и только тогда, когда
     * Дебафф Героя отличается от [Debuff.NONE] (Property 31 / R14.6).
     *
     * Requirements: 14.6
     */
    fun isDebuffIndicatorVisible(): Boolean = hero.debuff != Debuff.NONE
}
