package org.dhis2.pluginimplementationtest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.dhis2.mobile.plugin.sdk.Dhis2Plugin
import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.dhis2.mobile.plugin.sdk.InjectionPoint
import org.dhis2.mobile.plugin.sdk.PluginMetadata

class MyPlugin : Dhis2Plugin {
    override val metadata = PluginMetadata(
        id = "org.dhis2.myplugin",
        version = "1.0.0",
        entryPoint = "org.dhis2.pluginimplementationtest.MyPlugin",
        injectionPoints = listOf(InjectionPoint.HOME_ABOVE_PROGRAM_LIST),
    )

    override fun provideKoinModule() = null

    @Composable
    override fun content(context: Dhis2PluginContext) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF4CAF50), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "Plugin v${context.pluginMetadata.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Hello from MyPlugin! 👋",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "This composable was loaded from an external plugin via " +
                        "InMemoryDexClassLoader and rendered at the HOME_ABOVE_PROGRAM_LIST slot.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF555555),
                )
            }
        }
    }
}