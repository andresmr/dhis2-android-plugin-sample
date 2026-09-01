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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.dhis2.pluginimplementationtest.model.DataSetSummary
import org.dhis2.pluginimplementationtest.model.DataValueTarget
import org.dhis2.pluginimplementationtest.model.DeclaredGrant
import org.dhis2.pluginimplementationtest.model.EnrolledPerson
import org.dhis2.pluginimplementationtest.model.LabelledValue
import org.dhis2.pluginimplementationtest.model.MetadataItem
import org.dhis2.pluginimplementationtest.model.ProgramSummary
import org.dhis2.pluginimplementationtest.model.ScopeDimension
import org.dhis2.pluginimplementationtest.model.ScopeSnapshot
import org.dhis2.pluginimplementationtest.model.SearchProbe
import org.dhis2.pluginimplementationtest.model.SearchProbeRun
import org.dhis2.pluginimplementationtest.model.WriteTarget
import org.dhis2.pluginimplementationtest.ui.PluginCard
import org.dhis2.pluginimplementationtest.ui.DataSetState
import org.dhis2.pluginimplementationtest.ui.PluginUiState
import org.dhis2.pluginimplementationtest.ui.ScopeState
import org.dhis2.pluginimplementationtest.ui.SearchState
import org.dhis2.pluginimplementationtest.ui.SummaryState
import org.dhis2.pluginimplementationtest.ui.WriteState
import org.dhis2.pluginimplementationtest.ui.theme.PluginImplementationTestTheme

private const val PLUGIN_VERSION = "2.2.1"

/**
 * Sample data for the harness.
 *
 * This used to be a `StubDhis2PluginContext` that faked the plugin's data source. It cannot be any
 * more: `Dhis2PluginContext.sdk` is a `ScopedD2` with an internal constructor, so the only way to
 * produce one is `D2.scopedTo(…)` on a real, initialised SDK — which a preview app does not have.
 *
 * The workaround is also the better design. `PluginCard` takes [PluginUiState] and callbacks, so the
 * harness renders the plugin's actual UI with whatever data it likes. What is not covered here is
 * the fetch in `ScopedPluginRepository`, which needs the Capture App or a real D2.
 */
private val CHILD_PROGRAMME = MetadataItem("IpHINAT79UW", "Child Programme")
private val ANTENATAL = MetadataItem("lxAQ7Zs9VYR", "Antenatal care visit")
private val CHILD_HEALTH = MetadataItem("BfMAe6Itzgt", "Child Health")

private val SCOPE = ScopeSnapshot(
    capabilities = listOf("READ_METADATA", "READ_TRACKED_ENTITY", "READ_EVENT", "WRITE_EVENT"),
    dimensions = listOf(
        ScopeDimension(
            label = ScopeSnapshot.PROGRAMS,
            declared = DeclaredGrant.Uids(listOf(CHILD_PROGRAMME.uid, ANTENATAL.uid)),
            visible = listOf(CHILD_PROGRAMME, ANTENATAL),
        ),
        ScopeDimension(
            label = ScopeSnapshot.DATA_SETS,
            declared = DeclaredGrant.Uids(listOf(CHILD_HEALTH.uid)),
            visible = listOf(CHILD_HEALTH),
        ),
        ScopeDimension(
            label = "Org units",
            declared = DeclaredGrant.Uids(listOf("ImspTQPwCqd")),
            visible = listOf(MetadataItem("DiszpKrYNg8", "Ngelehun CHC")),
        ),
    ),
)

private val SAMPLE = ProgramSummary(
    programName = CHILD_PROGRAMME.displayName,
    programUid = CHILD_PROGRAMME.uid,
    enrolledCount = 27,
    recent = listOf(
        EnrolledPerson(
            uid = "qTgINZ9tOtV",
            attributes = listOf(LabelledValue("First name", "Alice"), LabelledValue("Last name", "Morgan")),
        ),
        EnrolledPerson(
            uid = "rJYd0Wn4p4f",
            attributes = listOf(LabelledValue("First name", "Bilal"), LabelledValue("Last name", "Khan")),
        ),
    ),
    eventCount = 41,
    writeTarget = WriteTarget(CHILD_PROGRAMME.uid, "TFEQXHXBiFO", "A03MvHHogjR", "DiszpKrYNg8"),
)

private val DATA_SET_SAMPLE = DataSetSummary(
    dataSetUid = CHILD_HEALTH.uid,
    dataSetName = CHILD_HEALTH.displayName,
    dataElementCount = 12,
    dataValueCount = 214,
    writeTarget = DataValueTarget(
        dataSetUid = CHILD_HEALTH.uid,
        dataElementUid = "s46m5MS0hxu",
        period = "202401",
        orgUnitUid = "DiszpKrYNg8",
        categoryOptionComboUid = "Prlt0C1RF0s",
        attributeOptionComboUid = "HllvX50cXC0",
        currentValue = "12",
    ),
)

