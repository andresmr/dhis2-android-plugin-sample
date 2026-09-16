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
// The one place the build learns who this plugin is.
//
// Everything downstream derives from plugin.json: the Android namespace, the bundle's id and entry
// point, the Compose Resources package, the harness's applicationId. Five hand-kept copies is how
// an `entryPoint` came to name a class that no longer existed — which builds green and then fails
// on the device with ClassNotFoundException, the one failure nothing else here can see.
//
// JsonSlurper is on Gradle's own classpath, so this needs no buildSrc and no included build.
// fileContents (rather than File.readText) so the configuration cache treats plugin.json as an
// input and re-configures when it changes.
// ─────────────────────────────────────────────────────────────────────────────
@Suppress("UNCHECKED_CAST")
val pluginIdentity = groovy.json.JsonSlurper()
    .parseText(
        providers.fileContents(layout.rootDirectory.file("plugin.json")).asText.get(),
    ) as Map<String, Any>

fun identity(key: String): String = (pluginIdentity[key] as String?)
    ?.takeIf { it.isNotBlank() }
    ?: error("plugin.json is missing \"$key\". Run ./init.sh, or see tools/plugin.schema.json.")

val pluginPackage = identity("package")

rootProject.name = identity("slug")

// Plain Strings into every project's extras: serializable, so the configuration cache is happy, and
// typed at the point of use via `by extra`. The four derived values are computed here and never
// stored in plugin.json — a value written down beside the one it comes from is a second copy.
gradle.beforeProject {
    extra["dhis2PluginName"] = identity("name")
    extra["dhis2PluginId"] = identity("pluginId")
    extra["dhis2PluginPackage"] = pluginPackage
    extra["dhis2PluginEntryPoint"] = identity("entryPoint")
    extra["dhis2PluginEntryPointFqcn"] = "$pluginPackage.${identity("entryPoint")}"
    extra["dhis2PluginVersion"] = identity("version")
    extra["dhis2ResourcePackage"] = "$pluginPackage.generated.resources"
    extra["dhis2HarnessApplicationId"] = "$pluginPackage.harness"
}

include(":app")
include(":plugin")
 