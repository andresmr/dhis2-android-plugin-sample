package org.dhis2.pluginimplementationtest.model

/**
 * What the plugin knows about one data set, as plain data.
 *
 * The aggregate counterpart to [ProgramSummary]. It exists to exercise a different half of the
 * scoping model: tracker data is bounded by program and org unit, aggregate data by *data element*
 * — and the grant names data **sets**, which the SDK resolves to the elements they contain.
 */
data class DataSetSummary(
    val dataSetUid: String,
    val dataSetName: String,
    /** Data elements the grant resolved this data set to. */
    val dataElementCount: Int,
    /** Data values readable under the grant, or null when `READ_DATA_VALUE` was not granted. */
    val dataValueCount: Int?,
    /** What the write test would overwrite, or null when no writable value could be resolved. */
    val writeTarget: DataValueTarget? = null,
)

/**
 * The data value the write test would set.
 *
 * Deliberately the coordinates of a value that already exists inside the grant: a data value is
 * identified by five fields, and inventing a combination risks failing for reasons that have nothing
 * to do with scoping. Reusing real coordinates keeps the test about the guard.
 *
 * The guard checks the *data element* and the *org unit* — not the data set — because `DataValue`
 * has no data set column. That indirection is exactly what makes this path worth testing.
 */
data class DataValueTarget(
    /**
     * The data set the value is reported under.
     *
     * Required by the v43+ write API, and a useful reminder that the data set is *not* what the
     * guard checks — it checks the data element and org unit.
     */
    val dataSetUid: String,
    val dataElementUid: String,
    val period: String,
    val orgUnitUid: String,
    val categoryOptionComboUid: String,
    val attributeOptionComboUid: String,
    /** Current value, so the test can write something visibly different. */
    val currentValue: String?,
)
