package app.olauncher.helper

import android.os.UserHandle
import app.olauncher.data.AppModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMatcherTest {

    // --- Tier 1: Exact (100) ---

    @Test
    fun `exact match scores 100`() {
        assertEquals(100, AppMatcher.score("WhatsApp", "whatsapp"))
        assertEquals(100, AppMatcher.score("YouTube", "youtube"))
        assertEquals(100, AppMatcher.score("Google Maps", "google maps"))
        assertEquals(100, AppMatcher.score("Google Maps", "googlemaps"))
        assertEquals(100, AppMatcher.score("Google Maps", "google-maps"))
        assertEquals(100, AppMatcher.score("K-9 Mail", "k9mail"))
    }

    // --- Tier 2: Prefix (90) ---

    @Test
    fun `prefix match scores 90`() {
        // "whatsap" -> WhatsApp (prefix)
        assertEquals(90, AppMatcher.score("WhatsApp", "whatsap"))
        assertEquals(90, AppMatcher.score("Gmail", "gm"))
        assertEquals(90, AppMatcher.score("YouTube", "you"))
        assertEquals(90, AppMatcher.score("Google Maps", "google"))
        assertEquals(90, AppMatcher.score("Google Maps", "google m"))
    }

    // --- Tier 3: Word-start prefix (80) ---

    @Test
    fun `word-start prefix scores 80`() {
        // "maps" in "Google Maps" (80)
        assertEquals(80, AppMatcher.score("Google Maps", "maps"))
        assertEquals(80, AppMatcher.score("Google Maps", "map"))
        assertEquals(80, AppMatcher.score("YouTube", "tube"))
        assertEquals(80, AppMatcher.score("YouTube", "tub"))
        assertEquals(80, AppMatcher.score("WhatsApp", "app"))
        assertEquals(80, AppMatcher.score("WhatsApp", "ap"))
        assertEquals(80, AppMatcher.score("F-Droid", "droid"))
        assertEquals(80, AppMatcher.score("Google Keep - notes and lists", "notes"))
        assertEquals(80, AppMatcher.score("Google Maps Go", "maps go"))
    }

    // --- Tier 4: Initials (70) ---

    @Test
    fun `initials score 70`() {
        // "gm" -> "Google Maps", "yt" -> "YouTube" via capitals
        assertEquals(70, AppMatcher.score("Google Maps", "gm"))
        assertEquals(70, AppMatcher.score("YouTube", "yt"))
        assertEquals(70, AppMatcher.score("WhatsApp", "wa"))
        assertEquals(70, AppMatcher.score("Call of Duty", "cod"))
        assertEquals(70, AppMatcher.score("Call of Duty", "cd"))
        assertEquals(70, AppMatcher.score("Google Play Store", "gps"))
        assertEquals(70, AppMatcher.score("Google Play Store", "gp"))
    }

    // --- Tier 5: Substring (60) ---

    @Test
    fun `substring match scores 60`() {
        assertEquals(60, AppMatcher.score("Telegram", "gram"))
        assertEquals(60, AppMatcher.score("Spotify", "tify"))
        assertEquals(60, AppMatcher.score("Google", "oogle"))
        assertEquals(60, AppMatcher.score("WhatsApp", "hat"))
    }

    // --- Tier 6: Fuzzy, one edit against label prefix for queries >= 4 chars (40) ---

    @Test
    fun `fuzzy one edit scores 40`() {
        // "whatsapo" -> WhatsApp (fuzzy)
        assertEquals(40, AppMatcher.score("WhatsApp", "whatsapo"))
        // "spotfy" -> Spotify (fuzzy)
        assertEquals(40, AppMatcher.score("Spotify", "spotfy"))
        // Substitution: "spitify" -> Spotify
        assertEquals(40, AppMatcher.score("Spotify", "spitify"))
        // Transposition: "spotfiy" -> Spotify
        assertEquals(40, AppMatcher.score("Spotify", "spotfiy"))
        // Fuzzy against label prefix for longer labels
        assertEquals(40, AppMatcher.score("WhatsApp Messenger", "whatsapo"))
        assertEquals(40, AppMatcher.score("Spotify: Music & Podcasts", "spotfy"))
    }

    @Test
    fun `fuzzy requires at least 4 characters`() {
        // Query of 3 chars with 1 edit: no match
        assertNull(AppMatcher.score("Spotify", "xpo"))
        assertNull(AppMatcher.score("Uber", "xbe"))
    }

    @Test
    fun `fuzzy fails when edit distance is greater than 1`() {
        // 2 edits away
        assertNull(AppMatcher.score("WhatsApp", "whatsapoo"))
        assertNull(AppMatcher.score("Spotify", "spotyfi")) // non-adjacent transposition
        assertNull(AppMatcher.score("Spotify", "spotyy"))
    }

    // --- Acceptance criteria: Relative ordering ---

    @Test
    fun `gm lists Gmail above Google Maps`() {
        val gmailScore = AppMatcher.score("Gmail", "gm")
        val googleMapsScore = AppMatcher.score("Google Maps", "gm")

        assertEquals(90, gmailScore)
        assertEquals(70, googleMapsScore)
        assertTrue(gmailScore!! > googleMapsScore!!)

        val apps = listOf("Google Maps", "Gmail")
        val sorted = apps.mapNotNull { label ->
            AppMatcher.score(label, "gm")?.let { score -> label to score }
        }.sortedWith(
            compareByDescending<Pair<String, Int>> { it.second }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first }
        ).map { it.first }

        assertEquals(listOf("Gmail", "Google Maps"), sorted)
    }

    // --- Acceptance criteria: Umlauts ("uber" <-> "Über") ---

    @Test
    fun `umlauts normalized correctly`() {
        // Exact match both directions
        assertEquals(100, AppMatcher.score("Über", "uber"))
        assertEquals(100, AppMatcher.score("uber", "Über"))
        // Prefix match both directions
        assertEquals(90, AppMatcher.score("Über", "ube"))
        assertEquals(90, AppMatcher.score("uber", "Übe"))
        // Other umlauts
        assertEquals(100, AppMatcher.score("München App", "munchen app"))
        assertEquals(90, AppMatcher.score("München App", "munchen"))
        assertEquals(80, AppMatcher.score("Stadt München", "munchen"))
    }

    // --- Acceptance criteria: Single-char queries (no fuzzy) ---

    @Test
    fun `single-char queries do not match fuzzy`() {
        assertNull(AppMatcher.score("Uber", "z"))
        assertNull(AppMatcher.score("WhatsApp", "x"))
        assertNull(AppMatcher.score("Spotify", "q"))
    }

    @Test
    fun `single-char queries can match prefix, word-start, or substring`() {
        assertEquals(90, AppMatcher.score("WhatsApp", "w"))
        assertEquals(80, AppMatcher.score("Google Maps", "m"))
        assertEquals(60, AppMatcher.score("Google", "l"))
    }

    // --- Acceptance criteria: Query that matches nothing ---

    @Test
    fun `query that matches nothing returns null`() {
        assertNull(AppMatcher.score("WhatsApp", "xyzabc"))
        assertNull(AppMatcher.score("Google Maps", "nonexistent"))
        assertNull(AppMatcher.score("Uber", "calculator"))
    }

    // --- Empty and blank queries/labels ---

    @Test
    fun `blank or empty queries return null`() {
        assertNull(AppMatcher.score("WhatsApp", ""))
        assertNull(AppMatcher.score("WhatsApp", "   "))
        assertNull(AppMatcher.score("WhatsApp", "---"))
        assertNull(AppMatcher.score("", "whatsapp"))
        assertNull(AppMatcher.score("   ", "whatsapp"))
    }

    // --- Damerau-Levenshtein helper tests ---

    @Test
    fun `damerau-levenshtein distance calculations`() {
        assertEquals(0, AppMatcher.damerauLevenshtein("spotify", "spotify"))
        assertEquals(1, AppMatcher.damerauLevenshtein("spotfy", "spotify")) // insertion
        assertEquals(1, AppMatcher.damerauLevenshtein("whatsapo", "whatsapp")) // deletion
        assertEquals(1, AppMatcher.damerauLevenshtein("spitify", "spotify")) // substitution
        assertEquals(1, AppMatcher.damerauLevenshtein("spotfiy", "spotify")) // adjacent transposition
        assertEquals(2, AppMatcher.damerauLevenshtein("spotyfi", "spotify")) // non-adjacent transposition
        assertEquals(2, AppMatcher.damerauLevenshtein("whatsapoo", "whatsapp"))
    }

    // --- Auto-launch score threshold helper tests ---

    @Test
    fun `auto launch threshold behavior`() {
        // Scores >= 60 qualify as strong matches: exact(100), prefix(90), word-start(80), initials(70), substring(60)
        assertTrue(100 >= 60)
        assertTrue(90 >= 60)
        assertTrue(80 >= 60)
        assertTrue(70 >= 60)
        assertTrue(60 >= 60)
        // Fuzzy (40) does NOT qualify
        assertTrue(40 < 60)

        // Typing "yt": exactly one match (YouTube, 70), strong match count = 1 -> auto-launch
        val ytScores = listOf(AppMatcher.score("YouTube", "yt")!!)
        assertEquals(listOf(70), ytScores)
        assertEquals(1, ytScores.count { it >= 60 })

        // Typing "spotfy": only fuzzy match (Spotify, 40), strong match count = 0 -> no auto-launch
        val spotfyScores = listOf(AppMatcher.score("Spotify", "spotfy")!!)
        assertEquals(listOf(40), spotfyScores)
        assertEquals(0, spotfyScores.count { it >= 60 })
    }

    // --- Adapter filter and ordering behavior tests ---

    private val dummyUser: UserHandle by lazy {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafeField.isAccessible = true
        val unsafe = theUnsafeField.get(null)
        val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
        allocateInstance.invoke(unsafe, UserHandle::class.java) as UserHandle
    }

    private fun createApp(
        label: String,
        packageName: String = "pkg.$label"
    ): AppModel.App = AppModel.App(
        appLabel = label,
        key = null,
        appPackage = packageName,
        activityClassName = null,
        isNew = false,
        user = dummyUser,
        firstInstallTime = 0L
    )

    private fun createPrivateHeader(): AppModel.PrivateSpaceHeader =
        AppModel.PrivateSpaceHeader(isLocked = true, user = dummyUser)

    private fun filterApps(apps: List<AppModel>, query: String): List<Pair<AppModel, Int>> {
        return apps.mapNotNull { app ->
            if (app is AppModel.PrivateSpaceHeader) return@mapNotNull null
            val score = AppMatcher.score(app.appLabel, query) ?: return@mapNotNull null
            app to score
        }.sortedWith(
            compareByDescending<Pair<AppModel, Int>> { it.second }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.appLabel }
        )
    }

    @Test
    fun `adapter filter keeps matches sorted by score descending then label`() {
        val app1 = createApp("Google Maps")
        val app2 = createApp("Gmail")
        val app3 = createApp("Camera")
        val app4 = createApp("Calculator")

        val results = filterApps(listOf(app1, app2, app3, app4), "gm")
        assertEquals(listOf("Gmail", "Google Maps"), results.map { it.first.appLabel })
        assertEquals(listOf(90, 70), results.map { it.second })

        // When scores are tied, sorted alphabetically by label
        val tiedResults = filterApps(listOf(app3, app4), "ca")
        assertEquals(listOf("Calculator", "Camera"), tiedResults.map { it.first.appLabel })
        assertEquals(listOf(90, 90), tiedResults.map { it.second })
    }

    @Test
    fun `private-space rows stay excluded from search results`() {
        val app1 = createApp("Google Maps")
        val header = createPrivateHeader()
        val app2 = createApp("Gmail")

        val results = filterApps(listOf(app1, header, app2), "gm")
        assertFalse(results.any { it.first is AppModel.PrivateSpaceHeader })
        assertEquals(listOf("Gmail", "Google Maps"), results.map { it.first.appLabel })
    }

    @Test
    fun `renamed labels are what get matched`() {
        val originalApp = createApp("Calculator", packageName = "com.android.calculator2")
        // Renamed to "Math Helper"
        val renamedApp = originalApp.copy(appLabel = "Math Helper")

        // Matches new label
        val resultsMath = filterApps(listOf(renamedApp), "math")
        assertEquals(1, resultsMath.size)
        assertEquals("Math Helper", resultsMath[0].first.appLabel)

        // Does not match old label
        val resultsCalc = filterApps(listOf(renamedApp), "calc")
        assertTrue(resultsCalc.isEmpty())
    }
}
