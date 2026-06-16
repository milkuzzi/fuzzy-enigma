package com.studydungeon.domain

/**
 * Чистая игровая логика Героя.
 *
 * Перенос логики `HeroCharacter` из оригинального `character.py`. В отличие от
 * мутабельного Python-класса, каждая операция возвращает **новый** неизменяемый
 * [Hero] без побочных эффектов — это упрощает тестирование и исключает скрытые
 * мутации состояния.
 *
 * Реализовано в рамках задачи 2.1: [addReward] и [levelUp].
 * Остальные операции ([HeroEngine]: takeDamage, heal, buyItem, forceQuitPenalty)
 * добавляются в задаче 2.5 и должны следовать тому же стилю (pure functions,
 * возврат нового [Hero]).
 */
object HeroEngine {

    /**
     * Начисляет Герою награду: опыт и золото, затем применяет повышения уровня,
     * пока накопленного опыта достаточно для перехода на следующий уровень.
     *
     * Паритет с `HeroCharacter.add_reward`:
     * ```python
     * self.current_xp += xp_reward
     * self.gold += gold_reward
     * while self.current_xp >= self.xp_to_next:
     *     self.level_up()
     * ```
     *
     * @param hero исходное состояние Героя
     * @param xpReward начисляемый опыт (ожидается неотрицательное значение)
     * @param goldReward начисляемое золото (ожидается неотрицательное значение)
     * @return новый [Hero] с обновлёнными опытом, золотом и уровнем
     *
     * Requirements: 2.1, 2.2, 2.3
     */
    fun addReward(hero: Hero, xpReward: Int, goldReward: Int): Hero {
        var result = hero.copy(
            currentXp = hero.currentXp + xpReward,
            gold = hero.gold + goldReward
        )
        while (result.currentXp >= result.xpToNext) {
            result = levelUp(result)
        }
        return result
    }

    /**
     * Выполняет одно повышение уровня Героя.
     *
     * Паритет с `HeroCharacter.level_up`:
     * ```python
     * self.level += 1
     * self.current_xp -= self.xp_to_next
     * self.xp_to_next = int(self.xp_to_next * 1.5)
     * self.max_hp += 20
     * self.current_hp = self.max_hp
     * self.debuff = Debuff.NONE
     * ```
     *
     * Примечание: `(xpToNext * 1.5).toInt()` повторяет усечение `int(...)` в Python
     * (отбрасывание дробной части в сторону нуля для неотрицательных значений).
     *
     * @param hero исходное состояние Героя
     * @return новый [Hero] после повышения уровня
     *
     * Requirements: 2.3
     */
    fun levelUp(hero: Hero): Hero {
        val newMaxHp = hero.maxHp + 20
        return hero.copy(
            level = hero.level + 1,
            currentXp = hero.currentXp - hero.xpToNext,
            xpToNext = (hero.xpToNext * 1.5).toInt(),
            maxHp = newMaxHp,
            currentHp = newMaxHp,
            debuff = Debuff.NONE
        )
    }

    /**
     * Наносит Герою урон: уменьшает текущее Здоровье на [damage] с нижней
     * границей 0; если итоговое Здоровье <= 30 — устанавливает дебафф DISTRACTED.
     *
     * Паритет с `HeroCharacter.take_damage`:
     * ```python
     * self.current_hp -= damage
     * if self.current_hp < 0:
     *     self.current_hp = 0
     * if self.current_hp <= 30:
     *     self.debuff = Debuff.DISTRACTED
     * ```
     *
     * @param hero исходное состояние Героя
     * @param damage величина урона (ожидается неотрицательное значение)
     * @return новый [Hero] с обновлённым Здоровьем и, при необходимости, дебаффом
     *
     * Requirements: 3.1, 3.2, 3.3
     */
    fun takeDamage(hero: Hero, damage: Int): Hero {
        val newHp = (hero.currentHp - damage).coerceAtLeast(0)
        return hero.copy(
            currentHp = newHp,
            debuff = if (newHp <= 30) Debuff.DISTRACTED else hero.debuff
        )
    }

    /**
     * Лечит Героя: устанавливает текущее Здоровье равным меньшему из
     * (currentHp + amount) и maxHp; если итоговое Здоровье > 50 — снимает дебафф (NONE).
     *
     * Паритет с `HeroCharacter.heal`:
     * ```python
     * self.current_hp = min(self.current_hp + amount, self.max_hp)
     * if self.current_hp > 50:
     *     self.debuff = Debuff.NONE
     * ```
     *
     * @param hero исходное состояние Героя
     * @param amount величина лечения (ожидается неотрицательное значение)
     * @return новый [Hero] с обновлённым Здоровьем и, при необходимости, снятым дебаффом
     *
     * Requirements: 4.1, 4.2
     */
    fun heal(hero: Hero, amount: Int): Hero {
        val newHp = minOf(hero.currentHp + amount, hero.maxHp)
        return hero.copy(
            currentHp = newHp,
            debuff = if (newHp > 50) Debuff.NONE else hero.debuff
        )
    }

    /**
     * Покупка предмета за Золото.
     *
     * При достаточном Золоте (`gold >= cost`) — списывает стоимость, добавляет
     * [itemName] в Инвентарь и возвращает успешный [BuyResult]. Иначе Золото и
     * Инвентарь не изменяются, результат — неуспешный.
     *
     * Паритет с `HeroCharacter.buy_item`:
     * ```python
     * if self.gold >= cost:
     *     self.gold -= cost
     *     self.inventory.append(item_name)
     *     return True
     * return False
     * ```
     *
     * @param hero исходное состояние Героя
     * @param itemName название покупаемого предмета
     * @param cost стоимость предмета в Золоте
     * @return [BuyResult] с флагом успеха и (обновлённым либо исходным) [Hero]
     *
     * Requirements: 5.1, 5.2
     */
    fun buyItem(hero: Hero, itemName: String, cost: Int): BuyResult {
        return if (hero.gold >= cost) {
            BuyResult(
                success = true,
                hero = hero.copy(
                    gold = hero.gold - cost,
                    inventory = hero.inventory + itemName
                )
            )
        } else {
            BuyResult(success = false, hero = hero)
        }
    }

    /**
     * Применяет штрафы за принудительное завершение Приложения во время работы
     * Таймера: текущее Здоровье `max(0, currentHp - 50)`, текущий Опыт
     * `max(0, currentXp - 30)`, дебафф WEAK — независимо от итогового Здоровья.
     *
     * Поведение задано Requirement 9.3 (в оригинальном `character.py` отдельного
     * метода нет — это Android-специфичная механика принудительного закрытия).
     *
     * @param hero исходное состояние Героя
     * @return новый [Hero] с применёнными штрафами
     *
     * Requirements: 9.3
     */
    fun forceQuitPenalty(hero: Hero): Hero {
        return hero.copy(
            currentHp = (hero.currentHp - 50).coerceAtLeast(0),
            currentXp = (hero.currentXp - 30).coerceAtLeast(0),
            debuff = Debuff.WEAK
        )
    }
}
