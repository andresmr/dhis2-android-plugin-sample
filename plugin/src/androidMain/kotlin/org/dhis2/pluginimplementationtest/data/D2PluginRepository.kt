package org.dhis2.pluginimplementationtest.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.pluginimplementationtest.model.EnrolledPerson
import org.dhis2.pluginimplementationtest.model.LabelledValue
import org.dhis2.pluginimplementationtest.model.ProgramSummary
import org.dhis2.pluginimplementationtest.model.WriteTarget
import org.dhis2.pluginimplementationtest.repository.PluginRepository
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.arch.repositories.scope.RepositoryScope
import org.hisp.dhis.android.core.event.EventCreateProjection
import org.hisp.dhis.android.core.maintenance.D2Error
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance

/** How many enrolled people the summary lists before collapsing the rest. */
private const val LISTED_LIMIT = 3

/**
 * The only class in this plugin that touches the DHIS2 SDK.
 *
 * Everything above it works on plain models, which is what keeps the ViewModel and UI in
 * `commonMain` and unit-testable.
 *
 * Two responsibilities beyond querying: move blocking SDK calls off the main thread, and turn
 * `D2Error` into a message worth showing, since `D2Error` passes nothing to the `Exception`
 * constructor and its `message` is always null.
 */
class D2PluginRepository(
    private val d2: D2,
) : PluginRepository {

    override suspend fun loadSummary(programUid: String): Result<ProgramSummary> = io {
        val program = d2.programModule().programs().uid(programUid).blockingGet()

        val enrolled = d2.trackedEntityModule().trackedEntityInstances()
            .byProgramUids(listOf(programUid))

        val recent = enrolled
            .withTrackedEntityAttributeValues()
            .orderByCreated(RepositoryScope.OrderByDirection.DESC)
            .blockingGet()
            .take(LISTED_LIMIT)

        val attributeNames = attributeLabels()

        ProgramSummary(
            programUid = programUid,
            programName = program?.displayName() ?: programUid,
            // A COUNT(*) in SQL rather than fetching every row to call .size on it.
            enrolledCount = enrolled.blockingCount(),
            eventCount = d2.eventModule().events().byProgramUid().eq(programUid).blockingCount(),
            recent = recent.map { it.toPerson(attributeNames) },
            writeTarget = writeTarget(programUid),
        )
    }

    override suspend fun addEvent(target: WriteTarget): Result<String> = io {
        d2.eventModule().events().blockingAdd(
            EventCreateProjection.create(
                target.enrollmentUid,
                target.programUid,
                target.programStageUid,
                target.orgUnitUid,
                null,
            ),
        )
    }

    /** Picks something to write to: the newest enrollment in the program, and its first stage. */
    private fun writeTarget(programUid: String): WriteTarget? {
        val enrollment = d2.enrollmentModule().enrollments()
            .byProgram().eq(programUid)
            .orderByCreated(RepositoryScope.OrderByDirection.DESC)
            .blockingGet()
            .firstOrNull() ?: return null

        val stage = d2.programModule().programStages()
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
        d2.trackedEntityModule().trackedEntityAttributes()
            .blockingGet()
            .associate { attribute ->
                attribute.uid() to (attribute.displayFormName() ?: attribute.displayName() ?: attribute.uid())
            }

    private suspend fun <T> io(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (error: D2Error) {
            // D2Error's `message` is always null — the diagnostic is in the code and description.
            Result.failure(IllegalStateException("[${error.errorCode()}] ${error.errorDescription()}"))
        } catch (error: Throwable) {
            // Rule 4 says a repository never throws, and catching only D2Error did not deliver that.
            // Anything else — an SDK internal, an unexpected null while mapping a result — would
            // propagate out of the ViewModel's launch and take the host's whole screen down, because
            // Compose has no error boundary around a composable call.
            Result.failure(error)
        }
    }
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
