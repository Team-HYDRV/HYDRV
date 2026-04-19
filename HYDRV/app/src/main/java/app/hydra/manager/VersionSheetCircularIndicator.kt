package app.hydra.manager

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp

@Composable
fun VersionSheetCircularIndicator(
    progress: Float?,
    indicatorColorArgb: Int,
    trackColorArgb: Int,
    strokeWidth: Dp
) {
    if (progress == null) {
        CircularProgressIndicator(
            modifier = Modifier.fillMaxSize(),
            color = Color(indicatorColorArgb),
            trackColor = Color(trackColorArgb),
            strokeCap = StrokeCap.Round,
            strokeWidth = strokeWidth
        )
    } else {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            color = Color(indicatorColorArgb),
            trackColor = Color(trackColorArgb),
            strokeCap = StrokeCap.Round,
            strokeWidth = strokeWidth
        )
    }
}
