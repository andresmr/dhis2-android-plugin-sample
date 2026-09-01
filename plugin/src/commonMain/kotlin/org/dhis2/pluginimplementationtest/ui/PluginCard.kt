package org.dhis2.pluginimplementationtest.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.dhis2.pluginimplementationtest.model.DataSetSummary
import org.dhis2.pluginimplementationtest.model.DataValueTarget
import org.dhis2.pluginimplementationtest.model.MetadataItem
import org.dhis2.pluginimplementationtest.model.ProgramSummary
import org.dhis2.pluginimplementationtest.model.ScopeDimension
import org.dhis2.pluginimplementationtest.model.ScopeSnapshot
import org.dhis2.pluginimplementationtest.model.SearchProbe
import org.dhis2.pluginimplementationtest.model.Verdict
import org.dhis2.pluginimplementationtest.model.WriteTarget
import org.dhis2.pluginimplementationtest.model.describe
import org.dhis2.pluginimplementationtest.model.verdict
import org.dhis2.pluginimplementationtest.plugin.generated.resources.Res
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_counts
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_dataset_counts
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_datavalue_short
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_error_prefix
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_icon
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_loading
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_scope_title
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_search_short
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_search_unavailable
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_searching
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_write_failed
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_write_ok
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_write_short
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_writing
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Muted grey used for secondary text throughout the card. */
private val Muted = Color(0xFF555555)

/** Amber: something to look at, but not a malfunction — a withheld grant or a refused write. */
private val Attention = Color(0xFF8D6E00)

private val Bad = Color(0xFFB00020)
private val Good = Color(0xFF1B5E20)

/** How many items each expanded scope dimension lists before collapsing the rest. */
private const val MAX_VISIBLE = 5

/** How many enrolled people the expanded detail lists. */
private const val MAX_LISTED = 3

/**
 * The plugin's whole UI, as a function of [state] alone.
 *
 * No `Dhis2PluginContext`, no SDK, no suspending work — so it renders in a `@Preview`, in a harness
 * app, and in a screenshot test, all with data you supply.
 *
 * **Deliberately short.** The host renders this at `HOME_ABOVE_PROGRAM_LIST`, which is a plain
 * non-scrolling `Column` above the host's own program list — so height taken here is height taken
 * from the host, and anything past the viewport is simply unreachable. Detail therefore lives behind
 * two toggles, with a bounded internal scroll as a backstop. The one thing never hidden is a scope
 * warning: a grant that is not doing what the administrator intended is the whole point of this
 * plugin.
 */
@Composable
fun PluginCard(
    state: PluginUiState,
    pluginVersion: String,
    modifier: Modifier = Modifier,
    onAddEvent: (WriteTarget) -> Unit = {},
    onProbeSearch: () -> Unit = {},
    onSelectProgram: (MetadataItem) -> Unit = {},
    onSelectDataSet: (MetadataItem) -> Unit = {},
    onWriteDataValue: (DataValueTarget) -> Unit = {},
    initiallyExpanded: Boolean = false,
) {
    var scopeExpanded by remember { mutableStateOf(initiallyExpanded) }
    var detailExpanded by remember { mutableStateOf(false) }

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
                // Bounded, so an expanded section scrolls inside the card instead of pushing the
                // host's program list off the screen.
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            TitleRow(pluginVersion)
            Spacer(modifier = Modifier.height(6.dp))

            ScopeSection(
                state = state.scope,
                selectedProgram = state.selectedProgram,
                selectedDataSet = state.selectedDataSet,
                expanded = scopeExpanded,
                onToggle = { scopeExpanded = !scopeExpanded },
                onSelectProgram = onSelectProgram,
                onSelectDataSet = onSelectDataSet,
            )

            Spacer(modifier = Modifier.height(8.dp))
            SummarySection(
                state = state.summary,
                expanded = detailExpanded,
                onToggle = { detailExpanded = !detailExpanded },
            )

            Spacer(modifier = Modifier.height(6.dp))
            DataSetSection(state.dataSet)

            Spacer(modifier = Modifier.height(8.dp))
            Actions(
                target = (state.summary as? SummaryState.Loaded)?.summary?.writeTarget,
                valueTarget = (state.dataSet as? DataSetState.Loaded)?.summary?.writeTarget,
                canProbe = state.selectedProgram != null,
                writeState = state.write,
                searchState = state.search,
                valueWriteState = state.dataValueWrite,
                onAddEvent = onAddEvent,
                onProbeSearch = onProbeSearch,
                onWriteDataValue = onWriteDataValue,
            )

            Results(
                writeState = state.write,
                valueWriteState = state.dataValueWrite,
                searchState = state.search,
            )
        }
    }
}

