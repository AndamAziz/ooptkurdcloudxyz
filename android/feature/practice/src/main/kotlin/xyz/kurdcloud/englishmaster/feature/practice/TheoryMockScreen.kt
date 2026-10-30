package xyz.kurdcloud.englishmaster.feature.practice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.kurdcloud.englishmaster.core.designsystem.EmptyState
import xyz.kurdcloud.englishmaster.core.designsystem.FeedbackColors
import xyz.kurdcloud.englishmaster.core.designsystem.KurdishPanel
import xyz.kurdcloud.englishmaster.core.designsystem.KurdishToggle
import xyz.kurdcloud.englishmaster.core.designsystem.KurdishType
import xyz.kurdcloud.englishmaster.core.designsystem.LearningType
import xyz.kurdcloud.englishmaster.core.designsystem.ListenControls
import xyz.kurdcloud.englishmaster.core.designsystem.LoadingState
import xyz.kurdcloud.englishmaster.core.designsystem.LocalSpeaker
import xyz.kurdcloud.englishmaster.core.designsystem.OptionCard
import xyz.kurdcloud.englishmaster.core.designsystem.OptionState
import xyz.kurdcloud.englishmaster.core.designsystem.QuestionMediaView
import xyz.kurdcloud.englishmaster.core.designsystem.questionSpeech
import xyz.kurdcloud.englishmaster.core.designsystem.rememberShowKurdish
import xyz.kurdcloud.englishmaster.core.model.TheoryTestItem
import xyz.kurdcloud.englishmaster.core.model.optionLetter

/**
 * A mock driving theory exam: questions from every chapter, a countdown, and no feedback until the
 * paper is handed in — the way the real test works. The learner can move between questions freely
 * and change an answer up to the moment they hand in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TheoryMockScreen(viewModel: TheoryMockViewModel, onClose: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val running = state.phase == TheoryMockPhase.RUNNING
    BackHandler(enabled = running) { confirmLeave = true }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.theory_mock_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    TextButton(onClick = { if (running) confirmLeave = true else onClose() }) {
                        Text(stringResource(R.string.practice_exit))
                    }
                },
                actions = { if (running) MockTimer(state.remainingSeconds) },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (state.phase) {
            TheoryMockPhase.LOADING -> LoadingState(modifier, stringResource(R.string.theory_mock_preparing))
            TheoryMockPhase.EMPTY -> EmptyState(
                stringResource(R.string.theory_mock_empty),
                modifier,
                stringResource(R.string.practice_back_home),
                onClose,
            )
            TheoryMockPhase.RUNNING -> RunningPaper(state, viewModel, modifier)
            TheoryMockPhase.RESULT -> MockResult(state, viewModel::again, onClose, modifier)
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.theory_mock_leave_title)) },
            text = { Text(stringResource(R.string.theory_mock_leave_body)) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; onClose() }) { Text(stringResource(R.string.theory_mock_leave)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.theory_mock_keep_going)) }
            },
        )
    }
}

@Composable
private fun Page(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

/** The countdown; it turns red over the last five minutes, as the official test does. */
@Composable
private fun MockTimer(seconds: Long) {
    val low = seconds <= 300
    Text(
        "%d:%02d".format(seconds / 60, seconds % 60),
        modifier = Modifier
            .padding(end = 12.dp)
            .background(
                if (low) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                CircleShape,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("theory_mock_timer"),
        color = if (low) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun RunningPaper(state: TheoryMockUiState, viewModel: TheoryMockViewModel, modifier: Modifier) {
    val item = state.item ?: return
    val question = item.question
    var showKurdish by rememberShowKurdish()
    var confirmSubmit by rememberSaveable { mutableStateOf(false) }
    val speaker = LocalSpeaker.current
    val speakId = "theory_mock_${question.id}"
    val parts = remember(question.id, item.options) { questionSpeech(question.prompt, item.options) }
    LaunchedEffect(question.id, speaker?.available) {
        if (speaker?.autoRead == true && speaker.available) speaker.speakParts(speakId, parts)
    }
    val reading = speaker?.let { if (it.speakingId == speakId) it.speakingPart else null }
    Page(modifier) {
        LinearProgressIndicator(
            progress = { state.answeredCount.toFloat() / state.total.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.theory_mock_counter, state.index + 1, state.total),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f).testTag("theory_mock_counter"),
            )
            if (question.promptCkb != null) {
                KurdishToggle(showKurdish, { showKurdish = !showKurdish }, stringResource(R.string.practice_show_kurdish))
            }
        }
        QuestionStrip(state, viewModel::goTo)
        ListenControls(speakId, parts)
        // The questions are English: keep them left-to-right inside the Sorani interface.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            question.media?.let { QuestionMediaView(it, stringResource(R.string.practice_media_unavailable)) }
            Text(
                question.prompt,
                style = LearningType.question,
                color = if (reading == "prompt") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("theory_mock_prompt"),
            )
        }
        KurdishPanel(question.promptCkb, showKurdish, style = KurdishType.question, tag = "kurdish_prompt")
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val chosen = state.choices[question.id]
                item.options.forEachIndexed { index, option ->
                    val letter = optionLetter(index)
                    OptionCard(
                        letter = letter,
                        text = option,
                        // Nothing is marked until the paper is handed in, so a pick only looks selected.
                        state = if (chosen == index) OptionState.SELECTED else OptionState.NEUTRAL,
                        reading = reading == letter,
                        enabled = true,
                        onClick = { viewModel.select(index) },
                        translation = item.optionsCkb.getOrNull(index),
                        showTranslation = showKurdish,
                        modifier = Modifier.testTag("theory_mock_option_$letter"),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = viewModel::previous,
                enabled = state.index > 0,
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text(stringResource(R.string.lituk_mock_previous)) }
            if (!state.isLast) {
                Button(
                    onClick = viewModel::next,
                    modifier = Modifier.weight(1f).height(52.dp).testTag("theory_mock_next"),
                ) { Text(stringResource(R.string.practice_next)) }
            } else {
                Button(
                    onClick = { confirmSubmit = true },
                    modifier = Modifier.weight(1f).height(52.dp).testTag("theory_mock_finish"),
                ) { Text(stringResource(R.string.theory_mock_finish)) }
            }
        }
        if (!state.isLast) {
            TextButton(onClick = { confirmSubmit = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.theory_mock_finish))
            }
        }
    }
    if (confirmSubmit) {
        AlertDialog(
            onDismissRequest = { confirmSubmit = false },
            title = { Text(stringResource(R.string.theory_mock_finish_title)) },
            text = {
                Text(
                    if (state.unanswered > 0) {
                        pluralStringResource(R.plurals.theory_mock_unanswered, state.unanswered, state.unanswered)
                    } else {
                        stringResource(R.string.theory_mock_all_answered)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmSubmit = false; viewModel.submit() },
                    modifier = Modifier.testTag("theory_mock_confirm_finish"),
                ) { Text(stringResource(R.string.theory_mock_finish)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSubmit = false }) { Text(stringResource(R.string.theory_mock_keep_going)) }
            },
        )
    }
}

