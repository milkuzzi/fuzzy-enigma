package com.studydungeon.domain

/**
 * Предмет Магазина.
 *
 * @property id стабильный идентификатор предмета, определяющий применяемый
 *   эффект при успешной покупке ("potion" → лечение, "scroll" → опыт).
 * @property displayName отображаемое название предмета (показывается в Интерфейсе)
 *   и записывается в Инвентарь Героя при покупке.
 * @property cost стоимость предмета в Золоте; для всех предметов каталога `cost > 0`.
 *
 * Requirements: 5.5, 5.6
 */
data class ShopItem(
    val id: String,
    val displayName: String,
    val cost: Int
)

/**
 * Каталог Магазина и логика покупки с применением эффекта предмета.
 *
 * Перенос игровой логики покупок из оригинального `main.py` (`buy_item`),
 * где успех покупки сопровождается эффектом предмета:
 * - "🧪 Малое зелье" за 20 → `heal(30)`;
 * - "📜 Свиток мудрости" за 50 → начисление 50 опыта (эквивалент `add_reward(50, 0)`).
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
        ShopItem("potion", "🧪 Малое зелье", 20),   // R5.3 -> heal(30)
        ShopItem("scroll", "📜 Свиток мудрости", 50) // R5.4 -> addReward(50, 0)
    )

    /**
     * Покупка [item] Героем [hero].
     *
     * Сначала применяется списание Золота через [HeroEngine.buyItem]
     * (с добавлением [ShopItem.displayName] в Инвентарь). Только при успешной
     * покупке (`success == true`) к итоговому Герою применяется эффект предмета:
     * - "potion" → [HeroEngine.heal] на 30 единиц Здоровья (R5.3);
     * - "scroll" → [HeroEngine.addReward] на 50 опыта и 0 золота, включая
     *   возможные повышения уровня (R5.4).
     *
     * При неудачной покупке (недостаточно Золота) Золото и Инвентарь Героя не
     * изменяются и эффект не применяется.
     *
     * @param hero исходное состояние Героя
     * @param item покупаемый предмет каталога
     * @return [BuyResult] с флагом успеха и итоговым [Hero]
     *
     * Requirements: 5.3, 5.4
     */
    fun purchase(hero: Hero, item: ShopItem): BuyResult {
        val buyResult = HeroEngine.buyItem(hero, item.displayName, item.cost)
        if (!buyResult.success) {
            return buyResult
        }
        val heroWithEffect = when (item.id) {
            "potion" -> HeroEngine.heal(buyResult.hero, 30)
            "scroll" -> HeroEngine.addReward(buyResult.hero, 50, 0)
            else -> buyResult.hero
        }
        return BuyResult(success = true, hero = heroWithEffect)
    }
}
