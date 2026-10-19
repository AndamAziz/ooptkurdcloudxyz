package xyz.kurdcloud.englishmaster.feature.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.kurdcloud.englishmaster.core.designsystem.EmptyState
import xyz.kurdcloud.englishmaster.core.designsystem.FeedbackColors
import xyz.kurdcloud.englishmaster.core.designsystem.LoadingState
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.PracticeAnswer
import xyz.kurdcloud.englishmaster.core.model.Question
import xyz.kurdcloud.englishmaster.core.model.SyncState
import xyz.kurdcloud.englishmaster.core.model.optionLetter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(level: CefrLevel, viewModel: PracticeViewModel, onExit: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.practice_title, level.label)) },
                navigationIcon = { TextButton(onClick = onExit) { Text(stringResource(R.string.practice_exit)) } },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            state.loading -> LoadingState(modifier = modifier)
            state.isEmpty -> EmptyState(stringResource(R.string.practice_empty), modifier, stringResource(R.string.practice_back_home), onExit)
            state.finished -> SummaryView(state.summary, onExit, modifier)
            else -> QuestionView(state, viewModel, modifier)
        }
    }
}

@Composable
private fun QuestionView(state: PracticeUiState, viewModel: PracticeViewModel, modifier: Modifier) {
    val question = state.current ?: return
    val answer = state.currentAnswer
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LinearProgressIndicator(progress = { (state.index + 1f) / state.questions.size }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.practice_counter, state.index + 1, state.questions.size), style = MaterialTheme.typography.labelLarge)
            if (!question.reviewed) {
                Text(stringResource(R.string.practice_unreviewed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Learning content is English: keep it left-to-right inside the Sorani UI.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                QuestionContent(question, state.selected, answer, viewModel::select)
            }
            if (answer == null) {
                Button(
                    onClick = viewModel::submit,
                    enabled = state.selected != null && !state.submitting,
                    modifier = Modifier.fillMaxWidth().testTag("check_answer"),
                ) { Text(stringResource(R.string.practice_check)) }
            } else {
                Feedback(answer, question)
                Button(onClick = viewModel::next, modifier = Modifier.fillMaxWidth().testTag("next_question")) {
                    Text(stringResource(if (state.index + 1 >= state.questions.size) R.string.practice_finish else R.string.practice_next))
                }
            }
        }
    }
}

@Composable
private fun QuestionContent(question: Question, selected: Int?, answer: PracticeAnswer?, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(question.prompt, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("question_prompt"))
        Spacer(Modifier.height(4.dp))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            question.options.forEachIndexed { index, option ->
                val letter = optionLetter(index)
                val border = when {
                    answer?.syncState != SyncState.SYNCED -> null
                    letter == answer.correctOption -> BorderStroke(2.dp, FeedbackColors.correct())
                    letter == answer.chosenOption -> BorderStroke(2.dp, FeedbackColors.incorrect())
                    else -> null
                }
                OutlinedCard(
                    border = border ?: androidx.compose.material3.CardDefaults.outlinedCardBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("option_$letter")
                        .selectable(selected = selected == index, enabled = answer == null, role = Role.RadioButton) { onSelect(index) },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == index, onClick = null, enabled = answer == null)
                        Text("$letter.  $option", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun Feedback(answer: PracticeAnswer, question: Question) {
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("feedback")
    when (answer.syncState) {
        SyncState.PENDING -> Text(stringResource(R.string.practice_saved_pending), style = MaterialTheme.typography.bodyLarge, modifier = live)
        SyncState.FAILED -> Text(stringResource(R.string.practice_rejected), color = MaterialTheme.colorScheme.error, modifier = live)
        SyncState.SYNCED -> Column(live, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (answer.isCorrect == true) {
                Text(stringResource(R.string.practice_correct), color = FeedbackColors.correct(), style = MaterialTheme.typography.titleMedium)
            } else {
                Text(stringResource(R.string.practice_incorrect), color = FeedbackColors.incorrect(), style = MaterialTheme.typography.titleMedium)
                val correct = answer.correctOption
                if (correct != null) {
                    val index = correct.single() - 'A'
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text("$correct.  ${question.options.getOrElse(index) { "" }}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            answer.explanation?.let {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SummaryView(summary: SessionSummary, onExit: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp).testTag("summary"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.practice_summary_title), style = MaterialTheme.typography.headlineSmall)
        val marked = summary.correct + summary.incorrect
        Text(pluralStringResource(R.plurals.practice_summary_scored, marked, summary.correct, marked), textAlign = TextAlign.Center)
        if (summary.pending > 0) {
            Text(pluralStringResource(R.plurals.practice_summary_pending, summary.pending, summary.pending), textAlign = TextAlign.Center)
        }
        Button(onClick = onExit) { Text(stringResource(R.string.practice_back_home)) }
    }
}
