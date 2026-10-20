package xyz.kurdcloud.englishmaster.feature.practice

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.kurdcloud.englishmaster.core.designsystem.LevelColors
import xyz.kurdcloud.englishmaster.core.designsystem.OfflineBanner
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.ExamRules
import xyz.kurdcloud.englishmaster.core.model.LevelOverview
import xyz.kurdcloud.englishmaster.core.model.LevelStage
import xyz.kurdcloud.englishmaster.core.model.Topic
import java.text.DateFormat
import java.util.Date

@StringRes
internal fun CefrLevel.descriptionRes(): Int = when (this) {
    CefrLevel.PRE_A1 -> R.string.level_pre_a1
    CefrLevel.A1 -> R.string.level_a1
    CefrLevel.A2 -> R.string.level_a2
    CefrLevel.B1 -> R.string.level_b1
    CefrLevel.B2 -> R.string.level_b2
    CefrLevel.C1 -> R.string.level_c1
    CefrLevel.C2 -> R.string.level_c2
}

/** Navigation out of the home screen. */
data class HomeActions(
    val onStartPractice: (CefrLevel) -> Unit,
    val onReviewMistakes: (CefrLevel) -> Unit = {},
    val onTakeExam: (CefrLevel) -> Unit = {},
    val onPlacementTest: () -> Unit = {},
    val onCertificates: () -> Unit = {},
    val onPractiseTopic: (Topic) -> Unit = {},
)

@StringRes
internal fun Topic.titleRes(): Int = when (this) {
    Topic.LIFE_IN_UK -> R.string.topic_life_in_uk
    Topic.KURDISTAN -> R.string.topic_kurdistan
    Topic.STORIES -> R.string.topic_stories
    Topic.WORLD -> R.string.topic_world
    Topic.PICTURES -> R.string.topic_pictures
}

@StringRes
private fun Topic.descriptionRes(): Int = when (this) {
    Topic.LIFE_IN_UK -> R.string.topic_life_in_uk_desc
    Topic.KURDISTAN -> R.string.topic_kurdistan_desc
    Topic.STORIES -> R.string.topic_stories_desc
    Topic.WORLD -> R.string.topic_world_desc
    Topic.PICTURES -> R.string.topic_pictures_desc
}

