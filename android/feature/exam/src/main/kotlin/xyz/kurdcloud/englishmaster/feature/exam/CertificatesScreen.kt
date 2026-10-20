package xyz.kurdcloud.englishmaster.feature.exam

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import xyz.kurdcloud.englishmaster.core.designsystem.EmptyState
import xyz.kurdcloud.englishmaster.core.designsystem.ErrorState
import xyz.kurdcloud.englishmaster.core.designsystem.LoadingState
import xyz.kurdcloud.englishmaster.core.model.Certificate
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificatesScreen(
    viewModel: CertificatesViewModel,
    exporter: CertificateExporter,
    verificationUrl: (Certificate) -> String,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val failed = stringResource(R.string.certificate_failed)
    val noApp = stringResource(R.string.certificate_no_app)
    var busy by remember { mutableStateOf<String?>(null) }

    fun launch(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar(noApp) }
        }
    }

    fun withPdf(certificate: Certificate, action: (Uri) -> Unit) {
        if (busy != null) return
        busy = certificate.code
        scope.launch {
            val uri = runCatching { exporter.export(certificate) }.getOrNull()
            busy = null
            if (uri == null) snackbar.showSnackbar(failed) else action(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.certificates_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.certificates_back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        val problem = state.problem
        when {
            state.loading -> LoadingState(modifier = modifier)
            problem != null -> ErrorState(stringResource(problem.messageRes()), viewModel::load, modifier)
            state.certificates.isEmpty() -> EmptyState(stringResource(R.string.certificates_empty), modifier)
            else -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.certificates, key = { it.code }) { c ->
                        CertificateCard(
                            c,
                            busy = busy == c.code,
                            onOpen = {
                                withPdf(c) { uri ->
                                    launch(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                }
                            },
                            onShare = {
                                withPdf(c) { uri ->
                                    val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    launch(Intent.createChooser(send, null))
                                }
                            },
                            onVerify = { launch(Intent(Intent.ACTION_VIEW, verificationUrl(c).toUri())) },
                        )
                    }
                    item {
                        Text(stringResource(R.string.certificate_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CertificateCard(c: Certificate, busy: Boolean, onOpen: () -> Unit, onShare: () -> Unit, onVerify: () -> Unit) {
    val navy = Color(0xFF1B2A5E)
    val gold = Color(0xFFC9A227)
    Card(
        modifier = Modifier.fillMaxWidth().testTag("certificate_${c.level.name}"),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            Modifier.background(Brush.linearGradient(listOf(navy, Color(0xFF2F4A9A)))).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(56.dp).background(gold, CircleShape), contentAlignment = Alignment.Center) {
                    Text(c.level.label, color = navy, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Column {
                    Text(stringResource(R.string.certificate_level, c.level.label), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(c.holderName, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge)
                }
            }
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(c.issuedAtMillis))
            Text(stringResource(R.string.certificate_issued, date), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.certificate_score, c.score, c.total), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.certificate_code, c.code), color = gold, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen, enabled = !busy) { Text(stringResource(R.string.certificate_open)) }
                OutlinedButton(onClick = onShare, enabled = !busy) { Text(stringResource(R.string.certificate_share), color = Color.White) }
                TextButton(onClick = onVerify) { Text(stringResource(R.string.certificate_verify), color = gold) }
            }
        }
    }
}
