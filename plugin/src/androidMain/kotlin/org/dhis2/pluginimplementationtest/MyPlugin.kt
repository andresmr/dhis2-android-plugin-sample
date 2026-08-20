package org.dhis2.pluginimplementationtest

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dhis2.mobile.plugin.sdk.Dhis2Plugin
import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.hisp.dhis.android.core.arch.repositories.scope.RepositoryScope
import org.hisp.dhis.android.core.event.EventCreateProjection
import org.hisp.dhis.android.core.maintenance.D2Error
import org.hisp.dhis.android.core.maintenance.D2ErrorCode
import org.hisp.dhis.android.core.organisationunit.OrganisationUnitMode
import org.hisp.dhis.android.core.scopedaccess.ScopedD2
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance

private const val CHILD_PROGRAMME_UID = "IpHINAT79UW"

/**
 * A program the grant is not expected to include, used only as a probe target.
 *
 * Hardcoded rather than discovered: the point is to ask for something outside the grant, and
 * anything the plugin can *see* is by definition inside it.
 */
private const val UNGRANTED_PROGRAMME_UID = "ur1Edk5Oe2n"

/**
 * A plugin that summarises one program and offers one write, using the scoped DHIS2 SDK.
 *
 * Lives in `androidMain` because [Dhis2PluginContext.sdk] is a [ScopedD2], which is the DHIS2
 * *Android* SDK. Everything it renders lives in `commonMain` ([ProgramSummaryCard]) and takes plain
 * data, which is what keeps the UI previewable without a context.
 *
 * What the SDK buys over the DTO API this replaces:
 *
 *  - the program's real display name, instead of a hardcoded string beside a raw UID;
 *  - a `COUNT(*)` in SQL instead of fetching every row to call `.size` on it;
 *  - the newest three rows ordered and limited by the database, instead of `.take(3)` after
 *    loading everything;
 *  - attribute values labelled with their attribute names, instead of a map keyed by UID.
 *
 * None of this widens what the plugin can reach. The repositories arrive already filtered to the
 * server-granted scope, and because SDK filters only ever accumulate, the extra `by*()` calls below
 * can only narrow further.
 *
 * The write button exercises the *other* enforcement mechanism. Reads are enforced by append-only
 * filters, so an out-of-scope read comes back empty and silent; writes are enforced by a guard that
 * inspects the object being written, so an out-of-scope write throws `SCOPE_VIOLATION`. Only
 * [addEvent] can demonstrate the second one.
 */
class MyPlugin : Dhis2Plugin {
    override fun provideKoinModule() = null

    @Composable
    override fun content(context: Dhis2PluginContext) {
        // Bumped after a permitted write so the counts reload and the new event appears — proof it
        // actually landed in the database rather than merely passing the guard.
        var reloads by remember { mutableIntStateOf(0) }
        var writeState by remember { mutableStateOf<WriteState>(WriteState.Idle) }
        var searchState by remember { mutableStateOf<SearchState>(SearchState.Idle) }
        val coroutineScope = rememberCoroutineScope()

        val state by produceState<SummaryState>(SummaryState.Loading, context, reloads) {
            value = withContext(Dispatchers.IO) {
                runCatching { loadSummary(context.sdk) }
                    .fold(
                        onSuccess = { SummaryState.Loaded(it) },
                        onFailure = { error -> SummaryState.Failed(error.describe()) },
                    )
            }
        }

        ProgramSummaryCard(
            state = state,
            pluginVersion = context.pluginMetadata.version,
            writeState = writeState,
            onAddEvent = { target ->
                coroutineScope.launch {
                    writeState = WriteState.Writing
                    val outcome = withContext(Dispatchers.IO) { addEvent(context.sdk, target) }
                    writeState = outcome
                    if (outcome is WriteState.Succeeded) reloads++
                }
            },
            searchState = searchState,
            onProbeSearch = {
                coroutineScope.launch {
                    searchState = SearchState.Running
                    searchState = withContext(Dispatchers.IO) { probeSearch(context.sdk) }
                }
            },
        )
    }
}

