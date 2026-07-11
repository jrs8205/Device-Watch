package org.jarsi.devicewatch.system

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.service.dreams.DreamService
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jarsi.devicewatch.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.abs

class MonitorDreamService : DreamService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val controller = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val viewModelStore: ViewModelStore = store
    override val savedStateRegistry: SavedStateRegistry = controller.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        controller.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        isInteractive = true
        isFullscreen = true

        // Optionally lock the screensaver to portrait (user setting). On Android 14+ the dream
        // is hosted by a framework DreamActivity, and DreamService exposes no orientation API —
        // but the dream window's context IS that host activity, so we set the orientation through
        // the real Activity.setRequestedOrientation API. The Compose content then picks the
        // portrait layout from the reported configuration.
        val dreamPrefs = getSharedPreferences(DreamPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val forcePortrait = dreamPrefs.getBoolean(DreamPreferences.KEY_FORCE_PORTRAIT, false)
        (window?.context as? android.app.Activity)?.requestedOrientation = if (forcePortrait) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        // Optional dim mode (user setting): manual dim, or an automatic night window. Must be set
        // no later than onDreamingStarted(), so onAttachedToWindow() is a valid place. The clock
        // loop re-evaluates this once a minute so a running screensaver dims/brightens live when
        // the night window starts or ends.
        setScreenBright(!shouldDimNow(dreamPrefs, currentMinuteOfDay()))

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@MonitorDreamService)
            setViewTreeViewModelStoreOwner(this@MonitorDreamService)
            setViewTreeSavedStateRegistryOwner(this@MonitorDreamService)
            
            setContent {
                ScreensaverContent(this@MonitorDreamService)
            }
        }
        
        setContentView(composeView)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

