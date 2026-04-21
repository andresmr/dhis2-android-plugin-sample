package org.dhis2.pluginimplementationtest

import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.dhis2.mobile.plugin.sdk.PluginMetadata
import org.dhis2.mobile.plugin.sdk.dto.DataValueDto
import org.dhis2.mobile.plugin.sdk.dto.TrackedEntityInstanceDto

/**
 * Stub implementation of [Dhis2PluginContext] for local development and testing.
 *
 * In production the host app provides `ScopedDhis2PluginContext` backed by the real D2 SDK.
 * This stub returns a small fake fixture for the well-known DHIS2 Child Programme so the
 * plugin UI renders something useful in `:app:installDebug` previews without a live server.
 */
class StubDhis2PluginContext(override val pluginMetadata: PluginMetadata) : Dhis2PluginContext {

    override suspend fun getTrackedEntityInstances(
        programUid: String,
    ): Result<List<TrackedEntityInstanceDto>> {
        if (programUid !in pluginMetadata.allowedProgramUids) {
            return Result.failure(SecurityException("program $programUid not in allow-list"))
        }
        return Result.success(FAKE_TEIS[programUid].orEmpty())
    }

    override suspend fun getDataValues(
        orgUnitUid: String,
        dataSetUid: String,
        period: String,
    ): Result<List<DataValueDto>> = Result.success(emptyList())

    override suspend fun saveDataValue(
        dataSetUid: String,
        dataValue: DataValueDto,
    ): Result<Unit> = Result.success(Unit)

    private companion object {
        val FAKE_TEIS = mapOf(
            "IpHINAT79UW" to listOf(
                TrackedEntityInstanceDto(
                    uid = "qTgINZ9tOtV",
                    programUid = "IpHINAT79UW",
                    attributes = mapOf("firstName" to "Alice", "lastName" to "Morgan"),
                ),
                TrackedEntityInstanceDto(
                    uid = "rJYd0Wn4p4f",
                    programUid = "IpHINAT79UW",
                    attributes = mapOf("firstName" to "Bilal", "lastName" to "Khan"),
                ),
                TrackedEntityInstanceDto(
                    uid = "sKm5pxN2h7g",
                    programUid = "IpHINAT79UW",
                    attributes = mapOf("firstName" to "Clara", "lastName" to "Santos"),
                ),
            ),
        )
    }
}
