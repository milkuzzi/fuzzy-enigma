package com.studydungeon.lock

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * Проверки и интенты системных разрешений, необходимых для блокировки телефона
 * без Device Owner (как в Focus To-do):
 *
 * - «Поверх других приложений» ([canDrawOverlays]) — для полноэкранного оверлея
 *   полной блокировки и экрана «Доступ запрещён».
 * - «Данные об использовании» ([hasUsageAccess]) — для определения текущего
 *   приложения на переднем плане (полная блокировка и блокировка отдельных
 *   приложений).
 * - «Доступ к уведомлениям» ([isNotificationAccessGranted]) — для подавления
 *   уведомлений во время полной блокировки.
 *
 * Эти разрешения нельзя получить обычным runtime-запросом — пользователя нужно
 * отправить в системные настройки соответствующими интентами.
 */
object LockPermissions {

    /** Разрешение «Поверх других приложений» (SYSTEM_ALERT_WINDOW). */
    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    /** Разрешение «Доступ к данным об использовании» (PACKAGE_USAGE_STATS). */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Разрешение «Доступ к уведомлениям» (NotificationListenerService включён). */
    fun isNotificationAccessGranted(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabled.split(":").any { it.contains(context.packageName) }
    }

    /** Интент в системный экран выдачи разрешения «Поверх других приложений». */
    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Интент в системный экран «Доступ к данным об использовании». */
    fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Интент в системный экран «Доступ к уведомлениям». */
    fun notificationAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Все ли разрешения, необходимые для заданного [mode], выданы.
     * Для [LockMode.NONE] разрешения не требуются.
     */
    fun hasPermissionsFor(context: Context, mode: LockMode): Boolean = when (mode) {
        LockMode.NONE -> true
        LockMode.FULL -> canDrawOverlays(context) &&
            hasUsageAccess(context) &&
            isNotificationAccessGranted(context)
        LockMode.PER_APP -> canDrawOverlays(context) && hasUsageAccess(context)
    }
}
