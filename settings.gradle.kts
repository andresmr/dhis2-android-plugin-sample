pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenLocal()
        mavenCentral()
        // The plugin API exposes D2, so a plugin compiles against org.hisp.dhis:android-core, which
        // pulls com.github.dhis2:sms-compression from JitPack. Without this the build fails at
        // dependency resolution, with an error that never mentions the DHIS2 SDK.
        maven("https://jitpack.io")
        // The host tracks SDK snapshots, so the injected android-core version is usually one.
        maven("https://central.sonatype.com/repository/maven-snapshots")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The one place the build learns who a plugin is.
//
// Everything downstream derives from plugin.json: the Android namespace, the bundle's id and
// entry point, the Compose Resources package, the harness's applicationId. Five hand-kept copies is
// how an `entryPoint` came to name a class that no longer existed — which builds green and then
// fails on the device with ClassNotFoundException, the one failure nothing else here can see.
//
// JsonSlurper is on Gradle's own classpath, so this needs no buildSrc and no included build.
// fileContents (rather than File.readText) so the configuration cache treats plugin.json as an
// input and re-configures when it changes.
// ─────────────────────────────────────────────────────────────────────────────

/** plugin.json, read into the flat map of Strings that every build script consumes. */
fun identity(): Map<String, String> {
    @Suppress("UNCHECKED_CAST")
    val json = groovy.json.JsonSlurper()
        .parseText(providers.fileContents(layout.rootDirectory.file("plugin.json")).asText.get())
            as Map<String, Any>

    fun required(key: String): String = (json[key] as String?)
        ?.takeIf { it.isNotBlank() }
        ?: error("plugin.json is missing \"$key\". See tools/plugin.schema.json.")

    val pkg = required("package")
    val entryPoint = required("entryPoint")

    // Both reach the generated plugin-config.json and nothing else. Carried as text because this
    // map is flat Strings by design — plugin/build.gradle.kts parses slotConfig back with the same
    // JsonSlurper that read it, rather than either file growing a second shape.
    @Suppress("UNCHECKED_CAST")
    val injectionPoints = (json["injectionPoints"] as? List<String>).orEmpty()
    val slotConfig = (json["slotConfig"] as? Map<String, Any>).orEmpty()
    // Derived here and never stored in the file: a value written down beside the one it comes from
    // is a second copy, and two copies is how they drift apart.
    return mapOf(
        "dhis2PluginSlug" to required("slug"),
        "dhis2PluginName" to required("name"),
        "dhis2PluginId" to required("pluginId"),
        "dhis2PluginPackage" to pkg,
        "dhis2PluginEntryPoint" to entryPoint,
        "dhis2PluginEntryPointFqcn" to "$pkg.$entryPoint",
        "dhis2PluginVersion" to required("version"),
        "dhis2ResourcePackage" to "$pkg.generated.resources",
        "dhis2HarnessApplicationId" to "$pkg.harness",
        "dhis2InjectionPoints" to injectionPoints.joinToString(","),
        "dhis2SlotConfigJson" to groovy.json.JsonOutput.toJson(slotConfig),
    )
}

val identity = identity()

rootProject.name = identity.getValue("dhis2PluginSlug")

include(":app")
include(":plugin")

gradle.beforeProject {
    // :app is the harness and is handed the plugin's identity, not its own — it never spells the
    // package, id or entry point itself.
    identity.forEach { (key, value) -> extra[key] = value }
}
