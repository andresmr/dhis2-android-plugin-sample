package org.dhis2.datasetsections.ui

import org.dhis2.datasetsections.model.DataSetSection
import org.dhis2.datasetsections.model.PeriodLabel

/**
 * Everything the plugin's UI renders, in one place.
 *
 * A field per concern rather than one flat bag, so a failed read leaves the rest of the screen
 * intact — and so a spec's `Then` has something specific to name.
 *
 * That split earns its keep here: [period] comes from the arguments the host handed over, so it is
 * known before any read starts and is still known when the read fails. Folding it into a state that
 * goes `Failed` would hide the one thing this screen is most responsible for showing.
 */
data class PluginUiState(
    val period: PeriodLabel? = null,
    val sections: SectionsState = SectionsState.Loading,
)

/** The data set's sections, and which one the user is on. */
sealed interface SectionsState {
    data object Loading : SectionsState

    /**
     * [sections] is in the order the user should see: `sortOrder` ascending, unordered last.
     *
     * [selectedSectionUid] is null only when there are no sections at all — a data set without
     * sections is an ordinary data set, not a failure, and it renders as the period alone.
     */
    data class Loaded(
        val sections: List<DataSetSection>,
        val selectedSectionUid: String?,
    ) : SectionsState

    data class Failed(val message: String) : SectionsState
}
