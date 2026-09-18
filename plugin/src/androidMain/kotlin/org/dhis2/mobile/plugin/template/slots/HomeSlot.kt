package org.dhis2.mobile.plugin.template.slots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.dhis2.mobile.plugin.template.ui.PluginCard
import org.dhis2.mobile.plugin.template.ui.PluginViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * `HOME_ABOVE_PROGRAM_LIST`: a short card above the host's programme list.
 *
 * An *additive* slot. The host renders every registered plugin here, one after another, in a column
 * that does not scroll — so height taken here is height taken from the host, and anything past the
 * viewport is unreachable rather than scrollable. [PluginCard] caps itself accordingly.
 *
 * This and its sibling under `slots/` are the entry point's own layer, split one file per slot, not
 * UI. That is why they take a [Dhis2PluginContext] where architecture rule 2 forbids a composable
 * from doing so: the rule exists to keep `@Preview` able to render the real UI without a server,
 * and the composables below this line still obey it.
 */
@Composable
fun HomeSlot(context: Dhis2PluginContext) {
    val viewModel: PluginViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    PluginCard(state = state, pluginVersion = context.pluginMetadata.version)
}
