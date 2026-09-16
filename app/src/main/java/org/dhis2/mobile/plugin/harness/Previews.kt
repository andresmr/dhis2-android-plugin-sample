package org.dhis2.mobile.plugin.harness

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.dhis2.mobile.plugin.harness.ui.theme.HarnessTheme
import org.dhis2.mobile.plugin.sample.model.EnrolledPerson
import org.dhis2.mobile.plugin.sample.model.ProgramSummary
import org.dhis2.mobile.plugin.sample.ui.PluginCard
import org.dhis2.mobile.plugin.sample.ui.PluginUiState
import org.dhis2.mobile.plugin.sample.ui.SummaryState

/**
 * `@Preview`s of the plugin's card, against sample state.
 *
 * The faster loop for pure UI work: a preview needs no server, no login and no sync, which is what
 * architecture rule 2 buys — composables take plain data and callbacks, never a
 * `Dhis2PluginContext`. For the plugin against real data, use the harness itself
 * (`./gradlew :app:installDebug`).
 *
 * **The only file in `:app` that names the plugin's own types.** Everything else here loads the
 * entry point by name (see `MainActivity`), so this is the whole surface `./init.sh` has to rewrite
 * in this module — a few import lines, at a path that never moves.
 */

private const val PLUGIN_VERSION = "preview"

/**
 * For the previews, which cannot log in. The harness itself uses real data.
 *
 * The uids are deliberately unmistakable rather than real DHIS2 demo uids. A fixture carrying
 * `IpHINAT79UW` reads as though the value matters, and that is how a hardcoded uid gets copied out
 * of a preview into something that ships.
 */
private val SAMPLE = ProgramSummary(
    programUid = "sample-programme",
    programName = "Child Programme",
    enrolledCount = 27,
    eventCount = 41,
    recent = listOf(
        EnrolledPerson(uid = "sample-person-1", displayLabel = "Alice Morgan"),
        EnrolledPerson(uid = "sample-person-2", displayLabel = "Bilal Khan"),
    ),
)

private val LOADED = PluginUiState(summary = SummaryState.Loaded(SAMPLE))

@Preview(showBackground = true, name = "Loaded")
@Composable
fun LoadedPreview() {
    HarnessTheme { PluginCard(state = LOADED, pluginVersion = PLUGIN_VERSION) }
}

@Preview(showBackground = true, name = "Loading")
@Composable
fun LoadingPreview() {
    HarnessTheme { PluginCard(state = PluginUiState(), pluginVersion = PLUGIN_VERSION) }
}

@Preview(showBackground = true, name = "Read failed")
@Composable
fun FailedPreview() {
    HarnessTheme {
        PluginCard(
            state = PluginUiState(summary = SummaryState.Failed("[UNEXPECTED] no such program")),
            pluginVersion = PLUGIN_VERSION,
        )
    }
}