@Composable
private fun TitleRow(pluginVersion: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Image (not Icon) so the drawable's own green shows through — makes resource loading from
        // the extracted bundle visibly unambiguous.
        Image(
            painter = painterResource(Res.drawable.plugin_icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
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
    }
}

// ── Scope ────────────────────────────────────────────────────────────────────

@Composable
private fun ScopeSection(
    state: ScopeState,
    selectedProgram: MetadataItem?,
    selectedDataSet: MetadataItem?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelectProgram: (MetadataItem) -> Unit,
    onSelectDataSet: (MetadataItem) -> Unit,
) {
    when (state) {
        is ScopeState.Loading -> Line("Reading the granted scope…", Muted)

        is ScopeState.Failed -> Line("Scope unreadable: ${state.message}", Bad)

        is ScopeState.Loaded -> {
            val snapshot = state.snapshot
            Text(
                text = (if (expanded) "▾ " else "▸ ") + stringResource(Res.string.plugin_scope_title) +
                    "   " + snapshot.headline(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 2.dp),
            )

            // Warnings stay visible while collapsed — hiding these would defeat the point.
            snapshot.warnings.forEach { warning -> Line("  ! $warning", Bad) }

            if (expanded) {
                Line("  capabilities: " + snapshot.capabilities.joinToString(", ").ifEmpty { "(none)" }, Muted)
                snapshot.dimensions.forEach { dimension ->
                    DimensionRow(dimension, selectedProgram, selectedDataSet, onSelectProgram, onSelectDataSet)
                }
            }
        }
    }
}

/**
 * "programs 1 · data sets 0 · org units 1 · types 3 · caps 6" — enough to tell at a glance whether a
 * grant looks like what was intended.
 *
 * Count second, not first: the dimension labels are already plural, and "1 programs" reads like a
 * bug in the plugin rather than a grant of one program.
 */
private fun ScopeSnapshot.headline(): String =
    (dimensions.map { "${it.label.shortened()} ${it.visible.size}" } + "caps ${capabilities.size}")
        .joinToString(" · ")

/** Keeps the collapsed headline on one or two lines; the full labels are in the expanded rows. */
private fun String.shortened(): String = when (this) {
    "Tracked entity types" -> "types"
    else -> lowercase()
}

@Composable
private fun DimensionRow(
    dimension: ScopeDimension,
    selectedProgram: MetadataItem?,
    selectedDataSet: MetadataItem?,
    onSelectProgram: (MetadataItem) -> Unit,
    onSelectDataSet: (MetadataItem) -> Unit,
) {
    Line(
        text = "  ${dimension.label}: ${dimension.declared.describe()} → visible ${dimension.visible.size}",
        color = if (dimension.grantedButEmpty) Attention else Color(0xFF333333),
    )

    // Programs and data sets double as selectors: tapping one points the matching test at it. That
    // removes two duplicate "under test" sections and makes the interaction obvious.
    val isPrograms = dimension.label == ScopeSnapshot.PROGRAMS
    val isDataSets = dimension.label == ScopeSnapshot.DATA_SETS
    val selectable = isPrograms || isDataSets
    val selected = if (isPrograms) selectedProgram else selectedDataSet
    val onSelect = if (isPrograms) onSelectProgram else onSelectDataSet

    dimension.visible.take(MAX_VISIBLE).forEach { item ->
        val isSelected = selectable && item.uid == selected?.uid
        Text(
            text = "     " + (if (selectable) if (isSelected) "● " else "○ " else "• ") +
                "${item.displayName} (${item.uid})",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Good else Color(0xFF666666),
            modifier = if (selectable) {
                // A real touch target. These rows are labelSmall, which on its own gives a ~12dp
                // strip — far under the 48dp minimum, and it showed up on device as taps that
                // landed a few pixels off doing nothing at all.
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable { onSelect(item) }
                    .padding(vertical = 12.dp)
            } else {
                Modifier.padding(vertical = 1.dp)
            },
        )
    }
    val hidden = dimension.visible.size - MAX_VISIBLE
    if (hidden > 0) Line("     … and $hidden more", Color(0xFF888888))
}

// ── Summary ──────────────────────────────────────────────────────────────────

@Composable
private fun SummarySection(
    state: SummaryState,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    when (state) {
        is SummaryState.Loading -> Line(stringResource(Res.string.plugin_loading), Muted)

        is SummaryState.NoProgram ->
            // Not an error: the config granted nothing readable. Naming that is the difference
            // between a diagnosable state and an empty card.
            Line("No program is visible under this grant — nothing to summarise.", Attention)

        is SummaryState.Failed ->
            Line("${stringResource(Res.string.plugin_error_prefix)} ${state.message}", Bad)

        is SummaryState.Loaded -> {
            val summary = state.summary
            Text(
                text = (if (expanded) "▾ " else "▸ ") + summary.programName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 2.dp),
            )
            Line(
                text = stringResource(
                    Res.string.plugin_counts,
                    summary.enrolledCount.toString(),
                    // Null means READ_EVENT was withheld, which has to read differently from zero.
                    summary.eventCount?.toString() ?: "—",
                ),
                color = if (summary.eventCount == null) Attention else Muted,
            )
            if (expanded) ExpandedDetail(summary)
        }
    }
}

