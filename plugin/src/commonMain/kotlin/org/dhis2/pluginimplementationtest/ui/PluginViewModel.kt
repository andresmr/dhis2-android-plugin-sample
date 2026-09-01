package org.dhis2.pluginimplementationtest.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.dhis2.pluginimplementationtest.model.WriteTarget
import org.dhis2.pluginimplementationtest.repository.PluginRepository

/**
 * Holds the plugin's state and nothing else.
 *
 * No SDK types reach here — [PluginRepository] speaks in plain models — so this whole class is
 * `commonMain` and testable on the JVM with a fake.
 *
 * Safe across a plugin reload: the host's `ViewModelStore` outlives the plugin's composition and
 * keys by class name, which is identical across two class loaders, but `ViewModelProvider`
 * type-checks the cached instance and replaces it on a mismatch. No explicit key is needed.
 */
class PluginViewModel(
    private val programUid: String,
    private val repository: PluginRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PluginUiState())
    val state: StateFlow<PluginUiState> = _state.asStateFlow()

    init {
        loadSummary()
    }

    fun addEvent(target: WriteTarget) {
        viewModelScope.launch {
            _state.update { it.copy(write = WriteState.Writing) }

            val outcome = repository.addEvent(target).fold(
                onSuccess = { WriteState.Succeeded(it) },
                onFailure = { WriteState.Failed(it.describe()) },
            )
            _state.update { it.copy(write = outcome) }

            // Reload only when something actually changed, so a failure costs no query.
            if (outcome is WriteState.Succeeded) loadSummary()
        }
    }

    private fun loadSummary() {
        viewModelScope.launch {
            val outcome = repository.loadSummary(programUid).fold(
                onSuccess = { SummaryState.Loaded(it) },
                onFailure = { SummaryState.Failed(it.describe()) },
            )
            _state.update { it.copy(summary = outcome) }
        }
    }
}

/**
 * A message worth showing a human.
 *
 * `D2Error` is `data class D2Error(…) : Exception()` and passes nothing to the `Exception`
 * constructor, so `Throwable.message` is **always null** for it — the whole diagnostic lives in
 * `errorCode()`/`errorDescription()`. The repository translates that before it reaches here; this
 * fallback is for everything else.
 */
private fun Throwable.describe(): String = message ?: this::class.simpleName ?: "unknown error"
