package org.dhis2.datasetsections.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.dhis2.datasetsections.generated.resources.Res
import org.dhis2.datasetsections.generated.resources.month_1
import org.dhis2.datasetsections.generated.resources.month_10
import org.dhis2.datasetsections.generated.resources.month_11
import org.dhis2.datasetsections.generated.resources.month_12
import org.dhis2.datasetsections.generated.resources.month_2
import org.dhis2.datasetsections.generated.resources.month_3
import org.dhis2.datasetsections.generated.resources.month_4
import org.dhis2.datasetsections.generated.resources.month_5
import org.dhis2.datasetsections.generated.resources.month_6
import org.dhis2.datasetsections.generated.resources.month_7
import org.dhis2.datasetsections.generated.resources.month_8
import org.dhis2.datasetsections.generated.resources.month_9
import org.dhis2.datasetsections.generated.resources.period_day
import org.dhis2.datasetsections.generated.resources.period_month
import org.dhis2.datasetsections.generated.resources.period_quarter
import org.dhis2.datasetsections.generated.resources.period_week
import org.dhis2.datasetsections.generated.resources.plugin_error_prefix
import org.dhis2.datasetsections.generated.resources.plugin_loading
import org.dhis2.datasetsections.model.PeriodLabel
import org.hisp.dhis.mobile.ui.designsystem.component.FilterChip
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2TextStyle
import org.hisp.dhis.mobile.ui.designsystem.theme.Spacing
import org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import org.hisp.dhis.mobile.ui.designsystem.theme.getTextStyle
import org.jetbrains.compose.resources.stringResource

/**
 * What the plugin renders in place of the host's data set table, at `DATA_SET_INSTANCE_CONTENT`.
 *
 * The period of the instance the user opened, and under it the data set's sections as a horizontal
 * row of chips with one selected. Nothing follows the row yet; when a form does, it is the selected
 * section that decides what the form shows.
 *
 * A function of its arguments alone — no `Dhis2PluginContext`, no SDK, no suspending work — so it
 * renders in an `@Preview` with data you supply. That is architecture rule 2, and
 * `checkPluginConventions` enforces it here because this file is in `commonMain`.
 *
 * Unlike an additive slot's card it does **not** cap its height. A replacement owns the region the
 * host gave it, so filling the space is correct and scrolling is this composable's job.
 *
 * [contentPadding] is the host's `LocalSlotContentPadding` — the space its floating save button
 * occupies over this region. Anything scrolling has to add it, or the last row sits under the
 * button and cannot be reached.
 */
@Composable
fun DataSetSectionsScreen(
    period: PeriodLabel?,
    sections: SectionsState,
    onSectionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(Spacing.Spacing16),
    ) {
        if (period != null) {
            Text(
                text = period.asText(),
                style = getTextStyle(DHIS2TextStyle.TITLE_MEDIUM),
                color = TextColor.OnSurface,
            )
            Spacer(modifier = Modifier.height(Spacing.Spacing16))
        }

        when (sections) {
            is SectionsState.Loading -> Body(
                text = stringResource(Res.string.plugin_loading),
                color = TextColor.OnSurfaceVariant,
            )

            is SectionsState.Failed -> Body(
                text = stringResource(Res.string.plugin_error_prefix) + " " + sections.message,
                color = SurfaceColor.Error,
            )

            // A data set with no sections renders as the period alone. Deliberate: an empty-state
            // message here would be an apology for an ordinary configuration.
            is SectionsState.Loaded -> SectionRow(
                state = sections,
                onSectionSelected = onSectionSelected,
            )
        }
    }
}

/**
 * The sections, side by side, one selected.
 *
 * Bounded horizontally rather than vertically: it scrolls sideways and stays one row tall however
 * many sections the data set has. It must never wrap, because a row that grows downward pushes the
 * form that will eventually sit below it off the screen.
 */
@Composable
private fun SectionRow(
    state: SectionsState.Loaded,
    onSectionSelected: (String) -> Unit,
) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        state.sections.forEachIndexed { index, section ->
            if (index > 0) Spacer(modifier = Modifier.width(Spacing.Spacing8))
            FilterChip(
                modifier = Modifier,
                label = section.displayName,
                selected = section.uid == state.selectedSectionUid,
                onSelected = { onSectionSelected(section.uid) },
                badge = null,
            )
        }
    }
}

/** One line of body copy, so loading and failure cannot drift apart in style. */
@Composable
private fun Body(text: String, color: Color) {
    Text(
        text = text,
        style = getTextStyle(DHIS2TextStyle.BODY_MEDIUM),
        color = color,
    )
}

/**
 * The period, as a person would say it.
 *
 * This is why the state carries a structured period rather than a string: month names come from
 * `composeResources`, which needs a composition to read, so the only place that can translate a
 * period is here. `PeriodLabel.Year` and `PeriodLabel.Raw` are already text in every language.
 */
@Composable
private fun PeriodLabel.asText(): String = when (this) {
    is PeriodLabel.Day -> stringResource(
        Res.string.period_day,
        dayOfMonth.toString(),
        monthName(month),
        year.toString(),
    )

    is PeriodLabel.Week -> stringResource(
        Res.string.period_week,
        week.toString(),
        year.toString(),
    )

    is PeriodLabel.Month -> stringResource(
        Res.string.period_month,
        monthName(month),
        year.toString(),
    )

    is PeriodLabel.Quarter -> stringResource(
        Res.string.period_quarter,
        quarter.toString(),
        year.toString(),
    )

    is PeriodLabel.Year -> year.toString()
    is PeriodLabel.Raw -> periodId
}

/** `periodLabelOf` only ever produces 1..12, so the `else` is unreachable rather than a default. */
@Composable
private fun monthName(month: Int): String = stringResource(
    when (month) {
        1 -> Res.string.month_1
        2 -> Res.string.month_2
        3 -> Res.string.month_3
        4 -> Res.string.month_4
        5 -> Res.string.month_5
        6 -> Res.string.month_6
        7 -> Res.string.month_7
        8 -> Res.string.month_8
        9 -> Res.string.month_9
        10 -> Res.string.month_10
        11 -> Res.string.month_11
        else -> Res.string.month_12
    },
)
