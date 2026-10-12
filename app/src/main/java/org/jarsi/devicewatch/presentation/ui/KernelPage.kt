package org.jarsi.devicewatch.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.ApplyOutcome
import org.jarsi.devicewatch.data.CpuPolicyId
import org.jarsi.devicewatch.data.CpuProfile
import org.jarsi.devicewatch.data.CpuProfileNotice
import org.jarsi.devicewatch.data.CpuProfilePlanner
import org.jarsi.devicewatch.data.GpuSnapshot
import org.jarsi.devicewatch.data.KernelSnapshot
import org.jarsi.devicewatch.data.SelinuxMode
import org.jarsi.devicewatch.data.ZramSnapshot
import org.jarsi.devicewatch.presentation.ClusterRole
import org.jarsi.devicewatch.presentation.CoolingKind
import org.jarsi.devicewatch.presentation.KernelPageState
import org.jarsi.devicewatch.presentation.KernelViewModel
import org.jarsi.devicewatch.presentation.capOverMaxText
import org.jarsi.devicewatch.presentation.clusterRoles
import org.jarsi.devicewatch.presentation.cpuRangeText
import org.jarsi.devicewatch.presentation.freqText
import org.jarsi.devicewatch.presentation.profileLabelRes
import org.jarsi.devicewatch.presentation.thermalZoneLabelRes
import kotlin.math.roundToInt

