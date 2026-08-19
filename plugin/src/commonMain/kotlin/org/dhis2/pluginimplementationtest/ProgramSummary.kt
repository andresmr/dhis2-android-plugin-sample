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

/** Loading state for [ProgramSummaryCard]. */
sealed interface SummaryState {
    data object Loading : SummaryState

    data class Loaded(val summary: ProgramSummary) : SummaryState

    data class Failed(val message: String) : SummaryState
}
