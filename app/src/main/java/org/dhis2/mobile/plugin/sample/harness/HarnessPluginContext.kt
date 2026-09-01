package org.dhis2.mobile.plugin.sample.harness

import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.dhis2.mobile.plugin.sdk.InjectionPoint
import org.dhis2.mobile.plugin.sdk.PluginMetadata
import org.hisp.dhis.android.core.D2

/**
 * A real [Dhis2PluginContext], carrying a real [D2].
 *
 * The Capture App builds one of these from the server's dataStore config; the harness builds one
 * from `local.properties` and a live login. The plugin cannot tell the difference, which is the
 * point — it lets `MyPlugin.content()` be rendered here, not just the card beneath it.
 *
 * The metadata is invented, because in the harness nobody has configured this plugin on a server.
 * Only [PluginMetadata.id] and [PluginMetadata.version] are ever read by a plugin at runtime; the
 * rest exists for the host's own pipeline, which is not running here.
 */
class HarnessPluginContext(
    override val sdk: D2,
) : Dhis2PluginContext {
    override val pluginMetadata: PluginMetadata = PluginMetadata(
        id = "org.dhis2.mobile.plugin.sample",
        version = "harness",
        entryPoint = "org.dhis2.mobile.plugin.sample.MyPlugin",
        injectionPoints = listOf(InjectionPoint.HOME_ABOVE_PROGRAM_LIST),
    )
}
