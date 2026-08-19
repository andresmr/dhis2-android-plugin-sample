package org.dhis2.pluginimplementationtest

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.mobile.plugin.sdk.Dhis2Plugin
import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.hisp.dhis.android.core.arch.repositories.scope.RepositoryScope
import org.hisp.dhis.android.core.scopedaccess.ScopedD2
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance

private const val CHILD_PROGRAMME_UID = "IpHINAT79UW"

/**
 * A plugin that summarises one program, using the scoped DHIS2 SDK.
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
 */
class MyPlugin : Dhis2Plugin {
    override fun provideKoinModule() = null

    @Composable
    override fun content(context: Dhis2PluginContext) {
        val state by produceState<SummaryState>(SummaryState.Loading, context) {
            value = withContext(Dispatchers.IO) {
                runCatching { loadSummary(context.sdk) }
                    .fold(
                        onSuccess = { SummaryState.Loaded(it) },
                        onFailure = { error ->
                            SummaryState.Failed(error.message ?: error::class.simpleName ?: "error")
                        },
                    )
            }
        }

        ProgramSummaryCard(
            state = state,
            pluginVersion = context.pluginMetadata.version,
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
    )
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