private fun Topic.emoji(): String = when (this) {
    Topic.LIFE_IN_UK -> "🇬🇧"
    Topic.KURDISTAN -> "☀️"
    Topic.STORIES -> "📖"
    Topic.WORLD -> "🌍"
    Topic.PICTURES -> "🎬"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    switchLanguageLabel: String,
    onSwitchLanguage: () -> Unit,
    onStartPractice: (CefrLevel) -> Unit,
    onSignOut: () -> Unit,
    onOpenStaff: () -> Unit = {},
    /** Menu label for the in-app update check; null hides the entry (e.g. Play builds). */
    checkForUpdatesLabel: String? = null,
    onCheckForUpdates: () -> Unit = {},
    actions: HomeActions = HomeActions(onStartPractice),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf<CefrLevel?>(null) }

    // Coming back from an exam or practice session: fetch fresh progress.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshLearning() }

    LaunchedEffect(state.message) {
        state.message?.let { msg ->
            val text = when {
                msg.plural -> resources.getQuantityString(msg.text, msg.arg ?: 0, msg.arg ?: 0)
                msg.arg != null -> resources.getString(msg.text, msg.arg)
                else -> resources.getString(msg.text)
            }
            snackbar.showSnackbar(text)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.practice_home_title), fontWeight = FontWeight.SemiBold) },
                actions = {
                    Box {
                        TextButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("home_menu")) {
                            Text(stringResource(R.string.practice_menu))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_my_certificates)) },
                                onClick = { menuOpen = false; actions.onCertificates() },
                                modifier = Modifier.testTag("menu_certificates"),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_placement_test)) },
                                onClick = { menuOpen = false; actions.onPlacementTest() },
                            )
                            if (state.staffAccess.any) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.practice_staff_tools)) },
                                    onClick = { menuOpen = false; onOpenStaff() },
                                    modifier = Modifier.testTag("menu_staff"),
                                )
                            }
                            DropdownMenuItem(text = { Text(switchLanguageLabel) }, onClick = { menuOpen = false; onSwitchLanguage() })
                            if (checkForUpdatesLabel != null) {
                                DropdownMenuItem(
                                    text = { Text(checkForUpdatesLabel) },
                                    onClick = { menuOpen = false; onCheckForUpdates() },
                                    modifier = Modifier.testTag("menu_check_updates"),
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.practice_sign_out)) },
                                onClick = { menuOpen = false; confirmSignOut = true },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!state.isOnline) OfflineBanner()
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { HeroCard(state, actions.onCertificates) }
                if (state.pendingCount > 0) {
                    item { PendingCard(state.pendingCount, state.isOnline, state.syncing, viewModel::syncNow) }
                }
                state.profile?.let { profile ->
                    item { PlacementCard(profile.placementLevel, actions.onPlacementTest, actions.onStartPractice) }
                }
                item { HowCertificatesWork() }
                if (state.hasDownloads) {
                    item { TopicsSection(state.topics, actions.onPractiseTopic) }
                }
                item {
                    Text(stringResource(R.string.practice_levels_header), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                items(state.levels, key = { it.level.name }) { row ->
                    LevelCard(
                        row = row,
                        nowMillis = state.nowMillis,
                        recommended = state.profile?.placementLevel == row.level,
                        onDownload = { viewModel.download(row.level) },
                        onCancel = { viewModel.cancelDownload(row.level) },
                        onRemove = if (state.staffAccess.any) ({ confirmRemove = row.level }) else null,
                        actions = actions,
                    )
                }
                item {
                    Text(stringResource(R.string.practice_cefr_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.practice_sign_out)) },
            text = {
                Text(
                    if (state.pendingCount > 0) pluralStringResource(R.plurals.practice_sign_out_pending, state.pendingCount, state.pendingCount)
                    else stringResource(R.string.practice_sign_out_confirm),
                )
            },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text(stringResource(R.string.practice_sign_out)) } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.practice_cancel)) } },
        )
    }

    confirmRemove?.let { level ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(R.string.practice_remove_title, level.label)) },
            text = { Text(stringResource(R.string.practice_remove_body)) },
            confirmButton = { TextButton(onClick = { viewModel.remove(level); confirmRemove = null }) { Text(stringResource(R.string.practice_remove)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text(stringResource(R.string.practice_cancel)) } },
        )
    }
}

@Composable
private fun HeroCard(state: HomeUiState, onCertificates: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent), modifier = Modifier.testTag("hero_card")) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val name = state.profile?.displayName
            Text(
                if (name != null) stringResource(R.string.home_hello, name) else stringResource(R.string.home_welcome),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onPrimary,
            )
            Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimary.copy(alpha = 0.9f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                val profile = state.profile
                Stat(profile?.streakDays?.toString() ?: "–", pluralStringResource(R.plurals.home_streak_days, profile?.streakDays ?: 0), Modifier.weight(1f))
                Stat(profile?.totalAnswered?.toString() ?: "–", stringResource(R.string.home_answers), Modifier.weight(1f))
                Stat(
                    state.certificates.toString(),
                    stringResource(R.string.home_certificates),
                    Modifier.weight(1f).clickable(onClick = onCertificates),
                )
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp)).padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f))
    }
}

@Composable
private fun PlacementCard(placement: CefrLevel?, onPlacement: () -> Unit, onPractise: (CefrLevel) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (placement == null) {
                Text(stringResource(R.string.home_placement_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.home_placement_body, ExamRules.PLACEMENT_QUESTIONS, ExamRules.PLACEMENT_MINUTES))
                Button(onClick = onPlacement, modifier = Modifier.testTag("home_placement")) { Text(stringResource(R.string.home_placement_start)) }
            } else {
                Text(stringResource(R.string.home_recommended, placement.label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPractise(placement) }) { Text(stringResource(R.string.home_practise_level, placement.label)) }
                    TextButton(onClick = onPlacement) { Text(stringResource(R.string.home_placement_retake)) }
                }
            }
        }
    }
}

