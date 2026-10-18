package app.olauncher.wellbeing

sealed interface FrictionChange {
    data class Pause(val enabled: Boolean) : FrictionChange {
        val on: Boolean get() = enabled
    }

    data class DistractingApps(
        val current: Set<String>,
        val updated: Set<String>,
    ) : FrictionChange {
        val old: Set<String> get() = current
        val new: Set<String> get() = updated
    }

    data class RemoveDistractingApp(
        val packageName: String,
    ) : FrictionChange {
        val app: String get() = packageName
        val appPackage: String get() = packageName
    }

    data class Allowance(
        val currentMin: Int,
        val updatedMin: Int,
    ) : FrictionChange {
        val from: Int get() = currentMin
        val to: Int get() = updatedMin
    }

    data class Escalate(val enabled: Boolean) : FrictionChange {
        val on: Boolean get() = enabled
    }

    data class AskWhy(val enabled: Boolean) : FrictionChange {
        val on: Boolean get() = enabled
    }

    data class Lock(val enabled: Boolean) : FrictionChange {
        val on: Boolean get() = enabled
    }
}

typealias FrictionChangeEscalation = FrictionChange.Escalate
typealias FrictionChangeLock = FrictionChange.Lock

/**
 * Pure commitment lock rules:
 * Tightening friction never requires a wait.
 * Loosening friction requires waiting [WAIT_SECONDS] seconds when commitment lock is enabled.
 */
object CommitLock {

    const val WAIT_SECONDS: Int = 30

    fun isLoosening(change: FrictionChange): Boolean = when (change) {
        is FrictionChange.Pause -> !change.enabled
        is FrictionChange.DistractingApps -> (change.current - change.updated).isNotEmpty()
        is FrictionChange.RemoveDistractingApp -> true
        is FrictionChange.Allowance -> {
            val from = change.currentMin
            val to = change.updatedMin
            if (from <= 0) {
                false
            } else {
                to == 0 || to > from
            }
        }
        is FrictionChange.Escalate -> !change.enabled
        is FrictionChange.AskWhy -> !change.enabled
        is FrictionChange.Lock -> !change.enabled
    }

    fun isTightening(change: FrictionChange): Boolean = when (change) {
        is FrictionChange.Pause -> change.enabled
        is FrictionChange.DistractingApps -> (change.current - change.updated).isEmpty() && (change.updated - change.current).isNotEmpty()
        is FrictionChange.RemoveDistractingApp -> false
        is FrictionChange.Allowance -> {
            val from = change.currentMin
            val to = change.updatedMin
            if (from <= 0) {
                to > 0
            } else {
                to in 1 until from
            }
        }
        is FrictionChange.Escalate -> change.enabled
        is FrictionChange.AskWhy -> change.enabled
        is FrictionChange.Lock -> change.enabled
    }

    fun needsWait(lockOn: Boolean, change: FrictionChange): Boolean {
        if (!lockOn) return false
        return isLoosening(change)
    }
}

/** The friction settings the commit lock protects, read from live prefs or from a backup. */
data class FrictionSettings(
    val pauseEnabled: Boolean,
    val distractingApps: Set<String>,
    val allowanceMin: Int,
    val escalate: Boolean,
    val askWhy: Boolean,
    val commitLock: Boolean,
) {
    companion object {
        const val KEY_PAUSE_ENABLED = "PAUSE_ENABLED"
        const val KEY_DISTRACTING_APPS = "DISTRACTING_APPS"
        const val KEY_ALLOWANCE_MIN = "PAUSE_ALLOWANCE_MIN"
        const val KEY_ESCALATE = "PAUSE_ESCALATE"
        const val KEY_ASK_WHY = "PAUSE_ASK_WHY"
        const val KEY_COMMIT_LOCK = "COMMIT_LOCK"

        /** A restore clears prefs first, so keys missing from the backup fall back to the defaults. */
        fun fromPrefsMap(prefs: Map<String, Any?>): FrictionSettings = FrictionSettings(
            pauseEnabled = prefs[KEY_PAUSE_ENABLED] as? Boolean ?: false,
            distractingApps = (prefs[KEY_DISTRACTING_APPS] as? Set<*>)
                ?.mapNotNull { it?.toString() }?.toSet() ?: emptySet(),
            allowanceMin = (prefs[KEY_ALLOWANCE_MIN] as? Number)?.toInt() ?: 0,
            escalate = prefs[KEY_ESCALATE] as? Boolean ?: false,
            askWhy = prefs[KEY_ASK_WHY] as? Boolean ?: false,
            commitLock = prefs[KEY_COMMIT_LOCK] as? Boolean ?: false,
        )
    }
}

/** Reuses [CommitLock.needsWait] for every friction setting a backup restore would change. */
fun CommitLock.restoreNeedsWait(
    lockOn: Boolean,
    before: FrictionSettings,
    after: FrictionSettings,
): Boolean {
    // A flag only counts as a change when it actually flips off from on.
    val changes = buildList {
        if (before.pauseEnabled != after.pauseEnabled) add(FrictionChange.Pause(after.pauseEnabled))
        add(FrictionChange.DistractingApps(before.distractingApps, after.distractingApps))
        add(FrictionChange.Allowance(before.allowanceMin, after.allowanceMin))
        if (before.escalate != after.escalate) add(FrictionChange.Escalate(after.escalate))
        if (before.askWhy != after.askWhy) add(FrictionChange.AskWhy(after.askWhy))
        if (before.commitLock != after.commitLock) add(FrictionChange.Lock(after.commitLock))
    }
    return changes.any { needsWait(lockOn, it) }
}
