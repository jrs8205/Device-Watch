package org.jarsi.devicewatch.presentation.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.SuspendStats
import org.jarsi.devicewatch.data.WakeCategory
import org.jarsi.devicewatch.data.WakeSnapshot
import org.jarsi.devicewatch.data.WakeupSource
import org.jarsi.devicewatch.presentation.WakePresentation
import org.jarsi.devicewatch.presentation.WakeupSourceKind

/**
 * The Kernel page's sleep section: how often the phone slept and failed to, where
 * and on what the last attempt failed, which sources interrupted its sleep most,
 * which held it awake longest, and which are holding it now. Root only, counted
 * since the phone started.
 */
@Composable
internal fun SleepSection(wake: WakeSnapshot?, readFailed: Boolean) {
    SettingsSectionCard(titleRes = R.string.kernel_sleep_section) {
        KernelNote(stringResource(R.string.kernel_sleep_note))
        if (readFailed) KernelNote(stringResource(R.string.kernel_sleep_read_failed), color = statusWarnColor())
        when {
            wake == null -> if (!readFailed) KernelNote(stringResource(R.string.kernel_sleep_reading))
            wake.empty -> KernelNote(stringResource(R.string.kernel_sleep_unavailable))
            else -> {
                wake.suspend?.let { SuspendRows(it) }
                SourceList(R.string.kernel_sleep_top_wakers, WakePresentation.topWakers(wake)) { source ->
                    val count = source.wakeupCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    pluralStringResource(R.plurals.kernel_wakeups, count, count)
                }
                val longest = WakePresentation.longestHolders(wake)
                SourceList(R.string.kernel_sleep_longest, longest) { source ->
                    stringResource(R.string.kernel_sleep_awake_total, shortDurationText(source.totalTimeMs))
                }
                if (longest.any { (WakePresentation.kindOf(it.name) as? WakeupSourceKind.Part)?.category == WakeCategory.INPUT }) {
                    KernelNote(stringResource(R.string.wake_source_touch_note))
                }
                val active = WakePresentation.activeNow(wake)
                if (active.isNotEmpty()) {
                    SourceList(R.string.kernel_sleep_active_now, active) { source ->
                        stringResource(R.string.kernel_sleep_held_for, shortDurationText(source.activeTimeMs))
                    }
                }
            }
        }
    }
}

/**
 * The attempts, then the last failure's step and device on rows of their own: the
 * kernel updates the two independently (a failed freeze names no device, a device's
 * refusal may be older than the last failed step), so neither stands for the other.
 */
@Composable
private fun SuspendRows(stats: SuspendStats) {
    val slept = stats.success.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val failed = stats.fail.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    KernelRow(
        R.string.kernel_sleep_attempts,
        pluralStringResource(R.plurals.kernel_sleep_slept, slept, slept) + " · " + pluralStringResource(R.plurals.kernel_sleep_failed, failed, failed),
        numeric = true,
        color = if (stats.fail > stats.success) statusWarnColor() else MaterialTheme.colorScheme.onSurface,
    )
    val stepName = stats.lastFailedStep
    val step = WakePresentation.stepOf(stepName)
    if (stepName != null) {
        KernelRow(R.string.kernel_sleep_last_step, step?.let { stringResource(WakePresentation.stepNameRes(it)) } ?: stepName)
        if (step != null) {
            KernelTechnical(stepName)
            KernelNote(stringResource(WakePresentation.stepExplanationRes(step)))
        }
    }
    val device = stats.lastFailedDev
    if (device != null) {
        val plain = sourcePlainName(device)
        KernelRow(R.string.kernel_sleep_last_device, plain)
        if (plain != device) KernelTechnical(device)
    }
}

@Composable
private fun SourceList(@StringRes titleRes: Int, sources: List<WakeupSource>, value: @Composable (WakeupSource) -> String) {
    if (sources.isEmpty()) return
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = stringResource(titleRes), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    sources.forEach { source ->
        val plain = sourcePlainName(source.name)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = plain, fontSize = 14.sp)
                if (plain != source.name) KernelTechnical(source.name)
            }
            Spacer(modifier = Modifier.width(12.dp))
            MetricValue(value(source), fontSize = 13.sp)
        }
    }
}

/** A wakeup source in plain words where the glossary has them, else as the kernel names it. */
@Composable
private fun sourcePlainName(name: String): String = when (val kind = WakePresentation.kindOf(name)) {
    is WakeupSourceKind.Process -> WakePresentation.processNameRes(kind.process)?.let { stringResource(it) }
        ?: stringResource(R.string.wake_process_other, kind.process)
    is WakeupSourceKind.Part -> stringResource(wakeCategoryRes(kind.category))
    WakeupSourceKind.Unknown -> name
}
