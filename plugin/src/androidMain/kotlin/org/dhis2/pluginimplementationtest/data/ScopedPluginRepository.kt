package org.dhis2.pluginimplementationtest.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.mobile.plugin.sdk.OrgUnitGrant
import org.dhis2.mobile.plugin.sdk.PluginMetadata
import org.dhis2.mobile.plugin.sdk.UidGrant
import org.dhis2.pluginimplementationtest.model.DataSetSummary
import org.dhis2.pluginimplementationtest.model.DataValueTarget
import org.dhis2.pluginimplementationtest.model.DeclaredGrant
import org.dhis2.pluginimplementationtest.model.EnrolledPerson
import org.dhis2.pluginimplementationtest.model.LabelledValue
import org.dhis2.pluginimplementationtest.model.MetadataItem
import org.dhis2.pluginimplementationtest.model.ProgramSummary
import org.dhis2.pluginimplementationtest.model.ScopeDimension
import org.dhis2.pluginimplementationtest.model.ScopeSnapshot
import org.dhis2.pluginimplementationtest.model.SearchProbe
import org.dhis2.pluginimplementationtest.model.SearchProbeRun
import org.dhis2.pluginimplementationtest.model.verdict
import org.dhis2.pluginimplementationtest.model.WriteTarget
import org.dhis2.pluginimplementationtest.repository.PluginRepository
import org.dhis2.pluginimplementationtest.repository.ScopeViolation
import org.hisp.dhis.android.core.arch.repositories.scope.RepositoryScope
import org.hisp.dhis.android.core.event.EventCreateProjection
import org.hisp.dhis.android.core.maintenance.D2Error
import org.hisp.dhis.android.core.maintenance.D2ErrorCode
import org.hisp.dhis.android.core.organisationunit.OrganisationUnitMode
import org.hisp.dhis.android.core.scopedaccess.ScopedD2
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance

/**
 * A program the grant is not expected to include, used only as a probe target.
 *
 * Hardcoded rather than discovered: the point is to ask for something outside the grant, and
 * anything the plugin can *see* is by definition inside it.
 */
private const val UNGRANTED_PROGRAMME_UID = "ur1Edk5Oe2n"

/** How many enrolled people the summary lists before collapsing the rest into "and N more". */
private const val LISTED_LIMIT = 3

/**
 * The only class in this plugin that touches the DHIS2 SDK.
 *
 * Everything above it works on plain models, which is what keeps the ViewModel and UI in
 * `commonMain` and unit-testable — `ScopedD2` has an internal constructor and cannot be faked.
 *
 * Two responsibilities beyond querying: move blocking SDK calls off the main thread, and translate
 * `D2Error` into domain outcomes so nothing above has to know the SDK's error model.
 */