/**
 * Reads the summary through [sdk].
 *
 * Blocking SDK calls, so callers must be off the main thread — `content` wraps this in
 * `Dispatchers.IO`.
 *
 * If the server did not grant this program, `programs().uid(…)` returns null and the count is zero:
 * an out-of-scope query yields nothing rather than throwing, because the grant and this filter are
 * AND-ed together.
 */
private fun loadSummary(sdk: ScopedD2): ProgramSummary {
    val program = sdk.programs().uid(CHILD_PROGRAMME_UID).blockingGet()

    val enrolled = sdk.trackedEntityInstances()
        .byProgramUids(listOf(CHILD_PROGRAMME_UID))

    val recent = enrolled
        .withTrackedEntityAttributeValues()
        .orderByCreated(RepositoryScope.OrderByDirection.DESC)
        .blockingGet()
        .take(LISTED_LIMIT)

    val attributeNames = attributeLabels(sdk)

    return ProgramSummary(
        programName = program?.displayName() ?: CHILD_PROGRAMME_UID,
        programUid = CHILD_PROGRAMME_UID,
        enrolledCount = enrolled.blockingCount(),
        recent = recent.map { it.toPerson(attributeNames) },
        eventCount = optional { eventCount(sdk) },
        writeTarget = optional { writeTarget(sdk) },
    )
}

/**
 * Runs a read whose capability may not have been granted, treating refusal as "unknown".
 *
 * `events()` and `enrollments()` throw `SCOPE_VIOLATION` when their capability is missing, and the
 * summary must not become an error page just because the write test is unavailable — the read tests
 * have to stay observable under a read-only grant. Only a scope violation is swallowed; any other
 * failure still propagates to [SummaryState.Failed].
 */
private inline fun <T> optional(read: () -> T): T? =
    try {
        read()
    } catch (error: D2Error) {
        if (error.errorCode() == D2ErrorCode.SCOPE_VIOLATION) null else throw error
    }

/**
 * A message worth showing a human.
 *
 * `D2Error` is `data class D2Error(…) : Exception()` — it never passes anything to the `Exception`
 * constructor, so `Throwable.message` is always **null** and the whole reason for the failure lives
 * in `errorCode()`/`errorDescription()` instead. Reading `message` here printed a bare "D2Error",
 * which is worse than useless for a scope violation: the entire diagnostic is in the description.
 */
private fun Throwable.describe(): String =
    when (this) {
        is D2Error -> "[${errorCode()}] ${errorDescription()}"
        else -> message ?: this::class.simpleName ?: "unknown error"
    }

/** Events of this program the grant lets the plugin see. Needs `READ_EVENT`. */
private fun eventCount(sdk: ScopedD2): Int =
    sdk.events()
        .byProgramUid().eq(CHILD_PROGRAMME_UID)
        .blockingCount()

/**
 * Picks something to write to: the newest enrollment in the granted program, and the program's
 * first stage.
 *
 * Deliberately resolved from *readable* data. The guard then checks the resulting event against the
 * `writable` grant, so a target found here can still be refused — which is the interesting case, and
 * the one showing read and write are separate grants rather than one.
 *
 * Needs `READ_ENROLLMENT`; the stage lookup needs `READ_METADATA`.
 */
private fun writeTarget(sdk: ScopedD2): WriteTarget? {
    val enrollment = sdk.enrollments()
        .byProgram().eq(CHILD_PROGRAMME_UID)
        .orderByCreated(RepositoryScope.OrderByDirection.DESC)
        .blockingGet()
        .firstOrNull() ?: return null

    val stage = sdk.programStages()
        .byProgramUid().eq(CHILD_PROGRAMME_UID)
        .orderBySortOrder(RepositoryScope.OrderByDirection.ASC)
        .blockingGet()
        .firstOrNull() ?: return null

    return WriteTarget(
        enrollmentUid = enrollment.uid(),
        programStageUid = stage.uid(),
        orgUnitUid = enrollment.organisationUnit() ?: return null,
    )
}

/**
 * Creates one event, and reports which of the three outcomes happened.
 *
 * The projection carries its own program and organisation unit, which is exactly why filtered reads
 * are not enough: nothing about the query that found [target] constrains what this object claims.
 * `blockingAdd` transforms the projection and hands the resulting `Event` to the guard before any
 * store call, so a refusal arrives as `SCOPE_VIOLATION` rather than as a write that half happened.
 */
