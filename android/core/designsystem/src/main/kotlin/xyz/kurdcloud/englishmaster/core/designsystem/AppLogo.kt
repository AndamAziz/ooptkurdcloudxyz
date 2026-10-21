package xyz.kurdcloud.englishmaster.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app logo as on the launcher (open book with a rising sun on the brand gradient), drawn from
 * the same adaptive-icon layers. Decorative: screens show the app name next to it.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 88.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.26f))) {
        Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.fillMaxSize())
        // Adaptive-icon artwork is drawn on a 108dp canvas of which ~72dp is visible: zoom to match.
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize().scale(1.5f))
    }
}
