package com.studydungeon.service

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity

/**
 * Android-эквивалент механики «блокировки экрана» оригинала: удержание
 * пользователя внутри Приложения на время Фазы_Работы через Lock Task Mode
 * (Screen Pinning / App Pinning).
 *
 * В оригинальном `main.py` фокус удерживался средствами оконного менеджера
 * рабочего стола (`overrideredirect`, перехват `WM_DELETE_WINDOW`, topmost).
 * На Android эквивалент — `Activity.startLockTask()` (пользовательское
 * Закрепление_Экрана либо Device Owner/kiosk), дополненный флагом
 * [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON] для удержания экрана
 * включённым.
 *
 * Контроллер инкапсулирует платформенные побочные эффекты Режима_Фокуса и не
 * содержит игровой логики: вычисление Длительности_Блокировки и применение
 * штрафов провала — ответственность доменного слоя
 * ([com.studydungeon.domain.SessionEngine]) и ViewModel. Здесь решаются только
 * три задачи:
 *
 * 1. [activate] — включить Закрепление_Экрана и удержание экрана (R13.1, R13.2,
 *    R13.3, R13.5); если закрепление недоступно — запросить его включение и
 *    показать инструкцию (R13.7).
 * 2. [deactivate] — снять закрепление и вернуть экрану обычное поведение
 *    (R13.8, R13.9).
 * 3. [checkForLockTaskExit] / [onLockTaskExitDetected] — детектировать
 *    принудительный выход пользователя из Закрепления_Экрана до завершения
 *    Фазы_Работы и сообщить о нём наблюдателю для применения штрафов провала
 *    (R9.4, R13.10).
 *
 * Класс привязан к жизненному циклу [ComponentActivity]: вызывающая сторона
 * (ViewModel/Activity) обязана вызывать [checkForLockTaskExit] при возврате
 * Activity на передний план (например, из `onResume`), поскольку платформа не
 * предоставляет прямого колбэка о выходе из Lock Task.
 *
 * Requirements: 13.1, 13.2, 13.3, 13.5, 13.7, 13.8, 13.9, 13.10
 */
class FocusModeController(private val activity: ComponentActivity) {

    private val activityManager: ActivityManager =
        activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /**
     * Длительность_Блокировки текущего Режима_Фокуса в секундах, совпадающая с
     * длительностью Фазы_Работы (устанавливается в [activate]). Значение 0
     * означает, что Режим_Фокуса не активирован.
     *
     * Requirements: 13.1
     */
    var lockDurationSec: Int = 0
        private set

    /**
     * Признак того, что контроллер находится в активном Режиме_Фокуса (между
     * успешным [activate] и [deactivate]). Используется, чтобы отличать штатное
     * снятие закрепления от принудительного выхода пользователя.
     */
    private var focusActive: Boolean = false

    private var lockTaskExitCallback: (() -> Unit)? = null
    private var lockTaskUnavailableCallback: ((instruction: String) -> Unit)? = null

    /**
     * Активирует Режим_Фокуса на заданную Длительность_Блокировки.
     *
     * Выполняет:
     * - удержание экрана включённым через [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON]
     *   (R13.5);
     * - включение Закрепления_Экрана через [Activity.startLockTask], что
     *   ограничивает кнопки «домой», «недавние» и «назад» (R13.2, R13.3).
     *
     * Если Закрепление_Экрана недоступно (не включено пользователем и нет прав
     * Device Owner), приложение не должно «тихо» считать фокус активным:
     * вызывается зарегистрированный через [onLockTaskUnavailable] колбэк с
     * текстом инструкции по ручному включению Screen Pinning (R13.7).
     *
     * @param lockDurationSec Длительность_Блокировки в секундах; должна быть
     *   равна длительности Фазы_Работы текущей сессии (R13.1).
     *
     * Requirements: 13.1, 13.2, 13.3, 13.5, 13.7
     */
    fun activate(lockDurationSec: Int, pinScreen: Boolean = true) {
        this.lockDurationSec = lockDurationSec

        // Удерживаем экран включённым на время Режима_Фокуса (R13.5).
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Если включена «сильная» блокировка через оверлей/usage-access
        // ([com.studydungeon.lock.LockEnforcementService]), Screen Pinning не
        // используется: пользователь жаловался на его подтверждения, а удержание
        // обеспечивает оверлей. В этом случае не трактуем отсутствие закрепления
        // как принудительный выход (focusActive остаётся false).
        if (!pinScreen) {
            focusActive = false
            return
        }

        focusActive = true

        // Включаем Закрепление_Экрана (R13.2, R13.3). При пользовательском
        // Screen Pinning система может показать подтверждающий диалог; при
        // отсутствии прав вызов может не перевести задачу в режим блокировки.
        try {
            activity.startLockTask()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "startLockTask() failed: ${e.message}")
        }

