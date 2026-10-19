package xyz.kurdcloud.englishmaster.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1F5FA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E3FF),
    onPrimaryContainer = Color(0xFF001B3C),
    secondary = Color(0xFF0F7B6C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB8F0E3),
    onSecondaryContainer = Color(0xFF00201B),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFFAF9FD),
    surface = Color(0xFFFAF9FD),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA7C8FF),
    onPrimary = Color(0xFF003061),
    primaryContainer = Color(0xFF004788),
    onPrimaryContainer = Color(0xFFD5E3FF),
    secondary = Color(0xFF9CD4C7),
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF005046),
    onSecondaryContainer = Color(0xFFB8F0E3),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
)

/** Semantic colours for answer feedback, readable in both themes. */
object FeedbackColors {
    @Composable fun correct() = if (isSystemInDarkTheme()) Color(0xFF7DDB93) else Color(0xFF1B6D2F)
    @Composable fun incorrect() = MaterialTheme.colorScheme.error
}

@Composable
fun EnglishMasterTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, content = content)
}
