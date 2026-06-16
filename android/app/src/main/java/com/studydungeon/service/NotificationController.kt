package com.studydungeon.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.studydungeon.R
import com.studydungeon.domain.Phase
import com.studydungeon.domain.PomodoroEngine
import com.studydungeon.domain.TimerState

/**
 * Платформенная подсистема Android-уведомлений (Система_Уведомлений).
 *
 * Управляет:
 * - каналами уведомлений (канал постоянного таймера + канал событий);
 * - постоянным уведомлением Таймера с текущей фазой и оставшимся временем,
 *   используемым [TimerForegroundService] для `startForeground` (R11.3);
 * - событийными уведомлениями: переход к Фазе_Отдыха (R12.1), переход к
 *   Фазе_Работы (R12.2), завершение Серии с суммарной наградой (R12.3),
 *   напоминание о возврате к учёбе во время Режима_Фокуса (R13.11).
 *
 * Разрешение `POST_NOTIFICATIONS` (Android 13+/Tiramisu) запрашивается при
 * первой попытке отправки уведомления: если разрешение отсутствует, контроллер
 * один раз инициирует запрос через зарегистрированный [permissionRequester] и
 * пропускает отправку. При отказе пользователя Приложение продолжает работу без
 * уведомлений — отправка молча пропускается, исключения не пробрасываются
 * (R12.4).
 *
 * Контроллер не зависит от конкретного `Activity`: запрос разрешения
 * делегируется через лямбду [permissionRequester], которую регистрирует
 * UI-слой (например, `MainActivity` через `ActivityResultLauncher`). Контентный
 * intent уведомлений открывает приложение через launcher-intent пакета, что
 * исключает прямую зависимость service-слоя от ui-слоя.
 *
 * Requirements: 11.3, 12.1, 12.2, 12.3, 12.4, 13.11
 */
