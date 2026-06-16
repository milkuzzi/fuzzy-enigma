package com.studydungeon.data

import com.studydungeon.domain.Debuff
import com.studydungeon.domain.Hero
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Сериализация/десериализация [Hero] в JSON.
 *
 * Паритет формата с Python-оригиналом (`HeroCharacter.to_dict`/`from_dict`
 * из `character.py`, persistence в `save_manager.py`): ключи в snake_case,
 * Дебафф сериализуется своим строковым значением (`"none"`, `"tired"`, ...).
 *
 * Формат соответствует `character_save.json`:
 * ```json
 * {
 *   "name": "Искатель Знаний",
 *   "level": 1,
 *   "current_xp": 0,
 *   "xp_to_next": 100,
 *   "current_hp": 100,
 *   "max_hp": 100,
 *   "gold": 50,
 *   "debuff": "none",
 *   "inventory": []
 * }
 * ```
 *
 * Requirements:
 * - 10.3 — при отсутствии/повреждении сохранения [fromJson] возвращает [Hero] по умолчанию.
 * - 10.4 — поля сохраняются и восстанавливаются без потери данных (round-trip).
 */
object HeroSerializer {

    /**
     * DTO с паритетом полей `to_dict`: snake_case ключи через [SerialName].
     * Дебафф хранится как строковое значение (а не имя enum-константы).
     */
    @Serializable
    private data class HeroDto(
        @SerialName("name") val name: String,
        @SerialName("level") val level: Int,
        @SerialName("current_xp") val currentXp: Int,
        @SerialName("xp_to_next") val xpToNext: Int,
        @SerialName("current_hp") val currentHp: Int,
        @SerialName("max_hp") val maxHp: Int,
        @SerialName("gold") val gold: Int,
        @SerialName("debuff") val debuff: String,
        @SerialName("inventory") val inventory: List<String>
    )

    private val json = Json {
        // Отказоустойчивость при чтении сохранений от других/будущих версий:
        // неизвестные поля игнорируются вместо выброса исключения.
        ignoreUnknownKeys = true
        // Совпадает с json.dump(..., ensure_ascii=False): кириллица в открытом виде.
        encodeDefaults = true
    }

    /**
     * Сериализует [hero] в JSON-строку c snake_case ключами (паритет с `to_dict`).
     */
    fun toJson(hero: Hero): String {
        val dto = HeroDto(
            name = hero.name,
            level = hero.level,
            currentXp = hero.currentXp,
            xpToNext = hero.xpToNext,
            currentHp = hero.currentHp,
            maxHp = hero.maxHp,
            gold = hero.gold,
            debuff = hero.debuff.value,
            inventory = hero.inventory
        )
        return json.encodeToString(dto)
    }

    /**
     * Разбирает JSON-строку в [Hero] (паритет с `from_dict`).
     *
     * Отказоустойчивость (R10.3): при пустом/некорректном вводе или любой
     * ошибке разбора возвращает [Hero] со значениями по умолчанию вместо
     * выброса исключения. Неизвестное значение Дебаффа отображается в NONE
     * (см. [Debuff.fromValue]).
     */
    fun fromJson(json: String): Hero {
        if (json.isBlank()) return Hero()
        return try {
            val dto = this.json.decodeFromString<HeroDto>(json)
            Hero(
                name = dto.name,
                level = dto.level,
                currentXp = dto.currentXp,
                xpToNext = dto.xpToNext,
                currentHp = dto.currentHp,
                maxHp = dto.maxHp,
                gold = dto.gold,
                debuff = Debuff.fromValue(dto.debuff),
                inventory = dto.inventory
            )
        } catch (e: Exception) {
            Hero()
        }
    }
}
