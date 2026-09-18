package org.dhis2.mobile.plugin.harness

import org.dhis2.mobile.plugin.sdk.DataSetInstanceSlotArguments
import org.dhis2.mobile.plugin.sdk.InjectionPoint
import org.dhis2.mobile.plugin.sdk.SlotArguments
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.arch.repositories.scope.RepositoryScope
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit

/**
 * Which slot the harness renders the plugin at, and what the host would tell it about the
 * occurrence.
 *
 * The host does this with a registry reading a server's dataStore config. The harness has no
 * registry, so it does the same job from `plugin.json` — which is the same file that produced the
 * config, so the two cannot disagree. Nothing here is a constant anyone edits.
 */

/** The outcome of choosing a slot. [Unavailable] carries a sentence worth showing a human. */
sealed interface SlotChoice {
    data class Chosen(val injectionPoint: InjectionPoint, val dataSetUid: String?) : SlotChoice
    data class Unavailable(val reason: String) : SlotChoice
}

/** The outcome of resolving that slot's arguments against a real database. */
sealed interface SlotResolution {
    /** `null` arguments are correct at a slot that has nothing to say about what is on screen. */
    data class Resolved(val arguments: SlotArguments?) : SlotResolution
    data class Unavailable(val reason: String) : SlotResolution
}

/**
 * Picks the slot, from `plugin.json` and an optional `local.properties` override.
 *
 * Deliberately pure — no `D2`. It runs before the SDK is even instantiated, so a typo in
 * `harness.slot` fails in a second rather than after a five-minute first-run metadata download.
 *
 * The rule: the override if there is one; otherwise the most specific slot the plugin could
 * actually be rendered at. A replacement wins when it is declared *and* configured, because a
 * replacement with an empty `dataSetUids` replaces nothing — that is the host's rule too
 * (`InjectionPoint.requiresConfiguration`), and a harness that rendered it anyway would be showing
 * you something the Capture App never would.
 */
fun chooseSlot(
    declared: List<InjectionPoint>,
    dataSetUids: List<String>,
    override: String,
): SlotChoice {
    if (declared.isEmpty()) {
        return SlotChoice.Unavailable(
            "plugin.json declares no injectionPoints, so there is no slot to render at. Add at " +
                "least one of ${InjectionPoint.entries.joinToString { it.name }}.",
        )
    }

    val requested = override.trim()
    if (requested.isNotEmpty()) {
        val point = InjectionPoint.entries.find { it.name.equals(requested, ignoreCase = true) }
            ?: return SlotChoice.Unavailable(
                "local.properties sets harness.slot=$requested, which is not a slot plugin-sdk " +
                    "defines. Known slots: ${InjectionPoint.entries.joinToString { it.name }}.",
            )
        if (point !in declared) {
            return SlotChoice.Unavailable(
                "local.properties sets harness.slot=${point.name}, but plugin.json does not " +
                    "declare it. The host only renders a plugin at slots its config names, so " +
                    "this would show you something a device never would. Declared: " +
                    declared.joinToString { it.name } + ".",
            )
        }
        return chosenAt(point, dataSetUids)
            ?: SlotChoice.Unavailable(
                "harness.slot=${point.name} is declared, but slotConfig." +
                    "${point.name}.dataSetUids is empty, so it replaces nothing. Add the UID of " +
                    "a data set on your server.",
            )
    }

    return declared
        .sortedByDescending { it.requiresConfiguration }
        .firstNotNullOfOrNull { chosenAt(it, dataSetUids) }
        ?: SlotChoice.Unavailable(
            "plugin.json declares only ${declared.joinToString { it.name }}, and every one of " +
                "them needs a slotConfig entry it does not have. Add the UIDs it applies to.",
        )
}

/** A slot is choosable when it needs no configuration, or has the configuration it needs. */
private fun chosenAt(point: InjectionPoint, dataSetUids: List<String>): SlotChoice.Chosen? = when {
    !point.requiresConfiguration -> SlotChoice.Chosen(point, dataSetUid = null)
    dataSetUids.isEmpty() -> null
    else -> SlotChoice.Chosen(point, dataSetUid = dataSetUids.first())
}

/**
 * Turns the chosen slot into the arguments the host would hand the plugin.
 *
 * The additive slot needs none — it is the whole home screen, and `null` is what says so.
 */
fun resolveSlotArguments(d2: D2, choice: SlotChoice.Chosen): SlotResolution =
    when (choice.injectionPoint) {
        InjectionPoint.HOME_ABOVE_PROGRAM_LIST -> SlotResolution.Resolved(null)
        InjectionPoint.DATA_SET_INSTANCE_CONTENT ->
            resolveDataSetInstance(d2, requireNotNull(choice.dataSetUid))
    }

