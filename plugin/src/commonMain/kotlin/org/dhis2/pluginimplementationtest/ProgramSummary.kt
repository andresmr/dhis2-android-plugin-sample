package org.dhis2.pluginimplementationtest

/**
 * What the card renders, as plain data.
 *
 * Kept deliberately free of SDK types and in `commonMain`, so the card can be rendered from a
 * `@Preview` or a harness app without a `Dhis2PluginContext` — which matters now that the context
 * hands out a real `ScopedD2` that cannot be faked. Fetching lives in `MyPlugin`; rendering only
 * ever sees this.
 */
data class ProgramSummary(
    /** The program's own display name, read from metadata rather than hardcoded. */
    val programName: String,
    val programUid: String,
    /** Enrolled tracked entities counted in SQL, not by materialising the list. */
    val enrolledCount: Int,
    val recent: List<EnrolledPerson>,
    /**
     * Events visible in this program, or null when `READ_EVENT` was not granted.
     *
     * Null rather than zero on purpose: "the grant does not let me look" and "I looked and found
     * none" are different answers, and conflating them is exactly the mistake the scoping model is
     * meant to make visible.
     */
    val eventCount: Int? = null,
    /** What the write button would write, or null when nothing writable could be resolved. */
    val writeTarget: WriteTarget? = null,
)

/**
 * The event the write test would create.
 *
 * Resolved from readable data, but every field here is one the write guard checks against the
 * *writable* grant — so a target existing does not imply the write will be permitted. That gap is
 * the point of the test: [ScopedAccessGuard][org.hisp.dhis.android.core.scopedaccess] validates the
 * object, not the query that found it.
 */
data class WriteTarget(
    val enrollmentUid: String,
    val programStageUid: String,
    /** The enrollment's own org unit — the value the guard checks, not one the plugin chose. */
    val orgUnitUid: String,
)

/** One tracked entity, with its attributes already resolved to human-readable labels. */
data class EnrolledPerson(
    val uid: String,
    val attributes: List<LabelledValue>,
)

/**
 * An attribute value and the label to show for it.
 *
 * The label is the point: the previous DTO API handed back a `Map<String, String>` keyed by raw
 * attribute UID, so a plugin could show values but never say what they were.
 */
data class LabelledValue(
    val label: String,
    val value: String,
)

/**
 * Outcome of the write test, kept separate from [SummaryState] so a refused write leaves the
 * summary on screen instead of replacing it with an error.
 */
sealed interface WriteState {
    data object Idle : WriteState

    data object Writing : WriteState

    /** The SDK permitted the write and the store returned this event UID. */
    data class Succeeded(val eventUid: String) : WriteState

    /** `D2ErrorCode.SCOPE_VIOLATION` — the write guard vetoed the object. */
    data class Refused(val message: String) : WriteState

    /** Anything else: a malformed target, a missing stage, a store failure. */
    data class Failed(val message: String) : WriteState
}

/** Loading state for [ProgramSummaryCard]. */
sealed interface SummaryState {
    data object Loading : SummaryState

    data class Loaded(val summary: ProgramSummary) : SummaryState

    data class Failed(val message: String) : SummaryState
}
