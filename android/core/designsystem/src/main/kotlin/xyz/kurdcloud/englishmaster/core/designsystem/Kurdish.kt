package xyz.kurdcloud.englishmaster.core.designsystem

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Vazirmatn (SIL Open Font License, see VAZIRMATN-OFL.txt): a clear modern face with every Sorani letter. */
val KurdishFont = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
)

/** Text styles for the Kurdish translation shown under English learning content. */
object KurdishType {
    val question = TextStyle(fontFamily = KurdishFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 28.sp, textDirection = TextDirection.Rtl)
    val option = TextStyle(fontFamily = KurdishFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp, textDirection = TextDirection.Rtl)
    val body = TextStyle(fontFamily = KurdishFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 26.sp, textDirection = TextDirection.Rtl)
}

private const val PREFS = "learning_prefs"
private const val SHOW_KURDISH = "show_kurdish"

/** Whether the learner keeps the Kurdish translation open; remembered on the device. */
@Composable
fun rememberShowKurdish(): MutableState<Boolean> {
    val prefs = LocalContext.current.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val state = remember { mutableStateOf(prefs.getBoolean(SHOW_KURDISH, false)) }
    LaunchedEffect(state) { snapshotFlow { state.value }.collect { prefs.edit().putBoolean(SHOW_KURDISH, it).apply() } }
    return state
}

/** The «کوردی» switch above a question: shows or hides the Kurdish translation. */
@Composable
fun KurdishToggle(on: Boolean, onToggle: () -> Unit, description: String, modifier: Modifier = Modifier) {
    FilterChip(
        selected = on,
        onClick = onToggle,
        label = { Text("کوردی", fontFamily = KurdishFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
        leadingIcon = { Icon(AppIcons.Translate, contentDescription = null, modifier = Modifier.size(18.dp)) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier.testTag("kurdish_toggle").semantics { contentDescription = description },
    )
}

/**
 * A Kurdish translation under English content: right-to-left, in a soft panel with an accent bar
 * on the reading side. It slides open and closed with [visible].
 */
@Composable
fun KurdishPanel(text: String?, visible: Boolean, modifier: Modifier = Modifier, style: TextStyle = KurdishType.body, maxHeight: Int? = null, tag: String = "kurdish_text") {
    AnimatedVisibility(
        visible = visible && !text.isNullOrBlank(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val scheme = MaterialTheme.colorScheme
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .background(scheme.primaryContainer.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .semantics(mergeDescendants = true) {}
                    .testTag(tag),
            ) {
                Box(Modifier.width(4.dp).fillMaxHeight().padding(vertical = 10.dp).background(scheme.primary, RoundedCornerShape(2.dp)))
                val scroll = if (maxHeight != null) Modifier.heightIn(max = maxHeight.dp).verticalScroll(rememberScrollState()) else Modifier
                Text(
                    text.orEmpty(),
                    style = style,
                    color = scheme.onSurface,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f).then(scroll).padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** A short Kurdish line under an answer option (no panel, so the option card stays compact). */
@Composable
fun KurdishLine(text: String?, visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible && !text.isNullOrBlank(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Text(
                text.orEmpty(),
                style = KurdishType.option,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Start,
                modifier = modifier.padding(top = 2.dp).testTag("kurdish_option"),
            )
        }
    }
}