class ScopedPluginRepository(
    private val sdk: ScopedD2,
    private val metadata: PluginMetadata,
) : PluginRepository {

    /**
     * The grant the server declared, beside what the SDK actually returns under it.
     *
     * Each dimension is read through [optional] so a withheld capability shows up as "declared N,
     * visible none" rather than failing the whole card. That contrast is the diagnostic: an
     * out-of-scope read is silently empty, so without it a wrong UID looks exactly like an empty
     * database.
     */
    override suspend fun loadScope(): Result<ScopeSnapshot> = io {
        val scope = metadata.effectiveScope

        ScopeSnapshot(
            capabilities = scope.capabilities,
            dimensions = listOf(
                ScopeDimension(
                    label = ScopeSnapshot.PROGRAMS,
                    declared = scope.programs.toDeclared(),
                    visible = optional {
                        sdk.programs().blockingGet().map { MetadataItem(it.uid(), it.displayName().orEmpty()) }
                    }.orEmpty(),
                ),
                ScopeDimension(
                    label = "Data sets",
                    declared = scope.dataSets.toDeclared(),
                    visible = optional {
                        sdk.dataSets().blockingGet().map { MetadataItem(it.uid(), it.displayName().orEmpty()) }
                    }.orEmpty(),
                ),
                ScopeDimension(
                    label = "Org units",
                    declared = scope.orgUnits.toDeclared(),
                    visible = optional {
                        sdk.organisationUnits().blockingGet().map { MetadataItem(it.uid(), it.displayName().orEmpty()) }
                    }.orEmpty(),
                ),
                ScopeDimension(
                    label = "Tracked entity types",
                    declared = scope.trackedEntityTypes.toDeclared(),
                    visible = optional {
                        sdk.trackedEntityTypes().blockingGet().map { MetadataItem(it.uid(), it.displayName().orEmpty()) }
                    }.orEmpty(),
                ),
            ),
        )
    }

    override suspend fun loadSummary(programUid: String): Result<ProgramSummary> = io {
        val program = sdk.programs().uid(programUid).blockingGet()

        val enrolled = sdk.trackedEntityInstances().byProgramUids(listOf(programUid))

        val recent = enrolled
            .withTrackedEntityAttributeValues()
            .orderByCreated(RepositoryScope.OrderByDirection.DESC)
            .blockingGet()
            .take(LISTED_LIMIT)

        val attributeNames = attributeLabels()

        ProgramSummary(
            programName = program?.displayName() ?: programUid,
            programUid = programUid,
            enrolledCount = enrolled.blockingCount(),
            recent = recent.map { it.toPerson(attributeNames) },
            eventCount = optional { eventCount(programUid) },
            writeTarget = optional { writeTarget(programUid) },
        )
    }

    /**
     * Creates one event.
     *
     * The projection carries its own program and organisation unit, which is exactly why filtered
     * reads are not enough: nothing about the query that found [target] constrains what this object
     * claims. The SDK checks the transformed event against the *writable* grant before any store
     * call, so a refusal arrives whole rather than as a half-completed write.
     */
    override suspend fun addEvent(target: WriteTarget): Result<String> = io {
        sdk.events().blockingAdd(
            EventCreateProjection.create(
                target.enrollmentUid,
                target.programUid,
                target.programStageUid,
                target.orgUnitUid,
                null,
            ),
        )
    }

    /**
     * Runs the search probes, each isolated from the others.
     *
     * Only failure to obtain the repository at all fails the whole run — that genuinely means the
     * capability was withheld. A single probe throwing is recorded on that probe, because one `try`
     * around everything once made an SDK bug that broke *all* scoped searches look identical to a
     * missing capability.
     */
    override suspend fun probeSearch(programUid: String): Result<SearchProbeRun> = io {
        sdk.trackedEntitySearch()

        val baseline = probe(
            label = "Granted program",
            mechanism = "ordinary in-scope search — the number the rest compare against",
            expectation = SearchProbe.Expectation.INFORMATIONAL,
        ) {
            sdk.trackedEntitySearch().byProgram().eq(programUid).blockingCount()
        }

        SearchProbeRun(
            baseline = baseline.count,
            probes = listOf(
                baseline,
                probe(
                    label = "Ungranted program",
                    mechanism = "applyGrant() rewrites an ungranted program to __scope_denied__",
                    expectation = SearchProbe.Expectation.EMPTY,
                ) {
                    sdk.trackedEntitySearch().byProgram().eq(UNGRANTED_PROGRAMME_UID).blockingCount()
                },
                typeGrantProbe(),
                probe(
                    label = "orgUnitMode = ACCESSIBLE",
                    mechanism = "grant forces SELECTED over its own pre-expanded unit set",
                    expectation = SearchProbe.Expectation.SAME_AS_BASELINE,
                ) {
                    sdk.trackedEntitySearch()
                        .byProgram().eq(programUid)
                        .byOrgUnitMode().eq(OrganisationUnitMode.ACCESSIBLE)
                        .blockingCount()
                },
                probe(
                    label = "onlineOnly()",
                    mechanism = "grant forces OFFLINE_ONLY — a server search answers where no filter applies",
                    expectation = SearchProbe.Expectation.SAME_AS_BASELINE,
                ) {
                    sdk.trackedEntitySearch()
                        .byProgram().eq(programUid)
                        .onlineOnly()
                        .blockingCount()
                },
            ),
        )
    }

    /**
     * Checks that an unfiltered search respects the *type* half of the grant.
     *
     * This replaced a probe that merely counted rows, and counting is why a real leak went unnoticed:
     * a grant of one tracked entity type returned 32 records of another, and a row count cannot tell
     * the difference. Asserting on the types of the rows themselves can, and needs no knowledge of
     * which types were *not* granted — [TrackedEntitySearchItem][
     * org.hisp.dhis.android.core.trackedentity.search.TrackedEntitySearchItem] carries its own type.
     *
     * The count reported is the number of **violations**, so a healthy grant scores zero.
     */
    private fun typeGrantProbe(): SearchProbe {
        val granted = metadata.effectiveScope.trackedEntityTypes
        val label = "Unfiltered search respects the type grant"
        val mechanism = "every row's tracked entity type must be inside the grant"

        // Nothing to violate when every type is granted, so report rather than assert.
        if (granted.all) {
            return probe(label, "$mechanism (all types granted — nothing to assert)", SearchProbe.Expectation.INFORMATIONAL) {
                sdk.trackedEntitySearch().blockingGet().size
            }
        }

        val allowed = granted.uids.toSet()
        return probe(label, mechanism, SearchProbe.Expectation.EMPTY) {
            sdk.trackedEntitySearch().blockingGet().count { it.type.uid() !in allowed }
        }
    }

    /**
     * Reads one data set's summary — the aggregate counterpart to [loadSummary].
     *
     * Worth understanding why this is a different enforcement path. `DataValue` has no data set
     * column, so the grant cannot filter on the thing the administrator actually named. The SDK
     * resolves the granted data **sets** to the data **elements** they contain and filters on those,
     * and the write guard checks the same way. That indirection is why the aggregate half needs its
     * own test rather than being assumed to work because the tracker half does.
     */
    override suspend fun loadDataSetSummary(dataSetUid: String): Result<DataSetSummary> = io {
        val dataSet = sdk.dataSets().withDataSetElements().uid(dataSetUid).blockingGet()
        val elements = dataSet?.dataSetElements()?.mapNotNull { it.dataElement()?.uid() }.orEmpty()

        DataSetSummary(
            dataSetUid = dataSetUid,
            dataSetName = dataSet?.displayName() ?: dataSetUid,
            dataElementCount = elements.size,
            dataValueCount = optional { dataValueCount(elements) },
            writeTarget = optional { dataValueTarget(dataSetUid, elements) },
        )
    }

    /**
     * Overwrites one data value.
     *
     * `blockingSet` routes through `ReadWriteWithValueObjectRepositoryImpl`, which consults the same
     * guard the tracker writes use — `checkDataValue` verifies the **data element** and org unit of
     * the value, never the data set. So a data set that is readable but whose elements are not
     * writable is refused here even though the read above succeeded.
     */
    override suspend fun writeDataValue(target: DataValueTarget): Result<String> = io {
        // Something visibly different from what is there, so a permitted write is obvious on reload.
        val next = ((target.currentValue?.toIntOrNull() ?: 0) + 1).toString()
        sdk.dataValues()
            .value(
                target.period,
                target.orgUnitUid,
                target.dataElementUid,
                target.categoryOptionComboUid,
                target.attributeOptionComboUid,
                target.dataSetUid,
            )
            .blockingSet(next)
        next
    }

    /** Data values readable under the grant, for the elements of this data set. Needs `READ_DATA_VALUE`. */
    private fun dataValueCount(dataElementUids: List<String>): Int =
        if (dataElementUids.isEmpty()) {
            0
        } else {
            sdk.dataValues().byDataElementUid().`in`(dataElementUids).blockingCount()
        }

    /**
     * Picks a data value to overwrite: one that already exists inside the grant.
     *
     * A data value is identified by five fields, and inventing a combination risks failing for
     * reasons unrelated to scoping. Reusing real coordinates keeps the test about the guard. The
     * repository is already narrowed to readable elements and org units, so any row here is inside
     * the read grant — which is the interesting starting point, since the *write* grant may still
     * refuse it.
     */
    private fun dataValueTarget(dataSetUid: String, dataElementUids: List<String>): DataValueTarget? {
        if (dataElementUids.isEmpty()) return null

        val existing = sdk.dataValues()
            .byDataElementUid().`in`(dataElementUids)
            .blockingGet()
            .firstOrNull() ?: return null

        return DataValueTarget(
            dataSetUid = dataSetUid,
            dataElementUid = existing.dataElement(),
            period = existing.period(),
            orgUnitUid = existing.organisationUnit(),
            categoryOptionComboUid = existing.categoryOptionCombo(),
            attributeOptionComboUid = existing.attributeOptionCombo(),
            currentValue = existing.value(),
        )
    }

    /** Events of this program the grant lets the plugin see. Needs `READ_EVENT`. */
    private fun eventCount(programUid: String): Int =
        sdk.events().byProgramUid().eq(programUid).blockingCount()

    /**
     * Picks something to write to: the newest enrollment in the program, and its first stage.
     *
     * Deliberately resolved from *readable* data. The guard then checks the resulting event against
     * the `writable` grant, so a target found here can still be refused — which is the interesting
     * case, and the one showing read and write are separate grants intersected.
     */
    private fun writeTarget(programUid: String): WriteTarget? {
        val enrollment = sdk.enrollments()
            .byProgram().eq(programUid)
            .orderByCreated(RepositoryScope.OrderByDirection.DESC)
            .blockingGet()
            .firstOrNull() ?: return null

        val stage = sdk.programStages()
            .byProgramUid().eq(programUid)
            .orderBySortOrder(RepositoryScope.OrderByDirection.ASC)
            .blockingGet()
            .firstOrNull() ?: return null

        return WriteTarget(
            programUid = programUid,
            enrollmentUid = enrollment.uid(),
            programStageUid = stage.uid(),
            orgUnitUid = enrollment.organisationUnit() ?: return null,
        )
    }

    /** Attribute UID to the label a person should see, so nothing renders under a raw UID. */
    private fun attributeLabels(): Map<String, String> =
        sdk.trackedEntityAttributes()
            .blockingGet()
            .associate { attribute ->
                attribute.uid() to (attribute.displayFormName() ?: attribute.displayName() ?: attribute.uid())
            }

    /**
     * Runs blocking SDK work off the main thread and turns a refusal into [ScopeViolation].
     *
     * The translation is the whole reason this is not just `withContext`: `SCOPE_VIOLATION` is an
     * expected outcome the UI renders differently from a fault, and `D2Error` must not leak past the
     * repository or the layers above it stop being platform-free.
     */
    private suspend fun <T> io(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (error: D2Error) {
            Result.failure(
                if (error.errorCode() == D2ErrorCode.SCOPE_VIOLATION) {
                    ScopeViolation(error.errorDescription())
                } else {
                    error
                },
            )
        }
    }

    /**
     * Runs a read whose capability may not have been granted, treating refusal as "unknown".
     *
     * `events()` and `enrollments()` throw when their capability is missing, and the summary must
     * not become an error page because the write test is unavailable — the read tests have to stay
     * observable under a read-only grant.
     */
    private inline fun <T> optional(read: () -> T): T? =
        try {
            read()
        } catch (error: D2Error) {
            if (error.errorCode() == D2ErrorCode.SCOPE_VIOLATION) null else throw error
        }

    private fun probe(
        label: String,
        mechanism: String,
        expectation: SearchProbe.Expectation,
        query: () -> Int,
    ): SearchProbe =
        runCatching(query).fold(
            onSuccess = { SearchProbe(label, mechanism, it, expectation) },
            onFailure = { SearchProbe(label, mechanism, null, expectation, it.describe()) },
        )
}

