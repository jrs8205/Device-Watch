package com.example.modernwidget.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedPreferences-backed [ChargeAnchorStore] ("charge_anchor"). Synchronous
 * for the same reason as the other tiny stores: it is written from the monitor
 * service's battery receiver on the main thread and the dataset is four values.
 */
@Singleton
class ChargeAnchorStoreImpl @Inject constructor(
    @ApplicationContext context: Context,
) : ChargeAnchorStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): ChargeAnchorLogic.State {
        val millis = prefs.getLong(KEY_ANCHOR_MILLIS, -1L)
        val anchor = if (millis <= 0L) {
            null
        } else {
            ChargeAnchor(
                timeMillis = millis,
                batteryLevel = prefs.getInt(KEY_ANCHOR_LEVEL, 0),
                type = try {
                    ChargeAnchorType.valueOf(prefs.getString(KEY_ANCHOR_TYPE, null).orEmpty())
                } catch (_: IllegalArgumentException) {
                    ChargeAnchorType.UNPLUGGED
                },
            )
        }
        return ChargeAnchorLogic.State(
            anchor = anchor,
            fullReachedThisPlug = prefs.getBoolean(KEY_FULL_THIS_PLUG, false),
        )
    }

    override fun save(state: ChargeAnchorLogic.State) {
        prefs.edit().apply {
            val anchor = state.anchor
            if (anchor == null) {
                remove(KEY_ANCHOR_MILLIS)
                remove(KEY_ANCHOR_LEVEL)
                remove(KEY_ANCHOR_TYPE)
            } else {
                putLong(KEY_ANCHOR_MILLIS, anchor.timeMillis)
                putInt(KEY_ANCHOR_LEVEL, anchor.batteryLevel)
                putString(KEY_ANCHOR_TYPE, anchor.type.name)
            }
            putBoolean(KEY_FULL_THIS_PLUG, state.fullReachedThisPlug)
        }.apply()
    }

    companion object {
        const val PREFS_NAME = "charge_anchor"
        private const val KEY_ANCHOR_MILLIS = "anchor_millis"
        private const val KEY_ANCHOR_LEVEL = "anchor_level"
        private const val KEY_ANCHOR_TYPE = "anchor_type"
        private const val KEY_FULL_THIS_PLUG = "full_this_plug"
    }
}
