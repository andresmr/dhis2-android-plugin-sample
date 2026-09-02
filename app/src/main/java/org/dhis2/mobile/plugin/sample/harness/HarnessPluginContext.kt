package org.dhis2.mobile.plugin.sample.harness

import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.dhis2.mobile.plugin.sdk.InjectionPoint
import org.dhis2.mobile.plugin.sdk.PluginMetadata
import org.hisp.dhis.android.core.D2

/**
 * A real [Dhis2PluginContext], so the harness can render the plugin's entry point rather than only
 * the card beneath it.
 *
 * The metadata is invented: no server has configured this plugin, and the host pipeline that would
 * normally supply it is not running here.
 */
class HarnessPluginContext(
    override val sdk: D2,
) : Dhis2PluginContext {
    override val pluginMetadata: PluginMetadata = PluginMetadata(
        id = "org.dhis2.mobile.plugin.sample",
        version = "harness",
        entryPoint = "org.dhis2.mobile.plugin.sample.ProgramOverviewPlugin",
        injectionPoints = listOf(InjectionPoint.HOME_ABOVE_PROGRAM_LIST),
    )
}
