package org.jarsi.devicewatch.presentation.ui

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
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.presentation.DashboardUiState

/** Device tab: static device facts plus the live SIM and Wi-Fi sections. */
@Composable
internal fun DeviceTab(uiState: DashboardUiState) {
    val currentStats = uiState.stats ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        uiState.deviceInfo?.let { info ->
            SettingsSectionCard(titleRes = R.string.device_info_section) {
                DeviceFact(R.string.device_info_manufacturer, info.manufacturer)
                DeviceFact(R.string.device_info_model, info.model)
                DeviceFact(R.string.device_info_codename, info.codename)
                DeviceFact(R.string.device_info_android, info.androidVersion)
                DeviceFact(R.string.device_info_security_patch, info.securityPatch)
                DeviceFact(R.string.device_info_build, info.buildNumber)
                DeviceFact(R.string.device_info_bootloader, info.bootloader)
                DeviceFact(R.string.device_info_radio, info.radioVersion)
                DeviceFact(R.string.device_info_soc, info.soc)
                DeviceFact(R.string.device_info_abis, info.supportedAbis)
                DeviceFact(R.string.device_info_kernel, info.kernelVersion)
                DeviceFact(R.string.device_info_gpu, info.gpuRenderer)
                DeviceFact(R.string.device_info_gl, info.glVersion)
                DeviceFact(R.string.device_info_resolution, info.screenResolution)
                DeviceFact(R.string.device_info_density, info.screenDensity)
                DeviceFact(R.string.device_info_physical_size, info.physicalSize)
                DeviceFact(R.string.device_info_refresh, info.refreshRate)
                DeviceFact(R.string.device_info_hdr, info.hdr)
                DeviceFact(R.string.device_info_ram, info.totalRam)
                DeviceFact(R.string.device_info_storage, info.totalStorage)
                DeviceFact(R.string.device_info_battery_tech, info.batteryTechnology)
                DeviceFact(R.string.device_info_battery_capacity, info.batteryCapacityMah)
            }

            SettingsSectionCard(titleRes = R.string.camera_info_section) {
                DeviceFact(R.string.camera_count, info.cameraCount)
                DeviceFact(R.string.camera_rear, info.rearCamera)
                DeviceFact(R.string.camera_front, info.frontCamera)
                DeviceFact(R.string.camera_flash, info.cameraFlash)
            }

            SettingsSectionCard(titleRes = R.string.sensors_section) {
                DeviceFact(R.string.sensors_count, info.sensorCount)
                DeviceFact(R.string.sensors_present, info.sensors)
            }

            SettingsSectionCard(titleRes = R.string.system_info_section) {
                DeviceFact(R.string.system_locale, info.locale)
                DeviceFact(R.string.system_timezone, info.timezone)
                DeviceFact(R.string.system_webview, info.webViewVersion)
                DeviceFact(R.string.system_play_services, info.playServicesVersion)
                DeviceFact(R.string.system_features, info.deviceFeatures)
                DeviceFact(R.string.device_info_boot_count, info.bootCountTotal)
            }
        }

        SettingsSectionCard(titleRes = R.string.sim_info_section) {
            DeviceFact(R.string.sim_operator, currentStats.operatorName)
            DeviceFact(R.string.sim_country, currentStats.networkCountry)
            DeviceFact(R.string.sim_network, currentStats.mobileNetworkType)
            DeviceFact(R.string.sim_signal, dbmText(currentStats.mobileSignalDbm))
            DeviceFact(R.string.sim_status, currentStats.simState)
            DeviceFact(R.string.sim_slots, countText(currentStats.simSlots))
            DeviceFact(simDataLabelRes(uiState.dataCounterMode), gbTodayText(currentStats.mobileDataUsedGb))
        }

        SettingsSectionCard(titleRes = R.string.wifi_info_section) {
            DeviceFact(R.string.wifi_name, currentStats.wifiSsidName)
            DeviceFact(R.string.wifi_band_label, currentStats.wifiBand)
            DeviceFact(R.string.wifi_standard, currentStats.wifiStandard)
            DeviceFact(R.string.wifi_signal, dbmText(currentStats.wifiRssiDbm))
            DeviceFact(R.string.wifi_link_speed, mbpsText(currentStats.wifiLinkSpeedMbps))
            DeviceFact(R.string.wifi_ip, currentStats.ipAddress)
            DeviceFact(wifiDataLabelRes(uiState.dataCounterMode), gbTodayText(currentStats.wifiBytesTodayGb))
            uiState.deviceInfo?.let { info ->
                DeviceFact(R.string.wifi_vpn, info.vpnActive)
                DeviceFact(R.string.wifi_dns, info.dnsServers)
            }
        }
    }
}
