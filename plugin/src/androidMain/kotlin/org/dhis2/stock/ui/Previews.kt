package org.dhis2.stock.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.dhis2.stock.model.DataSetSection
import org.dhis2.stock.model.PeriodLabel
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2Theme

/**
 * `@Preview`s of [DataSetSectionsScreen], against state you supply.
 *
 * The fastest loop for pure UI work: a preview needs no server, no login and no sync. That is what
 * architecture rule 2 buys — composables take plain data and callbacks, never a
 * `Dhis2PluginContext`. For the plugin against real data, run the harness
 * (`./gradlew :app:installDebug`).
 *
 * These live beside the screen rather than in `:app`, so the harness names no plugin type at all
 * and loads the entry point reflectively exactly as the host does. In `androidMain` because
 * `@Preview` is an Android annotation; [DataSetSectionsScreen] itself stays in `commonMain`.
 */

private val SECTIONS = listOf(
    DataSetSection(uid = "sectionA", displayName = "Stock on hand", sortOrder = 1),
    DataSetSection(uid = "sectionB", displayName = "Issued", sortOrder = 2),
    DataSetSection(uid = "sectionC", displayName = "Discarded", sortOrder = 3),
    DataSetSection(uid = "sectionD", displayName = "Expired and returned to store", sortOrder = 4),
)

@Preview(showBackground = true, name = "Sections", heightDp = 240)
@Composable
private fun LoadedPreview() {
    DHIS2Theme {
        DataSetSectionsScreen(
            period = PeriodLabel.Month(2024, 1),
            sections = SectionsState.Loaded(
                sections = SECTIONS,
                selectedSectionUid = "sectionA",
            ),
            onSectionSelected = {},
        )
    }
}

/** The one a data set without sections renders: the period, and nothing else. */
@Preview(showBackground = true, name = "No sections", heightDp = 240)
@Composable
private fun NoSectionsPreview() {
    DHIS2Theme {
        DataSetSectionsScreen(
            period = PeriodLabel.Quarter(2024, 1),
            sections = SectionsState.Loaded(sections = emptyList(), selectedSectionUid = null),
            onSectionSelected = {},
        )
    }
}

/** A period type this plugin does not format, shown as the host gave it. */
@Preview(showBackground = true, name = "Unformatted period", heightDp = 240)
@Composable
private fun RawPeriodPreview() {
    DHIS2Theme {
        DataSetSectionsScreen(
            period = PeriodLabel.Raw("2024April"),
            sections = SectionsState.Loaded(sections = SECTIONS, selectedSectionUid = "sectionB"),
            onSectionSelected = {},
        )
    }
}

@Preview(showBackground = true, name = "Loading", heightDp = 240)
@Composable
private fun LoadingPreview() {
    DHIS2Theme {
        DataSetSectionsScreen(
            period = PeriodLabel.Month(2024, 1),
            sections = SectionsState.Loading,
            onSectionSelected = {},
        )
    }
}

@Preview(showBackground = true, name = "Read failed", heightDp = 240)
@Composable
private fun FailedPreview() {
    DHIS2Theme {
        DataSetSectionsScreen(
            period = PeriodLabel.Month(2024, 1),
            sections = SectionsState.Failed("[UNEXPECTED] no database"),
            onSectionSelected = {},
        )
    }
}
