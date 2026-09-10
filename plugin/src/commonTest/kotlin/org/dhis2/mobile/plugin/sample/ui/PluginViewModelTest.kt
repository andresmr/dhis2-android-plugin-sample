package org.dhis2.mobile.plugin.sample.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.dhis2.mobile.plugin.sample.model.EnrolledPerson
import org.dhis2.mobile.plugin.sample.model.MAX_LISTED_PEOPLE
import org.dhis2.mobile.plugin.sample.model.ProgramSummary
import org.dhis2.mobile.plugin.sample.repository.PluginRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The ViewModel is the whole reason [PluginRepository] is an interface: none of this could run
 * without a device if it depended on `D2`.
 *
 * Assertions are on the **settled** state — `advanceUntilIdle()` then `state.value` — rather than on
 * a count of emissions. How many times the state happens to be republished is not part of the
 * contract; what it settles to is.
 *
 * Each test names the scenario it asserts, as `spec: <slug> <id>`. `tools/check-specs.py` fails the
 * build when a logic scenario in `specs/` has no such claim, so this comment is load-bearing rather
 * than decorative — and the numbers below come from the spec, not from whatever was convenient.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PluginViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val programUid = "resolved-programme"
    /** The counts are the spec's: "36 enrolled and 71 events". */
    private fun summary(
        recent: List<EnrolledPerson> = emptyList(),
    ) = ProgramSummary(
        programUid = programUid,
        programName = "Child Programme",
        enrolledCount = 36,
        eventCount = 71,
        recent = recent,
    )

    private fun person(name: String) = EnrolledPerson(uid = "tei-$name", displayLabel = name)

    /** A fake rather than a mock: it is our own interface, so this reads better than stubbing. */
    private class FakeRepository(
        var summaryResult: Result<ProgramSummary>,
    ) : PluginRepository {
        var summaryRequests = 0

        override suspend fun loadSummary(): Result<ProgramSummary> {
            summaryRequests++
            return summaryResult
        }
    }

    private fun TestScope.settled(repository: PluginRepository) =
        PluginViewModel(repository).also { advanceUntilIdle() }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // spec: example-program-summary L1
    @Test
    fun `load the summary of the programme the repository resolves`() = runTest(dispatcher) {
        val repository = FakeRepository(Result.success(summary()))

        val state = settled(repository).state.value

        val loaded = assertIs<SummaryState.Loaded>(state.summary).summary
        assertEquals("Child Programme", loaded.programName)
        assertEquals(36, loaded.enrolledCount)
        assertEquals(71, loaded.eventCount)
        // Nothing above the repository names a programme: the state reports which one was resolved.
        assertEquals(programUid, loaded.programUid)
        assertEquals(1, repository.summaryRequests)
    }

    // spec: example-program-summary L2
    @Test
    fun `carry the recent people, with their attribute labels`() = runTest(dispatcher) {
        val people = listOf(person("Filona Ryder"), person("Gertrude Fjordsen"), person("Frank Fjordsen"))
        val repository = FakeRepository(Result.success(summary(recent = people)))

        val state = settled(repository).state.value

        val loaded = assertIs<SummaryState.Loaded>(state.summary).summary
        // In the programme's order, not the order the SDK returned values in.
        assertEquals(people, loaded.recent)
        assertEquals("Filona Ryder", loaded.recent.first().displayLabel)
        // The defect this exists to prevent: a UID must never reach a human.
        loaded.recent.forEach { assertTrue(it.uid !in it.displayLabel) }
    }

    // spec: example-program-summary L3
    @Test
    fun `carry no people when there are none`() = runTest(dispatcher) {
        val repository = FakeRepository(Result.success(summary(recent = emptyList())))

        val state = settled(repository).state.value

        assertTrue(assertIs<SummaryState.Loaded>(state.summary).summary.recent.isEmpty())
    }

    // spec: example-program-summary L4
    @Test
    fun `render a failed read rather than throwing`() = runTest(dispatcher) {
        // A throw escaping here would take the host's whole screen down, so the repository returns
        // Result and this state is a real code path rather than decoration.
        val repository = FakeRepository(Result.failure(IllegalStateException("Program not found")))

        val state = settled(repository).state.value

        assertEquals("Program not found", assertIs<SummaryState.Failed>(state.summary).message)
    }




    // spec: example-program-summary L5
    @Test
    fun `cap the listed people at the shared display budget`() = runTest(dispatcher) {
        val crowd = List(5) { person("person-$it") }
        val repository = FakeRepository(Result.success(summary(recent = crowd)))

        val state = settled(repository).state.value

        // The promise is to the host, whose column does not scroll — so it is kept here, where a
        // fake repository can hand over more rows than the card may show. Capping only in the
        // repository, behind a D2 no test can build, is how this went unenforced.
        val listed = assertIs<SummaryState.Loaded>(state.summary).summary.recent
        assertEquals(MAX_LISTED_PEOPLE, listed.size)
        assertEquals(crowd.take(MAX_LISTED_PEOPLE), listed)
    }

    // spec: example-program-summary L6
    @Test
    fun `carry every person when there are fewer than the budget`() = runTest(dispatcher) {
        val few = List(2) { person("person-$it") }
        val repository = FakeRepository(Result.success(summary(recent = few)))

        val state = settled(repository).state.value

        assertEquals(few, assertIs<SummaryState.Loaded>(state.summary).summary.recent)
    }

}
