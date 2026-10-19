package xyz.kurdcloud.englishmaster.feature.practice

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.kurdcloud.englishmaster.core.designsystem.OfflineBanner
import xyz.kurdcloud.englishmaster.core.model.CefrLevel

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    switchLanguageLabel: String,
    onSwitchLanguage: () -> Unit,
    onStartPractice: (CefrLevel) -> Unit,
    onSignOut: () -> Unit,
    onOpenStaff: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf<CefrLevel?>(null) }

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
                title = { Text(stringResource(R.string.practice_home_title)) },
                actions = {
                    Box {
                        TextButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("home_menu")) {
                            Text(stringResource(R.string.practice_menu))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (state.staffAccess.any) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.practice_staff_tools)) },
                                    onClick = { menuOpen = false; onOpenStaff() },
                                    modifier = Modifier.testTag("menu_staff"),
                                )
                            }
                            DropdownMenuItem(text = { Text(switchLanguageLabel) }, onClick = { menuOpen = false; onSwitchLanguage() })
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.pendingCount > 0) {
                    item { PendingCard(state.pendingCount, state.isOnline, state.syncing, viewModel::syncNow) }
                }
                item {
                    Text(stringResource(R.string.practice_levels_header), style = MaterialTheme.typography.titleMedium)
                }
                items(state.levels, key = { it.level.name }) { row ->
                    LevelCard(
                        row = row,
                        onDownload = { viewModel.download(row.level) },
                        onCancel = { viewModel.cancelDownload(row.level) },
                        onRemove = { confirmRemove = row.level },
                        onPractice = { onStartPractice(row.level) },
                    )
                }
                item {
                    Text(stringResource(R.string.practice_cefr_note), style = MaterialTheme.typography.bodySmall)
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
private fun PendingCard(count: Int, online: Boolean, syncing: Boolean, onSync: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.testTag("pending_card")) {
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
private fun LevelCard(row: LevelRow, onDownload: () -> Unit, onCancel: () -> Unit, onRemove: () -> Unit, onPractice: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("level_${row.level.name}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.level.label, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(row.level.descriptionRes()), style = MaterialTheme.typography.bodyMedium)
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
                    row.progress?.let {
                        Text(stringResource(R.string.practice_progress, it.answered, it.correct), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onPractice, enabled = pack.questionCount > 0) { Text(stringResource(R.string.practice_start)) }
                        OutlinedButton(onClick = onDownload) { Text(stringResource(R.string.practice_update)) }
                        TextButton(onClick = onRemove) { Text(stringResource(R.string.practice_remove)) }
                    }
                }
            }
        }
    }
}
