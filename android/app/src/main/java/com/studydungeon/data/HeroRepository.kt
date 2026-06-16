package com.studydungeon.data

import android.content.Context
import android.util.Log
import com.studydungeon.domain.Hero
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Хранилище состояния Героя (локальная persistence).
 *
 * Паритет с `save_manager.py` оригинала: одно состояние Героя сохраняется
 * в JSON-файл `character_save.json`.
 *
 * Requirements:
 * - 10.1 — [save] сохраняет состояние Героя в локальное постоянное хранилище.
 * - 10.2 — [load] загружает сохранённое состояние при его наличии.
 * - 10.3 — при отсутствии/повреждении сохранения [load] возвращает Героя по умолчанию.
 */
interface HeroRepository {
    /**
     * Загружает состояние Героя из хранилища.
     *
     * При отсутствии файла либо его повреждении/непарсибельности возвращает
     * [Hero] со значениями по умолчанию (R10.2, R10.3).
     */
    suspend fun load(): Hero

    /**
     * Сохраняет состояние Героя в хранилище (R10.1).
     *
     * Запись атомарна; ошибки записи логируются, но не пробрасываются в UI.
     */
    suspend fun save(hero: Hero)
}

/**
 * Файловая реализация [HeroRepository]: пишет JSON во внутреннее хранилище
 * приложения в файл `character_save.json` (паритет имени с оригиналом).
 *
 * - Запись атомарна: данные пишутся во временный файл, который затем
 *   переименовывается в целевой. Это исключает частично записанное состояние
 *   при прерывании записи.
 * - При отсутствии или повреждении файла [load] возвращает [Hero] по умолчанию
 *   (R10.3) за счёт отказоустойчивости [HeroSerializer.fromJson] и перехвата
 *   ошибок ввода-вывода.
 * - Файловые операции выполняются на [Dispatchers.IO].
 *
 * Конструктор принимает каталог [dir] (а не [Context]), чтобы реализацию можно
 * было тестировать с временным каталогом. Для использования в приложении
 * предусмотрена фабрика [from], принимающая [Context].
 *
 * @param dir каталог для файла сохранения (например, `context.filesDir`).
 * @param ioDispatcher диспетчер для файловых операций (по умолчанию [Dispatchers.IO]).
 */
class FileHeroRepository(
    private val dir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : HeroRepository {

    private val saveFile: File get() = File(dir, SAVE_FILE_NAME)
    private val tempFile: File get() = File(dir, TEMP_FILE_NAME)

    override suspend fun load(): Hero = withContext(ioDispatcher) {
        val file = saveFile
        if (!file.exists()) return@withContext Hero()
        try {
            val content = file.readText(Charsets.UTF_8)
            // fromJson отказоустойчив: при повреждённом содержимом вернёт Hero().
            HeroSerializer.fromJson(content)
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось прочитать файл сохранения, возвращается Герой по умолчанию", e)
            Hero()
        }
    }

    override suspend fun save(hero: Hero) = withContext(ioDispatcher) {
        try {
            dir.mkdirs()
            val json = HeroSerializer.toJson(hero)
            val tmp = tempFile
            // Шаг 1: пишем во временный файл.
            tmp.writeText(json, Charsets.UTF_8)
            // Шаг 2: атомарно заменяем целевой файл.
            atomicReplace(tmp, saveFile)
        } catch (e: Exception) {
            // Ошибка записи не должна «ронять» приложение (см. design: Error Handling).
            Log.e(TAG, "Не удалось сохранить состояние Героя", e)
        }
        Unit
    }

    /**
     * Атомарно заменяет [target] содержимым [source].
     *
     * Предпочтительно используется `java.nio.Files.move` с `ATOMIC_MOVE`.
     * При невозможности атомарного перемещения (например, ОС/ФС не поддерживают
     * либо API-уровень ниже 26) выполняется откат к `File.renameTo`, а при его
     * неудаче — копирование с удалением временного файла.
     */
    private fun atomicReplace(source: File, target: File) {
        try {
            java.nio.file.Files.move(
                source.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
            return
        } catch (e: Throwable) {
            // ATOMIC_MOVE недоступен/не поддержан — пробуем неатомарные варианты.
            Log.w(TAG, "ATOMIC_MOVE недоступен, используется запасной механизм замены", e)
        }

        // Запасной вариант 1: renameTo (на многих ФС атомарен в пределах одного тома).
        if (target.exists()) target.delete()
        if (source.renameTo(target)) return

        // Запасной вариант 2: копирование + удаление временного файла.
        source.copyTo(target, overwrite = true)
        source.delete()
    }

    companion object {
        private const val TAG = "FileHeroRepository"

        /** Имя файла сохранения — паритет с Python-оригиналом. */
        const val SAVE_FILE_NAME = "character_save.json"

        /** Имя временного файла для атомарной записи. */
        const val TEMP_FILE_NAME = "character_save.json.tmp"

        /**
         * Создаёт репозиторий, сохраняющий данные во внутреннем хранилище
         * приложения (`context.filesDir/character_save.json`).
         */
        fun from(context: Context): FileHeroRepository =
            FileHeroRepository(context.filesDir)
    }
}
