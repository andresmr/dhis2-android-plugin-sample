package org.dhis2.stock.model

/**
 * One section of a data set, as the plugin needs it.
 *
 * Free of SDK types, like everything in `model/`. The DHIS2 `Section` carries its data elements,
 * greyed fields, indicators and display options too; none of that is read here, because nothing
 * renders it yet. Add fields when something on screen needs them.
 *
 * [sortOrder] is nullable because the SDK's is, and because that nullability is the whole reason
 * ordering is worth a scenario: a section the server never ordered must not disappear, and must not
 * jump to the front by sorting as zero.
 */
data class DataSetSection(
    val uid: String,
    val displayName: String,
    val sortOrder: Int?,
)