@Composable
private fun ExpandedDetail(summary: ProgramSummary) {
    Line("  uid: ${summary.programUid}", Color(0xFF888888))
    summary.recent.take(MAX_LISTED).forEach { person ->
        // Attributes arrive labelled, so this reads "First name: Alice" rather than a bare value
        // under an unprintable UID.
        Line("  • " + person.attributes.joinToString(" / ") { "${it.label}: ${it.value}" }, Color(0xFF333333))
    }
    summary.writeTarget?.let { Line("  write target: stage ${it.programStageUid} @ ${it.orgUnitUid}", Color(0xFF888888)) }
}

/**
 * The aggregate half, in one line.
 *
 * Deliberately terser than the tracker summary: its job is to show that the *other* enforcement path
 * resolved — data set → data elements → values — not to explore the data.
 */
@Composable
private fun DataSetSection(state: DataSetState) {
    when (state) {
        is DataSetState.Loading -> Line("Reading the granted data set…", Muted)

        // Not an error: aggregate access is simply closed.
        is DataSetState.NoDataSet -> Line("No data set is visible under this grant.", Attention)

        is DataSetState.Failed -> Line("Data set unreadable: ${state.message}", Bad)

        is DataSetState.Loaded -> LoadedDataSet(state.summary)
    }
}

@Composable
private fun LoadedDataSet(summary: DataSetSummary) {
    Text(
        text = summary.dataSetName,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
    )
    Line(
        text = stringResource(
            Res.string.plugin_dataset_counts,
            summary.dataElementCount.toString(),
            // Null means READ_DATA_VALUE was withheld, which reads differently from zero.
            summary.dataValueCount?.toString() ?: "—",
        ),
        color = if (summary.dataValueCount == null) Attention else Muted,
    )
}

// ── Actions and results ──────────────────────────────────────────────────────

/**
 * The two test buttons, side by side.
 *
 * Deliberately **not** using `Modifier.weight()`. On device that threw
 * `NoSuchMethodError: weight$default` — the plugin compiles against `foundation-layout` 1.10.5
 * (what Compose Multiplatform 1.10.3 brings transitively) while the host ships 1.10.6, which it
 * pulls in directly, and the defaulted overload differs between them. Matching the host's
 * `composeMultiplatform` version is necessary but not sufficient; sizing to content needs no
 * defaulted `RowScope` API and so cannot drift.
 */
