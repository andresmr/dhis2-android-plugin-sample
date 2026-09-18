package org.dhis2.mobile.plugin.template

import kotlin.test.Test
import kotlin.test.assertEquals
import org.dhis2.mobile.plugin.sdk.DataSetInstanceSlotArguments

/**
 * Which slot the entry point renders, for the arguments the host provides.
 *
 * `content()` cannot be asserted — there is no Compose UI test infrastructure here — so the mapping
 * it makes lives in [slotFor] and is asserted directly. A branch nothing can reach is a branch
 * nothing checks.
 */
class SlotForTest {

    // spec: data-set-body L1
    @Test
    fun `no arguments means the home slot`() {
        assertEquals(Slot.Home, slotFor(null))
    }

    // spec: data-set-body L2
    @Test
    fun `data set instance arguments mean the data set slot, carried through unchanged`() {
        val arguments = DataSetInstanceSlotArguments(
            dataSetUid = "BfMAe6Itzgt",
            periodId = "202401",
            organisationUnitUid = "DiszpKrYNg8",
            attributeOptionComboUid = "HllvX50cXC0",
        )

        assertEquals(Slot.DataSetInstance(arguments), slotFor(arguments))
    }
}
