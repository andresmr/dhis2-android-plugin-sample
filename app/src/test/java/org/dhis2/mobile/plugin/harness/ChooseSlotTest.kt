package org.dhis2.mobile.plugin.harness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.dhis2.mobile.plugin.sdk.InjectionPoint

/**
 * The harness's slot choice, which is the one part of `HarnessSlot.kt` a JVM test can reach.
 *
 * Everything past it needs a real `D2` — an Android `Context`, a database and an HTTP stack — so it
 * lives under `## Device scenarios` in the spec instead. This is the branchy half, and the half
 * that decides what a developer sees before anything has loaded.
 */
class ChooseSlotTest {

    private val both = listOf(
        InjectionPoint.HOME_ABOVE_PROGRAM_LIST,
        InjectionPoint.DATA_SET_INSTANCE_CONTENT,
    )

    // spec: data-set-body L3
    @Test
    fun `prefer the configured replacement over the additive slot`() {
        val choice = chooseSlot(declared = both, dataSetUids = listOf("BfMAe6Itzgt"), override = "")

        assertEquals(
            SlotChoice.Chosen(InjectionPoint.DATA_SET_INSTANCE_CONTENT, "BfMAe6Itzgt"),
            choice,
        )
    }

    // spec: data-set-body L4
    @Test
    fun `fall back to the additive slot when the replacement has no UIDs`() {
        val choice = chooseSlot(declared = both, dataSetUids = emptyList(), override = "")

        assertEquals(
            SlotChoice.Chosen(InjectionPoint.HOME_ABOVE_PROGRAM_LIST, null),
            choice,
        )
    }

    // spec: data-set-body L5
    @Test
    fun `refuse an override naming a slot the plugin does not declare`() {
        val choice = chooseSlot(
            declared = listOf(InjectionPoint.HOME_ABOVE_PROGRAM_LIST),
            dataSetUids = emptyList(),
            override = "DATA_SET_INSTANCE_CONTENT",
        )

        val unavailable = choice as SlotChoice.Unavailable
        assertTrue(
            unavailable.reason.contains("HOME_ABOVE_PROGRAM_LIST"),
            "the reason should name what *is* declared, or it says nothing actionable: " +
                unavailable.reason,
        )
    }

    @Test
    fun `honour an override the plugin does declare`() {
        val choice = chooseSlot(
            declared = both,
            dataSetUids = listOf("BfMAe6Itzgt"),
            override = " home_above_program_list ",
        )

        assertEquals(
            SlotChoice.Chosen(InjectionPoint.HOME_ABOVE_PROGRAM_LIST, null),
            choice,
            "harness.slot is hand-typed in local.properties, so it is trimmed and case-insensitive",
        )
    }

    @Test
    fun `report no slot at all rather than guessing`() {
        val choice = chooseSlot(declared = emptyList(), dataSetUids = emptyList(), override = "")

        assertTrue(choice is SlotChoice.Unavailable)
    }
}
