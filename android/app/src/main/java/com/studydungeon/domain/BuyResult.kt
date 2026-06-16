package com.studydungeon.domain

/**
 * Результат попытки покупки предмета.
 *
 * Возвращается из [HeroEngine.buyItem] (и используется [ShopCatalog.purchase]).
 * Делает явным исход покупки без мутаций исходного состояния:
 * - [success] — была ли покупка успешной (достаточно ли Золота);
 * - [hero] — итоговое состояние Героя: при успехе с обновлёнными Золотом и
 *   Инвентарём, при неудаче — исходный Герой без изменений.
 *
 * Requirements: 5.1, 5.2
 */
data class BuyResult(
    val success: Boolean,
    val hero: Hero
)