@Composable
fun ScreensaverContent(context: Context) {
    var batteryLevel by remember { mutableIntStateOf(50) }
    var isCharging by remember { mutableStateOf(false) }
    var batteryTemp by remember { mutableDoubleStateOf(25.6) }
    var batteryVoltage by remember { mutableDoubleStateOf(3.88) }
    var chargeFullAtMs by remember { mutableLongStateOf(-1L) }
    var chargeWatts by remember { mutableStateOf<Double?>(null) }
    var hasSentFullBatteryNotification by remember { mutableStateOf(false) }
    val preferences = remember(context) {
        context.getSharedPreferences(DreamPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) {
                    batteryLevel = (level * 100) / scale
                }
                
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                               status == BatteryManager.BATTERY_STATUS_FULL
                isCharging = charging

                if (batteryLevel >= 100 && charging) {
                    if (!hasSentFullBatteryNotification) {
                        BatteryFullNotifier.show(context.applicationContext)
                        hasSentFullBatteryNotification = true
                    }
                } else {
                    hasSentFullBatteryNotification = false
                }

                val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                batteryTemp = temp / 10.0

                val volt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                batteryVoltage = volt / 1000.0

                val batteryManager =
                    context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

                // Live charging power. CURRENT_NOW is documented as µA but some OEMs report mA
                // and the sign convention varies; chargingWatts() normalizes both and returns
                // null for missing/implausible readings (then no wattage is shown).
                chargeWatts = if (charging && batteryManager != null) {
                    chargingWatts(
                        batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
                        volt / 1000.0
                    )
                } else {
                    null
                }

                // Capture the absolute "battery full" wall-clock time ONCE, at the moment we
                // read the remaining duration. Previously the raw remaining-duration was stored
                // and added to System.currentTimeMillis() on every recomposition (once a second),
                // which made the estimate drift ~1 s later per second between battery broadcasts.
                chargeFullAtMs = if (charging && batteryManager != null &&
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val remaining = batteryManager.computeChargeTimeRemaining()
                    if (remaining > 0L) System.currentTimeMillis() + remaining else -1L
                } else {
                    -1L
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    
    var timeText by remember { mutableStateOf("") }
    var secondsText by remember { mutableStateOf("") }
    var amPmText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var alarmText by remember { mutableStateOf("") }
    var isLayoutSwapped by remember {
        mutableStateOf(preferences.getBoolean(DreamPreferences.KEY_LAYOUT_SWAPPED, false))
    }
    val configuration = LocalConfiguration.current
    val currentLocale = configuration.locales[0]
    // Honor the device's 12/24-hour clock preference instead of hardcoding 24-hour time,
    // so the screensaver clock reads naturally on every locale/brand.
    val is24Hour = remember(configuration) {
        android.text.format.DateFormat.is24HourFormat(context)
    }

    LaunchedEffect(currentLocale, is24Hour) {
        val timeFormat = SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm", currentLocale)
        val secondsFormat = SimpleDateFormat("ss", currentLocale)
        val amPmFormat = SimpleDateFormat("a", currentLocale)
        val dateFormat = SimpleDateFormat(context.getString(R.string.dream_date_pattern), currentLocale)
        val alarmFormat = SimpleDateFormat(if (is24Hour) "EEE HH:mm" else "EEE h:mm a", currentLocale)
        var lastMinuteOfDay = -1

        while (true) {
            val now = Date()
            timeText = timeFormat.format(now)
            secondsText = secondsFormat.format(now)
            amPmText = if (is24Hour) "" else amPmFormat.format(now)
            dateText = dateFormat.format(now).replaceFirstChar { it.uppercase(currentLocale) }

            // Once-a-minute work: the next-alarm line and the live dim state (so a screensaver
            // that runs across the night-dim window edges dims/brightens without a restart).
            val minuteOfDay = currentMinuteOfDay()
            if (minuteOfDay != lastMinuteOfDay) {
                lastMinuteOfDay = minuteOfDay
                alarmText = (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
                    ?.nextAlarmClock?.triggerTime
                    ?.let { "⏰ " + alarmFormat.format(Date(it)) }
                    ?: ""
                (context as? DreamService)?.setScreenBright(!shouldDimNow(preferences, minuteOfDay))
            }

            // Sleep exactly to the next second boundary so the seconds never visibly skip.
            kotlinx.coroutines.delay(1000L - (System.currentTimeMillis() % 1000L))
        }
    }
    
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60000)
            offsetX = (Math.random() * 30 - 15).toFloat()
            offsetY = (Math.random() * 30 - 15).toFloat()
        }
    }

    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val fullTimeStr = if (isCharging && batteryLevel < 100) {
        if (chargeFullAtMs > 0L) {
            val format = SimpleDateFormat("HH:mm", currentLocale)
            context.getString(R.string.dream_full_time_estimate, format.format(Date(chargeFullAtMs)))
        } else {
            context.getString(R.string.dream_full_time_unknown)
        }
    } else {
        ""
    }

    // Battery details line: volts and temperature, plus live charging power when available.
    val detailsStr = buildString {
        append(String.format(currentLocale, "%.2f", batteryVoltage))
        append(" V  ·  ")
        append(String.format(currentLocale, "%.1f", batteryTemp))
        append(" °C")
        if (isCharging) {
            chargeWatts?.let {
                append("  ·  ")
                append(String.format(currentLocale, "%.1f", it))
                append(" W")
            }
        }
    }
    val batteryGradientColors = getBatteryGradientColors(batteryLevel)

    // Soft pulse on the charging bolt; composed only while charging so no animation
    // frames are produced when the device is on battery.
    val boltAlpha = if (isCharging) {
        val boltTransition = rememberInfiniteTransition(label = "boltPulse")
        val pulsedAlpha by boltTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "boltAlpha"
        )
        pulsedAlpha
    } else {
        1f
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Dark vertical gradient tinted by the battery level: the viewer's top stays pure
            // black (clock legibility, burn-in) and the battery-colored tone builds up toward
            // the viewer's bottom. The background does not rotate with the 180° layout flip,
            // so when the layout is swapped (phone docked upside down) the gradient is
            // mirrored to keep the glow at the bottom of what the user actually sees.
            .background(
                Brush.verticalGradient(
                    colorStops = if (isLayoutSwapped) {
                        arrayOf(
                            0.0f to batteryTintDeep(batteryLevel),
                            0.6f to Color(0xFF000000),
                            1.0f to Color(0xFF000000)
                        )
                    } else {
                        arrayOf(
                            0.0f to Color(0xFF000000),
                            0.4f to Color(0xFF000000),
                            1.0f to batteryTintDeep(batteryLevel)
                        )
                    }
                )
            )
            .clickable {
                (context as? DreamService)?.finish()
            },
        contentAlignment = Alignment.Center
    ) {
        val horizontalPadding = when {
            maxWidth < 380.dp -> 20.dp
            maxWidth < 520.dp -> 28.dp
            else -> 40.dp
        }
        val verticalPadding = if (maxHeight < 560.dp) 24.dp else 40.dp
        val centerGap = if (maxWidth < 700.dp) 18.dp else 32.dp

        // The clock auto-sizes to the largest font that still fits the available width, so the
        // seconds never wrap or overlap on narrow phones (e.g. Pixel 9a portrait). The width
        // budget differs per orientation: portrait gets the full inner width; landscape gets
        // the clock column's weighted (0.58) share of the row after padding/divider/gap.
        val clockAvailableWidth = if (isLandscape) {
            (maxWidth - horizontalPadding * 2 - 1.5.dp - centerGap) * 0.58f
        } else {
            maxWidth - horizontalPadding * 2
        }
        val clockSize = rememberFittedClockSize(
            availableWidth = clockAvailableWidth,
            maxClockSp = if (isLandscape) 82 else 120,
            minClockSp = if (isLandscape) 48 else 56,
        )
        val secondsSize = (clockSize * 0.25f).toInt().coerceAtLeast(22)

        // Main container; rotation handles the 180-degree layout flip dynamically.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = offsetX.dp, y = offsetY.dp)
                .graphicsLayer {
                    rotationZ = if (isLayoutSwapped) 180f else 0f
                },
            contentAlignment = Alignment.Center
        ) {
            if (isLandscape) {
                // VAAKA-ASETTELU
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClockBlock(
                        modifier = Modifier.weight(0.58f),
                        timeText = timeText,
                        secondsText = secondsText,
                        dateText = dateText,
                        clockSize = clockSize,
                        secondsSize = secondsSize,
                        dateSize = if (clockSize < 100) 22 else 30,
                        amPmText = amPmText,
                        alarmText = alarmText
                    )

                    Box(
                        modifier = Modifier
                            .width(1.5.dp)
                            .fillMaxHeight(0.6f)
                            .background(Color(0x59FFFFFF))
                    )

                    Spacer(modifier = Modifier.width(centerGap))

                    BatteryBlockLandscape(
                        modifier = Modifier.weight(0.42f),
                        isCharging = isCharging,
                        batteryLevel = batteryLevel,
                        batteryGradientColors = batteryGradientColors,
                        detailsStr = detailsStr,
                        fullTimeStr = fullTimeStr,
                        boltAlpha = boltAlpha
                    )
                }
            } else {
                // PYSTY-ASETTELU
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    ClockBlock(
                        timeText = timeText,
                        secondsText = secondsText,
                        dateText = dateText,
                        clockSize = clockSize,
                        secondsSize = secondsSize,
                        dateSize = if (clockSize < 100) 22 else 28,
                        amPmText = amPmText,
                        alarmText = alarmText
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    BatteryBlockPortrait(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
                        isCharging = isCharging,
                        batteryLevel = batteryLevel,
                        batteryGradientColors = batteryGradientColors,
                        detailsStr = detailsStr,
                        fullTimeStr = fullTimeStr,
                        boltAlpha = boltAlpha
                    )

                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Rotation button in the bottom-right corner; it does not rotate with the content.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = offsetX.dp, y = offsetY.dp)
                .padding(12.dp)
                .size(112.dp)
                .clickable {
                    val newValue = !isLayoutSwapped
                    isLayoutSwapped = newValue
                    preferences.edit().putBoolean(DreamPreferences.KEY_LAYOUT_SWAPPED, newValue).apply()
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0x33FFFFFF))
                    .border(1.5.dp, Color(0x73FFFFFF), RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isLandscape) "⇄" else "⇅",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun ClockBlock(
    modifier: Modifier = Modifier,
    timeText: String,
    secondsText: String,
    dateText: String,
    clockSize: Int,
    secondsSize: Int,
    dateSize: Int,
    amPmText: String = "",
    alarmText: String = ""
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = timeText,
                fontSize = clockSize.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFEDEDED),
                style = TextStyle(fontFeatureSettings = "tnum"),
                letterSpacing = 0.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Text(
                    text = ":$secondsText",
                    fontSize = secondsSize.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8),
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
                if (amPmText.isNotEmpty()) {
                    Text(
                        text = amPmText,
                        fontSize = (secondsSize * 0.6f).sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF8B929C)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = dateText,
            fontSize = dateSize.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF8B929C)
        )
        if (alarmText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = alarmText,
                fontSize = (dateSize * 0.72f).sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF6E7681),
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
    }
}

