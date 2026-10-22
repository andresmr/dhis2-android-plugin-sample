package org.dhis2.stock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.dhis2.stock.model.DataSetSection
import org.dhis2.stock.model.periodLabelOf
import org.dhis2.stock.repository.PluginRepository

/**
 * Holds the plugin's state, and nothing else.
 *
 * No SDK type reaches here — it takes a [PluginRepository], which is an interface — so the whole
 * class is `commonMain` and every test of it runs on the JVM in milliseconds against a fake.
 */
class PluginViewModel(
    private val repository: PluginRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PluginUiState())
    val state: StateFlow<PluginUiState> = _state.asStateFlow()

    /**
     * Which instance the host opened.
     *
     * Called by the slot rather than run from `init`, because `init` has no arguments to load from.
     * The period is set immediately and synchronously: it comes from the host's own arguments, not
     * from a read, so there is nothing to wait for and nothing that can make it fail.
     */
    fun open(dataSetUid: String, periodId: String) {
        _state.update { it.copy(period = periodLabelOf(periodId)) }

        viewModelScope.launch {
            val sections = repository.loadSections(dataSetUid).fold(
                onSuccess = { it.inDisplayOrder() },
                onFailure = { return@launch failWith(it.describe()) },
            )

            _state.update {
                it.copy(
                    sections = SectionsState.Loaded(
                        sections = sections,
                        selectedSectionUid = sections.firstOrNull()?.uid,
                    ),
                )
            }
        }
    }

    /** Moves the selection, leaving the sections themselves alone. */
    fun selectSection(sectionUid: String) {
        _state.update { state ->
            val loaded = state.sections as? SectionsState.Loaded ?: return@update state
            state.copy(sections = loaded.copy(selectedSectionUid = sectionUid))
        }
    }

    private fun failWith(message: String) {
        _state.update { it.copy(sections = SectionsState.Failed(message)) }
    }
}

/**
 * The order the user should see: `sortOrder` ascending, with sections the server never ordered last.
 *
 * `sortOrder` is nullable in the SDK, and the two obvious shortcuts are both wrong. Sorting a null
 * as zero puts an unordered section in front of every ordered one; filtering it out makes a section
 * the administrator configured disappear from a screen that is meant to list them all.
 */
private fun List<DataSetSection>.inDisplayOrder(): List<DataSetSection> =
    sortedWith(compareBy(nullsLast()) { it.sortOrder })

/**
 * A message worth showing a human.
 *
 * `D2Error` is a data class that passes nothing to the `Exception` constructor, so its `message` is
 * **always null** — the repository translates it into something readable before it gets here, and
 * this fallback covers everything else.
 */
private fun Throwable.describe(): String =
    message ?: this::class.simpleName ?: "unknown error"
