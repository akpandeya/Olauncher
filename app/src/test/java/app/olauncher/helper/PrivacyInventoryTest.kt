package app.olauncher.helper

import app.olauncher.helper.PrivacyInventory.GrantState
import app.olauncher.helper.PrivacyInventory.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrivacyInventoryTest {

    private fun manifestPermissions(): Set<String> {
        val manifest = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
            .map(::File).first { it.exists() }
        val found = Regex("<uses-permission[^>]*?android:name=\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL)
            .findAll(manifest.readText()).map { it.groupValues[1] }.toSet()
        assertTrue("manifest parse found too few permissions", found.size >= 19)
        return found
    }

    @Test
    fun coversEveryManifestPermission() {
        val ids = PrivacyInventory.entries.map { it.id }.toSet()
        assertEquals(emptySet<String>(), manifestPermissions() - ids)
    }

    @Test
    fun hasNoStalePermissionEntries() {
        val permissionIds = PrivacyInventory.entries.filter { it.isPermission }.map { it.id }.toSet()
        assertEquals(manifestPermissions(), permissionIds)
    }

    @Test
    fun idsAreUnique() {
        val ids = PrivacyInventory.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun vibrateIsNormalNotRuntime() {
        val vibrate = PrivacyInventory.entries.first { it.id == "android.permission.VIBRATE" }
        assertEquals(Kind.NORMAL, vibrate.kind)
        assertEquals(false, vibrate.isRuntime)
    }

    @Test
    fun networkPathsAreNotPermissions() {
        val net = PrivacyInventory.entries.filter { it.kind == Kind.NETWORK }
        assertEquals(
            setOf(
                PrivacyInventory.NET_WEATHER,
                PrivacyInventory.NET_DICTIONARY,
                PrivacyInventory.NET_RATES,
                PrivacyInventory.NET_ARTWORK,
            ),
            net.map { it.id }.toSet(),
        )
        assertTrue(net.none { it.isPermission || it.isRuntime })
    }

    @Test
    fun grantedStateFollowsGrantedSet() {
        val states = PrivacyInventory.states(
            setOf("android.permission.READ_CALENDAR", "android.permission.health.READ_STEPS")
        ).associateBy { it.entry.id }

        assertEquals(GrantState.GRANTED, states.getValue("android.permission.READ_CALENDAR").state)
        assertEquals(GrantState.GRANTED, states.getValue("android.permission.health.READ_STEPS").state)
        assertEquals(GrantState.NOT_GRANTED, states.getValue("android.permission.READ_CONTACTS").state)
        assertEquals(GrantState.NOT_GRANTED, states.getValue("android.permission.ACCESS_COARSE_LOCATION").state)
        assertEquals(GrantState.NOT_GRANTED, states.getValue("android.permission.health.READ_SLEEP").state)
        assertEquals(GrantState.NOT_GRANTED, states.getValue("android.permission.PACKAGE_USAGE_STATS").state)
    }

    @Test
    fun normalPermissionsAlwaysGrantedAndNetworkNotApplicable() {
        val states = PrivacyInventory.states(emptySet()).associateBy { it.entry.id }
        assertTrue(states.getValue("android.permission.VIBRATE").isGranted)
        assertTrue(states.getValue("android.permission.INTERNET").isGranted)
        assertEquals(GrantState.NOT_APPLICABLE, states.getValue(PrivacyInventory.NET_WEATHER).state)
        assertEquals(PrivacyInventory.entries.size, states.size)
    }

    @Test
    fun revokeKeysMatchKind() {
        val byId = PrivacyInventory.entries.associateBy { it.id }
        assertEquals(PrivacyInventory.REVOKE_APP_INFO, byId.getValue("android.permission.READ_CALENDAR").revokeKey)
        assertEquals(PrivacyInventory.REVOKE_USAGE_ACCESS, byId.getValue("android.permission.PACKAGE_USAGE_STATS").revokeKey)
        assertEquals(PrivacyInventory.REVOKE_HEALTH_CONNECT, byId.getValue("android.permission.health.READ_STEPS").revokeKey)
        assertEquals(PrivacyInventory.REVOKE_ADB, byId.getValue("android.permission.WRITE_SECURE_SETTINGS").revokeKey)
        assertEquals(PrivacyInventory.REVOKE_NONE, byId.getValue("android.permission.VIBRATE").revokeKey)
    }
}
