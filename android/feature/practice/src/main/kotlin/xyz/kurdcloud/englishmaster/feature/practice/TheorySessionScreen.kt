package xyz.kurdcloud.englishmaster.feature.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import xyz.kurdcloud.englishmaster.core.designsystem.LoadingState
import xyz.kurdcloud.englishmaster.core.designsystem.OptionCard
import xyz.kurdcloud.englishmaster.core.designsystem.OptionState
import xyz.kurdcloud.englishmaster.core.designsystem.QuestionMediaView
import xyz.kurdcloud.englishmaster.core.designsystem.rememberShowKurdish
import xyz.kurdcloud.englishmaster.core.model.TheoryMode
import xyz.kurdcloud.englishmaster.core.model.TheoryTestItem
import xyz.kurdcloud.englishmaster.core.model.optionLetter

/**
 * One round through a theory chapter.
 *
 * In study mode the card shows the question, the learner thinks, reveals the answer and says
 * whether they knew it — the way the questions are meant to be learnt, since each one has only its
 * correct answer. In test mode the same question comes with four options, so it feels like the
 * real exam.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TheorySessionScreen(viewModel: TheorySessionViewModel, sectionName: String, onExit: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(viewModel.mode.titleRes()), fontWeight = FontWeight.SemiBold)
                        Text(sectionName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { TextButton(onClick = onExit) { Text(stringResource(R.string.practice_exit)) } },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (state.phase) {
            TheoryPhase.LOADING -> LoadingState(modifier = modifier)
            TheoryPhase.EMPTY -> EmptyState(
                stringResource(R.string.theory_session_empty),
                modifier,
                stringResource(R.string.practice_back_home),
                onExit,
            )
            TheoryPhase.DONE -> TheoryResult(state, viewModel.mode, viewModel::again, onExit, modifier)
            TheoryPhase.RUNNING -> TheoryCard(state, viewModel, modifier)
        }
    }
}

@Composable
private fun TheoryCard(state: TheorySessionUiState, viewModel: TheorySessionViewModel, modifier: Modifier) {
    val item = state.item ?: return
    val question = item.question
    var showKurdish by rememberShowKurdish()
    val hasKurdish = question.promptCkb != null || question.answerCkb != null
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LinearProgressIndicator(progress = { (state.index + 1f) / state.total }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.practice_counter, state.index + 1, state.total),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f).testTag("theory_counter"),
                )
                if (hasKurdish) {
                    KurdishToggle(showKurdish, { showKurdish = !showKurdish }, stringResource(R.string.practice_show_kurdish))
                }
            }
            // The questions are English: keep them left-to-right inside the Sorani interface.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                question.media?.let { QuestionMediaView(it, stringResource(R.string.practice_media_unavailable)) }
                Text(question.prompt, style = LearningType.question, modifier = Modifier.testTag("theory_prompt"))
            }
            KurdishPanel(question.promptCkb, showKurdish, style = KurdishType.question, tag = "kurdish_prompt")

            if (viewModel.mode == TheoryMode.STUDY) {
                StudyControls(state, viewModel, showKurdish)
            } else {
                TestControls(state, item, viewModel, showKurdish)
            }
        }
    }
}

@Composable
private fun StudyControls(state: TheorySessionUiState, viewModel: TheorySessionViewModel, showKurdish: Boolean) {
    val question = state.item?.question ?: return
    if (!state.revealed) {
        Button(
            onClick = viewModel::reveal,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("theory_reveal"),
        ) { Text(stringResource(R.string.theory_reveal)) }
        Text(
            stringResource(R.string.theory_study_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    AnswerPanel(question.answer, question.answerCkb, question.explanation, question.explanationCkb, showKurdish)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { viewModel.knew(true) },
            modifier = Modifier.weight(1f).height(52.dp).testTag("theory_knew"),
        ) { Text(stringResource(R.string.theory_knew_it)) }
        OutlinedButton(
            onClick = { viewModel.knew(false) },
            modifier = Modifier.weight(1f).height(52.dp).testTag("theory_not_yet"),
        ) { Text(stringResource(R.string.theory_not_yet)) }
    }
}

@Composable
private fun TestControls(
    state: TheorySessionUiState,
    item: TheoryTestItem,
    viewModel: TheorySessionViewModel,
    showKurdish: Boolean,
) {
    val chosen = state.chosen
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item.options.forEachIndexed { index, option ->
                val optionState = when {
                    chosen == null -> OptionState.NEUTRAL
                    index == item.correctIndex -> OptionState.CORRECT
                    index == chosen -> OptionState.WRONG
                    else -> OptionState.DIMMED
                }
                OptionCard(
                    letter = optionLetter(index),
                    text = option,
                    state = optionState,
                    reading = false,
                    enabled = chosen == null,
                    onClick = { viewModel.choose(index) },
                    translation = item.optionsCkb.getOrNull(index),
                    showTranslation = showKurdish,
                )
            }
        }
    }
    if (chosen != null) {
        val question = item.question
        if (chosen != item.correctIndex) {
            AnswerPanel(question.answer, question.answerCkb, question.explanation, question.explanationCkb, showKurdish)
        } else if (question.explanation != null) {
            AnswerPanel(null, null, question.explanation, question.explanationCkb, showKurdish)
        }
        Button(onClick = viewModel::next, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("theory_next")) {
            Text(stringResource(if (state.isLast) R.string.practice_finish else R.string.practice_next))
        }
    }
}

/** The correct answer, with its Sorani text and any explanation. */
@Composable
private fun AnswerPanel(answer: String?, answerCkb: String?, explanation: String?, explanationCkb: String?, showKurdish: Boolean) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FeedbackColors.correct().copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth().testTag("theory_answer"),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (answer != null) {
                Text(
                    stringResource(R.string.theory_answer_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(answer, style = LearningType.option, fontWeight = FontWeight.SemiBold)
                }
                KurdishPanel(answerCkb, showKurdish, tag = "kurdish_answer")
            }
            explanation?.let {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                KurdishPanel(explanationCkb, showKurdish, tag = "kurdish_explanation")
            }
        }
    }
}

@Composable
private fun TheoryResult(
    state: TheorySessionUiState,
    mode: TheoryMode,
    onAgain: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp).testTag("theory_result"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(if (mode == TheoryMode.STUDY) R.string.theory_result_study else R.string.theory_result_test),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.theory_result_score, state.correct, state.total, state.percent),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAgain, modifier = Modifier.testTag("theory_again")) { Text(stringResource(R.string.theory_again)) }
        FilledTonalButton(onClick = onExit) { Text(stringResource(R.string.theory_back_to_chapters)) }
    }
}
