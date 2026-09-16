package org.dhis2.mobile.plugin.template.model

/**
 * Everything this plugin knows about the DHIS2 database on this device, as plain data.
 *
 * Free of SDK types on purpose. That is what lets the state, the ViewModel and the Composables live
 * in `commonMain` and be tested on the JVM — a `D2` needs an Android `Context`, a database and an
 * HTTP stack, so anything holding one can only be exercised on a device.
 *
 * Replace this with your own model. It is the layer you will change first.
 */
data class PluginSummary(
    val programCount: Int,
)
