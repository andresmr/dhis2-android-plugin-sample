package org.dhis2.pluginimplementationtest.model

/**
 * What the plugin knows about one program, as plain data.
 *
 * Free of SDK types on purpose. Everything above the repository — state, ViewModel, Composables —
 * sees only this, which is what lets all of it live in `commonMain` and be tested without a device.
 */
data class ProgramSummary(
    /** The program's own display name, read from metadata rather than hardcoded. */
    val programName: String,
    val programUid: String,
    /** Enrolled tracked entities, counted in SQL rather than by materialising the list. */
    val enrolledCount: Int,
    val recent: List<EnrolledPerson>,
    /**
     * Events visible in this program, or null when `READ_EVENT` was not granted.
     *
     * Null rather than zero: "the grant does not let me look" and "I looked and found none" are
     * different answers, and conflating them hides exactly what the scoping model should expose.
     */
    val eventCount: Int? = null,
    /** What the write test would write, or null when nothing writable could be resolved. */
    val writeTarget: WriteTarget? = null,
)

/** One tracked entity, with its attributes already resolved to human-readable labels. */
data class EnrolledPerson(
    val uid: String,
    val attributes: List<LabelledValue>,
)

/** An attribute value and the label to show for it, so nothing renders under a raw UID. */
data class LabelledValue(
    val label: String,
    val value: String,
)

/**
 * The event the write test would create.
 *
 * Resolved from readable data, but every field is one the write guard checks against the *writable*
 * grant — so a target existing does not mean the write will be permitted. That gap is the point:
 * the SDK validates the object being written, not the query that found it.
 */
data class WriteTarget(
    val programUid: String,
    val enrollmentUid: String,
    val programStageUid: String,
    /** The enrollment's own org unit — the value the guard checks, not one the plugin chose. */
    val orgUnitUid: String,
)
