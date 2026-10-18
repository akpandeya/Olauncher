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
