package com.studydungeon.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.studydungeon.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import com.studydungeon.lock.LockMode
import com.studydungeon.lock.LockPermissions
import com.studydungeon.lock.LockPreferences

/**
 * Настройки блокировки телефона на время Фазы_Работы (пп. 5 и 6 запроса), по
 * образцу Focus To-do, без Device Owner/ADB:
 *
 * - выбор режима: выкл / полная блокировка / блокировка отдельных приложений;
 * - выдача необходимых системных разрешений (открывает системные экраны):
 *   «Поверх других приложений», «Данные об использовании», «Доступ к
 *   уведомлениям»;
 * - для режима «отдельные приложения» — список приложений с отметками, какие
 *   блокировать.
 *
 * Это настройки устройства (не игровая логика), поэтому composable работает
 * напрямую с [LockPreferences]/[LockPermissions] и не затрагивает доменный слой.
 * Статусы разрешений перечитываются при каждом возврате на экран (ON_RESUME).
 */
@Composable
fun LockSettingsContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { LockPreferences(context) }

    var mode by remember { mutableStateOf(prefs.mode) }
    var blocked by remember { mutableStateOf(prefs.blockedPackages) }

    // Бамп при ON_RESUME, чтобы перечитать статусы разрешений после возврата из
    // системных настроек.
    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val canOverlay = remember(refreshKey) { LockPermissions.canDrawOverlays(context) }
    val hasUsage = remember(refreshKey) { LockPermissions.hasUsageAccess(context) }
    val hasNotif = remember(refreshKey) { LockPermissions.isNotificationAccessGranted(context) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Блокировка телефона",
            style = MaterialTheme.typography.titleSmall,
            color = Dungeon.GoldBright
        )
        Text(
            text = "Блокирует телефон на время фазы работы. Разрешения выдаются один раз в системных настройках.",
            style = MaterialTheme.typography.bodySmall,
            color = Dungeon.ParchmentMuted
        )

        ModeOption(
            title = "Выключено",
            description = "Без блокировки (только удержание в приложении, если включено закрепление экрана).",
            selected = mode == LockMode.NONE,
            onClick = {
                mode = LockMode.NONE
                prefs.mode = LockMode.NONE
            }
        )
        ModeOption(
            title = "Полная блокировка",
            description = "Поверх всего экрана — нельзя уйти из приложения и пользоваться телефоном; уведомления не приходят.",
            selected = mode == LockMode.FULL,
            onClick = {
                mode = LockMode.FULL
                prefs.mode = LockMode.FULL
            }
        )
        ModeOption(
            title = "Блокировка отдельных приложений",
            description = "Выбранные приложения нельзя открыть: появится экран «Доступ запрещён» с возвратом к таймеру.",
            selected = mode == LockMode.PER_APP,
            onClick = {
                mode = LockMode.PER_APP
                prefs.mode = LockMode.PER_APP
            }
        )

        if (mode != LockMode.NONE) {
            HorizontalDivider(color = Dungeon.GoldTrim, modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = "Необходимые разрешения",
                style = MaterialTheme.typography.labelLarge,
                color = Dungeon.Parchment
            )

            PermissionRow(
                title = "Поверх других приложений",
                granted = canOverlay,
                onGrant = { runCatching { context.startActivity(LockPermissions.overlaySettingsIntent(context)) } }
            )
            PermissionRow(
                title = "Данные об использовании",
                granted = hasUsage,
                onGrant = { runCatching { context.startActivity(LockPermissions.usageAccessSettingsIntent()) } }
            )
            if (mode == LockMode.FULL) {
                PermissionRow(
                    title = "Доступ к уведомлениям",
                    granted = hasNotif,
                    onGrant = { runCatching { context.startActivity(LockPermissions.notificationAccessSettingsIntent()) } }
                )
            }
        }

        if (mode == LockMode.PER_APP && canOverlay && hasUsage) {
            HorizontalDivider(color = Dungeon.GoldTrim, modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = "Какие приложения блокировать",
                style = MaterialTheme.typography.labelLarge,
                color = Dungeon.Parchment
            )

            val apps = remember(refreshKey) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(intent, 0)
                    .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                    .filter { it.first != context.packageName }
                    .distinctBy { it.first }
                    .sortedBy { it.second.lowercase() }
            }

            var query by remember { mutableStateOf("") }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.lock_search_hint)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        TextButton(onClick = { query = "" }) {
                            Text(stringResource(R.string.lock_search_clear))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Dungeon.Parchment,
                    unfocusedTextColor = Dungeon.Parchment,
                    focusedBorderColor = Dungeon.Gold,
                    unfocusedBorderColor = Dungeon.GoldTrim,
                    cursorColor = Dungeon.GoldBright,
                    focusedPlaceholderColor = Dungeon.ParchmentMuted,
                    unfocusedPlaceholderColor = Dungeon.ParchmentMuted
                )
            )

            val filteredApps = remember(apps, query) {
                if (query.isBlank()) apps
                else apps.filter { it.second.contains(query.trim(), ignoreCase = true) }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                filteredApps.forEach { (pkg, label) ->
                    val isBlocked = pkg in blocked
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                prefs.setBlocked(pkg, !isBlocked)
                                blocked = prefs.blockedPackages
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Dungeon.Parchment,
                            modifier = Modifier.weight(1f)
                        )
                        Checkbox(
                            checked = isBlocked,
                            onCheckedChange = {
                                prefs.setBlocked(pkg, it)
                                blocked = prefs.blockedPackages
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Dungeon.Gold,
                                uncheckedColor = Dungeon.ParchmentMuted
                            )
                        )
                    }
                }
                if (filteredApps.isEmpty()) {
                    Text(
                        text = "Список приложений пуст.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Dungeon.ParchmentMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (selected) "◉" else "○",
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) Dungeon.Gold else Dungeon.ParchmentMuted
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) Dungeon.GoldBright else Dungeon.Parchment
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Dungeon.ParchmentMuted
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Dungeon.Parchment
            )
            Text(
                text = if (granted) "Выдано" else "Не выдано",
                style = MaterialTheme.typography.labelMedium,
                color = if (granted) Dungeon.Teal else Dungeon.Health
            )
        }
        if (granted) {
            TextButton(onClick = onGrant) { Text("Изменить") }
        } else {
            OutlinedButton(onClick = onGrant) { Text("Выдать") }
        }
    }
}
