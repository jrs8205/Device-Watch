package org.jarsi.devicewatch.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.jarsi.devicewatch.R

/**
 * The Apps tab's way into the system-apps page. With Shizuku or root on it opens
 * the page; without, the adb route included, it says what the page would do and
 * what it takes.
 */
@Composable
internal fun SystemAppsBand(shellAccessOn: Boolean, onOpen: () -> Unit) {
    SettingsSectionCard(
        titleRes = R.string.system_apps_section,
        trailing = if (shellAccessOn) {
            {
                SectionLink(
                    text = stringResource(R.string.system_apps_open),
                    contentDescription = stringResource(R.string.system_apps_open_description),
                    onClick = onOpen,
                )
            }
        } else {
            null
        },
    ) {
        KernelNote(stringResource(if (shellAccessOn) R.string.system_apps_band_on else R.string.system_apps_band_off))
    }
}
