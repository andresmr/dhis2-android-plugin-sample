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
// Everything downstream derives from a plugin.json: the Android namespace, the bundle's id and
// entry point, the Compose Resources package, the harness's applicationId. Five hand-kept copies is
// how an `entryPoint` came to name a class that no longer existed — which builds green and then
// fails on the device with ClassNotFoundException, the one failure nothing else here can see.
//
// JsonSlurper is on Gradle's own classpath, so this needs no buildSrc and no included build.
// fileContents (rather than File.readText) so the configuration cache treats each plugin.json as an
// input and re-configures when it changes.
// ─────────────────────────────────────────────────────────────────────────────

/** A plugin.json read into the flat map of Strings that every build script consumes. */
fun identityAt(relativePath: String): Map<String, String> {
    @Suppress("UNCHECKED_CAST")
    val json = groovy.json.JsonSlurper()
        .parseText(providers.fileContents(layout.rootDirectory.file(relativePath)).asText.get())
            as Map<String, Any>

    fun required(key: String): String = (json[key] as String?)
        ?.takeIf { it.isNotBlank() }
        ?: error("$relativePath is missing \"$key\". See tools/plugin.schema.json.")

    val pkg = required("package")
    val entryPoint = required("entryPoint")

    // Optional, harness-only, and false unless a module says otherwise: downloading tracker data
    // takes minutes and is wasted on a plugin that never reads a row.
    @Suppress("UNCHECKED_CAST")
    val harness = (json["harness"] as? Map<String, Any>).orEmpty()
    val trackerData = (harness["trackerData"] as? Boolean) ?: false
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
        "dhis2HarnessTrackerData" to trackerData.toString(),
    )
}

/** A directory's plugin.json, as a path relative to the root — "" for the root project itself. */
fun relativeJson(directory: File): String =
    directory.relativeTo(rootDir).invariantSeparatorsPath
        .let { if (it.isEmpty()) "plugin.json" else "$it/plugin.json" }

val rootIdentity = identityAt("plugin.json")

rootProject.name = rootIdentity.getValue("dhis2PluginSlug")

include(":app")
include(":plugin")

// ─────────────────────────────────────────────────────────────────────────────
// Examples: extra plugin modules, present in the template and deleted by ./init.sh.
//
// They live here rather than on a branch so that a change to the harness and the example that
// exercises it land in one commit. Included only if the directory survives, so deleting it needs
// no edit to this file.
// ─────────────────────────────────────────────────────────────────────────────
val exampleDirs = rootDir.resolve("examples")
    .listFiles { file: File -> file.isDirectory && file.resolve("build.gradle.kts").exists() }
    ?.sortedBy { it.name }
    .orEmpty()

exampleDirs.forEach { include(":examples:${it.name}") }

// ─────────────────────────────────────────────────────────────────────────────
// Which plugin the harness builds and renders.
//
// `harness.module` in local.properties, blank or absent meaning your own :plugin. The harness loads
// the entry point by name (see MainActivity), so this switch changes what :app depends on and what
// identity it is handed — no Kotlin in :app names a plugin type except Previews.kt.
// ─────────────────────────────────────────────────────────────────────────────
val harnessModule: String = java.util.Properties().apply {
    val file = rootDir.resolve("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}.getProperty("harness.module").orEmpty().trim().ifEmpty { ":plugin" }

val harnessProjectDir: File = when {
    harnessModule == ":plugin" -> rootDir.resolve("plugin")
    else -> rootDir.resolve(harnessModule.removePrefix(":").replace(':', '/'))
}

require(harnessProjectDir.resolve("plugin.json").exists() || harnessModule == ":plugin") {
    "local.properties names harness.module=$harnessModule, but $harnessProjectDir has no " +
        "plugin.json. Available: :plugin${exampleDirs.joinToString("") { ", :examples:${it.name}" }}"
}

val harnessIdentity = if (harnessModule == ":plugin") {
    rootIdentity
} else {
    identityAt(relativeJson(harnessProjectDir))
}

gradle.beforeProject {
    val identity = when {
        // :app is handed whichever plugin it is hosting, not its own.
        path == ":app" -> harnessIdentity + mapOf("dhis2HarnessModule" to harnessModule)
        projectDir.resolve("plugin.json").exists() -> identityAt(relativeJson(projectDir))
        else -> rootIdentity
    }
    identity.forEach { (key, value) -> extra[key] = value }
}
