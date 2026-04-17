# CLAUDE.md — DHIS2 Android plugin sample

A sample Android project that produces a standalone DEX plugin for the DHIS2
Android Capture App's plugin system. The host repo lives at
`~/StudioProjects/ai-dhis2-android-capture-app` on branch `feature/plugin-system`.

## Two-module layout — deliberate

```
Pluginimplementationtest/
├── app/      # com.android.application — dev-only harness. MainActivity +
│             #   StubDhis2PluginContext instantiate MyPlugin and render it
│             #   inside a mocked "program list" scaffold. Install it with
│             #   `:app:installDebug` to preview without the Capture App.
└── plugin/   # com.android.library  — the actual plugin. Produces the
              #   shippable DEX via `:plugin:buildPluginDex`.
```

Only `:plugin` is distributed. `:app` exists so plugin authors can click-and-run.

## Build commands

```bash
# Produce the standalone plugin DEX (run from repo root):
./gradlew :plugin:buildPluginDex
# → plugin/build/outputs/plugin-dex/{pluginId}-{pluginVersion}.dex
#   prints size + sha256 to the console

# Preview the plugin in the harness app:
./gradlew :app:installDebug

# Standard Gradle hygiene:
./gradlew :plugin:assembleRelease   # AAR (intermediate for buildPluginDex)
./gradlew :app:assembleDebug        # harness APK only
```

The `buildPluginDex` task is defined in-line in `plugin/build.gradle.kts` — not
shipped as a Gradle plugin yet. If you fork to a new plugin, copy the task
block.

## Non-obvious rules (read before editing `plugin/build.gradle.kts`)

1. **Every host-provided dep in `:plugin` must be `compileOnly`.** The Capture
   App's `InMemoryDexClassLoader` uses the host's class loader as parent, so
   `plugin-sdk`, Compose, Material3, AndroidX all resolve at runtime from the
   host. Declaring them as `implementation` bloats the DEX and risks
   `ClassCastException: … not assignable to Dhis2Plugin` from duplicated
   class definitions.
2. **`:plugin` is a library, not an application.** An application build
   produces multi-dex APKs where `classes.dex` is Compose/Material bloat and
   the plugin's own class lives in `classes2.dex`/`classes3.dex`. Using a
   library module + `d8` on `classes.jar` sidesteps that entirely.
3. **Filename convention**: the host app expects `{pluginId}-{pluginVersion}.dex`.
   `buildPluginDex` reads `pluginId` and `pluginVersion` from the `val`s at
   the top of `plugin/build.gradle.kts`. Keep them in sync with the
   `PluginMetadata` hard-coded in `MyPlugin.kt`.
4. **Bump `pluginVersion` to invalidate the device cache.** The host caches
   the downloaded DEX as `{id}-{version}.dex` in `filesDir/plugins/`. A rebuilt
   DEX at the same version reuses the old cache — symptom is "my code changes
   aren't visible". Either bump, or
   `adb shell run-as com.dhis2.debug rm -rf files/plugins`.

## Local testing flow (end-to-end)

The host repo has full instructions in `docs/plugin-system.md` (§9). Summary:

1. In the host repo: `./gradlew :plugin-sdk:publishToMavenLocal`.
2. Here: `./gradlew :plugin:buildPluginDex`. Record the printed SHA-256.
3. `cd plugin/build/outputs/plugin-dex && python3 -m http.server 8080`.
4. Point the host at the DEX — either:
   - **Server-side**: write to DHIS2 dataStore namespace `dhis2AndroidPlugins`,
     key `config`, with `{"plugins": [{...}]}`.
   - **Local hack**: edit `FALLBACK_CONFIG_JSON` in
     `plugin/src/main/java/org/dhis2/mobile/plugin/data/AppHubPluginRepository.kt`
     in the host repo. (Marked `TODO: remove` — revert before merging.)
5. Emulator URL: `http://10.0.2.2:8080/{pluginId}-{pluginVersion}.dex`.
6. Rebuild host, log in. Expected log:
   `Loading plugin '…' v… from DEX (16404 bytes)` and
   `Plugin '…' v… loaded successfully`.

## Entry-point contract

`MyPlugin` must:
- Live at the Kotlin FQCN declared in its own `PluginMetadata.entryPoint`.
- Have a **public no-arg constructor** — the host instantiates via reflection.
- Implement `org.dhis2.mobile.plugin.sdk.Dhis2Plugin`.
- Not bundle `Dhis2Plugin` / `Dhis2PluginContext` / `PluginMetadata` /
  `InjectionPoint` (they must be `compileOnly` as above).

`consumer-rules.pro` / `proguard-rules.pro` in `:plugin` keep the entry point
class and its members from being renamed by R8 — edit those if you add
reflectively-loaded classes.

## Versioning conventions

- `org.dhis2.mobile:plugin-sdk:0.1.0-SNAPSHOT` — compile-only dep, served from
  `mavenLocal()` while the SDK is pre-release. `settings.gradle.kts` already
  declares `mavenLocal()` in both `pluginManagement` and
  `dependencyResolutionManagement`.
- `minSdk = 26` is required — `InMemoryDexClassLoader` needs API 26+.

## Things to add next (backlog)

- Extract `buildPluginDex` into a real Gradle plugin (`org.dhis2.mobile.plugin`).
- Publish a `plugin-sdk-test` artefact so plugin authors don't have to
  copy-paste `StubDhis2PluginContext`.
- Wire the `pluginId` / `pluginVersion` Gradle vals to `MyPlugin.metadata`
  via a generated constants class — today they're duplicated.
