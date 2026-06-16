# Implementation Plan: Android Study Dungeon

## Overview

Реализация нативного Android-приложения Study Dungeon на **Kotlin + Jetpack Compose** по слоистой архитектуре MVVM с однонаправленным потоком данных. Сначала реализуется чистая доменная логика (`HeroEngine`, `ShopCatalog`, `PomodoroEngine`, `HeroSerializer`, логика видимости UI-секций) с покрытием property-тестами по разделу Correctness Properties дизайна (свойства 1–31). Затем добавляются слой данных, платформенные сервисы (foreground service, режим фокуса, уведомления), ViewModel и Compose-UI, после чего всё связывается воедино.

Доменная логика реализуется как чистые функции без зависимостей от Android SDK, что обеспечивает тестируемость и паритет с оригинальными Python-модулями.

## Tasks

- [x] 1. Set up project structure, domain models, and testing framework
  - [x] 1.1 Initialize Android project and module layout
    - Создать Android-проект (Kotlin, Jetpack Compose) с пакетами `domain`, `data`, `ui`, `service`
    - Настроить Gradle: Compose, корутины, сериализация (kotlinx.serialization или Jackson)
    - Подключить property-библиотеку (kotest property или jqwik) и unit-тестовый раннер для JVM
    - _Requirements: 1.1_
  - [x] 1.2 Define core domain data models
    - Реализовать `Debuff` (NONE, TIRED, DISTRACTED, WEAK) с `value`/`fromValue`
    - Реализовать immutable data class `Hero` с начальными значениями по умолчанию (имя "Искатель Знаний", level 1, currentXp 0, xpToNext 100, currentHp/maxHp 100, gold 50, debuff NONE, пустой inventory)
    - _Requirements: 1.1, 1.2_
  - [ ]* 1.3 Write unit test for default Hero initialization
    - Проверить, что `Hero()` по умолчанию имеет точные начальные значения (R1.1)
    - _Requirements: 1.1_

- [x] 2. Implement HeroEngine domain logic
  - [x] 2.1 Implement reward and level-up logic
    - Реализовать `addReward(hero, xpReward, goldReward)`: прибавить золото и опыт, затем цикл `levelUp` пока `currentXp >= xpToNext`
    - Реализовать `levelUp(hero)`: level+1, currentXp -= xpToNext, xpToNext = (xpToNext*1.5).toInt(), maxHp+20, currentHp = maxHp, debuff = NONE
    - _Requirements: 2.1, 2.2, 2.3_
  - [ ]* 2.2 Write property test for reward gold and effective XP
    - **Property 2: Начисление награды увеличивает золото и эффективный опыт**
    - **Validates: Requirements 2.1**
  - [ ]* 2.3 Write property test for XP below next-level threshold after reward
    - **Property 3: После начисления награды опыт меньше порога следующего уровня**
    - **Validates: Requirements 2.2**
  - [ ]* 2.4 Write property test for level-up transformations
    - **Property 4: Повышение уровня применяет корректные преобразования**
    - **Validates: Requirements 2.3**
  - [x] 2.5 Implement damage, heal, and force-quit penalty logic
    - Реализовать `takeDamage(hero, damage)`: currentHp -= damage, нижняя граница 0, если currentHp <= 30 → debuff DISTRACTED
    - Реализовать `heal(hero, amount)`: currentHp = min(currentHp+amount, maxHp), если currentHp > 50 → debuff NONE
    - Реализовать `buyItem(hero, itemName, cost)` → `BuyResult` (списание + добавление в инвентарь при достаточном золоте)
    - Реализовать `forceQuitPenalty(hero)`: currentHp = max(0, hp-50), currentXp = max(0, xp-30), debuff WEAK
    - _Requirements: 3.1, 3.2, 3.3, 4.1, 4.2, 5.1, 5.2, 9.3_
  - [ ]* 2.6 Write property test for debuff validity across operations
    - **Property 1: Допустимость дебаффа сохраняется**
    - **Validates: Requirements 1.2**
  - [ ]* 2.7 Write property test for damage with floor at 0
    - **Property 5: Урон уменьшает Здоровье с нижней границей 0**
    - **Validates: Requirements 3.1, 3.2**
  - [ ]* 2.8 Write property test for low-HP DISTRACTED debuff
    - **Property 6: Низкое Здоровье после урона даёт дебафф DISTRACTED**
    - **Validates: Requirements 3.3**
  - [ ]* 2.9 Write property test for heal capped at max HP
    - **Property 7: Лечение ограничено максимальным Здоровьем**
    - **Validates: Requirements 4.1**
  - [ ]* 2.10 Write property test for heal removing debuff
    - **Property 8: Высокое Здоровье после лечения снимает дебафф**
    - **Validates: Requirements 4.2**
  - [ ]* 2.11 Write property test for buy item gold sufficiency
    - **Property 9: Покупка зависит от достаточности золота**
    - **Validates: Requirements 5.1, 5.2**
  - [ ]* 2.12 Write property test for force-quit fixed penalties
    - **Property 25: Принудительное закрытие применяет фиксированные штрафы**
    - **Validates: Requirements 9.3**

