package org.dhis2.mobile.plugin.template.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.dhis2.mobile.plugin.template.model.PluginSummary

/**
 * `@Preview`s of [PluginCard], against state you supply.
 *
 * The fastest loop for pure UI work: a preview needs no server, no login and no sync. That is what
 * architecture rule 2 buys — composables take plain data and callbacks, never a
 * `Dhis2PluginContext`. For the plugin against real data, run the harness
 * (`./gradlew :app:installDebug`).
 *
 * These live beside the card rather than in `:app` so the harness names no plugin type at all,
 * which is what lets `harness.module` point it at any plugin module without an edit. In
 * `androidMain` because `@Preview` is an Android annotation; [PluginCard] itself stays in
 * `commonMain`.
 */

private const val PREVIEW_VERSION = "preview"

private val LOADED = PluginUiState(
    summary = SummaryState.Loaded(PluginSummary(programCount = 4)),
)

@Preview(showBackground = true, name = "Loaded")
@Composable
private fun LoadedPreview() {
    PluginCard(state = LOADED, pluginVersion = PREVIEW_VERSION)
}

@Preview(showBackground = true, name = "Loading")
@Composable
private fun LoadingPreview() {
    PluginCard(state = PluginUiState(), pluginVersion = PREVIEW_VERSION)
}

@Preview(showBackground = true, name = "Read failed")
@Composable
private fun FailedPreview() {
    PluginCard(
        state = PluginUiState(summary = SummaryState.Failed("[UNEXPECTED] no database")),
        pluginVersion = PREVIEW_VERSION,
    )
}
