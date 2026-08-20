package org.dhis2.pluginimplementationtest

/**
 * Results of probing the scoped tracker search.
 *
 * `trackedEntitySearch()` is the one accessor that cannot be secured the way the others are. Every
 * other repository carries append-only `RepositoryScope` filters, so a caller's `by*()` can only
 * narrow. The search scope is a record of *single-valued* fields that `by*()` **replaces** —
 * `program`, `orgUnits`, `orgUnitMode`, and the online/offline mode — so a caller could otherwise
 * overwrite the grant simply by asking for something else. The SDK answers that by re-applying the
 * grant in `TrackedEntitySearchOperators.scope`, which every `by*()` rebuilds through.
 *
 * That makes it worth probing rather than trusting: these are deliberate attempts to widen the
 * grant, and each one should come back neutralised.
 */
data class SearchProbe(
    /** Short name for the row. */
    val label: String,
    /** The SDK mechanism this probe is aimed at, so a failure points somewhere. */
    val mechanism: String,
    /** The result count, or null when this probe threw — see [error]. */
    val count: Int?,
    val expectation: Expectation,
    /**
     * Why this probe produced no count.
     *
     * Held per probe rather than per run. A single `try` around the whole run meant the first probe
     * to throw discarded every other result, which is precisely how an SDK bug that made *all*
     * scoped searches throw looked identical to "search is not granted".
     */
    val error: String? = null,
) {
    enum class Expectation {
        /** Reported, not asserted — there is no independent number to compare against. */
        INFORMATIONAL,

        /** The grant should reduce this to nothing. */
        EMPTY,

        /** The widening attempt should leave the result identical to the baseline. */
        SAME_AS_BASELINE,
    }
}

enum class Verdict { INFO, PASS, FAIL, ERROR }

/**
 * Scores a probe against [baseline], the count from an ordinary in-scope search.
 *
 * Comparing against the baseline rather than an absolute number is what makes these probes portable
 * across databases: the interesting claim is never "N results" but "asking for more did not get
 * more".
 */
fun SearchProbe.verdict(baseline: Int?): Verdict = when {
    // A probe that threw is neither a pass nor a widening — it is a broken probe, and saying so is
    // more useful than folding it into FAIL.
    error != null -> Verdict.ERROR
    count == null -> Verdict.ERROR
    expectation == SearchProbe.Expectation.INFORMATIONAL -> Verdict.INFO
    expectation == SearchProbe.Expectation.EMPTY -> if (count == 0) Verdict.PASS else Verdict.FAIL
    // Nothing to compare against if the baseline itself failed.
    baseline == null -> Verdict.ERROR
    else -> if (count == baseline) Verdict.PASS else Verdict.FAIL
}

/** State of the search probe run. */
sealed interface SearchState {
    data object Idle : SearchState

    data object Running : SearchState

    /** [baseline] is null when the baseline probe itself failed, which makes the rest unscoreable. */
    data class Done(val baseline: Int?, val probes: List<SearchProbe>) : SearchState

    /**
     * The probes could not run at all — normally because `SEARCH_TRACKED_ENTITY` was not granted,
     * which is itself a valid result rather than a malfunction.
     */
    data class Unavailable(val message: String) : SearchState
}
