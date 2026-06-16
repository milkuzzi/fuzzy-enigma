package com.studydungeon.domain

/**
 * Неизменяемое состояние игрового Героя.
 *
 * Паритет с `HeroCharacter` из оригинального `character.py`. В отличие от
 * мутабельного Python-класса, доменная модель неизменяема: операции игровой
 * логики ([HeroEngine]) возвращают новый экземпляр [Hero].
 *
 * Начальные значения по умолчанию соответствуют конструктору
 * `HeroCharacter.__init__` и Requirement 1.1:
 * имя "Искатель Знаний", level 1, currentXp 0, xpToNext 100,
 * currentHp/maxHp 100, gold 50, debuff NONE, пустой inventory.
 *
 * Requirements: 1.1, 1.2
 */
data class Hero(
    val name: String = "Искатель Знаний",
    val level: Int = 1,
    val currentXp: Int = 0,
    val xpToNext: Int = 100,
    val currentHp: Int = 100,
    val maxHp: Int = 100,
    val gold: Int = 50,
    val debuff: Debuff = Debuff.NONE,
    val inventory: List<String> = emptyList()
)
