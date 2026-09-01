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
    ├── src/commonMain/kotlin/…/
    │   ├── model/        # ProgramSummary — plain data, no SDK types
    │   ├── repository/   # PluginRepository interface
    │   └── ui/           # PluginUiState, PluginViewModel, PluginCard
    ├── src/commonTest/   # Unit tests — run on the JVM, no device
    └── src/androidMain/kotlin/…/
        ├── MyPlugin.kt   # entry point: provideKoinModule + content, nothing else
        └── data/         # D2PluginRepository — the only file that sees the SDK
```

Only `:plugin`'s output is shipped. `:app` is not.

## Architecture

Three layers, and no more than three. The Capture App itself also has a use-case layer; this project
deliberately does not — a plugin is small enough that a use case per action would be a file that only
forwards a call, and the point of this sample is to be read start to finish by someone who has never
seen it.

```
UiState  ←  ViewModel  ←  PluginRepository (interface)  ←  D2PluginRepository
commonMain   commonMain      commonMain                        androidMain
```

- **UiState** — a `sealed interface` per concern, holding plain data. No SDK types.
- **ViewModel** — exposes `StateFlow<PluginUiState>`, calls the repository, maps failures into
  state. Never touches `D2`.
- **Repository interface** — the plugin's own vocabulary, returning `Result` of plain models.
- **D2PluginRepository** — the *only* place `D2` appears. Moves blocking calls off the main thread
  and translates `D2Error` into a message worth showing.

**Why the interface earns its keep.** It is the seam that lets the ViewModel and UI be unit-tested on
the JVM against a fake. It also keeps the SDK surface in one file, which matters because the next
iteration of this PoC narrows that access — and one file is a far smaller thing to change than a
plugin scattered with `d2.` calls.

**Rules.**

1. Put it in `commonMain` unless it needs a platform API. In practice only `MyPlugin` and
   `D2PluginRepository` belong in `androidMain`, because `D2` is the Android SDK.
2. Composables take plain data and callbacks — never a `Dhis2PluginContext`. That is what lets
   `@Preview` and the harness render the real UI. Note the harness *cannot* fake a context any more:
   `sdk` is `D2` and there is no way to construct one.
3. **Stay short.** The host renders the slot in a non-scrolling `Column` above its own program list,
   so height taken here is height taken from the host and anything past the viewport is unreachable.
   `PluginCard` caps itself with `heightIn(max = …)` + `verticalScroll`.
4. A repository returns `Result`, never throws. An exception escaping into the host composition takes
   the enclosing screen with it, and Compose cannot express an error boundary around a composable
   call.
5. `D2Error` carries no `message`. It is `data class D2Error(…) : Exception()` and passes nothing to
   the `Exception` constructor, so `Throwable.message` is **always null** — read `errorCode()` and
   `errorDescription()`, or every failure renders as the bare word "D2Error".

## Commands

```bash
./gradlew :plugin:buildPluginBundle    # signed zip → plugin/build/outputs/plugin-bundle/
./gradlew :plugin:testAndroidHostTest  # unit tests (commonTest, JVM — no device)
./gradlew :app:installDebug            # preview harness on emulator
```

## Rules (read before editing `plugin/build.gradle.kts`)

1. **`compileOnly` everything host-provided, except `compose.components.resources`.**
   The Capture App provides Compose/Material3/plugin-sdk at runtime via
   `InMemoryDexClassLoader`'s parent delegation — bundling them causes DEX bloat
   and `ClassCastException`. **But** `compose.components.resources` must be
   `implementation` — it's the CMP plugin's opt-in signal to generate the `Res`
   accessor class. Swap it to `compileOnly` and `Res.*` imports stop resolving.
2. **The plugin compiles against the DHIS2 SDK, and `settings.gradle.kts` needs two extra
   repositories for it.** `Dhis2PluginContext.sdk` is `D2`, so the plugin-bundle Gradle plugin
   injects `org.hisp.dhis:android-core` at the host's version — never declare it yourself. It pulls
   `com.github.dhis2:sms-compression` from JitPack, and the host usually tracks SDK snapshots, so
   both JitPack and the snapshots repo must be in `dependencyResolutionManagement`. Without them the
   build fails at dependency *resolution*, with an error that never mentions the DHIS2 SDK.
3. **Matching `composeMultiplatform` is not enough.** The host declares CMP *and* androidx `compose`
   separately, depending on the latter directly and at a higher version, while CMP brings
   `foundation-layout` transitively at a lower one. Almost everything is identical, which is the
   trap: the first casualty is a *defaulted* overload whose `…$default` synthetic changed.
   `Modifier.weight(1f)` crashed the host with `NoSuchMethodError: weight$default` at composition.
   Prefer layout APIs without default arguments, and note `compose.foundation` is not even declared
   here — it arrives transitively, so its version floats.
4. **Use `kotlin.multiplatform` + `com.android.kotlin.multiplatform.library`,
   not `com.android.library`.** AGP 9 disallows mixing plain Android library
   with KMP.
5. **Set `compose.resources { packageOfResClass = "…" }` explicitly.** Without
   it CMP derives the package from the root project name (which has spaces →
   backtick-escaped imports).
6. **The plugin declares no identity.** Its id, version, entry point and injection points all live
   in the server dataStore config. `pluginBundle { pluginId; entryPoint }` only fills in the
   generated `plugin-config.json` for convenience — it reaches neither the bundle nor the host.
7. **Bump `pluginVersion` to invalidate the device cache.** The Capture App
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

1. `./gradlew :plugin-sdk:publishToMavenLocal :plugin-sdk-gradle:publishToMavenLocal`
   (host repo). Both, always: the `id("org.dhis2.mobile.plugin-bundle")` line resolves
   from Maven Local and is what pulls in the matching `plugin-sdk`. A stale
   `plugin-sdk-gradle` there is invisible from this side and surfaces as an unrelated
   dependency-resolution error in this project.
2. `./gradlew :plugin:buildPluginBundle` here. `plugin-config.json` beside the bundle is
   the dataStore entry with `version`, `checksum`, `id` and `entryPoint` already filled
   in — the last two come from `pluginBundle { }` in `plugin/build.gradle.kts`.
3. `cd plugin/build/outputs/plugin-bundle && python3 -m http.server 8081`.
   Not 8080: a local DHIS2 instance usually owns it and answers with its login redirect
   instead of the bundle, which reads on device as the plugin silently not loading.
4. Post that JSON to the DHIS2 server dataStore (`dhis2AndroidPlugins/config`) — POST
   creates the key, PUT updates it afterwards. It points the app at
   `http://10.0.2.2:8081/plugin-{version}.zip` (the bundle is named from the Gradle
   module, not from the config's `id`). The dataStore is the only source of plugin
   config; there is no in-app fallback. There is no data-scope field to set — the plugin gets the
   SDK unrestricted, so the config only names *which* code to run.
