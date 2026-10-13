package org.jarsi.devicewatch.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.AccessEvent
import org.jarsi.devicewatch.data.AppSensorUse
import org.jarsi.devicewatch.data.PrivacySensor
import org.jarsi.devicewatch.data.SensorUse
import org.jarsi.devicewatch.presentation.PrivacyPresentation
import org.jarsi.devicewatch.presentation.PrivacyViewModel
import org.jarsi.devicewatch.presentation.RelativeDay
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Android's own record of private things used, app by app: the camera, the
 * microphone, the location, and whatever else Android counts. Read through
 * extended access; shown, never stored.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPage(
    onBack: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // One app's moments open at a time, keyed by sensor and app, kept across rotation.
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_title), fontWeight = FontWeight.Bold) },
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
            val read = state.read
            val log = read?.log
            if (state.readFailed) {
                item(key = "read_failed") {
                    PageNote(stringResource(if (read == null) R.string.privacy_read_failed else R.string.privacy_read_failed_stale), warn = true)
                }
            }
            if (read == null) {
                if (!state.readFailed) item(key = "reading") { PageNote(stringResource(R.string.privacy_reading)) }
                return@LazyColumn
            }
            if (read.unsupported || log == null) {
                item(key = "unsupported") { PageNote(stringResource(R.string.privacy_unsupported)) }
                return@LazyColumn
            }
            item(key = "note") {
                Column(modifier = Modifier.padding(horizontal = BAND_INSET, vertical = 8.dp)) {
                    KernelNote(stringResource(R.string.privacy_page_note))
                    if (log.minuteQuantized) KernelNote(stringResource(R.string.privacy_minute_note))
                    log.oldestMillis?.let { KernelNote(stringResource(R.string.privacy_span, privacyMomentText(it))) }
                }
            }
            if (log.empty) {
                item(key = "empty") { PageNote(stringResource(R.string.privacy_empty)) }
            }
            log.sensors.forEach { use ->
                item(key = "sensor_${use.sensor.name}") {
                    SensorSection(
                        use = use,
                        minuteKept = log.minuteQuantized,
                        expandedKey = expandedKey,
                        onToggle = { key -> expandedKey = if (expandedKey == key) null else key },
                    )
                }
            }
        }
    }
}

/** Whether a length means the sensor was on that long: true of the camera and the microphone. */
private val PrivacySensor.hasMeaningfulDuration: Boolean
    get() = this == PrivacySensor.CAMERA || this == PrivacySensor.MICROPHONE

