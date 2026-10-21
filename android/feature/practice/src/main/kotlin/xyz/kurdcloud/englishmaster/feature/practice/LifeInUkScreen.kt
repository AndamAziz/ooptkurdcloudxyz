package xyz.kurdcloud.englishmaster.feature.practice

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
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
import xyz.kurdcloud.englishmaster.core.designsystem.OfflineBanner
import xyz.kurdcloud.englishmaster.core.model.LifeInUkChapter
import xyz.kurdcloud.englishmaster.core.model.LifeInUkTest

/** Navigation out of the Life in the UK page. */
data class LifeInUkActions(
    val onPractiseChapter: (LifeInUkChapter) -> Unit = {},
    /** Every Life in the UK question, all chapters mixed. */
    val onPractiseAll: () -> Unit = {},
    val onReviewMistakes: () -> Unit = {},
    val onMockTest: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@StringRes
fun LifeInUkChapter.titleRes(): Int = when (this) {
    LifeInUkChapter.VALUES -> R.string.lituk_ch1
    LifeInUkChapter.WHAT_IS_UK -> R.string.lituk_ch2
    LifeInUkChapter.HISTORY -> R.string.lituk_ch3
    LifeInUkChapter.SOCIETY -> R.string.lituk_ch4
    LifeInUkChapter.GOVERNMENT -> R.string.lituk_ch5
}

@StringRes
private fun LifeInUkChapter.descriptionRes(): Int = when (this) {
    LifeInUkChapter.VALUES -> R.string.lituk_ch1_desc
    LifeInUkChapter.WHAT_IS_UK -> R.string.lituk_ch2_desc
    LifeInUkChapter.HISTORY -> R.string.lituk_ch3_desc
    LifeInUkChapter.SOCIETY -> R.string.lituk_ch4_desc
    LifeInUkChapter.GOVERNMENT -> R.string.lituk_ch5_desc
}

private val UnionBlue = Color(0xFF012169)
private val UnionRed = Color(0xFFC8102E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeInUkScreen(viewModel: LifeInUkViewModel, actions: LifeInUkActions) {
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
                title = { Text(stringResource(R.string.lituk_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { TextButton(onClick = actions.onBack) { Text(stringResource(R.string.practice_exit)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!state.isOnline) OfflineBanner()
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().testTag("lituk_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { HeaderCard() }
                item { PackCard(state, viewModel::download) }
                if (state.pack != null) {
                    item { MockTestCard(state, actions.onMockTest) }
                    item {
                        Text(stringResource(R.string.lituk_chapters_header), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    items(LifeInUkChapter.entries, key = { it.name }) { chapter ->
                        ChapterCard(chapter, state.chapters[chapter] ?: 0) { actions.onPractiseChapter(chapter) }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = actions.onPractiseAll, modifier = Modifier.weight(1f).testTag("lituk_practise_all")) {
                                Text(stringResource(R.string.lituk_practise_all))
                            }
                            if (state.mistakes > 0) {
                                OutlinedButton(onClick = actions.onReviewMistakes, modifier = Modifier.weight(1f).testTag("lituk_mistakes")) {
                                    Text(pluralStringResource(R.plurals.lituk_review_mistakes, state.mistakes, state.mistakes))
                                }
                            }
                        }
                    }
                }
                item { OfficialLinks() }
                item {
                    Text(
                        stringResource(R.string.lituk_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderCard() {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(UnionBlue, Color(0xFF1D3F94), UnionRed)))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("🇬🇧", style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.lituk_header_title), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.lituk_header_body, LifeInUkTest.QUESTIONS, LifeInUkTest.MINUTES, LifeInUkTest.PASS_MARK),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
    }
}

@Composable
private fun PackCard(state: LifeInUkUiState, onDownload: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val downloading = state.downloading
            when {
                downloading != null -> {
                    Text(pluralStringResource(R.plurals.practice_downloading, downloading, downloading))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                state.pack == null -> {
                    Text(stringResource(R.string.lituk_download_body), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onDownload, modifier = Modifier.testTag("lituk_download")) { Text(stringResource(R.string.lituk_download)) }
                }
                else -> {
                    Text(
                        pluralStringResource(R.plurals.lituk_pack_ready, state.questionCount, state.questionCount),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("lituk_pack_count"),
                    )
                    state.progress?.let { p ->
                        val percent = if (p.answered == 0) 0 else p.correct * 100 / p.answered
                        Text(
                            pluralStringResource(R.plurals.lituk_progress, p.answered, p.answered, percent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDownload, modifier = Modifier.testTag("lituk_update")) { Text(stringResource(R.string.practice_update)) }
                }
            }
        }
    }
}

@Composable
private fun MockTestCard(state: LifeInUkUiState, onStart: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.lituk_mock_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.lituk_mock_body, LifeInUkTest.QUESTIONS, LifeInUkTest.MINUTES, LifeInUkTest.PASS_MARK),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.canTakeMockTest) {
                Button(onClick = onStart, modifier = Modifier.testTag("lituk_mock_start")) { Text(stringResource(R.string.lituk_mock_start)) }
            } else {
                Text(stringResource(R.string.lituk_mock_not_enough, LifeInUkTest.QUESTIONS), color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun ChapterCard(chapter: LifeInUkChapter, count: Int, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().clickable(enabled = count > 0, onClick = onClick).testTag("lituk_chapter_${chapter.number}"),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(UnionBlue, CircleShape), contentAlignment = Alignment.Center) {
                Text("${chapter.number}", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(stringResource(chapter.titleRes()), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(stringResource(chapter.descriptionRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                pluralStringResource(R.plurals.topic_questions, count, count),
                style = MaterialTheme.typography.labelMedium,
                color = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OfficialLinks() {
    val uri = LocalUriHandler.current
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.lituk_official_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.lituk_official_body), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { uri.openUri(LifeInUkTest.OFFICIAL_TEST_URL) }, modifier = Modifier.testTag("lituk_link_book")) {
                Text(stringResource(R.string.lituk_link_book))
            }
            TextButton(onClick = { uri.openUri(LifeInUkTest.TEST_DAY_URL) }) { Text(stringResource(R.string.lituk_link_test_day)) }
            TextButton(onClick = { uri.openUri(LifeInUkTest.HANDBOOK_URL) }) { Text(stringResource(R.string.lituk_link_handbook)) }
        }
    }
}
