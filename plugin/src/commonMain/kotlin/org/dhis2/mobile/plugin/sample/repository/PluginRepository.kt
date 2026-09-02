package org.dhis2.mobile.plugin.sample.repository

import org.dhis2.mobile.plugin.sample.model.ProgramSummary
import org.dhis2.mobile.plugin.sample.model.WriteTarget

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
    suspend fun loadSummary(programUid: String): Result<ProgramSummary>

    /** Creates one event, returning its UID — the simplest proof that writes work too. */
    suspend fun addEvent(target: WriteTarget): Result<String>
}
