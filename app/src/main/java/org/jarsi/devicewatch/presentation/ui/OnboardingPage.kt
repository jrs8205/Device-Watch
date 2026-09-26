package org.jarsi.devicewatch.presentation.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.AppSettingsRepositoryImpl

/**
 * Runtime permissions the app asks for, minus those already granted. COARSE must
 * accompany FINE: on Android 12+ a FINE-only request is silently ignored — no
 * dialog, nothing granted — including the upgrade case where the user picked
 * "Approximate" earlier, so an already-granted COARSE rides along with FINE.
 */
internal fun missingRuntimePermissions(context: Context): Array<String> {
    val permissions = buildList {
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val missing = permissions
        .filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        .toMutableList()
    if (Manifest.permission.ACCESS_FINE_LOCATION in missing &&
        Manifest.permission.ACCESS_COARSE_LOCATION !in missing
    ) {
        missing.add(0, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    return missing.toTypedArray()
}

private fun Context.appSettingsPrefs() =
    getSharedPreferences(AppSettingsRepositoryImpl.PREFS_NAME, Context.MODE_PRIVATE)

/**
 * Records that the runtime-permission dialog has been launched at least once.
 * Persisted, not per-screen state: permanent-denial detection needs it across
 * recreations, or the first tap after one is a silent no-op request.
 */
internal fun Context.markRuntimePermissionsRequested() {
    appSettingsPrefs().edit()
        .putBoolean(AppSettingsRepositoryImpl.KEY_RUNTIME_PERMISSIONS_REQUESTED, true)
        .apply()
}

/**
 * True when every still-missing runtime permission is permanently denied — the
 * system won't show a dialog anymore, so the only useful action is app settings.
 * (Rationale false + a recorded earlier request = permanently denied.)
 */
internal fun Context.runtimePermissionsPermanentlyDenied(missing: Array<String>): Boolean {
    val activity = this as? Activity ?: return false
    if (missing.isEmpty()) return false
    val requestedBefore = appSettingsPrefs()
        .getBoolean(AppSettingsRepositoryImpl.KEY_RUNTIME_PERMISSIONS_REQUESTED, false)
    if (!requestedBefore) return false
    return missing.all { !activity.shouldShowRequestPermissionRationale(it) }
}

/** Opens this app's system settings page — the only path once a permission is permanently denied. */
internal fun Context.openAppDetailsSettings() {
    try {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private const val PAGE_COUNT = 6

/**
 * Full-screen first-run intro: permissions up front (runtime batch + the two
 * special-access grants with live status dots), then a screenshot tour. Shown
 * once on first launch and re-openable from the Settings tab.
 */
@Composable
fun OnboardingPage(
    usageAccessGranted: Boolean,
    notificationAccessGranted: Boolean,
    onRequestRefresh: () -> Unit,
    /** requestedPermissions = true when the runtime dialog was launched from the intro. */
    onFinish: (requestedPermissions: Boolean) -> Unit,
) {
    val context = LocalContext.current
    var runtimeGranted by remember { mutableStateOf(missingRuntimePermissions(context).isEmpty()) }
    var runtimeRequested by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        runtimeGranted = missingRuntimePermissions(context).isEmpty()
        onRequestRefresh()
    }
    // The special-access grants happen in Settings; re-check when the user comes back.
    LifecycleResumeEffect(Unit) {
        runtimeGranted = missingRuntimePermissions(context).isEmpty()
        onRequestRefresh()
        onPauseOrDispose { }
    }

    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == PAGE_COUNT - 1

    // System Back pages backwards instead of abandoning the intro; on the first
    // page it falls through to the default (replay close / app exit).
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = withTapHaptic { onFinish(runtimeRequested) }) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (page) {
                    0 -> WelcomePage()
                    1 -> PermissionsPage(
                        runtimeGranted = runtimeGranted,
                        usageAccessGranted = usageAccessGranted,
                        notificationAccessGranted = notificationAccessGranted,
                        onGrantRuntime = {
                            val missing = missingRuntimePermissions(context)
                            when {
                                missing.isEmpty() -> Unit
                                context.runtimePermissionsPermanentlyDenied(missing) ->
                                    context.openAppDetailsSettings()
                                else -> {
                                    runtimeRequested = true
                                    context.markRuntimePermissionsRequested()
                                    permissionLauncher.launch(missing)
                                }
                            }
                        },
                        onOpenSettingsAction = { action ->
                            try {
                                context.startActivity(
                                    Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                    )
                    2 -> FeaturePage(
                        imageRes = R.drawable.onboarding_widget,
                        titleRes = R.string.onboarding_widget_title,
                        bodyRes = R.string.onboarding_widget_body,
                    )
                    3 -> FeaturePage(
                        imageRes = R.drawable.onboarding_screensaver,
                        titleRes = R.string.onboarding_screensaver_title,
                        bodyRes = R.string.onboarding_screensaver_body,
                    ) {
                        OutlinedButton(
                            onClick = withTapHaptic {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_DREAM_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(stringResource(R.string.open_screensaver_settings), fontSize = 12.sp)
                        }
                    }
                    4 -> FeaturePage(
                        imageRes = R.drawable.onboarding_history,
                        titleRes = R.string.onboarding_history_title,
                        bodyRes = R.string.onboarding_history_body,
                    )
                    else -> DonePage()
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(PAGE_COUNT) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (pagerState.currentPage > 0) {
                TextButton(onClick = withTapHaptic {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                }) {
                    Text(stringResource(R.string.onboarding_back))
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = withTapHaptic {
                    if (isLastPage) {
                        onFinish(runtimeRequested)
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    stringResource(
                        if (isLastPage) R.string.onboarding_start else R.string.onboarding_next
                    )
                )
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Spacer(modifier = Modifier.height(48.dp))
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier.size(160.dp),
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        text = stringResource(R.string.onboarding_welcome_title),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.onboarding_welcome_body),
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun PermissionsPage(
    runtimeGranted: Boolean,
    usageAccessGranted: Boolean,
    notificationAccessGranted: Boolean,
    onGrantRuntime: () -> Unit,
    onOpenSettingsAction: (String) -> Unit,
) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        text = stringResource(R.string.onboarding_permissions_title),
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_permissions_body),
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(20.dp))

    PermissionRow(
        granted = runtimeGranted,
        labelRes = R.string.onboarding_runtime_permissions_label,
        descriptionRes = R.string.onboarding_runtime_permissions_desc,
        buttonLabelRes = R.string.onboarding_grant_permissions,
        onClick = onGrantRuntime,
    )
    Spacer(modifier = Modifier.height(16.dp))
    PermissionRow(
        granted = usageAccessGranted,
        labelRes = R.string.usage_access_button,
        descriptionRes = R.string.usage_access_needed_body,
        buttonLabelRes = R.string.onboarding_open_settings,
        onClick = { onOpenSettingsAction(Settings.ACTION_USAGE_ACCESS_SETTINGS) },
    )
    Spacer(modifier = Modifier.height(16.dp))
    PermissionRow(
        granted = notificationAccessGranted,
        labelRes = R.string.notification_access_button,
        descriptionRes = R.string.notification_access_hint,
        buttonLabelRes = R.string.onboarding_open_settings,
        onClick = { onOpenSettingsAction(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
    )
}

@Composable
private fun PermissionRow(
    granted: Boolean,
    @StringRes labelRes: Int,
    @StringRes descriptionRes: Int,
    @StringRes buttonLabelRes: Int,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccessStatusDot(granted = granted)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                text = stringResource(labelRes),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(descriptionRes),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = withTapHaptic(onClick),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(buttonLabelRes), fontSize = 12.sp)
        }
    }
}

@Composable
private fun FeaturePage(
    @DrawableRes imageRes: Int,
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    extraContent: (@Composable () -> Unit)? = null,
) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        text = stringResource(titleRes),
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(bodyRes),
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Image(
        painter = painterResource(imageRes),
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth(0.62f)
            .heightIn(max = 380.dp)
            .clip(RoundedCornerShape(16.dp)),
    )
    if (extraContent != null) {
        Spacer(modifier = Modifier.height(16.dp))
        extraContent()
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun DonePage() {
    Spacer(modifier = Modifier.height(96.dp))
    Text(
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        text = stringResource(R.string.onboarding_done_title),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.onboarding_done_body),
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
