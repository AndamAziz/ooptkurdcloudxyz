package xyz.kurdcloud.englishmaster.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import xyz.kurdcloud.englishmaster.core.model.CefrLevel

// Brand: deep indigo with a teal accent and a warm gold for achievements.
private val Light = lightColorScheme(
    primary = Color(0xFF3A4FC4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC6F1EA),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF7B4FD6),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEBDDFF),
    onTertiaryContainer = Color(0xFF250059),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F7FC),
    surface = Color(0xFFF7F7FC),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F2F9),
    surfaceContainer = Color(0xFFECECF4),
    surfaceContainerHigh = Color(0xFFE6E7F0),
    surfaceContainerHighest = Color(0xFFE0E1EA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF08218F),
    primaryContainer = Color(0xFF2236AB),
    onPrimaryContainer = Color(0xFFDEE0FF),
    secondary = Color(0xFF7ED7C9),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFFC6F1EA),
    tertiary = Color(0xFFD3BBFF),
    onTertiary = Color(0xFF3F008D),
    tertiaryContainer = Color(0xFF5B2FB5),
    onTertiaryContainer = Color(0xFFEBDDFF),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318),
    surface = Color(0xFF121318),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF292A30),
    surfaceContainerHighest = Color(0xFF34343B),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val AppTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Nunito (SIL Open Font License, see NUNITO-OFL.txt): rounded and very legible for English learning content. */
val LearningFont = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
)

/** Text styles for questions, answers and explanations (English content; the UI keeps the system font for Sorani). */
object LearningType {
    val question = TextStyle(fontFamily = LearningFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp)
    val option = TextStyle(fontFamily = LearningFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp)
    val body = TextStyle(fontFamily = LearningFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val heading = TextStyle(fontFamily = LearningFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp)
    val label = TextStyle(fontFamily = LearningFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.6.sp)
}

/** Semantic colours for answer feedback, readable in both themes. */
object FeedbackColors {
    @Composable fun correct() = if (isSystemInDarkTheme()) Color(0xFF7DDB93) else Color(0xFF1B6D2F)
    @Composable fun incorrect() = MaterialTheme.colorScheme.error
}

/** One colour per CEFR level, from green (A1) to deep purple (C2); white text is readable on all. */
object LevelColors {
    fun of(level: CefrLevel): Color = when (level) {
        CefrLevel.PRE_A1 -> Color(0xFF607D8B)
        CefrLevel.A1 -> Color(0xFF2E7D32)
        CefrLevel.A2 -> Color(0xFF00897B)
        CefrLevel.B1 -> Color(0xFF1E88E5)
        CefrLevel.B2 -> Color(0xFF3949AB)
        CefrLevel.C1 -> Color(0xFF8E24AA)
        CefrLevel.C2 -> Color(0xFFC62828)
        CefrLevel.LIFE_IN_UK -> Color(0xFF012169) // Union Flag blue
    }
}

@Composable
fun EnglishMasterTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
