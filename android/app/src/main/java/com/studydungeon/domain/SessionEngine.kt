package com.studydungeon.domain

/**
 * Чистая доменная логика правил уровня Сессии: награды за успех Фазы_Работы,
 * штрафы провала Сессии и длительность блокировки Режима_Фокуса.
 *
 * Этот объект инкапсулирует «склейку» между [PomodoroEngine] (машина состояний
 * Таймера) и [HeroEngine] (характеристики Героя), которая в оригинале была
 * размазана по обработчикам UI в `main.py`:
 * ```python
 * # успешное завершение Фазы_Работы
 * self.hero.add_reward(50, 10)
 * # провал/отвлечение
 * self.hero.take_damage(15)
 * # старт: длительность блокировки = длительность работы
 * self.timer.work_duration = self.work_time * 60
 * ```
 *
 * Логика вынесена в отдельный объект без зависимостей от Android SDK, чтобы её
 * можно было проверять property-тестами (свойства 14, 20, 24, 26).
 *
 * Requirements: 7.6, 9.2, 9.4, 6.6, 13.1, 13.10
 */
object SessionEngine {

    /** Опыт, начисляемый за успешное завершение Фазы_Работы (паритет с `add_reward(50, 10)`). */
    const val WORK_SUCCESS_XP: Int = 50

    /** Золото, начисляемое за успешное завершение Фазы_Работы (паритет с `add_reward(50, 10)`). */
    const val WORK_SUCCESS_GOLD: Int = 10

    /** Урон, наносимый Герою при провале Сессии или отвлечении (паритет с `take_damage(15)`). */
    const val SESSION_FAIL_DAMAGE: Int = 15

    /**
     * Применяет награду за успешное завершение Фазы_Работы.
     *
     * Эквивалентно `HeroEngine.addReward(hero, 50, 10)`: 50 Опыта и 10 Золота
     * начисляются вместе через единственный вызов [HeroEngine.addReward],
     * который возвращает один новый [Hero] — обе части награды применяются
     * атомарно (вместе либо не применяются вовсе), что соответствует
     * Requirement 7.6 / Property 20.
     *
     * @param hero исходное состояние Героя.
     * @return новый [Hero] с начисленными 50 Опыта и 10 Золота (с учётом
     *   возможных повышений уровня по правилам [HeroEngine.addReward]).
     *
     * Requirements: 7.6
     */
    fun applyWorkSuccessReward(hero: Hero): Hero =
        HeroEngine.addReward(hero, WORK_SUCCESS_XP, WORK_SUCCESS_GOLD)

    /**
     * Применяет штраф за провал Сессии или фиксацию отвлечения.
     *
     * Эквивалентно `HeroEngine.takeDamage(hero, 15)`: Герою наносится 15 единиц
     * урона с применением нижней границы Здоровья (0) и правила дебаффа
     * DISTRACTED из [HeroEngine.takeDamage] (Requirement 9.2 / Property 24).
     *
     * Этот же штраф применяется при принудительном выходе из Закрепления_Экрана
     * во время активного Режима_Фокуса до завершения Фазы_Работы
     * (Requirements 9.4, 13.10 / Property 26).
     *
     * @param hero исходное состояние Героя.
     * @return новый [Hero] после нанесения 15 урона.
     *
     * Requirements: 9.2, 9.4, 13.10
     */
    fun applySessionFailPenalty(hero: Hero): Hero =
        HeroEngine.takeDamage(hero, SESSION_FAIL_DAMAGE)

    /**
     * Вычисляет Длительность_Блокировки Режима_Фокуса при старте Таймера.
     *
     * Длительность блокировки совпадает с длительностью Фазы_Работы текущей
     * сессии: при старте `Длительность_Блокировки = workSec` (паритет с
     * `self.timer.work_duration = self.work_time * 60` в оригинале).
     *
     * @param timerState текущее состояние Таймера.
     * @return длительность блокировки в секундах, равная
     *   [TimerState.workDurationSec].
     *
     * Requirements: 6.6, 13.1
     */
    fun lockDurationSec(timerState: TimerState): Int =
        timerState.workDurationSec
}
