# CLAUDE.md — DHIS2 Android plugin sample

Produces a **signed zip bundle** plugin for the DHIS2 Android Capture App.
Host repo: `~/StudioProjects/ai-dhis2-android-capture-app` on branch
`feature/plugin-system`. Full docs: `docs/plugin-system.md` there.

## Layout

```
Pluginimplementationtest/
├── app/      # Android application — dev-only preview harness.
│             # Uses CMP 1.10.3 (same Compose version as :plugin + Capture App).
│             # A stagePluginAssets task copies :plugin's composeResources into
│             # :app's assets at build time.
└── plugin/   # Kotlin Multiplatform + android.kotlin.multiplatform.library + CMP.
              # Contains MyPlugin + resources. Produces the shippable signed zip.
```

Only `:plugin`'s output is shipped. `:app` is not.

## Commands

```bash
./gradlew :plugin:buildPluginBundle    # signed zip → plugin/build/outputs/plugin-bundle/
./gradlew :app:installDebug            # preview harness on emulator
```

## Rules (read before editing `plugin/build.gradle.kts`)

1. **`compileOnly` everything host-provided, except `compose.components.resources`.**
   The Capture App provides Compose/Material3/plugin-sdk at runtime via
   `InMemoryDexClassLoader`'s parent delegation — bundling them causes DEX bloat
   and `ClassCastException`. **But** `compose.components.resources` must be
   `implementation` — it's the CMP plugin's opt-in signal to generate the `Res`
   accessor class. Swap it to `compileOnly` and `Res.*` imports stop resolving.
2. **Use `kotlin.multiplatform` + `com.android.kotlin.multiplatform.library`,
   not `com.android.library`.** AGP 9 disallows mixing plain Android library
   with KMP.
3. **Set `compose.resources { packageOfResClass = "…" }` explicitly.** Without
   it CMP derives the package from the root project name (which has spaces →
   backtick-escaped imports).
4. **Filename convention** (the host expects): `{pluginId}-{pluginVersion}.zip`.
   The Gradle `val`s at the top of `plugin/build.gradle.kts` drive the filename
   and must match the `PluginMetadata` in `MyPlugin.kt`.
5. **Bump `pluginVersion` to invalidate the device cache.** The Capture App
   caches by `{id}-{version}.zip`; rebuilding at the same version reuses the
   old cache. Symptom: "my code changes aren't showing."

## Resources

```
plugin/src/commonMain/composeResources/
├── values/strings.xml           # default (English)
├── values-es/strings.xml        # Spanish (add more as values-{locale}/)
└── drawable/plugin_icon.xml
```

Access from code:

```kotlin
import org.dhis2.pluginimplementationtest.plugin.generated.resources.Res
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource

Text(stringResource(Res.string.plugin_title))
Image(painter = painterResource(Res.drawable.plugin_icon), contentDescription = null)
```

Runtime resolution differs by host:

- **Capture App.** `PluginLoader` extracts the zip; `PluginSlot` provides a
  per-plugin `FileSystemResourceReader` via
  `CompositionLocalProvider(LocalResourceReader …)`. AssetManager is not
  involved.
- **Harness.** The `stagePluginAssets` task copies resources into
  `:app/build/generated/plugin-assets/composeResources/{package}/…` and
  registers the directory via AGP 9's Variant Sources API. CMP's default
  Android reader then finds them via `context.assets.open(…)`.

## Local testing flow

Full instructions: `docs/plugin-system.md` §8 in the host repo. Summary:

1. `./gradlew :plugin-sdk:publishToMavenLocal` (host repo).
2. `./gradlew :plugin:buildPluginBundle` here. Record printed SHA-256.
3. `cd plugin/build/outputs/plugin-bundle && python3 -m http.server 8080`.
4. Point the Capture App at `http://10.0.2.2:8080/{id}-{version}.zip`:
   - Real path — DHIS2 server dataStore (`dhis2AndroidPlugins/config`).
   - Fast path — edit `FALLBACK_CONFIG_JSON` in the host repo's
     `AppHubPluginRepository.kt`.
5. Rebuild + install `dhis2Debug` variant of the Capture App; log in.

For UI-only previews without the Capture App: `./gradlew :app:installDebug`.

## Entry-point contract

`MyPlugin` must:

- Implement `org.dhis2.mobile.plugin.sdk.Dhis2Plugin`.
- Live at the Kotlin FQCN declared in its own `PluginMetadata.entryPoint`.
- Have a public no-arg constructor — the host instantiates via reflection.

## Backlog

- Extract `buildPluginBundle` into a published Gradle plugin.
- Publish a `plugin-sdk-test` artefact so plugin authors don't copy-paste
  `StubDhis2PluginContext`.
- Unify the `pluginId`/`pluginVersion` Gradle vals with `MyPlugin.metadata`
  via generated constants — they're duplicated today.
- Add a `jvm("desktop")` target and a `desktop/plugin.jar` bundle subdir once
  a Desktop host exists.
- Per-publisher cert allow-list in the Capture App's `PluginVerifier`.
