package com.studydungeon.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Подавляет уведомления во время полной блокировки телефона (Режим_Фокуса с
 * [com.studydungeon.lock.LockMode.FULL]).
 *
 * Пока [fullLockActive] == true, любое появившееся уведомление немедленно
 * снимается ([cancelNotification]), а собственное постоянное уведомление
 * таймера ([CHANNEL_TIMER]) не трогается — иначе foreground-служба упадёт.
 * Это даёт поведение «уведомления не приходят», как в Focus To-do, на основе
 * разрешения «Доступ к уведомлениям».
 *
 * Служба объявляется в манифесте с `BIND_NOTIFICATION_LISTENER_SERVICE`;
 * фактический доступ пользователь выдаёт в системных настройках.
 */
class StudyNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || !fullLockActive.get()) return
        // Не снимаем собственные уведомления приложения (постоянное уведомление
        // таймера обязано жить, пока активна foreground-служба).
        if (sbn.packageName == packageName) return
        runCatching { cancelNotification(sbn.key) }
    }

    companion object {
        /**
         * Флаг полной блокировки: устанавливается [LockEnforcementService] на
         * время полной блокировки. Listener работает в отдельном процессе только
         * как биндинг системы, но в пределах одного процесса приложения этот
         * статический флаг — простой и достаточный канал управления.
         */
        val fullLockActive = AtomicBoolean(false)
    }
}
