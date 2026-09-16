package org.dhis2.mobile.plugin.harness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.dhis2.mobile.plugin.harness.ui.theme.HarnessTheme
import org.dhis2.mobile.plugin.sdk.Dhis2Plugin

/**
 * Signs in to a real DHIS2 (credentials from `local.properties`) and renders the real plugin.
 *
 * Not the Capture App — `AGENTS.md` lists what only the real host can exercise.
 *
 * Note what this file does **not** import: the plugin's entry point, its models, or its card. It
 * loads the entry point by name, exactly as the host does, so it is the same code path whichever
 * plugin `plugin.json` names — your own, or one of the `examples/`.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HarnessTheme {
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
                                    title = "Connected — ${BuildConfig.PLUGIN_NAME}",
                                    body = "Entry point ${BuildConfig.PLUGIN_ENTRY_POINT}\n" +
                                        "Downloaded tracker data for programme ${current.programUid}",
                                )
                                when (val loaded = rememberPlugin()) {
                                    is PluginLoad.Failed -> HarnessMessage(
                                        title = "Could not load the plugin",
                                        body = loaded.message,
                                    )

                                    is PluginLoad.Loaded -> PluginHost(
                                        plugin = loaded.plugin,
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
}

/** The outcome of looking the entry point up by name — a real failure mode, so it has a state. */
private sealed interface PluginLoad {
    data class Loaded(val plugin: Dhis2Plugin) : PluginLoad
    data class Failed(val message: String) : PluginLoad
}

/**
 * Instantiates the plugin the way the Capture App's `PluginLoader` does: `Class.forName` on the
 * FQCN the configuration names, then a no-argument constructor.
 *
 * This is the only thing in the repo that checks the entry-point contract without a device. A class
 * at the wrong FQCN, or without a public no-arg constructor, fails here — on a laptop, with a
 * readable message — instead of on the device as a `ClassNotFoundException` the host swallows into
 * "the plugin silently did not load".
 */
@Composable
private fun rememberPlugin(): PluginLoad = remember {
    val fqcn = BuildConfig.PLUGIN_ENTRY_POINT
    try {
        PluginLoad.Loaded(
            Class.forName(fqcn).getDeclaredConstructor().newInstance() as Dhis2Plugin,
        )
    } catch (error: ClassNotFoundException) {
        PluginLoad.Failed(
            "No class $fqcn. plugin.json's package + entryPoint must name a real class — " +
                "run python3 tools/check-identity.py.",
        )
    } catch (error: NoSuchMethodException) {
        PluginLoad.Failed("$fqcn has no public no-argument constructor; the host needs one.")
    } catch (error: ClassCastException) {
        PluginLoad.Failed("$fqcn does not implement Dhis2Plugin.")
    } catch (error: Throwable) {
        PluginLoad.Failed("${error::class.simpleName}: ${error.message ?: "no detail"}")
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
