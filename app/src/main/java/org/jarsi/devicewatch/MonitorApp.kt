package org.jarsi.devicewatch

import android.app.Application
import android.content.res.Configuration
import android.content.res.Resources
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MonitorApp : Application() {

    // The context Android attached stays as it is: the framework expects its own class there.
    private val latinDigitResources = LatinDigitResources { baseContext }

    /** The widgets, the alerts and the repositories format their texts with these. */
    override fun getResources(): Resources = latinDigitResources.get() ?: super.getResources()

    override fun onCreate() {
        LatinDigits.applyToDefault()
        super.onCreate()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Android has just set the process default from the new languages.
        LatinDigits.applyToDefault()
    }
}
