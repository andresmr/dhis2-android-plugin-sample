package org.dhis2.pluginimplementationtest.model

/**
 * What the server granted, next to what the SDK actually hands back.
 *
 * The comparison is the point. An out-of-scope read returns *empty*, never an error, so a config
 * naming the wrong UID looks exactly like a database with no data. Showing both columns turns that
 * silent failure into an obvious one.
 */
data class ScopeSnapshot(
    /** Capability names from the config. There is no "visible" counterpart — they are all-or-nothing. */
    val capabilities: List<String>,
    val dimensions: List<ScopeDimension>,
) {
    /** Programs the plugin can actually see, which is what the tracker half operates on. */
    val visiblePrograms: List<MetadataItem>
        get() = visible(PROGRAMS)

    /** Data sets the plugin can actually see, which is what the aggregate half operates on. */
    val visibleDataSets: List<MetadataItem>
        get() = visible(DATA_SETS)

    private fun visible(label: String): List<MetadataItem> =
        dimensions.firstOrNull { it.label == label }?.visible.orEmpty()

    /**
     * Everything wrong with this grant, one line each.
     *
     * Hoisted to the snapshot so it can be shown while the scope detail is collapsed. The card is
     * short by default, but a grant that is not doing what the administrator intended is the one
     * thing that must never be hidden behind a tap.
     */
    val warnings: List<String>
        get() = dimensions.mapNotNull { dimension ->
            when {
                // Naming the UID is strictly more useful than saying "nothing came back".
                dimension.missing.isNotEmpty() ->
                    "${dimension.label}: declared but not visible — ${dimension.missing.joinToString(", ")}"

                dimension.grantedButEmpty ->
                    "${dimension.label}: granted, but nothing is visible on this device"

                else -> null
            }
        }

    companion object {
        const val PROGRAMS = "Programs"
        const val DATA_SETS = "Data sets"
    }
}

/** One dimension of the grant — programs, data sets, org units, tracked entity types. */
data class ScopeDimension(
    val label: String,
    val declared: DeclaredGrant,
    val visible: List<MetadataItem>,
) {
    /**
     * UIDs the config named that the SDK does not return.
     *
     * Three possible causes, all worth surfacing: the UID is wrong, the metadata has not synced to
     * this device, or the capability that would expose it was not granted.
     */
    val missing: List<String>
        get() = when (declared) {
            is DeclaredGrant.Uids -> declared.uids.filterNot { uid -> visible.any { it.uid == uid } }
            // A root is not expected to appear itself, only to resolve to something.
            is DeclaredGrant.Roots -> emptyList()
            // These name no UIDs, so there is nothing that could fail to appear.
            DeclaredGrant.All, DeclaredGrant.None, DeclaredGrant.Capture -> emptyList()
        }

    /**
     * True when the config asked for something and got nothing back.
     *
     * The most common misconfiguration, and indistinguishable from "no data" without this flag. A
     * deliberately closed dimension is not flagged — `none` yielding nothing is correct.
     */
    val grantedButEmpty: Boolean
        get() = declared != DeclaredGrant.None && visible.isEmpty()
}

/** A metadata object the plugin can see, with a name so nothing renders as a bare UID. */
data class MetadataItem(
    val uid: String,
    val displayName: String,
)

/** How a dimension was granted in the dataStore config. */
sealed interface DeclaredGrant {
    /** `{"all": true}` — every object of this type. */
    data object All : DeclaredGrant

    /** Omitted or empty, which grants nothing. Closed by default is the rule everywhere. */
    data object None : DeclaredGrant

    /** `{"capture": true}` — the logged-in user's capture org units, resolved on the device. */
    data object Capture : DeclaredGrant

    /** Named UIDs of the objects themselves — expected to appear in `visible` one for one. */
    data class Uids(val uids: List<String>) : DeclaredGrant

    /**
     * Hierarchy **roots** plus a mode, which is how org units are granted.
     *
     * Semantically different from [Uids] and worth its own case: the declared UID is a starting
     * point, not something expected to show up. A root above the device's own subtree is often not
     * stored locally at all, so treating its absence as a misconfiguration cries wolf about a grant
     * that is working correctly.
     */
    data class Roots(val uids: List<String>, val mode: String) : DeclaredGrant
}

/** Short label for the declared column. */
fun DeclaredGrant.describe(): String = when (this) {
    DeclaredGrant.All -> "all"
    DeclaredGrant.None -> "none"
    DeclaredGrant.Capture -> "capture units"
    is DeclaredGrant.Uids -> "${uids.size} declared"
    is DeclaredGrant.Roots -> "${uids.size} root${if (uids.size == 1) "" else "s"}, $mode"
}
