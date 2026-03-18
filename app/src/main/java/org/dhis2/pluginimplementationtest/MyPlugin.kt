package org.dhis2.pluginimplementationtest

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.dhis2.mobile.plugin.sdk.Dhis2Plugin
import org.dhis2.mobile.plugin.sdk.Dhis2PluginContext
import org.dhis2.mobile.plugin.sdk.InjectionPoint
import org.dhis2.mobile.plugin.sdk.PluginMetadata

class MyPlugin : Dhis2Plugin {
    override val metadata = PluginMetadata(
        id = "my-plugin",
        version = "1.0.0",
        entryPoint = "com.example.MyPlugin",
        downloadUrl = "https://apphub.dhis2.org/...",
        checksum = "sha256:...",
        injectionPoints = listOf(InjectionPoint.HOME_ABOVE_PROGRAM_LIST),
    )

    override fun provideKoinModule() = null // or a Koin module

    @Composable
    override fun content(context: Dhis2PluginContext) {
        TODO("Not yet implemented")
    }

//    @Composable
//    override fun Content(point: InjectionPoint, context: Dhis2PluginContext) {
//        Text("Hello from MyPlugin!")
//    }
}