class NotificationController(
    private val context: Context
) {
    /**
     * Колбэк запроса разрешения `POST_NOTIFICATIONS`, регистрируемый UI-слоем.
     *
     * Вызывается не более одного раза (см. [permissionRequested]) при первой
     * попытке отправки, когда разрешение ещё не предоставлено.
     */
    private var permissionRequester: (() -> Unit)? = null

    /** Признак того, что запрос разрешения уже был инициирован (чтобы не дёргать пользователя повторно). */
    private var permissionRequested: Boolean = false

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    /**
     * Регистрирует колбэк, инициирующий запрос разрешения `POST_NOTIFICATIONS`.
     *
     * UI-слой передаёт сюда лямбду, которая запускает системный диалог запроса
     * разрешения (через `ActivityResultLauncher`). Контроллер вызовет её при
     * первой отправке уведомления без разрешения (R12.4).
     *
     * @param requester лямбда, запускающая запрос разрешения, либо null для сброса.
     */
    fun setPermissionRequester(requester: (() -> Unit)?) {
        permissionRequester = requester
    }

    /**
     * Сбрасывает флаг «запрос инициирован», позволяя повторно запросить
     * разрешение (например, после явного действия пользователя). Полезно, если
     * пользователь ранее отклонил запрос, а затем включил уведомления.
     */
    fun resetPermissionRequest() {
        permissionRequested = false
    }

    /**
     * Создаёт каналы уведомлений (требование Android 8.0+/Oreo).
     *
     * Канал [CHANNEL_TIMER] — для постоянного, малозаметного уведомления
     * Таймера (низкая важность, без звука). Канал [CHANNEL_EVENTS] — для
     * событийных уведомлений смены фаз/завершения серии/напоминаний (важность
     * по умолчанию). На API ниже 26 каналы не требуются и метод не выполняет
     * действий.
     */
    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val timerChannel = NotificationChannel(
            CHANNEL_TIMER,
            context.getString(R.string.notif_channel_timer_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_timer_desc)
            setShowBadge(false)
        }

        val eventsChannel = NotificationChannel(
            CHANNEL_EVENTS,
            context.getString(R.string.notif_channel_events_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_events_desc)
        }

        notificationManager.createNotificationChannel(timerChannel)
        notificationManager.createNotificationChannel(eventsChannel)
    }

    /**
     * Строит постоянное уведомление Таймера с текущей фазой и оставшимся
     * временем для использования в [TimerForegroundService.startForeground]
     * (R11.3).
     *
     * Уведомление помечается как `ongoing` (его нельзя смахнуть) и использует
     * низкоприоритетный канал [CHANNEL_TIMER]. Оставшееся время форматируется
     * через [PomodoroEngine.formatTime] в формат "ММ:СС".
     *
     * @param state текущее состояние Таймера.
     * @return готовое [android.app.Notification] для переднего плана сервиса.
     *
     * Requirements: 11.3
     */
    fun buildTimerNotification(state: TimerState): android.app.Notification {
        val phaseLabel = phaseLabel(state.phase)
        val content = context.getString(
            R.string.notif_timer_content,
            phaseLabel,
            PomodoroEngine.formatTime(state.secondsLeft)
        )
        return NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(contentIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    /**
     * Обновляет постоянное уведомление Таймера (фаза + оставшееся время).
     *
     * Используется тикером фонового сервиса раз в секунду для актуализации
     * отображаемого времени. Отправка подчиняется проверке разрешения
     * `POST_NOTIFICATIONS` (R12.4).
     *
     * @param state текущее состояние Таймера.
     *
     * Requirements: 11.3
     */
    fun updateTimerNotification(state: TimerState) {
        postIfPermitted(TIMER_NOTIFICATION_ID, buildTimerNotification(state))
    }

    /**
     * Отправляет уведомление о завершении Помидорки и переходе к Фазе_Отдыха.
     *
     * Requirements: 12.1
     */
    fun notifyBreakStarted() {
        postEvent(
            EVENT_PHASE_NOTIFICATION_ID,
            context.getString(R.string.notif_break_title),
            context.getString(R.string.notif_break_text)
        )
    }

    /**
     * Отправляет уведомление о начале новой Фазы_Работы (после отдыха).
     *
     * Requirements: 12.2
     */
    fun notifyWorkStarted() {
        postEvent(
            EVENT_PHASE_NOTIFICATION_ID,
            context.getString(R.string.notif_work_title),
            context.getString(R.string.notif_work_text)
        )
    }

    /**
     * Отправляет уведомление о завершении Серии с суммарной наградой.
     *
     * @param totalXp суммарный начисленный за Серию Опыт.
     * @param totalGold суммарное начисленное за Серию Золото.
     *
     * Requirements: 12.3
     */
    fun notifySeriesCompleted(totalXp: Int, totalGold: Int) {
        postEvent(
            EVENT_SERIES_NOTIFICATION_ID,
            context.getString(R.string.notif_series_title),
            context.getString(R.string.notif_series_text, totalXp, totalGold)
        )
    }

    /**
     * Отправляет напоминание о возврате к учёбе во время активного Режима_Фокуса
     * с указанием оставшегося времени и текущего Здоровья Героя.
     *
     * Отправляется независимо от значения Здоровья и оставшегося времени
     * (R13.11).
     *
     * @param secondsLeft оставшееся время Фазы_Работы в секундах.
     * @param currentHp текущее Здоровье Героя.
     *
     * Requirements: 13.11
     */
    fun notifyReturnReminder(secondsLeft: Int, currentHp: Int) {
        postEvent(
            EVENT_RETURN_NOTIFICATION_ID,
            context.getString(R.string.notif_return_title),
            context.getString(
                R.string.notif_return_text,
                PomodoroEngine.formatTime(secondsLeft),
                currentHp
            )
        )
    }

    /** Снимает постоянное уведомление Таймера (например, при остановке сессии). */
    fun cancelTimerNotification() {
        notificationManager.cancel(TIMER_NOTIFICATION_ID)
    }

    /**
     * Строит и публикует событийное уведомление на канале [CHANNEL_EVENTS].
     *
     * @param id идентификатор уведомления.
     * @param title заголовок.
     * @param text текст.
     */
    private fun postEvent(id: Int, title: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        postIfPermitted(id, notification)
    }

    /**
     * Публикует уведомление с проверкой разрешения `POST_NOTIFICATIONS`.
     *
     * Если разрешение отсутствует (Android 13+), инициирует запрос один раз
     * через [permissionRequester] и пропускает отправку. При отказе/отсутствии
     * разрешения Приложение продолжает работать без уведомлений (R12.4).
     *
     * @param id идентификатор уведомления.
     * @param notification готовое уведомление.
     */
    private fun postIfPermitted(id: Int, notification: android.app.Notification) {
        if (!hasPermission()) {
            requestPermissionOnce()
            return
        }
        // Дополнительная защита: даже при наличии разрешения системный вызов
        // может быть отклонён — не «роняем» Приложение из-за уведомления.
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Уведомления недоступны — продолжаем без них (R12.4).
        }
    }

    /**
     * Проверяет, предоставлено ли разрешение на отправку уведомлений.
     *
     * На Android ниже 13 (Tiramisu) разрешение времени выполнения не требуется,
     * поэтому метод возвращает `true`, если уведомления включены пользователем.
     */
    private fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Инициирует запрос разрешения не более одного раза (R12.4). */
    private fun requestPermissionOnce() {
        if (permissionRequested) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        permissionRequested = true
        permissionRequester?.invoke()
    }

    /** Локализованная метка текущей фазы для постоянного уведомления. */
    private fun phaseLabel(phase: Phase): String = when (phase) {
        Phase.WORK -> context.getString(R.string.notif_timer_phase_work)
        Phase.BREAK -> context.getString(R.string.notif_timer_phase_break)
    }

    /**
     * Контентный intent уведомлений: открывает Приложение через launcher-intent
     * пакета (без прямой зависимости от ui-слоя).
     */
    private fun contentIntent(): PendingIntent? {
        val launch = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
            ?: return null
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, 0, launch, flags)
    }

    companion object {
        /** Идентификатор канала постоянного уведомления Таймера. */
        const val CHANNEL_TIMER: String = "study_dungeon_timer"

        /** Идентификатор канала событийных уведомлений. */
        const val CHANNEL_EVENTS: String = "study_dungeon_events"

        /**
         * Идентификатор постоянного уведомления Таймера.
         *
         * Используется [TimerForegroundService] для `startForeground`.
         */
        const val TIMER_NOTIFICATION_ID: Int = 1001

        /** Идентификатор уведомления о смене фазы (работа/отдых). */
        const val EVENT_PHASE_NOTIFICATION_ID: Int = 1002

        /** Идентификатор уведомления о завершении Серии. */
        const val EVENT_SERIES_NOTIFICATION_ID: Int = 1003

        /** Идентификатор уведомления-напоминания о возврате к учёбе. */
        const val EVENT_RETURN_NOTIFICATION_ID: Int = 1004
    }
}
