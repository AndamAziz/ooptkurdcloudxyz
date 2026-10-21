package xyz.kurdcloud.englishmaster.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val SECTIONS = listOf(
    R.string.privacy_collect_title to R.string.privacy_collect_body,
    R.string.privacy_use_title to R.string.privacy_use_body,
    R.string.privacy_share_title to R.string.privacy_share_body,
    R.string.privacy_storage_title to R.string.privacy_storage_body,
    R.string.privacy_rights_title to R.string.privacy_rights_body,
    R.string.privacy_children_title to R.string.privacy_children_body,
    R.string.privacy_contact_title to R.string.privacy_contact_body,
)

/** The privacy policy, readable in the app (the same text is published for the Play listing). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_privacy_policy)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.account_back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("privacy_policy"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.privacy_intro), style = MaterialTheme.typography.bodyLarge)
                SECTIONS.forEach { (title, body) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(stringResource(R.string.privacy_updated), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
