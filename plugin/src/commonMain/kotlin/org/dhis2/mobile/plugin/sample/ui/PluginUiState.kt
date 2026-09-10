package org.dhis2.mobile.plugin.sample.ui

import org.dhis2.mobile.plugin.sample.model.ProgramSummary

/** Everything the plugin's UI renders, in one place. */
data class PluginUiState(
    val summary: SummaryState = SummaryState.Loading,
)

sealed interface SummaryState {
    data object Loading : SummaryState

    data class Loaded(val summary: ProgramSummary) : SummaryState

    data class Failed(val message: String) : SummaryState
}

