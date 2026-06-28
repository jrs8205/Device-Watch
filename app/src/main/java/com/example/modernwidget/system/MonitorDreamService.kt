package com.example.modernwidget.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.service.dreams.DreamService
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.util.Date
import java.util.Locale

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
    var chargeTimeRemainingMs by remember { mutableLongStateOf(-1L) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) {
                    batteryLevel = (level * 100) / scale
                }
                
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                             status == BatteryManager.BATTERY_STATUS_FULL
                
                val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                batteryTemp = temp / 10.0
                
                val volt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                batteryVoltage = volt / 1000.0
                
                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    chargeTimeRemainingMs = batteryManager.computeChargeTimeRemaining()
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    
    var timeText by remember { mutableStateOf("18:49") }
    var secondsText by remember { mutableStateOf("27") }
    var dateText by remember { mutableStateOf("Sunnuntai 28. kesäkuuta") }
    var isLayoutSwapped by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val secondsFormat = SimpleDateFormat("ss", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE d. MMMM", Locale("fi"))
        
        while (true) {
            val now = Date()
            timeText = timeFormat.format(now)
            secondsText = secondsFormat.format(now)
            dateText = dateFormat.format(now).replaceFirstChar { it.uppercase() }
            kotlinx.coroutines.delay(1000)
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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val fullTimeStrLandscape = if (isCharging) {
        if (chargeTimeRemainingMs > 0) {
            val fullTimeMs = System.currentTimeMillis() + chargeTimeRemainingMs
            val format = SimpleDateFormat("HH:mm", Locale.getDefault())
            "Täynnä, arviolta noin klo ${format.format(Date(fullTimeMs))}"
        } else {
            "Täynnä, arviolta noin --:--"
        }
    } else {
        ""
    }

    val voltStr = "%.2f".format(batteryVoltage).replace(".", ",")
    val tempStr = "%.1f".format(batteryTemp).replace(".", ",")
    val batteryGradientColors = getBatteryGradientColors(batteryLevel)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .clickable {
                (context as? DreamService)?.finish()
            },
        contentAlignment = Alignment.Center
    ) {
        // Pääsäiliö (kierto hoitaa 180 asteen peilauksen/käännön dynaamisesti)
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
                        .padding(horizontal = 40.dp, vertical = 40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClockBlock(
                        modifier = Modifier.weight(0.58f),
                        timeText = timeText,
                        secondsText = secondsText,
                        dateText = dateText,
                        dateSize = 30
                    )

                    Box(
                        modifier = Modifier
                            .width(1.5.dp)
                            .fillMaxHeight(0.6f)
                            .background(Color(0x59FFFFFF))
                    )

                    Spacer(modifier = Modifier.width(32.dp))

                    BatteryBlockLandscape(
                        modifier = Modifier.weight(0.42f),
                        isCharging = isCharging,
                        batteryLevel = batteryLevel,
                        batteryGradientColors = batteryGradientColors,
                        voltStr = voltStr,
                        tempStr = tempStr,
                        fullTimeStrLandscape = fullTimeStrLandscape
                    )
                }
            } else {
                // PYSTY-ASETTELU
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 40.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    ClockBlock(
                        timeText = timeText,
                        secondsText = secondsText,
                        dateText = dateText,
                        dateSize = 28
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    BatteryBlockPortrait(
                        isCharging = isCharging,
                        batteryLevel = batteryLevel,
                        batteryGradientColors = batteryGradientColors,
                        voltStr = voltStr,
                        tempStr = tempStr,
                        fullTimeStrLandscape = fullTimeStrLandscape
                    )

                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Suunnanvaihto-nappula (Oikeassa alakulmassa, ei pyöri mukana)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = offsetX.dp, y = offsetY.dp)
                .padding(24.dp)
                .size(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0x33FFFFFF))
                .border(1.5.dp, Color(0x73FFFFFF), RoundedCornerShape(32.dp))
                .clickable {
                    isLayoutSwapped = !isLayoutSwapped
                },
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

@Composable
fun ClockBlock(
    modifier: Modifier = Modifier,
    timeText: String,
    secondsText: String,
    dateText: String,
    dateSize: Int
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = timeText,
                fontSize = 120.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFEDEDED),
                style = TextStyle(fontFeatureSettings = "tnum"),
                letterSpacing = (-3).sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = ":$secondsText",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8),
                style = TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = dateText,
            fontSize = dateSize.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF8B929C)
        )
    }
}

@Composable
fun BatteryBlockLandscape(
    modifier: Modifier = Modifier,
    isCharging: Boolean,
    batteryLevel: Int,
    batteryGradientColors: List<Color>,
    voltStr: String,
    tempStr: String,
    fullTimeStrLandscape: String
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isCharging) "⚡" else "🔋",
                fontSize = 30.sp,
                color = if (isCharging) Color(0xFFFBBF24) else Color(0xFF34D399)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isCharging) {
                    if (batteryLevel == 100) "Täynnä" else "Latautuu"
                } else {
                    "Akkuvirralla"
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
            text = "$voltStr V  ·  $tempStr °C",
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF9AA0A8),
            style = TextStyle(fontFeatureSettings = "tnum")
        )
        
        if (fullTimeStrLandscape.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = fullTimeStrLandscape,
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
    voltStr: String,
    tempStr: String,
    fullTimeStrLandscape: String
) {
    Column(
        modifier = modifier.width(360.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isCharging) "⚡" else "🔋",
                fontSize = 32.sp,
                color = if (isCharging) Color(0xFFFBBF24) else Color(0xFF34D399)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isCharging) {
                    if (batteryLevel == 100) "Täynnä" else "Latautuu"
                } else {
                    "Akkuvirralla"
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
            text = "$voltStr V  ·  $tempStr °C",
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF9AA0A8),
            style = TextStyle(fontFeatureSettings = "tnum")
        )
        
        if (fullTimeStrLandscape.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = fullTimeStrLandscape,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF34D399),
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
    }
}

fun getBatteryGradientColors(level: Int): List<Color> {
    return when {
        level <= 20 -> listOf(Color(0xFFEF4444), Color(0xFFF87171))
        level <= 60 -> listOf(Color(0xFFF59E0B), Color(0xFFFBBF24))
        else -> listOf(Color(0xFF34D399), Color(0xFF6EE7A8))
    }
}
