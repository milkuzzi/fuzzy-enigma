package com.studydungeon.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.studydungeon.R

/** Один шаг онбординга: иконка-спрайт, заголовок и пояснение. */
private data class OnboardingStep(
    val iconRes: Int,
    val titleRes: Int,
    val textRes: Int
)

private val onboardingSteps = listOf(
    OnboardingStep(R.drawable.ic_hourglass, R.string.onboarding_title_1, R.string.onboarding_text_1),
    OnboardingStep(R.drawable.ic_potion, R.string.onboarding_title_2, R.string.onboarding_text_2),
    OnboardingStep(R.drawable.ic_hp, R.string.onboarding_title_3, R.string.onboarding_text_3)
)

/**
 * Экран онбординга при первом запуске: несколько шагов с пояснением ключевых
 * возможностей (помидорки и прокачка, магазин/предметы, блокировка телефона).
 * По завершении или пропуску вызывает [onFinish].
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    var stepIndex by remember { mutableIntStateOf(0) }
    val isLast = stepIndex == onboardingSteps.lastIndex

    Box(modifier = modifier.fillMaxSize()) {
        DungeonBackground(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onFinish) {
                    Text(text = stringResource(R.string.onboarding_skip))
                }
            }

            AnimatedContent(
                targetState = stepIndex,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboarding-step"
            ) { index ->
                val s = onboardingSteps[index]
                DungeonPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PixelIcon(resId = s.iconRes, contentDescription = null, size = 72.dp)
                        Text(
                            text = stringResource(s.titleRes),
                            style = MaterialTheme.typography.titleLarge,
                            color = Dungeon.GoldBright,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(s.textRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Dungeon.Parchment,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onboardingSteps.indices.forEach { i ->
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    if (i == stepIndex) Dungeon.Gold else Dungeon.GoldTrim.copy(alpha = 0.3f),
                                    RoundedCornerShape(5.dp)
                                )
                        )
                    }
                }
                Button(
                    onClick = { if (isLast) onFinish() else stepIndex++ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            if (isLast) R.string.onboarding_start else R.string.onboarding_next
                        )
                    )
                }
            }
        }
    }
}
