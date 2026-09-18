package org.dhis2.mobile.plugin.template.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2TextStyle
import org.hisp.dhis.mobile.ui.designsystem.theme.Radius
import org.hisp.dhis.mobile.ui.designsystem.theme.Spacing
import org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import org.hisp.dhis.mobile.ui.designsystem.theme.getTextStyle

/**
 * What the template renders in place of the host's data set table, at
 * `DATA_SET_INSTANCE_CONTENT`.
 *
 * It is a placeholder on purpose: it draws the four values identifying the instance the user
 * opened, which is the one thing worth seeing before writing a real form — it proves the plugin was
 * selected for this data set and was handed the right arguments. Replace the whole thing with your
 * own data entry UI.
 *
 * Unlike [PluginCard] it does **not** cap its height. A replacement owns the whole region the host
 * gave it, rather than sitting above the host's own scrolling content, so filling the space is
 * correct here and scrolling is this composable's job.
 *
 * [contentPadding] is the host's `LocalSlotContentPadding` — the space its floating save button
 * occupies over this region. Anything scrolling has to add it, or the last row sits under the
 * button and cannot be reached.
 */
@Composable
fun DataSetBodyPlaceholder(
    dataSetUid: String,
    periodId: String,
    organisationUnitUid: String,
    attributeOptionComboUid: String,
    pluginVersion: String,
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
        Text(
            text = "Rendered by a plugin",
            style = getTextStyle(DHIS2TextStyle.TITLE_MEDIUM),
            color = TextColor.OnSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )

        Spacer(modifier = Modifier.height(Spacing.Spacing4))

        Text(
            text =
                "The host's table for this data set was replaced, and its own top bar, save " +
                    "button and bottom bar were kept. Template v$pluginVersion.",
            style = getTextStyle(DHIS2TextStyle.BODY_MEDIUM),
            color = TextColor.OnSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.Spacing16))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.L),
            colors = CardDefaults.cardColors(containerColor = SurfaceColor.SurfaceBright),
        ) {
            Column(modifier = Modifier.padding(Spacing.Spacing12)) {
                Text(
                    text = "The instance the host handed us",
                    style = getTextStyle(DHIS2TextStyle.TITLE_SMALL),
                    color = TextColor.OnSurface,
                )
                Spacer(modifier = Modifier.height(Spacing.Spacing8))
                IdentifierRow(label = "Data set", value = dataSetUid)
                IdentifierRow(label = "Period", value = periodId)
                IdentifierRow(label = "Org unit", value = organisationUnitUid)
                IdentifierRow(label = "Attr. option combo", value = attributeOptionComboUid)
            }
        }
    }
}

/** One labelled value, so the four cannot drift apart in style. */
@Composable
private fun IdentifierRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.Spacing2)) {
        Text(
            text = label,
            style = getTextStyle(DHIS2TextStyle.LABEL_MEDIUM),
            color = TextColor.OnSurfaceVariant,
            modifier = Modifier.width(Spacing.Spacing160),
        )
        Text(
            text = value.ifBlank { "—" },
            style = getTextStyle(DHIS2TextStyle.BODY_MEDIUM),
            color = TextColor.OnSurface,
        )
    }
}
