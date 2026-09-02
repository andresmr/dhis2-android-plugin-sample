pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Vendored DHIS2 plugin artefacts, committed under vendor/maven so this project builds on a
        // machine that has never published them — a cloud session, a fresh worktree, a new laptop.
        // Group-scoped so it can never shadow anything else. Remove once these are published to a
        // real repository; see vendor/maven/README.md.
        maven {
            url = uri("$settingsDir/vendor/maven")
            content { includeGroupByRegex("org\\.dhis2\\.mobile.*") }
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
        // See the note in pluginManagement above.
        maven {
            url = uri("$settingsDir/vendor/maven")
            content { includeGroupByRegex("org\\.dhis2\\.mobile.*") }
        }
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

rootProject.name = "dhis2-android-plugin-sample"
include(":app")
include(":plugin")
 