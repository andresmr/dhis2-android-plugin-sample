package org.dhis2.mobile.plugin.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import org.dhis2.mobile.plugin.sample.harness.HarnessPluginContext
import org.dhis2.mobile.plugin.sample.harness.HarnessSession
import org.dhis2.mobile.plugin.sample.harness.HarnessState
import org.dhis2.mobile.plugin.sample.harness.PluginHost
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.dhis2.mobile.plugin.sample.model.EnrolledPerson
import org.dhis2.mobile.plugin.sample.model.ProgramSummary
import org.dhis2.mobile.plugin.sample.ui.PluginCard
import org.dhis2.mobile.plugin.sample.ui.PluginUiState
import org.dhis2.mobile.plugin.sample.ui.SummaryState
import org.dhis2.mobile.plugin.sample.ui.theme.PluginSampleTheme

private const val PLUGIN_VERSION = "preview"

/**
 * For the `@Preview`s below, which cannot log in. The harness itself uses real data.
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

/**
 * Signs in to a real DHIS2 (credentials from `local.properties`) and renders the real plugin.
 *
 * Not the Capture App — `CLAUDE.md` lists what only the real host can exercise.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PluginSampleTheme {
                var state: HarnessState by remember { mutableStateOf(HarnessState.Working("Starting")) }

                LaunchedEffect(Unit) {
                    val session = HarnessSession(applicationContext)
                    session.onStep = { step -> state = HarnessState.Working(step) }
                    state = session.start()
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        when (val current = state) {
                            is HarnessState.Working -> HarnessMessage("Working", current.step)

                            is HarnessState.NotConfigured -> HarnessMessage(
                                title = "Not configured",
                                body = "Add these to local.properties, then rebuild:\n\n" +
                                    current.missing.joinToString("\n") { "  $it=" },
                            )

                            is HarnessState.Failed -> HarnessMessage(
                                title = "Failed while: ${current.step}",
                                body = current.message,
                            )

                            is HarnessState.Ready -> {
                                HarnessMessage(
                                    title = "Connected",
                                    body = "Downloaded tracker data for programme " +
                                        "${current.programUid}\n" +
                                        "The plugin resolves the first tracker programme by name, " +
                                        "which is the same one this downloaded — unless " +
                                        "dhis2.programUid names a different one.",
                                )
                                PluginHost(
                                    plugin = ProgramOverviewPlugin(),
                                    context = HarnessPluginContext(current.d2),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Plain on purpose: harness chrome should not be mistaken for the plugin's UI. */
@Composable
private fun HarnessMessage(title: String, body: String) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(text = body, style = MaterialTheme.typography.bodySmall)
    }
}

@Preview(showBackground = true, name = "Loaded")
@Composable
fun LoadedPreview() {
    PluginSampleTheme { PluginCard(state = LOADED, pluginVersion = PLUGIN_VERSION) }
}

@Preview(showBackground = true, name = "Loading")
@Composable
fun LoadingPreview() {
    PluginSampleTheme { PluginCard(state = PluginUiState(), pluginVersion = PLUGIN_VERSION) }
}

@Preview(showBackground = true, name = "Read failed")
@Composable
fun FailedPreview() {
    PluginSampleTheme {
        PluginCard(
            state = PluginUiState(summary = SummaryState.Failed("[UNEXPECTED] no such program")),
            pluginVersion = PLUGIN_VERSION,
        )
    }
}
