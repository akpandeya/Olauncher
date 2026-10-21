package app.olauncher.ui.privacy

import app.olauncher.R
import app.olauncher.helper.PrivacyInventory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PrivacyTextTest {

    @Test
    fun `every inventory purpose key has a string`() {
        for (entry in PrivacyInventory.entries) {
            assertNotNull("no string for ${entry.purposeKey}", PrivacyText.purposeRes(entry.purposeKey))
        }
    }

    @Test
    fun `revoke keys map to their targets`() {
        assertEquals(PrivacyText.RevokeTarget.APP_INFO, PrivacyText.revokeTarget(PrivacyInventory.REVOKE_APP_INFO))
        assertEquals(PrivacyText.RevokeTarget.USAGE_ACCESS, PrivacyText.revokeTarget(PrivacyInventory.REVOKE_USAGE_ACCESS))
        assertEquals(PrivacyText.RevokeTarget.HEALTH_CONNECT, PrivacyText.revokeTarget(PrivacyInventory.REVOKE_HEALTH_CONNECT))
        assertEquals(PrivacyText.RevokeTarget.THIS_SETTINGS, PrivacyText.revokeTarget(PrivacyInventory.REVOKE_FEATURE_OFF))
        assertEquals(PrivacyText.RevokeTarget.NONE, PrivacyText.revokeTarget(PrivacyInventory.REVOKE_NONE))
        assertEquals(PrivacyText.RevokeTarget.NONE, PrivacyText.revokeTarget(PrivacyInventory.REVOKE_ADB))
        assertNull(PrivacyText.revokeLabelRes(PrivacyText.RevokeTarget.NONE))
        assertEquals(R.string.privacy_revoke_feature_off, PrivacyText.revokeLabelRes(PrivacyText.RevokeTarget.THIS_SETTINGS))
    }

    @Test
    fun `states map to three different strings`() {
        val ids = PrivacyInventory.GrantState.values().map { PrivacyText.stateRes(it) }
        assertEquals(3, ids.toSet().size)
    }
}