/**
 * Maps the wire-format grant onto the plugin's own display model.
 *
 * `all` and an empty UID list are the two ends of the range, and an empty list means *none* — closed
 * by default, the same rule the SDK applies.
 */
private fun UidGrant.toDeclared(): DeclaredGrant = when {
    all -> DeclaredGrant.All
    uids.isEmpty() -> DeclaredGrant.None
    else -> DeclaredGrant.Uids(uids)
}

private fun OrgUnitGrant.toDeclared(): DeclaredGrant = when {
    all -> DeclaredGrant.All
    capture -> DeclaredGrant.Capture
    uids.isEmpty() -> DeclaredGrant.None
    // Roots, not objects: `mode` decides what they resolve to, and the root itself is frequently not
    // stored on the device.
    else -> DeclaredGrant.Roots(uids, mode)
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

/**
 * A message worth showing a human.
 *
 * `D2Error` is `data class D2Error(…) : Exception()` — it never passes anything to the `Exception`
 * constructor, so `Throwable.message` is always **null** and the whole reason for the failure lives
 * in `errorCode()`/`errorDescription()`.
 */
private fun Throwable.describe(): String = when (this) {
    is D2Error -> "[${errorCode()}] ${errorDescription()}"
    else -> message ?: this::class.simpleName ?: "unknown error"
}
