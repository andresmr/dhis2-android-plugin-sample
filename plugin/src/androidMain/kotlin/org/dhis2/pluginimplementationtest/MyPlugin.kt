package org.dhis2.pluginimplementationtest

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.dhis2.mobile.plugin.sdk.Dhis2Plugin
import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.dhis2.pluginimplementationtest.data.D2PluginRepository
import org.dhis2.pluginimplementationtest.repository.PluginRepository
import org.dhis2.pluginimplementationtest.ui.PluginCard
import org.dhis2.pluginimplementationtest.ui.PluginViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.module

/** The program this sample reports on. Any tracker program with enrollments will do. */
private const val CHILD_PROGRAMME_UID = "IpHINAT79UW"

/**
 * The plugin's entry point, and deliberately nothing more.
 *
 * All it does is declare its dependencies and render a Composable against a ViewModel. The data
 * access lives in [D2PluginRepository] (`androidMain`, the only file that sees the SDK) and the state
 * and UI live in `commonMain`, where they can be unit-tested and previewed.
 *
 * Lives in `androidMain` because [Dhis2PluginContext.sdk] is `D2` — the DHIS2 *Android* SDK.
 *
 * Note what is absent: no id, no version, no injection points. All of that is the server
 * administrator's to declare in the dataStore config, which the plugin reads back through
 * [Dhis2PluginContext.pluginMetadata] if it needs it.
 */
class MyPlugin : Dhis2Plugin {

    /**
     * The plugin's own bindings, in its own private container.
     *
     * `get()` resolves the `D2` the host seeds into that container — the same object handed to
     * [content] as `context.sdk`. Nothing the plugin binds here can leak out and override a host
     * binding, which is what the private container is for.
     */
    override fun provideKoinModule() = module {
        single<PluginRepository> { D2PluginRepository(get()) }
        viewModel { PluginViewModel(CHILD_PROGRAMME_UID, get()) }
    }

    @Composable
    override fun content(context: Dhis2PluginContext) {
        val viewModel: PluginViewModel = koinViewModel()
        val state by viewModel.state.collectAsState()

        PluginCard(
            state = state,
            pluginVersion = context.pluginMetadata.version,
            onAddEvent = viewModel::addEvent,
        )
    }
}
