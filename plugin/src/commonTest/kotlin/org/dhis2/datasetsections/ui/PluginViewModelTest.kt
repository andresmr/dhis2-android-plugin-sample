package org.dhis2.datasetsections.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.dhis2.datasetsections.model.DataSetSection
import org.dhis2.datasetsections.model.PeriodLabel
import org.dhis2.datasetsections.repository.PluginRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    private class FakeRepository(
        private val result: Result<List<DataSetSection>>,
    ) : PluginRepository {
        var requestedDataSetUid: String? = null

        override suspend fun loadSections(dataSetUid: String): Result<List<DataSetSection>> {
            requestedDataSetUid = dataSetUid
            return result
        }
    }

    private fun repositoryReturning(vararg sections: DataSetSection) =
        FakeRepository(Result.success(sections.toList()))

    /** Opens an instance and lets the load settle — the shape every test below starts from. */
    private fun TestScope.opened(
        repository: PluginRepository,
        periodId: String = "202401",
        dataSetUid: String = "BfMAe6Itzgt",
    ): PluginViewModel = PluginViewModel(repository).also {
        it.open(dataSetUid = dataSetUid, periodId = periodId)
        advanceUntilIdle()
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // spec: data-set-sections L1
    @Test
    fun `map each supported period id to its label`() = runTest(dispatcher) {
        fun periodFor(periodId: String): PeriodLabel? =
            opened(repositoryReturning(), periodId = periodId).state.value.period

        assertEquals(PeriodLabel.Day(2024, 1, 15), periodFor("20240115"))
        assertEquals(PeriodLabel.Week(2024, 3), periodFor("2024W3"))
        assertEquals(PeriodLabel.Month(2024, 1), periodFor("202401"))
        assertEquals(PeriodLabel.Quarter(2024, 1), periodFor("2024Q1"))
        assertEquals(PeriodLabel.Year(2024), periodFor("2024"))
    }

    // spec: data-set-sections L2
    @Test
    fun `fall back to the raw period id for an unsupported type`() = runTest(dispatcher) {
        fun periodFor(periodId: String): PeriodLabel? =
            opened(repositoryReturning(), periodId = periodId).state.value.period

        // Bi-monthly and a financial year. Both are near-misses for a supported pattern, which is
        // exactly why they are the two worth naming: `202402B` must not read as the month 202402,
        // and `2024April` must not read as the year 2024.
        assertEquals(PeriodLabel.Raw("202402B"), periodFor("202402B"))
        assertEquals(PeriodLabel.Raw("2024April"), periodFor("2024April"))
    }

    // spec: data-set-sections L3
    @Test
    fun `list the sections in the server's sort order, unordered last`() = runTest(dispatcher) {
        val repository = repositoryReturning(
            DataSetSection(uid = "sectionC", displayName = "Discarded", sortOrder = 3),
            DataSetSection(uid = "sectionX", displayName = "Unordered", sortOrder = null),
            DataSetSection(uid = "sectionA", displayName = "Stock on hand", sortOrder = 1),
        )

        val viewModel = opened(repository)

        val loaded = assertIs<SectionsState.Loaded>(viewModel.state.value.sections)
        assertEquals(
            listOf("Stock on hand", "Discarded", "Unordered"),
            loaded.sections.map { it.displayName },
        )
        assertEquals("BfMAe6Itzgt", repository.requestedDataSetUid)
    }

    // spec: data-set-sections L4
    @Test
    fun `a section-less data set is empty, not an error`() = runTest(dispatcher) {
        val viewModel = opened(repositoryReturning())

        val loaded = assertIs<SectionsState.Loaded>(viewModel.state.value.sections)
        assertTrue(loaded.sections.isEmpty())
        assertNull(loaded.selectedSectionUid)
        // The period is the whole screen in this case, so losing it would leave nothing at all.
        assertEquals(PeriodLabel.Month(2024, 1), viewModel.state.value.period)
    }

    // spec: data-set-sections L5
    @Test
    fun `select the first section in sort order by default`() = runTest(dispatcher) {
        val viewModel = opened(
            repositoryReturning(
                DataSetSection(uid = "sectionC", displayName = "Discarded", sortOrder = 3),
                DataSetSection(uid = "sectionA", displayName = "Stock on hand", sortOrder = 1),
            ),
        )

        val loaded = assertIs<SectionsState.Loaded>(viewModel.state.value.sections)
        // The first in sort order, not the first the repository happened to return.
        assertEquals("sectionA", loaded.selectedSectionUid)
    }

    // spec: data-set-sections L6
    @Test
    fun `selecting a section moves the selection and leaves the list alone`() = runTest(dispatcher) {
        val viewModel = opened(
            repositoryReturning(
                DataSetSection(uid = "sectionA", displayName = "Stock on hand", sortOrder = 1),
                DataSetSection(uid = "sectionB", displayName = "Issued", sortOrder = 2),
            ),
        )
        val before = assertIs<SectionsState.Loaded>(viewModel.state.value.sections).sections

        viewModel.selectSection("sectionB")
        advanceUntilIdle()

        val loaded = assertIs<SectionsState.Loaded>(viewModel.state.value.sections)
        assertEquals("sectionB", loaded.selectedSectionUid)
        assertEquals(before, loaded.sections)
    }

    // spec: data-set-sections L7
    @Test
    fun `render a failed read rather than throwing`() = runTest(dispatcher) {
        // A throw escaping here would take the host's whole screen with it, which is why the
        // repository returns Result. This state is a real code path, not decoration.
        val repository = FakeRepository(Result.failure(IllegalStateException("No database")))

        val viewModel = opened(repository)

        val failed = assertIs<SectionsState.Failed>(viewModel.state.value.sections)
        assertEquals("No database", failed.message)
        // The period came from the host's arguments, not from the read, so it survives the failure.
        assertEquals(PeriodLabel.Month(2024, 1), viewModel.state.value.period)
    }
}
