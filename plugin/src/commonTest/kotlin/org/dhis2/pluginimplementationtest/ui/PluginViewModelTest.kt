package org.dhis2.pluginimplementationtest.ui

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.dhis2.pluginimplementationtest.model.DataSetSummary
import org.dhis2.pluginimplementationtest.model.DataValueTarget
import org.dhis2.pluginimplementationtest.model.DeclaredGrant
import org.dhis2.pluginimplementationtest.model.MetadataItem
import org.dhis2.pluginimplementationtest.model.ProgramSummary
import org.dhis2.pluginimplementationtest.model.ScopeDimension
import org.dhis2.pluginimplementationtest.model.ScopeSnapshot
import org.dhis2.pluginimplementationtest.model.SearchProbe
import org.dhis2.pluginimplementationtest.model.SearchProbeRun
import org.dhis2.pluginimplementationtest.model.WriteTarget
import org.dhis2.pluginimplementationtest.repository.PluginRepository
import org.dhis2.pluginimplementationtest.repository.ScopeViolation
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The ViewModel is the whole reason the repository is an interface: none of this could run without a
 * device if it depended on `ScopedD2`.
 *
 * Assertions are made on the **settled** state — `advanceUntilIdle()` then `state.value` — rather
 * than on a count of emissions. An earlier version used `skipItems(n)`, and adding a second
 * independent load (the aggregate half) silently invalidated seven tests at once. How many times the
 * state happens to be republished is not part of the contract; what it settles to is. Turbine is
 * used only where an intermediate transition is genuinely the thing under test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PluginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val childProgramme = MetadataItem("IpHINAT79UW", "Child Programme")
    private val antenatal = MetadataItem("lxAQ7Zs9VYR", "Antenatal care")
    private val childHealth = MetadataItem("BfMAe6Itzgt", "Child Health")

    private val writeTarget = WriteTarget(childProgramme.uid, "enrollment", "stage", "orgUnit")

    private val dataValueTarget = DataValueTarget(
        dataSetUid = childHealth.uid,
        dataElementUid = "s46m5MS0hxu",
        period = "202401",
        orgUnitUid = "DiszpKrYNg8",
        categoryOptionComboUid = "Prlt0C1RF0s",
        attributeOptionComboUid = "HllvX50cXC0",
        currentValue = "12",
    )

    private val probeRun = SearchProbeRun(
        baseline = 2,
        probes = listOf(SearchProbe("Granted program", "baseline", 2, SearchProbe.Expectation.INFORMATIONAL)),
    )

    private fun snapshot(programs: List<MetadataItem>, dataSets: List<MetadataItem>) = ScopeSnapshot(
        capabilities = listOf("READ_METADATA"),
        dimensions = listOf(
            ScopeDimension(ScopeSnapshot.PROGRAMS, DeclaredGrant.Uids(programs.map { it.uid }), programs),
            ScopeDimension(ScopeSnapshot.DATA_SETS, DeclaredGrant.Uids(dataSets.map { it.uid }), dataSets),
        ),
    )

    private fun summaryOf(uid: String) =
        ProgramSummary("Name of $uid", uid, enrolledCount = 1, recent = emptyList())

    private fun dataSetSummaryOf(uid: String) =
        DataSetSummary(uid, "Name of $uid", dataElementCount = 4, dataValueCount = 9, writeTarget = dataValueTarget)

    /** A fake rather than a mock: it is our own interface, so this reads better than stubbing. */
    private class FakeRepository(
        var scopeResult: Result<ScopeSnapshot>,
        var summaryFor: (String) -> Result<ProgramSummary>,
        var dataSetSummaryFor: (String) -> Result<DataSetSummary>,
        var writeResult: Result<String> = Result.success("newEvent"),
        var probeResult: Result<SearchProbeRun> = Result.failure(IllegalStateException("not set")),
        var dataValueWriteResult: Result<String> = Result.success("13"),
    ) : PluginRepository {
        val summaryRequests = mutableListOf<String>()
        val dataSetRequests = mutableListOf<String>()
        var probedProgram: String? = null

        override suspend fun loadScope() = scopeResult

        override suspend fun loadSummary(programUid: String): Result<ProgramSummary> {
            summaryRequests += programUid
            return summaryFor(programUid)
        }

        override suspend fun addEvent(target: WriteTarget) = writeResult

        override suspend fun probeSearch(programUid: String): Result<SearchProbeRun> {
            probedProgram = programUid
            return probeResult
        }

        override suspend fun loadDataSetSummary(dataSetUid: String): Result<DataSetSummary> {
            dataSetRequests += dataSetUid
            return dataSetSummaryFor(dataSetUid)
        }

        override suspend fun writeDataValue(target: DataValueTarget) = dataValueWriteResult
    }

    private fun repository(
        programs: List<MetadataItem> = listOf(childProgramme, antenatal),
        dataSets: List<MetadataItem> = listOf(childHealth),
        writeResult: Result<String> = Result.success("newEvent"),
        probeResult: Result<SearchProbeRun> = Result.failure(IllegalStateException("not set")),
        dataValueWriteResult: Result<String> = Result.success("13"),
    ) = FakeRepository(
        scopeResult = Result.success(snapshot(programs, dataSets)),
        summaryFor = { Result.success(summaryOf(it)) },
        dataSetSummaryFor = { Result.success(dataSetSummaryOf(it)) },
        writeResult = writeResult,
        probeResult = probeResult,
        dataValueWriteResult = dataValueWriteResult,
    )

    /** Builds the ViewModel and lets its initial loads finish, which every test needs first. */
    private fun TestScope.settled(repository: PluginRepository): PluginViewModel =
        PluginViewModel(repository).also { advanceUntilIdle() }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // ── Scope ────────────────────────────────────────────────────────────────

    @Test
    fun `load the granted scope on creation`() = runTest(dispatcher) {
        val state = settled(repository()).state.value

        assertEquals(
            snapshot(listOf(childProgramme, antenatal), listOf(childHealth)),
            assertIs<ScopeState.Loaded>(state.scope).snapshot,
        )
    }

    @Test
    fun `render a failed scope rather than throwing`() = runTest(dispatcher) {
        val repository = repository()
        repository.scopeResult = Result.failure(ScopeViolation("[SCOPE_VIOLATION] no metadata"))

        val state = settled(repository).state.value

        assertEquals("[SCOPE_VIOLATION] no metadata", assertIs<ScopeState.Failed>(state.scope).message)
    }

    // ── Tracker half ─────────────────────────────────────────────────────────

    @Test
    fun `select the first visible program so there is something to look at`() = runTest(dispatcher) {
        val repository = repository()

        val state = settled(repository).state.value

        assertEquals(childProgramme, state.selectedProgram)
        // Nothing hardcoded: fetched for whatever the grant actually exposed.
        assertEquals(listOf(childProgramme.uid), repository.summaryRequests)
    }

    @Test
    fun `say so plainly when the grant exposes no program`() = runTest(dispatcher) {
        val repository = repository(programs = emptyList())

        val state = settled(repository).state.value

        assertNull(state.selectedProgram)
        assertIs<SummaryState.NoProgram>(state.summary)
        // An empty grant must not send us querying a program we were never given.
        assertEquals(emptyList(), repository.summaryRequests)
    }

    @Test
    fun `load the summary of a newly selected program`() = runTest(dispatcher) {
        val repository = repository()
        val viewModel = settled(repository)

        viewModel.selectProgram(antenatal)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(antenatal, state.selectedProgram)
        assertEquals(antenatal.uid, assertIs<SummaryState.Loaded>(state.summary).summary.programUid)
        assertEquals(listOf(childProgramme.uid, antenatal.uid), repository.summaryRequests)
    }

    @Test
    fun `render a failed summary rather than throwing`() = runTest(dispatcher) {
        val repository = repository()
        repository.summaryFor = { Result.failure(ScopeViolation("[SCOPE_VIOLATION] nope")) }

        val state = settled(repository).state.value

        assertEquals("[SCOPE_VIOLATION] nope", assertIs<SummaryState.Failed>(state.summary).message)
    }

    @Test
    fun `reload the selected program after a permitted write, so the new event is visible`() = runTest(dispatcher) {
        val repository = repository()
        val viewModel = settled(repository)

        viewModel.addEvent(writeTarget)
        advanceUntilIdle()

        assertEquals("newEvent", assertIs<WriteState.Succeeded>(viewModel.state.value.write).eventUid)
        // Proof the write landed rather than merely being permitted.
        assertEquals(listOf(childProgramme.uid, childProgramme.uid), repository.summaryRequests)
    }

    @Test
    fun `pass through Writing on the way to a result`() = runTest(dispatcher) {
        // The one place an intermediate state is the point, so this one uses Turbine.
        val repository = repository()
        val viewModel = settled(repository)

        viewModel.state.test {
            skipItems(1)
            viewModel.addEvent(writeTarget)

            assertIs<WriteState.Writing>(awaitItem().write)
            assertIs<WriteState.Succeeded>(awaitItem().write)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `keep the summary on screen when a write is refused`() = runTest(dispatcher) {
        val repository = repository(writeResult = Result.failure(ScopeViolation("not your org unit")))
        val viewModel = settled(repository)

        viewModel.addEvent(writeTarget)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("not your org unit", assertIs<WriteState.Refused>(state.write).message)
        // A refusal is not a page-level failure: the summary stays.
        assertIs<SummaryState.Loaded>(state.summary)
        // Nothing changed, so nothing is re-queried.
        assertEquals(listOf(childProgramme.uid), repository.summaryRequests)
    }

    @Test
    fun `distinguish a genuine write failure from a refusal`() = runTest(dispatcher) {
        val repository = repository(writeResult = Result.failure(IllegalStateException("database is on fire")))
        val viewModel = settled(repository)

        viewModel.addEvent(writeTarget)
        advanceUntilIdle()

        assertIs<WriteState.Failed>(viewModel.state.value.write)
    }

    // ── Search probes ────────────────────────────────────────────────────────

    @Test
    fun `probe the selected program, not a hardcoded one`() = runTest(dispatcher) {
        val repository = repository(probeResult = Result.success(probeRun))
        val viewModel = settled(repository)

        viewModel.selectProgram(antenatal)
        advanceUntilIdle()
        viewModel.probeSearch()
        advanceUntilIdle()

        assertEquals(probeRun, assertIs<SearchState.Done>(viewModel.state.value.search).run)
        assertEquals(antenatal.uid, repository.probedProgram)
    }

    @Test
    fun `report search as unavailable when it is not granted`() = runTest(dispatcher) {
        val repository = repository(probeResult = Result.failure(ScopeViolation("SEARCH_TRACKED_ENTITY not granted")))
        val viewModel = settled(repository)

        viewModel.probeSearch()
        advanceUntilIdle()

        assertEquals(
            "SEARCH_TRACKED_ENTITY not granted",
            assertIs<SearchState.Unavailable>(viewModel.state.value.search).message,
        )
    }

    // ── Aggregate half ───────────────────────────────────────────────────────

    @Test
    fun `select the first visible data set alongside the first program`() = runTest(dispatcher) {
        // The two axes are independent: a grant can expose data sets and no programs, or the
        // reverse, so neither selection may depend on the other.
        val repository = repository()

        val state = settled(repository).state.value

        assertEquals(childHealth, state.selectedDataSet)
        assertEquals(childHealth.uid, assertIs<DataSetState.Loaded>(state.dataSet).summary.dataSetUid)
        assertEquals(listOf(childHealth.uid), repository.dataSetRequests)
    }

    @Test
    fun `say so plainly when the grant exposes no data set`() = runTest(dispatcher) {
        val repository = repository(dataSets = emptyList())

        val state = settled(repository).state.value

        assertNull(state.selectedDataSet)
        assertIs<DataSetState.NoDataSet>(state.dataSet)
        // Aggregate access being closed must not send us querying a data set we never got.
        assertEquals(emptyList(), repository.dataSetRequests)
    }

    @Test
    fun `expose the aggregate half even when no program is granted`() = runTest(dispatcher) {
        // A data-set-only grant is a legitimate configuration and must still be usable.
        val repository = repository(programs = emptyList())

        val state = settled(repository).state.value

        assertIs<SummaryState.NoProgram>(state.summary)
        assertIs<DataSetState.Loaded>(state.dataSet)
    }

    @Test
    fun `load the summary of a newly selected data set`() = runTest(dispatcher) {
        val morbidity = MetadataItem("Lpw6GcnTrmS", "Morbidity")
        val repository = repository(dataSets = listOf(childHealth, morbidity))
        val viewModel = settled(repository)

        viewModel.selectDataSet(morbidity)
        advanceUntilIdle()

        assertEquals(morbidity, viewModel.state.value.selectedDataSet)
        assertEquals(listOf(childHealth.uid, morbidity.uid), repository.dataSetRequests)
    }

    @Test
    fun `reload the data set after a permitted value write`() = runTest(dispatcher) {
        val repository = repository()
        val viewModel = settled(repository)

        viewModel.writeDataValue(dataValueTarget)
        advanceUntilIdle()

        assertEquals("13", assertIs<WriteState.Succeeded>(viewModel.state.value.dataValueWrite).eventUid)
        assertEquals(listOf(childHealth.uid, childHealth.uid), repository.dataSetRequests)
    }

    @Test
    fun `keep the aggregate summary when a value write is refused`() = runTest(dispatcher) {
        val repository = repository(
            dataValueWriteResult = Result.failure(ScopeViolation("not a writable data element")),
        )
        val viewModel = settled(repository)

        viewModel.writeDataValue(dataValueTarget)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("not a writable data element", assertIs<WriteState.Refused>(state.dataValueWrite).message)
        assertIs<DataSetState.Loaded>(state.dataSet)
        assertEquals(listOf(childHealth.uid), repository.dataSetRequests)
    }

    @Test
    fun `keep the tracker and aggregate write outcomes apart`() = runTest(dispatcher) {
        // One refusal must not make the other look refused: they are different grants and a reader
        // needs to see which one failed.
        val repository = repository(dataValueWriteResult = Result.failure(ScopeViolation("aggregate refused")))
        val viewModel = settled(repository)

        viewModel.writeDataValue(dataValueTarget)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertIs<WriteState.Refused>(state.dataValueWrite)
        assertIs<WriteState.Idle>(state.write)
    }
}
