package app.hydra.manager

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

@Composable
fun DownloadInstallWavyIndicator(
    stage: InstallStatusCenter.InstallStage?,
    visualProgress: Int,
    confirmationRequested: Boolean,
    indicatorColorArgb: Int,
    trackColorArgb: Int
) {
    val progress = remember { Animatable(0f) }
    val shouldShowWaitingAnimation =
        !confirmationRequested || stage == InstallStatusCenter.InstallStage.WAITING_CONFIRMATION

    LaunchedEffect(stage, confirmationRequested, visualProgress) {
        when {
            shouldShowWaitingAnimation -> progress.snapTo(0f)
            stage == InstallStatusCenter.InstallStage.PREPARING -> {
                if (progress.value <= 0f) {
                    progress.snapTo(0f)
                }
                progress.animateTo(
                    targetValue = (visualProgress.coerceIn(0, 100) / 100f).coerceAtLeast(0.58f),
                    animationSpec = tween(
                        durationMillis = 1600,
                        easing = LinearOutSlowInEasing
                    )
                )
            }
            stage == InstallStatusCenter.InstallStage.SUCCESS -> {
                if (progress.value < 0.58f) {
                    progress.snapTo(0.58f)
                }
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 360,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            else -> progress.snapTo(0f)
        }
    }

    if (shouldShowWaitingAnimation) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxSize(),
            color = Color(indicatorColorArgb),
            trackColor = Color(trackColorArgb),
            strokeCap = StrokeCap.Round,
            gapSize = 2.dp
        )
    } else {
        LinearProgressIndicator(
            progress = { progress.value.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            color = Color(indicatorColorArgb),
            trackColor = Color(trackColorArgb),
            strokeCap = StrokeCap.Round,
            gapSize = 2.dp
        )
    }
}