- [x] 3. Implement ShopCatalog domain logic
  - [x] 3.1 Implement shop catalog and purchase effects
    - Реализовать `ShopItem` и `ShopCatalog.items` (Малое зелье 20, Свиток мудрости 50, все cost > 0)
    - Реализовать `purchase(hero, item)`: применить `buyItem`, при успехе применить эффект (зелье → heal(30), свиток → addReward(50, 0))
    - _Requirements: 5.3, 5.4, 5.5, 5.6_
  - [ ]* 3.2 Write property test for successful potion purchase
    - **Property 10: Успешная покупка зелья лечит на 30**
    - **Validates: Requirements 5.3**
  - [ ]* 3.3 Write property test for successful scroll purchase
    - **Property 11: Успешная покупка свитка начисляет 50 опыта**
    - **Validates: Requirements 5.4**
  - [ ]* 3.4 Write unit tests for shop catalog invariants
    - Проверить, что все предметы имеют cost > 0 (R5.5) и предоставляют название и стоимость (R5.6)
    - _Requirements: 5.5, 5.6_

- [x] 4. Checkpoint - Hero and shop domain logic
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement PomodoroEngine and timer state machine
  - [x] 5.1 Implement TimerState and settings/start/pause logic
    - Реализовать `Phase`, `RunState`, data class `TimerState` (с `phaseEndEpochMs` как источником истины)
    - Реализовать `applySettings`: отклонять при RUNNING (R6.4); иначе установить длительности, secondsLeft = workSec, phase = WORK
    - Реализовать `start(state, nowMs)`: RUNNING, phaseEndEpochMs = nowMs + secondsLeft*1000
    - Реализовать `pause(state, nowMs)`: PAUSED, сохранить оставшееся время
    - Реализовать `formatTime(secondsLeft)` → "ММ:СС"
    - _Requirements: 6.4, 6.5, 7.1, 7.2, 7.5_
  - [ ]* 5.2 Write property test for settings rejected while running
    - **Property 12: Настройки отклоняются при работающем таймере**
    - **Validates: Requirements 6.4**
  - [ ]* 5.3 Write property test for applying settings
    - **Property 13: Применение настроек устанавливает длительности и оставшееся время**
    - **Validates: Requirements 6.5**
  - [ ]* 5.4 Write property test for start transition
    - **Property 15: Старт переводит таймер в выполнение**
    - **Validates: Requirements 7.1**
  - [ ]* 5.5 Write property test for time format round-trip
    - **Property 16: Round-trip форматирования времени**
    - **Validates: Requirements 7.2**
  - [ ]* 5.6 Write property test for pause preserving remaining time
    - **Property 19: Пауза сохраняет оставшееся время**
    - **Validates: Requirements 7.5**
  - [x] 5.7 Implement tick, phase switching, series, and fail logic
    - Реализовать `tick(state, nowMs)` → `TickResult` с wall-clock расчётом `secondsLeft = max(0, (phaseEndEpochMs - nowMs)/1000)`
    - WORK→ноль: completedPomodoros+1, переход в BREAK (secondsLeft = breakDuration); если completedPomodoros >= total → `SeriesCompleted`
    - BREAK→ноль: переход в WORK (secondsLeft = workDuration)
    - Реализовать `fail(state)`: STOPPED, phase = WORK, secondsLeft = workDuration
    - Реализовать запуск новой серии (сброс completedPomodoros = 0)
    - _Requirements: 7.3, 7.4, 8.1, 8.3, 8.4, 9.1, 11.1, 11.2, 13.4_
  - [ ]* 5.8 Write property test for work-phase completion
    - **Property 17: Завершение фазы работы фиксирует помидорку и переходит к отдыху**
    - **Validates: Requirements 7.3, 8.1**
  - [ ]* 5.9 Write property test for break-phase completion
    - **Property 18: Завершение фазы отдыха переходит к работе**
    - **Validates: Requirements 7.4**
  - [ ]* 5.10 Write property test for series completion
    - **Property 21: Достижение цели серии фиксирует завершение серии**
    - **Validates: Requirements 8.3**
  - [ ]* 5.11 Write property test for new series reset
    - **Property 22: Новая серия обнуляет счётчик помидорок**
    - **Validates: Requirements 8.4**
  - [ ]* 5.12 Write property test for give-up reset
    - **Property 23: Сдача останавливает таймер и сбрасывает в фазу работы**
    - **Validates: Requirements 9.1**
  - [ ]* 5.13 Write property test for wall-clock countdown fidelity
    - **Property 28: Достоверность отсчёта по «настенным» часам**
    - **Validates: Requirements 11.1, 11.2, 13.4**

