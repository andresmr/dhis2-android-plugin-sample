package org.dhis2.pluginimplementationtest.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.dhis2.pluginimplementationtest.model.DataValueTarget
import org.dhis2.pluginimplementationtest.model.MetadataItem
import org.dhis2.pluginimplementationtest.model.WriteTarget
import org.dhis2.pluginimplementationtest.repository.PluginRepository
import org.dhis2.pluginimplementationtest.repository.ScopeViolation

/**
 * Holds the plugin's state and nothing else.
 *
 * No SDK types reach here — [PluginRepository] speaks in plain models — so this whole class is
 * `commonMain` and testable on the JVM with a fake.
 */
class PluginViewModel(
    private val repository: PluginRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PluginUiState())
    val state: StateFlow<PluginUiState> = _state.asStateFlow()

    init {
        loadScope()
    }

    /** Switches which data set the aggregate summary and value write operate on. */
    fun selectDataSet(dataSet: MetadataItem) {
        _state.update { it.copy(selectedDataSet = dataSet, dataSet = DataSetState.Loading) }
        loadDataSet(dataSet.uid)
    }

    /**
     * Overwrites one data value.
     *
     * Deliberately a separate outcome from [addEvent]: the tracker and aggregate grants are
     * independent, and a reader needs to see which of the two refused.
     */
    fun writeDataValue(target: DataValueTarget) {
        viewModelScope.launch {
            _state.update { it.copy(dataValueWrite = WriteState.Writing) }

            val outcome = repository.writeDataValue(target).fold(
                onSuccess = { WriteState.Succeeded(it) },
                onFailure = { error ->
                    when (error) {
                        is ScopeViolation -> WriteState.Refused(error.message)
                        else -> WriteState.Failed(error.describe())
                    }
                },
            )
            _state.update { it.copy(dataValueWrite = outcome) }

            if (outcome is WriteState.Succeeded) {
                _state.value.selectedDataSet?.let { loadDataSet(it.uid) }
            }
        }
    }

    /** Switches which program the summary, write test and probes operate on. */
    fun selectProgram(program: MetadataItem) {
        _state.update { it.copy(selectedProgram = program, summary = SummaryState.Loading) }
        loadSummary(program.uid)
    }

    fun addEvent(target: WriteTarget) {
        viewModelScope.launch {
            _state.update { it.copy(write = WriteState.Writing) }

            val outcome = repository.addEvent(target).fold(
                onSuccess = { WriteState.Succeeded(it) },
                onFailure = { error ->
                    // A refusal is the guard working; only anything else is a failure.
                    when (error) {
                        is ScopeViolation -> WriteState.Refused(error.message)
                        else -> WriteState.Failed(error.describe())
                    }
                },
            )
            _state.update { it.copy(write = outcome) }

            // Reload only when something actually changed, so a refusal costs no query.
            if (outcome is WriteState.Succeeded) {
                _state.value.selectedProgram?.let { loadSummary(it.uid) }
            }
        }
    }

    fun probeSearch() {
        val program = _state.value.selectedProgram ?: return

        viewModelScope.launch {
            _state.update { it.copy(search = SearchState.Running) }

            val outcome = repository.probeSearch(program.uid).fold(
                onSuccess = { SearchState.Done(it) },
                onFailure = { SearchState.Unavailable(it.describe()) },
            )
            _state.update { it.copy(search = outcome) }
        }
    }

    private fun loadScope() {
        viewModelScope.launch {
            repository.loadScope().fold(
                onSuccess = { snapshot ->
                    // Default to the first program the grant actually exposed, so the card shows
                    // something without the reader having to pick. If the grant exposed none, say so
                    // rather than querying a program we were never given.
                    val firstProgram = snapshot.visiblePrograms.firstOrNull()
                    val firstDataSet = snapshot.visibleDataSets.firstOrNull()
                    _state.update {
                        it.copy(
                            scope = ScopeState.Loaded(snapshot),
                            selectedProgram = firstProgram,
                            summary = if (firstProgram == null) SummaryState.NoProgram else SummaryState.Loading,
                            selectedDataSet = firstDataSet,
                            dataSet = if (firstDataSet == null) DataSetState.NoDataSet else DataSetState.Loading,
                        )
                    }
                    firstProgram?.let { loadSummary(it.uid) }
                    firstDataSet?.let { loadDataSet(it.uid) }
                },
                onFailure = { error ->
                    _state.update { it.copy(scope = ScopeState.Failed(error.describe())) }
                },
            )
        }
    }

    private fun loadDataSet(dataSetUid: String) {
        viewModelScope.launch {
            val outcome = repository.loadDataSetSummary(dataSetUid).fold(
                onSuccess = { DataSetState.Loaded(it) },
                onFailure = { DataSetState.Failed(it.describe()) },
            )
            _state.update { it.copy(dataSet = outcome) }
        }
    }

    private fun loadSummary(programUid: String) {
        viewModelScope.launch {
            val outcome = repository.loadSummary(programUid).fold(
                onSuccess = { SummaryState.Loaded(it) },
                onFailure = { SummaryState.Failed(it.describe()) },
            )
            _state.update { it.copy(summary = outcome) }
        }
    }
}

/** Falls back to the type name, because not every exception carries a message. */
private fun Throwable.describe(): String = message ?: this::class.simpleName ?: "unknown error"
