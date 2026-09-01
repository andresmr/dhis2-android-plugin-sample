package org.dhis2.pluginimplementationtest.repository

import org.dhis2.pluginimplementationtest.model.DataSetSummary
import org.dhis2.pluginimplementationtest.model.DataValueTarget
import org.dhis2.pluginimplementationtest.model.ProgramSummary
import org.dhis2.pluginimplementationtest.model.ScopeSnapshot
import org.dhis2.pluginimplementationtest.model.SearchProbeRun
import org.dhis2.pluginimplementationtest.model.WriteTarget

/**
 * Everything the plugin needs from DHIS2, in the plugin's own vocabulary.
 *
 * This interface is the seam that makes the plugin testable. `ScopedD2` is
 * `class ScopedD2 internal constructor(…)` — final, not constructible outside the SDK, and its
 * accessors return more final SDK repositories — so nothing above this line could be unit-tested if
 * it depended on the SDK directly. The implementation is the only file that does.
 *
 * Every method returns [Result] rather than throwing. A refused read or write is an expected
 * outcome to render, not an exception: a throw escaping into the host composition takes the
 * enclosing screen with it, and Compose cannot express an error boundary around a composable call.
 */
interface PluginRepository {
    /**
     * Reads the granted scope and what is visible under it.
     *
     * Nothing here is hardcoded: the plugin discovers which programs and data sets it may touch
     * rather than naming one and hoping it was granted.
     */
    suspend fun loadScope(): Result<ScopeSnapshot>

    /** Reads the summary of one program the grant exposed. */
    suspend fun loadSummary(programUid: String): Result<ProgramSummary>

    /** Creates one event, returning its UID. Fails with a scope violation when refused. */
    suspend fun addEvent(target: WriteTarget): Result<String>

    /** Runs the search probes against one program. Fails when search itself is not granted. */
    suspend fun probeSearch(programUid: String): Result<SearchProbeRun>

    /** Reads the summary of one data set the grant exposed. */
    suspend fun loadDataSetSummary(dataSetUid: String): Result<DataSetSummary>

    /**
     * Overwrites one data value, returning the value written.
     *
     * Fails with a [ScopeViolation] when the guard refuses — which it checks against the data
     * element and org unit of the value itself, never the data set it belongs to.
     */
    suspend fun writeDataValue(target: DataValueTarget): Result<String>
}