@Composable
fun BatteryBlockLandscape(
    modifier: Modifier = Modifier,
    isCharging: Boolean,
    batteryLevel: Int,
    batteryGradientColors: List<Color>,
    detailsStr: String,
    fullTimeStr: String,
    boltAlpha: Float = 1f
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isCharging) "⚡" else "🔋",
                fontSize = 30.sp,
                color = if (isCharging) Color(0xFFFBBF24) else Color(0xFF34D399),
                modifier = Modifier.graphicsLayer { alpha = boltAlpha }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isCharging) {
                    if (batteryLevel == 100) {
                        stringResource(R.string.dream_battery_full)
                    } else {
                        stringResource(R.string.dream_charging)
                    }
                } else {
                    stringResource(R.string.dream_on_battery)
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF34D399)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "$batteryLevel%",
            fontSize = 58.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFEDEDED),
            style = TextStyle(fontFeatureSettings = "tnum")
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(11.dp)
                .clip(RoundedCornerShape(5.5.dp))
                .background(Color(0x33FFFFFF))
                .border(
                    1.dp,
                    Color(0x59FFFFFF),
                    RoundedCornerShape(5.5.dp)
                )
        ) {
            val activeFraction = batteryLevel / 100f
            Box(
                modifier = Modifier
                    .fillMaxWidth(activeFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.5.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = batteryGradientColors
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = detailsStr,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF9AA0A8),
            style = TextStyle(fontFeatureSettings = "tnum")
        )

        if (fullTimeStr.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = fullTimeStr,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF34D399),
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
    }
}

