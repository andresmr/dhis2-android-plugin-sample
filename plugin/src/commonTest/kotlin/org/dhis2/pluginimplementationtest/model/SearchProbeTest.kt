package org.dhis2.pluginimplementationtest.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The scoring rules for [SearchProbe], which are the only part of the probe run that is pure.
 *
 * Worth pinning because the verdicts encode the claim the probes exist to make: a widening attempt
 * that returns *the same* as the baseline passed, one that returns more failed, and one that threw
 * says nothing either way.
 */
class SearchProbeTest {

    private fun probe(
        count: Int? = 0,
        expectation: SearchProbe.Expectation = SearchProbe.Expectation.SAME_AS_BASELINE,
        error: String? = null,
    ) = SearchProbe("probe", "mechanism", count, expectation, error)

    @Test
    fun `an informational probe is never scored`() {
        val verdict = probe(count = 99, expectation = SearchProbe.Expectation.INFORMATIONAL)
            .verdict(baseline = 3)

        assertEquals(Verdict.INFO, verdict)
    }

    @Test
    fun `an empty-expected probe passes on zero`() {
        assertEquals(
            Verdict.PASS,
            probe(count = 0, expectation = SearchProbe.Expectation.EMPTY).verdict(baseline = 3),
        )
    }

    @Test
    fun `an empty-expected probe fails on any result`() {
        // One row of an ungranted program is a grant that leaked, not a rounding error.
        assertEquals(
            Verdict.FAIL,
            probe(count = 1, expectation = SearchProbe.Expectation.EMPTY).verdict(baseline = 3),
        )
    }

    @Test
    fun `a widening probe passes when it matches the baseline`() {
        assertEquals(Verdict.PASS, probe(count = 3).verdict(baseline = 3))
    }

    @Test
    fun `a widening probe fails when it returns more than the baseline`() {
        assertEquals(Verdict.FAIL, probe(count = 4).verdict(baseline = 3))
    }

    @Test
    fun `a widening probe fails when it returns fewer than the baseline`() {
        // Narrower is still wrong: the grant should be invisible to a query already inside it.
        assertEquals(Verdict.FAIL, probe(count = 2).verdict(baseline = 3))
    }

    @Test
    fun `a probe that threw is an error rather than a failure`() {
        assertEquals(Verdict.ERROR, probe(count = null, error = "boom").verdict(baseline = 3))
    }

    @Test
    fun `a probe cannot be scored without a baseline`() {
        assertEquals(Verdict.ERROR, probe(count = 3).verdict(baseline = null))
    }
}
