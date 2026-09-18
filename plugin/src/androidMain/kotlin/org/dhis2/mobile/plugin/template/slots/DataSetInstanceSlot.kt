package org.dhis2.mobile.plugin.template.slots

import androidx.compose.runtime.Composable
import org.dhis2.mobile.plugin.sdk.DataSetInstanceSlotArguments
import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.dhis2.mobile.plugin.sdk.LocalSlotContentPadding
import org.dhis2.mobile.plugin.template.ui.DataSetBodyPlaceholder

/**
 * `DATA_SET_INSTANCE_CONTENT`: the body of the data set instance screen, in place of the host's
 * table.
 *
 * A *replacement* slot, and the opposite of [HomeSlot] in every way that matters. The host hands it
 * a bounded region and keeps only the frame — top bar, save button, bottom bar — so filling the
 * space is correct here and scrolling is the plugin's job, not the host's. It applies
 * [LocalSlotContentPadding] because the save button floats over that region, and content that
 * ignores it has a last row nobody can reach.
 *
 * The host renders it only for the data sets `slotConfig.DATA_SET_INSTANCE_CONTENT.dataSetUids`
 * names, so this is never reached for a data set the plugin did not ask for.
 *
 * What it draws is a placeholder on purpose: the four identifiers of the instance the user opened.
 * That is the one thing worth seeing before there is a form — it proves the plugin was selected for
 * this data set and handed the right arguments. Read and write the instance's values through
 * `context.sdk`, keyed by these four; the host's save button then validates against what you wrote,
 * because it queries the SDK rather than its own table.
 */
@Composable
fun DataSetInstanceSlot(
    arguments: DataSetInstanceSlotArguments,
    context: Dhis2PluginContext,
) {
    DataSetBodyPlaceholder(
        dataSetUid = arguments.dataSetUid,
        periodId = arguments.periodId,
        organisationUnitUid = arguments.organisationUnitUid,
        attributeOptionComboUid = arguments.attributeOptionComboUid,
        pluginVersion = context.pluginMetadata.version,
        contentPadding = LocalSlotContentPadding.current,
    )
}
