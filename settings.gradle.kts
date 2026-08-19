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
        // The plugin API exposes ScopedD2, so a plugin now compiles against org.hisp.dhis:android-core,
        // which pulls com.github.dhis2:sms-compression from JitPack. Without this the build fails at
        // dependency resolution, not at compile time, so the error does not point at the cause.
        maven("https://jitpack.io")
        // The SDK is a -SNAPSHOT until the scoped-access work is released.
        maven("https://central.sonatype.com/repository/maven-snapshots")
    }
}

rootProject.name = "Plugin implementation test"
include(":app")
include(":plugin")
 