private val LOADED = PluginUiState(
    scope = ScopeState.Loaded(SCOPE),
    selectedProgram = CHILD_PROGRAMME,
    summary = SummaryState.Loaded(SAMPLE),
    selectedDataSet = CHILD_HEALTH,
    dataSet = DataSetState.Loaded(DATA_SET_SAMPLE),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PluginImplementationTestTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // The program picker is interactive here, so the harness exercises selection
                    // without needing a repository behind it.
                    var selected by remember { mutableStateOf(CHILD_PROGRAMME) }

                    Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        PluginCard(
                            state = LOADED.copy(
                                selectedProgram = selected,
                                summary = SummaryState.Loaded(
                                    SAMPLE.copy(programName = selected.displayName, programUid = selected.uid),
                                ),
                            ),
                            pluginVersion = PLUGIN_VERSION,
                            onSelectProgram = { selected = it },
                            initiallyExpanded = true,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Loaded")
@Composable
fun CollapsedPreview() {
    // The default: everything behind two toggles, so the host's program list keeps its space.
    PluginImplementationTestTheme { PluginCard(state = LOADED, pluginVersion = PLUGIN_VERSION) }
}

@Preview(showBackground = true, name = "Scope expanded")
@Composable
fun ExpandedPreview() {
    PluginImplementationTestTheme {
        PluginCard(state = LOADED, pluginVersion = PLUGIN_VERSION, initiallyExpanded = true)
    }
}

@Preview(showBackground = true, name = "Loading")
@Composable
fun LoadingPreview() {
    PluginImplementationTestTheme { PluginCard(state = PluginUiState(), pluginVersion = PLUGIN_VERSION) }
}

@Preview(showBackground = true, name = "Grant exposes no program")
@Composable
fun NoProgramPreview() {
    // The most common misconfiguration: UIDs declared, nothing visible. The card names the problem
    // instead of rendering empty.
    PluginImplementationTestTheme {
        PluginCard(
            state = PluginUiState(
                scope = ScopeState.Loaded(
                    ScopeSnapshot(
                        capabilities = listOf("READ_METADATA"),
                        dimensions = listOf(
                            ScopeDimension(
                                label = ScopeSnapshot.PROGRAMS,
                                declared = DeclaredGrant.Uids(listOf("IpHINAT79UW", "typo-uid")),
                                visible = emptyList(),
                            ),
                        ),
                    ),
                ),
                summary = SummaryState.NoProgram,
            ),
            pluginVersion = PLUGIN_VERSION,
        )
    }
}

@Preview(showBackground = true, name = "Out of scope")
@Composable
fun FailedPreview() {
    PluginImplementationTestTheme {
        PluginCard(
            state = LOADED.copy(
                summary = SummaryState.Failed(
                    "[SCOPE_VIOLATION] This D2DataScope does not grant the READ_TRACKED_ENTITY capability",
                ),
            ),
            pluginVersion = PLUGIN_VERSION,
        )
    }
}

@Preview(showBackground = true, name = "Write refused")
@Composable
fun WriteRefusedPreview() {
    // Reads succeeded, so the summary is intact, and the write was vetoed on its own terms. Amber
    // rather than red — the guard doing its job is not a bug.
    PluginImplementationTestTheme {
        PluginCard(
            state = LOADED.copy(
                write = WriteState.Refused(
                    "Write refused: this D2DataScope does not permit writing event in organisation unit 'DiszpKrYNg8'",
                ),
            ),
            pluginVersion = PLUGIN_VERSION,
        )
    }
}

@Preview(showBackground = true, name = "Aggregate write refused")
@Composable
fun ValueWriteRefusedPreview() {
    // The aggregate grant refusing while the tracker one is untouched — the case the two separate
    // outcome lines exist for.
    PluginImplementationTestTheme {
        PluginCard(
            state = LOADED.copy(
                dataValueWrite = WriteState.Refused(
                    "Write refused: this D2DataScope does not permit writing data element 's46m5MS0hxu'",
                ),
            ),
            pluginVersion = PLUGIN_VERSION,
        )
    }
}

@Preview(showBackground = true, name = "No aggregate grant")
@Composable
fun NoDataSetPreview() {
    PluginImplementationTestTheme {
        PluginCard(
            state = LOADED.copy(selectedDataSet = null, dataSet = DataSetState.NoDataSet),
            pluginVersion = PLUGIN_VERSION,
        )
    }
}

@Preview(showBackground = true, name = "Write permitted")
@Composable
fun WritePermittedPreview() {
    PluginImplementationTestTheme {
        PluginCard(
            state = LOADED.copy(write = WriteState.Succeeded("Xk9pQ2mLvRt")),
            pluginVersion = PLUGIN_VERSION,
        )
    }
}

@Preview(showBackground = true, name = "Search probes")
@Composable
fun SearchProbesPreview() {
    // A healthy run: widening attempts come back equal to the baseline, the ungranted program comes
    // back empty. A red ✗ would mean a probe got past the grant.
    PluginImplementationTestTheme {
        PluginCard(
            state = LOADED.copy(
                search = SearchState.Done(
                    SearchProbeRun(
                        baseline = 27,
                        probes = listOf(
                            SearchProbe("Granted program", "ordinary in-scope search", 27, SearchProbe.Expectation.INFORMATIONAL),
                            SearchProbe("Ungranted program", "rewritten to __scope_denied__", 0, SearchProbe.Expectation.EMPTY),
                            SearchProbe("orgUnitMode = ACCESSIBLE", "forced to SELECTED", 27, SearchProbe.Expectation.SAME_AS_BASELINE),
                            SearchProbe("onlineOnly()", "forced to OFFLINE_ONLY", 27, SearchProbe.Expectation.SAME_AS_BASELINE),
                        ),
                    ),
                ),
            ),
            pluginVersion = PLUGIN_VERSION,
        )
    }
}

@Preview(showBackground = true, name = "Search not granted")
@Composable
fun SearchUnavailablePreview() {
    PluginImplementationTestTheme {
        PluginCard(
            state = LOADED.copy(
                search = SearchState.Unavailable(
                    "[SCOPE_VIOLATION] This D2DataScope does not grant the SEARCH_TRACKED_ENTITY capability",
                ),
            ),
            pluginVersion = PLUGIN_VERSION,
        )
    }
}
