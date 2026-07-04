package com.example.modernwidget.presentation.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.modernwidget.R
import com.example.modernwidget.data.LaunchableApp
import com.example.modernwidget.data.UsageEventAggregator
import com.example.modernwidget.presentation.AppsViewModel

/** Apps tab: screen-time donut, top data consumers and the last-opened list. */
@Composable
internal fun AppsTab(viewModel: AppsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Reload on every resume: first open, returning from the Usage Access settings
    // or from an uninstall dialog (ACTION_DELETE result codes are unreliable).
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val uninstallLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refresh()
    }

    fun openAction(action: String) {
        try {
            context.startActivity(Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    uiState.selectedDetail?.let { detail ->
        AppDetailSheet(
            detail = detail,
            onDismiss = viewModel::onDetailDismiss,
            onEnableNotifications = { openAction(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
        )
    }

    if (!uiState.hasUsageAccess) {
        // Empty state as a call to action: explain and open the right settings page.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.QueryStats,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.usage_access_needed_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.usage_access_needed_body),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(
                onClick = { openAction(Settings.ACTION_USAGE_ACCESS_SETTINGS) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.usage_access_button), fontSize = 12.sp)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "screen_time") {
            SettingsSectionCard(
                titleRes = R.string.screen_time_section,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val colors = donutSegmentColors(uiState.screenTimeSegments)
                Spacer(modifier = Modifier.height(4.dp))
                ScreenTimeDonut(
                    segments = uiState.screenTimeSegments,
                    colors = colors,
                    totalText = durationText(context, uiState.totalScreenTimeMillis),
                )
                Spacer(modifier = Modifier.height(12.dp))
                uiState.screenTimeSegments.forEachIndexed { index, segment ->
                    val rowModifier = if (segment.packageName != null) {
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.onAppSelected(segment.packageName!!) }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    }
                    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(colors[index])
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = segment.label ?: stringResource(R.string.screen_time_others),
                            fontSize = 14.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = durationText(context, segment.millis),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(segment.fraction * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        val mostOpened = UsageEventAggregator.topByLaunches(uiState.screenTimes)
        if (mostOpened.isNotEmpty()) {
            item(key = "most_opened") {
                SettingsSectionCard(titleRes = R.string.most_opened_section) {
                    mostOpened.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.onAppSelected(entry.packageName) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(entry.packageName, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = entry.label,
                                fontSize = 14.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${entry.launchCount}×",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item(key = "data_consumers") {
            SettingsSectionCard(titleRes = R.string.data_consumers_section) {
                uiState.dataConsumers.take(10).forEach { consumer ->
                    val rowModifier = if (consumer.packageName != null) {
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.onAppSelected(consumer.packageName!!) }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    }
                    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
                        if (consumer.packageName != null) {
                            AppIcon(consumer.packageName!!, modifier = Modifier.size(28.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = consumer.label,
                            fontSize = 14.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = bytesText(consumer.bytes),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item(key = "last_opened_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.last_opened_section),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = viewModel::onSortToggle) {
                    Icon(
                        imageVector = Icons.Outlined.SwapVert,
                        contentDescription = stringResource(R.string.sort_toggle_description),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        items(uiState.apps, key = { it.packageName }) { app ->
            AppListRow(
                app = app,
                onClick = { viewModel.onAppSelected(app.packageName) },
                onUninstall = {
                    uninstallLauncher.launch(
                        Intent(Intent.ACTION_DELETE, "package:${app.packageName}".toUri())
                    )
                },
            )
        }
    }
}

@Composable
private fun AppListRow(
    app: LaunchableApp,
    onClick: () -> Unit,
    onUninstall: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app.packageName, modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = app.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            val tier = UsageEventAggregator.lastUsedTier(daysSinceLastUse(app.lastUsedEpochMillis))
            Text(
                text = lastUsedText(app.lastUsedEpochMillis),
                fontSize = 12.sp,
                color = lastUsedTierColor(tier)
            )
        }
        if (!app.isSystemApp) {
            IconButton(onClick = onUninstall) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.uninstall_content_description),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
