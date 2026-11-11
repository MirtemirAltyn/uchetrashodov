package com.moifinansy.ui.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.ui.BackTopBar
import com.moifinansy.core.ui.SectionTitle
import com.moifinansy.core.ui.theme.Spacing
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataScreen(onBack: () -> Unit, viewModel: DataViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }
    var exportPeriod by remember { mutableStateOf(ExportPeriod.MONTH) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val today = LocalDate.now()
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { viewModel.exportCsv(it, exportPeriod) } }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(viewModel::backup) }
    val resetBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.backupBeforeReset(uri) else viewModel.dismissDialogs()
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmRestore = uri }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.more_data), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            Section(stringResource(R.string.data_section_demo), first = true)
            if (state.demoCount > 0) {
                Link(stringResource(R.string.demo_delete), stringResource(R.string.demo_delete_hint), onClick = viewModel::requestDeleteDemo)
            } else {
                Link(stringResource(R.string.demo_fill), stringResource(R.string.demo_fill_any_hint), onClick = viewModel::requestDemo)
            }

            Section(stringResource(R.string.data_section_backup))
            Link(stringResource(R.string.settings_backup), stringResource(R.string.settings_backup_hint)) { backupLauncher.launch("moi-finansy-backup-$today.json") }
            Link(stringResource(R.string.settings_restore), null) { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }

            Section(stringResource(R.string.data_section_export))
            FlowRow(Modifier.padding(horizontal = Spacing.screenHorizontal), horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                listOf(ExportPeriod.MONTH to R.string.period_month, ExportPeriod.YEAR to R.string.period_year, ExportPeriod.ALL to R.string.ops_all_time)
                    .forEach { (p, label) -> FilterChip(exportPeriod == p, { exportPeriod = p }, label = { Text(stringResource(label)) }) }
            }
            Link(stringResource(R.string.settings_export_csv), null) { csvLauncher.launch("moi-finansy-$today.csv") }

            Section(stringResource(R.string.data_section_reset))
            Link(stringResource(R.string.reset_action), stringResource(R.string.reset_action_hint), color = MaterialTheme.colorScheme.error, onClick = viewModel::requestReset)
        }
    }

    if (state.confirmAddDemo) {
        Confirm(
            title = stringResource(R.string.demo_add_title), text = stringResource(R.string.demo_add_text),
            confirm = stringResource(R.string.demo_add_confirm), onConfirm = viewModel::fillDemo, onDismiss = viewModel::dismissDialogs,
        )
    }
    if (state.confirmDeleteDemo) {
        Confirm(
            title = stringResource(R.string.demo_delete_title), text = stringResource(R.string.demo_delete_text),
            confirm = stringResource(R.string.action_delete), danger = true, onConfirm = viewModel::deleteDemo, onDismiss = viewModel::dismissDialogs,
        )
    }
    when (state.reset) {
        ResetStep.OFFER_BACKUP -> AlertDialog(
            onDismissRequest = viewModel::dismissDialogs,
            title = { Text(stringResource(R.string.reset_backup_title)) },
            text = { Text(stringResource(R.string.reset_backup_text)) },
            confirmButton = { TextButton(onClick = { resetBackupLauncher.launch("moi-finansy-backup-$today.json") }) { Text(stringResource(R.string.reset_backup_save)) } },
            dismissButton = { TextButton(onClick = viewModel::skipBackup) { Text(stringResource(R.string.reset_backup_skip)) } },
        )
        ResetStep.CONFIRM -> Confirm(
            title = stringResource(R.string.reset_confirm_title), text = stringResource(R.string.reset_confirm_text),
            confirm = stringResource(R.string.reset_confirm), danger = true, onConfirm = viewModel::confirmReset, onDismiss = viewModel::dismissDialogs,
        )
        ResetStep.NONE -> Unit
    }
    confirmRestore?.let { uri ->
        Confirm(
            title = stringResource(R.string.settings_restore_title), text = stringResource(R.string.settings_restore_text),
            confirm = stringResource(R.string.settings_restore_confirm), danger = true,
            onConfirm = {
                confirmRestore = null
                viewModel.restore(uri)
            },
            onDismiss = { confirmRestore = null },
        )
    }
}

@Composable
private fun Section(title: String, first: Boolean = false) {
    if (!first) HorizontalDivider(Modifier.padding(top = Spacing.grid))
    SectionTitle(title, Modifier.padding(horizontal = Spacing.screenHorizontal))
}

@Composable
private fun Link(title: String, subtitle: String?, color: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid * 1.5f)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Confirm(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
