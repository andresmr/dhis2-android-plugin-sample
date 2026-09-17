package org.dhis2.mobile.plugin.template.repository

import org.dhis2.mobile.plugin.template.model.PluginSummary

/**
 * Everything this plugin needs from DHIS2, in the plugin's own vocabulary.
 *
 * The seam the whole design rests on: the implementation is the only file that touches `D2`, so
 * everything above it is unit-testable against a fake. When a scenario you want to automate keeps
 * needing a device, the fix is almost always to push the SDK call down here and return plain data.
 *
 * **Every function returns `Result`, and none of them throws.** An exception escaping into the host
 * composition takes the enclosing screen with it, and Compose cannot express an error boundary
 * around a composable call. `tools/check-rules.py` enforces this over every interface named in
 * `plugin.json`'s `conventions.repositoryInterfaces`.
 */
interface PluginRepository {
    suspend fun loadSummary(): Result<PluginSummary>
}
