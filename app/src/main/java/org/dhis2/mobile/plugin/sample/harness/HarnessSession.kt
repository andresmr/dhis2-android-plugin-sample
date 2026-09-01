package org.dhis2.mobile.plugin.sample.harness

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.mobile.plugin.sample.harness.BuildConfig
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.D2Configuration
import org.hisp.dhis.android.core.D2Manager
import org.hisp.dhis.android.core.program.ProgramType

/**
 * What the harness is doing, in the order it does it.
 *
 * Every step is on screen rather than behind a spinner: the first run downloads metadata and takes
 * minutes, and a harness that looks hung is one nobody trusts enough to leave running.
 */
sealed interface HarnessState {
    /** No credentials in `local.properties` — [missing] names the keys that are absent. */
    data class NotConfigured(val missing: List<String>) : HarnessState

    data class Working(val step: String) : HarnessState

    data class Ready(val d2: D2, val programUid: String) : HarnessState

    data class Failed(val step: String, val message: String) : HarnessState
}

/**
 * Brings up a real [D2] against a real server, so the plugin can be exercised with real data
 * instead of hand-written samples.
 *
 * This is the thing that was previously believed impossible. A *unit test* cannot construct a `D2`
 * — it needs an Android `Context`, a database and an HTTP stack — but an application module can,
 * and does so exactly the way the Capture App does: [D2Manager.blockingInstantiateD2].
 *
 * It does **not** make this app a substitute for the Capture App. See `CLAUDE.md` for what only the
 * real host can exercise.
 */
class HarnessSession(private val context: Context) {

    suspend fun start(): HarnessState = withContext(Dispatchers.IO) {
        val missing = missingCredentials()
        if (missing.isNotEmpty()) return@withContext HarnessState.NotConfigured(missing)

        val d2 = step("Starting the SDK") {
            // Instantiating twice in one process throws; a config change or a retry must reuse it.
            if (D2Manager.isD2Instantiated()) D2Manager.getD2() else D2Manager.blockingInstantiateD2(configuration())
        } ?: return@withContext failure

        step("Signing in to ${BuildConfig.DHIS2_SERVER_URL}") {
            // Already logged in is the normal case after the first run — the session is on disk.
            if (!d2.userModule().blockingIsLogged()) {
                d2.userModule().blockingLogIn(
                    BuildConfig.DHIS2_USERNAME,
                    BuildConfig.DHIS2_PASSWORD,
                    BuildConfig.DHIS2_SERVER_URL,
                )
            }
        } ?: return@withContext failure

        step("Downloading metadata (first run only, this takes a few minutes)") {
            // Guarded, not unconditional: this is the slow part, and it only has to happen once per
            // device. Programs are the cheapest proof that a download already landed.
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
            // Metadata is not data. The metadata download brings programmes, stages and attributes;
            // enrolments and events arrive only through the tracker downloader, and without this the
            // plugin renders real structure over zero rows — which looks like a plugin bug.
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
     * The programme the harness downloads data for: whatever `local.properties` names, or the first
     * tracker programme on the server.
     *
     * By type rather than by "has enrolments", which would be circular — nothing has enrolments
     * before the download this choice feeds. Falling back rather than hardcoding a UID matters: the
     * demo database's UIDs are not any real server's, and a harness that works against one server is
     * a hardcoded sample with extra steps.
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

    /** Set by [step] when something throws, so the caller can bail with the step that failed named. */
    private var failure: HarnessState.Failed = HarnessState.Failed("", "")

    /**
     * Runs one step, reporting the step's own name when it fails.
     *
     * "Login failed" without saying which server, or "download failed" without saying what was being
     * downloaded, costs more time than the step itself.
     */
    private fun <T> step(name: String, block: () -> T): T? =
        try {
            onStep(name)
            block()
        } catch (error: Throwable) {
            failure = HarnessState.Failed(name, error.message ?: error::class.simpleName ?: "unknown error")
            null
        }

    /** Progress callback, set by the caller before [start]. */
    var onStep: (String) -> Unit = {}
}
