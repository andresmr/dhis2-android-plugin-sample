# CLAUDE.md — DHIS2 Android plugin sample

Produces a **signed zip bundle** plugin for the DHIS2 Android Capture App.
Host repo: `~/StudioProjects/ai-dhis2-mobile/ai-dhis2-android-capture-app` on branch
`poc/plugin-system-scopedSDK`; the DHIS2 SDK it needs is `~/StudioProjects/ai-dhis2-mobile/ai-dhis2-android-sdk`
on `poc/scoped-sdk`. Full docs: `docs/plugin-system.md` in the host repo.

## Layout

```
Pluginimplementationtest/
├── app/      # Android application — dev-only preview harness.
│             # Uses CMP 1.10.3 (same Compose version as :plugin + Capture App).
│             # A stagePluginAssets task copies :plugin's composeResources into
│             # :app's assets at build time.
└── plugin/   # Kotlin Multiplatform + android.kotlin.multiplatform.library + CMP.
    ├── src/androidMain/  # MyPlugin — the Dhis2Plugin implementation and all data access.
    └── src/commonMain/   # ProgramSummary(+Card) — pure UI and data, plus composeResources.
```

Only `:plugin`'s output is shipped. `:app` is not.

**Why the split.** `Dhis2PluginContext.sdk` is a `ScopedD2` — the DHIS2 *Android* SDK — so the
`Dhis2Plugin` implementation cannot live in `commonMain`. Everything it renders is kept in
`commonMain` as plain data plus a Composable that takes it, which is what lets the harness and
`@Preview` render the real UI without a context.

## Commands

```bash
./gradlew :plugin:buildPluginBundle    # signed zip → plugin/build/outputs/plugin-bundle/
./gradlew :app:installDebug            # preview harness on emulator
```

## Rules (read before editing `plugin/build.gradle.kts` or `settings.gradle.kts`)

1. **`compileOnly` everything host-provided, except `compose.components.resources`.**
   The Capture App provides Compose/Material3/plugin-sdk/the DHIS2 SDK at runtime via
   `InMemoryDexClassLoader`'s parent delegation — bundling them causes DEX bloat
   and `ClassCastException`. **But** `compose.components.resources` must be
   `implementation` — it's the CMP plugin's opt-in signal to generate the `Res`
   accessor class. Swap it to `compileOnly` and `Res.*` imports stop resolving.
   `plugin-sdk` and `org.hisp.dhis:android-core` are added automatically by the
   `org.dhis2.mobile.plugin-bundle` plugin, at the host's versions — never declare them.
2. **`settings.gradle.kts` needs JitPack and the snapshots repo.** Compiling against the DHIS2 SDK
   pulls `com.github.dhis2:sms-compression` from JitPack. Without it the build fails at dependency
   *resolution*, with an error that does not mention the SDK.
3. **Use `kotlin.multiplatform` + `com.android.kotlin.multiplatform.library`,
   not `com.android.library`.** AGP 9 disallows mixing plain Android library
   with KMP.
4. **Set `compose.resources { packageOfResClass = "…" }` explicitly.** Without
   it CMP derives the package from the root project name (which has spaces →
   backtick-escaped imports).
5. **Bump `version` to invalidate the device cache.** The Capture App
   caches by `{id}-{version}.zip`; rebuilding at the same version reuses the
   old cache. Symptom: "my code changes aren't showing." The bundle is otherwise
   reproducible, so an unchanged plugin keeps its SHA-256 and the dataStore
   config does not need re-editing.

## Data access

`context.sdk` is the DHIS2 SDK restricted to the scope the server granted. Repositories arrive
pre-filtered, and because SDK filters only ever accumulate, anything you add can only narrow
further — an out-of-scope query returns nothing rather than throwing.

```kotlin
val recent = context.sdk.trackedEntityInstances()
    .byProgramUids(listOf(CHILD_PROGRAMME_UID))
    .withTrackedEntityAttributeValues()
    .orderByCreated(RepositoryScope.OrderByDirection.DESC)
    .blockingGet()
```

`blocking*` calls must run off the main thread — `MyPlugin` wraps them in `Dispatchers.IO`.

Accessors throw `D2Error(SCOPE_VIOLATION)` when the granted scope lacks the capability they need,
so the plugin's `Failed` state is a real code path, not decoration.

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
import org.dhis2.pluginimplementationtest.plugin.generated.resources.plugin_loading
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource

Text(stringResource(Res.string.plugin_loading))
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

1. In the SDK repo: `./gradlew :core:publishToMavenLocal`. A plugin now compiles against
   `org.hisp.dhis:android-core`, so the scoped-access build has to be in Maven Local before
   anything here resolves.
2. In the host repo: `./gradlew :plugin-sdk:publishToMavenLocal :plugin-sdk-gradle:publishToMavenLocal`.
   Both, always: the `id("org.dhis2.mobile.plugin-bundle")` line resolves from Maven Local and is
   what pulls in the matching `plugin-sdk` *and* the matching `android-core`. A stale
   `plugin-sdk-gradle` there is invisible from this side and surfaces as an unrelated
   dependency-resolution error in this project.
3. `./gradlew :plugin:buildPluginBundle` here. `plugin-config.json` beside the bundle is the
   dataStore entry with `version`, `checksum`, `id` and `entryPoint` already filled in — the last
   two come from `pluginBundle { }` in `plugin/build.gradle.kts`. Only `downloadUrl` and the
   `scope` block are left as placeholders.
4. `cd plugin/build/outputs/plugin-bundle && python3 -m http.server 8081`.
   Not 8080: a local DHIS2 instance usually owns it and answers with its login redirect
   instead of the bundle, which reads on device as the plugin silently not loading.
5. Post that JSON to the DHIS2 server dataStore (`dhis2AndroidPlugins/config`) — POST creates the
   key, PUT updates it afterwards. It points the app at
   `http://10.0.2.2:8081/plugin-{version}.zip` (the bundle is named from the Gradle module, not
   from the config's `id`). The dataStore is the only source of plugin config; there is no in-app
   fallback. The `scope` block has to grant `IpHINAT79UW` and `READ_TRACKED_ENTITY`, or
   `MyPlugin`'s card renders empty instead of TEIs.
6. Rebuild + install the `dhis2Debug` variant of the Capture App; log in.

For UI-only previews without the Capture App: `./gradlew :app:installDebug`.

## Entry-point contract

`MyPlugin` must:

- Implement `org.dhis2.mobile.plugin.sdk.Dhis2Plugin`.
- Live in `src/androidMain`, at the FQCN the dataStore config names as `entryPoint`.
- Have a public no-arg constructor — the host instantiates via reflection.

The plugin declares no id, version or scope of its own; all of it lives in the server config.

## Backlog

- Publish a `plugin-sdk-test` artefact. The old `StubDhis2PluginContext` is gone and cannot come
  back as-is: `ScopedD2` has an internal constructor, so a fake context is not constructible. Either
  the SDK exposes a test-visible constructor or the artefact ships an in-memory `D2`. Until then,
  keep UI in `commonMain` taking plain data (as this sample now does) and exercise the fetch path
  only in the Capture App.
- Add a `jvm("desktop")` target and a `desktop/plugin.jar` bundle subdir once
  a Desktop host exists. Note the plugin API is Android-only today.
- Per-publisher cert allow-list in the Capture App's `PluginVerifier`.
