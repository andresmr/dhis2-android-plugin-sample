package org.dhis2.mobile.plugin.sample.harness

import androidx.compose.runtime.Composable
import org.dhis2.mobile.plugin.sdk.Dhis2Plugin
import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.koin.compose.KoinIsolatedContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * The smallest thing that can render a plugin the way the Capture App does.
 *
 * The host gives each plugin a **private** Koin container seeded with its context, metadata and
 * `D2`, then renders inside `KoinIsolatedContext` so `koinInject`/`koinViewModel` resolve there and
 * nowhere else. Without that, `MyPlugin.content()` throws: it asks for a ViewModel from a Koin that
 * does not exist.
 *
 * Deliberately mirrors `PluginContainer.create` in the host rather than inventing its own wiring —
 * a harness whose DI differs from the host's is a harness that proves the wrong thing.
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
