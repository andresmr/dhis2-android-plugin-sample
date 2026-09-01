package org.dhis2.pluginimplementationtest.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Comparing the *declared* grant against what the SDK actually returns.
 *
 * This is the diagnostic the plugin exists to provide. A UID the config names but the SDK never
 * hands back means one of three things — wrong UID, not synced to the device, or the capability that
 * would expose it was withheld — and all three are otherwise invisible, because an out-of-scope read
 * returns empty rather than failing.
 */
class ScopeSnapshotTest {

    private fun item(uid: String) = MetadataItem(uid, "Name of $uid")

    @Test
    fun `report a declared uid the SDK does not return`() {
        val dimension = ScopeDimension(
            label = "Programs",
            declared = DeclaredGrant.Uids(listOf("granted", "typo")),
            visible = listOf(item("granted")),
        )

        assertEquals(listOf("typo"), dimension.missing)
    }

    @Test
    fun `report nothing missing when every declared uid is visible`() {
        val dimension = ScopeDimension(
            label = "Programs",
            declared = DeclaredGrant.Uids(listOf("a", "b")),
            visible = listOf(item("b"), item("a")),
        )

        assertTrue(dimension.missing.isEmpty())
    }

    @Test
    fun `report nothing missing for an all grant, however much is visible`() {
        // "all" names no UIDs, so there is nothing that could fail to appear.
        val dimension = ScopeDimension(
            label = "Programs",
            declared = DeclaredGrant.All,
            visible = listOf(item("whatever")),
        )

        assertTrue(dimension.missing.isEmpty())
    }

    @Test
    fun `report nothing missing for a none grant`() {
        val dimension = ScopeDimension(
            label = "Data sets",
            declared = DeclaredGrant.None,
            visible = emptyList(),
        )

        assertTrue(dimension.missing.isEmpty())
    }

    @Test
    fun `flag a grant that yields nothing at all as worth explaining`() {
        // Declared UIDs but an empty result is the single most common misconfiguration, and the one
        // that looks identical to "there is no data" from inside the plugin.
        val dimension = ScopeDimension(
            label = "Programs",
            declared = DeclaredGrant.Uids(listOf("a")),
            visible = emptyList(),
        )

        assertTrue(dimension.grantedButEmpty)
    }

    @Test
    fun `do not flag an intentionally closed dimension as a problem`() {
        val dimension = ScopeDimension(
            label = "Data sets",
            declared = DeclaredGrant.None,
            visible = emptyList(),
        )

        assertTrue(!dimension.grantedButEmpty)
    }

    @Test
    fun `never report a hierarchy root as missing`() {
        // Verified on a device: a config granting DESCENDANTS of the Sierra Leone root resolved to
        // Ngelehun CHC, the only org unit present locally. The root is not a row on the device at
        // all, so treating it as "declared but not visible" cried wolf about a working grant.
        val dimension = ScopeDimension(
            label = "Org units",
            declared = DeclaredGrant.Roots(listOf("ImspTQPwCqd"), "DESCENDANTS"),
            visible = listOf(item("DiszpKrYNg8")),
        )

        assertTrue(dimension.missing.isEmpty())
    }

    @Test
    fun `still flag a root grant that resolved to nothing`() {
        // The root not appearing is normal; nothing beneath it appearing is not.
        val dimension = ScopeDimension(
            label = "Org units",
            declared = DeclaredGrant.Roots(listOf("ImspTQPwCqd"), "DESCENDANTS"),
            visible = emptyList(),
        )

        assertTrue(dimension.grantedButEmpty)
    }

    @Test
    fun `surface a missing uid as a warning, so it survives the card being collapsed`() {
        val snapshot = ScopeSnapshot(
            capabilities = listOf("READ_METADATA"),
            dimensions = listOf(
                ScopeDimension("Programs", DeclaredGrant.Uids(listOf("ok", "typo")), listOf(item("ok"))),
            ),
        )

        assertEquals(listOf("Programs: declared but not visible — typo"), snapshot.warnings)
    }

    @Test
    fun `prefer the specific warning when a grant is both missing and empty`() {
        // Both conditions hold, but naming the UID is strictly more useful than saying "nothing".
        val snapshot = ScopeSnapshot(
            capabilities = emptyList(),
            dimensions = listOf(
                ScopeDimension("Programs", DeclaredGrant.Uids(listOf("typo")), emptyList()),
            ),
        )

        assertEquals(listOf("Programs: declared but not visible — typo"), snapshot.warnings)
    }

    @Test
    fun `warn when a root grant resolved to nothing`() {
        val snapshot = ScopeSnapshot(
            capabilities = emptyList(),
            dimensions = listOf(
                ScopeDimension("Org units", DeclaredGrant.Roots(listOf("root"), "DESCENDANTS"), emptyList()),
            ),
        )

        assertEquals(listOf("Org units: granted, but nothing is visible on this device"), snapshot.warnings)
    }

    @Test
    fun `stay silent when every dimension is healthy or deliberately closed`() {
        val snapshot = ScopeSnapshot(
            capabilities = listOf("READ_METADATA"),
            dimensions = listOf(
                ScopeDimension("Programs", DeclaredGrant.Uids(listOf("ok")), listOf(item("ok"))),
                ScopeDimension("Data sets", DeclaredGrant.None, emptyList()),
            ),
        )

        assertTrue(snapshot.warnings.isEmpty())
    }

    @Test
    fun `describe a declared grant for display`() {
        assertEquals("all", DeclaredGrant.All.describe())
        assertEquals("none", DeclaredGrant.None.describe())
        assertEquals("2 declared", DeclaredGrant.Uids(listOf("a", "b")).describe())
        // The mode matters as much as the root, so it belongs in the label.
        assertEquals("1 root, DESCENDANTS", DeclaredGrant.Roots(listOf("a"), "DESCENDANTS").describe())
    }
}
