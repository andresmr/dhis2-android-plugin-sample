package org.dhis2.pluginimplementationtest

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.dhis2.mobile.plugin.sdk.Dhis2Plugin
import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.dhis2.pluginimplementationtest.data.ScopedPluginRepository
import org.dhis2.pluginimplementationtest.repository.PluginRepository
import org.dhis2.pluginimplementationtest.ui.PluginCard
import org.dhis2.pluginimplementationtest.ui.PluginViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.module

/**
 * The plugin's entry point, and deliberately nothing more.
 *
 * All it does is declare its dependencies and render a Composable against a ViewModel. The data
 * access lives in [ScopedPluginRepository] (`androidMain`, the only file that sees the SDK) and the
 * state and UI live in `commonMain`, where they can be unit-tested and previewed.
 *
 * Note what is absent: no id, no version, no data scope. All of that is the server administrator's
 * to declare in the dataStore config, which the plugin reads back through
 * [Dhis2PluginContext.pluginMetadata] if it needs it.
 */
class MyPlugin : Dhis2Plugin {

    /**
     * The plugin's own bindings, in its own private container.
     *
     * `get()` resolves the [org.hisp.dhis.android.core.scopedaccess.ScopedD2] the host seeds into
     * that container — the same object handed to [content] as `context.sdk`. Nothing host-owned is
     * reachable here; `get<D2>()` would not resolve.
     */
    override fun provideKoinModule() = module {
        single<PluginRepository> { ScopedPluginRepository(get(), get()) }
        viewModel { PluginViewModel(get()) }
    }

    @Composable
    override fun content(context: Dhis2PluginContext) {
        val viewModel: PluginViewModel = koinViewModel()
        val state by viewModel.state.collectAsState()

        PluginCard(
            state = state,
            pluginVersion = context.pluginMetadata.version,
            onAddEvent = viewModel::addEvent,
            onProbeSearch = viewModel::probeSearch,
            onSelectProgram = viewModel::selectProgram,
            onSelectDataSet = viewModel::selectDataSet,
            onWriteDataValue = viewModel::writeDataValue,
        )
    }
}
