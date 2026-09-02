@file:Suppress("DEPRECATION")

import java.io.File
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

/** Harness credentials, from gitignored local.properties.
 * Enter credentials to test the plugin:
 * dhis2.serverUrl=
 * dhis2.username=
 * dhis2.password=
 **/
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun harnessProperty(key: String): String = localProperties.getProperty(key).orEmpty()

// ──────────────────────────────────────────────────────────────────────────────
// Harness plumbing: stage `:plugin`'s Compose Multiplatform resources into this
// app's assets directory at `composeResources/{package}/…` so CMP's
// DefaultAndroidResourceReader (which reads via `Context.assets.open(path)`)
// can find them when MainActivity instantiates ProgramOverviewPlugin directly for preview.
//
// In production (Capture App host), the host's PluginSlot injects a filesystem-
// backed ResourceReader — we don't need AssetManager there. The harness skips
// that pipeline, so we mimic what AGP normally does for CMP-library consumers.
// ──────────────────────────────────────────────────────────────────────────────

val pluginResourcePackage = "org.dhis2.mobile.plugin.sample.generated.resources"
val pluginProject = project(":plugin")

abstract class StagePluginAssets : DefaultTask() {
    @get:InputDirectory
    abstract val source: DirectoryProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        val target = File(out, "composeResources/${packageName.get()}")
        target.mkdirs()
        source.get().asFile.copyRecursively(target, overwrite = true)
    }
}

val stagePluginAssets by tasks.registering(StagePluginAssets::class) {
    dependsOn(pluginProject.tasks.named("prepareComposeResourcesTaskForCommonMain"))
    source.set(
        pluginProject.layout.buildDirectory.dir(
            "generated/compose/resourceGenerator/preparedResources/commonMain/composeResources",
        ),
    )
    packageName.set(pluginResourcePackage)
    outputDir.set(layout.buildDirectory.dir("generated/plugin-assets"))
}

android {
    namespace = "org.dhis2.mobile.plugin.sample.harness"
    // Must be >= the Capture App host's compileSdk, since plugin-sdk is compiled against it.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.dhis2.mobile.plugin.sample.harness"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "DHIS2_SERVER_URL", "\"${harnessProperty("dhis2.serverUrl")}\"")
        buildConfigField("String", "DHIS2_USERNAME", "\"${harnessProperty("dhis2.username")}\"")
        buildConfigField("String", "DHIS2_PASSWORD", "\"${harnessProperty("dhis2.password")}\"")
        buildConfigField("String", "PLUGIN_PROGRAM_UID", "\"${harnessProperty("dhis2.programUid")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // android-core's AAR metadata requires this of every consumer, and the harness is now one.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// AGP 9 Sources API: register the staged plugin assets as an extra assets source
// directory for every variant. Gradle wires task dependencies automatically when
// the Provider is passed in.
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            stagePluginAssets,
            StagePluginAssets::outputDir,
        )
    }
}

// Harness deps — Compose Multiplatform 1.10.3, matching the :plugin module and the
// real DHIS2 Capture App host. NOT the Google AndroidX Compose BOM — those two ABIs
// are incompatible and crash the plugin with NoSuchMethodError at composition time.
dependencies {
    implementation(project(":plugin"))
    implementation(libs.plugin.sdk)
    // A real dependency here, not compileOnly: :app is the harness, not a shipped plugin, and it is
    // the thing that constructs the D2 the plugin is handed.
    implementation(libs.dhis2.android.core)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    // The harness has to reproduce the host's private container, because the plugin resolves its
    // ViewModel with koinViewModel() and would otherwise find no Koin at all.
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)

    // Android / lifecycle integration — not Compose proper; compatible with CMP.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose Multiplatform (same artifacts the plugin is compiled against).
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.material3)
    implementation(compose.components.resources)

    // Compose tooling (@Preview + inspector). Kept on direct coordinates to avoid
    // deprecated CMP extension accessors.
    debugImplementation("org.jetbrains.compose.ui:ui-tooling:1.10.3")
    implementation("org.jetbrains.compose.ui:ui-tooling-preview:1.10.3")

}
