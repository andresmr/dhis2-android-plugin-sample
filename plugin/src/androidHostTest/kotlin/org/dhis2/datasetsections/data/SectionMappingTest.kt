package org.dhis2.datasetsections.data

import org.hisp.dhis.android.core.common.ObjectWithUid
import org.hisp.dhis.android.core.dataset.Section
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The mapping from the SDK's `Section` to the plugin's own, on the JVM with no `D2`.
 *
 * Built from a real SDK value through its own builder rather than a mock, for the reason
 * `AGENTS.md` gives: a mocked fluent chain proves the mock works, not that the mapping does.
 *
 * No spec scenario claims these. Every `@L` in `data-set-sections.md` is about the ViewModel's
 * ordering and selection, which a fake repository arranges — the nullability below is `androidMain`'s
 * own, and it is the sort of thing that is discovered on a device otherwise.
 */
class SectionMappingTest {

    private fun section(
        uid: String,
        name: String?,
        displayName: String?,
        sortOrder: Int?,
    ): Section = Section.builder()
        .uid(uid)
        .name(name)
        .displayName(displayName)
        .sortOrder(sortOrder)
        .dataSet(ObjectWithUid.create("BfMAe6Itzgt"))
        .build()

    @Test
    fun `carry the display name, the uid and the sort order across`() {
        val mapped = section(
            uid = "sectionA",
            name = "Stock on hand",
            displayName = "Existencias disponibles",
            sortOrder = 1,
        ).toPluginSection()

        assertEquals("sectionA", mapped.uid)
        assertEquals("Existencias disponibles", mapped.displayName)
        assertEquals(1, mapped.sortOrder)
    }

    @Test
    fun `fall back to the name when the server sent no display name`() {
        val mapped = section(
            uid = "sectionA",
            name = "Stock on hand",
            displayName = null,
            sortOrder = 1,
        ).toPluginSection()

        assertEquals("Stock on hand", mapped.displayName)
    }

    @Test
    fun `fall back to the uid rather than to a blank chip`() {
        // A section nobody can read is bad; a chip with no text at all is worse, because there is
        // nothing on screen to report when someone says the plugin is broken.
        val mapped = section(uid = "sectionA", name = null, displayName = null, sortOrder = null)
            .toPluginSection()

        assertEquals("sectionA", mapped.displayName)
        assertEquals(null, mapped.sortOrder)
    }
}
