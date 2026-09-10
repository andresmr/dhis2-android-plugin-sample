package org.dhis2.mobile.plugin.sample.model

/**
 * How many enrolled people the card lists before collapsing the rest into "… and N more".
 *
 * One constant, three callers, and — this is the point — three different promises:
 *
 * - [org.dhis2.mobile.plugin.sample.ui.PluginViewModel] keeps the host's non-scrolling column
 *   intact. That is a promise made to the host, and the only one a unit test can reach: a fake
 *   repository can hand the ViewModel fifty rows and a test can watch what comes out.
 * - `PluginCard` is the backstop for anything that bypasses the ViewModel — a `@Preview`, the
 *   harness, a future screenshot test.
 * - `D2PluginRepository` avoids resolving rows nobody will see. An efficiency measure, not a
 *   promise, and it lives in `androidMain` behind a `D2` no unit test can construct.
 *
 * The duplication is deliberate. Two unshared private threes — one in the card, one in the
 * repository — is how the ViewModel came to enforce none of it, and a limit enforced only where no
 * test can reach it is not enforced, it is hoped for.
 *
 * It lives in `model/` rather than `ui/` so `androidMain/data` can share it without importing `ui`.
 */
const val MAX_LISTED_PEOPLE = 3
