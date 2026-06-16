package com.studydungeon.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.studydungeon.R
import com.studydungeon.lock.LockMode
import com.studydungeon.lock.LockPreferences
import com.studydungeon.ui.BlockedAccessActivity
import com.studydungeon.ui.MainActivity

/**
 * Foreground-служба, реализующая блокировку телефона на время Режима_Фокуса без
 * Device Owner — на системных разрешениях, как в Focus To-do.
 *
 * Режимы (см. [LockMode]):
 * - [LockMode.FULL] — поверх всех приложений выводится полноэкранный оверлей
 *   ([WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY]), перехватывающий
 *   касания и кнопку «назад»; уведомления подавляются через
 *   [StudyNotificationListener]; если пользователь всё же уходит из приложения,
 *   служба возвращает его на передний план. Это даёт поведение «нельзя сделать
 *   ничего, кроме как завершить Фазу_Работы».
 * - [LockMode.PER_APP] — служба отслеживает приложение на переднем плане через
 *   [UsageStatsManager]; при запуске заблокированного приложения показывает
 *   [BlockedAccessActivity] («Доступ запрещён»).
 *
 * Служба намеренно отделена от [TimerForegroundService]: таймер должен работать
 * и без блокировки, а блокировка — это отдельный платформенный эффект.
 */
