package org.dhis2.datasetsections.slots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.dhis2.mobile.plugin.sdk.DataSetInstanceSlotArguments
import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.dhis2.mobile.plugin.sdk.LocalSlotContentPadding
import org.dhis2.datasetsections.ui.DataSetSectionsScreen
import org.dhis2.datasetsections.ui.PluginViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * `DATA_SET_INSTANCE_CONTENT`: the body of the data set instance screen, in place of the host's
 * table.
 *
 * A *replacement* slot. The host hands it a bounded region and keeps only the frame — top bar, save
 * button, bottom bar — so filling the space is correct here and scrolling is the plugin's job. It
 * applies [LocalSlotContentPadding] because the save button floats over that region, and content
 * that ignores it has a last row nobody can reach.
 *
 * The host renders it only for the data sets `slotConfig.DATA_SET_INSTANCE_CONTENT.dataSetUids`
 * names, so this is never reached for a data set the plugin did not ask for.
 *
 * The ViewModel is told which instance is open rather than loading in its `init`: `init` has no
 * arguments to load *from*, and keying the effect on the two identifiers means re-rendering the
 * same instance does not re-read, while the host moving to another one does.
 */
@Composable
fun DataSetInstanceSlot(
    arguments: DataSetInstanceSlotArguments,
    @Suppress("UNUSED_PARAMETER") context: Dhis2PluginContext,
) {
    val viewModel: PluginViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(arguments.dataSetUid, arguments.periodId) {
        viewModel.open(dataSetUid = arguments.dataSetUid, periodId = arguments.periodId)
    }

    DataSetSectionsScreen(
        period = state.period,
        sections = state.sections,
        onSectionSelected = viewModel::selectSection,
        contentPadding = LocalSlotContentPadding.current,
    )
}