- [x] 6. Implement session reward and failure domain logic
  - [x] 6.1 Implement work-success reward and session-fail penalty application
    - Связать успешное завершение Фазы_Работы с `HeroEngine.addReward(hero, 50, 10)` атомарно
    - Реализовать применение штрафа провала сессии `HeroEngine.takeDamage(hero, 15)` (провал/отвлечение)
    - Реализовать применение длительности блокировки: при старте Длительность_Блокировки = workSec
    - _Requirements: 7.6, 9.2, 9.4, 6.6, 13.1, 13.10_
  - [ ]* 6.2 Write property test for atomic work-success reward
    - **Property 20: Успех фазы работы начисляет награду атомарно**
    - **Validates: Requirements 7.6**
  - [ ]* 6.3 Write property test for session-fail 15 damage
    - **Property 24: Провал сессии наносит 15 урона**
    - **Validates: Requirements 9.2**
  - [ ]* 6.4 Write property test for lock duration equals work duration
    - **Property 14: Длительность блокировки равна длительности фазы работы**
    - **Validates: Requirements 6.6, 13.1**
  - [ ]* 6.5 Write property test for screen-pin exit equals session fail
    - **Property 26: Выход из закрепления эквивалентен провалу сессии**
    - **Validates: Requirements 9.4, 13.10**

- [x] 7. Implement data persistence layer
  - [x] 7.1 Implement HeroSerializer
    - Реализовать `toJson(hero)` с паритетом полей `to_dict` (snake_case)
    - Реализовать `fromJson(json)` с отказоустойчивостью: при ошибке разбора → `Hero()` по умолчанию
    - _Requirements: 10.3, 10.4_
  - [ ]* 7.2 Write property test for Hero save round-trip
    - **Property 27: Round-trip сохранения состояния Героя**
    - **Validates: Requirements 10.4**
  - [ ]* 7.3 Write unit tests for corrupted/missing JSON handling
    - Проверить, что невалидный, пустой и отсутствующий JSON → дефолтный Герой (R10.3)
    - _Requirements: 10.3_
  - [x] 7.4 Implement FileHeroRepository
    - Реализовать `HeroRepository` с `load()`/`save()` через JSON-файл во внутреннем хранилище (`filesDir/character_save.json`)
    - Реализовать атомарную запись (temp-файл + rename); при отсутствии/повреждении файла `load()` → дефолтный Герой
    - _Requirements: 10.1, 10.2, 10.3_
  - [ ]* 7.5 Write integration test for load-after-save
    - Проверить, что `save` затем `load` возвращает того же Героя (R10.2)
    - _Requirements: 10.2_

