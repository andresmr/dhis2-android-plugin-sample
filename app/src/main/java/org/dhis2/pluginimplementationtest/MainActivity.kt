package org.dhis2.pluginimplementationtest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.dhis2.pluginimplementationtest.ui.theme.PluginImplementationTestTheme

class MainActivity : ComponentActivity() {
    private val plugin = MyPlugin()
    private val pluginContext = StubDhis2PluginContext(plugin.metadata)

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
                MyPlugin().content(StubDhis2PluginContext(MyPlugin().metadata))
            },
        )
    }
}