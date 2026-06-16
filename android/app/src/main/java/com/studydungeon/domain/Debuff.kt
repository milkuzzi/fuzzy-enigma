package com.studydungeon.domain

/**
 * Негативное состояние Героя.
 *
 * Паритет с `Debuff(Enum)` из оригинального `character.py`:
 * значения строк (`value`) совпадают с сериализуемым представлением Python.
 *
 * Requirements: 1.2 — Система_Героя хранит значения Дебаффа только из набора
 * {NONE, TIRED, DISTRACTED, WEAK}.
 */
enum class Debuff(val value: String) {
    NONE("none"),
    TIRED("tired"),
    DISTRACTED("distracted"),
    WEAK("weak");

    companion object {
        /**
         * Возвращает [Debuff] по его строковому значению.
         * При неизвестном значении возвращает [NONE] (отказоустойчивость при загрузке).
         * Паритет с циклом поиска в `HeroCharacter.from_dict`.
         */
        fun fromValue(v: String): Debuff = entries.firstOrNull { it.value == v } ?: NONE
    }
}
