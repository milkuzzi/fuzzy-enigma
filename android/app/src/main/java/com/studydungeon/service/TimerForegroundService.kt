package com.studydungeon.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.studydungeon.domain.Phase
import com.studydungeon.domain.PomodoroEngine
import com.studydungeon.domain.RunState
import com.studydungeon.domain.SessionEngine
import com.studydungeon.domain.TickResult
import com.studydungeon.domain.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Фоновая_Служба (Android foreground service), владеющая «тикером» Таймера.
 *
 * Служба обеспечивает достоверный обратный отсчёт Pomodoro-таймера при свёрнутом
 * Приложении и выключенном экране устройства (R11.1): процесс остаётся живым как
 * foreground service, а раз в секунду корутина-тикер вызывает
 * [PomodoroEngine.tick]. Поскольку источником истины отсчёта является
 * [TimerState.phaseEndEpochMs] (wall-clock), пропущенные системой тики (Doze,
 * выключенный экран) не искажают оставшееся время — при возврате Приложения на
 * передний план ViewModel наблюдает согласованное значение через [timerState]
 * (R11.2).
 *
 * Пока Таймер выполняется, служба отображает постоянное уведомление с текущей
 * фазой и оставшимся временем через [NotificationController] (R11.3), обновляя
 * его на каждом тике. На смене фаз и завершении Серии отправляются событийные
 * уведомления (R12.1, R12.2, R12.3).
 *
 * Состояние публикуется через статический [timerState] ([StateFlow]), который
 * наблюдает `StudyDungeonViewModel`. Это разрывает прямую зависимость
 * service → ui: служба ничего не знает о ViewModel, лишь эмитит состояние.
 *
 * Управление службой выполняется через статические помощники [start], [pause] и
 * [stop], формирующие соответствующие [Intent].
 *
 * Requirements: 11.1, 11.2, 11.3, 12.1, 12.2, 12.3
 */
class TimerForegroundService : Service() {

    private lateinit var notificationController: NotificationController
    private lateinit var feedback: SessionFeedback

    /** Скоуп службы для тикера; завершается в [onDestroy]. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Текущая задача тикера; пересоздаётся при старте, отменяется при паузе/останове. */
    private var tickerJob: Job? = null

    /**
     * Суммарная награда, накопленная за Серию (для уведомления о завершении
     * Серии — R12.3). Каждое успешное завершение Фазы_Работы прибавляет
     * [SessionEngine.WORK_SUCCESS_XP]/[SessionEngine.WORK_SUCCESS_GOLD].
     */
    private var accumulatedXp: Int = 0
    private var accumulatedGold: Int = 0

    override fun onCreate() {
        super.onCreate()
        notificationController = NotificationController(applicationContext)
        feedback = SessionFeedback(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_STOP -> handleStop()
            else -> {
                // Перезапуск службы системой без сохранённого намерения: без
                // данных для восстановления безопасно остановиться (STOPPED)
                // без штрафа (см. design: Error Handling).
                stopService()
            }
        }
        // Перезапланировать с последним намерением, чтобы восстановить отсчёт
        // из phaseEndEpochMs после возможного убийства процесса системой.
        return START_REDELIVER_INTENT
    }

    /**
     * Запускает (или восстанавливает) выполнение Таймера и переводит службу в
     * foreground с постоянным уведомлением.
     *
     * Если в намерении передан [EXTRA_PHASE_END_MS] (восстановление), состояние
     * воссоздаётся как [RunState.RUNNING] с этим моментом окончания фазы —
     * тикер сразу пересчитает оставшееся время по wall-clock (R11.2). Иначе
     * отсчёт стартует от текущего момента: `phaseEndEpochMs = now + secondsLeft*1000`.
     */
    private fun handleStart(intent: Intent) {
        val workSec = intent.getIntExtra(EXTRA_WORK_SEC, TimerState.DEFAULT_WORK_SEC)
        val breakSec = intent.getIntExtra(EXTRA_BREAK_SEC, TimerState.DEFAULT_BREAK_SEC)
        val total = intent.getIntExtra(EXTRA_TOTAL, 1)
        val completed = intent.getIntExtra(EXTRA_COMPLETED, 0)
        val phaseOrdinal = intent.getIntExtra(EXTRA_PHASE, Phase.WORK.ordinal)
        val phase = Phase.entries.getOrElse(phaseOrdinal) { Phase.WORK }
        val phaseEndMs = if (intent.hasExtra(EXTRA_PHASE_END_MS)) {
            intent.getLongExtra(EXTRA_PHASE_END_MS, 0L)
        } else {
            null
        }

        accumulatedXp = intent.getIntExtra(EXTRA_ACCUMULATED_XP, 0)
        accumulatedGold = intent.getIntExtra(EXTRA_ACCUMULATED_GOLD, 0)

        val now = System.currentTimeMillis()
        val secondsLeftSeed = if (phase == Phase.BREAK) breakSec else workSec
        val baseState = TimerState(
            phase = phase,
            runState = RunState.RUNNING,
            workDurationSec = workSec,
            breakDurationSec = breakSec,
            secondsLeft = secondsLeftSeed,
            phaseEndEpochMs = phaseEndMs,
            completedPomodoros = completed,
            totalPomodoros = total
        )
        // При восстановлении phaseEndEpochMs уже задан; при свежем старте
        // фиксируем момент окончания фазы от текущего времени (R7.1).
        val running = if (phaseEndMs != null) baseState else PomodoroEngine.start(baseState, now)

        publish(running)

        val notification = notificationController.buildTimerNotification(running)
        startForegroundCompat(notification)

        startTicker()
    }

