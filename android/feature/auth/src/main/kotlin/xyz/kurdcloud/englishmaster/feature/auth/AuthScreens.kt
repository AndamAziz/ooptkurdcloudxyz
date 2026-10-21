package xyz.kurdcloud.englishmaster.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.kurdcloud.englishmaster.core.designsystem.AppLogo
import xyz.kurdcloud.englishmaster.core.data.RegisterOutcome

/** Interface languages offered by the app. Independent of the language being learned (English). */
enum class InterfaceLanguage(val tag: String) { ENGLISH("en"), SORANI("ckb") }

@Composable
fun WelcomeScreen(
    currentLanguage: InterfaceLanguage,
    onLanguageSelected: (InterfaceLanguage) -> Unit,
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onOpenPrivacy: () -> Unit = {},
) {
    FormColumn {
        AppLogo(size = 80.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.auth_welcome_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.auth_welcome_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.auth_interface_language), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = currentLanguage == InterfaceLanguage.SORANI,
                onClick = { onLanguageSelected(InterfaceLanguage.SORANI) },
                label = { Text("کوردی (سۆرانی)") },
            )
            FilterChip(
                selected = currentLanguage == InterfaceLanguage.ENGLISH,
                onClick = { onLanguageSelected(InterfaceLanguage.ENGLISH) },
                label = { Text("English") },
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.auth_privacy_summary), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onOpenPrivacy, modifier = Modifier.testTag("welcome_privacy")) { Text(stringResource(R.string.account_privacy_policy)) }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.auth_create_account)) }
        OutlinedButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.auth_have_account)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    viewModel: SignInViewModel,
    onSignedIn: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.signedIn) { if (state.signedIn) onSignedIn() }
    AuthScaffold(title = stringResource(R.string.auth_sign_in), onBack = onBack) {
        EmailField(state.email, viewModel::onEmailChange, state.emailError, enabled = !state.submitting)
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            isError = state.passwordError,
            errorText = stringResource(R.string.auth_password_required),
            enabled = !state.submitting,
            newPassword = false,
            onDone = viewModel::submit,
        )
        MessageText(state.message)
        SubmitButton(stringResource(R.string.auth_sign_in), state.submitting, viewModel::submit)
        TextButton(onClick = onForgotPassword) { Text(stringResource(R.string.auth_forgot_password)) }
        TextButton(onClick = onCreateAccount) { Text(stringResource(R.string.auth_create_account)) }
    }
}

@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel,
    onRegistered: (RegisterOutcome) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.outcome) { state.outcome?.let(onRegistered) }
    AuthScaffold(title = stringResource(R.string.auth_create_account), onBack = onBack) {
        OutlinedTextField(
            value = state.displayName,
            onValueChange = viewModel::onNameChange,
            label = { Text(stringResource(R.string.auth_display_name)) },
            isError = state.nameError,
            supportingText = if (state.nameError) ({ Text(stringResource(R.string.auth_display_name_invalid)) }) else null,
            singleLine = true,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
        EmailField(state.email, viewModel::onEmailChange, state.emailError, enabled = !state.submitting)
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            isError = state.passwordError,
            errorText = stringResource(R.string.auth_password_rules),
            enabled = !state.submitting,
            newPassword = true,
            onDone = viewModel::submit,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = state.consent, role = Role.Checkbox, onValueChange = viewModel::onConsentChange),
        ) {
            Checkbox(checked = state.consent, onCheckedChange = null)
            Text(stringResource(R.string.auth_consent), style = MaterialTheme.typography.bodyMedium)
        }
        if (state.consentError) {
            Text(stringResource(R.string.auth_consent_required), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        MessageText(state.message)
        SubmitButton(stringResource(R.string.auth_create_account), state.submitting, viewModel::submit)
    }
}

@Composable
fun ForgotPasswordScreen(viewModel: ForgotPasswordViewModel, onSent: () -> Unit, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.sent) { if (state.sent) onSent() }
    AuthScaffold(title = stringResource(R.string.auth_forgot_password), onBack = onBack) {
        Text(stringResource(R.string.auth_reset_explainer), style = MaterialTheme.typography.bodyMedium)
        EmailField(state.email, viewModel::onEmailChange, state.emailError, enabled = !state.submitting, imeAction = ImeAction.Done)
        MessageText(state.message)
        SubmitButton(stringResource(R.string.auth_send_reset_link), state.submitting, viewModel::submit)
    }
}

/** Opened from the password-reset email (the link has already signed the user in). */
@Composable
fun SetPasswordScreen(viewModel: SetPasswordViewModel, onDone: () -> Unit, onCancel: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    AuthScaffold(title = stringResource(R.string.auth_set_password_title), onBack = onCancel) {
        Text(stringResource(R.string.auth_set_password_body), style = MaterialTheme.typography.bodyMedium)
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            isError = state.passwordError,
            errorText = stringResource(R.string.auth_password_rules),
            enabled = !state.submitting,
            newPassword = true,
            onDone = {},
        )
        OutlinedTextField(
            value = state.confirm,
            onValueChange = viewModel::onConfirmChange,
            label = { Text(stringResource(R.string.auth_set_password_confirm)) },
            isError = state.mismatch,
            supportingText = if (state.mismatch) ({ Text(stringResource(R.string.auth_set_password_mismatch)) }) else null,
            singleLine = true,
            enabled = !state.submitting,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
            modifier = Modifier.fillMaxWidth().testTag("set_password_confirm"),
        )
        MessageText(state.message)
        SubmitButton(stringResource(R.string.auth_set_password_save), state.submitting, viewModel::submit)
    }
}

enum class CheckEmailReason { CONFIRM_ACCOUNT, PASSWORD_RESET }

@Composable
fun CheckEmailScreen(reason: CheckEmailReason, onContinue: () -> Unit) {
    FormColumn {
        Text(stringResource(R.string.auth_check_email_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(if (reason == CheckEmailReason.CONFIRM_ACCOUNT) R.string.auth_check_email_confirm else R.string.auth_check_email_reset),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.auth_back_to_sign_in)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.auth_back)) } },
            )
        },
    ) { padding ->
        FormColumn(Modifier.padding(padding)) { content() }
    }
}

@Composable
private fun FormColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Column(Modifier.widthIn(max = 480.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
private fun MessageText(message: AuthMessage?) {
    if (message != null) {
        Text(
            stringResource(message.text),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("auth_message").semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun SubmitButton(label: String, submitting: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !submitting, modifier = Modifier.fillMaxWidth().testTag("auth_submit")) {
        if (submitting) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp) else Text(label)
    }
}
