package org.dhis2.pluginimplementationtest

import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.dhis2.mobile.plugin.sdk.PluginMetadata
import org.dhis2.mobile.plugin.sdk.dto.DataValueDto
import org.dhis2.mobile.plugin.sdk.dto.TrackedEntityInstanceDto

/**
 * Stub implementation of [Dhis2PluginContext] for local development and testing.
 *
 * In production the host app provides [ScopedDhis2PluginContext] backed by the real D2 SDK.
 * This stub returns empty results so the plugin UI can be previewed without a live server.
 */
class StubDhis2PluginContext(override val pluginMetadata: PluginMetadata) : Dhis2PluginContext {

    override suspend fun getTrackedEntityInstances(
        programUid: String,
    ): Result<List<TrackedEntityInstanceDto>> = Result.success(emptyList())

    override suspend fun getDataValues(
        orgUnitUid: String,
        dataSetUid: String,
        period: String,
    ): Result<List<DataValueDto>> = Result.success(emptyList())

    override suspend fun saveDataValue(
        dataSetUid: String,
        dataValue: DataValueDto,
    ): Result<Unit> = Result.success(Unit)
}
