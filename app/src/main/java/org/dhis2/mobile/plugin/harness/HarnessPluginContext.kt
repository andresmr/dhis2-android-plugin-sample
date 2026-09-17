package org.dhis2.mobile.plugin.harness

import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.dhis2.mobile.plugin.sdk.InjectionPoint
import org.dhis2.mobile.plugin.sdk.PluginMetadata
import org.hisp.dhis.android.core.D2

/**
 * A real [Dhis2PluginContext], so the harness can render the plugin's entry point rather than only
 * the card beneath it.
 *
 * The metadata stands in for a server's: no DHIS2 has configured this plugin, and the host pipeline
 * that would normally supply it is not running here. What it is *not* is invented — the values come
 * from `plugin.json` through `BuildConfig`, so the harness hands the plugin the same id, version and
 * entry point the real dataStore config would. A plugin that reads `pluginMetadata.version` (this
 * one does, for the chip on the card) therefore sees a real value here.
 */
class HarnessPluginContext(
    override val sdk: D2,
) : Dhis2PluginContext {
    override val pluginMetadata: PluginMetadata = PluginMetadata(
        id = BuildConfig.PLUGIN_ID,
        version = BuildConfig.PLUGIN_VERSION,
        entryPoint = BuildConfig.PLUGIN_ENTRY_POINT,
        injectionPoints = listOf(InjectionPoint.HOME_ABOVE_PROGRAM_LIST),
    )
}
