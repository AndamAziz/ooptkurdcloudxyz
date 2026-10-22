package xyz.kurdcloud.englishmaster.feature.exam

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import xyz.kurdcloud.englishmaster.core.designsystem.ListenControls
import xyz.kurdcloud.englishmaster.core.designsystem.LocalSpeaker
import xyz.kurdcloud.englishmaster.core.designsystem.OptionCard
import xyz.kurdcloud.englishmaster.core.designsystem.OptionState
import xyz.kurdcloud.englishmaster.core.designsystem.questionSpeech
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
import xyz.kurdcloud.englishmaster.core.designsystem.SpeakButton
import xyz.kurdcloud.englishmaster.core.designsystem.speechText
import xyz.kurdcloud.englishmaster.core.designsystem.FeedbackColors
import xyz.kurdcloud.englishmaster.core.designsystem.PassageCard
import xyz.kurdcloud.englishmaster.core.designsystem.QuestionMediaView
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.ExamKind
import xyz.kurdcloud.englishmaster.core.model.ExamRules
import xyz.kurdcloud.englishmaster.core.model.optionLetter
import java.text.DateFormat
import java.util.Date

@StringRes
internal fun ExamProblem.messageRes(): Int = when (this) {
    ExamProblem.OFFLINE -> R.string.problem_offline
    ExamProblem.SIGN_IN_AGAIN -> R.string.problem_sign_in
    ExamProblem.NOT_UNLOCKED -> R.string.problem_not_unlocked
    ExamProblem.RETRY_LATER -> R.string.problem_retry_later
    ExamProblem.ALREADY_CERTIFIED -> R.string.problem_already_certified
    ExamProblem.PENDING_APPROVAL -> R.string.problem_pending_approval
    ExamProblem.NOT_ENOUGH_QUESTIONS -> R.string.problem_not_enough
    ExamProblem.INVALID_NAME -> R.string.problem_invalid_name
    ExamProblem.UNEXPECTED -> R.string.problem_unexpected
}

internal fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamScreen(
    viewModel: ExamViewModel,
    onClose: () -> Unit,
    onViewCertificates: () -> Unit,
    onPractise: (CefrLevel) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val running = state.phase == ExamPhase.RUNNING || state.phase == ExamPhase.SUBMITTING
    BackHandler(enabled = running) { confirmLeave = true }

    val title = if (state.kind == ExamKind.PLACEMENT) stringResource(R.string.placement_title)
    else stringResource(R.string.exam_title, state.level?.label.orEmpty())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TextButton(onClick = { if (running) confirmLeave = true else onClose() }, modifier = Modifier.testTag("exam_close")) {
                        Text(stringResource(R.string.exam_close))
                    }
                },
                actions = {
                    if (running) TimerChip(state.remainingSeconds)
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            AnimatedContent(targetState = state.phase, label = "exam-phase") { phase ->
                when (phase) {
                    ExamPhase.INTRO -> IntroView(state, viewModel)
                    ExamPhase.STARTING -> Busy(stringResource(R.string.exam_starting))
                    ExamPhase.RUNNING, ExamPhase.SUBMITTING -> RunningView(state, viewModel)
                    ExamPhase.FINISHED -> ResultView(state, onClose, onViewCertificates, onPractise)
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.exam_leave_title)) },
            text = { Text(stringResource(R.string.exam_leave_body)) },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onClose() }) { Text(stringResource(R.string.exam_leave)) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.exam_keep_working)) } },
        )
    }
}

