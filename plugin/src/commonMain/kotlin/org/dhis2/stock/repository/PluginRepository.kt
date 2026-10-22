package org.dhis2.stock.repository

import org.dhis2.stock.model.DataSetSection

/**
 * Everything this plugin needs from DHIS2, in the plugin's own vocabulary.
 *
 * The seam the whole design rests on: the implementation is the only file that touches `D2`, so
 * everything above it is unit-testable against a fake. When a scenario you want to automate keeps
 * needing a device, the fix is almost always to push the SDK call down here and return plain data.
 *
 * Note what is *not* here: the period. The host already names it in
 * `DataSetInstanceSlotArguments.periodId`, and the SDK has no display name for a period to fetch —
 * so asking DHIS2 for it would be a round trip that answers nothing. Sections are the only read.
 *
 * **Every function returns `Result`, and none of them throws.** An exception escaping into the host
 * composition takes the enclosing screen with it, and Compose cannot express an error boundary
 * around a composable call. `tools/check-rules.py` enforces this over every interface named in
 * `plugin.json`'s `conventions.repositoryInterfaces`.
 */
interface PluginRepository {

    /**
     * The sections of one data set, in whatever order the database returns them.
     *
     * Ordering is the caller's job, not this one's: the sort is a rule about what the user should
     * see (`sortOrder` ascending, unordered sections last) rather than a property of the query, and
     * keeping it above this line is what lets it be asserted without a device.
     */
    suspend fun loadSections(dataSetUid: String): Result<List<DataSetSection>>
}
