package org.dhis2.pluginimplementationtest.ui

import org.dhis2.pluginimplementationtest.model.DataSetSummary
import org.dhis2.pluginimplementationtest.model.MetadataItem
import org.dhis2.pluginimplementationtest.model.ProgramSummary
import org.dhis2.pluginimplementationtest.model.ScopeSnapshot
import org.dhis2.pluginimplementationtest.model.SearchProbeRun

/**
 * Everything the plugin's UI renders, in one place.
 *
 * Three independent parts rather than one state machine, because they genuinely are independent: a
 * refused write must leave the summary on screen, and a search probe run must leave both alone.
 * Collapsing them into a single sealed hierarchy would force one to erase another.
 */
data class PluginUiState(
    val scope: ScopeState = ScopeState.Loading,
    /** Which of the visible programs the write test and probes operate on. */
    val selectedProgram: MetadataItem? = null,
    val summary: SummaryState = SummaryState.Loading,
    val write: WriteState = WriteState.Idle,
    /** Which of the visible data sets the aggregate write test operates on. */
    val selectedDataSet: MetadataItem? = null,
    val dataSet: DataSetState = DataSetState.Loading,
    /** Outcome of the aggregate write, kept separate from the tracker one. */
    val dataValueWrite: WriteState = WriteState.Idle,
    val search: SearchState = SearchState.Idle,
)

/** The granted scope, next to what is actually visible. */
sealed interface ScopeState {
    data object Loading : ScopeState

    data class Loaded(val snapshot: ScopeSnapshot) : ScopeState

    data class Failed(val message: String) : ScopeState
}

/** The summary of the selected program. */
sealed interface SummaryState {
    data object Loading : SummaryState

    /**
     * The grant exposes no program to summarise.
     *
     * Distinct from [Failed]: nothing went wrong, the config simply granted nothing readable. Saying
     * that plainly is the difference between a diagnosable state and an empty card.
     */
    data object NoProgram : SummaryState

    data class Loaded(val summary: ProgramSummary) : SummaryState

    data class Failed(val message: String) : SummaryState
}

/**
 * Outcome of the write test.
 *
 * [Refused] is deliberately separate from [Failed]: a scope violation is the guard doing its job,
 * and rendering it as an error makes working software look broken.
 */
sealed interface WriteState {
    data object Idle : WriteState

    data object Writing : WriteState

    data class Succeeded(val eventUid: String) : WriteState

    data class Refused(val message: String) : WriteState

    data class Failed(val message: String) : WriteState
}

/** The summary of the selected data set — the aggregate half of the scope. */
sealed interface DataSetState {
    data object Loading : DataSetState

    /** The grant exposes no data set. Not an error: aggregate access is simply closed. */
    data object NoDataSet : DataSetState

    data class Loaded(val summary: DataSetSummary) : DataSetState

    data class Failed(val message: String) : DataSetState
}

/** Outcome of the tracker-search probe run. */
sealed interface SearchState {
    data object Idle : SearchState

    data object Running : SearchState

    data class Done(val run: SearchProbeRun) : SearchState

    /** Search itself was not granted — a valid result, not a malfunction. */
    data class Unavailable(val message: String) : SearchState
}
