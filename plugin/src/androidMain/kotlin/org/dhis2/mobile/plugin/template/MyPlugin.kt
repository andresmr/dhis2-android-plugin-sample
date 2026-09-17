package org.dhis2.mobile.plugin.template

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.dhis2.mobile.plugin.sdk.DataSetInstanceSlotArguments
import org.dhis2.mobile.plugin.sdk.Dhis2Plugin
import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.dhis2.mobile.plugin.sdk.LocalSlotArguments
import org.dhis2.mobile.plugin.sdk.LocalSlotContentPadding
import org.dhis2.mobile.plugin.template.data.D2PluginRepository
import org.dhis2.mobile.plugin.template.repository.PluginRepository
import org.dhis2.mobile.plugin.template.ui.DataSetBodyPlaceholder
import org.dhis2.mobile.plugin.template.ui.PluginCard
import org.dhis2.mobile.plugin.template.ui.PluginViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * This plugin's entry point, and deliberately nothing more: it declares its dependencies and
 * renders a Composable against a ViewModel.
 *
 * Lives in `androidMain` because [Dhis2PluginContext.sdk] is `D2` — the DHIS2 *Android* SDK, which
 * has no common-source equivalent.
 *
 * **It must sit at the fully-qualified name `plugin.json` declares**, and it must have a public
 * no-argument constructor: the host instantiates it with `Class.forName(...).newInstance()`.
 * `tools/check-identity.py` checks the name; the development harness checks the constructor, by
 * loading it exactly the same way (see `MainActivity`).
 *
 * Note what is absent: no id, no version, no injection points. Those belong to the DHIS2
 * administrator, in the server dataStore, and arrive through [Dhis2PluginContext.pluginMetadata].
 * A plugin that declared its own identity could rename itself into someone else's configuration.
 */
class MyPlugin : Dhis2Plugin {

    /**
     * The plugin's own bindings, loaded into a container private to this plugin.
     *
     * `get()` resolves the host's `D2`, which it seeded for us. Nothing bound here is visible to
     * the host or to another plugin.
     */
    override fun provideKoinModule() = module {
        single<PluginRepository> { D2PluginRepository(get()) }
        viewModel { PluginViewModel(get()) }
    }

    /**
     * One entry point, however many slots the plugin is configured for.
     *
     * `LocalSlotArguments` is what says which one is being rendered right now. It is null at a slot
     * that has nothing to say about *what* is on screen — `HOME_ABOVE_PROGRAM_LIST` is the whole
     * home screen — and a typed value at one that does.
     *
     * The registry only renders a plugin at slots its server config declares, so this `when` never
     * has to defend against a slot the plugin did not ask for.
     */
    @Composable
    override fun content(context: Dhis2PluginContext) {
        when (val arguments = LocalSlotArguments.current as? DataSetInstanceSlotArguments) {
            null -> HomeCard(context)
            else -> DataSetBody(arguments, context)
        }
    }

    /** `HOME_ABOVE_PROGRAM_LIST`: a short card above the host's programme list. */
    @Composable
    private fun HomeCard(context: Dhis2PluginContext) {
        val viewModel: PluginViewModel = koinViewModel()
        val state by viewModel.state.collectAsState()
        PluginCard(state = state, pluginVersion = context.pluginMetadata.version)
    }

    /**
     * `DATA_SET_INSTANCE_CONTENT`: the body of the data set instance screen, in place of the
     * host's table.
     *
     * A placeholder that draws the arguments it was given — enough to prove the wiring before there
     * is a form to show. Read and write the instance's values through `context.sdk`, keyed by these
     * four identifiers; the host's save button then validates and completes against what you wrote,
     * because it queries the SDK rather than its own table.
     */
    @Composable
    private fun DataSetBody(
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
}
