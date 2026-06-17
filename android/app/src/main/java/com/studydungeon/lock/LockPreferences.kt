package com.studydungeon.lock

import android.content.Context

/**
 * Хранилище пользовательских настроек блокировки телефона (выбранный
 * [LockMode] и набор заблокированных приложений для режима
 * [LockMode.PER_APP]).
 *
 * Это настройка интерфейса, а не игровая логика, поэтому хранится в простом
 * [android.content.SharedPreferences] и не затрагивает доменный слой и
 * persistence Героя.
 */
class LockPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Выбранный режим блокировки телефона. */
    var mode: LockMode
        get() = LockMode.fromName(prefs.getString(KEY_MODE, LockMode.NONE.name))
        set(value) {
            prefs.edit().putString(KEY_MODE, value.name).apply()
        }

    /** Множество package-имён приложений, заблокированных в режиме PER_APP. */
    var blockedPackages: Set<String>
        get() = prefs.getStringSet(KEY_BLOCKED, emptySet())?.toSet() ?: emptySet()
        set(value) {
            prefs.edit().putStringSet(KEY_BLOCKED, value).apply()
        }

    /** Добавляет/убирает приложение из списка заблокированных. */
    fun setBlocked(packageName: String, blocked: Boolean) {
        val current = blockedPackages.toMutableSet()
        if (blocked) current.add(packageName) else current.remove(packageName)
        blockedPackages = current
    }

    private companion object {
        const val PREFS_NAME = "study_dungeon_lock"
        const val KEY_MODE = "lock_mode"
        const val KEY_BLOCKED = "blocked_packages"
    }
}
