package org.jarsi.devicewatch.presentation.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.SystemApp
import org.jarsi.devicewatch.data.SystemAppActionResult
import org.jarsi.devicewatch.data.SystemAppRisk
import org.jarsi.devicewatch.data.SystemAppRole
import org.jarsi.devicewatch.presentation.SystemAppAction
import org.jarsi.devicewatch.presentation.SystemAppsViewModel

/**
 * The system image's apps, installed for this user or removed from it, with a
 * way to remove the ones not needed and bring a removed one back. Every removal
 * goes through a question; the apps the phone depends on say so, or cannot be
 * removed from here at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemAppsPage(
    onBack: () -> Unit,
    viewModel: SystemAppsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Read again on every return: Settings may have changed the default apps meanwhile.
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    state.pending?.let { app ->
        RemoveQuestion(
            app = app,
            acknowledged = state.acknowledged,
            canConfirm = state.canConfirm,
            onAcknowledgeChange = viewModel::onAcknowledgeChange,
            onConfirm = viewModel::onConfirmRemove,
            onDismiss = viewModel::onDismissRemove,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.system_apps_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = withTapHaptic(onBack)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "note") {
                Column(modifier = Modifier.padding(horizontal = BAND_INSET, vertical = 8.dp)) {
                    KernelNote(stringResource(R.string.system_apps_page_note))
                }
            }
            item(key = "search") {
                // The keyboard's Search key puts the keyboard away: the list filters as
                // one types, and the keyboard would otherwise cover the rows' buttons.
                val focusManager = LocalFocusManager.current
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = BAND_INSET, vertical = 4.dp),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.system_apps_search)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                )
            }
            state.lastAction?.let { action ->
                item(key = "notice") { ActionNotice(action, onDismiss = viewModel::onDismissAction) }
            }
            if (state.loading) {
                item(key = "loading") {
                    Column(modifier = Modifier.padding(horizontal = BAND_INSET, vertical = 8.dp)) {
                        KernelNote(stringResource(R.string.system_apps_reading))
                    }
                }
                return@LazyColumn
            }
            val removed = state.visibleRemoved
            if (removed.isNotEmpty()) {
                item(key = "removed_title") { ListTitle(stringResource(R.string.system_apps_removed_title, removed.size)) }
                items(removed.size, key = { "removed_" + removed[it].packageName }) { index ->
                    val app = removed[index]
                    SystemAppRow(
                        app = app,
                        busy = state.busyPackage != null,
                        restore = true,
                        actionRes = R.string.system_apps_restore,
                        onAction = { viewModel.onRestore(app.packageName) },
                    )
                }
            }
            val apps = state.visibleApps
            item(key = "installed_title") { ListTitle(stringResource(R.string.system_apps_installed_title, apps.size)) }
            if (apps.isEmpty()) {
                item(key = "none") {
                    Column(modifier = Modifier.padding(horizontal = BAND_INSET, vertical = 8.dp)) {
                        KernelNote(stringResource(R.string.system_apps_none_match))
                    }
                }
            }
            items(apps.size, key = { apps[it].packageName }) { index ->
                val app = apps[index]
                SystemAppRow(
                    app = app,
                    busy = state.busyPackage != null,
                    restore = false,
                    actionRes = R.string.system_apps_remove,
                    onAction = { viewModel.onRemoveRequested(app.packageName) },
                )
            }
        }
    }
}

@Composable
private fun ListTitle(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = SECTION_TITLE_TRACKING,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BAND_INSET)
            .padding(top = 20.dp, bottom = 4.dp),
    )
}

/**
 * One app: its icon, name and package, what its removal would mean, and the
 * button. A blocked app has the reason where the Remove button would be; the
 * verdict only ever withholds a removal, so a removed app (by another tool,
 * say) always has its Restore, with the reason as a note.
 */
