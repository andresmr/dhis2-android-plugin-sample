package org.dhis2.stock.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.stock.model.DataSetSection
import org.dhis2.stock.repository.PluginRepository
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.dataset.Section
import org.hisp.dhis.android.core.maintenance.D2Error

/**
 * The only class in this plugin that touches the DHIS2 SDK.
 *
 * Keeping it that way is what makes everything above it testable without a device, and it is
 * checked: `plugin.json`'s `conventions.sdkAllowed` lists the files allowed to mention
 * `org.hisp.dhis`, and `tools/check-rules.py` fails when another one does.
 *
 * Two jobs beyond querying. It moves blocking SDK calls off the main thread — `blockingGet()` and
 * friends will happily block the UI thread if you let them. And it translates failures into
 * something a human can read, because `D2Error`'s `message` is always null (see [catchingD2]).
 *
 * Note what it does *not* read: the period. The host names it in the slot arguments, and `Period`
 * in the SDK carries no display name anyway — only `periodId`, `periodType`, `startDate` and
 * `endDate`. Asking DHIS2 for it would be a round trip that answers nothing.
 */
class D2PluginRepository(
    private val d2: D2,
) : PluginRepository {

    /**
     * Every section of one data set, materialised — there are a handful per data set and all of
     * them are shown, so there is nothing here to count instead of load.
     *
     * Not enriched with `.withDataElements()`: nothing renders them yet, and each `with…()` is
     * another join. Add it when something needs it.
     */
    override suspend fun loadSections(dataSetUid: String): Result<List<DataSetSection>> = io {
        d2.dataSetModule().sections()
            .byDataSetUid().eq(dataSetUid)
            .blockingGet()
            .map { it.toPluginSection() }
    }

    /** Off the main thread, and never throwing. Both halves matter; neither is optional. */
    private suspend fun <T> io(block: () -> T): Result<T> =
        withContext(Dispatchers.IO) { catchingD2(block) }
}

/**
 * An SDK [Section] as the plugin's own model.
 *
 * A top-level function so `plugin/src/androidHostTest/` can exercise it on the JVM without a `D2`,
 * which is the whole reason the mapping does not live inside the repository class.
 *
 * `displayName` is nullable on every DHIS2 identifiable object, and so is `name`. A section with
 * neither is not a section anyone can pick, so it falls back to its UID rather than to a blank chip.
 */
internal fun Section.toPluginSection(): DataSetSection = DataSetSection(
    uid = uid,
    displayName = displayName ?: name ?: uid,
    sortOrder = sortOrder,
)

/**
 * Runs [block], turning any failure into a `Result` rather than letting it escape.
 *
 * Catches `Throwable`, not just `D2Error`. A repository that only catches the SDK's own error type
 * still lets an unexpected null while mapping a result reach the host — where it takes the whole
 * enclosing screen down, because Compose cannot express an error boundary around a composable call.
 *
 * `D2Error` is a `data class … : Exception()` that passes nothing to the `Exception` constructor,
 * so `Throwable.message` on one is **always null**. Reading `errorCode()` and `errorDescription()`
 * is the difference between a useful message and the bare word "D2Error" on screen.
 *
 * A top-level function so `plugin/src/androidHostTest/` can exercise it on the JVM without a `D2`.
 */
internal fun <T> catchingD2(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: D2Error) {
        Result.failure(IllegalStateException("[${error.errorCode()}] ${error.errorDescription()}"))
    } catch (error: Throwable) {
        Result.failure(error)
    }
