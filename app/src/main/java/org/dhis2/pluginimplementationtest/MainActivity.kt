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
import org.dhis2.mobile.plugin.sdk.InjectionPoint
import org.dhis2.mobile.plugin.sdk.PluginMetadata
import org.dhis2.pluginimplementationtest.ui.theme.PluginImplementationTestTheme

/**
 * Stands in for the server dataStore config that the Capture App would read.
 *
 * In production the DHIS2 administrator authors this as JSON; the plugin itself declares none of
 * it. Declaring it here keeps the harness honest about where the values really come from — and
 * lets you exercise scope enforcement by removing a UID from [PluginMetadata.allowedProgramUids].
 */
private val serverConfig = PluginMetadata(
    id = "org.dhis2.myplugin",
    version = "1.5.0",
    entryPoint = "org.dhis2.pluginimplementationtest.MyPlugin",
    allowedProgramUids = listOf("IpHINAT79UW"),
    injectionPoints = listOf(InjectionPoint.HOME_ABOVE_PROGRAM_LIST),
)

class MainActivity : ComponentActivity() {
    private val plugin = MyPlugin()
    private val pluginContext = StubDhis2PluginContext(serverConfig)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PluginImplementationTestTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreenPreview(
                        modifier = Modifier.padding(innerPadding),
                        pluginContent = { plugin.content(pluginContext) },
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

@Preview(showBackground = true)
@Composable
fun HomeScreenPreviewDefault() {
    PluginImplementationTestTheme {
        HomeScreenPreview(
            pluginContent = {
                MyPlugin().content(StubDhis2PluginContext(serverConfig))
            },
        )
    }
}