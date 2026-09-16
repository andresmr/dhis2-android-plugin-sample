package org.dhis2.mobile.plugin.template.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.dhis2.mobile.plugin.template.generated.resources.Res
import org.dhis2.mobile.plugin.template.generated.resources.plugin_error_prefix
import org.dhis2.mobile.plugin.template.generated.resources.plugin_icon
import org.dhis2.mobile.plugin.template.generated.resources.plugin_loading
import org.dhis2.mobile.plugin.template.generated.resources.plugin_program_count
import org.dhis2.mobile.plugin.template.generated.resources.plugin_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The plugin's whole UI, as a function of [state] alone.
 *
 * No `Dhis2PluginContext`, no SDK, no suspending work — so it renders in an `@Preview` with data
 * you supply, which is by far the fastest loop for UI work. That is architecture rule 2, and it is
 * enforced by the build.
 *
 * **Deliberately short.** The host renders this at `HOME_ABOVE_PROGRAM_LIST`: a non-scrolling
 * `Column` above its own programme list. Height taken here is height taken from the app, and
 * anything past the viewport is unreachable rather than scrollable — hence `heightIn` plus
 * `verticalScroll`, which `tools/check-rules.py` checks for by name.
 *
 * Note the layout APIs used have no defaulted parameters. The host declares androidx Compose
 * separately from CMP and at a higher version, so a defaulted overload compiled here can meet a
 * different `…$default` synthetic there: `Modifier.weight(1f)` once crashed the host with
 * `NoSuchMethodError: weight$default` at composition. See AGENTS.md, build rule 3.
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
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .heightIn(max = 160.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(Res.drawable.plugin_icon),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.plugin_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "v$pluginVersion",
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (val summary = state.summary) {
                is SummaryState.Loading -> Text(
                    text = stringResource(Res.string.plugin_loading),
                    style = MaterialTheme.typography.bodySmall,
                )

                is SummaryState.Loaded -> Text(
                    text = stringResource(
                        Res.string.plugin_program_count,
                        summary.summary.programCount.toString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )

                is SummaryState.Failed -> Text(
                    text = stringResource(Res.string.plugin_error_prefix) + " " + summary.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
