package org.jarsi.devicewatch.presentation.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.presentation.PrivacyPresentation
import org.jarsi.devicewatch.presentation.PrivacyState

/**
 * The Apps tab's first band. With extended access on it says, sensor by sensor,
 * how many apps used it and when the last one did, and opens the privacy log;
 * without, it explains and offers Android's own Privacy dashboard instead.
 */
@Composable
internal fun PrivacyBand(
    state: PrivacyState,
    extendedAccessOn: Boolean,
    onOpen: () -> Unit,
    onOpenDashboard: () -> Unit,
) {
    SettingsSectionCard(
        titleRes = R.string.privacy_section,
        trailing = if (extendedAccessOn) {
            {
                SectionLink(
                    text = stringResource(R.string.privacy_open),
                    contentDescription = stringResource(R.string.privacy_open_description),
                    onClick = onOpen,
                )
            }
        } else {
            null
        },
    ) {
        if (!extendedAccessOn) {
            KernelNote(stringResource(R.string.privacy_band_off))
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = withTapHaptic(onOpenDashboard),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.privacy_dashboard_button), fontSize = 12.sp)
            }
            return@SettingsSectionCard
        }
        val read = state.read
        val log = read?.log
        when {
            read == null && state.readFailed -> KernelNote(stringResource(R.string.privacy_read_failed), color = statusWarnColor())
            read == null -> KernelNote(stringResource(R.string.privacy_reading))
            read.unsupported || log == null -> KernelNote(stringResource(R.string.privacy_unsupported))
            else -> {
                if (state.readFailed) KernelNote(stringResource(R.string.privacy_read_failed_stale), color = statusWarnColor())
                PrivacyPresentation.bandLines(log).forEach { line ->
                    val value = if (line.apps == 0 || line.lastMillis == null) {
                        stringResource(R.string.privacy_band_none)
                    } else {
                        pluralStringResource(R.plurals.privacy_band_apps, line.apps, line.apps) + " · " +
                            stringResource(R.string.privacy_last, privacyMomentText(line.lastMillis))
                    }
                    KernelRow(stringResource(PrivacyPresentation.sensorNameRes(line.sensor)), value)
                }
            }
        }
    }
}
