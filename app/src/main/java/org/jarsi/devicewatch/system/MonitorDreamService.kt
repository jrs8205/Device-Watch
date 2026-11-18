package org.jarsi.devicewatch.system

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Resources
import android.service.dreams.DreamService
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
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
import org.jarsi.devicewatch.LatinDigitResources
import org.jarsi.devicewatch.system.dream.DreamHost
import org.jarsi.devicewatch.system.dream.DreamStyle
import org.jarsi.devicewatch.system.dream.LocalDreamWideGamut
import org.jarsi.devicewatch.system.dream.currentMinuteOfDay
import org.jarsi.devicewatch.system.dream.dreamWindowBrightness
import org.jarsi.devicewatch.system.dream.readDreamOptions
import org.jarsi.devicewatch.system.dream.setDreamBrightness
import org.jarsi.devicewatch.system.dream.shouldDimNow
import org.jarsi.devicewatch.system.dream.useWideColorGamut
import org.jarsi.devicewatch.ui.theme.ProvideBoundedDensity

/**
 * The screensaver. This class is only the service: its lifecycle and its window (orientation,
 * dimming and colour mode). What it draws lives in the dream package. Android's screensaver setting
 * stores this class's name, so the name and the package never change.
 */
class MonitorDreamService : DreamService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val lifecycleDriver = DreamLifecycleDriver(lifecycleRegistry)
    private val store = ViewModelStore()
    private val controller = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val viewModelStore: ViewModelStore = store
    override val savedStateRegistry: SavedStateRegistry = controller.savedStateRegistry

    private val latinDigitResources = LatinDigitResources { baseContext }

    /** The screensaver's view is made from this service, so its texts and its locale come from these. */
    override fun getResources(): Resources = latinDigitResources.get() ?: super.getResources()

    override fun onCreate() {
        super.onCreate()
        controller.performRestore(null)
        lifecycleDriver.onCreate()
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
        val dimNow = shouldDimNow(dreamPrefs, currentMinuteOfDay())
        setScreenBright(!dimNow)

        // The style and the other options are read once per dream, here; they are changed in
        // the app, never while the dream is up.
        val options = readDreamOptions(dreamPrefs)

        // The Edge style's line is as saturated as the screen allows: on a wide-gamut screen
        // the window is switched to wide colour and the style draws its Display P3 colours.
        val wideGamut = options.style == DreamStyle.EDGE && resources.configuration.isScreenWideColorGamut
        if (wideGamut) {
            window?.useWideColorGamut()
        }
        // The screensaver's own brightness (user setting), though not while it is dimmed.
        // The clock loop asks again once a minute, like the dimming above.
        window?.setDreamBrightness(dreamWindowBrightness(options, dimNow))

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@MonitorDreamService)
            setViewTreeViewModelStoreOwner(this@MonitorDreamService)
            setViewTreeSavedStateRegistryOwner(this@MonitorDreamService)

            setContent {
                ProvideBoundedDensity {
                    CompositionLocalProvider(LocalDreamWideGamut provides wideGamut) {
                        DreamHost(service = this@MonitorDreamService, prefs = dreamPrefs, options = options)
                    }
                }
            }
        }

        setContentView(composeView)
        lifecycleDriver.onAttachedToWindow()
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        isDreaming = true
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        isDreaming = false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // GitHub #4: may arrive after onDestroy() on Android 14+; the driver skips it then.
        lifecycleDriver.onDetachedFromWindow()
    }

    override fun onDestroy() {
        super.onDestroy()
        isDreaming = false
        lifecycleDriver.onDestroy()
        store.clear()
    }

    companion object {
        /** Whether this screensaver is up, for [ChargeStartScreensaver]: same process. */
        @Volatile
        var isDreaming = false
            private set
    }
}
