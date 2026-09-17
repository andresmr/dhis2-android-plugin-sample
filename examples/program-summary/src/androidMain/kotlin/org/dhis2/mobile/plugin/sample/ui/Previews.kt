package org.dhis2.mobile.plugin.sample.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.dhis2.mobile.plugin.sample.model.EnrolledPerson
import org.dhis2.mobile.plugin.sample.model.ProgramSummary
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2Theme

/**
 * `@Preview`s of [PluginCard], against state you supply.
 *
 * Under [DHIS2Theme], because that is what the host applies — without it the design-system tokens
 * resolve to Material's defaults here and to something else on a device, which makes the preview
 * worse than no preview.
 *
 * These were briefly in `:app` and belong here: the harness must name no plugin type, or pointing
 * `harness.module` at a different module stops compiling. They came back when this example adopted
 * the design system, since the whole reason to preview is to see the tokens land.
 */

private const val PREVIEW_VERSION = "preview"

/**
 * The uids are deliberately unmistakable rather than real DHIS2 demo uids. A fixture carrying
 * `IpHINAT79UW` reads as though the value matters, and that is how a hardcoded uid gets copied out
 * of a preview into something that ships.
 */
private val SAMPLE = ProgramSummary(
    programUid = "sample-programme",
    programName = "Child Programme",
    enrolledCount = 27,
    eventCount = 41,
    recent = listOf(
        EnrolledPerson(uid = "sample-person-1", displayLabel = "Alice Morgan"),
        EnrolledPerson(uid = "sample-person-2", displayLabel = "Bilal Khan"),
    ),
)

@Preview(showBackground = true, name = "Loaded")
@Composable
private fun LoadedPreview() {
    DHIS2Theme {
        PluginCard(
            state = PluginUiState(summary = SummaryState.Loaded(SAMPLE)),
            pluginVersion = PREVIEW_VERSION,
        )
    }
}

@Preview(showBackground = true, name = "Loaded, capped with more to come")
@Composable
private fun CappedPreview() {
    // enrolledCount well above the rows carried, which is what puts the "… and N more" line on
    // screen — the one part of this card derived rather than reported.
    DHIS2Theme {
        PluginCard(
            state = PluginUiState(
                summary = SummaryState.Loaded(SAMPLE.copy(enrolledCount = 499)),
            ),
            pluginVersion = PREVIEW_VERSION,
        )
    }
}

@Preview(showBackground = true, name = "Loading")
@Composable
private fun LoadingPreview() {
    DHIS2Theme { PluginCard(state = PluginUiState(), pluginVersion = PREVIEW_VERSION) }
}

@Preview(showBackground = true, name = "Read failed")
@Composable
private fun FailedPreview() {
    DHIS2Theme {
        PluginCard(
            state = PluginUiState(summary = SummaryState.Failed("[UNEXPECTED] no such program")),
            pluginVersion = PREVIEW_VERSION,
        )
    }
}
