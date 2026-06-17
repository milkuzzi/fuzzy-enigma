package com.studydungeon.service

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.studydungeon.data.SettingsStore

/**
 * Звуковая и тактильная обратная связь на смену фаз Таймера и завершение Серии.
 *
 * Подчиняется пользовательским переключателям звука/вибрации из [SettingsStore]
 * (читаются при каждом событии, чтобы изменения настроек применялись
 * немедленно). Любые ошибки воспроизведения молча игнорируются — это
 * вспомогательный эффект, он не должен «ронять» сервис.
 */
class SessionFeedback(context: Context) {

    private val appContext = context.applicationContext
    private val settings = SettingsStore(appContext)

    /** Удерживаемая ссылка на текущий проигрываемый рингтон, чтобы он не был
     *  собран GC до окончания воспроизведения. */
    private var activeRingtone: Ringtone? = null

    /** Обратная связь на смену фазы (работа↔отдых): короткая вибрация + звук. */
    fun onPhaseChange() = play(longArrayOf(0, 200))

    /** Обратная связь на завершение Серии: более выраженный паттерн. */
    fun onSeriesCompleted() = play(longArrayOf(0, 150, 120, 150, 120, 300))

    private fun play(pattern: LongArray) {
        if (settings.vibrationEnabledBlocking()) vibrate(pattern)
        if (settings.soundEnabledBlocking()) playSound()
    }

    private fun vibrate(pattern: LongArray) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }.onFailure { Log.w(TAG, "Не удалось воспроизвести вибрацию", it) }
    }

    private fun playSound() {
        runCatching {
            // Берём первый доступный системный звук: уведомление → будильник →
            // звонок. На части устройств звук уведомления может быть «Нет».
            val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: return

            val ringtone = RingtoneManager.getRingtone(appContext, uri) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            // Останавливаем предыдущий и удерживаем ссылку на текущий рингтон.
            activeRingtone?.runCatching { stop() }
            activeRingtone = ringtone
            ringtone.play()
        }.onFailure { Log.w(TAG, "Не удалось воспроизвести звук", it) }
    }

    private companion object {
        const val TAG = "SessionFeedback"
    }
}
