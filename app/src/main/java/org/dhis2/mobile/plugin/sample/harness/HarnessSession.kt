package org.dhis2.mobile.plugin.sample.harness

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.D2Configuration
import org.hisp.dhis.android.core.D2Manager
import org.hisp.dhis.android.core.program.ProgramType

sealed interface HarnessState {
    /** [missing] names the `local.properties` keys that are absent. */
    data class NotConfigured(val missing: List<String>) : HarnessState

    data class Working(val step: String) : HarnessState

    data class Ready(val d2: D2, val programUid: String) : HarnessState

    data class Failed(val step: String, val message: String) : HarnessState
}

/**
 * Brings up a real [D2] against a real server so the plugin can be run with real data.
 *
 * Not a substitute for the Capture App — `CLAUDE.md` lists what only the real host can exercise.
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

        return d2.programModule().programs()
            .byProgramType().eq(ProgramType.WITH_REGISTRATION)
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
            failure = HarnessState.Failed(name, error.message ?: error::class.simpleName ?: "unknown error")
            null
        }
}