5. Rebuild + install `dhis2Debug` variant of the Capture App; log in.

For UI-only previews without the Capture App: `./gradlew :app:installDebug`.

## Entry-point contract

`MyPlugin` must:

- Implement `org.dhis2.mobile.plugin.sdk.Dhis2Plugin`.
- Live in `src/androidMain`, because `Dhis2PluginContext.sdk` is `D2` — the DHIS2 *Android* SDK.
- Live at the FQCN the dataStore config names as `entryPoint`. The plugin declares none of it.
- Have a public no-arg constructor — the host instantiates via reflection.

## Backlog

- Extract `buildPluginBundle` into a published Gradle plugin.
- Publish a `plugin-sdk-test` artefact. `StubDhis2PluginContext` is gone and cannot come back as-is:
  `sdk` is `D2`, which a test has no way to construct. Until then, keep UI in `commonMain` taking
  plain data (as this sample now does) and exercise the fetch path only in the Capture App.
- Narrow the plugin's SDK access. This iteration hands over `D2` unrestricted; the next one restricts
  it to a server-declared subset, enforced inside the SDK rather than by the host.
- Have the plugin-bundle Gradle plugin pin the host's androidx Compose version the way it already
  pins `plugin-sdk` and `android-core`, so rule 3 stops being a manual concern. Note the sub-groups
  do not share one version line (`material3` is on 1.4.x while `ui`/`foundation` are on 1.10.x), so a
  group-wide force is wrong.
- Add a `jvm("desktop")` target and a `desktop/plugin.jar` bundle subdir once
  a Desktop host exists.
- Per-publisher cert allow-list in the Capture App's `PluginVerifier`.
