package org.dhis2.mobile.plugin.sample.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.mobile.plugin.sdk.TrackedEntityLabeller
import org.dhis2.mobile.plugin.sdk.trackedEntityLabeller
import org.dhis2.mobile.plugin.sample.model.EnrolledPerson
import org.dhis2.mobile.plugin.sample.model.MAX_LISTED_PEOPLE
import org.dhis2.mobile.plugin.sample.model.ProgramSummary
import org.dhis2.mobile.plugin.sample.repository.PluginRepository
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.arch.repositories.scope.RepositoryScope
import org.hisp.dhis.android.core.maintenance.D2Error
import org.hisp.dhis.android.core.program.Program
import org.hisp.dhis.android.core.program.ProgramType
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance

/**
 * The only class in this plugin that touches the DHIS2 SDK.
 *
 * Everything above it works on plain models, which is what keeps the ViewModel and UI in
 * `commonMain` and unit-testable.
 *
 * Two responsibilities beyond querying: move blocking SDK calls off the main thread, and turn
 * `D2Error` into a message worth showing, since `D2Error` passes nothing to the `Exception`
 * constructor and its `message` is always null.
 *
 * Reads only. The plugin API hands over `D2` unrestricted, so a plugin can write exactly as the app
 * can — `eventModule().events().blockingAdd(…)` and the rest — and this sample once carried a button
 * proving it. Nothing was learned from keeping the proof around: it is the same SDK either way. See
 * the git history if you want the shape of it.
 */
class D2PluginRepository(
    private val d2: D2,
) : PluginRepository {

    override suspend fun loadSummary(): Result<ProgramSummary> = io {
        val program = trackerProgram()
            ?: error("This server has no tracker programme for the plugin to report on.")
        val programUid = program.uid()

        val enrolled = d2.trackedEntityModule().trackedEntityInstances()
            .byProgramUids(listOf(programUid))

        // Two steps on purpose. Step one orders and caps *bare* rows — no `.with…()` — because the
        // children appenders are what cost: each row resolved with its attribute values drags in an
        // enrollment and an org unit too, so enriching everything to show three reads hundreds of
        // records. The SDK has no synchronous row limit, so all rows are still materialised; what
        // this buys is that only three are enriched, which is where the cost actually was.
        val recentUids = enrolled
            .orderByCreated(RepositoryScope.OrderByDirection.DESC)
            .blockingGet()
            .take(MAX_LISTED_PEOPLE)
            .map { it.uid() }

        // Step two enriches exactly those. The ordering has to be re-applied: a uid filter carries
        // none, and database order is precisely the "no particular order" this change is about.
        val recent = if (recentUids.isEmpty()) {
            emptyList()
        } else {
            d2.trackedEntityModule().trackedEntityInstances()
                .byUid().`in`(recentUids)
                .withTrackedEntityAttributeValues()
                .orderByCreated(RepositoryScope.OrderByDirection.DESC)
                .blockingGet()
        }

        // Resolved once for the programme, then applied to each row — see plugin-sdk. Doing this
        // by hand is how the card came to read "Gender: Female / First name: Filona".
        val labeller = d2.trackedEntityLabeller(programUid)

        ProgramSummary(
            programUid = programUid,
            programName = program.displayName() ?: programUid,
            // A COUNT(*) in SQL rather than fetching every row to call .size on it.
            enrolledCount = enrolled.blockingCount(),
            eventCount = d2.eventModule().events().byProgramUid().eq(programUid).blockingCount(),
            recent = recent.map { it.toPerson(labeller) },
        )
    }


    /**
     * The programme this plugin reports on, resolved rather than hardcoded.
     *
     * A UID in the source would be a constant pretending to be configuration: the dataStore config
     * has no programme field, so there is nothing for it to agree with, and it would make the sample
     * work on exactly one server — the DHIS2 demo database. Taking the first tracker programme by
     * name means the plugin runs anywhere, and a template nobody has to edit to try is the point.
     *
     * A real plugin with a programme in mind should filter here — `byUid()`, or a code its
     * administrator agrees on — rather than reintroduce a literal further up.
     */
    private fun trackerProgram(): Program? =
        d2.programModule().programs()
            .byProgramType().eq(ProgramType.WITH_REGISTRATION)
            .orderByDisplayName(RepositoryScope.OrderByDirection.ASC)
            .blockingGet()
            .firstOrNull()


    private suspend fun <T> io(block: () -> T): Result<T> =
        withContext(Dispatchers.IO) { catchingD2(block) }
}

/**
 * Runs [block], turning any failure into a [Result] rather than letting it escape.
 *
 * `D2Error.message` is always null, so its diagnostic has to be read from the code and description.
 * Everything else is caught too: an exception reaching the ViewModel's launch would take the host's
 * whole screen down, and Compose cannot express an error boundary around a composable call.
 */
internal fun <T> catchingD2(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: D2Error) {
        Result.failure(IllegalStateException("[${error.errorCode()}] ${error.errorDescription()}"))
    } catch (error: Throwable) {
        Result.failure(error)
    }

/**
 * The programme's own name for this person, and nothing else the card does not show.
 *
 * The labelling rule lives in `plugin-sdk` rather than here: a tracked entity's attribute values
 * come back in no order, so the programme's `displayInList` configuration is what decides which
 * ones make a name and in what sequence. Every plugin rendering a tracked entity needs that, so
 * none of them should have to re-derive it.
 */
internal fun TrackedEntityInstance.toPerson(labeller: TrackedEntityLabeller) = EnrolledPerson(
    uid = uid(),
    displayLabel = labeller.labelFor(this),
)
