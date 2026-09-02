package org.dhis2.mobile.plugin.sample.data

import org.hisp.dhis.android.core.trackedentity.TrackedEntityAttributeValue
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Mapping an SDK tracked entity to the plugin's own model, on the JVM.
 *
 * No `D2` and no mocks: the mapping takes data, so the test builds that data through the SDK's own
 * builders. Which is where the bugs live — every label defect this plugin has had was here, not in
 * the query that fetched the rows.
 */
class ToPersonTest {

    private companion object {
        const val TEI_UID = "tei-uid"
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

    @Test
    fun `labels each value with the attribute's display name`() {
        val person = tei(
            attributeValue("attr-first", "Filona"),
            attributeValue("attr-last", "Ryder"),
        ).toPerson(mapOf("attr-first" to "First name", "attr-last" to "Last name"))

        assertEquals(TEI_UID, person.uid)
        assertEquals(
            listOf("First name" to "Filona", "Last name" to "Ryder"),
            person.attributes.map { it.label to it.value },
        )
    }

    @Test
    fun `keeps the source order, so the label reads the way the server configured it`() {
        val person = tei(
            attributeValue("attr-last", "Ryder"),
            attributeValue("attr-first", "Filona"),
        ).toPerson(mapOf("attr-first" to "First name", "attr-last" to "Last name"))

        assertEquals(listOf("Last name", "First name"), person.attributes.map { it.label })
    }

    @Test
    fun `drops an attribute with no value rather than rendering a blank row`() {
        val person = tei(
            attributeValue("attr-first", "Filona"),
            attributeValue("attr-last", null),
        ).toPerson(mapOf("attr-first" to "First name", "attr-last" to "Last name"))

        assertEquals(listOf("First name"), person.attributes.map { it.label })
    }

    @Test
    fun `falls back to the attribute uid when no display name is known`() {
        // Better a UID than an empty label — but it is a signal that attributeLabels() missed one.
        val person = tei(attributeValue("attr-unknown", "42"))
            .toPerson(attributeNames = emptyMap())

        assertEquals(listOf("attr-unknown" to "42"), person.attributes.map { it.label to it.value })
    }

    @Test
    fun `maps a tracked entity with no attributes at all`() {
        assertEquals(emptyList(), tei().toPerson(emptyMap()).attributes)
    }
}