- [x] 8. Checkpoint - Timer engine and persistence
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Implement UI state and section visibility logic
  - [x] 9.1 Implement UiState and section visibility rules
    - Реализовать `UiState` (Hero + TimerState + флаги видимости секций + флаг режима фокуса)
    - Реализовать логику: в режиме фокуса секции настроек/магазина/инвентаря скрыты, после деактивации видимость восстанавливается
    - Реализовать переключение (toggle) сворачиваемых секций и вычисление индикатора дебаффа (видим ⇔ debuff != NONE)
    - _Requirements: 13.6, 14.4, 14.6_
  - [ ]* 9.2 Write property test for hiding sections in focus mode
    - **Property 29: Скрытие секций в режиме фокуса**
    - **Validates: Requirements 13.6**
  - [ ]* 9.3 Write property test for section toggle idempotence
    - **Property 30: Идемпотентность пары переключений секции**
    - **Validates: Requirements 14.4**
  - [ ]* 9.4 Write property test for debuff indicator visibility
    - **Property 31: Индикатор дебаффа отображается тогда и только тогда, когда дебафф активен**
    - **Validates: Requirements 14.6**

- [x] 10. Implement platform services
  - [x] 10.1 Implement NotificationController
    - Реализовать каналы уведомлений, постоянное уведомление таймера (фаза + оставшееся время) и событийные уведомления (переход к отдыху/работе, завершение серии, напоминание о возврате)
    - Запрашивать `POST_NOTIFICATIONS` при первой отправке; при отказе продолжать без уведомлений
    - _Requirements: 11.3, 12.1, 12.2, 12.3, 12.4, 13.11_
  - [x] 10.2 Implement TimerForegroundService
    - Реализовать foreground service с тикером (раз в секунду), вызывающим `PomodoroEngine.tick`, обновление постоянного уведомления и публикацию `TimerState` через `StateFlow`
    - Обеспечить работу при свёрнутом приложении/выключенном экране и восстановление состояния из `phaseEndEpochMs`
    - Отправлять событийные уведомления при смене фаз и завершении серии (через NotificationController)
    - _Requirements: 11.1, 11.2, 11.3, 12.1, 12.2, 12.3_
  - [x] 10.3 Implement FocusModeController
    - Реализовать `activate(lockDurationSec)`: `startLockTask()` + `KEEP_SCREEN_ON`, длительность = workSec
    - Реализовать `deactivate()`: `stopLockTask()`, возврат экрана к обычному поведению
    - Реализовать детекцию выхода из Lock Task (`onLockTaskExitDetected`) и обработку недоступности закрепления (запрос/инструкция)
    - _Requirements: 13.1, 13.2, 13.3, 13.5, 13.7, 13.8, 13.9, 13.10_

- [x] 11. Implement ViewModel and wire components together
  - [x] 11.1 Implement StudyDungeonViewModel
    - Связать домен (`HeroEngine`, `ShopCatalog`, `PomodoroEngine`), `HeroRepository`, foreground service, FocusModeController и NotificationController
    - Экспонировать `UiState`; обрабатывать применение настроек (отклонение при работе), старт/паузу/сдачу, покупки, начисление награды за помидорку и штрафы провала
    - Сохранять состояние Героя после каждой мутации; активировать/деактивировать режим фокуса и скрытие секций
    - Обрабатывать принудительный выход из закрепления как провал сессии и принудительное закрытие приложения как штраф R9.3
    - _Requirements: 6.4, 7.1, 7.5, 7.6, 8.1, 8.4, 9.1, 9.2, 9.3, 9.4, 10.1, 13.6, 13.10_
  - [ ]* 11.2 Write integration test for save after each Hero mutation
    - Проверить вызов `save` после награды/урона/лечения/покупки (R10.1)
    - _Requirements: 10.1_