class LockEnforcementService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var prefs: LockPreferences

    private var overlayView: View? = null
    private var overlayTimeView: TextView? = null
    private var mode: LockMode = LockMode.NONE
    private var phaseEndMs: Long = 0L

    /** Пакет, для которого экран блокировки уже показан (чтобы не дёргать повторно). */
    private var lastBlockedPackage: String? = null
    private var lastRefocusMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        prefs = LockPreferences(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                mode = LockMode.fromName(intent.getStringExtra(EXTRA_MODE))
                phaseEndMs = intent.getLongExtra(EXTRA_END_MS, 0L)
                startLock()
            }
            else -> stopLock()
        }
        return START_NOT_STICKY
    }

    private fun startLock() {
        startForeground(LOCK_NOTIFICATION_ID, buildNotification())
        if (mode == LockMode.FULL) {
            StudyNotificationListener.fullLockActive.set(true)
            showOverlay()
        }
        handler.removeCallbacks(watcher)
        handler.post(watcher)
    }

    private fun stopLock() {
        handler.removeCallbacks(watcher)
        handler.removeCallbacks(timeUpdater)
        StudyNotificationListener.fullLockActive.set(false)
        removeOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // region Overlay (полная блокировка)

    private fun showOverlay() {
        if (overlayView != null) return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(STONE_BG)
            setPadding(64, 64, 64, 64)
            isFocusableInTouchMode = true
            isClickable = true
        }

        val title = TextView(this).apply {
            text = "Подземелье заперто"
            setTextColor(GOLD)
            textSize = 28f
            gravity = Gravity.CENTER
        }
        val time = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 56f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        val hint = TextView(this).apply {
            text = "Телефон заблокирован до конца фазы работы.\nЗаверши фазу в приложении, чтобы выйти."
            setTextColor(PARCHMENT_MUTED)
            textSize = 15f
            gravity = Gravity.CENTER
        }
        root.addView(title)
        root.addView(time)
        root.addView(hint)

        // Перехватываем «назад», чтобы из-под оверлея нельзя было уйти.
        root.setOnKeyListener { _, keyCode, _ ->
            keyCode == KeyEvent.KEYCODE_BACK
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        )

        runCatching {
            windowManager.addView(root, params)
            overlayView = root
            overlayTimeView = time
            root.requestFocus()
            handler.post(timeUpdater)
        }
    }

    private fun removeOverlay() {
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        overlayTimeView = null
    }

    /** Обновляет оставшееся время на оверлее раз в секунду. */
    private val timeUpdater = object : Runnable {
        override fun run() {
            val view = overlayTimeView ?: return
            val remaining = if (phaseEndMs > 0) {
                maxOf(0L, (phaseEndMs - System.currentTimeMillis()) / 1000L)
            } else {
                0L
            }
            val m = remaining / 60
            val s = remaining % 60
            view.text = "%02d:%02d".format(m, s)
            handler.postDelayed(this, 1000L)
        }
    }

    // endregion

    // region Watcher (слежение за активным приложением)

    private val watcher = object : Runnable {
        override fun run() {
            val fg = foregroundPackage()
            when (mode) {
                LockMode.FULL -> enforceFull(fg)
                LockMode.PER_APP -> enforcePerApp(fg)
                LockMode.NONE -> {}
            }
            handler.postDelayed(this, WATCH_INTERVAL_MS)
        }
    }

    /** Полная блокировка: если пользователь ушёл из приложения — возвращаем его. */
    private fun enforceFull(fg: String?) {
        if (fg == null || fg == packageName) return
        val now = System.currentTimeMillis()
        if (now - lastRefocusMs < REFOCUS_COOLDOWN_MS) return
        lastRefocusMs = now
        bringAppToFront()
    }

    /** Блокировка отдельных приложений: показываем «Доступ запрещён». */
    private fun enforcePerApp(fg: String?) {
        if (fg == null || fg == packageName) {
            lastBlockedPackage = null
            return
        }
        if (fg == BLOCK_ACTIVITY_PACKAGE_MARKER) return

        val blocked = prefs.blockedPackages
        if (fg in blocked) {
            // Не показываем экран повторно, пока пользователь не сменил приложение.
            if (fg == lastBlockedPackage) return
            lastBlockedPackage = fg
            launchBlockScreen(fg)
        } else {
            lastBlockedPackage = null
        }
    }

    private fun bringAppToFront() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        runCatching { startActivity(intent) }
    }

    private fun launchBlockScreen(blockedPackage: String) {
        val label = runCatching {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(blockedPackage, 0)).toString()
        }.getOrNull()
        val intent = Intent(this, BlockedAccessActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra(BlockedAccessActivity.EXTRA_APP_LABEL, label)
        }
        runCatching { startActivity(intent) }
    }

    /** Текущее приложение на переднем плане по данным об использовании. */
    private fun foregroundPackage(): String? {
        val end = System.currentTimeMillis()
        val begin = end - USAGE_WINDOW_MS
        val events = usageStatsManager.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            ) {
                last = event.packageName
            }
        }
        return last
    }

    // endregion

    private fun buildNotification(): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (mode == LockMode.FULL) {
            "Полная блокировка активна"
        } else {
            "Блокировка приложений активна"
        }
        return NotificationCompat.Builder(this, CHANNEL_LOCK)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_LOCK) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_LOCK,
                        "Блокировка телефона",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Уведомление активной блокировки телефона на время фокуса."
                        setShowBadge(false)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        StudyNotificationListener.fullLockActive.set(false)
        removeOverlay()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.studydungeon.lock.action.START"
        const val ACTION_STOP = "com.studydungeon.lock.action.STOP"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_END_MS = "extra_end_ms"

        const val CHANNEL_LOCK = "study_dungeon_lock"
        const val LOCK_NOTIFICATION_ID = 1101

        private const val WATCH_INTERVAL_MS = 500L
        private const val USAGE_WINDOW_MS = 10_000L
        private const val REFOCUS_COOLDOWN_MS = 800L
        private const val BLOCK_ACTIVITY_PACKAGE_MARKER = ""

        private val STONE_BG = Color.rgb(20, 15, 9)
        private val GOLD = Color.rgb(214, 169, 72)
        private val PARCHMENT_MUTED = Color.rgb(176, 162, 138)

        /**
         * Запускает блокировку телефона в заданном [mode] с [phaseEndMs] —
         * моментом окончания Фазы_Работы для обратного отсчёта на оверлее.
         */
        fun start(context: Context, mode: LockMode, phaseEndMs: Long) {
            if (mode == LockMode.NONE) return
            val intent = Intent(context, LockEnforcementService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_END_MS, phaseEndMs)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Останавливает блокировку телефона. */
        fun stop(context: Context) {
            val intent = Intent(context, LockEnforcementService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }
}
