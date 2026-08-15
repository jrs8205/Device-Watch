package org.jarsi.devicewatch.presentation.ui

import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.presentation.AppsViewModel
import org.jarsi.devicewatch.presentation.DashboardViewModel
import org.jarsi.devicewatch.system.SystemMonitorService

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
        viewModel.loadWidgetOpacity()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.main_title),
                        fontWeight = FontWeight.Bold
                    )
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
        },
        bottomBar = {
            val density = LocalDensity.current
            val barDensity = remember(density) {
                Density(density.density, clampedNavBarFontScale(density.fontScale))
            }
            CompositionLocalProvider(LocalDensity provides barDensity) {
                NavigationBar {
                    DashboardTab.entries.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = index == pagerState.currentPage,
                            onClick = withTapHaptic {
                                scope.launch { pagerState.animateScrollToPage(index) }
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

                        DashboardTab.Apps -> AppsTab(viewModel = appsViewModel)

                        DashboardTab.Device -> DeviceTab(uiState = uiState)

                        DashboardTab.Settings -> SettingsTab(
                            uiState = uiState,
                            onWidgetOpacityChange = viewModel::onWidgetOpacityChange,
                            onCommitWidgetOpacity = viewModel::commitWidgetOpacity,
                            onDataCounterModeSelected = viewModel::onDataCounterModeSelected,
                            onCycleStartDayChange = viewModel::onCycleStartDayChange,
                            onCommitCycleStartDay = viewModel::commitCycleStartDay,
                            onDataQuotaChange = viewModel::onDataQuotaChange,
                            onCommitDataQuota = viewModel::onCommitDataQuota,
                            onChargeLimitChange = viewModel::onChargeLimitChange,
                            onCommitChargeLimit = viewModel::onCommitChargeLimit,
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