    /**
     * Приостанавливает тикер и сохраняет оставшееся время (R7.5). Служба
     * остаётся в foreground с обновлённым уведомлением, чтобы пользователь видел
     * состояние паузы.
     */
    private fun handlePause() {
        val current = _timerState.value ?: return
        tickerJob?.cancel()
        tickerJob = null
        val paused = PomodoroEngine.pause(current, System.currentTimeMillis())
        publish(paused)
        notificationController.updateTimerNotification(paused)
    }

    /**
     * Возобновляет ранее приостановленный Таймер (кнопка «Продолжить» в
     * уведомлении). Сохранённое при паузе оставшееся время снова
     * превращается в phaseEndEpochMs от текущего момента.
     */
    private fun handleResume() {
        val current = _timerState.value ?: return
        if (current.runState == RunState.RUNNING) return
        val running = PomodoroEngine.start(current, System.currentTimeMillis())
        publish(running)
        val notification = notificationController.buildTimerNotification(running)
        startForegroundCompat(notification)
        startTicker()
    }

    /** Останавливает Таймер, снимает уведомление и завершает службу. */
    private fun handleStop() {
        val current = _timerState.value
        if (current != null) {
            publish(PomodoroEngine.fail(current))
        }
        stopService()
    }

    /**
     * Запускает корутину-тикер, которая раз в секунду продвигает Таймер по
     * wall-clock через [PomodoroEngine.tick], обновляет постоянное уведомление и
     * публикует новое [TimerState].
     */
    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                val current = _timerState.value
                if (current == null || current.runState != RunState.RUNNING) break

                when (val result = PomodoroEngine.tick(current, System.currentTimeMillis())) {
                    is TickResult.Continue -> {
                        publish(result.state)
                        notificationController.updateTimerNotification(result.state)
                    }
                    is TickResult.WorkCompleted -> {
                        accumulateWorkReward()
                        publish(result.state)
                        feedback.onPhaseChange()
                        notificationController.notifyBreakStarted()
                        notificationController.updateTimerNotification(result.state)
                    }
                    is TickResult.BreakCompleted -> {
                        publish(result.state)
                        feedback.onPhaseChange()
                        notificationController.notifyWorkStarted()
                        notificationController.updateTimerNotification(result.state)
                    }
                    is TickResult.SeriesCompleted -> {
                        accumulateWorkReward()
                        publish(result.state)
                        feedback.onSeriesCompleted()
                        notificationController.notifySeriesCompleted(accumulatedXp, accumulatedGold)
                        // Серия завершена — отсчёт прекращаем и сворачиваем службу.
                        stopService()
                        return@launch
                    }
                }

