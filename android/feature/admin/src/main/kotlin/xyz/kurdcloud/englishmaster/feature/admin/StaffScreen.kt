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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.kurdcloud.englishmaster.core.designsystem.EmptyState
import xyz.kurdcloud.englishmaster.core.designsystem.ErrorState
import xyz.kurdcloud.englishmaster.core.designsystem.LoadingState
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.Role
import xyz.kurdcloud.englishmaster.core.model.StaffAccess
import xyz.kurdcloud.englishmaster.core.model.StaffQuestion
import xyz.kurdcloud.englishmaster.core.model.StaffUser
import xyz.kurdcloud.englishmaster.core.model.optionLetter

private enum class StaffTab(val title: Int) { USERS(R.string.admin_tab_users), QUESTIONS(R.string.admin_tab_questions), ACTIVITY(R.string.admin_tab_activity) }

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
) {
    val tabs = buildList {
        if (access.canManageUsers) add(StaffTab.USERS)
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
                PrimaryTabRow(selectedTabIndex = selected.coerceIn(tabs.indices)) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(selected = selected == index, onClick = { selected = index }, text = { Text(stringResource(tab.title)) })
                    }
                }
            }
            when (tabs[selected.coerceIn(tabs.indices)]) {
                StaffTab.USERS -> UsersTab(usersViewModel(), currentUserId, snackbar)
                StaffTab.QUESTIONS -> QuestionsTab(questionsViewModel(), snackbar)
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
private fun UsersTab(vm: UsersViewModel, currentUserId: String, snackbar: SnackbarHostState) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    MessageEffect(state.message, snackbar, vm::messageShown)
    var confirm by remember { mutableStateOf<PendingChange?>(null) }

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
                        busy = state.busyUserId == user.userId,
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
private fun UserCard(
    user: StaffUser,
    self: Boolean,
    busy: Boolean,
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
private fun QuestionsTab(vm: QuestionsViewModel, snackbar: SnackbarHostState) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    MessageEffect(state.message, snackbar, vm::messageShown)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CefrLevel.practiceLevels.forEach { level ->
                FilterChip(selected = state.level == level, onClick = { vm.load(level) }, label = { Text(level.label) })
            }
        }
        val approved = state.questions.count { it.reviewStatus == "approved" }
        if (!state.loading && state.error == null) {
            Text(
                stringResource(R.string.admin_review_summary, approved, state.questions.size),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        when {
            state.loading -> LoadingState()
            state.error != null -> ErrorState(stringResource(state.error!!), { vm.load(state.level) })
            state.questions.isEmpty() -> EmptyState(stringResource(R.string.admin_no_questions))
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.questions, key = { it.id }) { q ->
                    QuestionCard(q, busy = state.busyQuestionId == q.id, onApprove = { vm.setApproved(q, it) }, onRetire = { vm.setRetired(q, it) })
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(q: StaffQuestion, busy: Boolean, onApprove: (Boolean) -> Unit, onRetire: (Boolean) -> Unit) {
    val approved = q.reviewStatus == "approved"
    val retired = q.status == "retired"
    val draft = q.status == "draft"
    Card(Modifier.fillMaxWidth().testTag("question_${q.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = {}, label = { Text(stringResource(if (approved) R.string.admin_approved else R.string.admin_unreviewed)) })
                if (retired) AssistChip(onClick = {}, label = { Text(stringResource(R.string.admin_retired)) })
                if (draft) AssistChip(onClick = {}, label = { Text(stringResource(R.string.admin_draft)) })
            }
            // Learning content is English: keep it left-to-right in the Sorani UI.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    else -> R.string.event_other
}
