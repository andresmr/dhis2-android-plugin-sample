package org.dhis2.mobile.plugin.sample.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.dhis2.mobile.plugin.sample.model.ProgramSummary
import org.dhis2.mobile.plugin.sample.model.WriteTarget
import org.dhis2.mobile.plugin.sample.repository.PluginRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The ViewModel is the whole reason [PluginRepository] is an interface: none of this could run
 * without a device if it depended on `D2`.
 *
 * Assertions are on the **settled** state — `advanceUntilIdle()` then `state.value` — rather than on
 * a count of emissions. How many times the state happens to be republished is not part of the
 * contract; what it settles to is.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PluginViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val programUid = "IpHINAT79UW"
    private val target = WriteTarget(programUid, "enrollment", "stage", "orgUnit")

    private fun summary() = ProgramSummary(
        programUid = programUid,
        programName = "Child Programme",
        enrolledCount = 3,
        eventCount = 7,
        recent = emptyList(),
        writeTarget = target,
    )

    /** A fake rather than a mock: it is our own interface, so this reads better than stubbing. */
    private class FakeRepository(
        var summaryResult: Result<ProgramSummary>,
        var writeResult: Result<String> = Result.success("newEvent"),
    ) : PluginRepository {
        val summaryRequests = mutableListOf<String>()

        override suspend fun loadSummary(programUid: String): Result<ProgramSummary> {
            summaryRequests += programUid
            return summaryResult
        }

        override suspend fun addEvent(target: WriteTarget) = writeResult
    }

    private fun TestScope.settled(repository: PluginRepository) =
        PluginViewModel(programUid, repository).also { advanceUntilIdle() }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load the summary of the configured program on creation`() = runTest(dispatcher) {
        val repository = FakeRepository(Result.success(summary()))

        val state = settled(repository).state.value

        assertEquals("Child Programme", assertIs<SummaryState.Loaded>(state.summary).summary.programName)
        assertEquals(listOf(programUid), repository.summaryRequests)
    }

    @Test
    fun `render a failed read rather than throwing`() = runTest(dispatcher) {
        // A throw escaping here would take the host's whole screen down, so the repository returns
        // Result and this state is a real code path rather than decoration.
        val repository = FakeRepository(Result.failure(IllegalStateException("no such program")))

        val state = settled(repository).state.value

        assertEquals("no such program", assertIs<SummaryState.Failed>(state.summary).message)
    }

    @Test
    fun `reload the summary after a successful write, so the new event is visible`() = runTest(dispatcher) {
        val repository = FakeRepository(Result.success(summary()))
        val viewModel = settled(repository)

        viewModel.addEvent(target)
        advanceUntilIdle()

        assertEquals("newEvent", assertIs<WriteState.Succeeded>(viewModel.state.value.write).eventUid)
        // Proof the write landed rather than merely being accepted.
        assertEquals(listOf(programUid, programUid), repository.summaryRequests)
    }

    @Test
    fun `keep the summary on screen when a write fails`() = runTest(dispatcher) {
        val repository = FakeRepository(
            Result.success(summary()),
            writeResult = Result.failure(IllegalStateException("database is on fire")),
        )
        val viewModel = settled(repository)

        viewModel.addEvent(target)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertIs<WriteState.Failed>(state.write)
        // A failed write is not a page-level failure.
        assertIs<SummaryState.Loaded>(state.summary)
        // Nothing changed, so nothing is re-queried.
        assertEquals(listOf(programUid), repository.summaryRequests)
    }
}
