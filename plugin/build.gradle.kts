@file:Suppress("DEPRECATION")

import java.io.File
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    androidLibrary {
        namespace = "org.dhis2.pluginimplementationtest.plugin"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    // Future Desktop support — add `jvm("desktop")` and a desktop/ subtree in buildPluginBundle.

    sourceSets {
        val commonMain by getting {
            dependencies {
                // All of these are provided by the Capture App at runtime via
                // InMemoryDexClassLoader's parent class loader chain — NOT bundled
                // into the plugin DEX.
                compileOnly(libs.plugin.sdk)
                compileOnly(compose.runtime)
                compileOnly(compose.ui)
                compileOnly(compose.material3)
                // Must be `implementation`: the Compose Resources plugin uses this
                // declaration as an opt-in signal to generate the `Res` accessor
                // class. With `compileOnly` the generator is skipped and `Res.string.*`
                // imports fail to resolve. The actual runtime classes are still
                // resolved from the host's class loader.
                implementation(compose.components.resources)
            }
        }
    }
}

compose.resources {
    // Override default (which derives from the root project name — gives an ugly
    // backtick-escaped package when the project name contains spaces).
    packageOfResClass = "org.dhis2.pluginimplementationtest.plugin.generated.resources"
    publicResClass = true
}

// ──────────────────────────────────────────────────────────────────────────────
// Plugin packaging — signed zip bundle
//
// `./gradlew :plugin:buildPluginBundle` produces a signed zip bundle containing
// the plugin's DEX + compose resources (strings, drawables). The host:
//
//   1. Downloads the zip.
//   2. Verifies SHA-256 (integrity).
//   3. Verifies the JAR signature (publisher identity).
//   4. Unzips into a cache dir.
//   5. Loads android/classes.dex via InMemoryDexClassLoader.
//   6. Installs a per-plugin ResourceReader pointing at android/composeResources/
//      so the plugin's CMP Resources (Res.string.foo, painterResource(Res.drawable.foo))
//      resolve from the plugin's own files.
//
// Bundle layout:
//
//   {module}-{version}.zip
//   ├── META-INF/…               (jarsigner)
//   └── android/
//       ├── classes.dex
//       └── composeResources/    (extracted from the release AAR)
//
// The bundle carries no manifest — the host reads id/version/entryPoint/scope from the DHIS2
// server dataStore config, so the filename is only a convenience for whoever hosts the file.
//
// Future: add desktop/plugin.jar alongside android/; host picks by target.
//
// Signing: uses ~/.android/debug.keystore (standard Android debug key).
// ──────────────────────────────────────────────────────────────────────────────

// The only plugin-specific knob. Everything else about the plugin — its id, entry-point class,
// injection points and data scope — lives in the DHIS2 server dataStore config, which is the
// single source of truth. The plugin's Kotlin declares none of it.
version = "1.5.0"

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

fun resolveJdkTool(name: String): File {
    val javaHome = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")
        ?: error("Cannot locate a JDK. Set JAVA_HOME.")
    val direct = File(javaHome, "bin/$name")
    if (direct.exists()) return direct
    val parent = File(javaHome).parentFile?.let { File(it, "bin/$name") }
    if (parent != null && parent.exists()) return parent
    error("$name not found near $javaHome — install a JDK (not just a JRE).")
}

val resourcePackage = "org.dhis2.pluginimplementationtest.plugin.generated.resources"

