package org.jarsi.devicewatch.presentation.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.AdbGrantCommands
import org.jarsi.devicewatch.data.AdbPermission
import org.jarsi.devicewatch.presentation.AdbStatus

/**
 * The third extended-access switch: the route that needs neither Shizuku nor
 * root, only permissions granted once from a computer. With any of them still
 * to grant the switch stays off and the row shows what to run, since nothing
 * on the phone can grant them.
 */
@Composable
internal fun AdbAccessRow(
    status: AdbStatus,
    missing: Set<AdbPermission>,
    packageName: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    SettingsToggleRow(
        titleRes = R.string.adb_mode_title,
        descriptionRes = R.string.adb_mode_description,
        checked = status == AdbStatus.ON || status == AdbStatus.STARTING,
        onCheckedChange = onCheckedChange,
    )
    when (status) {
        AdbStatus.ON -> {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.adb_status_on),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        AdbStatus.MISSING -> {
            val commands = remember(packageName) { AdbGrantCommands.commands(packageName).joinToString("\n") }
            // Named in the order the commands come in.
            val names = AdbPermission.entries.filter { it in missing }.joinToString { it.name }
            val clipLabel = stringResource(R.string.adb_mode_title)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.adb_status_missing, names),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(6.dp))
            // All three, whichever are missing: granting one again does no harm,
            // and the same three lines are what the documentation gives.
            SelectionContainer {
                Text(
                    text = commands,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            TextButton(
                onClick = withTapHaptic {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText(clipLabel, commands))
                },
            ) {
                Text(stringResource(R.string.adb_copy_commands))
            }
        }
        AdbStatus.OFF, AdbStatus.STARTING -> Unit
    }
}
