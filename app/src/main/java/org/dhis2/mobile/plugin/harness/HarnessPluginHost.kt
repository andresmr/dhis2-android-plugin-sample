package org.dhis2.mobile.plugin.harness

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.dhis2.mobile.plugin.sdk.DataSetInstanceSlotArguments
import org.dhis2.mobile.plugin.sdk.Dhis2Plugin
import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.dhis2.mobile.plugin.sdk.LocalSlotArguments
import org.dhis2.mobile.plugin.sdk.LocalSlotContentPadding
import org.dhis2.mobile.plugin.sdk.SlotArguments
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2Theme
import org.koin.compose.KoinIsolatedContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Renders a plugin the way the Capture App does: inside the DHIS2 design system's theme, and inside
 * a private Koin container seeded with its context, metadata and `D2`, entered through
 * [KoinIsolatedContext] so the plugin's `koinViewModel` resolves there and nowhere else.
 *
 * Mirrors the host's own `PluginContainer.create` — a harness whose DI differs proves the wrong
 * thing, and so does one whose theme differs. [DHIS2Theme] is what makes `SurfaceColor`,
 * `TextColor` and the type scale resolve to the values the host would supply; without it a plugin
 * using them renders against Material's defaults here and something else entirely on a device,
 * which is worse than not testing the theme at all.
 *
 * Which slot the plugin is rendered at is [HARNESS_SLOT]. The harness does not filter by slot the
 * way the host's registry does — it renders whichever entry point `harness.module` names — so this
 * is a switch you flip, not something a configuration decides.
 */
@Composable
fun PluginHost(plugin: Dhis2Plugin, context: Dhis2PluginContext) {
    DHIS2Theme {
        KoinIsolatedContext(context = containerFor(plugin, context)) {
            CompositionLocalProvider(
                LocalSlotArguments provides HARNESS_SLOT,
                LocalSlotContentPadding provides slotContentPadding(),
            ) {
                when (HARNESS_SLOT) {
                    // An additive slot: the host gives it as much height as it takes, above its own
                    // scrolling content, and MainActivity's scrolling Column is that arrangement.
                    null -> plugin.content(context)

                    // A replacement slot: the host gives it a bounded region of its own screen. The
                    // bound is not cosmetic — MainActivity scrolls vertically, so it measures its
                    // children with an infinite maximum height, and a replacement that fills its
                    // region and scrolls inside it then dies with "Vertically scrollable component
                    // was measured with an infinity maximum height constraints".
                    else -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(REPLACEMENT_SLOT_HEIGHT),
                    ) {
                        plugin.content(context)
                    }
                }
            }
        }
    }
}

/**
 * The space the host's own chrome would occupy over a replacement's content — its floating save
 * button. Zero at an additive slot, which has no chrome over it.
 *
 * Worth providing rather than leaving at the default: a plugin that forgets to apply it looks
 * correct until the last row of a real form turns out to be unreachable on a device.
 */
private fun slotContentPadding(): PaddingValues = when (HARNESS_SLOT) {
    null -> PaddingValues()
    else -> PaddingValues(bottom = SAVE_BUTTON_HEIGHT)
}

/**
 * Which slot to render the plugin at, and with what the host would tell it about the occurrence.
 *
 * `null` renders it at `HOME_ABOVE_PROGRAM_LIST`, which needs no arguments — the slot is the whole
 * home screen. A [SlotArguments] renders it at that slot instead.
 *
 * The UIDs are yours to change. They can be anything while a plugin only displays them; once it
 * queries the instance they have to exist on the server the harness logged into, because the `D2`
 * here is a real session and a wrong UID reads as empty rather than failing.
 */
private val HARNESS_SLOT: SlotArguments? = DataSetInstanceSlotArguments(
    dataSetUid = "lyLU2wR22tC",
    periodId = "202401",
    organisationUnitUid = "DiszpKrYNg8",
    attributeOptionComboUid = "HllvX50cXC0",
)

/** Stands in for the region the host's data set screen gives its body. */
private val REPLACEMENT_SLOT_HEIGHT = 500.dp

/** The Capture App's floating save button: 56dp plus its 16dp margins. */
private val SAVE_BUTTON_HEIGHT = 88.dp

private fun containerFor(plugin: Dhis2Plugin, context: Dhis2PluginContext): KoinApplication =
    koinApplication {
        modules(
            listOfNotNull(
                module {
                    single { context }
                    single { context.pluginMetadata }
                    single { context.sdk }
                },
                plugin.provideKoinModule(),
            ),
        )
    }
