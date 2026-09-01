package org.dhis2.pluginimplementationtest.repository

/**
 * The granted scope refused this operation.
 *
 * A domain failure, deliberately not `D2Error`. The implementation translates
 * `D2Error(SCOPE_VIOLATION)` into this, so everything above the repository can tell "refused" from
 * "broken" without importing the DHIS2 Android SDK — which is what keeps the ViewModel in
 * `commonMain` and unit-testable.
 */
class ScopeViolation(override val message: String) : Exception(message)
