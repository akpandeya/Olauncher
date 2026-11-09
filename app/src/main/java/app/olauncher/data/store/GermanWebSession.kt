package app.olauncher.data.store

import android.content.Context
import app.olauncher.data.GermanPrefs
import app.olauncher.data.GermanPrefsStore
import app.olauncher.data.appPrefs

/**
 * The launcher's session with the German web app (epic #835, task 01):
 * [SignedOut] (never paired, or signed out by the user), [SignedIn] (a stored
 * credential) or [Lapsed] (a token that used to work now gets 401 — revoked on
 * the web or the account removed; the user must pair again).
 *
 * The state itself ([stateOf]) is Context-free and unit-tested; the class only
 * wires it to the encrypted credential store and two prefs
 * (`GERMAN_WEB_LAPSED`, `GERMAN_WEB_BASE_URL` — see [PrefsGerman]). The
 * credential never leaves [GermanDeviceCredentialStorage]; the lapsed flag and
 * the normalized server address are the only pairing state in prefs, both
 * device-specific and never backed up.
 *
 * Signing out or lapsing touches only the credential and those prefs —
 * never `words.json` or any capture data, so local learning keeps working.
 */
sealed class GermanWebSessionState {
    object SignedOut : GermanWebSessionState()

    data class SignedIn(val username: String, val deviceId: String) : GermanWebSessionState()

    object Lapsed : GermanWebSessionState()
}

class GermanWebSession(
    private val credentials: GermanDeviceCredentialStorage,
    private val prefs: GermanPrefs,
) {
    constructor(context: Context) : this(
        GermanDeviceCredentialStore(context),
        GermanPrefsStore(context.appPrefs()),
    )

    /** The session state right now: a valid credential wins over the lapsed flag. */
    fun state(): GermanWebSessionState = stateOf(credentials.read(), prefs.germanWebLapsed)

    /**
     * A pairing succeeded: store the credential, remember the normalized server
     * address (tasks 03–08 build every URL from it; null when unset means no
     * network call, no link) and clear any lapsed flag from an earlier token.
     */
    fun onPaired(credential: GermanDeviceCredential, baseUrl: String) {
        credentials.save(credential)
        prefs.germanWebBaseUrl = GermanWebBaseUrl.normalize(baseUrl) ?: ""
        prefs.germanWebLapsed = false
    }

    /** The user signed out: drop the credential and the server address; not lapsed. */
    fun signOut() {
        credentials.clear()
        prefs.germanWebBaseUrl = ""
        prefs.germanWebLapsed = false
    }

    /**
     * A stored token got 401: revoked on the web or the account removed. The
     * credential is gone; the server address stays so pairing again does not
     * need it retyped. Tasks 03 and 04 call this on any 401.
     */
    fun onUnauthorized() {
        credentials.clear()
        prefs.germanWebLapsed = true
    }

    /** The normalized server address, or null when not paired with one yet. */
    fun baseUrl(): String? = prefs.germanWebBaseUrl.ifBlank { null }

    companion object {
        /** The state for a stored credential (null = nothing usable) and lapsed flag. */
        fun stateOf(credential: GermanDeviceCredential?, lapsed: Boolean): GermanWebSessionState = when {
            credential != null ->
                GermanWebSessionState.SignedIn(username = credential.username, deviceId = credential.deviceId)
            lapsed -> GermanWebSessionState.Lapsed
            else -> GermanWebSessionState.SignedOut
        }
    }
}