@Composable
private fun PageNote(text: String, warn: Boolean = false) {
    Column(modifier = Modifier.padding(horizontal = BAND_INSET, vertical = 8.dp)) {
        KernelNote(text, color = if (warn) statusWarnColor() else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SensorSection(use: SensorUse, minuteKept: Boolean, expandedKey: String?, onToggle: (String) -> Unit) {
    SettingsSectionCard(titleRes = PrivacyPresentation.sensorNameRes(use.sensor)) {
        if (use.sensor == PrivacySensor.OTHER) KernelNote(stringResource(R.string.privacy_sensor_other_note))
        if (use.empty) {
            KernelNote(stringResource(R.string.privacy_sensor_empty))
            return@SettingsSectionCard
        }
        // Two apps called "Camera" tell apart only by their package names.
        val sharedLabels = (use.apps + use.denied).groupBy { it.label }.filterValues { it.size > 1 }.keys
        use.apps.forEach { app ->
            val key = "${use.sensor.name}/${app.uid}/${app.packageName}"
            AppUseRow(app, use.sensor, minuteKept, showPackage = app.label in sharedLabels, expanded = expandedKey == key, onToggle = { onToggle(key) })
        }
        if (use.denied.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = stringResource(R.string.privacy_denied_title), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            KernelNote(stringResource(R.string.privacy_denied_note))
            use.denied.forEach { app ->
                val key = "${use.sensor.name}/denied/${app.uid}/${app.packageName}"
                AppUseRow(app, use.sensor, minuteKept, showPackage = app.label in sharedLabels, expanded = expandedKey == key, onToggle = { onToggle(key) })
            }
        }
    }
}

/**
 * One app under one sensor: its name, what it did in sum, and on a tap every moment
 * on record. A length is told for the camera and the microphone, where it is how
 * long the sensor was on; location "monitoring" runs side by side under several
 * tags and would add up to more hours than the record spans. A copy in a work
 * profile or another user says so under its name: it is the same package twice.
 */
@Composable
private fun AppUseRow(app: AppSensorUse, sensor: PrivacySensor, minuteKept: Boolean, showPackage: Boolean, expanded: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val showDuration = sensor.hasMeaningfulDuration
    val profile = PrivacyPresentation.profileOf(app.uid)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = withTapHaptic(onToggle))
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = app.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(R.string.privacy_toggle_events),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showPackage) KernelTechnical(app.packageName)
        if (profile != 0) KernelTechnical(stringResource(R.string.privacy_profile, profile))
        if (sensor == PrivacySensor.OTHER) {
            KernelTechnical(app.ops.sorted().joinToString(", ") { op -> PrivacyPresentation.opNameRes(op)?.let(context::getString) ?: op })
        }
        val summary = listOfNotNull(
            pluralStringResource(R.plurals.privacy_times, app.accesses, app.accesses).takeIf { app.accesses > 0 },
            privacyDurationText(app.durationMillis, minuteKept).takeIf { showDuration && app.durationMillis > 0 },
            app.lastMillis?.let { stringResource(R.string.privacy_last, privacyMomentText(it)) },
            // An app only ever refused has no use to date, so its latest refusal stands in.
            app.lastRejectedMillis?.takeIf { app.lastMillis == null }?.let { stringResource(R.string.privacy_last, privacyMomentText(it)) },
        )
        if (summary.isNotEmpty()) {
            Text(text = summary.joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val extras = listOfNotNull(
            pluralStringResource(R.plurals.privacy_background, app.backgroundAccesses, app.backgroundAccesses).takeIf { app.backgroundAccesses > 0 },
            pluralStringResource(R.plurals.privacy_refused, app.rejected, app.rejected).takeIf { app.rejected > 0 },
        )
        if (extras.isNotEmpty()) {
            Text(
                text = extras.joinToString(" · "),
                fontSize = 12.sp,
                color = if (app.rejected > 0) statusWarnColor() else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) EventList(app, sensor, minuteKept)
    }
}

@Composable
private fun EventList(app: AppSensorUse, sensor: PrivacySensor, minuteKept: Boolean) {
    val zone = remember { ZoneId.systemDefault() }
    val (shown, earlier) = PrivacyPresentation.shownEvents(app.events)
    Spacer(modifier = Modifier.height(4.dp))
    PrivacyPresentation.groupByDay(shown, zone).forEach { (day, events) ->
        Text(
            text = dayTitle(day, events.first().timeMillis),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        )
        events.forEach { event -> EventRow(event, sensor, minuteKept, showOp = sensor == PrivacySensor.OTHER || app.ops.size > 1) }
    }
    if (earlier > 0) KernelNote(pluralStringResource(R.plurals.privacy_earlier, earlier, earlier))
}

@Composable
private fun EventRow(event: AccessEvent, sensor: PrivacySensor, minuteKept: Boolean, showOp: Boolean) {
    val context = LocalContext.current
    val clock = remember(event.timeMillis) { android.text.format.DateFormat.getTimeFormat(context).format(Date(event.timeMillis)) }
    Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        MetricValue(clock, fontSize = 13.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            val parts = listOfNotNull(
                pluralStringResource(R.plurals.privacy_times, event.count, event.count).takeIf { event.count > 0 },
                privacyDurationText(event.durationMillis, minuteKept).takeIf { sensor.hasMeaningfulDuration && event.durationMillis > 0 },
                stringResource(R.string.privacy_event_background).takeIf { event.background },
                pluralStringResource(R.plurals.privacy_refused, event.rejected, event.rejected).takeIf { event.rejected > 0 },
            )
            Text(
                text = parts.joinToString(" · "),
                fontSize = 12.sp,
                color = if (event.rejected > 0 && event.count == 0) statusWarnColor() else MaterialTheme.colorScheme.onSurface,
            )
            val technical = listOfNotNull(
                event.op.takeIf { showOp }?.let { op -> if (sensor == PrivacySensor.OTHER) PrivacyPresentation.opNameRes(op)?.let(context::getString) ?: op else op },
                event.tag,
            )
            if (technical.isNotEmpty()) KernelTechnical(technical.joinToString(" · "))
        }
    }
}

@Composable
private fun dayTitle(day: LocalDate, sampleMillis: Long): String {
    val context = LocalContext.current
    return when (PrivacyPresentation.relativeDay(sampleMillis, System.currentTimeMillis(), ZoneId.systemDefault())) {
        RelativeDay.TODAY -> stringResource(R.string.privacy_day_today)
        RelativeDay.YESTERDAY -> stringResource(R.string.privacy_day_yesterday)
        RelativeDay.EARLIER -> remember(day) {
            android.text.format.DateFormat.getMediumDateFormat(context).format(Date(sampleMillis))
        }
    }
}

/** "today 09.36", "yesterday 21.12" or the date and the time, in the phone's own formats. */
@Composable
internal fun privacyMomentText(timeMillis: Long): String {
    val context = LocalContext.current
    val clock = remember(timeMillis) { android.text.format.DateFormat.getTimeFormat(context).format(Date(timeMillis)) }
    return when (PrivacyPresentation.relativeDay(timeMillis, System.currentTimeMillis(), ZoneId.systemDefault())) {
        RelativeDay.TODAY -> stringResource(R.string.privacy_today, clock)
        RelativeDay.YESTERDAY -> stringResource(R.string.privacy_yesterday, clock)
        RelativeDay.EARLIER -> remember(timeMillis) { android.text.format.DateFormat.getDateFormat(context).format(Date(timeMillis)) } + " " + clock
    }
}

/** A use's length as the record kept it: to the second, or "about 3 min" where Android kept only whole minutes. */
@Composable
private fun privacyDurationText(millis: Long, minuteKept: Boolean): String =
    if (minuteKept) {
        val minutes = PrivacyPresentation.aboutMinutes(millis)
        stringResource(R.string.privacy_duration_about_minutes, minutes)
    } else {
        shortDurationText(millis)
    }

/** "1 min 20 s", "2 s", "under a second": the lengths a camera or a recording has. */
@Composable
internal fun shortDurationText(millis: Long): String {
    val parts = PrivacyPresentation.durationParts(millis)
    return when {
        parts.underSecond -> stringResource(R.string.privacy_duration_under_second)
        parts.hours > 0 -> stringResource(R.string.privacy_duration_hours, parts.hours, parts.minutes)
        parts.minutes > 0 -> stringResource(R.string.privacy_duration_minutes, parts.minutes, parts.seconds)
        else -> stringResource(R.string.privacy_duration_seconds, parts.seconds)
    }
}
