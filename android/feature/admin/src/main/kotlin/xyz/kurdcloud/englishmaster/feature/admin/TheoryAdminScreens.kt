package xyz.kurdcloud.englishmaster.feature.admin

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import xyz.kurdcloud.englishmaster.core.designsystem.EmptyState
import xyz.kurdcloud.englishmaster.core.designsystem.LoadingState
import xyz.kurdcloud.englishmaster.core.model.MediaType
import xyz.kurdcloud.englishmaster.core.model.TheorySection

/** Shows a [StaffMessage] in [snackbar] once, then clears it. */
@Composable
internal fun StaffMessageEffect(message: StaffMessage?, snackbar: SnackbarHostState, onShown: () -> Unit) {
    val resources = LocalResources.current
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(
                if (message.plural) resources.getQuantityString(message.text, message.arg ?: 0, message.arg ?: 0)
                else resources.getString(message.text),
            )
            onShown()
        }
    }
}

/**
 * The driving theory chapters, for teachers and admins: what each one publishes, a button to
 * publish its imported drafts, and a way into the add-question form.
 */
@Composable
internal fun TheoryTab(vm: TheoryAdminViewModel, snackbar: SnackbarHostState, onAddQuestion: (TheorySection) -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    StaffMessageEffect(state.message, snackbar, vm::messageShown)
    when {
        state.loading -> LoadingState()
        state.sections.isEmpty() -> EmptyState(stringResource(R.string.admin_theory_none), actionLabel = stringResource(R.string.admin_retry), onAction = vm::refresh)
        else -> LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().testTag("admin_theory_list"),
        ) {
            item {
                Text(stringResource(R.string.admin_theory_intro), style = MaterialTheme.typography.bodyMedium)
            }
            items(state.sections, key = { it.slug }) { section ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(section.nameEn, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            pluralStringResource(R.plurals.admin_theory_published_count, section.published, section.published),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onAddQuestion(section) },
                                modifier = Modifier.testTag("admin_theory_add_${section.slug}"),
                            ) { Text(stringResource(R.string.admin_theory_add)) }
                            OutlinedButton(
                                onClick = { vm.publishDrafts(section.slug) },
                                enabled = state.publishing == null,
                                modifier = Modifier.testTag("admin_theory_publish_${section.slug}"),
                            ) { Text(stringResource(R.string.admin_theory_publish_drafts)) }
                        }
                    }
                }
            }
        }
    }
}

/** The form a teacher fills in to add one theory question. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTheoryQuestionScreen(viewModel: AddTheoryQuestionViewModel, sectionName: String, onDone: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            viewModel.pickMedia(readPickedImage(context, uri) ?: return@launch)
        }
    }
    val resources = LocalResources.current
    LaunchedEffect(state.savedCount) {
        if (state.savedCount > 0) snackbar.showSnackbar(resources.getString(R.string.admin_theory_saved))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_theory_add_title, sectionName)) },
                navigationIcon = { TextButton(onClick = onDone) { Text(stringResource(R.string.admin_cancel)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.admin_theory_add_hint), style = MaterialTheme.typography.bodySmall)
                // The question and its answer are English: edit them left-to-right in the Sorani UI.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.prompt,
                            onValueChange = { v -> viewModel.update { it.copy(prompt = v.take(2000)) } },
                            label = { Text(stringResource(R.string.admin_add_prompt)) },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth().testTag("theory_prompt"),
                        )
                        OutlinedTextField(
                            value = state.answer,
                            onValueChange = { v -> viewModel.update { it.copy(answer = v.take(1000)) } },
                            label = { Text(stringResource(R.string.admin_theory_answer)) },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth().testTag("theory_answer"),
                        )
                        OutlinedTextField(
                            value = state.explanation,
                            onValueChange = { v -> viewModel.update { it.copy(explanation = v.take(2000)) } },
                            label = { Text(stringResource(R.string.admin_add_explanation)) },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth().testTag("theory_explanation"),
                        )
                    }
                }
                OutlinedTextField(
                    value = state.promptKu,
                    onValueChange = { v -> viewModel.update { it.copy(promptKu = v.take(3000)) } },
                    label = { Text(stringResource(R.string.admin_theory_prompt_ku)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("theory_prompt_ku"),
                )
                OutlinedTextField(
                    value = state.answerKu,
                    onValueChange = { v -> viewModel.update { it.copy(answerKu = v.take(1500)) } },
                    label = { Text(stringResource(R.string.admin_theory_answer_ku)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("theory_answer_ku"),
                )
                OutlinedTextField(
                    value = state.explanationKu,
                    onValueChange = { v -> viewModel.update { it.copy(explanationKu = v.take(3000)) } },
                    label = { Text(stringResource(R.string.admin_theory_explanation_ku)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.admin_theory_picture), style = MaterialTheme.typography.titleSmall)
                        val media = state.media
                        if (media == null) {
                            Text(stringResource(R.string.admin_theory_picture_hint), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { pickImage.launch("image/*") }, modifier = Modifier.testTag("theory_pick_image")) {
                                Text(stringResource(R.string.admin_add_picture))
                            }
                        } else {
                            Text(stringResource(R.string.admin_add_picture_chosen, media.name, media.bytes.size / 1024))
                            TextButton(onClick = { viewModel.pickMedia(null) }) { Text(stringResource(R.string.admin_add_remove_media)) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.admin_add_publish_now), modifier = Modifier.weight(1f))
                    Switch(checked = state.publish, onCheckedChange = { v -> viewModel.update { it.copy(publish = v) } })
                }
                state.error?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("theory_error"))
                }
                Button(
                    onClick = viewModel::save,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().testTag("theory_save"),
                ) {
                    Text(stringResource(if (state.saving) R.string.admin_add_saving else R.string.admin_theory_save))
                }
                if (state.savedCount > 0) {
                    Text(
                        pluralStringResource(R.plurals.admin_theory_saved_count, state.savedCount, state.savedCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Reads a picked picture, bounded by the staff upload limit. */
private suspend fun readPickedImage(context: Context, uri: Uri): PickedMedia? =
    readPicked(context, uri, MediaType.IMAGE, AddQuestionViewModel.MAX_IMAGE)
