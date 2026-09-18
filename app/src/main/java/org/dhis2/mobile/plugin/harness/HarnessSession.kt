package org.dhis2.mobile.plugin.harness

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.mobile.plugin.sdk.SlotArguments
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.D2Configuration
import org.hisp.dhis.android.core.D2Manager
import org.hisp.dhis.android.core.maintenance.D2Error

sealed interface HarnessState {
    /** [missing] names the `local.properties` keys that are absent. */
    data class NotConfigured(val missing: List<String>) : HarnessState

    data class Working(val step: String) : HarnessState

    /**
     * [slotArguments] is what the host would tell the plugin about the occurrence it is rendering
     * — null at a slot that has nothing to say about what is on screen.
     */
    data class Ready(
        val d2: D2,
        val slot: SlotChoice.Chosen,
        val slotArguments: SlotArguments?,
    ) : HarnessState

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

        // Before the SDK, deliberately: this reads nothing but BuildConfig, so a plugin.json that
        // names no renderable slot fails in a second, not after a first-run metadata download.
        onStep(CHOOSING_SLOT)
        val slot = when (val choice = harnessSlotChoice()) {
            is SlotChoice.Unavailable ->
                return@withContext HarnessState.Failed(CHOOSING_SLOT, choice.reason)

            is SlotChoice.Chosen -> choice
        }

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
            // Organisation units, not programmes: every server has at least one, and a plugin
            // targeting a data set may be running against a server with no tracker programme at
            // all — which the old check read as "nothing downloaded yet", every single launch.
            if (d2.organisationUnitModule().organisationUnits().blockingCount() == 0) {
                d2.metadataModule().blockingDownload()
            }
        } ?: return@withContext failure

        val resolving = resolvingStepFor(slot)
        val resolution = step(resolving) { resolveSlotArguments(d2, slot) }
            ?: return@withContext failure
        if (resolution is SlotResolution.Unavailable) {
            return@withContext HarnessState.Failed(resolving, resolution.reason)
        }

        val arguments = (resolution as SlotResolution.Resolved).arguments

        // The one thing the host's registry does that the harness otherwise never would: check the
        // plugin would actually be selected here. With both sides derived from plugin.json this can
        // only fire if they have come apart, which makes it a regression guard rather than a path a
        // developer is meant to hit — but an unguarded `appliesTo` is a method nothing ever calls.
        val slotConfig = harnessPluginMetadata().slotConfig[slot.injectionPoint]
        if (arguments != null && !arguments.appliesTo(slotConfig)) {
            return@withContext HarnessState.Failed(
                resolving,
                "The plugin would not be selected for this instance on a device: its slotConfig " +
                    "does not cover it. plugin.json and what the harness resolved have come apart.",
            )
        }

        HarnessState.Ready(d2, slot, arguments)
    }

    /** The slot `plugin.json` names. */
    private fun harnessSlotChoice(): SlotChoice = chooseSlot(
        declared = harnessInjectionPoints(),
        dataSetUids = harnessDataSetUids(),
    )

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

/**
 * Step names, as constants rather than literals.
 *
 * `onStep` prints one and [HarnessState.Failed] carries another, and when they are two copies of
 * the same sentence they drift — the screen then names a step that never ran.
 */
private const val CHOOSING_SLOT = "Choosing a slot"

private fun resolvingStepFor(slot: SlotChoice.Chosen): String = when (slot.dataSetUid) {
    null -> "Preparing the ${slot.injectionPoint.name} slot"
    else -> "Resolving the data set instance for ${slot.dataSetUid}"
}