@Composable
fun BatteryBlockPortrait(
    modifier: Modifier = Modifier,
    isCharging: Boolean,
    batteryLevel: Int,
    batteryGradientColors: List<Color>,
    detailsStr: String,
    fullTimeStr: String,
    boltAlpha: Float = 1f
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isCharging) "⚡" else "🔋",
                fontSize = 32.sp,
                color = if (isCharging) Color(0xFFFBBF24) else Color(0xFF34D399),
                modifier = Modifier.graphicsLayer { alpha = boltAlpha }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isCharging) {
                    if (batteryLevel == 100) {
                        stringResource(R.string.dream_battery_full)
                    } else {
                        stringResource(R.string.dream_charging)
                    }
                } else {
                    stringResource(R.string.dream_on_battery)
                },
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF34D399)
            )
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Text(
            text = "$batteryLevel%",
            fontSize = 64.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFEDEDED),
            style = TextStyle(fontFeatureSettings = "tnum")
        )

        Spacer(modifier = Modifier.height(14.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0x33FFFFFF))
                .border(
                    1.dp,
                    Color(0x59FFFFFF),
                    RoundedCornerShape(5.dp)
                )
        ) {
            val activeFraction = batteryLevel / 100f
            Box(
                modifier = Modifier
                    .fillMaxWidth(activeFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = batteryGradientColors
                        )
                    )
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = detailsStr,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF9AA0A8),
            style = TextStyle(fontFeatureSettings = "tnum")
        )
        
        if (fullTimeStr.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = fullTimeStr,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF34D399),
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
    }
}

/**
 * Largest clock font size (in sp) whose "HH:mm" + gap + ":ss" line fits [availableWidth].
 * Text width scales linearly with font size, so we measure both parts once at a reference size
 * and let [fittedClockSp] solve for the biggest size that fits, clamped to
 * [minClockSp]..[maxClockSp]. This keeps the seconds from wrapping/overlapping on narrow
 * screens regardless of device width. The measurement is cached and only re-runs when the
 * available width, density, or font resolver changes (not on every clock tick).
 */
@Composable
private fun rememberFittedClockSize(
    availableWidth: Dp,
    maxClockSp: Int,
    minClockSp: Int,
    secondsRatio: Float = 0.25f,
    secondsMinSp: Int = 22,
    gap: Dp = 8.dp,
): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    return remember(availableWidth, maxClockSp, minClockSp, density, resolver) {
        if (availableWidth <= 0.dp) return@remember minClockSp
        val referenceSp = 100f
        val timeWidthPx = measurer.measure(
            text = "00:00",
            style = TextStyle(
                fontSize = referenceSp.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFeatureSettings = "tnum",
                letterSpacing = 0.sp,
            ),
            softWrap = false,
            maxLines = 1,
            density = density,
            fontFamilyResolver = resolver,
        ).size.width.toFloat()
        val secWidthPx = measurer.measure(
            text = ":00",
            style = TextStyle(
                fontSize = referenceSp.sp,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum",
            ),
            softWrap = false,
            maxLines = 1,
            density = density,
            fontFamilyResolver = resolver,
        ).size.width.toFloat()
        fittedClockSp(
            availablePx = with(density) { availableWidth.toPx() },
            gapPx = with(density) { gap.toPx() },
            timePerSp = timeWidthPx / referenceSp,
            secPerSp = secWidthPx / referenceSp,
            secondsRatio = secondsRatio,
            secondsMinSp = secondsMinSp,
            minClockSp = minClockSp,
            maxClockSp = maxClockSp,
        )
    }
}