- [x] 12. Implement Compose UI
  - [x] 12.1 Implement Hero stats and timer display
    - Реализовать панель характеристик (уровень, индикатор Опыта, индикатор Здоровья, Золото, индикатор Дебаффа)
    - Реализовать отображение таймера (метка фазы, "ММ:СС", счётчик помидорок серии) и анимацию успеха при завершении помидорки
    - _Requirements: 1.3, 7.2, 8.2, 14.1, 14.2, 14.5, 14.6_
  - [x] 12.2 Implement controls, settings, shop, and inventory sections
    - Реализовать элементы управления (старт/пауза/сдача) и сворачиваемые секции настроек, магазина и инвентаря
    - Реализовать ввод длительностей и числа помидорок с клампингом диапазонов (1–60, 1–30, 1–10) и отображение каталога/инвентаря
    - _Requirements: 5.6, 5.7, 6.1, 6.2, 6.3, 14.3, 14.4_
  - [ ]* 12.3 Write UI tests for stats, timer, controls, and sections
    - Snapshot/Compose-тесты панели характеристик, таймера, элементов управления, сворачиваемости секций и индикатора дебаффа
    - _Requirements: 5.7, 14.1, 14.2, 14.3, 14.5, 14.6_

- [ ] 13. Platform integration and smoke tests
  - [ ]* 13.1 Write integration tests for foreground service and notifications
    - Постоянное уведомление с фазой/временем (R11.3); уведомления при смене фаз и завершении серии (R12.1–12.3)
    - _Requirements: 11.3, 12.1, 12.2, 12.3_
  - [ ]* 13.2 Write integration tests for Lock Task focus mode lifecycle
    - Lock Task активен в течение Фазы_Работы и снимается при завершении/паузе/провале (R13.2, R13.3, R13.8, R13.9); напоминание о возврате (R13.11)
    - _Requirements: 13.2, 13.3, 13.8, 13.9, 13.11_
  - [ ]* 13.3 Write smoke tests for permissions and screen behavior
    - Запрос разрешения на уведомления при первой отправке (R12.4); удержание экрана включённым (R13.5); обработка недоступности закрепления (R13.7)
    - _Requirements: 12.4, 13.5, 13.7_

- [x] 14. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Реализация ведётся на Kotlin + Jetpack Compose; доменная логика — чистые функции без зависимостей от Android SDK.
- Задачи, помеченные `*`, опциональны (тесты) и могут быть пропущены для ускорения MVP.
- Каждая задача ссылается на конкретные требования для трассируемости.
- Каждый property-тест реализуется одним тестом (минимум 100 итераций) и помечается комментарием `// Feature: android-study-dungeon, Property {number}: {property_text}`.
- Свойства 1–31 из раздела Correctness Properties покрыты отдельными подзадачами; платформенные эффекты покрыты integration/smoke-тестами.
- Чекпоинты обеспечивают инкрементальную валидацию.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2"] },
    { "id": 2, "tasks": ["1.3", "2.1", "5.1", "7.1", "9.1"] },
    { "id": 3, "tasks": ["2.5", "5.7", "7.4", "2.2", "2.3", "2.4", "5.2", "5.3", "5.4", "5.5", "5.6", "9.2", "9.3", "9.4", "7.2", "7.3"] },
    { "id": 4, "tasks": ["3.1", "6.1", "7.5", "2.6", "2.7", "2.8", "2.9", "2.10", "2.11", "2.12", "5.8", "5.9", "5.10", "5.11", "5.12", "5.13"] },
    { "id": 5, "tasks": ["3.2", "3.3", "3.4", "6.2", "6.3", "6.4", "6.5"] },
    { "id": 6, "tasks": ["10.1", "10.3"] },
    { "id": 7, "tasks": ["10.2"] },
    { "id": 8, "tasks": ["11.1"] },
    { "id": 9, "tasks": ["11.2", "12.1", "12.2"] },
    { "id": 10, "tasks": ["12.3", "13.1", "13.2", "13.3"] }
  ]
}
```
