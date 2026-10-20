package xyz.kurdcloud.englishmaster.feature.admin

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.kurdcloud.englishmaster.core.designsystem.LoadingState
import xyz.kurdcloud.englishmaster.core.designsystem.QuestionMediaView
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.MediaType
import xyz.kurdcloud.englishmaster.core.model.optionLetter

private val SKILLS = listOf("grammar" to R.string.admin_skill_grammar, "vocabulary" to R.string.admin_skill_vocabulary, "reading" to R.string.admin_skill_reading)

/** Reads a picked file (bounded by [limit]); null if it cannot be read or is larger than the limit. */
private suspend fun readPicked(context: Context, uri: Uri, type: MediaType, limit: Int): PickedMedia? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: return@runCatching null
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        // Read at most limit + 1 bytes so an oversized file is detected without loading it all.
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (out.size() <= limit) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        } ?: return@runCatching null
        PickedMedia(type, mime, bytes, name)
    }.getOrNull()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddQuestionScreen(viewModel: AddQuestionViewModel, onDone: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            viewModel.pickMedia(readPicked(context, uri, MediaType.IMAGE, AddQuestionViewModel.MAX_IMAGE) ?: return@launch)
        }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            viewModel.pickMedia(readPicked(context, uri, MediaType.VIDEO, AddQuestionViewModel.MAX_VIDEO) ?: return@launch)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.editing) R.string.admin_edit_title else R.string.admin_add_title, state.level.label))
                },
                navigationIcon = { TextButton(onClick = onDone) { Text(stringResource(R.string.admin_cancel)) } },
            )
        },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.admin_add_level), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CefrLevel.practiceLevels.forEach { level ->
                        FilterChip(selected = state.level == level, onClick = { viewModel.update { it.copy(level = level) } }, label = { Text(level.label) })
                    }
                }
                Text(stringResource(R.string.admin_add_skill), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SKILLS.forEach { (skill, label) ->
                        FilterChip(selected = state.skill == skill, onClick = { viewModel.update { it.copy(skill = skill) } }, label = { Text(stringResource(label)) })
                    }
                }
                // Question content is English: edit it left-to-right inside the Sorani UI.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.passage, onValueChange = { v -> viewModel.update { it.copy(passage = v.take(3000)) } },
                            label = { Text(stringResource(R.string.admin_add_passage)) }, minLines = 2, maxLines = 8,
                            modifier = Modifier.fillMaxWidth().testTag("add_passage"),
                        )
                        OutlinedTextField(
                            value = state.prompt, onValueChange = { v -> viewModel.update { it.copy(prompt = v.take(2000)) } },
                            label = { Text(stringResource(R.string.admin_add_prompt)) }, minLines = 2,
                            modifier = Modifier.fillMaxWidth().testTag("add_prompt"),
                        )
                        Text(stringResource(R.string.admin_add_options_hint), style = MaterialTheme.typography.bodySmall)
                        state.options.forEachIndexed { i, option ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = state.correctIndex == i,
                                    onClick = { viewModel.update { it.copy(correctIndex = i) } },
                                    modifier = Modifier.testTag("add_correct_$i"),
                                )
                                OutlinedTextField(
                                    value = option, onValueChange = { viewModel.setOption(i, it.take(300)) },
                                    label = { Text(optionLetter(i)) }, singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("add_option_$i"),
                                )
                            }
                        }
                        OutlinedTextField(
                            value = state.explanation, onValueChange = { v -> viewModel.update { it.copy(explanation = v.take(2000)) } },
                            label = { Text(stringResource(R.string.admin_add_explanation)) }, minLines = 2,
                            modifier = Modifier.fillMaxWidth().testTag("add_explanation"),
                        )
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.admin_add_media), style = MaterialTheme.typography.titleSmall)
                        val media = state.media
                        val existing = state.existingMedia
                        if (media == null && existing != null) {
                            // The question's current picture/video: keep it, replace it or remove it.
                            QuestionMediaView(existing, stringResource(R.string.admin_media_unavailable))
                            OutlinedTextField(
                                value = state.caption, onValueChange = { v -> viewModel.update { it.copy(caption = v.take(300)) } },
                                label = { Text(stringResource(R.string.admin_add_caption)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { pickImage.launch("image/*") }) { Text(stringResource(R.string.admin_edit_replace_picture)) }
                                OutlinedButton(onClick = { pickVideo.launch("video/mp4") }) { Text(stringResource(R.string.admin_edit_replace_video)) }
                                TextButton(onClick = viewModel::removeExistingMedia, modifier = Modifier.testTag("edit_remove_media")) {
                                    Text(stringResource(R.string.admin_add_remove_media), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        } else if (media == null) {
                            Text(stringResource(R.string.admin_add_media_hint), style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { pickImage.launch("image/*") }) { Text(stringResource(R.string.admin_add_picture)) }
                                OutlinedButton(onClick = { pickVideo.launch("video/mp4") }) { Text(stringResource(R.string.admin_add_video)) }
                            }
                        } else {
                            Text(
                                stringResource(
                                    if (media.type == MediaType.VIDEO) R.string.admin_add_video_chosen else R.string.admin_add_picture_chosen,
                                    media.name, media.bytes.size / 1024,
                                ),
                            )
                            OutlinedTextField(
                                value = state.caption, onValueChange = { v -> viewModel.update { it.copy(caption = v.take(300)) } },
                                label = { Text(stringResource(R.string.admin_add_caption)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            )
                            TextButton(onClick = { viewModel.pickMedia(null) }) { Text(stringResource(R.string.admin_add_remove_media)) }
                        }
                    }
                }
                if (state.editing) {
                    // Editing keeps the question's review and publish state.
                    Text(stringResource(R.string.admin_edit_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.admin_add_publish_now), modifier = Modifier.weight(1f))
                        Switch(checked = state.publish, onCheckedChange = { v -> viewModel.update { it.copy(publish = v) } })
                    }
                }
                state.error?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("add_error"))
                }
                Button(onClick = viewModel::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth().testTag("add_save")) {
                    Text(
                        stringResource(
                            when {
                                state.saving -> R.string.admin_add_saving
                                state.editing -> R.string.admin_edit_save
                                else -> R.string.admin_add_save
                            },
                        ),
                    )
                }
            }
        }
    }
}
