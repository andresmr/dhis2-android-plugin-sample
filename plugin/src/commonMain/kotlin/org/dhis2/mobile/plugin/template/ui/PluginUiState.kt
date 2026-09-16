package org.dhis2.mobile.plugin.template.ui

import org.dhis2.mobile.plugin.template.model.PluginSummary

/**
 * Everything the plugin's UI renders, in one place.
 *
 * A class per concern rather than one flat bag, so a failed read can leave the rest of the card
 * intact — and so a spec's `Then` has something specific to name. If a value the card derives while
 * rendering is worth asserting, put it here: that is what makes it a logic scenario instead of a
 * device one.
 */
data class PluginUiState(
    val summary: SummaryState = SummaryState.Loading,
)

sealed interface SummaryState {
    data object Loading : SummaryState
    data class Loaded(val summary: PluginSummary) : SummaryState
    data class Failed(val message: String) : SummaryState
}
