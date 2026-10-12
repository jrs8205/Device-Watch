package org.jarsi.devicewatch.presentation.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.presentation.KernelSummary
import org.jarsi.devicewatch.presentation.capOverMaxText
import org.jarsi.devicewatch.presentation.profileLabelRes

/** The Device tab's first band on a rooted phone: the profile at a glance, and the way into the Kernel page. */
@Composable
internal fun KernelBand(summary: KernelSummary?, onOpen: () -> Unit) {
    val locale = LocalLocale.current.platformLocale
    SettingsSectionCard(
        titleRes = R.string.kernel_section,
        trailing = {
            SectionLink(
                text = stringResource(R.string.kernel_open),
                contentDescription = stringResource(R.string.kernel_open_description),
                onClick = onOpen,
            )
        },
    ) {
        when {
            summary == null -> KernelNote(stringResource(R.string.kernel_reading))
            !summary.supported -> KernelNote(stringResource(R.string.kernel_not_supported))
            else -> {
                KernelRow(R.string.kernel_band_profile, stringResource(profileLabelRes(summary.profile)))
                if (summary.bigCapKHz != null && summary.bigMaxKHz != null) {
                    KernelRow(R.string.kernel_band_big_limit, capOverMaxText(summary.bigCapKHz, summary.bigMaxKHz, locale), numeric = true)
                }
                KernelRow(
                    R.string.kernel_band_throttling,
                    stringResource(if (summary.throttled) R.string.kernel_throttling_active else R.string.kernel_throttling_none),
                    color = if (summary.throttled) statusWarnColor() else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** A label and its value on one line, the value in the number face when it is a reading. */
@Composable
internal fun KernelRow(
    @StringRes labelRes: Int,
    value: String,
    numeric: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    KernelRow(stringResource(labelRes), value, numeric, color)
}

@Composable
internal fun KernelRow(
    label: String,
    value: String,
    numeric: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    LabelValueRow(
        modifier = Modifier.padding(vertical = 5.dp),
        label = { MetricLabel(label) },
        value = {
            if (numeric) {
                MetricValue(value, fontSize = 15.sp, color = color)
            } else {
                Text(text = value, fontSize = 15.sp, color = color)
            }
        },
    )
}

/** A quiet sentence that explains or qualifies what is around it. */
@Composable
internal fun KernelNote(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text = text, fontSize = 12.sp, color = color, modifier = Modifier.padding(vertical = 4.dp))
}

/** The kernel's own name for a thing, small, under the plain one. */
@Composable
internal fun KernelTechnical(text: String) {
    Text(text = text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