@Composable
private fun TimerChip(seconds: Long) {
    val text = "%d:%02d".format(seconds / 60, seconds % 60)
    val low = seconds <= 120
    Text(
        stringResource(R.string.exam_time_left, text),
        modifier = Modifier
            .padding(end = 12.dp)
            .background(
                if (low) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                CircleShape,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("exam_timer"),
        color = if (low) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun Busy(text: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator()
        Text(text, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Content(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
    }
}

@Composable
private fun ProblemCard(problem: ExamProblem?, retryAtMillis: Long? = null) {
    if (problem == null) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.testTag("exam_problem")) {
        Text(
            if (problem == ExamProblem.RETRY_LATER && retryAtMillis != null) stringResource(R.string.problem_retry_at, formatDateTime(retryAtMillis))
            else stringResource(problem.messageRes()),
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun IntroView(state: ExamUiState, viewModel: ExamViewModel) {
    var editName by rememberSaveable { mutableStateOf(false) }
    Content {
        val placement = state.kind == ExamKind.PLACEMENT
        Text(
            if (placement) stringResource(R.string.placement_intro_heading)
            else stringResource(R.string.exam_intro_heading, state.level?.label.orEmpty()),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (placement) {
                    Rule("1", stringResource(R.string.placement_rule_questions, ExamRules.PLACEMENT_QUESTIONS))
                    Rule("2", stringResource(R.string.placement_rule_time, ExamRules.PLACEMENT_MINUTES))
                    Rule("3", stringResource(R.string.placement_rule_attempts, ExamRules.PLACEMENT_PER_DAY))
                    Rule("4", stringResource(R.string.exam_rule_online))
                } else {
                    Rule("1", stringResource(R.string.exam_rule_questions, ExamRules.EXAM_QUESTIONS, state.level?.label.orEmpty()))
                    Rule("2", stringResource(R.string.exam_rule_time, ExamRules.EXAM_MINUTES))
                    Rule("3", stringResource(R.string.exam_rule_pass, ExamRules.PASS_PERCENT))
                    Rule("4", stringResource(R.string.exam_rule_retry, ExamRules.RETRY_HOURS))
                    Rule("5", stringResource(R.string.exam_rule_online))
                }
            }
        }
        if (!placement) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.exam_name_label), style = MaterialTheme.typography.labelLarge)
                        Text(state.displayName ?: "…", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("exam_name"))
                    }
                    TextButton(onClick = { editName = true }, enabled = state.displayName != null) { Text(stringResource(R.string.exam_name_edit)) }
                }
            }
        }
        if (placement) state.placementStatus?.let { PlacementStatusCard(it, state.placementLocked) }
        // The status card already says when the next test opens; avoid repeating it.
        if (!(state.placementLocked && state.problem == ExamProblem.RETRY_LATER)) ProblemCard(state.problem, state.retryAtMillis)
        Button(onClick = viewModel::start, enabled = !state.placementLocked, modifier = Modifier.fillMaxWidth().testTag("exam_start")) {
            Text(
                stringResource(
                    when {
                        placement && state.placementStatus?.inProgress == true -> R.string.placement_continue
                        placement -> R.string.placement_start
                        else -> R.string.exam_start
                    },
                ),
            )
        }
        Text(stringResource(R.string.certificate_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (editName) {
        NameDialog(initial = state.displayName.orEmpty(), onDismiss = { editName = false }) { name ->
            editName = false
            viewModel.updateName(name)
        }
    }
}

@Composable
private fun PlacementStatusCard(status: xyz.kurdcloud.englishmaster.core.model.PlacementStatus, locked: Boolean) {
    val next = status.nextAttemptAtMillis
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (locked) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth().testTag("placement_status"),
    ) {
        Text(
            when {
                status.inProgress -> stringResource(R.string.placement_status_in_progress)
                locked && next != null -> stringResource(R.string.placement_status_locked, formatDateTime(next))
                else -> pluralStringResource(R.plurals.placement_status_left, status.attemptsLeft, status.attemptsLeft)
            },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun Rule(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(26.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(number, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium) }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun NameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exam_name_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.exam_name_dialog_hint))
                OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, singleLine = true, modifier = Modifier.testTag("exam_name_field"))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.exam_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.exam_cancel)) } },
    )
}

@Composable
private fun RunningView(state: ExamUiState, viewModel: ExamViewModel) {
    val question = state.question ?: return
    var confirmSubmit by rememberSaveable { mutableStateOf(false) }
    val submitting = state.phase == ExamPhase.SUBMITTING
    Content {
        LinearProgressIndicator(progress = { state.answeredCount.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.exam_counter, state.index + 1, state.total), style = MaterialTheme.typography.labelLarge)
        QuestionStrip(state, viewModel::goTo)
        ProblemCard(state.problem)
        val questionParts = remember(question.id) { questionSpeech(question.prompt, question.options) }
        val speaker = LocalSpeaker.current
        LaunchedEffect(question.id, speaker?.available) {
            if (speaker?.autoRead == true && speaker.available) speaker.speakParts("question_${question.id}", questionParts)
        }
        val reading = speaker?.let { if (it.speakingId == "question_${question.id}") it.speakingPart else null }
        ListenControls("question_${question.id}", questionParts)
        // Learning content is English: keep it left-to-right inside the Sorani UI.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                question.passage?.let { PassageCard(it, speakId = "passage_${question.id}") }
                question.media?.let { QuestionMediaView(it, stringResource(R.string.exam_media_unavailable)) }
                Text(
                    question.prompt,
                    style = LearningType.question,
                    color = if (reading == "prompt") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("exam_prompt"),
                )
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val chosen = state.answers[question.id]
                    question.options.forEachIndexed { i, option ->
                        val letter = optionLetter(i)
                        OptionCard(
                            letter, option,
                            if (chosen == i) OptionState.SELECTED else OptionState.NEUTRAL,
                            reading = reading == letter, enabled = !submitting, onClick = { viewModel.select(i) },
                            modifier = Modifier.testTag("exam_option_$letter"),
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = viewModel::previous, enabled = state.index > 0 && !submitting, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.exam_previous))
            }
            if (state.index + 1 < state.total) {
                Button(onClick = viewModel::next, enabled = !submitting, modifier = Modifier.weight(1f).testTag("exam_next")) {
                    Text(stringResource(R.string.exam_next))
                }
            }
        }
        Button(
            onClick = { confirmSubmit = true },
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth().testTag("exam_finish"),
        ) {
            Text(stringResource(if (submitting) R.string.exam_submitting else R.string.exam_review_submit))
        }
    }
    if (confirmSubmit) {
        AlertDialog(
            onDismissRequest = { confirmSubmit = false },
            title = { Text(stringResource(R.string.exam_confirm_title)) },
            text = {
                Text(
                    if (state.unanswered > 0) pluralStringResource(R.plurals.exam_confirm_unanswered, state.unanswered, state.unanswered)
                    else stringResource(R.string.exam_confirm_all),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmSubmit = false; viewModel.submit() }, modifier = Modifier.testTag("exam_confirm_submit")) {
                    Text(stringResource(R.string.exam_submit))
                }
            },
            dismissButton = { TextButton(onClick = { confirmSubmit = false }) { Text(stringResource(R.string.exam_keep_working)) } },
        )
    }
}

