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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_event_count
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_event_count_denied
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_icon
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_loading
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_search_baseline
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_search_button
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_search_results
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_search_unavailable
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_searching
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_write_button
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_write_failed
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_write_no_target
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_write_ok
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_write_refused
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_writing
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
    writeState: WriteState = WriteState.Idle,
    onAddEvent: (WriteTarget) -> Unit = {},
    searchState: SearchState = SearchState.Idle,
    onProbeSearch: () -> Unit = {},
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
            if (state is SummaryState.Loaded) {
                Spacer(modifier = Modifier.height(12.dp))
                WriteTest(
                    target = state.summary.writeTarget,
                    writeState = writeState,
                    onAddEvent = onAddEvent,
                )
                Spacer(modifier = Modifier.height(12.dp))
                SearchTest(searchState = searchState, onProbeSearch = onProbeSearch)
            }
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

    Spacer(modifier = Modifier.height(4.dp))
    Text(
        // Null means READ_EVENT was withheld, which has to read differently from a count of zero.
        text = summary.eventCount
            ?.let { stringResource(Res.string.plugin_event_count, it.toString()) }
            ?: stringResource(Res.string.plugin_event_count_denied),
        style = MaterialTheme.typography.bodyMedium,
        color = if (summary.eventCount == null) Color(0xFF8D6E00) else Color(0xFF555555),
    )

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

/**
 * The write half of the scoping test.
 *
 * Reads are enforced by filters, so an out-of-scope read is silently empty; writes are enforced by a
 * guard, so an out-of-scope write is a loud `SCOPE_VIOLATION`. Those are two different mechanisms and
 * only this button exercises the second one. The target is resolved from readable data, but the guard
 * checks it against the *writable* grant — so the button being enabled says nothing about whether the
 * write will be allowed.
 */
@Composable
private fun WriteTest(
    target: WriteTarget?,
    writeState: WriteState,
    onAddEvent: (WriteTarget) -> Unit,
) {
    if (target == null) {
        Text(
            text = stringResource(Res.string.plugin_write_no_target),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8D6E00),
        )
        return
    }

    Button(
        onClick = { onAddEvent(target) },
        enabled = writeState !is WriteState.Writing,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
    ) {
        Text(text = stringResource(Res.string.plugin_write_button))
    }

    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "  → stage ${target.programStageUid} @ ${target.orgUnitUid}",
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF888888),
    )

    when (writeState) {
        is WriteState.Idle -> Unit

        is WriteState.Writing -> WriteResultText(stringResource(Res.string.plugin_writing), Color(0xFF555555))

        is WriteState.Succeeded -> WriteResultText(
            text = stringResource(Res.string.plugin_write_ok, writeState.eventUid),
            color = Color(0xFF1B5E20),
        )

        // Amber, not red: a refusal is the scope working, not the plugin breaking.
        is WriteState.Refused -> WriteResultText(
            text = "${stringResource(Res.string.plugin_write_refused)} ${writeState.message}",
            color = Color(0xFF8D6E00),
        )

        is WriteState.Failed -> WriteResultText(
            text = "${stringResource(Res.string.plugin_write_failed)} ${writeState.message}",
            color = Color(0xFFB00020),
        )
    }
}

@Composable
private fun WriteResultText(text: String, color: Color) {
    Spacer(modifier = Modifier.height(4.dp))
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

/**
 * The search half of the scoping test.
 *
 * Separate from [WriteTest] because it targets a third mechanism. Reads are bounded by append-only
 * filters and writes by a guard on the object; tracker search is bounded by neither, because its
 * scope fields are *replaced* by `by*()` rather than accumulated. The grant is re-applied on every
 * repository the fluent API builds, and these probes are what confirm that — each one asks for more
 * than the grant allows and should come back with no more than the baseline.
 */
@Composable
private fun SearchTest(
    searchState: SearchState,
    onProbeSearch: () -> Unit,
) {
    Button(
        onClick = onProbeSearch,
        enabled = searchState !is SearchState.Running,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
    ) {
        Text(text = stringResource(Res.string.plugin_search_button))
    }

    when (searchState) {
        is SearchState.Idle -> Unit

        is SearchState.Running -> WriteResultText(stringResource(Res.string.plugin_searching), Color(0xFF555555))

        is SearchState.Unavailable -> WriteResultText(
            // Not red: a missing SEARCH_TRACKED_ENTITY capability is a result, not a malfunction.
            text = "${stringResource(Res.string.plugin_search_unavailable)} ${searchState.message}",
            color = Color(0xFF8D6E00),
        )

        is SearchState.Done -> {
            WriteResultText(
                text = stringResource(Res.string.plugin_search_baseline, searchState.baseline.toString()),
                color = Color(0xFF555555),
            )
            searchState.probes.forEach { probe ->
                ProbeRow(probe = probe, baseline = searchState.baseline)
            }
        }
    }
}

@Composable
private fun ProbeRow(probe: SearchProbe, baseline: Int) {
    val verdict = probe.verdict(baseline)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "  ${verdict.symbol()} ${probe.label} — " +
            stringResource(Res.string.plugin_search_results, probe.count.toString()),
        style = MaterialTheme.typography.bodySmall,
        color = verdict.color(),
    )
    Text(
        text = "      ${probe.mechanism}",
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF888888),
    )
}

private fun Verdict.symbol(): String = when (this) {
    Verdict.INFO -> "•"
    Verdict.PASS -> "✓"
    Verdict.FAIL -> "✗"
}

private fun Verdict.color(): Color = when (this) {
    Verdict.INFO -> Color(0xFF555555)
    Verdict.PASS -> Color(0xFF1B5E20)
    // A FAIL here means a widening attempt got through, which is the one outcome that is a real bug.
    Verdict.FAIL -> Color(0xFFB00020)
}

/** How many entries [ProgramSummaryCard] lists before collapsing the rest into "and N more". */
internal const val LISTED_LIMIT = MAX_LISTED
