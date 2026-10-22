package org.dhis2.datasetsections.model

/**
 * The period of the instance the host opened, structured rather than formatted.
 *
 * Structured on purpose. The state has to be assertable on the JVM *and* the text has to be
 * translatable, and a `String` built here could only be one or the other: month names live in
 * `composeResources`, which needs a composition to read. So the state carries `Month(2024, 1)` and
 * the composable turns it into "January 2024" — or "enero de 2024".
 *
 * Nothing here reads DHIS2. The host hands the period id over in `DataSetInstanceSlotArguments`,
 * and the SDK has no display name for a period to fetch anyway: `Period` is `periodId`,
 * `periodType`, `startDate` and `endDate`, nothing more.
 */
sealed interface PeriodLabel {
    data class Day(val year: Int, val month: Int, val dayOfMonth: Int) : PeriodLabel
    data class Week(val year: Int, val week: Int) : PeriodLabel
    data class Month(val year: Int, val month: Int) : PeriodLabel
    data class Quarter(val year: Int, val quarter: Int) : PeriodLabel
    data class Year(val year: Int) : PeriodLabel

    /**
     * A period id of a type this plugin does not format, shown exactly as the host gave it.
     *
     * DHIS2 has twenty-three period types. Five are formatted here; the other eighteen —
     * bi-monthly, six-monthly, the four weekly variants, the seven financial years — land here. A
     * raw id on screen is worse than a formatted one and far better than a blank line, which is
     * what a missing branch would leave.
     */
    data class Raw(val periodId: String) : PeriodLabel
}

private val DAILY = Regex("""^(\d{4})(\d{2})(\d{2})$""")
private val WEEKLY = Regex("""^(\d{4})W(\d{1,2})$""")
private val MONTHLY = Regex("""^(\d{4})(\d{2})$""")
private val QUARTERLY = Regex("""^(\d{4})Q(\d)$""")
private val YEARLY = Regex("""^(\d{4})$""")

/**
 * Reads a DHIS2 period id into something renderable, falling back to [PeriodLabel.Raw].
 *
 * Every pattern is anchored, which is what keeps the near-misses apart: `202402B` is bi-monthly and
 * must not read as the month `202402`, and `2024April` is a financial year and must not read as
 * `2024`. The ranges are checked for the same reason — a `202413` is not a thirteenth month, it is
 * an id this function has misread, and `Raw` is the honest answer.
 */
fun periodLabelOf(periodId: String): PeriodLabel {
    DAILY.matchEntire(periodId)?.let { match ->
        val (year, month, day) = match.destructured
        if (month.toInt() in 1..12 && day.toInt() in 1..31) {
            return PeriodLabel.Day(year.toInt(), month.toInt(), day.toInt())
        }
    }
    WEEKLY.matchEntire(periodId)?.let { match ->
        val (year, week) = match.destructured
        if (week.toInt() in 1..53) return PeriodLabel.Week(year.toInt(), week.toInt())
    }
    MONTHLY.matchEntire(periodId)?.let { match ->
        val (year, month) = match.destructured
        if (month.toInt() in 1..12) return PeriodLabel.Month(year.toInt(), month.toInt())
    }
    QUARTERLY.matchEntire(periodId)?.let { match ->
        val (year, quarter) = match.destructured
        if (quarter.toInt() in 1..4) return PeriodLabel.Quarter(year.toInt(), quarter.toInt())
    }
    YEARLY.matchEntire(periodId)?.let { match ->
        return PeriodLabel.Year(match.groupValues[1].toInt())
    }
    return PeriodLabel.Raw(periodId)
}