/**
 * Pure width-fit math, extracted for unit testing. Given the time/seconds widths-per-sp,
 * returns the largest integer sp size in [minClockSp]..[maxClockSp] whose
 * time + [gapPx] + seconds line fits [availablePx]. The seconds normally scale as
 * [secondsRatio] of the clock size, but are pinned at [secondsMinSp] once that would drop
 * below the floor — so the floor case is re-solved with a fixed seconds width.
 */
internal fun fittedClockSp(
    availablePx: Float,
    gapPx: Float,
    timePerSp: Float,
    secPerSp: Float,
    secondsRatio: Float,
    secondsMinSp: Int,
    minClockSp: Int,
    maxClockSp: Int,
): Int {
    var fitted = (availablePx - gapPx) / (timePerSp + secPerSp * secondsRatio)
    if (secondsRatio * fitted < secondsMinSp) {
        val secFlooredPx = secPerSp * secondsMinSp
        fitted = (availablePx - gapPx - secFlooredPx) / timePerSp
    }
    return fitted.toInt().coerceIn(minClockSp, maxClockSp)
}

fun getBatteryGradientColors(level: Int): List<Color> {
    return when {
        level <= 20 -> listOf(Color(0xFFEF4444), Color(0xFFF87171))
        level <= 60 -> listOf(Color(0xFFF59E0B), Color(0xFFFBBF24))
        else -> listOf(Color(0xFF34D399), Color(0xFF6EE7A8))
    }
}

/**
 * Deep background tint matching the battery-bar color thresholds. Used as the bottom stop
 * of the screensaver's background gradient (black at the top). Values are bright enough to
 * read as a clear color glow on an OLED panel while staying comfortably dark.
 */
fun batteryTintDeep(level: Int): Color {
    return when {
        level <= 20 -> Color(0xFF4A1210)
        level <= 60 -> Color(0xFF46360A)
        else -> Color(0xFF0E4A36)
    }
}

/** Minutes since local midnight, for the night-dim window check. */
internal fun currentMinuteOfDay(): Int {
    val calendar = Calendar.getInstance()
    return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}

/**
 * Effective dim state: the manual dim switch, or the automatic night window when enabled.
 * Pure apart from the SharedPreferences reads so the window logic itself is unit-testable
 * via [isInNightWindow].
 */
internal fun shouldDimNow(prefs: SharedPreferences, nowMinutes: Int): Boolean {
    if (prefs.getBoolean(DreamPreferences.KEY_DIM_SCREENSAVER, false)) return true
    if (!prefs.getBoolean(DreamPreferences.KEY_NIGHT_DIM, false)) return false
    return isInNightWindow(
        nowMinutes,
        prefs.getInt(
            DreamPreferences.KEY_NIGHT_DIM_START_MINUTES,
            DreamPreferences.DEFAULT_NIGHT_DIM_START_MINUTES
        ),
        prefs.getInt(
            DreamPreferences.KEY_NIGHT_DIM_END_MINUTES,
            DreamPreferences.DEFAULT_NIGHT_DIM_END_MINUTES
        )
    )
}

/**
 * Whether [nowMinutes] (minutes since midnight) falls inside the window from [startMinutes]
 * (inclusive) to [endMinutes] (exclusive). A window that crosses midnight (e.g. 22:00–07:00)
 * is handled; an empty window (start == end) never matches.
 */
internal fun isInNightWindow(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean {
    if (startMinutes == endMinutes) return false
    return if (startMinutes < endMinutes) {
        nowMinutes >= startMinutes && nowMinutes < endMinutes
    } else {
        nowMinutes >= startMinutes || nowMinutes < endMinutes
    }
}

/**
 * Charging power in watts from BATTERY_PROPERTY_CURRENT_NOW and the battery voltage.
 * CURRENT_NOW is documented as µA, but some OEMs report mA and the sign convention for
 * charge/discharge also varies — so the magnitude is used, and a reading that is only
 * plausible as mA is rescaled. Returns null when the reading is missing or implausible
 * (never show a fabricated value).
 */
internal fun chargingWatts(currentRaw: Int, voltageVolts: Double): Double? {
    if (currentRaw == 0 || currentRaw == Int.MIN_VALUE || voltageVolts <= 0.0) return null
    val plausibleWatts = 0.5..150.0
    val wattsAsMicroAmps = abs(currentRaw.toDouble()) / 1_000_000.0 * voltageVolts
    if (wattsAsMicroAmps in plausibleWatts) return wattsAsMicroAmps
    val wattsAsMilliAmps = wattsAsMicroAmps * 1000.0
    return wattsAsMilliAmps.takeIf { it in plausibleWatts }
}
