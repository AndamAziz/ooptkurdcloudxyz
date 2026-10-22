package xyz.kurdcloud.englishmaster.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledTonalButton
import xyz.kurdcloud.englishmaster.core.designsystem.AppIcons
import xyz.kurdcloud.englishmaster.core.designsystem.BadgeColors
import xyz.kurdcloud.englishmaster.core.designsystem.IconBadge
import xyz.kurdcloud.englishmaster.core.model.CredentialRules
import xyz.kurdcloud.englishmaster.core.model.QuestionUpdateState
import xyz.kurdcloud.englishmaster.core.model.Role as UserRole

/** What the account screen can open or change outside itself. */
data class AccountActions(
    val languageLabel: String,
    val onSwitchLanguage: () -> Unit,
    val onOpenPrivacy: () -> Unit,
    /** Menu label for the in-app update check; null hides it (e.g. Play builds). */
    val checkForUpdatesLabel: String? = null,
    val onCheckForUpdates: () -> Unit = {},
    /** Refreshes every question section at once; null hides the row. */
    val onUpdateQuestions: (() -> Unit)? = null,
)

/**
 * The learner's account: profile, language, privacy policy, sign out and account deletion
 * (deleting from inside the app is a Google Play requirement for apps with sign-up).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    versionName: String,
    pendingAnswers: Int,
    actions: AccountActions,
    onSignedOut: () -> Unit,
    onBack: () -> Unit,
    questionUpdate: QuestionUpdateState = QuestionUpdateState(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var editName by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.deleted) { if (state.deleted) onSignedOut() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(resources.getString(it)); viewModel.messageShown() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.account_back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProfileHeader(state)

                Section(stringResource(R.string.account_section_profile)) {
                    SettingsRow(
                        title = stringResource(R.string.account_name),
                        value = state.name ?: "…",
                        action = stringResource(R.string.account_edit),
                        tag = "account_edit_name",
                        onClick = { editName = true },
                    )
                    HorizontalDivider()
                    SettingsRow(title = stringResource(R.string.account_email), value = state.email)
                }

                actions.onUpdateQuestions?.let { onUpdate ->
                    Section(stringResource(R.string.account_section_questions)) { QuestionsUpdateRow(questionUpdate, onUpdate) }
                }

                Section(stringResource(R.string.account_section_app)) {
                    SettingsRow(
                        title = stringResource(R.string.account_language),
                        value = actions.languageLabel,
                        tag = "account_language",
                        onClick = actions.onSwitchLanguage,
                    )
                    actions.checkForUpdatesLabel?.let { label ->
                        HorizontalDivider()
                        SettingsRow(title = label, tag = "account_updates", onClick = actions.onCheckForUpdates)
                    }
                    HorizontalDivider()
                    SettingsRow(title = stringResource(R.string.account_privacy_policy), tag = "account_privacy", onClick = actions.onOpenPrivacy)
                    HorizontalDivider()
                    SettingsRow(title = stringResource(R.string.account_version), value = versionName)
                }

                OutlinedButton(
                    onClick = { confirmSignOut = true },
                    modifier = Modifier.fillMaxWidth().testTag("account_sign_out"),
                ) { Text(stringResource(R.string.account_sign_out)) }

                DangerZone(state, onDelete = { confirmDelete = true })
            }
        }
    }

    if (editName) {
        var name by rememberSaveable { mutableStateOf(state.name.orEmpty()) }
        var invalid by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text(stringResource(R.string.account_edit_name_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.account_edit_name_body), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(CredentialRules.MAX_DISPLAY_NAME_LENGTH); invalid = false },
                        singleLine = true,
                        isError = invalid,
                        supportingText = if (invalid) ({ Text(stringResource(R.string.account_name_invalid)) }) else null,
                        modifier = Modifier.fillMaxWidth().testTag("account_name_field"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { if (viewModel.rename(name)) editName = false else invalid = true },
                    modifier = Modifier.testTag("account_name_save"),
                ) { Text(stringResource(R.string.account_save)) }
            },
            dismissButton = { TextButton(onClick = { editName = false }) { Text(stringResource(R.string.account_cancel)) } },
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.account_sign_out)) },
            text = {
                Text(
                    if (pendingAnswers > 0) pluralStringResource(R.plurals.account_sign_out_pending, pendingAnswers, pendingAnswers)
                    else stringResource(R.string.account_sign_out_confirm),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; viewModel.signOut(onSignedOut) }, modifier = Modifier.testTag("account_sign_out_confirm")) {
                    Text(stringResource(R.string.account_sign_out))
                }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.account_cancel)) } },
        )
    }

    if (confirmDelete) {
        var understood by rememberSaveable { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!state.deleting) confirmDelete = false },
            title = { Text(stringResource(R.string.account_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.account_delete_body))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Checkbox) { understood = !understood }
                            .testTag("account_delete_understood"),
                    ) {
                        Checkbox(checked = understood, onCheckedChange = null)
                        Text(stringResource(R.string.account_delete_understood), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteAccount() },
                    enabled = understood && !state.deleting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.testTag("account_delete_confirm"),
                ) {
                    if (state.deleting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onError)
                    else Text(stringResource(R.string.account_delete_forever))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, enabled = !state.deleting) { Text(stringResource(R.string.account_cancel)) }
            },
        )
    }
}

@Composable
private fun ProfileHeader(state: AccountUiState) {
    val scheme = MaterialTheme.colorScheme
    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(64.dp).background(Color.White.copy(alpha = 0.22f), CircleShape), contentAlignment = Alignment.Center) {
                Text(state.initials, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(state.name ?: "…", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("account_name"))
                Text(state.email, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                Box(Modifier.background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                    Text(
                        stringResource(
                            when (state.role) {
                                UserRole.ADMIN -> R.string.account_role_admin
                                UserRole.TEACHER -> R.string.account_role_teacher
                                UserRole.STUDENT -> R.string.account_role_learner
                            },
                        ),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.testTag("account_role"),
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth(),
        ) { Column { content() } }
    }
}

/** One settings row: title, optional value, optional action text; clickable when [onClick] is set. */
@Composable
private fun SettingsRow(title: String, value: String? = null, action: String? = null, tag: String? = null, onClick: (() -> Unit)? = null) {
    var modifier = Modifier.fillMaxWidth()
    if (onClick != null) modifier = modifier.clickable(role = Role.Button, onClick = onClick)
    if (tag != null) modifier = modifier.testTag(tag)
    Row(modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        when {
            action != null -> Text(action, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            // The chevron points towards the end of the row, so it flips in the Sorani (RTL) layout.
            onClick != null -> Text(
                if (LocalLayoutDirection.current == LayoutDirection.Rtl) "‹" else "›",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

/** One button that updates the questions of every section; shows progress and the outcome. */
@Composable
private fun QuestionsUpdateRow(update: QuestionUpdateState, onUpdate: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(AppIcons.Refresh, BadgeColors.Update)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.account_update_questions), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                val current = update.current
                val status = when {
                    update.running && current != null && update.total > 0 ->
                        stringResource(R.string.account_update_progress, current.label, update.done + 1, update.total)
                    update.running -> stringResource(R.string.account_update_checking)
                    update.result == QuestionUpdateState.Result.UPDATED ->
                        pluralStringResource(R.plurals.account_update_done, update.updatedQuestions, update.updatedQuestions)
                    update.result == QuestionUpdateState.Result.UP_TO_DATE -> stringResource(R.string.account_update_up_to_date)
                    update.result == QuestionUpdateState.Result.OFFLINE -> stringResource(R.string.account_update_offline)
                    update.result == QuestionUpdateState.Result.FAILED -> stringResource(R.string.account_update_failed)
                    else -> stringResource(R.string.account_update_hint)
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (update.result) {
                        QuestionUpdateState.Result.OFFLINE, QuestionUpdateState.Result.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.testTag("account_update_status"),
                )
            }
        }
        if (update.running) {
            if (update.total > 0) {
                LinearProgressIndicator(progress = { update.done.toFloat() / update.total }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        } else {
            FilledTonalButton(onClick = onUpdate, modifier = Modifier.fillMaxWidth().testTag("account_update_questions")) {
                Text(stringResource(R.string.account_update_now))
            }
        }
    }
}

@Composable
private fun DangerZone(state: AccountUiState, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.account_delete_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error)
            if (state.canDeleteAccount) {
                Text(stringResource(R.string.account_delete_summary), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onDelete, modifier = Modifier.testTag("account_delete")) {
                    Text(stringResource(R.string.account_delete), color = MaterialTheme.colorScheme.error)
                }
            } else {
                // Staff accounts are removed by an admin (the server refuses self-deletion too).
                Text(stringResource(R.string.account_delete_staff), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
