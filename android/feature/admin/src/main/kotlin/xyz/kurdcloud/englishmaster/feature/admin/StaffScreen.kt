package xyz.kurdcloud.englishmaster.feature.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.kurdcloud.englishmaster.core.designsystem.AppIcons
import xyz.kurdcloud.englishmaster.core.designsystem.EmptyState
import xyz.kurdcloud.englishmaster.core.designsystem.ErrorState
import xyz.kurdcloud.englishmaster.core.designsystem.LoadingState
import xyz.kurdcloud.englishmaster.core.designsystem.PassageCard
import xyz.kurdcloud.englishmaster.core.designsystem.QuestionMediaView
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.CertificateRequest
import xyz.kurdcloud.englishmaster.core.model.CertificateRequestStatus
import xyz.kurdcloud.englishmaster.core.model.Role
import xyz.kurdcloud.englishmaster.core.model.StaffAccess
import xyz.kurdcloud.englishmaster.core.model.StaffQuestion
import xyz.kurdcloud.englishmaster.core.model.StaffUser
import xyz.kurdcloud.englishmaster.core.model.optionLetter

private enum class StaffTab(val title: Int) {
    CERTIFICATES(R.string.admin_tab_certificates),
    USERS(R.string.admin_tab_users),
    QUESTIONS(R.string.admin_tab_questions),
    ACTIVITY(R.string.admin_tab_activity),
}