@Composable
private fun Actions(
    target: WriteTarget?,
    valueTarget: DataValueTarget?,
    canProbe: Boolean,
    writeState: WriteState,
    searchState: SearchState,
    valueWriteState: WriteState,
    onAddEvent: (WriteTarget) -> Unit,
    onProbeSearch: () -> Unit,
    onWriteDataValue: (DataValueTarget) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { target?.let(onAddEvent) },
            enabled = target != null && writeState !is WriteState.Writing,
            colors = ButtonDefaults.buttonColors(containerColor = Good),
        ) {
            Text(text = stringResource(Res.string.plugin_write_short), maxLines = 1)
        }
        Button(
            onClick = onProbeSearch,
            enabled = canProbe && searchState !is SearchState.Running,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
        ) {
            Text(text = stringResource(Res.string.plugin_search_short), maxLines = 1)
        }
        Button(
            onClick = { valueTarget?.let(onWriteDataValue) },
            enabled = valueTarget != null && valueWriteState !is WriteState.Writing,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A)),
        ) {
            Text(text = stringResource(Res.string.plugin_datavalue_short), maxLines = 1)
        }
    }
}

/**
 * Outcomes, rendered only once there is one.
 *
 * The idle card pays nothing for these, which is most of why it fits.
 */
@Composable
private fun Results(writeState: WriteState, valueWriteState: WriteState, searchState: SearchState) {
    // Prefixed, because two independent grants can refuse and the reader must see which one did.
    WriteOutcome("event", writeState)
    WriteOutcome("value", valueWriteState)

    when (searchState) {
        is SearchState.Idle -> Unit
        is SearchState.Running -> Line(stringResource(Res.string.plugin_searching), Muted)
        is SearchState.Unavailable ->
            Line("${stringResource(Res.string.plugin_search_unavailable)} ${searchState.message}", Attention)
        is SearchState.Done -> {
            val run = searchState.run
            Line("probes (baseline ${run.baseline ?: "—"}):", Muted)
            run.probes.forEach { probe -> ProbeRow(probe, run.baseline) }
        }
    }
}

@Composable
private fun WriteOutcome(what: String, state: WriteState) {
    when (state) {
        is WriteState.Idle -> Unit
        is WriteState.Writing -> Line(stringResource(Res.string.plugin_writing), Muted)
        is WriteState.Succeeded ->
            Line(stringResource(Res.string.plugin_write_ok, "$what ${state.eventUid}"), Good)
        // Amber, not red: a refusal is the scope working, not the plugin breaking. No prefix on the
        // message itself — the SDK's description already opens with "Write refused: …".
        is WriteState.Refused -> Line(state.message, Attention)
        is WriteState.Failed ->
            Line("${stringResource(Res.string.plugin_write_failed)} ($what) ${state.message}", Bad)
    }
}

@Composable
private fun ProbeRow(probe: SearchProbe, baseline: Int?) {
    val verdict = probe.verdict(baseline)
    Line(
        text = "  ${verdict.symbol()} ${probe.label} — ${probe.count?.toString() ?: probe.error.orEmpty()}",
        color = verdict.color(),
    )
}

private fun Verdict.symbol(): String = when (this) {
    Verdict.INFO -> "•"
    Verdict.PASS -> "✓"
    Verdict.FAIL -> "✗"
    Verdict.ERROR -> "!"
}

private fun Verdict.color(): Color = when (this) {
    Verdict.INFO -> Muted
    Verdict.PASS -> Good
    // A FAIL means a widening attempt got through — the one outcome that is a real bug.
    Verdict.FAIL -> Bad
    // The probe itself broke, which says nothing about the grant either way.
    Verdict.ERROR -> Attention
}

/** Every one-line row in this card, so spacing and type stay consistent. */
@Composable
private fun Line(text: String, color: Color) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
}
