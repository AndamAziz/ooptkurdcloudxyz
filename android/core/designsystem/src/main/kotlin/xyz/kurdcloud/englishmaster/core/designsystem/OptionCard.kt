package xyz.kurdcloud.englishmaster.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** How an answer option is shown. */
enum class OptionState { NEUTRAL, SELECTED, CORRECT, WRONG, DIMMED }

/** A modern answer card: letter badge, the option, and a tick or cross once marked; highlighted while it is read aloud. */
@Composable
fun OptionCard(
    letter: String,
    text: String,
    state: OptionState,
    reading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Kurdish translation shown under the option while [showTranslation] is on. */
    translation: String? = null,
    showTranslation: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val good = FeedbackColors.correct()
    val bad = FeedbackColors.incorrect()
    val accent = when (state) {
        OptionState.CORRECT -> good
        OptionState.WRONG -> bad
        OptionState.SELECTED -> scheme.primary
        else -> if (reading) scheme.tertiary else scheme.outlineVariant
    }
    val container by animateColorAsState(
        when (state) {
            OptionState.CORRECT -> good.copy(alpha = 0.12f)
            OptionState.WRONG -> bad.copy(alpha = 0.10f)
            OptionState.SELECTED -> scheme.primaryContainer.copy(alpha = 0.55f)
            else -> if (reading) scheme.tertiaryContainer.copy(alpha = 0.6f) else scheme.surface
        },
        label = "optionBg",
    )
    OutlinedCard(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (state == OptionState.NEUTRAL && !reading) 1.dp else 2.dp, accent),
        colors = CardDefaults.outlinedCardColors(containerColor = container),
        modifier = modifier
            .fillMaxWidth()
            .testTag("option_$letter")
            .selectable(selected = state == OptionState.SELECTED, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val badgeBg = when (state) {
                OptionState.NEUTRAL, OptionState.DIMMED -> if (reading) scheme.tertiary else scheme.secondaryContainer
                else -> accent
            }
            val badgeFg = if (state == OptionState.NEUTRAL || state == OptionState.DIMMED) {
                if (reading) scheme.onTertiary else scheme.onSecondaryContainer
            } else Color.White
            Box(Modifier.size(34.dp).background(badgeBg, CircleShape), contentAlignment = Alignment.Center) {
                Text(
                    when (state) { OptionState.CORRECT -> "✓"; OptionState.WRONG -> "✗"; else -> letter },
                    color = badgeFg,
                    style = LearningType.option,
                )
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text,
                    style = LearningType.option,
                    color = if (state == OptionState.DIMMED) scheme.onSurfaceVariant else scheme.onSurface,
                )
                KurdishLine(translation, showTranslation)
            }
        }
    }
}

