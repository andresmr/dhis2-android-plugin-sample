package org.dhis2.mobile.plugin.harness

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.dhis2.mobile.plugin.sdk.InjectionPoint
import org.dhis2.mobile.plugin.sdk.PluginMetadata
import org.hisp.dhis.android.core.D2

/**
 * A real [Dhis2PluginContext], so the harness can render the plugin's entry point rather than only
 * the card beneath it.
 *
 * The metadata stands in for a server's: no DHIS2 has configured this plugin, and the host pipeline
 * that would normally supply it is not running here. What it is *not* is invented — every field
 * comes from `plugin.json` through `BuildConfig`, so the harness hands the plugin the same id,
 * version, entry point, slots and slot configuration the real dataStore config would. A plugin that
 * reads `pluginMetadata.version` (this one does, for the chip on the card) therefore sees a real
 * value here, and `SlotArguments.appliesTo` answers what it would answer on a device.
 */
class HarnessPluginContext(
    override val sdk: D2,
) : Dhis2PluginContext {
    override val pluginMetadata: PluginMetadata = harnessPluginMetadata()
}

/** Top-level so [HarnessSession] can read the same record the plugin is handed, not a copy. */
fun harnessPluginMetadata(): PluginMetadata = PluginMetadata(
    id = BuildConfig.PLUGIN_ID,
    version = BuildConfig.PLUGIN_VERSION,
    entryPoint = BuildConfig.PLUGIN_ENTRY_POINT,
    injectionPoints = harnessInjectionPoints(),
    slotConfig = harnessSlotConfig(),
)

/**
 * The slots `plugin.json` declares.
 *
 * A name this `plugin-sdk` does not define is dropped rather than throwing — exactly what the host
 * does when it reads a config naming a slot its version has never heard of. `plugin.schema.json`
 * documents that behaviour; this is where the harness reproduces it.
 */
fun harnessInjectionPoints(): List<InjectionPoint> =
    BuildConfig.PLUGIN_INJECTION_POINTS
        .split(",")
        .mapNotNull { name -> InjectionPoint.entries.find { it.name == name.trim() } }

/** The per-slot configuration, keyed the way [PluginMetadata] keys it. */
fun harnessSlotConfig(): Map<InjectionPoint, JsonObject> = runCatching {
    Json.parseToJsonElement(BuildConfig.PLUGIN_SLOT_CONFIG).jsonObject
        .mapNotNull { (key, value) ->
            InjectionPoint.entries.find { it.name == key }?.to(value.jsonObject)
        }
        .toMap()
}.getOrDefault(emptyMap())

/**
 * The data set UIDs the replacement slot is configured for.
 *
 * Read back out of the slot config rather than carried separately, so there is one place the
 * harness learns this and it is the same place the plugin does.
 */
fun harnessDataSetUids(): List<String> = runCatching {
    harnessSlotConfig()[InjectionPoint.DATA_SET_INSTANCE_CONTENT]
        ?.get("dataSetUids")
        ?.let { element -> element.jsonArray.map { it.jsonPrimitive.content } }
        .orEmpty()
}.getOrDefault(emptyList())
