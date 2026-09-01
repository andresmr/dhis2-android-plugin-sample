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

rootProject.name = "Plugin implementation test"
include(":app")
include(":plugin")
 