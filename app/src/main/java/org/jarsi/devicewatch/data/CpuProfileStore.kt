package org.jarsi.devicewatch.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Where the processor profile's state lives between passes and process starts. */
interface CpuProfileStore {
    fun load(): CpuProfileState
    fun save(state: CpuProfileState)
}

/**
 * In `device_local.xml`, which Android's backup never carries: limits read and
 * written on this phone mean nothing on another, and a restored profile there
 * would start writing to a kernel nobody chose it for.
 */
@Singleton
class DeviceLocalCpuProfileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : CpuProfileStore {

    private val prefs: SharedPreferences get() = DeviceLocalFlags.prefs(context)

    override fun load(): CpuProfileState {
        val p = prefs
        val pendingProfile = p.getString(KEY_PENDING_PROFILE, null)?.let(::profileOf)
        return CpuProfileState(
            active = p.getString(KEY_ACTIVE, null)?.let(::profileOf) ?: CpuProfile.STOCK,
            held = CpuCapsCodec.decode(p.getString(KEY_HELD, null)),
            custom = CpuCapsCodec.decode(p.getString(KEY_CUSTOM, null)),
            originals = CpuCapsCodec.decode(p.getString(KEY_ORIGINALS, null)),
            originalsAssumed = CpuCapsCodec.decodeIds(p.getString(KEY_ORIGINALS_ASSUMED, null)),
            pending = pendingProfile?.let {
                PendingApply(
                    profile = it,
                    writes = CpuCapsCodec.decode(p.getString(KEY_PENDING_WRITES, null)),
                    previousHeld = CpuCapsCodec.decode(p.getString(KEY_PENDING_PREVIOUS, null)),
                    held = CpuCapsCodec.decode(p.getString(KEY_PENDING_HELD, null)),
                    custom = CpuCapsCodec.decode(p.getString(KEY_PENDING_CUSTOM, null)),
                )
            },
            applyAfterRestart = p.getBoolean(KEY_APPLY_AFTER_RESTART, false),
            bootProfile = p.getString(KEY_BOOT_PROFILE, null)?.let(::profileOf),
            overrideCount = p.getInt(KEY_OVERRIDE_COUNT, 0),
            reapplyFailures = p.getInt(KEY_REAPPLY_FAILURES, 0),
            bootId = p.getString(KEY_BOOT_ID, null),
            notice = p.getString(KEY_NOTICE, null)?.let { name -> CpuProfileNotice.entries.firstOrNull { it.name == name } },
        )
    }

    /** Committed, not applied: a write-ahead that is not on disk before the shell runs protects nothing. */
    @SuppressLint("ApplySharedPref")
    override fun save(state: CpuProfileState) {
        prefs.edit().apply {
            putString(KEY_ACTIVE, state.active.name)
            putString(KEY_HELD, CpuCapsCodec.encode(state.held))
            putString(KEY_CUSTOM, CpuCapsCodec.encode(state.custom))
            putString(KEY_ORIGINALS, CpuCapsCodec.encode(state.originals))
            putString(KEY_ORIGINALS_ASSUMED, CpuCapsCodec.encodeIds(state.originalsAssumed))
            val pending = state.pending
            if (pending == null) {
                remove(KEY_PENDING_PROFILE)
                remove(KEY_PENDING_WRITES)
                remove(KEY_PENDING_PREVIOUS)
                remove(KEY_PENDING_HELD)
                remove(KEY_PENDING_CUSTOM)
            } else {
                putString(KEY_PENDING_PROFILE, pending.profile.name)
                putString(KEY_PENDING_WRITES, CpuCapsCodec.encode(pending.writes))
                putString(KEY_PENDING_PREVIOUS, CpuCapsCodec.encode(pending.previousHeld))
                putString(KEY_PENDING_HELD, CpuCapsCodec.encode(pending.held))
                putString(KEY_PENDING_CUSTOM, CpuCapsCodec.encode(pending.custom))
            }
            putBoolean(KEY_APPLY_AFTER_RESTART, state.applyAfterRestart)
            if (state.bootProfile == null) remove(KEY_BOOT_PROFILE) else putString(KEY_BOOT_PROFILE, state.bootProfile.name)
            putInt(KEY_OVERRIDE_COUNT, state.overrideCount)
            putInt(KEY_REAPPLY_FAILURES, state.reapplyFailures)
            if (state.bootId == null) remove(KEY_BOOT_ID) else putString(KEY_BOOT_ID, state.bootId)
            if (state.notice == null) remove(KEY_NOTICE) else putString(KEY_NOTICE, state.notice.name)
        }.commit()
    }

    private fun profileOf(name: String): CpuProfile? = CpuProfile.entries.firstOrNull { it.name == name }

    private companion object {
        const val KEY_ACTIVE = "cpu_active"
        const val KEY_HELD = "cpu_held"
        const val KEY_CUSTOM = "cpu_custom"
        const val KEY_ORIGINALS = "cpu_originals"
        const val KEY_ORIGINALS_ASSUMED = "cpu_originals_assumed"
        const val KEY_PENDING_PROFILE = "cpu_pending_profile"
        const val KEY_PENDING_WRITES = "cpu_pending_writes"
        const val KEY_PENDING_PREVIOUS = "cpu_pending_previous"
        const val KEY_PENDING_HELD = "cpu_pending_held"
        const val KEY_PENDING_CUSTOM = "cpu_pending_custom"
        const val KEY_APPLY_AFTER_RESTART = "cpu_apply_after_restart"
        const val KEY_BOOT_PROFILE = "cpu_boot_profile"
        const val KEY_OVERRIDE_COUNT = "cpu_override_count"
        const val KEY_REAPPLY_FAILURES = "cpu_reapply_failures"
        const val KEY_BOOT_ID = "cpu_boot_id"
        const val KEY_NOTICE = "cpu_notice"
    }
}

/**
 * Limits by cluster as `policy4=1836000;policy8=2294000`. Reading back, anything
 * that is not a valid policy name and a speed a command would accept is dropped,
 * so a damaged file can never put a path or a number into a command.
 */
internal object CpuCapsCodec {

    fun encode(caps: Map<CpuPolicyId, Long>): String = caps.entries.joinToString(";") { "${it.key.name}=${it.value}" }

    fun decode(text: String?): Map<CpuPolicyId, Long> {
        if (text.isNullOrEmpty()) return emptyMap()
        val result = LinkedHashMap<CpuPolicyId, Long>()
        for (entry in text.split(';')) {
            val parts = entry.split('=')
            if (parts.size != 2) continue
            val id = CpuPolicyId.of(parts[0]) ?: continue
            val value = parts[1].toLongOrNull()?.takeIf { it in 1..KernelCommands.MAX_KHZ } ?: continue
            result[id] = value
        }
        return result
    }

    fun encodeIds(ids: Set<CpuPolicyId>): String = ids.joinToString(",") { it.name }

    fun decodeIds(text: String?): Set<CpuPolicyId> =
        text.orEmpty().split(',').mapNotNull(CpuPolicyId::of).toSet()
}
