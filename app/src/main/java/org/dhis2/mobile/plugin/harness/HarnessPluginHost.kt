package org.dhis2.mobile.plugin.harness

import androidx.compose.runtime.Composable
import org.dhis2.mobile.plugin.sdk.Dhis2Plugin
import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2Theme
import org.koin.compose.KoinIsolatedContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Renders a plugin the way the Capture App does: inside the DHIS2 design system's theme, and inside
 * a private Koin container seeded with its context, metadata and `D2`, entered through
 * [KoinIsolatedContext] so the plugin's `koinViewModel` resolves there and nowhere else.
 *
 * Mirrors the host's own `PluginContainer.create` — a harness whose DI differs proves the wrong
 * thing, and so does one whose theme differs. [DHIS2Theme] is what makes `SurfaceColor`,
 * `TextColor` and the type scale resolve to the values the host would supply; without it a plugin
 * using them renders against Material's defaults here and something else entirely on a device,
 * which is worse than not testing the theme at all.
 */
@Composable
fun PluginHost(plugin: Dhis2Plugin, context: Dhis2PluginContext) {
    DHIS2Theme {
        KoinIsolatedContext(context = containerFor(plugin, context)) {
            plugin.content(context)
        }
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
