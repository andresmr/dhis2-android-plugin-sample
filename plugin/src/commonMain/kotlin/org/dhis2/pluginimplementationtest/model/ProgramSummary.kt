package org.dhis2.pluginimplementationtest.model

/**
 * What the plugin knows about one program, as plain data.
 *
 * Free of SDK types on purpose. Everything above the repository — state, ViewModel, Composables —
 * sees only this, which is what lets all of it live in `commonMain` and be tested without a device.
 */
data class ProgramSummary(
    val programUid: String,
    /** The program's real display name, read from metadata rather than hardcoded. */
    val programName: String,
    /** Enrolled tracked entities, counted in SQL rather than by materialising the list. */
    val enrolledCount: Int,
    val eventCount: Int,
    val recent: List<EnrolledPerson>,
    /** What the write test would create, or null when no enrollment could be resolved. */
    val writeTarget: WriteTarget? = null,
)

/** One tracked entity, with attributes already resolved to human-readable labels. */
data class EnrolledPerson(
    val uid: String,
    val attributes: List<LabelledValue>,
)

/** An attribute value and the label to show for it, so nothing renders under a raw UID. */
data class LabelledValue(
    val label: String,
    val value: String,
)

/** The event the write test would create — proof that SDK access includes writes. */
data class WriteTarget(
    val programUid: String,
    val enrollmentUid: String,
    val programStageUid: String,
    val orgUnitUid: String,
)
