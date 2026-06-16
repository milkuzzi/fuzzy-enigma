package com.studydungeon.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.studydungeon.service.FocusModeController

/**
 * Entry-point Activity, host'ящая Compose-экран Study Dungeon с биндингом к
 * [StudyDungeonViewModel].
 *
 * Activity отвечает за платформенную проводку, которую ViewModel не может
 * выполнить самостоятельно:
 * - создаёт [FocusModeController] (привязан к жизненному циклу Activity) и
 *   связывает его с ViewModel ([StudyDungeonViewModel.bindFocusController]);
 * - регистрирует запрос разрешения `POST_NOTIFICATIONS` (R12.4) и передаёт его
 *   ViewModel через [StudyDungeonViewModel.setNotificationPermissionRequester];
 * - транслирует события жизненного цикла: возврат на передний план
 *   ([StudyDungeonViewModel.onActivityResumed] — детект выхода из
 *   Закрепления_Экрана, R9.4/R13.10), уход в фон
 *   ([StudyDungeonViewModel.onAppBackgrounded] — напоминание о возврате, R13.11)
 *   и принудительное закрытие ([StudyDungeonViewModel.onForceQuit] — штраф,
 *   R9.3).
 */
class MainActivity : ComponentActivity() {

    private val viewModel: StudyDungeonViewModel by viewModels {
        StudyDungeonViewModel.Factory(application)
    }

    private lateinit var focusModeController: FocusModeController

    /**
     * Лаунчер запроса разрешения на уведомления. Результат не требует обработки
     * на стороне Activity: при отказе Таймер продолжает работать без уведомлений
     * (см. design — «Разрешения»).
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Контроллер Режима_Фокуса привязан к Activity и связывается с ViewModel.
        focusModeController = FocusModeController(this)
        viewModel.bindFocusController(focusModeController)

        // Запрос разрешения POST_NOTIFICATIONS при первой отправке уведомления
        // (R12.4). На версиях ниже Android 13 разрешение не требуется.
        viewModel.setNotificationPermissionRequester {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            StudyDungeonTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                Box(modifier = Modifier.fillMaxSize()) {
                    // Каменный фон подземелья под всем интерфейсом (R14).
                    DungeonBackground(modifier = Modifier.fillMaxSize())
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                    ) { innerPadding ->
                        StudyDungeonScreen(
                            viewModel = viewModel,
                            snackbarHostState = snackbarHostState,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    /**
     * При возврате на передний план проверяем принудительный выход из
     * Закрепления_Экрана: при обнаружении ViewModel трактует его как провал
     * сессии (R9.4, R13.10).
     */
    override fun onResume() {
        super.onResume()
        viewModel.onActivityResumed()
    }

    /**
     * Уход Приложения в фон: при активном Режиме_Фокуса и выполняющемся Таймере
     * ViewModel отправит напоминание о возврате к учёбе (R13.11).
     */
    override fun onStop() {
        super.onStop()
        viewModel.onAppBackgrounded()
    }

    /**
     * Принудительное закрытие Приложения во время выполнения Таймера трактуется
     * как штраф (R9.3). Срабатывает только при фактическом завершении Activity.
     */
    override fun onDestroy() {
        if (isFinishing) {
            viewModel.onForceQuit()
        }
        super.onDestroy()
    }
}
