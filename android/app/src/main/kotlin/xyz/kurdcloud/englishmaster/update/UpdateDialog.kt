package xyz.kurdcloud.englishmaster.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import xyz.kurdcloud.englishmaster.R
import xyz.kurdcloud.englishmaster.core.model.AppUpdate
import xyz.kurdcloud.englishmaster.feature.auth.InterfaceLanguage

@Composable
fun UpdateDialog(state: UpdateUiState, language: InterfaceLanguage, viewModel: UpdateViewModel) {
    val mandatory = when (state) {
        is UpdateUiState.Available -> state.update.mandatory
        is UpdateUiState.Downloading -> state.update.mandatory
        is UpdateUiState.NeedsPermission -> state.update.mandatory
        is UpdateUiState.Installing -> state.update.mandatory
        is UpdateUiState.Failed -> state.update.mandatory
        else -> false
    }
    val properties = DialogProperties(dismissOnBackPress = !mandatory, dismissOnClickOutside = !mandatory)
    val later: @Composable (() -> Unit)? = if (mandatory) null else {
        { TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.update_later)) } }
    }

    when (state) {
        UpdateUiState.Hidden -> Unit
        UpdateUiState.Checking -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text(stringResource(R.string.update_checking)) },
            text = { CircularProgressIndicator() },
            confirmButton = {},
        )
        UpdateUiState.UpToDate -> InfoDialog(stringResource(R.string.update_up_to_date), viewModel::dismiss)
        UpdateUiState.LoadingNotes -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text(stringResource(R.string.update_whats_new)) },
            text = { CircularProgressIndicator() },
            confirmButton = {},
        )
        is UpdateUiState.Notes -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            modifier = Modifier.testTag("update_whats_new"),
            title = {
                Text(
                    state.notes?.versionName
                        ?.let { stringResource(R.string.update_whats_new_in, it) }
                        ?: stringResource(R.string.update_whats_new),
                )
            },
            text = {
                Text(
                    state.notes?.forSorani(language == InterfaceLanguage.SORANI)
                        ?: stringResource(R.string.update_whats_new_none),
                    modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.update_ok)) } },
        )
        UpdateUiState.CheckFailed -> InfoDialog(stringResource(R.string.update_check_failed), viewModel::dismiss)
        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            properties = properties,
            modifier = Modifier.testTag("update_available"),
            title = { Text(stringResource(R.string.update_available_title, state.update.versionName)) },
            text = { UpdateBody(state.update, language, if (mandatory) stringResource(R.string.update_mandatory) else null) },
            confirmButton = { TextButton(onClick = viewModel::startUpdate) { Text(stringResource(R.string.update_now)) } },
            dismissButton = later,
        )
        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.update_downloading)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(state.progress * 100).toInt()}%")
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = viewModel::cancelDownload) { Text(stringResource(R.string.update_cancel)) } },
        )
        is UpdateUiState.NeedsPermission -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            properties = properties,
            title = { Text(stringResource(R.string.update_permission_title)) },
            text = { Text(stringResource(R.string.update_permission_body)) },
            confirmButton = { TextButton(onClick = viewModel::openPermissionSettings) { Text(stringResource(R.string.update_open_settings)) } },
            dismissButton = later,
        )
        is UpdateUiState.Installing -> AlertDialog(
            onDismissRequest = {},
            properties = properties,
            title = { Text(stringResource(R.string.update_installing_title)) },
            text = { Text(stringResource(R.string.update_installing_body)) },
            confirmButton = {},
            dismissButton = later,
        )
        is UpdateUiState.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            properties = properties,
            title = { Text(stringResource(R.string.update_failed_title)) },
            text = {
                Text(
                    stringResource(
                        when (state.reason) {
                            UpdateUiState.Reason.NETWORK -> R.string.update_failed_network
                            UpdateUiState.Reason.CORRUPT -> R.string.update_failed_corrupt
                            UpdateUiState.Reason.STORAGE -> R.string.update_failed_storage
                            UpdateUiState.Reason.INCOMPATIBLE -> R.string.update_failed_incompatible
                            UpdateUiState.Reason.INSTALL -> R.string.update_failed_install
                        },
                    ),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::startUpdate) { Text(stringResource(R.string.update_retry)) } },
            dismissButton = later,
        )
    }
}

@Composable
private fun UpdateBody(update: AppUpdate, language: InterfaceLanguage, mandatoryNote: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        mandatoryNote?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val notes = if (language == InterfaceLanguage.SORANI) update.notesCkb ?: update.notesEn else update.notesEn ?: update.notesCkb
        notes?.takeIf { it.isNotBlank() }?.let {
            Text(
                stringResource(R.string.update_whats_new),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            // Release notes run to several lines; let them scroll rather than push the buttons away.
            Text(it, modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()))
        }
        Text(stringResource(R.string.update_size, update.sizeBytes / 1_048_576.0), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InfoDialog(text: String, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    text = { Text(text) },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_ok)) } },
)
