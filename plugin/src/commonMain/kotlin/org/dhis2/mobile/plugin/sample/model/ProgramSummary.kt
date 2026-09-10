package org.dhis2.mobile.plugin.sample.model

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
)

/**
 * One tracked entity, already resolved to the name the programme lists it under.
 *
 * A name rather than a list of attributes, because that is what the app itself shows for a tracked
 * entity and a plugin disagreeing with the list the user just tapped through is worse than one
 * showing nothing. `plugin-sdk`'s `TrackedEntityLabeller` composes it from the attributes the
 * programme marks `displayInList`, in configured order — never from whichever attribute the SDK
 * happened to return first, which is how a person came to be labelled *Female*.
 *
 * [displayLabel] is never a UID and never empty: the labeller falls back to the org unit's name.
 */
data class EnrolledPerson(
    val uid: String,
    val displayLabel: String,
)