tasks.register("buildPluginBundle") {
    group = "plugin"
    description = "Produces a signed zip bundle containing classes.dex + composeResources/."

    // bundleAndroidMainAar is the AGP 9 KMP-library equivalent of assembleRelease.
    // Depending on it transitively pulls in kotlin compilation + compose resource generation.
    dependsOn("bundleAndroidMainAar")

    val stagingProvider = layout.buildDirectory.dir("tmp/plugin-bundle")
    val outDirProvider = layout.buildDirectory.dir("outputs/plugin-bundle")
    val aarFileProvider = layout.buildDirectory.file("outputs/aar/plugin.aar")
    val preparedResourcesProvider = layout.buildDirectory
        .dir("generated/compose/resourceGenerator/preparedResources/commonMain/composeResources")
    val sdkDir: String = resolveAndroidSdkDir()
    val buildToolsVersion: String = File(sdkDir, "build-tools").listFiles { f -> f.isDirectory }
        ?.maxByOrNull { it.name }
        ?.name
        ?: error("No build-tools installed under $sdkDir/build-tools. Install via SDK Manager.")
    val resourcePackageCaptured = resourcePackage
    val bundleName = "${project.name}-$version.zip"

    inputs.file(aarFileProvider)
        .withPropertyName("pluginAar")
        .withPathSensitivity(PathSensitivity.NAME_ONLY)
    inputs.files(preparedResourcesProvider)
        .withPropertyName("preparedComposeResources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .optional()
    inputs.property("bundleName", bundleName)
    inputs.property("resourcePackage", resourcePackageCaptured)
    inputs.property("buildToolsVersion", buildToolsVersion)

    outputs.dir(outDirProvider)

    doLast {
        val staging = stagingProvider.get().asFile.apply { deleteRecursively(); mkdirs() }
        val target = outDirProvider.get().asFile.apply { mkdirs() }

        val aar = aarFileProvider.get().asFile
        check(aar.exists()) { "AAR not found at $aar. Run :plugin:bundleAndroidMainAar first." }

        // 1. Extract classes.jar from the AAR.
        val androidDir = File(staging, "android").apply { mkdirs() }
        val classesJar = File(staging, "classes.jar")
        ZipFile(aar).use { zip ->
            val classesEntry = zip.getEntry("classes.jar")
                ?: error("classes.jar not found inside $aar")
            zip.getInputStream(classesEntry).use { input ->
                classesJar.outputStream().use { output -> input.copyTo(output) }
            }
        }

        // 1b. Copy compose resources from the generator's prepared-resources directory
        // into android/composeResources/{package}/... which is the path layout the
        // Compose runtime expects when resolving Res.string.foo / Res.drawable.foo.
        val preparedResources = preparedResourcesProvider.get().asFile
        val resourcesRoot = File(androidDir, "composeResources/$resourcePackageCaptured")
        if (preparedResources.exists()) {
            preparedResources.walkTopDown().filter { it.isFile }.forEach { src ->
                val rel = src.relativeTo(preparedResources).path
                val dst = File(resourcesRoot, rel).apply { parentFile?.mkdirs() }
                src.copyTo(dst, overwrite = true)
            }
        } else {
            logger.warn("No prepared compose resources at $preparedResources. Bundle will have no resources.")
        }

        // 2. Run d8 on classes.jar → android/classes.dex.
        val d8 = File(sdkDir, "build-tools/$buildToolsVersion/d8").also {
            check(it.exists()) {
                "d8 not found at $it — install build-tools $buildToolsVersion via the SDK Manager."
            }
        }
        val dexStaging = File(staging, "dex-out").apply { mkdirs() }
        val dexProc = ProcessBuilder(
            d8.absolutePath,
            "--min-api", "26",
            "--output", dexStaging.absolutePath,
            classesJar.absolutePath,
        ).redirectErrorStream(true).start()
        val dexLog = dexProc.inputStream.bufferedReader().readText()
        val dexExit = dexProc.waitFor()
        check(dexExit == 0) { "d8 failed with exit $dexExit:\n$dexLog" }
        val dexProduced = File(dexStaging, "classes.dex")
        check(dexProduced.exists()) { "d8 did not produce classes.dex in $dexStaging" }
        dexProduced.copyTo(File(androidDir, "classes.dex"), overwrite = true)

        // 3. Zip staging → unsigned bundle.
        //
        // No manifest is written: the host learns the plugin's id, version, entry point and data
        // scope from the server dataStore config, never from the bundle. Adding a plugin.json here
        // would just be a second copy of those facts, free to drift from the config that matters.
        val unsignedZip = File(staging, "unsigned.zip")
        ZipOutputStream(unsignedZip.outputStream()).use { zipOut ->
            fun addFile(file: File, entryName: String) {
                zipOut.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zipOut) }
                zipOut.closeEntry()
            }
            androidDir.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = "android/" + f.relativeTo(androidDir).invariantSeparatorsPath
                addFile(f, rel)
            }
        }

        // 4. Sign the zip with jarsigner (Android debug key).
        val keystore = File(System.getProperty("user.home"), ".android/debug.keystore")
        check(keystore.exists()) {
            "Debug keystore not found at $keystore. " +
                "Install Android Studio, or create one via: " +
                "keytool -genkey -v -keystore ~/.android/debug.keystore " +
                "-storepass android -alias androiddebugkey -keypass android " +
                "-dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000"
        }
        val jarsigner = resolveJdkTool("jarsigner")
        val signedZip = File(target, bundleName)
        if (signedZip.exists()) signedZip.delete()
        unsignedZip.copyTo(signedZip, overwrite = true)
        val signProc = ProcessBuilder(
            jarsigner.absolutePath,
            "-keystore", keystore.absolutePath,
            "-storepass", "android",
            "-keypass", "android",
            signedZip.absolutePath,
            "androiddebugkey",
        ).redirectErrorStream(true).start()
        val signLog = signProc.inputStream.bufferedReader().readText()
        val signExit = signProc.waitFor()
        check(signExit == 0) { "jarsigner failed with exit $signExit:\n$signLog" }

        // 5. Report.
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(signedZip.readBytes())
            .joinToString("") { "%02x".format(it) }

        logger.lifecycle("")
        logger.lifecycle("Built plugin bundle")
        logger.lifecycle("  path:     ${signedZip.absolutePath}")
        logger.lifecycle("  size:     ${signedZip.length()} bytes")
        logger.lifecycle("  checksum: sha256:$hash")
        logger.lifecycle("")
    }
}
