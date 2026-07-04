package com.example.modernwidget.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.modernwidget.R
import com.example.modernwidget.presentation.DashboardUiState

/** Device tab: static device facts plus the live SIM and Wi-Fi sections. */
@Composable
internal fun DeviceTab(uiState: DashboardUiState) {
    val currentStats = uiState.stats ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        uiState.deviceInfo?.let { info ->
            SettingsSectionCard(titleRes = R.string.device_info_section) {
                DeviceInfoRow(R.string.device_info_manufacturer, info.manufacturer)
                DeviceInfoRow(R.string.device_info_model, info.model)
                DeviceInfoRow(R.string.device_info_codename, info.codename)
                DeviceInfoRow(R.string.device_info_android, info.androidVersion)
                DeviceInfoRow(R.string.device_info_security_patch, info.securityPatch)
                DeviceInfoRow(R.string.device_info_build, info.buildNumber)
                DeviceInfoRow(R.string.device_info_bootloader, info.bootloader)
                DeviceInfoRow(R.string.device_info_radio, info.radioVersion)
                DeviceInfoRow(R.string.device_info_soc, info.soc)
                DeviceInfoRow(R.string.device_info_abis, info.supportedAbis)
                DeviceInfoRow(R.string.device_info_kernel, info.kernelVersion)
                DeviceInfoRow(R.string.device_info_gpu, info.gpuRenderer)
                DeviceInfoRow(R.string.device_info_gl, info.glVersion)
                DeviceInfoRow(R.string.device_info_resolution, info.screenResolution)
                DeviceInfoRow(R.string.device_info_density, info.screenDensity)
                DeviceInfoRow(R.string.device_info_physical_size, info.physicalSize)
                DeviceInfoRow(R.string.device_info_refresh, info.refreshRate)
                DeviceInfoRow(R.string.device_info_hdr, info.hdr)
                DeviceInfoRow(R.string.device_info_ram, info.totalRam)
                DeviceInfoRow(R.string.device_info_storage, info.totalStorage)
                DeviceInfoRow(R.string.device_info_battery_tech, info.batteryTechnology)
                DeviceInfoRow(R.string.device_info_battery_capacity, info.batteryCapacityMah)
            }

            SettingsSectionCard(titleRes = R.string.camera_info_section) {
                DeviceInfoRow(R.string.camera_count, info.cameraCount)
                DeviceInfoRow(R.string.camera_rear, info.rearCamera)
                DeviceInfoRow(R.string.camera_front, info.frontCamera)
                DeviceInfoRow(R.string.camera_flash, info.cameraFlash)
            }

            SettingsSectionCard(titleRes = R.string.sensors_section) {
                DeviceInfoRow(R.string.sensors_count, info.sensorCount)
                DeviceInfoRow(R.string.sensors_present, info.sensors)
            }

            SettingsSectionCard(titleRes = R.string.system_info_section) {
                DeviceInfoRow(R.string.system_locale, info.locale)
                DeviceInfoRow(R.string.system_timezone, info.timezone)
                DeviceInfoRow(R.string.system_webview, info.webViewVersion)
                DeviceInfoRow(R.string.system_play_services, info.playServicesVersion)
                DeviceInfoRow(R.string.system_features, info.deviceFeatures)
            }
        }

        SettingsSectionCard(titleRes = R.string.sim_info_section) {
            DeviceInfoRow(R.string.sim_operator, currentStats.operatorName)
            DeviceInfoRow(R.string.sim_country, currentStats.networkCountry)
            DeviceInfoRow(R.string.sim_network, currentStats.mobileNetworkType)
            DeviceInfoRow(R.string.sim_signal, dbmText(currentStats.mobileSignalDbm))
            DeviceInfoRow(R.string.sim_status, currentStats.simState)
            DeviceInfoRow(R.string.sim_slots, countText(currentStats.simSlots))
            DeviceInfoRow(simDataLabelRes(uiState.dataCounterMode), gbTodayText(currentStats.mobileDataUsedGb))
        }

        SettingsSectionCard(titleRes = R.string.wifi_info_section) {
            DeviceInfoRow(R.string.wifi_name, currentStats.wifiSsidName)
            DeviceInfoRow(R.string.wifi_band_label, currentStats.wifiBand)
            DeviceInfoRow(R.string.wifi_standard, currentStats.wifiStandard)
            DeviceInfoRow(R.string.wifi_signal, dbmText(currentStats.wifiRssiDbm))
            DeviceInfoRow(R.string.wifi_link_speed, mbpsText(currentStats.wifiLinkSpeedMbps))
            DeviceInfoRow(R.string.wifi_ip, currentStats.ipAddress)
            DeviceInfoRow(wifiDataLabelRes(uiState.dataCounterMode), gbTodayText(currentStats.wifiBytesTodayGb))
            uiState.deviceInfo?.let { info ->
                DeviceInfoRow(R.string.wifi_vpn, info.vpnActive)
                DeviceInfoRow(R.string.wifi_dns, info.dnsServers)
            }
        }
    }
}
