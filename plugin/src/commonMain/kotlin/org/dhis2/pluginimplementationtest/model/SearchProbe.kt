package org.dhis2.pluginimplementationtest.model

/**
 * One deliberate attempt to search beyond the granted scope, and what came back.
 *
 * Tracker search needs probing in a way the other accessors do not. Everywhere else the grant rides
 * on append-only `RepositoryScope` filters, so a plugin's own `by*()` can only narrow. Search uses a
 * scope whose fields are single-valued and *replaced* by `by*()` — `program`, `orgUnits`,
 * `orgUnitMode`, and the online/offline mode — so overwriting one would widen the query if the SDK
 * did not re-apply the grant on every repository the fluent API produces.
 */
data class SearchProbe(
    /** Short name for the row. */
    val label: String,
    /** The SDK mechanism this probe aims at, so a failure points somewhere. */
    val mechanism: String,
    /** The result count, or null when this probe threw — see [error]. */
    val count: Int?,
    val expectation: Expectation,
    /**
     * Why this probe produced no count.
     *
     * Held per probe rather than per run. A single `try` around a whole run meant the first throw
     * discarded every other result, which made an SDK bug that broke *all* scoped searches look
     * identical to "search was not granted".
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

/** A whole probe run. [baseline] is null when the baseline probe itself failed. */
data class SearchProbeRun(
    val baseline: Int?,
    val probes: List<SearchProbe>,
)

enum class Verdict { INFO, PASS, FAIL, ERROR }

/**
 * Scores a probe against [baseline], the count from an ordinary in-scope search.
 *
 * Comparing against the baseline rather than an absolute number is what makes these probes portable
 * across databases: the claim under test is never "N results" but "asking for more did not get
 * more".
 */
fun SearchProbe.verdict(baseline: Int?): Verdict = when {
    // A probe that threw is neither a pass nor a widening — it is a broken probe, and saying so is
    // more useful than folding it into FAIL.
    error != null || count == null -> Verdict.ERROR
    expectation == SearchProbe.Expectation.INFORMATIONAL -> Verdict.INFO
    expectation == SearchProbe.Expectation.EMPTY -> if (count == 0) Verdict.PASS else Verdict.FAIL
    // Nothing to compare against if the baseline itself failed.
    baseline == null -> Verdict.ERROR
    else -> if (count == baseline) Verdict.PASS else Verdict.FAIL
}