@Composable
private fun HowCertificatesWork() {
    var open by rememberSaveable { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable { open = !open }.testTag("how_certificates"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏅", style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.home_how_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
                Text(if (open) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (open) {
                Step(1, stringResource(R.string.home_how_step1, ExamRules.PRACTICE_PERCENT))
                Step(2, stringResource(R.string.home_how_step2, ExamRules.EXAM_QUESTIONS, ExamRules.EXAM_MINUTES))
                Step(3, stringResource(R.string.home_how_step3, ExamRules.PASS_PERCENT))
                Step(4, stringResource(R.string.home_how_step4, ExamRules.RETRY_HOURS))
            }
        }
    }
}

@Composable
private fun TopicsSection(counts: Map<Topic, Int>, onPractise: (Topic) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("topics_section")) {
        Text(stringResource(R.string.topics_header), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.topics_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (counts.values.all { it == 0 }) {
            // Packs downloaded before topics existed have no tags until they are updated.
            Text(stringResource(R.string.topics_update_levels), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
        }
        Topic.entries.forEach { topic ->
            val count = counts[topic] ?: 0
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth().clickable(enabled = count > 0) { onPractise(topic) }.testTag("topic_${topic.apiValue}"),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(topic.emoji(), style = MaterialTheme.typography.headlineSmall)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(stringResource(topic.titleRes()), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(topic.descriptionRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        pluralStringResource(R.plurals.topic_questions, count, count),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(26.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PendingCard(count: Int, online: Boolean, syncing: Boolean, onSync: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.testTag("pending_card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(pluralStringResource(R.plurals.practice_pending, count, count), style = MaterialTheme.typography.bodyLarge)
            if (online) {
                Button(onClick = onSync, enabled = !syncing) {
                    Text(stringResource(if (syncing) R.string.practice_syncing else R.string.practice_sync_now))
                }
            }
        }
    }
}

@Composable
private fun LevelBadge(level: CefrLevel) {
    val color = LevelColors.of(level)
    Box(Modifier.size(48.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(level.label, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun LevelCard(
    row: LevelRow,
    nowMillis: Long,
    recommended: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    /** Null for learners: only staff may delete a downloaded level from the device. */
    onRemove: (() -> Unit)?,
    actions: HomeActions,
) {
    val overview = row.overview
    val stage = overview?.stage(nowMillis)
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize().testTag("level_${row.level.name}"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LevelBadge(row.level)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_level_title, row.level.label), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(row.level.descriptionRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (recommended) StatusChip(stringResource(R.string.home_chip_recommended), MaterialTheme.colorScheme.secondaryContainer)
                when (stage) {
                    LevelStage.CERTIFIED -> StatusChip(stringResource(R.string.home_chip_certified), Color(0xFFFFE08A))
                    LevelStage.AWAITING_APPROVAL -> StatusChip(stringResource(R.string.home_chip_awaiting_approval), MaterialTheme.colorScheme.secondaryContainer)
                    LevelStage.EXAM_READY -> StatusChip(stringResource(R.string.home_chip_exam_ready), MaterialTheme.colorScheme.primaryContainer)
                    LevelStage.EXAM_IN_PROGRESS -> StatusChip(stringResource(R.string.home_chip_in_progress), MaterialTheme.colorScheme.tertiaryContainer)
                    else -> Unit
                }
            }
            if (overview != null) ProgressSection(overview, stage!!)

            val pack = row.pack
            when {
                row.downloading != null -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(pluralStringResource(R.plurals.practice_downloading, row.downloading, row.downloading))
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.practice_cancel)) }
                }
                pack == null -> {
                    Text(stringResource(R.string.practice_not_downloaded), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onDownload) { Text(stringResource(R.string.practice_download)) }
                }
                else -> {
                    Text(pluralStringResource(R.plurals.practice_downloaded, pack.questionCount, pack.questionCount), style = MaterialTheme.typography.bodySmall)
                    if (overview == null) {
                        row.progress?.let {
                            Text(stringResource(R.string.practice_progress, it.answered, it.correct), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { actions.onStartPractice(row.level) }, enabled = pack.questionCount > 0) { Text(stringResource(R.string.practice_start)) }
                        if (row.mistakes > 0) {
                            FilledTonalButton(onClick = { actions.onReviewMistakes(row.level) }, modifier = Modifier.testTag("review_${row.level.name}")) {
                                Text(pluralStringResource(R.plurals.home_review_mistakes, row.mistakes, row.mistakes))
                            }
                        }
                        if (onRemove != null) {
                            LevelMenu(onUpdate = onDownload, onRemove = onRemove)
                        } else {
                            TextButton(onClick = onDownload, modifier = Modifier.testTag("update_${row.level.name}")) {
                                Text(stringResource(R.string.practice_update))
                            }
                        }
                    }
                }
            }
            when (stage) {
                LevelStage.EXAM_READY, LevelStage.EXAM_IN_PROGRESS ->
                    Button(onClick = { actions.onTakeExam(row.level) }, modifier = Modifier.fillMaxWidth().testTag("exam_${row.level.name}")) {
                        Text(stringResource(if (stage == LevelStage.EXAM_IN_PROGRESS) R.string.home_continue_exam else R.string.home_take_exam))
                    }
                LevelStage.CERTIFIED ->
                    OutlinedButton(onClick = actions.onCertificates, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_view_certificate)) }
                else -> Unit
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    AssistChip(
        onClick = {},
        label = { Text(text, style = MaterialTheme.typography.labelMedium) },
        colors = AssistChipDefaults.assistChipColors(containerColor = color, labelColor = Color(0xFF1B1B1F)),
        border = null,
    )
}

@Composable
private fun ProgressSection(o: LevelOverview, stage: LevelStage) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (stage == LevelStage.PRACTISE || stage == LevelStage.EXAM_READY) {
            LinearProgressIndicator(
                progress = { o.unlockProgress },
                modifier = Modifier.fillMaxWidth().testTag("unlock_progress_${o.level.name}"),
            )
            Text(
                stringResource(R.string.home_progress_line, o.answered, o.required, o.accuracyPercent),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val status = when (stage) {
            LevelStage.PRACTISE -> if (o.needsBetterAccuracy) stringResource(R.string.home_status_accuracy, o.requiredPercent)
            else pluralStringResource(R.plurals.home_status_practise_more, o.remainingToUnlock, o.remainingToUnlock, o.requiredPercent)
            LevelStage.EXAM_READY -> stringResource(R.string.home_status_exam_ready, ExamRules.PASS_PERCENT)
            LevelStage.EXAM_IN_PROGRESS -> stringResource(R.string.home_status_in_progress)
            LevelStage.WAIT_FOR_RETRY -> stringResource(
                R.string.home_status_retry,
                o.lastExamScore ?: 0, o.lastExamTotal ?: 0,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(o.examAvailableAtMillis ?: 0)),
            )
            LevelStage.AWAITING_APPROVAL -> stringResource(R.string.home_status_awaiting_approval, o.lastExamScore ?: 0, o.lastExamTotal ?: 0)
            LevelStage.CERTIFIED -> stringResource(R.string.home_status_certified, o.lastExamScore ?: 0, o.lastExamTotal ?: 0)
        }
        Text(status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("level_status_${o.level.name}"))
    }
}

@Composable
private fun LevelMenu(onUpdate: () -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(stringResource(R.string.home_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.practice_update)) }, onClick = { open = false; onUpdate() })
            DropdownMenuItem(text = { Text(stringResource(R.string.practice_remove)) }, onClick = { open = false; onRemove() })
        }
    }
}
