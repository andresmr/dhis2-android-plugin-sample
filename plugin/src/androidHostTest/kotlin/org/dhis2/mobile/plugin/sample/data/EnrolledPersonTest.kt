package org.dhis2.mobile.plugin.sample.data

import org.dhis2.mobile.plugin.sdk.DisplayAttribute
import org.dhis2.mobile.plugin.sdk.LabelledAttribute
import org.dhis2.mobile.plugin.sdk.TrackedEntityLabeller
import org.hisp.dhis.android.core.trackedentity.TrackedEntityAttributeValue
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Mapping an SDK tracked entity to the plugin's own model, on the JVM.
 *
 * No `D2` and no mocks: the mapping takes data, so the test builds that data through the SDK's own
 * builders and constructs the labeller directly with its fallback injected. Which is where the bugs
 * live — every label defect this plugin has had was here, not in the query that fetched the rows.
 *
 * The labelling *rule* is `plugin-sdk`'s and is tested there. What these assert is that this plugin
 * uses it, and uses it for the programme's attributes rather than for whatever came back.
 */
class EnrolledPersonTest {

    private companion object {
        const val TEI_UID = "tei-uid"

        // Sort order 1 then 2, as the programme configured them.
        val FIRST_NAME = DisplayAttribute(uid = "attr-first", label = "First name")
        val LAST_NAME = DisplayAttribute(uid = "attr-last", label = "Last name")

        val ORG_UNIT = LabelledAttribute("Organisation unit", "Ngelehun CHC")
    }

    // trackedEntityInstance is lateinit on the SDK's builder, so it has to be set even though the
    // mapping never reads it.
    private fun attributeValue(attribute: String, value: String?) =
        TrackedEntityAttributeValue.builder()
            .trackedEntityAttribute(attribute)
            .trackedEntityInstance(TEI_UID)
            .value(value)
            .build()

    private fun tei(vararg values: TrackedEntityAttributeValue) =
        TrackedEntityInstance.builder()
            .uid(TEI_UID)
            .trackedEntityAttributeValues(values.toList())
            .build()

    private fun labeller(vararg attributes: DisplayAttribute) =
        TrackedEntityLabeller(attributes.toList()) { ORG_UNIT }

    // spec: example-program-summary L2
    @Test
    fun `names a person from the programme's attributes, in the programme's order`() {
        // The values arrive gender-first and surname-before-given-name, as the SDK is free to.
        val person = tei(
            attributeValue("attr-gender", "Female"),
            attributeValue("attr-last", "Ryder"),
            attributeValue("attr-first", "Filona"),
        ).toPerson(labeller(FIRST_NAME, LAST_NAME))

        assertEquals(TEI_UID, person.uid)
        // Not "Female", which reading the first value with something in it produces; and not
        // "Ryder Filona", which trusting the SDK's order produces.
        assertEquals("Filona Ryder", person.displayLabel)
    }

    // spec: example-program-summary L2
    @Test
    fun `leaves out an attribute the programme does not list`() {
        val person = tei(
            attributeValue("attr-first", "Filona"),
            attributeValue("attr-gender", "Female"),
        ).toPerson(labeller(FIRST_NAME))

        assertFalse("Female" in person.displayLabel)
        assertEquals("Filona", person.displayLabel)
    }

    // spec: example-program-summary L2
    @Test
    fun `falls back to something a human recognises, never a uid`() {
        // A programme listing attributes this person has no value for. Rendering the uid here is
        // the defect; rendering nothing is nearly as bad.
        val person = tei(attributeValue("attr-gender", "Female"))
            .toPerson(labeller(FIRST_NAME, LAST_NAME))

        assertEquals("Ngelehun CHC", person.displayLabel)
        assertFalse(TEI_UID in person.displayLabel)
    }
}
