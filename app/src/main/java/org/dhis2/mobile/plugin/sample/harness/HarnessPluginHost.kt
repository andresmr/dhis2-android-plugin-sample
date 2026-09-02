package org.dhis2.mobile.plugin.sample.harness

import androidx.compose.runtime.Composable
import org.dhis2.mobile.plugin.sdk.Dhis2Plugin
import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.koin.compose.KoinIsolatedContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Renders a plugin the way the Capture App does: a private Koin container seeded with its context,
 * metadata and `D2`, entered through [KoinIsolatedContext] so the plugin's `koinViewModel` resolves
 * there and nowhere else.
 *
 * Mirrors the host's own `PluginContainer.create` — a harness whose DI differs proves the wrong
 * thing.
 */
@Composable
fun PluginHost(plugin: Dhis2Plugin, context: Dhis2PluginContext) {
    KoinIsolatedContext(context = containerFor(plugin, context)) {
        plugin.content(context)
    }
}

private fun containerFor(plugin: Dhis2Plugin, context: Dhis2PluginContext): KoinApplication =
    koinApplication {
        modules(
            listOfNotNull(
                module {
                    single { context }
                    single { context.pluginMetadata }
                    single { context.sdk }
                },
                plugin.provideKoinModule(),
            ),
        )
    }
