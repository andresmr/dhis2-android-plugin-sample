package org.dhis2.mobile.plugin.template

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.dhis2.mobile.plugin.sdk.Dhis2Plugin
import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.dhis2.mobile.plugin.template.data.D2PluginRepository
import org.dhis2.mobile.plugin.template.repository.PluginRepository
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

    @Composable
    override fun content(context: Dhis2PluginContext) {
        val viewModel: PluginViewModel = koinViewModel()
        val state by viewModel.state.collectAsState()
        PluginCard(state = state, pluginVersion = context.pluginMetadata.version)
    }
}