/** Numbered chips to jump between questions; the ones already answered are filled in. */
@Composable
private fun QuestionStrip(state: TheoryMockUiState, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("theory_mock_strip")) {
        itemsIndexed(state.items, key = { _, it -> it.question.id }) { i, item ->
            val answered = item.question.id in state.choices
            val current = i == state.index
            val background = when {
                current -> MaterialTheme.colorScheme.primary
                answered -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            }
            val foreground = when {
                current -> MaterialTheme.colorScheme.onPrimary
                answered -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                Modifier
                    .size(34.dp)
                    .background(background, CircleShape)
                    .selectable(selected = current, role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text("${i + 1}", color = foreground, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun MockResult(state: TheoryMockUiState, onAgain: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    var showKurdish by rememberShowKurdish()
    Page(modifier) {
        if (state.autoSubmitted) {
            Text(stringResource(R.string.theory_mock_time_up), style = MaterialTheme.typography.bodyMedium)
        }
        val color = if (state.passed) FeedbackColors.correct() else MaterialTheme.colorScheme.error
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { state.correct.toFloat() / state.total.coerceAtLeast(1) },
                    modifier = Modifier.size(120.dp),
                    color = color,
                    strokeWidth = 10.dp,
                )
                Text(
                    "${state.correct}/${state.total}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
        }
        Text(
            stringResource(if (state.passed) R.string.theory_mock_passed else R.string.theory_mock_failed),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag("theory_mock_result_title"),
        )
        Text(
            stringResource(R.string.theory_mock_score, state.correct, state.total, state.percent, state.passMark),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        val wrong = state.wrong
        if (wrong.isNotEmpty()) {
            Text(
                stringResource(R.string.theory_mock_review),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            wrong.forEach { item -> ReviewCard(item, state.choices[item.question.id], showKurdish) }
        }
        Button(onClick = onAgain, modifier = Modifier.fillMaxWidth().testTag("theory_mock_again")) {
            Text(stringResource(R.string.theory_mock_again))
        }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.theory_mock_done))
        }
    }
}

/** One question the learner got wrong: what they picked, the right answer and why. */
@Composable
private fun ReviewCard(item: TheoryTestItem, chosen: Int?, showKurdish: Boolean) {
    val question = item.question
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().testTag("theory_mock_review_item"),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(question.prompt, style = LearningType.option, fontWeight = FontWeight.SemiBold)
            }
            KurdishPanel(question.promptCkb, showKurdish, tag = "kurdish_prompt")
            if (chosen == null) {
                Text(
                    stringResource(R.string.theory_mock_not_answered),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        stringResource(R.string.theory_mock_your_answer, item.options.getOrNull(chosen).orEmpty()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(
                stringResource(R.string.theory_answer_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(question.answer, style = LearningType.option, color = FeedbackColors.correct())
            }
            KurdishPanel(question.answerCkb, showKurdish, tag = "kurdish_answer")
            question.explanation?.let {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                KurdishPanel(question.explanationCkb, showKurdish, tag = "kurdish_explanation")
            }
        }
    }
}
