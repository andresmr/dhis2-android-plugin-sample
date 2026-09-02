package org.dhis2.mobile.plugin.sample.ui

import org.dhis2.mobile.plugin.sample.model.ProgramSummary

/**
 * Everything the plugin's UI renders, in one place.
 *
 * The summary and the write outcome are separate on purpose: a failed write must leave the summary
 * on screen rather than replacing it.
 */
data class PluginUiState(
    val summary: SummaryState = SummaryState.Loading,
    val write: WriteState = WriteState.Idle,
)

sealed interface SummaryState {
    data object Loading : SummaryState

    data class Loaded(val summary: ProgramSummary) : SummaryState

    data class Failed(val message: String) : SummaryState
}

sealed interface WriteState {
    data object Idle : WriteState

    data object Writing : WriteState

    data class Succeeded(val eventUid: String) : WriteState

    data class Failed(val message: String) : WriteState
}