/**
 * Staff section. Tabs follow [access]; the server re-checks every call, so a hidden tab
 * is a convenience, not a security boundary. View models are created lazily per tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffScreen(
    access: StaffAccess,
    currentUserId: String,
    usersViewModel: @Composable () -> UsersViewModel,
    questionsViewModel: @Composable () -> QuestionsViewModel,
    activityViewModel: @Composable () -> ActivityViewModel,
    onBack: () -> Unit,
    onAddQuestion: (CefrLevel) -> Unit = {},
    certificatesViewModel: (@Composable () -> CertificateRequestsViewModel)? = null,
    onEditQuestion: (CefrLevel, String) -> Unit = { _, _ -> },
) {
    val tabs = buildList {
        // Pending certificate approvals come first: they are what learners are waiting for.
        if (access.canApproveCertificates && certificatesViewModel != null) add(StaffTab.CERTIFICATES)
        if (access.canSeeUsers) add(StaffTab.USERS)
        if (access.canReviewQuestions) add(StaffTab.QUESTIONS)
        if (access.canManageUsers) add(StaffTab.ACTIVITY)
    }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.admin_back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (tabs.isEmpty()) {
                EmptyState(stringResource(R.string.admin_no_access))
                return@Column
            }
            if (tabs.size > 1) {
                val tabContent: @Composable () -> Unit = {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selected == index,
                            onClick = { selected = index },
                            text = { Text(stringResource(tab.title), maxLines = 1) },
                            modifier = Modifier.testTag("tab_${tab.name.lowercase()}"),
                        )
                    }
                }
                // Four tabs do not fit side by side on a phone in Sorani: let them scroll.
                if (tabs.size > 3) {
                    PrimaryScrollableTabRow(selectedTabIndex = selected.coerceIn(tabs.indices), edgePadding = 8.dp) { tabContent() }
                } else {
                    PrimaryTabRow(selectedTabIndex = selected.coerceIn(tabs.indices)) { tabContent() }
                }
            }
            when (tabs[selected.coerceIn(tabs.indices)]) {
                StaffTab.CERTIFICATES -> CertificatesTab(certificatesViewModel!!(), currentUserId, snackbar)
                StaffTab.USERS -> UsersTab(usersViewModel(), currentUserId, access.canManageUsers, snackbar)
                StaffTab.QUESTIONS -> QuestionsTab(questionsViewModel(), snackbar, onAddQuestion, onEditQuestion, canDelete = access.canManageUsers)
                StaffTab.ACTIVITY -> ActivityTab(activityViewModel())
            }
        }
    }
}

@Composable
private fun MessageEffect(message: Int?, snackbar: SnackbarHostState, onShown: () -> Unit) {
    val resources = LocalResources.current
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(resources.getString(message))
            onShown()
        }
    }
}

private data class PendingChange(val title: Int, val user: StaffUser, val apply: () -> Unit)

@Composable
private fun UsersTab(vm: UsersViewModel, currentUserId: String, canManage: Boolean, snackbar: SnackbarHostState) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    MessageEffect(state.message, snackbar, vm::messageShown)
    var confirm by remember { mutableStateOf<PendingChange?>(null) }
    var rename by remember { mutableStateOf<StaffUser?>(null) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            label = { Text(stringResource(R.string.admin_search_users)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.load() }),
            trailingIcon = { TextButton(onClick = vm::load) { Text(stringResource(R.string.admin_search)) } },
            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("user_search"),
        )
        when {
            state.loading -> LoadingState()
            state.error != null -> ErrorState(stringResource(state.error!!), vm::load)
            state.users.isEmpty() -> EmptyState(stringResource(R.string.admin_no_users))
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag("users_list"),
            ) {
                items(state.users, key = { it.userId }) { user ->
                    val self = user.userId == currentUserId
                    UserCard(
                        user = user,
                        self = self,
                        canManage = canManage,
                        busy = state.busyUserId == user.userId,
                        onRename = { rename = user },
                        onTeacher = { granted -> vm.setRole(user, Role.TEACHER, granted) },
                        onAdmin = { granted ->
                            confirm = PendingChange(if (granted) R.string.admin_confirm_make_admin else R.string.admin_confirm_remove_admin, user) {
                                vm.setRole(user, Role.ADMIN, granted)
                            }
                        },
                        onSuspend = { suspended ->
                            confirm = PendingChange(if (suspended) R.string.admin_confirm_suspend else R.string.admin_confirm_activate, user) {
                                vm.setSuspended(user, suspended)
                            }
                        },
                    )
                }
            }
        }
    }

    rename?.let { user ->
        RenameDialog(user, onDismiss = { rename = null }) { name ->
            rename = null
            vm.setDisplayName(user, name)
        }
    }

    confirm?.let { change ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(change.title)) },
            text = { Text("${change.user.displayName} — ${change.user.email}") },
            confirmButton = { TextButton(onClick = { change.apply(); confirm = null }) { Text(stringResource(R.string.admin_confirm)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.admin_cancel)) } },
        )
    }
}

@Composable
private fun RenameDialog(user: StaffUser, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable(user.userId) { mutableStateOf(user.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.admin_rename_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.admin_rename_body, user.email))
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(80) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_field"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank(), modifier = Modifier.testTag("rename_save")) {
                Text(stringResource(R.string.admin_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_cancel)) } },
    )
}

@Composable
private fun UserCard(
    user: StaffUser,
    self: Boolean,
    canManage: Boolean,
    busy: Boolean,
    onRename: () -> Unit,
    onTeacher: (Boolean) -> Unit,
    onAdmin: (Boolean) -> Unit,
    onSuspend: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth().widthIn(max = 720.dp).testTag("user_${user.email}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(user.displayName + if (self) " " + stringResource(R.string.admin_you) else "", style = MaterialTheme.typography.titleMedium)
            Text(user.email, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                user.roles.sortedBy { it.ordinal }.forEach { AssistChip(onClick = {}, label = { Text(stringResource(it.label())) }) }
                if (user.suspended) AssistChip(onClick = {}, label = { Text(stringResource(R.string.admin_suspended)) })
            }
            Text(stringResource(R.string.admin_user_progress, user.answered, user.correct), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRename, enabled = !busy, modifier = Modifier.testTag("rename_${user.email}")) {
                    Text(stringResource(R.string.admin_rename))
                }
                if (!canManage) return@FlowRow
                val teacher = Role.TEACHER in user.roles
                val admin = Role.ADMIN in user.roles
                OutlinedButton(onClick = { onTeacher(!teacher) }, enabled = !busy) {
                    Text(stringResource(if (teacher) R.string.admin_remove_teacher else R.string.admin_make_teacher))
                }
                OutlinedButton(onClick = { onAdmin(!admin) }, enabled = !busy && !(self && admin)) {
                    Text(stringResource(if (admin) R.string.admin_remove_admin else R.string.admin_make_admin))
                }
                OutlinedButton(onClick = { onSuspend(!user.suspended) }, enabled = !busy && !self) {
                    Text(stringResource(if (user.suspended) R.string.admin_activate else R.string.admin_suspend))
                }
            }
        }
    }
}

private fun Role.label(): Int = when (this) {
    Role.STUDENT -> R.string.admin_role_student
    Role.TEACHER -> R.string.admin_role_teacher
    Role.ADMIN -> R.string.admin_role_admin
}

@Composable
private fun QuestionsTab(
    vm: QuestionsViewModel,
    snackbar: SnackbarHostState,
    onAddQuestion: (CefrLevel) -> Unit,
    onEditQuestion: (CefrLevel, String) -> Unit,
    /** Deleting questions is admin-only (the server checks it too). */
    canDelete: Boolean,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    MessageEffect(state.message, snackbar, vm::messageShown)
    var confirmPublish by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<StaffQuestion?>(null) }
    // Back from "Add question": show the new item.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (!state.loading) vm.load(state.level) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CefrLevel.authoringLevels.forEach { level ->
                FilterChip(selected = state.level == level, onClick = { vm.load(level) }, label = { Text(level.label) })
            }
        }
        val approved = state.questions.count { it.reviewStatus == "approved" }
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onAddQuestion(state.level) }, modifier = Modifier.testTag("add_question")) {
                Text(stringResource(R.string.admin_add_question))
            }
            if (state.drafts > 0) {
                OutlinedButton(onClick = { confirmPublish = true }, enabled = !state.publishing, modifier = Modifier.testTag("publish_drafts")) {
                    Text(stringResource(R.string.admin_publish_drafts, state.drafts))
                }
            }
        }
        if (!state.loading && state.error == null) {
            Text(
                stringResource(R.string.admin_review_summary, approved, state.questions.size),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (confirmPublish) {
            AlertDialog(
                onDismissRequest = { confirmPublish = false },
                title = { Text(pluralStringResource(R.plurals.admin_publish_drafts_title, state.drafts, state.drafts, state.level.label)) },
                text = { Text(stringResource(R.string.admin_publish_drafts_body)) },
                confirmButton = {
                    TextButton(onClick = { confirmPublish = false; vm.publishAllDrafts() }, modifier = Modifier.testTag("publish_drafts_confirm")) {
                        Text(stringResource(R.string.admin_confirm))
                    }
                },
                dismissButton = { TextButton(onClick = { confirmPublish = false }) { Text(stringResource(R.string.admin_cancel)) } },
            )
        }
        confirmDelete?.let { q ->
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                icon = { Icon(AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(stringResource(R.string.admin_delete_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text("“${q.prompt}”", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 3)
                        }
                        Text(stringResource(R.string.admin_delete_body))
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { vm.delete(q); confirmDelete = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier.testTag("delete_confirm"),
                    ) { Text(stringResource(R.string.admin_delete)) }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.admin_cancel)) } },
            )
        }
        when {
            state.loading -> LoadingState()
            state.error != null -> ErrorState(stringResource(state.error!!), { vm.load(state.level) })
            state.questions.isEmpty() -> EmptyState(stringResource(R.string.admin_no_questions))
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.questions, key = { it.id }) { q ->
                    QuestionCard(
                        q,
                        busy = state.busyQuestionId == q.id,
                        onApprove = { vm.setApproved(q, it) },
                        onRetire = { vm.setRetired(q, it) },
                        onEdit = { onEditQuestion(q.level, q.id) },
                        onDelete = if (canDelete) ({ confirmDelete = q }) else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(
    q: StaffQuestion,
    busy: Boolean,
    onApprove: (Boolean) -> Unit,
    onRetire: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val approved = q.reviewStatus == "approved"
    val retired = q.status == "retired"
    val draft = q.status == "draft"
    Card(Modifier.fillMaxWidth().testTag("question_${q.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = {}, label = { Text(stringResource(if (approved) R.string.admin_approved else R.string.admin_unreviewed)) })
                    if (retired) AssistChip(onClick = {}, label = { Text(stringResource(R.string.admin_retired)) })
                    if (draft) AssistChip(onClick = {}, label = { Text(stringResource(R.string.admin_draft)) })
                }
                // Edit (teachers and admins) and delete (admins) as compact round actions.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(onClick = onEdit, enabled = !busy, modifier = Modifier.testTag("edit_${q.id}")) {
                        Icon(AppIcons.Edit, contentDescription = stringResource(R.string.admin_edit), modifier = Modifier.size(20.dp))
                    }
                    if (onDelete != null) {
                        FilledTonalIconButton(
                            onClick = onDelete,
                            enabled = !busy,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                            modifier = Modifier.testTag("delete_${q.id}"),
                        ) {
                            Icon(AppIcons.Delete, contentDescription = stringResource(R.string.admin_delete), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            // Learning content is English: keep it left-to-right in the Sorani UI.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    q.passage?.let { PassageCard(it) }
                    q.media?.let { QuestionMediaView(it, stringResource(R.string.admin_media_unavailable)) }
                    Text(q.prompt, style = MaterialTheme.typography.titleMedium)
                    q.options.forEachIndexed { i, option ->
                        val letter = optionLetter(i)
                        val key = letter == q.correctOption
                        Text(
                            (if (key) "✓ " else "   ") + "$letter. $option",
                            fontWeight = if (key) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    q.explanation?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                OutlinedButton(onClick = { onApprove(!approved) }, enabled = !busy, modifier = Modifier.testTag("approve_${q.id}")) {
                    Text(stringResource(if (approved) R.string.admin_mark_unreviewed else if (draft) R.string.admin_approve_publish else R.string.admin_approve))
                }
                OutlinedButton(onClick = { onRetire(!retired) }, enabled = !busy) {
                    Text(stringResource(if (retired) R.string.admin_publish else R.string.admin_retire))
                }
            }
        }
    }
}

@Composable
private fun CertificatesTab(vm: CertificateRequestsViewModel, currentUserId: String, snackbar: SnackbarHostState) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    MessageEffect(state.message, snackbar, vm::messageShown)
    var approve by remember { mutableStateOf<CertificateRequest?>(null) }
    var reject by remember { mutableStateOf<CertificateRequest?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (!state.loading) vm.load() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CertificateRequestStatus.entries.forEach { f ->
                FilterChip(
                    selected = state.filter == f,
                    onClick = { vm.setFilter(f) },
                    label = { Text(stringResource(f.label())) },
                    modifier = Modifier.testTag("cert_filter_${f.apiValue}"),
                )
            }
        }
        when {
            state.loading -> LoadingState()
            state.error != null -> ErrorState(stringResource(state.error!!), vm::load)
            state.requests.isEmpty() -> EmptyState(
                stringResource(if (state.filter == CertificateRequestStatus.PENDING) R.string.cert_none_pending else R.string.cert_none),
            )
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.filter == CertificateRequestStatus.PENDING) {
                    item {
                        Text(stringResource(R.string.cert_pending_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(state.requests, key = { it.id }) { request ->
                    CertificateRequestCard(
                        request = request,
                        own = request.userId == currentUserId,
                        busy = state.busyId == request.id,
                        onApprove = { approve = request },
                        onReject = { reject = request },
                    )
                }
            }
        }
    }

    approve?.let { r ->
        AlertDialog(
            onDismissRequest = { approve = null },
            title = { Text(stringResource(R.string.cert_approve_title, r.level.label)) },
            text = { Text(stringResource(R.string.cert_approve_body, r.displayName.ifBlank { r.email }, r.score, r.total)) },
            confirmButton = {
                TextButton(onClick = { vm.approve(r); approve = null }, modifier = Modifier.testTag("cert_confirm_approve")) {
                    Text(stringResource(R.string.cert_approve))
                }
            },
            dismissButton = { TextButton(onClick = { approve = null }) { Text(stringResource(R.string.admin_cancel)) } },
        )
    }
    reject?.let { r ->
        var note by rememberSaveable(r.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reject = null },
            title = { Text(stringResource(R.string.cert_reject_title, r.level.label)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.cert_reject_body, r.displayName.ifBlank { r.email }))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(300) },
                        label = { Text(stringResource(R.string.cert_reject_note)) },
                        modifier = Modifier.fillMaxWidth().testTag("cert_reject_note"),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.reject(r, note); reject = null }, modifier = Modifier.testTag("cert_confirm_reject")) {
                    Text(stringResource(R.string.cert_reject), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { reject = null }) { Text(stringResource(R.string.admin_cancel)) } },
        )
    }
}

