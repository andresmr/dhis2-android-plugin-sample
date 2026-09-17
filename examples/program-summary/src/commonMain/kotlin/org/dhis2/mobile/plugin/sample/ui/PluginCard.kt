package org.dhis2.mobile.plugin.sample.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.dhis2.mobile.plugin.sample.generated.resources.Res
import org.dhis2.mobile.plugin.sample.generated.resources.plugin_and_more
import org.dhis2.mobile.plugin.sample.generated.resources.plugin_error_prefix
import org.dhis2.mobile.plugin.sample.generated.resources.plugin_icon
import org.dhis2.mobile.plugin.sample.generated.resources.plugin_loading
import org.dhis2.mobile.plugin.sample.generated.resources.plugin_teis_count
import org.dhis2.mobile.plugin.sample.model.MAX_LISTED_PEOPLE
import org.dhis2.mobile.plugin.sample.model.ProgramSummary
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2TextStyle
import org.hisp.dhis.mobile.ui.designsystem.theme.Radius
import org.hisp.dhis.mobile.ui.designsystem.theme.Spacing
import org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import org.hisp.dhis.mobile.ui.designsystem.theme.getTextStyle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The plugin's whole UI, as a function of [state] alone.
 *
 * No `Dhis2PluginContext`, no SDK, no suspending work — so it renders in a `@Preview`, in the harness
 * app, and in a screenshot test, all with data you supply.
 *
 * **Every colour, space, corner and text style comes from the DHIS2 design system**, exactly as the
 * seed in `plugin/` does. This card used to carry five hardcoded hex values and Material's own type
 * scale, which is what made it look like a foreign object on the host's screen — and an *example*
 * that does not follow the guidance is an example teaching the wrong thing.
 *
 * The tokens are in `org.hisp.dhis.mobile.ui.designsystem.theme`; components richer than these
 * (`BaseCard`, `ListCard`, `Button`, `Badge`) are in `…designsystem.component`. See AGENTS.md.
 *
 * **Deliberately short.** The host renders this at `HOME_ABOVE_PROGRAM_LIST`, a plain non-scrolling
 * `Column` above the host's own program list — so height taken here is height taken from the host,
 * and anything past the viewport is unreachable rather than scrollable. Hence the bounded internal
 * scroll as a backstop.
 */
@Composable
fun PluginCard(
    state: PluginUiState,
    pluginVersion: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Spacing16, vertical = Spacing.Spacing8),
        shape = RoundedCornerShape(Radius.L),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor.SurfaceBright),
        elevation = CardDefaults.cardElevation(defaultElevation = Spacing.Spacing2),
    ) {
        Column(
            modifier = Modifier
                .padding(Spacing.Spacing12)
                // The one number NOT from a token: the design system's Spacing scale stops at
                // 200 and then jumps to 416, and this is a height budget from the spec rather than
                // a spacing step. Bending the budget to fit a token would be the tail wagging the
                // dog — see `## UI budget` in specs/program-summary.md.
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            TitleRow(pluginVersion, state.summary)
            Spacer(modifier = Modifier.height(Spacing.Spacing8))
            Body(state.summary)
        }
    }
}

@Composable
private fun TitleRow(pluginVersion: String, summary: SummaryState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Image (not Icon) so the drawable's own colours show through — makes resource loading from
        // the extracted bundle visibly unambiguous.
        Image(
            painter = painterResource(Res.drawable.plugin_icon),
            contentDescription = null,
            modifier = Modifier.size(Spacing.Spacing24),
        )
        Spacer(modifier = Modifier.width(Spacing.Spacing8))
        Box(
            modifier = Modifier
                .background(SurfaceColor.Primary, RoundedCornerShape(Radius.XS))
                .padding(horizontal = Spacing.Spacing6, vertical = Spacing.Spacing1),
        ) {
            Text(
                text = "Plugin v$pluginVersion",
                style = getTextStyle(DHIS2TextStyle.LABEL_MEDIUM),
                color = TextColor.OnPrimary,
            )
        }
        Spacer(modifier = Modifier.width(Spacing.Spacing8))
        Text(
            text = (summary as? SummaryState.Loaded)?.summary?.programName.orEmpty(),
            style = getTextStyle(DHIS2TextStyle.TITLE_SMALL),
            color = TextColor.OnSurface,
        )
    }
}

@Composable
private fun Body(state: SummaryState) {
    when (state) {
        is SummaryState.Loading ->
            Line(stringResource(Res.string.plugin_loading), TextColor.OnSurfaceVariant)

        is SummaryState.Failed ->
            Line(
                "${stringResource(Res.string.plugin_error_prefix)} ${state.message}",
                SurfaceColor.Error,
            )

        is SummaryState.Loaded -> LoadedBody(state.summary)
    }
}

@Composable
private fun LoadedBody(summary: ProgramSummary) {
    Line(
        stringResource(Res.string.plugin_teis_count, summary.enrolledCount),
        TextColor.OnSurfaceVariant,
    )
    Line("${summary.eventCount} event(s) in this program", TextColor.OnSurfaceVariant)

    summary.recent.take(MAX_LISTED_PEOPLE).forEach { person ->
        Spacer(modifier = Modifier.height(Spacing.Spacing2))
        // Already the name the programme lists this person under — never a UID, and never
        // whichever attribute the SDK happened to return first.
        Line("  • " + person.displayLabel, TextColor.OnSurface)
    }
    val remaining = summary.enrolledCount - minOf(summary.recent.size, MAX_LISTED_PEOPLE)
    if (remaining > 0) {
        Line(
            "  " + stringResource(Res.string.plugin_and_more, remaining),
            TextColor.OnSurfaceLight,
        )
    }
}

/** Every one-line row in this card, so spacing and type stay consistent. */
@Composable
private fun Line(text: String, color: Color) {
    Spacer(modifier = Modifier.height(Spacing.Spacing2))
    Text(
        text = text,
        style = getTextStyle(DHIS2TextStyle.BODY_MEDIUM),
        color = color,
    )
}