@Composable
private fun SystemAppRow(
    app: SystemApp,
    busy: Boolean,
    restore: Boolean,
    @StringRes actionRes: Int,
    onAction: () -> Unit,
) {
    val verdict = app.verdict
    val blocked = !restore && verdict.risk == SystemAppRisk.BLOCKED
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BAND_INSET, vertical = 8.dp),
    ) {
        AppIcon(app.packageName, modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            KernelTechnical(app.packageName)
            val captions = buildList {
                if (verdict.aospComponent) add(stringResource(R.string.system_apps_aosp))
                if (app.updated) add(stringResource(R.string.system_apps_updated))
            }
            if (captions.isNotEmpty()) KernelTechnical(captions.joinToString(" · "))
            verdict.role?.let { role ->
                val color = if (blocked || restore) MaterialTheme.colorScheme.onSurfaceVariant else statusWarnColor()
                val lead = stringResource(
                    when {
                        restore -> R.string.system_apps_note
                        blocked -> R.string.system_apps_blocked
                        else -> R.string.system_apps_warning
                    }
                )
                Text(
                    text = "$lead: " + stringResource(roleExplanation(role)),
                    fontSize = 12.sp,
                    color = color,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (!blocked) {
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = withTapHaptic(onAction), enabled = !busy) {
                Text(stringResource(actionRes), fontSize = 13.sp)
            }
        }
    }
}

/**
 * The question every removal goes through: what happens, and for a warned-about
 * app what breaks, with the remove button held back until that is acknowledged.
 */
@Composable
private fun RemoveQuestion(
    app: SystemApp,
    acknowledged: Boolean,
    canConfirm: Boolean,
    onAcknowledgeChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val warned = app.verdict.risk == SystemAppRisk.WARNING
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.system_apps_remove_title, app.label)) },
        text = {
            Column {
                KernelTechnical(app.packageName)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.system_apps_remove_body), fontSize = 14.sp)
                if (warned) {
                    app.verdict.role?.let { role ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.system_apps_warning) + ": " + stringResource(roleExplanation(role)),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusWarnColor(),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = acknowledged, onCheckedChange = onAcknowledgeChange)
                        Text(stringResource(R.string.system_apps_remove_acknowledge), fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = withTapHaptic(onConfirm), enabled = canConfirm) {
                Text(stringResource(R.string.system_apps_remove), color = if (canConfirm) statusWarnColor() else Color.Unspecified)
            }
        },
        dismissButton = {
            TextButton(onClick = withTapHaptic(onDismiss)) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** How the last removal or restore went, in the package manager's words when it refused. */
@Composable
private fun ActionNotice(action: SystemAppAction, onDismiss: () -> Unit) {
    val label = action.app.label
    val ok = action.result == SystemAppActionResult.Done
    val text = when (val result = action.result) {
        SystemAppActionResult.Done ->
            stringResource(if (action.restore) R.string.system_apps_done_restored else R.string.system_apps_done_removed, label)
        SystemAppActionResult.NoShell ->
            stringResource(
                if (action.restore) R.string.system_apps_failed_restored else R.string.system_apps_failed_removed,
                label, stringResource(R.string.system_apps_no_shell),
            )
        is SystemAppActionResult.Failed ->
            stringResource(
                if (action.restore) R.string.system_apps_failed_restored else R.string.system_apps_failed_removed,
                label, result.message.ifBlank { stringResource(R.string.system_apps_no_reason) },
            )
        is SystemAppActionResult.Blocked ->
            stringResource(
                R.string.system_apps_failed_removed,
                label,
                result.verdict.role?.let { stringResource(roleExplanation(it)) } ?: stringResource(R.string.system_apps_blocked_now),
            )
        SystemAppActionResult.NotInstalled ->
            stringResource(R.string.system_apps_failed_removed, label, stringResource(R.string.system_apps_not_installed_now))
        SystemAppActionResult.Unchanged ->
            stringResource(
                if (action.restore) R.string.system_apps_failed_restored else R.string.system_apps_failed_removed,
                label, stringResource(R.string.system_apps_unchanged),
            )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BAND_INSET, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = if (ok) statusOkColor() else statusWarnColor(),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = withTapHaptic(onDismiss)) {
            Text(stringResource(R.string.system_apps_dismiss_notice), fontSize = 13.sp)
        }
    }
}

@StringRes
private fun roleExplanation(role: SystemAppRole): Int = when (role) {
    SystemAppRole.CORE -> R.string.system_app_role_core
    SystemAppRole.SELF -> R.string.system_app_role_self
    SystemAppRole.HOME_IN_USE -> R.string.system_app_role_home_in_use
    SystemAppRole.HOME -> R.string.system_app_role_home
    SystemAppRole.KEYBOARD_IN_USE -> R.string.system_app_role_keyboard_in_use
    SystemAppRole.KEYBOARD -> R.string.system_app_role_keyboard
    SystemAppRole.DIALER_IN_USE -> R.string.system_app_role_dialer_in_use
    SystemAppRole.DIALER -> R.string.system_app_role_dialer
    SystemAppRole.MESSAGING -> R.string.system_app_role_messaging
    SystemAppRole.WEBVIEW_IN_USE -> R.string.system_app_role_webview_in_use
    SystemAppRole.WEBVIEW -> R.string.system_app_role_webview
    SystemAppRole.PLAY_SERVICES -> R.string.system_app_role_play_services
    SystemAppRole.PLAY_STORE -> R.string.system_app_role_play_store
    SystemAppRole.RADIO -> R.string.system_app_role_radio
    SystemAppRole.TELEPHONY -> R.string.system_app_role_telephony
    SystemAppRole.EMERGENCY -> R.string.system_app_role_emergency
    SystemAppRole.SETUP -> R.string.system_app_role_setup
    SystemAppRole.UPDATES -> R.string.system_app_role_updates
    SystemAppRole.VENDOR -> R.string.system_app_role_vendor
    SystemAppRole.CHIPSET -> R.string.system_app_role_chipset
    SystemAppRole.OVERLAY -> R.string.system_app_role_overlay
    SystemAppRole.FILES -> R.string.system_app_role_files
}
