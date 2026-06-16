package com.studydungeon.domain

/**
 * Предмет Магазина.
 *
 * @property id стабильный идентификатор предмета, определяющий применяемый
 *   эффект при успешной покупке ("potion" → лечение, "scroll" → опыт).
 * @property displayName отображаемое название предмета (показывается в Интерфейсе)
 *   и записывается в Инвентарь Героя при покупке.
 * @property description краткое пояснение эффекта предмета (показывается в
 *   Магазине, чтобы было понятно, что делает предмет).
 * @property cost стоимость предмета в Золоте; для всех предметов каталога `cost > 0`.
 *
 * Requirements: 5.5, 5.6
 */
data class ShopItem(
    val id: String,
    val displayName: String,
    val description: String,
    val cost: Int
)

/**
 * Каталог Магазина и логика покупки с применением эффекта предмета.
 *
 * Перенос игровой логики покупок из оригинального `main.py` (`buy_item`),
 * где успех покупки сопровождается эффектом предмета:
 * - "Малое зелье" за 20 → `heal(30)`;
 * - "Свиток мудрости" за 50 → начисление 50 опыта (эквивалент `add_reward(50, 0)`).
 *
 * В отличие от мутабельного Python-кода, [purchase] — чистая функция: она не
 * мутирует исходного Героя, а возвращает [BuyResult] с итоговым состоянием.
 */
object ShopCatalog {

    /**
     * Доступные для покупки предметы. Все предметы имеют `cost > 0` (R5.5) и
     * предоставляют название и стоимость для отображения в Интерфейсе (R5.6).
     *
     * Requirements: 5.5, 5.6
     */
    val items: List<ShopItem> = listOf(
        ShopItem(
            id = "potion",
            displayName = "Малое зелье",
            description = "Восстанавливает 30 единиц здоровья героя.",
            cost = 20
        ),   // эффект при использовании -> heal(30)
        ShopItem(
            id = "scroll",
            displayName = "Свиток мудрости",
            description = "Даёт 50 опыта герою.",
            cost = 50
        ) // эффект при использовании -> addReward(50, 0)
    )

    /**
     * Покупка [item] Героем [hero].
     *
     * Списывает стоимость через [HeroEngine.buyItem] и кладёт **идентификатор**
     * предмета в Инвентарь как расходник. Эффект предмета при покупке больше не
     * применяется — он срабатывает при использовании ([useItem]). При недостатке
     * Золота Золото и Инвентарь не изменяются.
     *
     * @param hero исходное состояние Героя
     * @param item покупаемый предмет каталога
     * @return [BuyResult] с флагом успеха и итоговым [Hero]
     */
    fun purchase(hero: Hero, item: ShopItem): BuyResult =
        HeroEngine.buyItem(hero, item.id, item.cost)

    /**
     * Сопоставляет запись Инвентаря (идентификатор или старое отображаемое имя —
     * для совместимости с сохранениями прежних версий) с предметом каталога.
     */
    fun itemForEntry(entry: String): ShopItem? =
        items.firstOrNull { it.id == entry || it.displayName == entry }

    /** Отображаемое имя записи Инвентаря (по каталогу либо как есть). */
    fun displayNameForEntry(entry: String): String =
        itemForEntry(entry)?.displayName ?: entry

    /** Описание эффекта записи Инвентаря, либо null если предмет неизвестен. */
    fun descriptionForEntry(entry: String): String? =
        itemForEntry(entry)?.description

    /**
     * Использует предмет Инвентаря по индексу [index]: применяет его эффект к
     * Герою и удаляет одну единицу предмета из Инвентаря (расходник).
     *
     * - "potion" → [HeroEngine.heal] на 30 единиц Здоровья;
     * - "scroll" → [HeroEngine.addReward] на 50 опыта (с учётом повышений уровня);
     * - неизвестный предмет просто удаляется без эффекта.
     *
     * При выходе [index] за границы Инвентаря Герой возвращается без изменений.
     *
     * @return новый [Hero] после применения эффекта и расхода предмета.
     */
    fun useItem(hero: Hero, index: Int): Hero {
        if (index !in hero.inventory.indices) return hero
        val entry = hero.inventory[index]
        val remaining = hero.inventory.toMutableList().apply { removeAt(index) }
        val consumed = hero.copy(inventory = remaining)
        return when (itemForEntry(entry)?.id) {
            "potion" -> HeroEngine.heal(consumed, 30)
            "scroll" -> HeroEngine.addReward(consumed, 50, 0)
            else -> consumed
        }
    }
}
