package org.dhis2.mobile.plugin.harness

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.D2Configuration
import org.hisp.dhis.android.core.D2Manager
import org.hisp.dhis.android.core.arch.repositories.scope.RepositoryScope
import org.hisp.dhis.android.core.maintenance.D2Error
import org.hisp.dhis.android.core.program.ProgramType

sealed interface HarnessState {
    /** [missing] names the `local.properties` keys that are absent. */
    data class NotConfigured(val missing: List<String>) : HarnessState

    data class Working(val step: String) : HarnessState

    /** [programUid] is null when the plugin does not need tracker data, so none was downloaded. */
    data class Ready(val d2: D2, val programUid: String?) : HarnessState

    data class Failed(val step: String, val message: String) : HarnessState
}

/**
 * Brings up a real [D2] against a real server so the plugin can be run with real data.
 *
 * Not a substitute for the Capture App — `AGENTS.md` lists what only the real host can exercise.
 */
class HarnessSession(private val context: Context) {

    /** Progress callback, set before [start]; the first run takes minutes. */
    var onStep: (String) -> Unit = {}

    private var failure: HarnessState.Failed = HarnessState.Failed("", "")

    suspend fun start(): HarnessState = withContext(Dispatchers.IO) {
        val missing = missingCredentials()
        if (missing.isNotEmpty()) return@withContext HarnessState.NotConfigured(missing)

        val d2 = step("Starting the SDK") {
            // Instantiating twice in one process throws, so a retry has to reuse the first one.
            if (D2Manager.isD2Instantiated()) {
                D2Manager.getD2()
            } else {
                D2Manager.blockingInstantiateD2(configuration())
            }
        } ?: return@withContext failure

        step("Signing in to ${BuildConfig.DHIS2_SERVER_URL}") {
            if (!d2.userModule().blockingIsLogged()) {
                d2.userModule().blockingLogIn(
                    BuildConfig.DHIS2_USERNAME,
                    BuildConfig.DHIS2_PASSWORD,
                    BuildConfig.DHIS2_SERVER_URL,
                )
            }
        } ?: return@withContext failure

        step("Downloading metadata (first run only, this takes a few minutes)") {
            if (d2.programModule().programs().blockingCount() == 0) {
                d2.metadataModule().blockingDownload()
            }
        } ?: return@withContext failure

        // Only for a plugin that reads rows. Downloading tracker data takes minutes on a first run,
        // and a plugin counting programmes gets nothing from it but the wait — plus a line on screen
        // about a programme it never looks at, which is worse than slow: it is misleading.
        // Declared per module in plugin.json, as `harness.trackerData`.
        if (!BuildConfig.HARNESS_TRACKER_DATA) return@withContext HarnessState.Ready(d2, null)

        val programUid = resolveProgramUid(d2)
            ?: return@withContext HarnessState.Failed(
                step = "Choosing a programme",
                message = "This server has no tracker programme. Set dhis2.programUid in " +
                    "local.properties to one this user can see.",
            )

        step("Downloading tracker data for $programUid") {
            // Metadata brings programmes and stages but no enrolments or events, so without this the
            // plugin renders real structure over zero rows — which reads as a plugin bug.
            if (d2.enrollmentModule().enrollments().byProgram().eq(programUid).blockingCount() == 0) {
                d2.trackedEntityModule().trackedEntityInstanceDownloader()
                    .byProgramUid(programUid)
                    .limitByProgram(true)
                    .blockingDownload()
            }
        } ?: return@withContext failure

        HarnessState.Ready(d2, programUid)
    }

    /**
     * Chosen by programme *type*, because "has enrolments" would be circular — nothing has any until
     * the download this choice feeds.
     */
    private fun resolveProgramUid(d2: D2): String? {
        val configured = BuildConfig.PLUGIN_PROGRAM_UID
        if (configured.isNotBlank()) return configured

        // Ordered the same way D2PluginRepository orders it, so leaving dhis2.programUid blank
        // downloads the very programme the plugin will resolve. Drop the ordering here and the two
        // can disagree on a server with several tracker programmes, for no reason a reader could see.
        return d2.programModule().programs()
            .byProgramType().eq(ProgramType.WITH_REGISTRATION)
            .orderByDisplayName(RepositoryScope.OrderByDirection.ASC)
            .blockingGet()
            .firstOrNull()
            ?.uid()
    }

    private fun missingCredentials(): List<String> = buildList {
        if (BuildConfig.DHIS2_SERVER_URL.isBlank()) add("dhis2.serverUrl")
        if (BuildConfig.DHIS2_USERNAME.isBlank()) add("dhis2.username")
        if (BuildConfig.DHIS2_PASSWORD.isBlank()) add("dhis2.password")
    }

    private fun configuration(): D2Configuration = D2Configuration(
        appName = "dhis2-android-plugin-sample harness",
        appVersion = "1.0.0",
        readTimeoutInSeconds = 30,
        connectTimeoutInSeconds = 30,
        writeTimeoutInSeconds = 30,
        interceptors = emptyList(),
        networkInterceptors = emptyList(),
        networkPlugins = emptyList(),
        context = context,
    )

    /** Names the failing step, so "download failed" says *what* was being downloaded. */
    private fun <T> step(name: String, block: () -> T): T? =
        try {
            onStep(name)
            block()
        } catch (error: Throwable) {
            failure = HarnessState.Failed(name, error.describe())
            null
        }
}

/**
 * A message worth showing a human.
 *
 * `D2Error` is a `data class … : Exception()` that passes nothing to the `Exception` constructor, so
 * `Throwable.message` on one is **always null** — falling back to the class name renders the bare
 * word "D2Error", which says nothing at all. That is architecture rule 6, and this file was breaking
 * it: a failed login showed "D2Error" and left you guessing between a wrong password, a wrong URL
 * and a server that was not running.
 *
 * The plugin's own repository already does this properly in `catchingD2`. The harness is the first
 * thing a developer meets, and it was the one place still getting it wrong.
 */
private fun Throwable.describe(): String = when (this) {
    is D2Error -> listOfNotNull(
        errorCode()?.let { "[$it]" },
        errorDescription(),
        httpErrorCode()?.let { "(HTTP $it)" },
    ).joinToString(" ").ifBlank { "D2Error with no description" }

    else -> message ?: this::class.simpleName ?: "unknown error"
}
