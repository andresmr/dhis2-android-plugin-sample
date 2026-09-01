package org.dhis2.pluginimplementationtest.di

import org.dhis2.pluginimplementationtest.model.DataSetSummary
import org.dhis2.pluginimplementationtest.model.DataValueTarget
import org.dhis2.pluginimplementationtest.model.ProgramSummary
import org.dhis2.pluginimplementationtest.model.ScopeSnapshot
import org.dhis2.pluginimplementationtest.model.SearchProbeRun
import org.dhis2.pluginimplementationtest.model.WriteTarget
import org.dhis2.pluginimplementationtest.repository.PluginRepository
import org.dhis2.pluginimplementationtest.ui.PluginViewModel
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * The plugin's DI graph, checked without a device.
 *
 * A missing binding is otherwise found at render time on the emulator, which is a slow way to learn
 * about a typo. The real module lives in `androidMain` because it names the SDK-backed repository,
 * so this asserts the shape it has to satisfy: given the seeded gateway, a [PluginViewModel] must be
 * constructible.
 */
class PluginModuleTest {

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun `build a ViewModel from a repository binding`() {
        val app = koinApplication {
            modules(
                module {
                    single<PluginRepository> { FakeRepository() }
                    factory { PluginViewModel(get()) }
                },
            )
        }

        try {
            assertNotNull(app.koin.get<PluginViewModel>())
        } finally {
            app.close()
        }
    }

    private class FakeRepository : PluginRepository {
        override suspend fun loadScope(): Result<ScopeSnapshot> =
            Result.failure(UnsupportedOperationException())

        override suspend fun loadSummary(programUid: String): Result<ProgramSummary> =
            Result.failure(UnsupportedOperationException())

        override suspend fun addEvent(target: WriteTarget): Result<String> =
            Result.failure(UnsupportedOperationException())

        override suspend fun probeSearch(programUid: String): Result<SearchProbeRun> =
            Result.failure(UnsupportedOperationException())

        override suspend fun loadDataSetSummary(dataSetUid: String): Result<DataSetSummary> =
            Result.failure(UnsupportedOperationException())

        override suspend fun writeDataValue(target: DataValueTarget): Result<String> =
            Result.failure(UnsupportedOperationException())
    }
}