@Composable
private fun CertificateRequestCard(request: CertificateRequest, own: Boolean, busy: Boolean, onApprove: () -> Unit, onReject: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("cert_request_${request.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(request.displayName.ifBlank { request.email }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(request.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(onClick = {}, label = { Text(request.level.label, fontWeight = FontWeight.Bold) })
            }
            Text(
                stringResource(R.string.cert_request_detail, request.score, request.total,
                    if (request.total == 0) 0 else request.score * 100 / request.total, request.requestedAt.take(16).replace('T', ' ')),
                style = MaterialTheme.typography.bodyMedium,
            )
            val reviewer = request.reviewerName
            if (request.status != CertificateRequestStatus.PENDING && reviewer != null) {
                Text(
                    stringResource(R.string.cert_reviewed_by, reviewer, request.reviewedAt.orEmpty().take(16).replace('T', ' ')),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (request.status == CertificateRequestStatus.PENDING) {
                if (own) {
                    Text(stringResource(R.string.cert_own_request), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onApprove, enabled = !busy, modifier = Modifier.testTag("cert_approve_${request.id}")) {
                            Text(stringResource(R.string.cert_approve))
                        }
                        OutlinedButton(onClick = onReject, enabled = !busy, modifier = Modifier.testTag("cert_reject_${request.id}")) {
                            Text(stringResource(R.string.cert_reject))
                        }
                    }
                }
            }
        }
    }
}

