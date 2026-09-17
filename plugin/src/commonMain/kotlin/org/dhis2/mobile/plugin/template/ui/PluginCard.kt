package org.dhis2.mobile.plugin.template.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.dhis2.mobile.plugin.template.generated.resources.Res
import org.dhis2.mobile.plugin.template.generated.resources.plugin_error_prefix
import org.dhis2.mobile.plugin.template.generated.resources.plugin_icon
import org.dhis2.mobile.plugin.template.generated.resources.plugin_loading
import org.dhis2.mobile.plugin.template.generated.resources.plugin_program_count
import org.dhis2.mobile.plugin.template.generated.resources.plugin_title
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
 * No `Dhis2PluginContext`, no SDK, no suspending work — so it renders in an `@Preview` with data you
 * supply, which is by far the fastest loop for UI work. That is architecture rule 2, and it is
 * enforced by the build.
 *
 * **Every colour, space, corner and text style comes from the DHIS2 design system**, not from raw
 * hex or Material's defaults. That is the point: the host renders this inside its own screen, and a
 * card that invents its own palette is instantly recognisable as a foreign object. The tokens live
 * in `org.hisp.dhis.mobile.ui.designsystem.theme` — `SurfaceColor`, `TextColor`, `Spacing`,
 * `Radius` — and resolve against whatever theme the host has applied, light or dark.
 *
 * For anything richer than this, reach for a real component rather than rebuilding one:
 * `BaseCard`, `ListCard`, `Button`, `Badge`, `InfoBar` and the rest are in
 * `org.hisp.dhis.mobile.ui.designsystem.component`. The API reference is linked from AGENTS.md.
 *
 * **Deliberately short.** The host renders this at `HOME_ABOVE_PROGRAM_LIST`: a non-scrolling
 * `Column` above its own programme list. Height taken here is height taken from the app, and
 * anything past the viewport is unreachable rather than scrollable — hence `heightIn` plus
 * `verticalScroll`, which `tools/check-rules.py` checks for by name.
 *
 * Note the layout APIs used have no defaulted parameters, and neither does `DHIS2Theme`. The host
 * declares androidx Compose separately from CMP, so a defaulted overload compiled here can meet a
 * different `…$default` synthetic there. The root build now forces the host's version and
 * `checkHostAlignment` proves it, but preferring APIs without defaults is still the cheaper habit.
 * See AGENTS.md, build rule 3.
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
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.L),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor.SurfaceBright),
    ) {
        Column(
            modifier = Modifier
                .padding(Spacing.Spacing12)
                .heightIn(max = Spacing.Spacing160)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(Res.drawable.plugin_icon),
                    contentDescription = null,
                    modifier = Modifier.size(Spacing.Spacing24),
                )
                Spacer(modifier = Modifier.width(Spacing.Spacing8))
                Text(
                    text = stringResource(Res.string.plugin_title),
                    style = getTextStyle(DHIS2TextStyle.TITLE_SMALL),
                    color = TextColor.OnSurface,
                )
                Spacer(modifier = Modifier.width(Spacing.Spacing8))
                Text(
                    text = "v$pluginVersion",
                    style = getTextStyle(DHIS2TextStyle.LABEL_MEDIUM),
                    color = TextColor.OnSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.Spacing8))

            when (val summary = state.summary) {
                is SummaryState.Loading -> Body(
                    text = stringResource(Res.string.plugin_loading),
                    color = TextColor.OnSurfaceVariant,
                )

                is SummaryState.Loaded -> Body(
                    text = stringResource(
                        Res.string.plugin_program_count,
                        summary.summary.programCount.toString(),
                    ),
                    color = TextColor.OnSurface,
                )

                is SummaryState.Failed -> Body(
                    text = stringResource(Res.string.plugin_error_prefix) + " " + summary.message,
                    color = SurfaceColor.Error,
                )
            }
        }
    }
}

/** One line of body copy, so the three states cannot drift apart in style. */
@Composable
private fun Body(text: String, color: Color) {
    Text(
        text = text,
        style = getTextStyle(DHIS2TextStyle.BODY_MEDIUM),
        color = color,
    )
}
