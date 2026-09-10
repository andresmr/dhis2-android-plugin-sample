package org.dhis2.mobile.plugin.sample.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import org.dhis2.mobile.plugin.sample.model.MAX_LISTED_PEOPLE
import org.dhis2.mobile.plugin.sample.model.ProgramSummary
import org.dhis2.mobile.plugin.sample.generated.resources.Res
import org.dhis2.mobile.plugin.sample.generated.resources.plugin_and_more
import org.dhis2.mobile.plugin.sample.generated.resources.plugin_error_prefix
import org.dhis2.mobile.plugin.sample.generated.resources.plugin_icon
import org.dhis2.mobile.plugin.sample.generated.resources.plugin_loading
import org.dhis2.mobile.plugin.sample.generated.resources.plugin_teis_count
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource


private val Muted = Color(0xFF555555)
private val Bad = Color(0xFFB00020)

/**
 * The plugin's whole UI, as a function of [state] alone.
 *
 * No `Dhis2PluginContext`, no SDK, no suspending work — so it renders in a `@Preview`, in the harness
 * app, and in a screenshot test, all with data you supply.
 *
 * **Deliberately short.** The host renders this at `HOME_ABOVE_PROGRAM_LIST`, a plain non-scrolling
 * `Column` above the host's own program list — so height taken here is height taken from the host,
 * and anything past the viewport is unreachable rather than scrollable. Hence the bounded internal
 * scroll as a backstop.
 */
@Composable
fun PluginCard(
    state: PluginUiState,
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
        Column(
            modifier = Modifier
                .padding(12.dp)
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            TitleRow(pluginVersion, state.summary)
            Spacer(modifier = Modifier.height(8.dp))
            Body(state.summary)
        }
    }
}

@Composable
private fun TitleRow(pluginVersion: String, summary: SummaryState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Image (not Icon) so the drawable's own green shows through — makes resource loading from
        // the extracted bundle visibly unambiguous.
        Image(
            painter = painterResource(Res.drawable.plugin_icon),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .background(Color(0xFF4CAF50), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            Text(
                text = "Plugin v$pluginVersion",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = (summary as? SummaryState.Loaded)?.summary?.programName.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun Body(state: SummaryState) {
    when (state) {
        is SummaryState.Loading ->
            Line(stringResource(Res.string.plugin_loading), Muted)

        is SummaryState.Failed ->
            Line("${stringResource(Res.string.plugin_error_prefix)} ${state.message}", Bad)

        is SummaryState.Loaded -> LoadedBody(state.summary)
    }
}

@Composable
private fun LoadedBody(summary: ProgramSummary) {
    Line(stringResource(Res.string.plugin_teis_count, summary.enrolledCount), Muted)
    Line("${summary.eventCount} event(s) in this program", Muted)

    summary.recent.take(MAX_LISTED_PEOPLE).forEach { person ->
        Spacer(modifier = Modifier.height(2.dp))
        // Already the name the programme lists this person under — never a UID, and never
        // whichever attribute the SDK happened to return first.
        Line("  • " + person.displayLabel, Color(0xFF333333))
    }
    val remaining = summary.enrolledCount - minOf(summary.recent.size, MAX_LISTED_PEOPLE)
    if (remaining > 0) {
        Line("  " + stringResource(Res.string.plugin_and_more, remaining), Color(0xFF888888))
    }
}



/** Every one-line row in this card, so spacing and type stay consistent. */
@Composable
private fun Line(text: String, color: Color) {
    Spacer(modifier = Modifier.height(2.dp))
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}