/** Numbered chips to jump between questions; answered ones are filled. */
@Composable
private fun QuestionStrip(state: ExamUiState, onSelect: (Int) -> Unit) {
    val questions = state.session?.questions ?: return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(questions, key = { _, q -> q.id }) { i, q ->
            val answered = q.id in state.answers
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
                Modifier.size(34.dp).background(bg, CircleShape)
                    .selectable(selected = current, role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text("${i + 1}", color = fg, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun ResultView(state: ExamUiState, onClose: () -> Unit, onViewCertificates: () -> Unit, onPractise: (CefrLevel) -> Unit) {
    Content {
        if (state.autoSubmitted) Text(stringResource(R.string.exam_auto_submitted), style = MaterialTheme.typography.bodyMedium)
        val result = state.result
        val placement = state.placement
        if (result != null) {
            val passed = result.passed
            ScoreBadge(result.percent, passed)
            Text(
                stringResource(if (passed) R.string.exam_passed_title else R.string.exam_failed_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().testTag("exam_result_title"),
                textAlign = TextAlign.Center,
            )
            Text(stringResource(R.string.exam_score, result.score, result.total, result.percent), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.exam_pass_mark, result.passPercent), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall)
            if (result.awaitingApproval) {
                // Certificates are issued only after a teacher or admin approves the result.
                Text(
                    stringResource(R.string.exam_awaiting_approval, state.level?.label.orEmpty()),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag("exam_awaiting_approval"),
                )
            } else if (passed) {
                Text(stringResource(R.string.exam_certificate_ready, state.level?.label.orEmpty()), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Button(onClick = onViewCertificates, modifier = Modifier.fillMaxWidth().testTag("exam_view_certificate")) {
                    Text(stringResource(R.string.exam_view_certificate))
                }
            } else {
                if (result.expired) Text(stringResource(R.string.exam_expired), color = MaterialTheme.colorScheme.error)
                result.nextAttemptAtMillis?.let { Text(stringResource(R.string.exam_next_attempt, formatDateTime(it))) }
                Text(stringResource(R.string.exam_failed_advice))
            }
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.exam_back_home)) }
        } else if (placement != null) {
            ScoreBadge(if (placement.total == 0) 0 else placement.score * 100 / placement.total, passed = true, label = placement.recommendedLevel.label)
            Text(
                stringResource(R.string.placement_result_title, placement.recommendedLevel.label),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("placement_result"),
            )
            Text(stringResource(R.string.placement_result_score, placement.score, placement.total), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(
                placement.passedLevel?.let { stringResource(R.string.placement_result_passed, it.label) } ?: stringResource(R.string.placement_result_none),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onPractise(placement.recommendedLevel) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.placement_start_level, placement.recommendedLevel.label))
            }
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.exam_back_home)) }
        }
    }
}

@Composable
private fun ScoreBadge(percent: Int, passed: Boolean, label: String = "$percent%") {
    val color = if (passed) FeedbackColors.correct() else MaterialTheme.colorScheme.error
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { percent / 100f }, modifier = Modifier.size(120.dp), color = color, strokeWidth = 10.dp)
            Text(label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