/**
 * The four identifiers of a data set instance, for the data set `plugin.json` names.
 *
 * Two routes, in order. An instance that *exists* is the highest fidelity available, and is exactly
 * what a user would have opened on a device. Failing that, the coordinates are assembled from
 * metadata — correct, and enough to prove the plugin was selected and handed the right arguments.
 */
private fun resolveDataSetInstance(d2: D2, dataSetUid: String): SlotResolution {
    d2.dataSetModule().dataSetInstances()
        .byDataSetUid().eq(dataSetUid)
        .one().blockingGet()
        ?.let { instance ->
            return SlotResolution.Resolved(
                DataSetInstanceSlotArguments(
                    dataSetUid = instance.dataSetUid(),
                    periodId = instance.period(),
                    organisationUnitUid = instance.organisationUnitUid(),
                    attributeOptionComboUid = instance.attributeOptionComboUid(),
                ),
            )
        }

    // Must come first of the metadata reads. PeriodHelper resolves the data set internally through
    // a deprecated Rx accessor that throws a bare NullPointerException for a UID that is not there
    // — which would replace the named failure below with one naming nothing.
    val dataSet = d2.dataSetModule().dataSets().uid(dataSetUid).blockingGet()
        ?: return SlotResolution.Unavailable(
            "Data set $dataSetUid is not on ${BuildConfig.DHIS2_SERVER_URL}. It is what " +
                "plugin.json's slotConfig.DATA_SET_INSTANCE_CONTENT.dataSetUids names, and the " +
                "harness renders the instance it identifies — name a data set this server has, " +
                "or point dhis2.serverUrl at the server that has this one.",
        )

    val organisationUnitUid = d2.organisationUnitModule().organisationUnits()
        .byDataSetUids(listOf(dataSetUid))
        .byOrganisationUnitScope(OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
        .orderByDisplayName(RepositoryScope.OrderByDirection.ASC)
        .one().blockingGet()
        ?.uid()
        ?: return SlotResolution.Unavailable(unassignedReason(d2, dataSetUid))

    // Generated ascending, so the last is the newest period the data set is *open* for — this
    // honours openFuturePeriods and dataInputPeriods, which picking a date by hand does not.
    //
    // It writes: the helper stores the periods it generates in the local Period table. That is the
    // same local insert the host's own data set screen performs, not a download.
    val periodId = d2.periodModule().periodHelper()
        .blockingGetPeriodsForDataSet(dataSetUid)
        .lastOrNull()
        ?.periodId()
        ?: return SlotResolution.Unavailable(
            "Data set $dataSetUid has no periods to enter data for. Its period type is " +
                "${dataSet.periodType()}; check its data input periods on the server.",
        )

    // A data set on the `default` category combo has exactly one option combo, so this needs no
    // special case — the query returns it.
    val attributeOptionComboUid = d2.categoryModule().categoryOptionCombos()
        .byCategoryComboUid().eq(dataSet.categoryCombo().uid())
        .orderByDisplayName(RepositoryScope.OrderByDirection.ASC)
        .one().blockingGet()
        ?.uid()
        ?: return SlotResolution.Unavailable(
            "Category combo ${dataSet.categoryCombo().uid()} of data set $dataSetUid has no " +
                "option combos in the local database. Clear the app's data so metadata downloads " +
                "again.",
        )

    return SlotResolution.Resolved(
        DataSetInstanceSlotArguments(
            dataSetUid = dataSetUid,
            periodId = periodId,
            organisationUnitUid = organisationUnitUid,
            attributeOptionComboUid = attributeOptionComboUid,
        ),
    )
}

/**
 * "Assigned to nothing" and "assigned to nothing *you* can capture for" are different problems with
 * different fixes, and the query above cannot tell them apart. Re-counting without the user scope
 * costs one `COUNT(*)` on a path that is already failing, and turns a puzzling message into one
 * naming what to change.
 */
private fun unassignedReason(d2: D2, dataSetUid: String): String {
    val assignedAnywhere = d2.organisationUnitModule().organisationUnits()
        .byDataSetUids(listOf(dataSetUid))
        .blockingCount()

    return if (assignedAnywhere == 0) {
        "Data set $dataSetUid is on this server but assigned to no organisation unit. Assign it " +
            "to one, or name a data set that is already assigned."
    } else {
        "Data set $dataSetUid is assigned to $assignedAnywhere organisation unit(s), but none " +
            "that ${BuildConfig.DHIS2_USERNAME} can capture data for. Log in as a user with one " +
            "of them in their capture scope."
    }
}
