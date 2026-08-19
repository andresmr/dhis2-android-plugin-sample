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

private const val PLUGIN_VERSION = "2.0.0"

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