        // Если закрепление так и не включилось — запрашиваем его у пользователя
        // и показываем инструкцию (R13.7). Фокус не считается удерживаемым
        // средствами Lock Task, пока пользователь не включит закрепление.
        if (!isLockTaskActive()) {
            lockTaskUnavailableCallback?.invoke(PINNING_INSTRUCTION)
        }
    }

    /**
     * Деактивирует Режим_Фокуса: снимает Закрепление_Экрана и возвращает экрану
     * обычное поведение.
     *
     * Вызывается при штатном завершении Фазы_Работы (R13.8) и при паузе Таймера
     * (R13.9). Сбрасывает внутренний признак активности, чтобы последующая
     * проверка [checkForLockTaskExit] не трактовала снятие закрепления как
     * принудительный выход пользователя.
     *
     * Requirements: 13.8, 13.9
     */
    fun deactivate() {
        // Сначала снимаем признак активности: штатное снятие закрепления не
        // должно интерпретироваться как принудительный выход (R13.10).
        focusActive = false
        lockDurationSec = 0

        try {
            activity.stopLockTask()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "stopLockTask() failed: ${e.message}")
        }

        // Возвращаем экрану обычное поведение — снимаем удержание экрана.
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Возвращает `true`, если задача в данный момент находится в режиме
     * блокировки (Lock Task / Screen Pinning), то есть выход из Приложения
     * ограничен системой.
     *
     * Опирается на [ActivityManager.getLockTaskModeState]: режимы
     * [ActivityManager.LOCK_TASK_MODE_LOCKED] (Device Owner/kiosk) и
     * [ActivityManager.LOCK_TASK_MODE_PINNED] (пользовательский Screen Pinning)
     * считаются активным закреплением; [ActivityManager.LOCK_TASK_MODE_NONE] —
     * нет.
     */
    fun isLockTaskActive(): Boolean =
        activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    /**
     * Регистрирует наблюдателя принудительного выхода из Закрепления_Экрана.
     *
     * Колбэк вызывается из [checkForLockTaskExit], когда обнаруживается, что
     * пользователь покинул режим блокировки во время активного Режима_Фокуса
     * (до штатной [deactivate]). Наблюдатель (ViewModel) применяет штрафы
     * провала сессии и деактивирует Режим_Фокуса (R9.4, R13.10).
     *
     * Requirements: 9.4, 13.10
     */
    fun onLockTaskExitDetected(callback: () -> Unit) {
        lockTaskExitCallback = callback
    }

    /**
     * Регистрирует наблюдателя недоступности Закрепления_Экрана.
     *
     * Колбэк вызывается из [activate], если после попытки включить закрепление
     * задача не перешла в режим блокировки. Наблюдатель показывает пользователю
     * запрос и инструкцию по ручному включению Screen Pinning (R13.7).
     *
     * Requirements: 13.7
     */
    fun onLockTaskUnavailable(callback: (instruction: String) -> Unit) {
        lockTaskUnavailableCallback = callback
    }

    /**
     * Детектирует принудительный выход пользователя из Закрепления_Экрана.
     *
     * Платформа не предоставляет прямого колбэка об выходе из Lock Task, поэтому
     * вызывающая сторона обязана вызывать этот метод при возврате Activity на
     * передний план (например, из `Activity.onResume`/`onTopResumedActivityChanged`).
     *
     * Если Режим_Фокуса считается активным ([focusActive]), но задача больше не
     * находится в режиме блокировки ([isLockTaskActive] == false), это означает
     * принудительный выход до завершения Фазы_Работы: метод штатно снимает
     * удержание экрана, сбрасывает состояние и уведомляет
     * [onLockTaskExitDetected]-наблюдателя для применения штрафов провала
     * (R9.4, R13.10).
     *
     * @return `true`, если был обнаружен и обработан принудительный выход.
     *
     * Requirements: 9.4, 13.10
     */
    fun checkForLockTaskExit(): Boolean {
        if (!focusActive) return false
        if (isLockTaskActive()) return false

        // Принудительный выход из закрепления до штатной деактивации.
        focusActive = false
        lockDurationSec = 0
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        lockTaskExitCallback?.invoke()
        return true
    }

    companion object {
        private const val TAG = "FocusModeController"

        /**
         * Инструкция по ручному включению Закрепления_Экрана (Screen Pinning),
         * показываемая пользователю, когда закрепление недоступно (R13.7).
         */
        const val PINNING_INSTRUCTION: String =
            "Чтобы Режим Фокуса удерживал вас в приложении, включите Закрепление экрана: " +
                "Настройки → Безопасность → Дополнительно → Закрепление приложений (Screen pinning). " +
                "После включения снова запустите таймер."
    }
}
