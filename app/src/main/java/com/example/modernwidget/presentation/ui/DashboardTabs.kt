package com.example.modernwidget.presentation.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.modernwidget.R
import com.example.modernwidget.presentation.AppsViewModel
import com.example.modernwidget.presentation.DashboardViewModel
import com.example.modernwidget.system.SystemMonitorService

/** The dashboard's flat bottom-navigation destinations, in display order. */
internal enum class DashboardTab(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Overview(R.string.tab_overview, Icons.Filled.Dashboard),
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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

    fun missingRuntimePermissions(): Array<String> {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        return permissions
            .filter { permission ->
                ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()
    }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        startSystemMonitorService()
        viewModel.refresh()
    }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val safeTabIndex = selectedTabIndex.coerceIn(0, DashboardTab.entries.lastIndex)
    val selectedTab = DashboardTab.entries[safeTabIndex]
    val tabStateHolder = rememberSaveableStateHolder()

    // Historia full-screen page, reached from the Overview usage-counters card.
    var showHistory by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showHistory) { showHistory = false }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        viewModel.loadWidgetOpacity()
        viewModel.loadDataCounterSettings()
        viewModel.loadDeviceInfo()
        val missingPermissions = missingRuntimePermissions()
        if (missingPermissions.isNotEmpty()) {
            runtimePermissionLauncher.launch(missingPermissions)
        } else {
            startSystemMonitorService()
        }
    }

    if (showHistory) {
        HistoryPage(onBack = { showHistory = false })
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
                    IconButton(onClick = {
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
            NavigationBar {
                DashboardTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = index == safeTabIndex,
                        onClick = { selectedTabIndex = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
    ) { paddingValues ->
        if (uiState.stats != null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // Each tab keeps its own scroll position across tab switches.
                tabStateHolder.SaveableStateProvider(selectedTab.name) {
                    when (selectedTab) {
                        DashboardTab.Overview -> OverviewTab(
                            uiState = uiState,
                            onRefresh = viewModel::refresh,
                            onOpenHistory = { showHistory = true }
                        )

                        DashboardTab.Apps -> AppsTab(viewModel = appsViewModel)

                        DashboardTab.Device -> DeviceTab(uiState = uiState)

                        DashboardTab.Settings -> SettingsTab(
                            uiState = uiState,
                            onWidgetOpacityChange = viewModel::onWidgetOpacityChange,
                            onCommitWidgetOpacity = viewModel::commitWidgetOpacity,
                            onDataCounterModeSelected = viewModel::onDataCounterModeSelected,
                            onCycleStartDayChange = viewModel::onCycleStartDayChange,
                            onCommitCycleStartDay = viewModel::commitCycleStartDay
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
