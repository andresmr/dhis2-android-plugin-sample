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

/**
 * A Java string literal, for a value that may itself contain quotes.
 *
 * `buildConfigField` pastes its third argument into `BuildConfig.java` verbatim, so the usual
 * `"\"$value\""` idiom works only for values with no quote in them. `slotConfig` is JSON, which is
 * nothing but quotes: unescaped, it closes the literal early and the build fails with a syntax
 * error in a generated file that never mentions plugin.json.
 */
fun quote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

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

// Identity from plugin.json, via settings.gradle.kts. The harness never spells the plugin's
// package, id or entry point itself — it is handed them, exactly as the Capture App is handed them
// by the server dataStore.
val dhis2PluginName: String by extra
val dhis2PluginId: String by extra
val dhis2PluginEntryPointFqcn: String by extra
val dhis2PluginVersion: String by extra
val dhis2ResourcePackage: String by extra
val dhis2HarnessApplicationId: String by extra

// Which slots this plugin declares, and how they are configured — both straight from plugin.json.
// The harness renders the slot these name (see HarnessSlot.kt) instead of a constant someone edits,
// and hands the plugin the same metadata a server dataStore would.
val dhis2InjectionPoints: String by extra
val dhis2SlotConfigJson: String by extra

// Must equal :plugin's packageOfResClass, or the staged assets land at a path CMP's reader never
// looks in and every Res.string.* resolves to nothing — with no error anywhere. Both come from
// plugin.json, which is the only reason that sentence is now a fact rather than a hope.
val pluginResourcePackage = dhis2ResourcePackage

// One plugin per fork, so there is only ever one module to host.
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
    // Fixed and template-owned: this module is harness infrastructure and is never shipped, so
    // its Kotlin package does not follow the fork's. Only applicationId carries the fork's identity,
    // which is what lets two forks' harnesses sit on one device at once.
    namespace = "org.dhis2.mobile.plugin.harness"
    // Must be >= the Capture App host's compileSdk, since plugin-sdk is compiled against it.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = dhis2HarnessApplicationId
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "DHIS2_SERVER_URL", "\"${harnessProperty("dhis2.serverUrl")}\"")
        buildConfigField("String", "DHIS2_USERNAME", "\"${harnessProperty("dhis2.username")}\"")
        buildConfigField("String", "DHIS2_PASSWORD", "\"${harnessProperty("dhis2.password")}\"")

        // The plugin's identity, so MainActivity can load it by name the way the host does rather
        // than importing its entry point. See MainActivity.kt.
        buildConfigField("String", "PLUGIN_NAME", "\"$dhis2PluginName\"")
        buildConfigField("String", "PLUGIN_ID", "\"$dhis2PluginId\"")
        buildConfigField("String", "PLUGIN_ENTRY_POINT", "\"$dhis2PluginEntryPointFqcn\"")
        buildConfigField("String", "PLUGIN_VERSION", "\"$dhis2PluginVersion\"")

        // The slots plugin.json declares, and their configuration. These are what the harness picks
        // a slot from, and what it hands the plugin as PluginMetadata — so `appliesTo()` here
        // answers what it would answer on a device.
        buildConfigField("String", "PLUGIN_INJECTION_POINTS", "\"$dhis2InjectionPoints\"")
        buildConfigField("String", "PLUGIN_SLOT_CONFIG", quote(dhis2SlotConfigJson))

        // Optional override for which slot to render, when plugin.json declares more than one.
        // local.properties, because which slot *you* are working on is not a property of the plugin.
        buildConfigField("String", "HARNESS_SLOT", "\"${harnessProperty("harness.slot")}\"")
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
    implementation(pluginProject)
    implementation(libs.plugin.sdk)
    // PluginMetadata.slotConfig is Map<InjectionPoint, JsonObject>, and plugin-sdk publishes
    // kotlinx-serialization-json in androidRuntimeElements only — never on a consumer's *compile*
    // classpath. Without this, naming JsonObject here fails to compile with an error that says
    // nothing about Gradle variants. Pinned to what plugin-sdk's own metadata requires.
    implementation(libs.kotlinx.serialization.json)
    // A real dependency here, not compileOnly: :app is the harness, not a shipped plugin, and it is
    // the thing that constructs the D2 the plugin is handed.
    implementation(libs.dhis2.android.core)
    // Real, not compileOnly: :app is the harness, and the harness is what plays the host's part —
    // it has to actually provide what the Capture App provides.
    implementation(libs.dhis2.mobile.designsystem)
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

    // The harness's own unit tests. Only its pure logic is reachable — everything past choosing a
    // slot needs a real D2, which no JVM test can construct.
    // kotlin("test") alone leaves kotlin.test.Test unbound in an Android module — the annotation is
    // a typealias onto whichever framework is present, and none is by default.
    testImplementation(kotlin("test-junit"))

    // Compose tooling (@Preview + inspector). Kept on direct coordinates to avoid
    // deprecated CMP extension accessors.
    debugImplementation("org.jetbrains.compose.ui:ui-tooling:1.10.3")
    implementation("org.jetbrains.compose.ui:ui-tooling-preview:1.10.3")

}