private fun CertificateRequestStatus.label(): Int = when (this) {
    CertificateRequestStatus.PENDING -> R.string.cert_filter_pending
    CertificateRequestStatus.APPROVED -> R.string.cert_filter_approved
    CertificateRequestStatus.REJECTED -> R.string.cert_filter_rejected
}

@Composable
private fun ActivityTab(vm: ActivityViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    when {
        state.loading -> LoadingState()
        state.error != null -> ErrorState(stringResource(state.error!!), vm::load)
        state.entries.isEmpty() -> EmptyState(stringResource(R.string.admin_no_activity))
        else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                TextButton(onClick = vm::load) { Text(stringResource(R.string.admin_refresh)) }
            }
            items(state.entries) { e ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(eventLabel(e.eventType)), style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(e.actorName, e.detail, e.occurredAt.take(16).replace('T', ' ')).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private fun eventLabel(type: String): Int = when (type) {
    "auth.account_created" -> R.string.event_account_created
    "practice.response_submitted" -> R.string.event_response_submitted
    "admin.role_granted" -> R.string.event_role_granted
    "admin.role_revoked" -> R.string.event_role_revoked
    "admin.account_suspended" -> R.string.event_account_suspended
    "admin.account_activated" -> R.string.event_account_activated
    "admin.bootstrap_admin_granted" -> R.string.event_bootstrap_admin
    "content.question_reviewed" -> R.string.event_question_reviewed
    "staff.question_created" -> R.string.event_question_created
    "staff.question_edited" -> R.string.event_question_edited
    "staff.question_deleted" -> R.string.event_question_deleted
    "certificate.requested" -> R.string.event_certificate_requested
    "certificate.approved" -> R.string.event_certificate_approved
    "certificate.rejected" -> R.string.event_certificate_rejected
    else -> R.string.event_other
}
