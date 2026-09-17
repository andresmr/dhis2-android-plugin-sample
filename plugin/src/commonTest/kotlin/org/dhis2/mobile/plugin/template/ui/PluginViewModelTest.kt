package org.dhis2.mobile.plugin.template.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.dhis2.mobile.plugin.template.model.PluginSummary
import org.dhis2.mobile.plugin.template.repository.PluginRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The ViewModel is the whole reason [PluginRepository] is an interface: none of this could run
 * without a device if it depended on `D2`.
 *
 * Assertions are on the *settled* state — `advanceUntilIdle()` then `state.value` — never on a
 * count of emissions. How often the state is republished is not part of the contract, and tests
 * coupled to it all break together the moment anything else in the load path changes.
 *
 * Each test names the scenario it asserts, as `spec: <slug> <id>`. `tools/check-specs.py` fails the
 * build when a logic scenario has no such claim, so these comments are load-bearing rather than
 * decorative.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PluginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** A fake rather than a mock: it is our own interface, so this reads better than stubbing. */
    private class FakeRepository(private val result: Result<PluginSummary>) : PluginRepository {
        var requests = 0

        override suspend fun loadSummary(): Result<PluginSummary> {
            requests++
            return result
        }
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // spec: first-card L1
    @Test
    fun `report the summary the repository returns`() = runTest(dispatcher) {
        val repository = FakeRepository(Result.success(PluginSummary(programCount = 4)))

        val viewModel = PluginViewModel(repository)
        advanceUntilIdle()

        val loaded = assertIs<SummaryState.Loaded>(viewModel.state.value.summary)
        assertEquals(4, loaded.summary.programCount)
        assertEquals(1, repository.requests)
    }

    // spec: first-card L2
    @Test
    fun `render a failed read rather than throwing`() = runTest(dispatcher) {
        // A throw escaping here would take the host's whole screen with it, which is why the
        // repository returns Result. This state is a real code path, not decoration.
        val repository = FakeRepository(Result.failure(IllegalStateException("No database")))

        val viewModel = PluginViewModel(repository)
        advanceUntilIdle()

        val failed = assertIs<SummaryState.Failed>(viewModel.state.value.summary)
        assertEquals("No database", failed.message)
    }
}
