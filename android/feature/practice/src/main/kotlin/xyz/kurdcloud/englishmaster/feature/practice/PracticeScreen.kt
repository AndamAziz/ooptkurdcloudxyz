package xyz.kurdcloud.englishmaster.feature.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import xyz.kurdcloud.englishmaster.core.designsystem.LearningType
import xyz.kurdcloud.englishmaster.core.designsystem.OptionCard
import xyz.kurdcloud.englishmaster.core.designsystem.OptionState
import xyz.kurdcloud.englishmaster.core.designsystem.ListenControls
import xyz.kurdcloud.englishmaster.core.designsystem.LocalSpeaker
import xyz.kurdcloud.englishmaster.core.designsystem.questionSpeech
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.kurdcloud.englishmaster.core.designsystem.EmptyState
import xyz.kurdcloud.englishmaster.core.designsystem.LoadingState
import xyz.kurdcloud.englishmaster.core.designsystem.PassageCard
import xyz.kurdcloud.englishmaster.core.designsystem.QuestionMediaView
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.PracticeAnswer
import xyz.kurdcloud.englishmaster.core.model.Question
import xyz.kurdcloud.englishmaster.core.model.SyncState
import xyz.kurdcloud.englishmaster.core.model.optionLetter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    level: CefrLevel?,
    viewModel: PracticeViewModel,
    onExit: () -> Unit,
    /** Replaces the level/topic title (Life in the UK chapters). */
    title: String? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val topic = viewModel.topic
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            title != null -> title
                            topic != null -> stringResource(topic.titleRes())
                            viewModel.mistakesOnly -> stringResource(R.string.practice_review_title, level?.label.orEmpty())
                            else -> stringResource(R.string.practice_title, level?.label.orEmpty())
                        },
                    )
                },
                navigationIcon = { TextButton(onClick = onExit) { Text(stringResource(R.string.practice_exit)) } },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            state.loading -> LoadingState(modifier = modifier)
            state.isEmpty -> EmptyState(
                stringResource(
                    when {
                        title != null -> R.string.lituk_chapter_empty
                        topic != null -> R.string.practice_topic_empty
                        viewModel.mistakesOnly -> R.string.practice_review_empty
                        else -> R.string.practice_empty
                    },
                ),
                modifier,
                stringResource(R.string.practice_back_home),
                onExit,
            )
            state.finished -> SummaryView(state.summary, onExit, modifier)
            else -> QuestionView(state, viewModel, modifier, showLevel = topic != null)
        }
    }
}

@Composable
private fun QuestionView(state: PracticeUiState, viewModel: PracticeViewModel, modifier: Modifier, showLevel: Boolean) {
    val question = state.current ?: return
    val answer = state.currentAnswer
    val speaker = LocalSpeaker.current
    val questionParts = remember(question.id) { questionSpeech(question.prompt, question.options) }
    // Auto-read: the question when it appears, the explanation once the answer is marked.
    LaunchedEffect(question.id, speaker?.available) {
        if (speaker?.autoRead == true && speaker.available) speaker.speakParts("question_${question.id}", questionParts)
    }
    LaunchedEffect(question.id, answer?.syncState) {
        if (speaker?.autoRead == true && answer?.syncState == SyncState.SYNCED) speaker.speakParts("feedback_${question.id}", feedbackSpeech(question, answer))
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LinearProgressIndicator(progress = { (state.index + 1f) / state.questions.size }, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.practice_counter, state.index + 1, state.questions.size) +
                    if (showLevel) "  ·  ${question.level.label}" else "",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.testTag("practice_counter"),
            )
            if (!question.reviewed) {
                Text(stringResource(R.string.practice_unreviewed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ListenControls("question_${question.id}", questionParts)
            // Learning content is English: keep it left-to-right inside the Sorani UI.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                QuestionContent(question, state.selected, answer, viewModel::select)
            }
            if (answer == null) {
                Button(
                    onClick = viewModel::submit,
                    enabled = state.selected != null && !state.submitting,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("check_answer"),
                ) { Text(stringResource(R.string.practice_check)) }
            } else {
                FeedbackCard(question, answer)
                Button(onClick = viewModel::next, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("next_question")) {
                    Text(stringResource(if (state.index + 1 >= state.questions.size) R.string.practice_finish else R.string.practice_next))
                }
            }
        }
    }
}

@Composable
private fun QuestionContent(question: Question, selected: Int?, answer: PracticeAnswer?, onSelect: (Int) -> Unit) {
    val reading = LocalSpeaker.current?.let { if (it.speakingId == "question_${question.id}") it.speakingPart else null }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        question.passage?.let { PassageCard(it, speakId = "passage_${question.id}") }
        question.media?.let { QuestionMediaView(it, stringResource(R.string.practice_media_unavailable)) }
        Text(
            question.prompt,
            style = LearningType.question,
            color = if (reading == "prompt") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("question_prompt"),
        )
        Spacer(Modifier.height(2.dp))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            question.options.forEachIndexed { index, option ->
                val letter = optionLetter(index)
                val marked = answer?.syncState == SyncState.SYNCED
                val optionState = when {
                    marked && letter == answer.correctOption -> OptionState.CORRECT
                    marked && letter == answer.chosenOption -> OptionState.WRONG
                    marked -> OptionState.DIMMED
                    selected == index -> OptionState.SELECTED
                    else -> OptionState.NEUTRAL
                }
                OptionCard(letter, option, optionState, reading == letter, enabled = answer == null, onClick = { onSelect(index) })
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
