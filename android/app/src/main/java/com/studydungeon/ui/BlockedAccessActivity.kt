package com.studydungeon.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Полноэкранный экран «Доступ запрещён», который показывается при попытке
 * открыть заблокированное приложение в режиме [com.studydungeon.lock.LockMode.PER_APP].
 *
 * Поведение повторяет Focus To-do: экран занимает весь дисплей, через
 * [REDIRECT_DELAY_MS] миллисекунд пользователь возвращается в приложение-таймер
 * ([MainActivity]). Экран нельзя закрыть кнопкой «назад».
 *
 * Активность строится программно (без layout-ресурса), чтобы не плодить XML ради
 * одного экрана и сохранить оформление в стиле подземелья.
 */
class BlockedAccessActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var secondsLeft = REDIRECT_DELAY_MS / 1000
    private lateinit var countdownView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val blockedLabel = intent.getStringExtra(EXTRA_APP_LABEL)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(STONE_BG)
            setPadding(64, 64, 64, 64)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(this).apply {
            text = "Доступ запрещён"
            setTextColor(GOLD)
            textSize = 30f
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = if (blockedLabel.isNullOrBlank()) {
                "Это приложение заблокировано на время учёбы."
            } else {
                "«$blockedLabel» заблокировано на время учёбы."
            }
            setTextColor(PARCHMENT)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 48)
        }

        countdownView = TextView(this).apply {
            setTextColor(PARCHMENT_MUTED)
            textSize = 14f
            gravity = Gravity.CENTER
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(countdownView)
        setContentView(root)

        tick()
    }

    private fun tick() {
        countdownView.text = "Возврат к таймеру через $secondsLeft…"
        if (secondsLeft <= 0) {
            returnToTimer()
            return
        }
        secondsLeft -= 1
        handler.postDelayed({ tick() }, 1000L)
    }

    private fun returnToTimer() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(intent)
        finish()
    }

    /** Блокируем «назад»: уйти можно только через возврат к таймеру. */
    @Deprecated("Back navigation is intentionally disabled on the block screen")
    override fun onBackPressed() {
        // no-op
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_APP_LABEL = "extra_app_label"
        const val REDIRECT_DELAY_MS = 5000

        private val STONE_BG = Color.rgb(22, 16, 10)
        private val GOLD = Color.rgb(214, 169, 72)
        private val PARCHMENT = Color.rgb(232, 220, 196)
        private val PARCHMENT_MUTED = Color.rgb(168, 156, 134)
    }
}
