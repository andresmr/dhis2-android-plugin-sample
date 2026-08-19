package org.dhis2.pluginimplementationtest

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.dhis2.pluginimplementationtest.plugin.generated.resources.Res
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_and_more
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_enrolled_count
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_error_prefix
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_icon
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_loading
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val MAX_LISTED = 3

/**
 * The plugin's whole UI, as a function of [state] alone.
 *
 * No `Dhis2PluginContext`, no SDK, no suspending work — so it renders in a `@Preview`, in a harness
 * app, and in a screenshot test, all with data you supply.
 */
@Composable
fun ProgramSummaryCard(
    state: SummaryState,
    pluginVersion: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            VersionBadge(pluginVersion)
            Spacer(modifier = Modifier.height(8.dp))
            Header(state)
            Spacer(modifier = Modifier.height(8.dp))
            Body(state)
        }
    }
}

@Composable
private fun VersionBadge(pluginVersion: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFF4CAF50), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = "Plugin v$pluginVersion",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

@Composable
private fun Header(state: SummaryState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Image (not Icon) so the drawable's intrinsic green shield colour shows through — makes
        // resource loading visibly unambiguous.
        Image(
            painter = painterResource(Res.drawable.plugin_icon),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            // The program's real name, resolved from metadata. The previous version could only
            // print a hardcoded string next to a raw UID.
            text = when (state) {
                is SummaryState.Loaded -> "${state.summary.programName} (${state.summary.programUid})"
                else -> stringResource(Res.string.plugin_loading)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun Body(state: SummaryState) {
    when (state) {
        is SummaryState.Loading ->
            Text(
                text = stringResource(Res.string.plugin_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF555555),
            )

        is SummaryState.Failed ->
            Text(
                text = "${stringResource(Res.string.plugin_error_prefix)} ${state.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB00020),
            )

        is SummaryState.Loaded -> LoadedBody(state.summary)
    }
}

@Composable
private fun LoadedBody(summary: ProgramSummary) {
    Text(
        text = stringResource(Res.string.plugin_enrolled_count, summary.enrolledCount.toString()),
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFF555555),
    )

    summary.recent.forEach { person ->
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            // Attributes now arrive labelled, so this reads "First name: Alice" rather than a bare
            // value under an unprintable UID.
            text = "  • " + person.attributes.joinToString(" / ") { "${it.label}: ${it.value}" },
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF333333),
        )
    }

    val remaining = summary.enrolledCount - summary.recent.size
    if (remaining > 0) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "  " + stringResource(Res.string.plugin_and_more, remaining.toString()),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF888888),
        )
    }
}

/** How many entries [ProgramSummaryCard] lists before collapsing the rest into "and N more". */
internal const val LISTED_LIMIT = MAX_LISTED
