# Vendored DHIS2 plugin artefacts

These are **committed binaries**, and they exist for one reason: without them this project does not
configure. `settings.gradle.kts` needs the `org.dhis2.mobile.plugin-bundle` Gradle plugin before it
can evaluate `plugin/build.gradle.kts`, and `:plugin` compiles against `plugin-sdk`. Resolving those
from `mavenLocal()` alone means the project builds only on a machine where someone has published
them by hand — not in a cloud session, not in a fresh worktree, not on a new laptop.

**This is temporary. Delete it as soon as the artefacts are published.**

## How to remove this

Publish `org.dhis2.mobile:plugin-sdk` and `org.dhis2.mobile:plugin-sdk-gradle` to a real repository
(Maven Central snapshots or GitHub Packages) under a **new version** — not by overwriting
`0.1.2-SNAPSHOT`, or a consumer still holding this copy resolves a different artefact at the same
coordinate. The `org.dhis2.mobile.plugin-bundle` marker POM is published automatically alongside
`plugin-sdk-gradle` by `java-gradle-plugin`; that marker is what `plugins { id(...) }` resolves.

Then, three edits:

1. Delete the `vendor/` directory.
2. In `settings.gradle.kts`, delete **both** `maven { url = uri("$settingsDir/vendor/maven") ... }`
   blocks — one in `pluginManagement`, one in `dependencyResolutionManagement` — and their comments.
3. In their place declare the repository you published to, in **both** blocks, and set the new
   version in `gradle/libs.versions.toml`.

Step 3 is a substitution, not just a deletion. `plugins { }` resolves from `pluginManagement` and the
injected `plugin-sdk` dependency from `dependencyResolutionManagement`, so leaving either one out
brings back the original failure — the project not configuring at all. `mavenLocal()` may stay or go;
it is no longer load-bearing.

`grep -rn vendor` outside this directory returns nothing else, so there is no third place to check.
Confirm with the cold-build command at the bottom of this file: it is the only check that tells a
working setup apart from a machine that happens to have the artefacts already.

## What is here

Version **0.1.2-SNAPSHOT** of:

| Coordinate | Why a consumer needs it |
|---|---|
| `org.dhis2.mobile:plugin-sdk` | the plugin API — `Dhis2Plugin`, `Dhis2PluginContext`, `PluginMetadata`, `InjectionPoint` |
| `org.dhis2.mobile:plugin-sdk-android` | the Android variant (`.aar`); `Dhis2PluginContext.sdk` is `D2`, so the real interfaces live here |
| `org.dhis2.mobile:plugin-sdk-gradle` | implementation of the `buildPluginBundle` task and the toolchain preflight |
| `org.dhis2.mobile.plugin-bundle:...gradle.plugin` | the marker POM the `plugins { id(...) }` line resolves |

Sources jars and Kotlin tooling metadata were stripped, and so was the `plugin-sdk-desktop`
variant — a cold build confirmed nothing resolves it while no target is a JVM one. Four modules,
18 files, 160 KB. Both the root `plugin-sdk` and `plugin-sdk-android` are required: the injected
coordinate is the root, and Gradle Module Metadata redirects it to the Android variant, so removing
either breaks resolution.
`maven-metadata.xml` is a copy of the `-local` variant that `publishToMavenLocal` writes, because a
file-backed `maven { }` repository looks for the non-local name.

## Provenance

Vendored on 2026-09-01 from a local build of the plugin system at version 0.1.2-SNAPSHOT.

SHA-256 of each resolvable artefact, so drift against a future published copy is detectable:

```
03b2cc8dd436ea52a5205abddb1433d6d37ebd81c593a9fbca12599c9970fd90  org/dhis2/mobile/plugin-bundle/org.dhis2.mobile.plugin-bundle.gradle.plugin/0.1.2-SNAPSHOT/org.dhis2.mobile.plugin-bundle.gradle.plugin-0.1.2-SNAPSHOT.pom
e3a3efb908f980b8ceb7383b9b7c922650065ecadae9e1a54e45fd5831d43fc6  org/dhis2/mobile/plugin-sdk-android/0.1.2-SNAPSHOT/plugin-sdk-android-0.1.2-SNAPSHOT.aar
06ce566e071bf3cc848aaec8431615d80d1095a6153189b991bfc1f25dc0cecb  org/dhis2/mobile/plugin-sdk-android/0.1.2-SNAPSHOT/plugin-sdk-android-0.1.2-SNAPSHOT.pom
e519139f55e8d03777a0c076d3f3b98e0d4d221b2510a165b5359a651af476dc  org/dhis2/mobile/plugin-sdk-gradle/0.1.2-SNAPSHOT/plugin-sdk-gradle-0.1.2-SNAPSHOT.jar
2200cedffeaf8e04ba18c69ef1337f2a2558b24181d71746d1ae67ebbc40971c  org/dhis2/mobile/plugin-sdk-gradle/0.1.2-SNAPSHOT/plugin-sdk-gradle-0.1.2-SNAPSHOT.pom
3725b1c3a3851bf7cdc1426964dfa7e58714958e0c56b94a73aeb5e7952f7552  org/dhis2/mobile/plugin-sdk/0.1.2-SNAPSHOT/plugin-sdk-0.1.2-SNAPSHOT.jar
409e80d21567f2f8181e690bb8770bfdb9eb8fb11d4b3728dde6dcc8e968269c  org/dhis2/mobile/plugin-sdk/0.1.2-SNAPSHOT/plugin-sdk-0.1.2-SNAPSHOT.pom
```

## Refreshing them

Publish `plugin-sdk` and `plugin-sdk-gradle` to your local Maven repository, then re-copy the
`0.1.2-SNAPSHOT` directories here, strip sources and tooling metadata, and duplicate each
`maven-metadata-local.xml` to `maven-metadata.xml`. **Bump the version** when the API changes rather
than overwriting in place — a plugin compiled against a stale copy of this API fails at runtime with
`NoSuchMethodError` or `ClassCastException`, which is the hardest class of bug this project has.

## Verifying it actually stands alone

```bash
./gradlew -g /tmp/cold-gradle-home -Dmaven.repo.local=/tmp/empty-m2 \
  :plugin:testAndroidHostTest :plugin:buildPluginBundle
```

A fresh Gradle home plus an empty local Maven repository is the only honest test — an ordinary run
passes on a primed machine whether or not this directory works.
