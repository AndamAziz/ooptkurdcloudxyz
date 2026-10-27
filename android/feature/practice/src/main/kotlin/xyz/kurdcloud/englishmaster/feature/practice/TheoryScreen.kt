package xyz.kurdcloud.englishmaster.feature.practice

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.kurdcloud.englishmaster.core.designsystem.AppIcons
import xyz.kurdcloud.englishmaster.core.designsystem.BadgeColors
import xyz.kurdcloud.englishmaster.core.designsystem.GlassBadge
import xyz.kurdcloud.englishmaster.core.designsystem.KurdishFont
import xyz.kurdcloud.englishmaster.core.designsystem.LoadingState
import xyz.kurdcloud.englishmaster.core.designsystem.OfflineBanner
import xyz.kurdcloud.englishmaster.core.designsystem.TextBadge
import xyz.kurdcloud.englishmaster.core.model.TheoryMode
import xyz.kurdcloud.englishmaster.core.model.TheorySection
import xyz.kurdcloud.englishmaster.core.model.TheoryTest

/** Navigation out of the driving theory page. */
data class TheoryActions(
    val onStudy: (TheorySection) -> Unit = {},
    val onTest: (TheorySection) -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * The UK driving theory course: the chapters of the official test, each showing how many of its
 * questions the learner has attempted and how many they got right, the way the official app does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TheoryScreen(viewModel: TheoryViewModel, actions: TheoryActions) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(state.message) {
        state.message?.let { msg ->
            snackbar.showSnackbar(
                if (msg.plural) resources.getQuantityString(msg.text, msg.arg ?: 0, msg.arg ?: 0) else resources.getString(msg.text),
            )
            viewModel.messageShown()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.theory_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { TextButton(onClick = actions.onBack) { Text(stringResource(R.string.practice_exit)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!state.isOnline) OfflineBanner()
            if (state.loading) {
                LoadingState()
                return@Column
            }
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().testTag("theory_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { TheoryHeaderCard(state) }
                item { TheoryDownloadCard(state, viewModel::downloadAll) }
                item {
                    Text(
                        stringResource(R.string.theory_sections_header),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(state.sections, key = { it.slug }) { section ->
                    TheorySectionCard(
                        section = section,
                        number = state.sections.indexOf(section) + 1,
                        saved = state.isSaved(section),
                        actions = actions,
                        onReset = { viewModel.resetSection(section.slug) },
                    )
                }
                item { TheoryOfficialLinks() }
                item {
                    Text(
                        stringResource(R.string.theory_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TheoryHeaderCard(state: TheoryUiState) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(BadgeColors.Theory, BadgeColors.TheoryEnd)))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassBadge(AppIcons.Car)
            Text(
                stringResource(R.string.theory_header_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.theory_header_body, TheoryTest.QUESTIONS, TheoryTest.MINUTES, TheoryTest.PASS_MARK),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f),
            )
            if (state.published > 0) {
                Text(
                    stringResource(R.string.theory_overall, state.answered, state.published, state.accuracyPercent),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("theory_overall"),
                )
            }
        }
    }
}

@Composable
private fun TheoryDownloadCard(state: TheoryUiState, onDownload: () -> Unit) {
    val downloading = state.downloading
    val missing = state.missing.size
    if (downloading == null && missing == 0 && state.pending == 0) return
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                downloading != null -> {
                    Text(stringResource(R.string.theory_downloading, downloading.done + 1, downloading.total))
                    LinearProgressIndicator(
                        progress = { (downloading.done + 1f) / downloading.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                missing > 0 -> {
                    Text(stringResource(R.string.theory_download_body), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onDownload, modifier = Modifier.testTag("theory_download")) {
                        Text(stringResource(R.string.theory_download))
                    }
                }
                else -> Text(
                    pluralStringResource(R.plurals.theory_pending, state.pending, state.pending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun TheorySectionCard(
    section: TheorySection,
    number: Int,
    saved: Boolean,
    actions: TheoryActions,
    onReset: () -> Unit,
) {
    var open by rememberSaveable(section.slug) { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(enabled = !section.isEmpty) { open = !open }
            .testTag("theory_section_${section.slug}"),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextBadge("$number", if (section.isEmpty) MaterialTheme.colorScheme.outline else BadgeColors.Theory)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(section.nameEn, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    section.nameCkb?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = KurdishFont, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (section.isComplete) {
                    Text("✓", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (section.isEmpty) {
                Text(
                    stringResource(R.string.theory_section_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("theory_section_empty_${section.slug}"),
                )
            } else {
                // The line the official app shows: attempted out of the chapter's total, and accuracy.
                Text(
                    stringResource(R.string.theory_section_progress, section.answered, section.published, section.accuracyPercent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("theory_progress_${section.slug}"),
                )
                LinearProgressIndicator(progress = { section.answeredPercent / 100f }, modifier = Modifier.fillMaxWidth())
                if (open) {
                    if (!saved) {
                        Text(
                            stringResource(R.string.theory_section_not_saved),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { actions.onStudy(section) },
                            modifier = Modifier.weight(1f).testTag("theory_study_${section.slug}"),
                        ) { Text(stringResource(R.string.theory_study)) }
                        FilledTonalButton(
                            onClick = { actions.onTest(section) },
                            modifier = Modifier.weight(1f).testTag("theory_test_${section.slug}"),
                        ) { Text(stringResource(R.string.theory_test)) }
                    }
                    if (section.answered > 0) {
                        OutlinedButton(onClick = onReset, modifier = Modifier.testTag("theory_reset_${section.slug}")) {
                            Text(stringResource(R.string.theory_reset))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TheoryOfficialLinks() {
    val uri = LocalUriHandler.current
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.theory_official_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.theory_official_body, TheoryTest.HAZARD_CLIPS, TheoryTest.HAZARD_PASS_MARK, TheoryTest.HAZARD_TOTAL),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { uri.openUri(TheoryTest.BOOK_URL) }, modifier = Modifier.testTag("theory_link_book")) {
                Text(stringResource(R.string.theory_link_book))
            }
            TextButton(onClick = { uri.openUri(TheoryTest.PRACTISE_URL) }) { Text(stringResource(R.string.theory_link_practise)) }
        }
    }
}

/** The mode names, for the session screen's title. */
internal fun TheoryMode.titleRes(): Int = when (this) {
    TheoryMode.STUDY -> R.string.theory_study
    TheoryMode.TEST -> R.string.theory_test
}
