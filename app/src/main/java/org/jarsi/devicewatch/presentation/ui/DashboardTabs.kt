package org.jarsi.devicewatch.presentation.ui

import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.presentation.AppsViewModel
import org.jarsi.devicewatch.presentation.DashboardViewModel
import org.jarsi.devicewatch.presentation.KernelViewModel
import org.jarsi.devicewatch.presentation.PrivacyState
import org.jarsi.devicewatch.presentation.PrivacyViewModel
import org.jarsi.devicewatch.presentation.KernelBandState
import org.jarsi.devicewatch.presentation.RootStatus
import org.jarsi.devicewatch.presentation.ShizukuStatus
import org.jarsi.devicewatch.presentation.SystemAppsViewModel
import org.jarsi.devicewatch.system.SystemMonitorService

/** Doubles the selection pill so colour is never the only cue. */
private val SELECTED_TAB_RULE = 3.dp

/** The dashboard's flat bottom-navigation destinations, in display order. */
internal enum class DashboardTab(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    // Declaration order = pager page order; per-tab state is keyed by page index.
    Overview(R.string.tab_overview, Icons.Filled.Home),
    Apps(R.string.tab_apps, Icons.Filled.Apps),
    Device(R.string.tab_device, Icons.Filled.PhoneAndroid),
    Settings(R.string.tab_settings, Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemDashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    appsViewModel: AppsViewModel = hiltViewModel(),
    kernelViewModel: KernelViewModel = hiltViewModel(),
    privacyViewModel: PrivacyViewModel = hiltViewModel(),
    systemAppsViewModel: SystemAppsViewModel = hiltViewModel(),
    /** Grows each time a tile, the single-figure widget or the notification asks for the Overview. */
    openOverviewRequest: Int = 0,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appsUiState by appsViewModel.uiState.collectAsStateWithLifecycle()

    // Start the background monitoring service (independent of the notification permission result).
    fun startSystemMonitorService() {
        val serviceIntent = Intent(context, SystemMonitorService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        startSystemMonitorService()
        viewModel.refresh()
    }

    // The pager is the single source of truth for the selected tab: swiping and
    // the bottom bar both drive it, and each page keeps its own saved scroll state.
    val pagerState = rememberPagerState(pageCount = { DashboardTab.entries.size })
    val selectedTab = DashboardTab.entries[pagerState.currentPage]
    val scope = rememberCoroutineScope()

    // Historia full-screen page, reached from the Overview usage-counters card.
    var showHistory by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showHistory) { showHistory = false }

    // "Since charge" full-screen page, reached from the Overview battery card.
    var showSinceCharge by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showSinceCharge) { showSinceCharge = false }

    // Kernel page, reached from the Device tab's KERNEL band; root only, so it
    // closes the moment root does.
    var showKernel by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showKernel) { showKernel = false }
    LaunchedEffect(uiState.rootStatus) {
        if (uiState.rootStatus != RootStatus.ON) showKernel = false
    }

    // Privacy log page, reached from the Apps tab's PRIVACY band. It reads through
    // the shell, so it closes the moment extended access goes off.
    val extendedAccessOn = uiState.rootStatus == RootStatus.ON || uiState.shizukuStatus == ShizukuStatus.ON
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showPrivacy) { showPrivacy = false }
    LaunchedEffect(extendedAccessOn) {
        if (!extendedAccessOn) showPrivacy = false
    }

    // System apps page, reached from the Apps tab's SYSTEM APPS band. Its removals
    // go through the shell, so it too closes the moment extended access goes off.
    var showSystemApps by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showSystemApps) { showSystemApps = false }
    LaunchedEffect(extendedAccessOn) {
        if (!extendedAccessOn) showSystemApps = false
    }

    // Every figure a tile, the widget or the notification shows is on the Overview.
    LaunchedEffect(openOverviewRequest) {
        if (openOverviewRequest > 0) {
            showHistory = false
            showSinceCharge = false
            showKernel = false
            showPrivacy = false
            showSystemApps = false
            pagerState.scrollToPage(DashboardTab.Overview.ordinal)
        }
    }

    // First-run intro state: shown until completed, and replayable from Settings.
    val onboardingCompleted = uiState.onboardingCompleted
    var replayOnboarding by rememberSaveable { mutableStateOf(false) }
    // Suppresses the auto permission ask only when the intro's permission dialog
    // was actually launched this session — deliberately NOT saveable, so a
    // process-death restore falls back to asking.
    var permissionsAskedInIntro by remember { mutableStateOf(false) }
    BackHandler(enabled = replayOnboarding) { replayOnboarding = false }

    LaunchedEffect(Unit) {
        // The monitor service needs no runtime permission — start it regardless of
        // whether the intro is still open, or nothing collects while it is.
        startSystemMonitorService()
        viewModel.refresh()
        viewModel.loadWidgetAppearance()
        viewModel.loadDataCounterSettings()
        viewModel.loadDeviceInfo()
    }

    // The automatic permission ask waits until the intro is out of the way — its
    // permission page owns the asking on first run. If the dialog was already
    // launched from the intro just now, the user made their choices; don't re-ask.
    LaunchedEffect(onboardingCompleted) {
        if (onboardingCompleted == true && !permissionsAskedInIntro) {
            val missingPermissions = missingRuntimePermissions(context)
            if (missingPermissions.isNotEmpty()) {
                context.markRuntimePermissionsRequested()
                runtimePermissionLauncher.launch(missingPermissions)
            }
        }
    }

    if (onboardingCompleted == false || replayOnboarding) {
        OnboardingPage(
            usageAccessGranted = uiState.usageAccessEnabled,
            notificationAccessGranted = uiState.notificationAccessEnabled,
            onRequestRefresh = viewModel::refresh,
            onFinish = { requestedPermissions ->
                if (replayOnboarding) {
                    replayOnboarding = false
                } else {
                    permissionsAskedInIntro = requestedPermissions
                    viewModel.completeOnboarding()
                }
            },
        )
        return
    }

    if (showHistory) {
        HistoryPage(onBack = { showHistory = false })
        return
    }

    if (showSinceCharge) {
        SinceChargePage(onBack = { showSinceCharge = false })
        return
    }

    if (showKernel && uiState.rootStatus == RootStatus.ON) {
        KernelPage(onBack = { showKernel = false }, viewModel = kernelViewModel)
        return
    }

    if (showPrivacy && extendedAccessOn) {
        PrivacyPage(onBack = { showPrivacy = false }, viewModel = privacyViewModel)
        return
    }

    if (showSystemApps && extendedAccessOn) {
        SystemAppsPage(onBack = { showSystemApps = false }, viewModel = systemAppsViewModel)
        return
    }

    Scaffold(
        topBar = {
            Column {
            TopAppBar(
                title = {
                    Column {
                        // The app names itself once, quietly, above the page title —
                        // the launcher icon is gone the moment the app is open.
                        Text(
                            stringResource(R.string.app_name).uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = SECTION_TITLE_TRACKING,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.main_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = withTapHaptic {
                        viewModel.refresh()
                        // The Apps tab has its own on-demand queries; refresh it too
                        // when it is the one on screen.
                        if (selectedTab == DashboardTab.Apps) {
                            appsViewModel.refresh()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        },
        bottomBar = {
            // The theme root already bounds the font scale for the whole tree
            // (AppScale.kt), which is what keeps these labels on one line.
            Column {
                // On the light theme the bar is white on a near-white page:
                // this rule is the only thing that separates the two.
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                NavigationBar {
                    val indicator = MaterialTheme.colorScheme.primary
                    DashboardTab.entries.forEachIndexed { index, tab ->
                        val selected = index == pagerState.currentPage
                        NavigationBarItem(
                            selected = selected,
                            onClick = withTapHaptic {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            // Selection is never colour alone: the pill is
                            // doubled by a rule under the item, where the eye
                            // is already travelling from icon to label.
                            modifier = Modifier.drawBehind {
                                if (selected) {
                                    val rule = SELECTED_TAB_RULE.toPx()
                                    drawRect(
                                        color = indicator,
                                        topLeft = Offset(0f, size.height - rule),
                                        size = Size(size.width, rule)
                                    )
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = {
                                Text(
                                    stringResource(tab.labelRes),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        if (uiState.stats != null) {
            // Driven ONLY by the pull-initiated flags — never by generic loading
            // state, or the spinner would pop on tab switches and the refresh icon.
            val isPullRefreshing = uiState.isRefreshing ||
                (selectedTab == DashboardTab.Apps && appsUiState.isRefreshing)
            // One pull-to-refresh wrapper serves all four tabs via nested scroll.
            PullToRefreshBox(
                isRefreshing = isPullRefreshing,
                onRefresh = {
                    view.performTapHaptic()
                    viewModel.pullRefresh()
                    if (selectedTab == DashboardTab.Apps) {
                        appsViewModel.pullRefresh()
                    }
                },
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            ) {
                // Swiping between tabs; the pager preserves each page's scroll state.
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (DashboardTab.entries[page]) {
                        DashboardTab.Overview -> OverviewTab(
                            uiState = uiState,
                            onRefresh = viewModel::refresh,
                            onOpenHistory = { showHistory = true },
                            onOpenSinceCharge = { showSinceCharge = true }
                        )

                        DashboardTab.Apps -> {
                            // The band reads the record only with extended access on, and only while shown.
                            val privacy = if (extendedAccessOn) {
                                privacyViewModel.state.collectAsStateWithLifecycle().value
                            } else {
                                PrivacyState()
                            }
                            AppsTab(
                                viewModel = appsViewModel,
                                privacy = privacy,
                                extendedAccessOn = extendedAccessOn,
                                onOpenPrivacy = { showPrivacy = true },
                                onOpenSystemApps = { showSystemApps = true },
                            )
                        }

                        DashboardTab.Device -> {
                            // The band reads the kernel only on a rooted phone, and only while shown.
                            val kernelBand = if (uiState.rootStatus == RootStatus.ON) {
                                kernelViewModel.band.collectAsStateWithLifecycle().value
                            } else {
                                KernelBandState()
                            }
                            DeviceTab(
                                uiState = uiState,
                                kernelBand = kernelBand,
                                onOpenKernel = { showKernel = true },
                            )
                        }

                        DashboardTab.Settings -> SettingsTab(
                            uiState = uiState,
                            onWidgetOpacityChange = viewModel::onWidgetOpacityChange,
                            onCommitWidgetOpacity = viewModel::commitWidgetOpacity,
                            onWidgetBlackBackgroundChange = viewModel::onWidgetBlackBackgroundChange,
                            onDataCounterModeSelected = viewModel::onDataCounterModeSelected,
                            onCycleStartDayChange = viewModel::onCycleStartDayChange,
                            onCommitCycleStartDay = viewModel::commitCycleStartDay,
                            onDataQuotaChange = viewModel::onDataQuotaChange,
                            onCommitDataQuota = viewModel::onCommitDataQuota,
                            onChargeLimitChange = viewModel::onChargeLimitChange,
                            onCommitChargeLimit = viewModel::onCommitChargeLimit,
                            onClassicLookChange = viewModel::onClassicLookChange,
                            onRootModeChange = viewModel::onRootModeChange,
                            onShizukuModeChange = viewModel::onShizukuModeChange,
                            onAlertToggle = viewModel::onAlertToggle,
                            onNotificationMetricToggle = viewModel::onNotificationMetricToggle,
                            onTileValueIconsChange = viewModel::onTileValueIconsChange,
                            onShowIntro = { replayOnboarding = true }
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
