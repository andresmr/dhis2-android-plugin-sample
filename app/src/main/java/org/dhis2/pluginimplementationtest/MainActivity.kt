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
import org.dhis2.pluginimplementationtest.model.EnrolledPerson
import org.dhis2.pluginimplementationtest.model.LabelledValue
import org.dhis2.pluginimplementationtest.model.ProgramSummary
import org.dhis2.pluginimplementationtest.model.WriteTarget
import org.dhis2.pluginimplementationtest.ui.PluginCard
import org.dhis2.pluginimplementationtest.ui.PluginUiState
import org.dhis2.pluginimplementationtest.ui.SummaryState
import org.dhis2.pluginimplementationtest.ui.WriteState
import org.dhis2.pluginimplementationtest.ui.theme.PluginImplementationTestTheme

private const val PLUGIN_VERSION = "1.0.0"

/**
 * Sample data for the harness.
 *
 * This used to be a `StubDhis2PluginContext` that faked the plugin's data source. It cannot be any
 * more: `Dhis2PluginContext.sdk` is `D2`, which a preview app has no way to construct.
 *
 * The workaround is also the better design. `PluginCard` takes [PluginUiState] and callbacks, so the
 * harness renders the plugin's *actual* UI with whatever data it likes. What is no longer covered
 * here is the fetch in `D2PluginRepository`, which needs the Capture App or a real D2 — and that
 * boundary is exactly why the repository is kept as thin as possible.
 */
private val SAMPLE = ProgramSummary(
    programUid = "IpHINAT79UW",
    programName = "Child Programme",
    enrolledCount = 27,
    eventCount = 41,
    recent = listOf(
        EnrolledPerson(
            uid = "qTgINZ9tOtV",
            attributes = listOf(LabelledValue("First name", "Alice"), LabelledValue("Last name", "Morgan")),
        ),
        EnrolledPerson(
            uid = "rJYd0Wn4p4f",
            attributes = listOf(LabelledValue("First name", "Bilal"), LabelledValue("Last name", "Khan")),
        ),
    ),
    writeTarget = WriteTarget("IpHINAT79UW", "TFEQXHXBiFO", "A03MvHHogjR", "DiszpKrYNg8"),
)

private val LOADED = PluginUiState(summary = SummaryState.Loaded(SAMPLE))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PluginImplementationTestTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        PluginCard(state = LOADED, pluginVersion = PLUGIN_VERSION)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Loaded")
@Composable
fun LoadedPreview() {
    PluginImplementationTestTheme { PluginCard(state = LOADED, pluginVersion = PLUGIN_VERSION) }
}

@Preview(showBackground = true, name = "Loading")
@Composable
fun LoadingPreview() {
    PluginImplementationTestTheme { PluginCard(state = PluginUiState(), pluginVersion = PLUGIN_VERSION) }
}

@Preview(showBackground = true, name = "Read failed")
@Composable
fun FailedPreview() {
    PluginImplementationTestTheme {
        PluginCard(
            state = PluginUiState(summary = SummaryState.Failed("[UNEXPECTED] no such program")),
            pluginVersion = PLUGIN_VERSION,
        )
    }
}

@Preview(showBackground = true, name = "Write succeeded")
@Composable
fun WriteSucceededPreview() {
    PluginImplementationTestTheme {
        PluginCard(
            state = LOADED.copy(write = WriteState.Succeeded("Xk9pQ2mLvRt")),
            pluginVersion = PLUGIN_VERSION,
        )
    }
}
