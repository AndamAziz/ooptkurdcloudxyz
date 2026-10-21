package xyz.kurdcloud.englishmaster.feature.practice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import xyz.kurdcloud.englishmaster.core.designsystem.LoadingState
import xyz.kurdcloud.englishmaster.core.designsystem.SpeakButton
import xyz.kurdcloud.englishmaster.core.designsystem.speechText
import xyz.kurdcloud.englishmaster.core.model.LifeInUkTest
import xyz.kurdcloud.englishmaster.core.model.SyncState
import xyz.kurdcloud.englishmaster.core.model.optionLetter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MockTestScreen(viewModel: MockTestViewModel, onClose: () -> Unit, onPractiseMistakes: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val running = state.phase == MockPhase.RUNNING
    BackHandler(enabled = running) { confirmLeave = true }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lituk_mock_title)) },
                navigationIcon = {
                    TextButton(onClick = { if (running) confirmLeave = true else onClose() }) { Text(stringResource(R.string.practice_exit)) }
                },
                actions = { if (running) TimerChip(state.remainingSeconds) },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (state.phase) {
            MockPhase.LOADING -> LoadingState(modifier)
            MockPhase.NOT_ENOUGH -> EmptyState(
                stringResource(R.string.lituk_mock_not_enough, LifeInUkTest.QUESTIONS),
                modifier,
                stringResource(R.string.practice_back_home),
                onClose,
            )
            MockPhase.RUNNING -> RunningView(state, viewModel, modifier)
            MockPhase.MARKING -> LoadingState(modifier, stringResource(R.string.lituk_mock_marking))
            MockPhase.RESULT -> ResultView(state, modifier, onClose, onPractiseMistakes)
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.lituk_mock_leave_title)) },
            text = { Text(stringResource(R.string.lituk_mock_leave_body)) },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onClose() }) { Text(stringResource(R.string.lituk_mock_leave)) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.lituk_mock_keep_going)) } },
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

@Composable
private fun TimerChip(seconds: Long) {
    val low = seconds <= 300
    Text(
        "%d:%02d".format(seconds / 60, seconds % 60),
        modifier = Modifier
            .padding(end = 12.dp)
            .background(if (low) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("mock_timer"),
        color = if (low) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun RunningView(state: MockTestUiState, viewModel: MockTestViewModel, modifier: Modifier) {
    val question = state.question ?: return
    var confirmSubmit by rememberSaveable { mutableStateOf(false) }
    Page(modifier) {
        LinearProgressIndicator(progress = { state.answeredCount.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.practice_counter, state.index + 1, state.total), style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("mock_counter"))
        QuestionStrip(state, viewModel::goTo)
        // Learning content is English: keep it left-to-right inside the Sorani UI.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(question.prompt, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).testTag("mock_prompt"))
                    SpeakButton("question_${question.id}", speechText(question.prompt, question.options))
                }
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val chosen = state.choices[question.id]
                    question.options.forEachIndexed { i, option ->
                        val letter = optionLetter(i)
                        OutlinedCard(
                            border = if (chosen == i) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.fillMaxWidth().testTag("mock_option_$letter")
                                .selectable(selected = chosen == i, role = Role.RadioButton) { viewModel.select(i) },
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = chosen == i, onClick = null)
                                Text("$letter.  $option", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::previous, enabled = state.index > 0, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.lituk_mock_previous))
            }
            if (state.index + 1 < state.total) {
                Button(onClick = viewModel::next, modifier = Modifier.weight(1f).testTag("mock_next")) { Text(stringResource(R.string.practice_next)) }
            } else {
                Button(onClick = { confirmSubmit = true }, modifier = Modifier.weight(1f).testTag("mock_finish")) {
                    Text(stringResource(R.string.lituk_mock_finish))
                }
            }
        }
        if (state.index + 1 < state.total) {
            TextButton(onClick = { confirmSubmit = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lituk_mock_finish)) }
        }
    }
    if (confirmSubmit) {
        AlertDialog(
            onDismissRequest = { confirmSubmit = false },
            title = { Text(stringResource(R.string.lituk_mock_finish_title)) },
            text = {
                Text(
                    if (state.unanswered > 0) pluralStringResource(R.plurals.lituk_mock_unanswered, state.unanswered, state.unanswered)
                    else stringResource(R.string.lituk_mock_all_answered),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmSubmit = false; viewModel.submit() }, modifier = Modifier.testTag("mock_confirm_finish")) {
                    Text(stringResource(R.string.lituk_mock_finish))
                }
            },
            dismissButton = { TextButton(onClick = { confirmSubmit = false }) { Text(stringResource(R.string.lituk_mock_keep_going)) } },
        )
    }
}

/** Numbered chips to jump between questions; answered ones are filled. */
@Composable
private fun QuestionStrip(state: MockTestUiState, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(state.questions, key = { _, q -> q.id }) { i, q ->
            val answered = q.id in state.choices
            val current = i == state.index
            val bg = when {
                current -> MaterialTheme.colorScheme.primary
                answered -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            }
            val fg = when {
                current -> MaterialTheme.colorScheme.onPrimary
                answered -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                Modifier.size(34.dp).background(bg, CircleShape).selectable(selected = current, role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text("${i + 1}", color = fg, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun ResultView(state: MockTestUiState, modifier: Modifier, onClose: () -> Unit, onPractiseMistakes: () -> Unit) {
    Page(modifier) {
        if (state.autoSubmitted) Text(stringResource(R.string.lituk_mock_time_up), style = MaterialTheme.typography.bodyMedium)
        val color = if (state.passed) FeedbackColors.correct() else MaterialTheme.colorScheme.error
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { state.correct.toFloat() / state.total }, modifier = Modifier.size(120.dp), color = color, strokeWidth = 10.dp)
                Text("${state.correct}/${state.total}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            }
        }
        if (state.waiting > 0) {
            Text(
                pluralStringResource(R.plurals.lituk_mock_waiting, state.waiting, state.waiting),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("mock_waiting"),
            )
        } else {
            Text(
                stringResource(if (state.passed) R.string.lituk_mock_passed else R.string.lituk_mock_failed),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("mock_result_title"),
            )
        }
        Text(
            stringResource(R.string.lituk_mock_score, state.correct, state.total, state.percent, LifeInUkTest.PASS_MARK),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        val wrong = state.questions.filter { q -> state.marked[q.id].let { it == null || (it.syncState == SyncState.SYNCED && it.isCorrect == false) } }
        if (wrong.isNotEmpty()) {
            Text(stringResource(R.string.lituk_mock_review), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            wrong.forEach { q -> ReviewItem(q, state.marked[q.id]) }
            Button(onClick = onPractiseMistakes, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lituk_mock_practise_mistakes)) }
        }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lituk_mock_done)) }
    }
}

@Composable
private fun ReviewItem(question: xyz.kurdcloud.englishmaster.core.model.Question, answer: xyz.kurdcloud.englishmaster.core.model.PracticeAnswer?) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("mock_review_item")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(question.prompt, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    fun option(letter: String?) = letter?.let { l -> question.options.getOrNull(l.single() - 'A')?.let { "$l.  $it" } }
                    option(answer?.chosenOption)?.let { Text("✗  $it", color = MaterialTheme.colorScheme.error) }
                    option(answer?.correctOption)?.let { Text("✓  $it", color = FeedbackColors.correct()) }
                    answer?.explanation?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (answer == null) {
                Text(stringResource(R.string.lituk_mock_not_answered), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
