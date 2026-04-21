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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.dhis2.mobile.plugin.sdk.Dhis2Plugin
import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.dhis2.mobile.plugin.sdk.InjectionPoint
import org.dhis2.mobile.plugin.sdk.PluginMetadata
import org.dhis2.mobile.plugin.sdk.dto.TrackedEntityInstanceDto

private const val CHILD_PROGRAMME_UID = "IpHINAT79UW"

class MyPlugin : Dhis2Plugin {
    override val metadata = PluginMetadata(
        id = "org.dhis2.myplugin",
        version = "1.1.0",
        entryPoint = "org.dhis2.pluginimplementationtest.MyPlugin",
        allowedProgramUids = listOf(CHILD_PROGRAMME_UID),
        injectionPoints = listOf(InjectionPoint.HOME_ABOVE_PROGRAM_LIST),
    )

    override fun provideKoinModule() = null

    @Composable
    override fun content(context: Dhis2PluginContext) {
        val fetch by produceState<FetchState>(
            initialValue = FetchState.Loading,
            CHILD_PROGRAMME_UID,
        ) {
            value = context.getTrackedEntityInstances(CHILD_PROGRAMME_UID).fold(
                onSuccess = { FetchState.Loaded(it) },
                onFailure = { FetchState.Failed(it.message ?: it::class.simpleName ?: "error") },
            )
        }

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
                    text = "Child Programme ($CHILD_PROGRAMME_UID)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                when (val state = fetch) {
                    is FetchState.Loading -> Text(
                        text = "Loading tracked entity instances…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF555555),
                    )
                    is FetchState.Failed -> Text(
                        text = "Failed to read TEIs: ${state.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB00020),
                    )
                    is FetchState.Loaded -> {
                        Text(
                            text = "${state.teis.size} tracked entity instance(s) available offline",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF555555),
                        )
                        state.teis.take(3).forEach { tei ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "  • ${tei.uid}  ${tei.attributes.values.joinToString(" / ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF333333),
                            )
                        }
                        if (state.teis.size > 3) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "  … and ${state.teis.size - 3} more",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF888888),
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed interface FetchState {
    data object Loading : FetchState
    data class Loaded(val teis: List<TrackedEntityInstanceDto>) : FetchState
    data class Failed(val message: String) : FetchState
}