                delay(TICK_INTERVAL_MS)
            }
        }
    }

    /** Прибавляет награду за успешную Помидорку к суммарной награде Серии (R12.3). */
    private fun accumulateWorkReward() {
        accumulatedXp += SessionEngine.WORK_SUCCESS_XP
        accumulatedGold += SessionEngine.WORK_SUCCESS_GOLD
    }

    /** Публикует новое состояние Таймера наблюдателям (ViewModel) через [timerState]. */
    private fun publish(state: TimerState) {
        _timerState.value = state
    }

    /** Снимает постоянное уведомление, выходит из foreground и завершает службу. */
    private fun stopService() {
        tickerJob?.cancel()
        tickerJob = null
        notificationController.cancelTimerNotification()
        stopForegroundCompat()
        stopSelf()
    }

    /**
     * Вызывает `startForeground` с корректным типом службы на API 29+/34+.
     *
     * Тип [ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE] соответствует
     * объявлению `foregroundServiceType="specialUse"` в манифесте.
     */
    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationController.TIMER_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationController.TIMER_NOTIFICATION_ID, notification)
        }
    }

    /** Выходит из foreground, удаляя уведомление, с учётом версии API. */
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        serviceScope.cancel()
        _timerState.value = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TimerForegroundService"

        /** Интервал тикера обновления UI/уведомления (источник истины — wall-clock). */
        private const val TICK_INTERVAL_MS = 1000L

        /** Действие: запустить/восстановить выполнение Таймера. */
        const val ACTION_START = "com.studydungeon.service.action.START"

        /** Действие: приостановить Таймер. */
        const val ACTION_PAUSE = "com.studydungeon.service.action.PAUSE"

        /** Действие: возобновить приостановленный Таймер. */
        const val ACTION_RESUME = "com.studydungeon.service.action.RESUME"

        /** Действие: остановить Таймер и завершить службу. */
        const val ACTION_STOP = "com.studydungeon.service.action.STOP"

        const val EXTRA_WORK_SEC = "extra_work_sec"
        const val EXTRA_BREAK_SEC = "extra_break_sec"
        const val EXTRA_TOTAL = "extra_total"
        const val EXTRA_COMPLETED = "extra_completed"
        const val EXTRA_PHASE = "extra_phase"
        const val EXTRA_PHASE_END_MS = "extra_phase_end_ms"
        const val EXTRA_ACCUMULATED_XP = "extra_accumulated_xp"
        const val EXTRA_ACCUMULATED_GOLD = "extra_accumulated_gold"

        private val _timerState = MutableStateFlow<TimerState?>(null)

        /**
         * Поток состояния Таймера, публикуемый службой и наблюдаемый ViewModel
         * (R11.2). `null` означает, что служба не активна.
         */
        val timerState: StateFlow<TimerState?> = _timerState.asStateFlow()

        /**
         * Запускает службу и выполнение Таймера со свежим стартом отсчёта.
         *
         * @param context контекст для запуска службы.
         * @param workSec длительность Фазы_Работы в секундах.
         * @param breakSec длительность Фазы_Отдыха в секундах.
         * @param total общее число Помидорок в Серии.
         */
        fun start(context: Context, workSec: Int, breakSec: Int, total: Int) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_WORK_SEC, workSec)
                putExtra(EXTRA_BREAK_SEC, breakSec)
                putExtra(EXTRA_TOTAL, total)
            }
            ContextCompatStartForegroundService(context, intent)
        }

        /**
         * Восстанавливает выполнение Таймера из ранее сохранённого
         * [TimerState], используя его [TimerState.phaseEndEpochMs] как источник
         * истины отсчёта (R11.2).
         */
        fun restore(context: Context, state: TimerState, accumulatedXp: Int, accumulatedGold: Int) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_WORK_SEC, state.workDurationSec)
                putExtra(EXTRA_BREAK_SEC, state.breakDurationSec)
                putExtra(EXTRA_TOTAL, state.totalPomodoros)
                putExtra(EXTRA_COMPLETED, state.completedPomodoros)
                putExtra(EXTRA_PHASE, state.phase.ordinal)
                state.phaseEndEpochMs?.let { putExtra(EXTRA_PHASE_END_MS, it) }
                putExtra(EXTRA_ACCUMULATED_XP, accumulatedXp)
                putExtra(EXTRA_ACCUMULATED_GOLD, accumulatedGold)
            }
            ContextCompatStartForegroundService(context, intent)
        }

        /** Приостанавливает выполнение Таймера. */
        fun pause(context: Context) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_PAUSE
            }
            ContextCompatStartForegroundService(context, intent)
        }

        /** Возобновляет приостановленный Таймер. */
        fun resume(context: Context) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_RESUME
            }
            ContextCompatStartForegroundService(context, intent)
        }

        /** Останавливает Таймер и завершает службу. */
        fun stop(context: Context) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            ContextCompatStartForegroundService(context, intent)
        }

        /**
         * Запускает службу как foreground-совместимо: на Android 8.0+ требуется
         * [Context.startForegroundService], иначе [Context.startService].
         */
        private fun ContextCompatStartForegroundService(context: Context, intent: Intent) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: IllegalStateException) {
                // Например, попытка старта из фона на новых API; логируем и
                // продолжаем — ViewModel не «падает» из-за платформенного лимита.
                Log.w(TAG, "Failed to start TimerForegroundService: ${e.message}")
            }
        }
    }
}
