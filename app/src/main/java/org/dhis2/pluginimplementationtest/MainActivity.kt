package org.dhis2.pluginimplementationtest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.dhis2.pluginimplementationtest.ui.theme.PluginImplementationTestTheme

private const val PLUGIN_VERSION = "2.2.0"

/**
 * Sample data for the harness.
 *
 * This used to be a `StubDhis2PluginContext` that faked the plugin's data source. It cannot be any
 * more: `Dhis2PluginContext.sdk` is a `ScopedD2` with an internal constructor, so the only way to
 * produce one is `D2.scopedTo(…)` on a real, initialised SDK — which a preview app does not have.
 *
 * The workaround is also the better design. `ProgramSummaryCard` takes plain data and no context, so
 * the harness renders the plugin's actual UI with whatever data it likes. What is no longer covered
 * here is the *fetch* in `MyPlugin`, which now needs the Capture App (§8 of the plugin docs) or a
 * real D2 to exercise.
 */
private val SAMPLE = ProgramSummary(
    programName = "Child Programme",
    programUid = "IpHINAT79UW",
    enrolledCount = 27,
    recent = listOf(
        EnrolledPerson(
            uid = "qTgINZ9tOtV",
            attributes = listOf(
                LabelledValue("First name", "Alice"),
                LabelledValue("Last name", "Morgan"),
            ),
        ),
        EnrolledPerson(
            uid = "rJYd0Wn4p4f",
            attributes = listOf(
                LabelledValue("First name", "Bilal"),
                LabelledValue("Last name", "Khan"),
            ),
        ),
        EnrolledPerson(
            uid = "sKm5pxN2h7g",
            attributes = listOf(
                LabelledValue("First name", "Clara"),
                LabelledValue("Last name", "Santos"),
            ),
        ),
    ),
    eventCount = 41,
    writeTarget = WriteTarget(
        enrollmentUid = "TFEQXHXBiFO",
        programStageUid = "A03MvHHogjR",
        orgUnitUid = "DiszpKrYNg8",
    ),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PluginImplementationTestTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreenPreview(
                        modifier = Modifier.padding(innerPadding),
                        pluginContent = {
                            ProgramSummaryCard(
                                state = SummaryState.Loaded(SAMPLE),
                                pluginVersion = PLUGIN_VERSION,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreenPreview(
    modifier: Modifier = Modifier,
    pluginContent: @Composable () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        pluginContent()
    }
}

@Preview(showBackground = true, name = "Loaded")
@Composable
fun ProgramSummaryLoadedPreview() {
    PluginImplementationTestTheme {
        ProgramSummaryCard(state = SummaryState.Loaded(SAMPLE), pluginVersion = PLUGIN_VERSION)
    }
}

@Preview(showBackground = true, name = "Loading")
@Composable
fun ProgramSummaryLoadingPreview() {
    PluginImplementationTestTheme {
        ProgramSummaryCard(state = SummaryState.Loading, pluginVersion = PLUGIN_VERSION)
    }
}

@Preview(showBackground = true, name = "Write refused")
@Composable
fun ProgramSummaryWriteRefusedPreview() {
    // The interesting half of the scoping model: reads succeeded, so the summary is intact, and the
    // write was vetoed on its own terms. Amber rather than red — the guard doing its job is not a bug.
    PluginImplementationTestTheme {
        ProgramSummaryCard(
            state = SummaryState.Loaded(SAMPLE),
            pluginVersion = PLUGIN_VERSION,
            writeState = WriteState.Refused(
                "Write refused: this D2DataScope does not permit writing event null in program 'IpHINAT79UW'",
            ),
        )
    }
}

@Preview(showBackground = true, name = "Write permitted")
@Composable
fun ProgramSummaryWriteOkPreview() {
    PluginImplementationTestTheme {
        ProgramSummaryCard(
            state = SummaryState.Loaded(SAMPLE),
            pluginVersion = PLUGIN_VERSION,
            writeState = WriteState.Succeeded("Xk9pQ2mLvRt"),
        )
    }
}

@Preview(showBackground = true, name = "Read-only grant")
@Composable
fun ProgramSummaryReadOnlyPreview() {
    // What a grant without READ_EVENT / READ_ENROLLMENT looks like: the summary still renders, the
    // event count reads "not readable" rather than "0", and there is no write target at all.
    PluginImplementationTestTheme {
        ProgramSummaryCard(
            state = SummaryState.Loaded(SAMPLE.copy(eventCount = null, writeTarget = null)),
            pluginVersion = PLUGIN_VERSION,
        )
    }
}

@Preview(showBackground = true, name = "Search probes")
@Composable
fun ProgramSummarySearchProbesPreview() {
    // The shape a healthy run has: the widening attempts come back equal to the baseline, and the
    // ungranted program comes back empty. A red ✗ here would mean a probe got past the grant.
    PluginImplementationTestTheme {
        ProgramSummaryCard(
            state = SummaryState.Loaded(SAMPLE),
            pluginVersion = PLUGIN_VERSION,
            searchState = SearchState.Done(
                baseline = 27,
                probes = listOf(
                    SearchProbe(
                        label = "Granted program",
                        mechanism = "ordinary in-scope search",
                        count = 27,
                        expectation = SearchProbe.Expectation.INFORMATIONAL,
                    ),
                    SearchProbe(
                        label = "Ungranted program",
                        mechanism = "applyGrant() rewrites an ungranted program to __scope_denied__",
                        count = 0,
                        expectation = SearchProbe.Expectation.EMPTY,
                    ),
                    SearchProbe(
                        label = "orgUnitMode = ACCESSIBLE",
                        mechanism = "grant forces SELECTED over its own pre-expanded unit set",
                        count = 27,
                        expectation = SearchProbe.Expectation.SAME_AS_BASELINE,
                    ),
                    SearchProbe(
                        label = "onlineOnly()",
                        mechanism = "grant forces OFFLINE_ONLY",
                        count = 27,
                        expectation = SearchProbe.Expectation.SAME_AS_BASELINE,
                    ),
                ),
            ),
        )
    }
}

@Preview(showBackground = true, name = "Search not granted")
@Composable
fun ProgramSummarySearchUnavailablePreview() {
    PluginImplementationTestTheme {
        ProgramSummaryCard(
            state = SummaryState.Loaded(SAMPLE),
            pluginVersion = PLUGIN_VERSION,
            searchState = SearchState.Unavailable(
                "[SCOPE_VIOLATION] This D2DataScope does not grant the SEARCH_TRACKED_ENTITY capability",
            ),
        )
    }
}

@Preview(showBackground = true, name = "Out of scope")
@Composable
fun ProgramSummaryFailedPreview() {
    // What a scope violation looks like to the user: the SDK refuses the write or the accessor
    // throws, and the plugin renders the message rather than disappearing.
    PluginImplementationTestTheme {
        ProgramSummaryCard(
            state = SummaryState.Failed("This D2DataScope does not grant the READ_TRACKED_ENTITY capability"),
            pluginVersion = PLUGIN_VERSION,
        )
    }
}
