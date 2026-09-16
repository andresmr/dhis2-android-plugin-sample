package org.dhis2.mobile.plugin.template.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.mobile.plugin.template.model.PluginSummary
import org.dhis2.mobile.plugin.template.repository.PluginRepository
import org.hisp.dhis.android.core.D2
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
 * `blockingCount()` is a `COUNT(*)` in SQL; `blockingGet()` materialises rows. Count what you
 * report, materialise only what you show — a card reporting 4 000 enrolments should not load 4 000
 * objects to do it.
 */
class D2PluginRepository(
    private val d2: D2,
) : PluginRepository {

    override suspend fun loadSummary(): Result<PluginSummary> = io {
        PluginSummary(
            programCount = d2.programModule().programs().blockingCount(),
        )
    }

    /** Off the main thread, and never throwing. Both halves matter; neither is optional. */
    private suspend fun <T> io(block: () -> T): Result<T> =
        withContext(Dispatchers.IO) { catchingD2(block) }
}

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
