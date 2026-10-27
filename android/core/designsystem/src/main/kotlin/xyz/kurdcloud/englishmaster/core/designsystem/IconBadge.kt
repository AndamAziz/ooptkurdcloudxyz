package xyz.kurdcloud.englishmaster.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's one badge style: a rounded square with a soft diagonal gradient and a white glyph.
 * Every section, topic and level uses it, at the same size, so the screens look consistent.
 */
object BadgeColors {
    val Britain = Color(0xFF3949AB)
    val Stories = Color(0xFFD81B60)
    val World = Color(0xFF00897B)
    val Pictures = Color(0xFFEF6C00)
    val Certificates = Color(0xFFF9A825)
    val Placement = Color(0xFF6A1B9A)
    val Update = Color(0xFF1E88E5)
    /** The driving theory course: motorway-sign blue to green. */
    val Theory = Color(0xFF00695C)
    val TheoryEnd = Color(0xFF2E7D32)
    val UnionBlue = Color(0xFF012169)
    val UnionRed = Color(0xFFC8102E)
}

val BadgeSize: Dp = 44.dp

private fun badgeBrush(color: Color, end: Color = color) =
    Brush.linearGradient(listOf(lerp(color, Color.White, 0.22f), lerp(end, Color.Black, 0.08f)))

@Composable
fun IconBadge(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = BadgeSize,
    /** Second gradient colour, e.g. Union Flag blue to red. */
    endColor: Color = color,
) {
    Box(
        modifier.size(size).background(badgeBrush(color, endColor), RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

/** The same badge with a short label (a level such as "B2", or a chapter number) instead of a glyph. */
@Composable
fun TextBadge(text: String, color: Color, modifier: Modifier = Modifier, size: Dp = BadgeSize) {
    Box(
        modifier.size(size).background(badgeBrush(color), RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

/** The badge for use on a coloured or gradient card: frosted white instead of a colour. */
@Composable
fun GlassBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = BadgeSize) {
    Box(
        modifier.size(size).background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}
