import java.io.File
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.dhis2.pluginimplementationtest.plugin"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // compileOnly is LOAD-BEARING: these types are provided by the host Capture App
    // at runtime via InMemoryDexClassLoader's parent class loader. Bundling them into
    // the plugin DEX duplicates classes and risks ClassCastException.
    compileOnly(libs.plugin.sdk)
    compileOnly(platform(libs.androidx.compose.bom))
    compileOnly(libs.androidx.compose.ui)
    compileOnly(libs.androidx.compose.ui.graphics)
    compileOnly(libs.androidx.compose.material3)
}

// ──────────────────────────────────────────────────────────────────────────────
// Plugin packaging
//
// `./gradlew :plugin:buildPluginDex` produces a single standalone DEX containing
// ONLY this module's own compiled classes. Compose, Material3, AndroidX and the
// plugin-sdk are resolved from the host app's class loader at runtime (see
// InMemoryDexClassLoader parent delegation in the host's PluginLoader).
//
// Output: plugin/build/outputs/plugin-dex/{pluginId}-{pluginVersion}.dex
// ──────────────────────────────────────────────────────────────────────────────

val pluginId = "org.dhis2.myplugin"
val pluginVersion = "1.0.0"

fun resolveAndroidSdkDir(): String {
    System.getenv("ANDROID_HOME")?.let { return it }
    System.getenv("ANDROID_SDK_ROOT")?.let { return it }
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) {
        val props = Properties()
        propsFile.inputStream().use { props.load(it) }
        props.getProperty("sdk.dir")?.let { return it }
    }
    error("Cannot locate the Android SDK. Set ANDROID_HOME or sdk.dir in local.properties.")
}

tasks.register("buildPluginDex") {
    group = "plugin"
    description = "Produces a standalone DEX containing only this plugin's classes."

    dependsOn("assembleRelease")

    val stagingProvider = layout.buildDirectory.dir("tmp/plugin-dex")
    val outDirProvider = layout.buildDirectory.dir("outputs/plugin-dex")
    val aarDirProvider = layout.buildDirectory.dir("outputs/aar")
    val buildToolsVersion: String = android.buildToolsVersion
    val sdkDir: String = resolveAndroidSdkDir()
    val pluginIdCaptured = pluginId
    val pluginVersionCaptured = pluginVersion

    outputs.dir(outDirProvider)

    doLast {
        val staging = stagingProvider.get().asFile.apply { deleteRecursively(); mkdirs() }
        val target = outDirProvider.get().asFile.apply { mkdirs() }

        val aarDir = aarDirProvider.get().asFile
        val aar = aarDir.listFiles { f -> f.name.endsWith("-release.aar") }?.singleOrNull()
            ?: error("No release AAR found in $aarDir. Run :plugin:assembleRelease first.")

        val classesJar = File(staging, "classes.jar")
        ZipFile(aar).use { zip ->
            val entry = zip.getEntry("classes.jar")
                ?: error("classes.jar not found inside $aar")
            zip.getInputStream(entry).use { input ->
                classesJar.outputStream().use { output -> input.copyTo(output) }
            }
        }
        check(classesJar.exists()) { "classes.jar not extracted to $classesJar" }

        val d8 = File(sdkDir, "build-tools/$buildToolsVersion/d8")
        check(d8.exists()) {
            "d8 not found at $d8 — install build-tools $buildToolsVersion via the SDK Manager."
        }

        val process = ProcessBuilder(
            d8.absolutePath,
            "--min-api", "26",
            "--output", staging.absolutePath,
            classesJar.absolutePath,
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        check(exit == 0) { "d8 failed with exit $exit:\n$output" }

        val produced = File(staging, "classes.dex")
        check(produced.exists()) { "d8 did not produce classes.dex in $staging" }

        val renamed = File(target, "$pluginIdCaptured-$pluginVersionCaptured.dex")
        if (renamed.exists()) renamed.delete()
        check(produced.renameTo(renamed)) { "Could not move $produced -> $renamed" }

        val hash = MessageDigest.getInstance("SHA-256")
            .digest(renamed.readBytes())
            .joinToString("") { "%02x".format(it) }

        logger.lifecycle("")
        logger.lifecycle("Built plugin DEX")
        logger.lifecycle("  path:     ${renamed.absolutePath}")
        logger.lifecycle("  size:     ${renamed.length()} bytes")
        logger.lifecycle("  checksum: sha256:$hash")
        logger.lifecycle("")
    }
}