private fun addEvent(sdk: ScopedD2, target: WriteTarget): WriteState =
    try {
        val uid = sdk.events().blockingAdd(
            EventCreateProjection.create(
                target.enrollmentUid,
                CHILD_PROGRAMME_UID,
                target.programStageUid,
                target.orgUnitUid,
                null,
            ),
        )
        WriteState.Succeeded(uid)
    } catch (error: D2Error) {
        if (error.errorCode() == D2ErrorCode.SCOPE_VIOLATION) {
            WriteState.Refused(error.errorDescription())
        } else {
            WriteState.Failed("${error.errorCode()}: ${error.errorDescription()}")
        }
    }

/**
 * Runs the tracker-search probes.
 *
 * Each probe deliberately asks for more than the grant allows, because tracker search is the one
 * accessor where asking is not obviously futile: its scope fields are single-valued and `by*()`
 * *replaces* them instead of appending, so overwriting `program` or `orgUnitMode` would widen the
 * query if the SDK did not re-apply the grant on every repository the fluent API produces.
 *
 * Compared against a baseline rather than absolute numbers, so the probes mean the same thing on any
 * database: the claim under test is "asking for more did not return more", not "N results".
 */
private fun probeSearch(sdk: ScopedD2): SearchState =
    try {
        val baseline = sdk.trackedEntitySearch()
            .byProgram().eq(CHILD_PROGRAMME_UID)
            .blockingCount()

        SearchState.Done(
            baseline = baseline,
            probes = listOf(
                SearchProbe(
                    label = "Granted program",
                    mechanism = "ordinary in-scope search — the number the rest compare against",
                    count = baseline,
                    expectation = SearchProbe.Expectation.INFORMATIONAL,
                ),
                SearchProbe(
                    label = "Ungranted program",
                    mechanism = "applyGrant() rewrites an ungranted program to __scope_denied__",
                    count = sdk.trackedEntitySearch()
                        .byProgram().eq(UNGRANTED_PROGRAMME_UID)
                        .blockingCount(),
                    expectation = SearchProbe.Expectation.EMPTY,
                ),
                SearchProbe(
                    label = "No program filter",
                    mechanism = "appendGrantWhere() bounds an unfiltered search by a sub-select on granted programs",
                    count = sdk.trackedEntitySearch().blockingCount(),
                    expectation = SearchProbe.Expectation.INFORMATIONAL,
                ),
                SearchProbe(
                    label = "orgUnitMode = ACCESSIBLE",
                    mechanism = "grant forces SELECTED over its own pre-expanded unit set",
                    count = sdk.trackedEntitySearch()
                        .byProgram().eq(CHILD_PROGRAMME_UID)
                        .byOrgUnitMode().eq(OrganisationUnitMode.ACCESSIBLE)
                        .blockingCount(),
                    expectation = SearchProbe.Expectation.SAME_AS_BASELINE,
                ),
                SearchProbe(
                    label = "onlineOnly()",
                    mechanism = "grant forces OFFLINE_ONLY — a server search answers where no filter applies",
                    count = sdk.trackedEntitySearch()
                        .byProgram().eq(CHILD_PROGRAMME_UID)
                        .onlineOnly()
                        .blockingCount(),
                    expectation = SearchProbe.Expectation.SAME_AS_BASELINE,
                ),
            ),
        )
    } catch (error: D2Error) {
        SearchState.Unavailable(error.describe())
    }

/** Attribute UID to the label a person should see, so values are not rendered under raw UIDs. */
private fun attributeLabels(sdk: ScopedD2): Map<String, String> =
    sdk.trackedEntityAttributes()
        .blockingGet()
        .associate { attribute ->
            attribute.uid() to (attribute.displayFormName() ?: attribute.displayName() ?: attribute.uid())
        }

private fun TrackedEntityInstance.toPerson(attributeNames: Map<String, String>) = EnrolledPerson(
    uid = uid(),
    attributes = trackedEntityAttributeValues()
        .orEmpty()
        .mapNotNull { value ->
            val attributeUid = value.trackedEntityAttribute()
            LabelledValue(
                label = attributeNames[attributeUid] ?: attributeUid,
                value = value.value() ?: return@mapNotNull null,
            )
        },
)