/**
 * The Kernel page, on rooted phones only: the processor profile, and what the
 * kernel says of the processor, heat, graphics and memory, read live while open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KernelPage(
    onBack: () -> Unit,
    viewModel: KernelViewModel = hiltViewModel(),
) {
    val state by viewModel.page.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.kernel_title), fontWeight = FontWeight.Bold) },
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
            val snapshot = state.snapshot
            if (state.readFailed) {
                item(key = "read_failed") {
                    Column(modifier = Modifier.padding(horizontal = BAND_INSET, vertical = 8.dp)) {
                        KernelNote(stringResource(R.string.kernel_read_failed), color = statusWarnColor())
                    }
                }
            }
            if (snapshot == null) {
                if (!state.readFailed) {
                    item(key = "reading") {
                        Column(modifier = Modifier.padding(horizontal = BAND_INSET, vertical = BAND_SPACING)) {
                            KernelNote(stringResource(R.string.kernel_reading))
                        }
                    }
                }
                return@LazyColumn
            }
            item(key = "profile") {
                ProfileSection(
                    state = state,
                    snapshot = snapshot,
                    onSelect = viewModel::select,
                    onCustomStep = viewModel::onCustomStep,
                    onApplyCustom = viewModel::applyCustom,
                    onCancelCustom = viewModel::cancelCustom,
                    onApplyAfterRestart = viewModel::setApplyAfterRestart,
                )
            }
            item(key = "processor") { ProcessorSection(snapshot, state) }
            item(key = "throttling") { ThrottlingSection(snapshot) }
            snapshot.gpu?.let { gpu -> item(key = "gpu") { GpuSection(gpu) } }
            snapshot.zram?.let { zram -> item(key = "zram") { MemorySection(zram) } }
            item(key = "system") { SystemSection(snapshot) }
            if (snapshot.thermalZones.isNotEmpty()) {
                item(key = "temps") { TemperatureSection(snapshot) }
            }
        }
    }
}

private val PROFILE_ORDER = listOf(CpuProfile.POWER_SAVING, CpuProfile.BALANCED, CpuProfile.STOCK, CpuProfile.CUSTOM)

@Composable
private fun ProfileSection(
    state: KernelPageState,
    snapshot: KernelSnapshot,
    onSelect: (CpuProfile) -> Unit,
    onCustomStep: (CpuPolicyId, Long) -> Unit,
    onApplyCustom: () -> Unit,
    onCancelCustom: () -> Unit,
    onApplyAfterRestart: (Boolean) -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    val presets = CpuProfilePlanner.presetsAvailable(snapshot.policies)
    val anySteps = snapshot.policies.any { it.online && it.steps.isNotEmpty() }
    SettingsSectionCard(titleRes = R.string.kernel_profile_section) {
        if (snapshot.policies.isEmpty()) {
            KernelNote(stringResource(R.string.kernel_no_cpufreq))
            return@SettingsSectionCard
        }
        Column(modifier = Modifier.selectableGroup()) {
            for (profile in PROFILE_ORDER) {
                val enabled = !state.busy && when (profile) {
                    CpuProfile.STOCK -> true
                    CpuProfile.CUSTOM -> anySteps
                    else -> presets
                }
                val selected = if (state.editingCustom) profile == CpuProfile.CUSTOM else state.status.active == profile
                ProfileOption(profile, selected, enabled) { onSelect(profile) }
            }
        }
        if (!presets) KernelNote(stringResource(R.string.cpu_profile_presets_unavailable))

        if (state.editingCustom) {
            Spacer(modifier = Modifier.height(8.dp))
            CustomEditor(state, snapshot, onCustomStep, onApplyCustom, onCancelCustom)
        }

        Spacer(modifier = Modifier.height(6.dp))
        if (state.busy) {
            KernelNote(stringResource(R.string.cpu_profile_applying), color = MaterialTheme.colorScheme.primary)
        }
        outcomeText(state.lastOutcome, state.outcomeAwaitsMonitor, snapshot)?.let { KernelNote(it, color = statusWarnColor()) }
        noticeRes(state.status.notice)?.let { KernelNote(stringResource(it), color = statusWarnColor()) }
        if (state.status.active != CpuProfile.STOCK && state.status.overrideCount > 0) {
            KernelNote(pluralStringResource(R.plurals.cpu_profile_overrides, state.status.overrideCount, state.status.overrideCount))
        }

        Spacer(modifier = Modifier.height(10.dp))
        SettingsToggleRow(
            titleRes = R.string.cpu_profile_after_restart_title,
            descriptionRes = R.string.cpu_profile_after_restart_desc,
            checked = state.status.applyAfterRestart,
            onCheckedChange = onApplyAfterRestart,
        )
        Spacer(modifier = Modifier.height(8.dp))
        KernelNote(stringResource(R.string.cpu_profile_explanation))
        // The held limits, so the choice reads as numbers too.
        if (state.status.held.isNotEmpty()) {
            val roles = clusterRoles(snapshot.policies)
            snapshot.policies.filter { it.id in state.status.held }.forEach { policy ->
                val role = roles[policy.id] ?: ClusterRole.ONLY
                KernelRow(
                    stringResource(role.labelRes),
                    freqText(state.status.held.getValue(policy.id), locale),
                    numeric = true,
                )
            }
        }
    }
}

@Composable
private fun ProfileOption(profile: CpuProfile, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val descRes = when (profile) {
        CpuProfile.POWER_SAVING -> R.string.cpu_profile_power_saving_desc
        CpuProfile.BALANCED -> R.string.cpu_profile_balanced_desc
        CpuProfile.STOCK -> R.string.cpu_profile_stock_desc
        CpuProfile.CUSTOM -> R.string.cpu_profile_custom_desc
    }
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = withTapHaptic(onClick))
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(profileLabelRes(profile)),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(descRes),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
    }
}

@Composable
private fun CustomEditor(
    state: KernelPageState,
    snapshot: KernelSnapshot,
    onCustomStep: (CpuPolicyId, Long) -> Unit,
    onApplyCustom: () -> Unit,
    onCancelCustom: () -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    val roles = clusterRoles(snapshot.policies)
    for (policy in snapshot.policies.filter { it.online }) {
        val steps = CpuProfilePlanner.customSteps(policy, state.status.originals[policy.id])
        if (steps.isEmpty()) continue
        val value = state.draft[policy.id] ?: steps.last()
        val index = steps.indexOf(value).takeIf { it >= 0 } ?: steps.indexOfLast { it <= value }.coerceAtLeast(0)
        val role = roles[policy.id] ?: ClusterRole.ONLY
        KernelRow(
            stringResource(R.string.kernel_cluster_title, stringResource(role.labelRes), cpuRangeText(policy.relatedCpus)),
            freqText(steps[index], locale),
            numeric = true,
        )
        if (steps.size > 1) {
            val speed = freqText(steps[index], locale)
            Slider(
                value = index.toFloat(),
                onValueChange = { onCustomStep(policy.id, steps[it.roundToInt().coerceIn(steps.indices)]) },
                valueRange = 0f..(steps.size - 1).toFloat(),
                steps = (steps.size - 2).coerceAtLeast(0),
                enabled = !state.busy,
                // TalkBack reads the speed, not a percentage of the slider.
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { stateDescription = speed },
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = withTapHaptic(onCancelCustom), enabled = !state.busy) {
            Text(stringResource(R.string.cpu_profile_cancel_custom))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = withTapHaptic(onApplyCustom), enabled = !state.busy && state.draft.isNotEmpty()) {
            Text(stringResource(R.string.cpu_profile_apply_custom))
        }
    }
}

@Composable
private fun outcomeText(outcome: ApplyOutcome?, awaitsMonitor: Boolean, snapshot: KernelSnapshot): String? = when (outcome) {
    null, ApplyOutcome.Applied, ApplyOutcome.NothingToChange -> null
    ApplyOutcome.HeldLower -> stringResource(R.string.cpu_profile_outcome_held_lower)
    ApplyOutcome.Unconfirmed -> stringResource(
        if (awaitsMonitor) R.string.cpu_profile_outcome_unconfirmed else R.string.cpu_profile_outcome_no_answer
    )
    ApplyOutcome.NoRoot -> stringResource(R.string.cpu_profile_outcome_no_root)
    is ApplyOutcome.NotSupported -> {
        val roles = clusterRoles(snapshot.policies)
        val names = outcome.ids.map { id -> stringResource((roles[id] ?: ClusterRole.ONLY).labelRes) }.distinct()
        stringResource(R.string.cpu_profile_outcome_not_supported, names.joinToString(", "))
    }
}

private fun noticeRes(notice: CpuProfileNotice?): Int? = when (notice) {
    null -> null
    CpuProfileNotice.RESET_BY_RESTART -> R.string.cpu_profile_notice_reset
    CpuProfileNotice.KEPT_UNTIL_RESTART -> R.string.cpu_profile_notice_kept
    CpuProfileNotice.ANDROID_KEEPS_CHANGING -> R.string.cpu_profile_notice_android
}

@Composable
private fun ProcessorSection(snapshot: KernelSnapshot, state: KernelPageState) {
    val locale = LocalLocale.current.platformLocale
    val roles = clusterRoles(snapshot.policies)
    SettingsSectionCard(titleRes = R.string.kernel_processor_section) {
        if (snapshot.policies.isEmpty()) {
            KernelNote(stringResource(R.string.kernel_no_cpufreq))
            return@SettingsSectionCard
        }
        snapshot.policies.forEachIndexed { index, policy ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                val role = roles[policy.id] ?: ClusterRole.ONLY
                Text(
                    text = stringResource(R.string.kernel_cluster_title, stringResource(role.labelRes), cpuRangeText(policy.relatedCpus)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                val max = policy.cpuinfoMaxKHz
                val limit = policy.scalingMaxKHz
                if (!policy.online || max == null || limit == null) {
                    KernelNote(stringResource(R.string.kernel_cluster_offline))
                    KernelTechnical(policy.id.name)
                    return@Column
                }
                policy.curKHz?.let { KernelRow(R.string.kernel_speed_now, freqText(it, locale), numeric = true) }
                val held = policy.id in state.status.held
                KernelRow(
                    R.string.kernel_speed_limit,
                    capOverMaxText(limit, max, locale),
                    numeric = true,
                    color = if (held || limit < max) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                policy.cpuinfoMinKHz?.let { min ->
                    KernelRow(
                        R.string.kernel_speed_range,
                        stringResource(R.string.kernel_range_value, freqText(min, locale), freqText(max, locale)),
                        numeric = true,
                    )
                }
                // Worth a row only when the phone's own limit is not simply the maximum.
                state.status.originals[policy.id]?.let { original ->
                    val assumed = policy.id in state.status.originalsAssumed
                    if (original < max || assumed) {
                        val text = freqText(original, locale)
                        KernelRow(
                            R.string.kernel_phone_limit,
                            if (assumed) stringResource(R.string.kernel_phone_limit_assumed, text) else text,
                            numeric = !assumed,
                        )
                    }
                }
                if (policy.steps.isEmpty()) {
                    KernelNote(stringResource(R.string.kernel_cluster_cannot_limit), color = statusWarnColor())
                }
                KernelTechnical(
                    stringResource(R.string.kernel_cluster_technical, policy.id.name, policy.governor ?: "–", policy.driver ?: "–")
                )
            }
        }
        KernelNote(stringResource(R.string.kernel_processor_note))
    }
}

@Composable
private fun ThrottlingSection(snapshot: KernelSnapshot) {
    val active = snapshot.coolingDevices.filter { it.cur > 0 }
    // thermal-cpufreq-N follows the kernel's own policy order, by policy number.
    val byNumber = snapshot.policies.sortedBy { it.id.name.removePrefix("policy").toInt() }
    val roles = clusterRoles(snapshot.policies)
    SettingsSectionCard(titleRes = R.string.kernel_throttling_section) {
        if (active.isEmpty()) {
            KernelNote(stringResource(R.string.kernel_throttling_none_long), color = statusOkColor())
            return@SettingsSectionCard
        }
        for (device in active) {
            val label = when (val kind = CoolingKind.of(device.type)) {
                is CoolingKind.CpuCluster -> {
                    val role = byNumber.getOrNull(kind.index)?.let { roles[it.id] } ?: ClusterRole.ONLY
                    stringResource(R.string.cooling_cpu, stringResource(role.labelRes))
                }
                is CoolingKind.CpuCore -> {
                    val role = snapshot.policies.firstOrNull { kind.cpu in it.relatedCpus }?.let { roles[it.id] } ?: ClusterRole.ONLY
                    stringResource(R.string.cooling_cpu, stringResource(role.labelRes))
                }
                CoolingKind.Gpu -> stringResource(R.string.cooling_gpu)
                CoolingKind.ChargingCurrent -> stringResource(R.string.cooling_charging)
                CoolingKind.Other -> device.type
            }
            KernelRow(
                label,
                stringResource(R.string.kernel_cooling_level, device.cur, device.max),
                numeric = true,
                color = statusWarnColor(),
            )
            KernelTechnical(device.type)
        }
    }
}

@Composable
private fun GpuSection(gpu: GpuSnapshot) {
    val locale = LocalLocale.current.platformLocale
    SettingsSectionCard(titleRes = R.string.kernel_gpu_section) {
        gpu.curKHz?.let { KernelRow(R.string.kernel_speed_now, freqText(it, locale), numeric = true) }
        gpu.maxKHz?.let { KernelRow(R.string.kernel_gpu_max, freqText(it, locale), numeric = true) }
        val min = gpu.minKHz
        val max = gpu.maxKHz
        if (min != null && max != null) {
            KernelRow(
                R.string.kernel_speed_range,
                stringResource(R.string.kernel_range_value, freqText(min, locale), freqText(max, locale)),
                numeric = true,
            )
        }
        gpu.powerPolicy?.let { KernelRow(R.string.kernel_gpu_power_policy, it) }
    }
}

@Composable
private fun MemorySection(zram: ZramSnapshot) {
    val locale = LocalLocale.current.platformLocale
    SettingsSectionCard(titleRes = R.string.kernel_memory_section) {
        KernelNote(stringResource(R.string.kernel_zram_note))
        zram.disksizeBytes?.let { KernelRow(R.string.kernel_zram_size, bytesText(it), numeric = true) }
        val orig = zram.origBytes
        val compr = zram.comprBytes
        val ratio = zram.ratio
        if (orig != null && compr != null && ratio != null) {
            KernelRow(
                R.string.kernel_zram_stored,
                stringResource(R.string.kernel_zram_stored_value, bytesText(orig), bytesText(compr), String.format(locale, "%.1f", ratio)),
                numeric = true,
            )
        }
        zram.algorithm?.let { KernelRow(R.string.kernel_zram_algorithm, it) }
    }
}

@Composable
private fun SystemSection(snapshot: KernelSnapshot) {
    SettingsSectionCard(titleRes = R.string.kernel_system_section) {
        snapshot.kernelRelease?.let { DeviceFact(R.string.kernel_release, it) }
        snapshot.selinux?.let { mode ->
            DeviceFact(
                R.string.kernel_selinux,
                stringResource(if (mode == SelinuxMode.ENFORCING) R.string.kernel_selinux_enforcing else R.string.kernel_selinux_permissive),
            )
        }
        val schedulers = snapshot.ioSchedulers.values.distinct()
        if (schedulers.isNotEmpty()) DeviceFact(R.string.kernel_io_scheduler, schedulers.joinToString(", "))
    }
}

@Composable
private fun TemperatureSection(snapshot: KernelSnapshot) {
    SettingsSectionCard(titleRes = R.string.kernel_temps_section) {
        for (zone in snapshot.thermalZones) {
            val plain = thermalZoneLabelRes(zone.type)
            KernelRow(
                if (plain != null) stringResource(plain) else zone.type,
                celsiusText(zone.celsius),
                numeric = true,
            )
            if (plain != null) KernelTechnical(zone.type)
        }
    }
}
