package org.dhis2.mobile.plugin.template.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.dhis2.mobile.plugin.template.repository.PluginRepository

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

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = PluginUiState(
                summary = repository.loadSummary().fold(
                    onSuccess = { SummaryState.Loaded(it) },
                    onFailure = { SummaryState.Failed(it.describe()) },
                ),
            )
        }
    }
}

/**
 * A message worth showing a human.
 *
 * `D2Error` is a data class that passes nothing to the `Exception` constructor, so its `message` is
 * **always null** — the repository translates it into something readable before it gets here, and
 * this fallback covers everything else.
 */
private fun Throwable.describe(): String =
    message ?: this::class.simpleName ?: "unknown error"
