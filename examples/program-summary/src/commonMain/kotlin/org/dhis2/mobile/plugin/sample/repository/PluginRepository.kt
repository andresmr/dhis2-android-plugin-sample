package org.dhis2.mobile.plugin.sample.repository

import org.dhis2.mobile.plugin.sample.model.ProgramSummary

/**
 * Everything the plugin needs from DHIS2, in the plugin's own vocabulary.
 *
 * This interface is the seam that makes the plugin testable. The implementation is the only file
 * that touches `D2`, so everything above it can be unit-tested on the JVM against a fake.
 *
 * Every method returns [Result] rather than throwing. A plugin's failures have to stay inside the
 * plugin: an exception escaping into the host composition takes the enclosing screen with it, and
 * Compose cannot express an error boundary around a composable call.
 */
interface PluginRepository {
    /**
     * Summarises the tracker programme this plugin reports on.
     *
     * Takes no UID on purpose. A plugin is told which *code* to run and nothing else — the dataStore
     * config carries no programme field — so choosing the programme is the repository's job, and it
     * resolves one from the server's own metadata. [ProgramSummary.programUid] reports which.
     */
    suspend fun loadSummary(): Result<ProgramSummary>
}
