import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    // Packaging: registers `buildPluginBundle`, adds plugin-sdk as compileOnly at the version the
    // host publishes, and checks this module's toolchain against that host. Resolved from Maven
    // Local while the plugin system is in preview — publish it from the Capture App repo with
    // `./gradlew :plugin-sdk:publishToMavenLocal :plugin-sdk-gradle:publishToMavenLocal`.
    alias(libs.plugins.dhis2.pluginBundle)
}

// The only plugin-specific knob. Everything else about the plugin — its id, entry-point class,
// injection points and data scope — lives in the DHIS2 server dataStore config, which is the
// single source of truth. The plugin's Kotlin declares none of it.
version = "1.5.0"

kotlin {
    android {
        namespace = "org.dhis2.mobile.plugin.sample"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        // Opt in to a JVM test target for commonTest. Without this the AGP KMP library plugin
        // registers no test task at all and `commonTest` is silently never compiled or run.
        withHostTestBuilder {}.configure {}
    }
    // Future Desktop support — add `jvm("desktop")` and a desktop/ subtree to the bundle.

    sourceSets {
        val commonMain by getting {
            dependencies {
                // plugin-sdk is added as compileOnly by the plugin-bundle plugin, at the version of
                // the host that will load this DEX — it is never declared here.
                // All of these are provided by the Capture App at runtime via
                // InMemoryDexClassLoader's parent class loader chain — NOT bundled
                // into the plugin DEX.
                compileOnly(compose.runtime)
                compileOnly(compose.ui)
                compileOnly(compose.material3)
                // Must be `implementation`: the Compose Resources plugin uses this
                // declaration as an opt-in signal to generate the `Res` accessor
                // class. With `compileOnly` the generator is skipped and `Res.string.*`
                // imports fail to resolve. The actual runtime classes are still
                // resolved from the host's class loader.
                implementation(compose.components.resources)

                // Host-provided, so compileOnly — same rule as compose.*. A ViewModel needs the
                // lifecycle artifact; the runtime classes come from the Capture App.
                compileOnly(libs.androidx.lifecycle.viewmodel)
                compileOnly(libs.koin.core)
                compileOnly(libs.koin.compose)
                compileOnly(libs.koin.compose.viewmodel)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.test.coroutines)
                implementation(libs.test.turbine)
                // Real dependencies here, not compileOnly: a unit test has no host to borrow from.
                implementation(libs.androidx.lifecycle.viewmodel)
                implementation(libs.koin.core)
            }
        }
    }
}

// The bundle plugin takes d8 and apksigner from the *newest installed* build-tools, which differs
// between a CI runner and a laptop and changes the DEX bytes. Pinned here rather than in CI because
// reproducibility is this project's claim, not the workflow's. Raising it moves the checksum.
val pluginBuildToolsVersion = "36.1.0"

/** AGP's own resolution order. */
val androidSdkDirectory: File = run {
    val local = Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }.getProperty("sdk.dir")

    val path = local
        ?: System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: error("No Android SDK found: set sdk.dir in local.properties, or ANDROID_HOME.")

    File(path)
}

val pinnedBuildTools: File = File(androidSdkDirectory, "build-tools/$pluginBuildToolsVersion").also {
    // Named at configuration time, rather than a bare missing-file error inside the bundle task.
    require(it.isDirectory) {
        "build-tools $pluginBuildToolsVersion is not installed at $it — " +
            "install it with: sdkmanager --install \"build-tools;$pluginBuildToolsVersion\""
    }
}

// Fills in the two fields of the generated `plugin-config.json` that a build cannot work out for
// itself, so the file is postable to the dataStore as it is instead of needing the same two edits
// after every build. The bundle carries neither value — the server dataStore stays the single
// source of truth for this plugin's identity, and the host reads both from there.
pluginBundle {
    pluginId = "org.dhis2.mobile.plugin.sample"
    entryPoint = "org.dhis2.mobile.plugin.sample.ProgramOverviewPlugin"

    d8Executable = File(pinnedBuildTools, "d8")
    apksignerExecutable = File(pinnedBuildTools, "apksigner")
}

// Tests of androidMain code need the SDK *classes* at runtime. It is compileOnly for the plugin —
// the Capture App supplies it through its class loader — so it is on the compile classpath but not
// the runtime one, and a JVM test that builds a real D2Error dies with NoClassDefFoundError.
//
// Extending rather than declaring it: the version stays wherever the bundle plugin injected it, so
// there is nothing here to keep equal to the host by hand.
configurations.named("androidHostTestRuntimeOnly") {
    extendsFrom(configurations.getByName("androidMainCompileOnly"))
}

compose.resources {
    // Override default (which derives from the root project name — gives an ugly
    // backtick-escaped package when the project name contains spaces).
    packageOfResClass = "org.dhis2.mobile.plugin.sample.generated.resources"
    publicResClass = true
}
