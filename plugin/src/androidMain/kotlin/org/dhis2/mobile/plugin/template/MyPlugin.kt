package org.dhis2.mobile.plugin.template

import androidx.compose.runtime.Composable
import org.dhis2.mobile.plugin.sdk.DataSetInstanceSlotArguments
import org.dhis2.mobile.plugin.sdk.Dhis2Plugin
import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.dhis2.mobile.plugin.sdk.LocalSlotArguments
import org.dhis2.mobile.plugin.sdk.SlotArguments
import org.dhis2.mobile.plugin.template.data.D2PluginRepository
import org.dhis2.mobile.plugin.template.repository.PluginRepository
import org.dhis2.mobile.plugin.template.slots.DataSetInstanceSlot
import org.dhis2.mobile.plugin.template.slots.HomeSlot
import org.dhis2.mobile.plugin.template.ui.PluginViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * This plugin's entry point, and deliberately nothing more: it declares its dependencies and
 * renders a Composable against a ViewModel.
 *
 * Lives in `androidMain` because [Dhis2PluginContext.sdk] is `D2` — the DHIS2 *Android* SDK, which
 * has no common-source equivalent.
 *
 * **It must sit at the fully-qualified name `plugin.json` declares**, and it must have a public
 * no-argument constructor: the host instantiates it with `Class.forName(...).newInstance()`.
 * `tools/check-identity.py` checks the name; the development harness checks the constructor, by
 * loading it exactly the same way (see `MainActivity`).
 *
 * Note what is absent: no id, no version, no injection points. Those belong to the DHIS2
 * administrator, in the server dataStore, and arrive through [Dhis2PluginContext.pluginMetadata].
 * A plugin that declared its own identity could rename itself into someone else's configuration.
 */
class MyPlugin : Dhis2Plugin {

    /**
     * The plugin's own bindings, loaded into a container private to this plugin.
     *
     * `get()` resolves the host's `D2`, which it seeded for us. Nothing bound here is visible to
     * the host or to another plugin.
     */
    override fun provideKoinModule() = module {
        single<PluginRepository> { D2PluginRepository(get()) }
        viewModel { PluginViewModel(get()) }
    }

    /**
     * One entry point, however many slots the plugin is configured for.
     *
     * `LocalSlotArguments` is what says which one is being rendered right now, and [slotFor] turns
     * it into the answer. The registry only renders a plugin at slots its server config declares,
     * so neither has to defend against a slot the plugin did not ask for.
     *
     * Each slot's own rendering lives in `slots/`, one file each, so adding a slot is adding a file
     * rather than growing this class. A slot your plugin does not declare is an unused file you can
     * delete once you are sure — not a branch you have to read past.
     */
    @Composable
    override fun content(context: Dhis2PluginContext) {
        when (val slot = slotFor(LocalSlotArguments.current)) {
            Slot.Home -> HomeSlot(context)
            is Slot.DataSetInstance -> DataSetInstanceSlot(slot.arguments, context)
        }
    }
}

/** Which of this plugin's slots is on screen. */
sealed interface Slot {
    /** A slot with nothing to say about what is on screen — the home slot is the whole screen. */
    data object Home : Slot

    data class DataSetInstance(val arguments: DataSetInstanceSlotArguments) : Slot
}

/**
 * The slot the host's arguments name.
 *
 * A plain function rather than a `when` inside `content`, so the mapping can be asserted without a
 * composition — there is no Compose UI test infrastructure here, and a branch nothing can reach is
 * a branch nothing checks.
 */
fun slotFor(arguments: SlotArguments?): Slot = when (arguments) {
    is DataSetInstanceSlotArguments -> Slot.DataSetInstance(arguments)
    else -> Slot.Home
}
