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
    ├── src/commonMain/kotlin/…/
    │   ├── model/        # ScopeSnapshot, ProgramSummary, DataSetSummary, SearchProbe
    │   │                 # — plain data, no SDK types
    │   ├── repository/   # PluginRepository interface + ScopeViolation
    │   └── ui/           # PluginUiState, PluginViewModel, PluginCard
    ├── src/commonTest/   # Unit tests — run on the JVM, no device
    └── src/androidMain/kotlin/…/
        ├── MyPlugin.kt   # entry point: provideKoinModule + content, nothing else
        └── data/         # ScopedPluginRepository — the only file that sees ScopedD2
```

Only `:plugin`'s output is shipped. `:app` is not.

**Why the split.** `Dhis2PluginContext.sdk` is a `ScopedD2` — the DHIS2 *Android* SDK — so the
`Dhis2Plugin` implementation cannot live in `commonMain`. Everything it renders is kept in
`commonMain` as plain data plus a Composable that takes it, which is what lets the harness and
`@Preview` render the real UI without a context.

## Commands

```bash
./gradlew :plugin:buildPluginBundle    # signed zip → plugin/build/outputs/plugin-bundle/
./gradlew :plugin:testAndroidHostTest  # unit tests (commonTest, JVM — no device)
./gradlew :app:installDebug            # preview harness on emulator
```

## Architecture

Three layers, and no more than three. The Capture App itself also has a use-case layer; this project
deliberately does not — a plugin is small enough that a use case per action would be a file that only
forwards a call, and the point of this sample is to be read start to finish by someone who has never
seen it.

```
UiState  ←  ViewModel  ←  Repository (interface)  ←  RepositoryImpl (ScopedD2)
commonMain   commonMain      commonMain                  androidMain
```

- **UiState** — a `sealed interface` per screen area, holding plain data. No SDK types.
- **ViewModel** — exposes `StateFlow<UiState>`, calls the repository, maps failures into state.
  Never touches `ScopedD2`.
- **Repository interface** — the plugin's own vocabulary (`fun programsInScope(): List<ProgramItem>`),
  returning `Result` of plain models.
- **RepositoryImpl** — the *only* place `ScopedD2` appears. Translates SDK types to plain models and
  `D2Error` into domain failures.

**Why the interface earns its keep here specifically.** `ScopedD2` is
`class ScopedD2 internal constructor(…)` — final, and not constructible outside the SDK. A test
cannot build one, and the accessors return more final SDK repositories, so mocking the chain is
worse than useless. The repository interface is therefore the only seam that makes the plugin
testable at all, and it is why everything above it lives in `commonMain`.

**Rules.**

1. Put it in `commonMain` unless it needs a platform API. In practice only `MyPlugin` and the
   repository implementations belong in `androidMain`.
2. Composables take plain data and callbacks — never a `Dhis2PluginContext`. That is what lets
   `@Preview` and the harness render the real UI.
4. **Stay short.** The host renders the slot in a non-scrolling `Column` above its own program list,
   so height taken here is height taken from the host and anything past the viewport is unreachable.
   `PluginCard` keeps its resting state to a handful of lines, hides detail behind two toggles, and
   caps itself with `heightIn(max = 360.dp)` + `verticalScroll`. Scope *warnings* are the one thing
   never hidden by a collapsed section.
4. A repository returns `Result`, never throws. `D2Error(SCOPE_VIOLATION)` is an expected outcome to
   render, not an exception to propagate — a throw escaping into the host composition takes the
   enclosing screen with it, and Compose cannot express an error boundary around a composable call.
5. `D2Error` carries no `message`. It is `data class D2Error(…) : Exception()` and passes nothing to
   the `Exception` constructor, so `Throwable.message` is **always null** — read `errorCode()` and
   `errorDescription()` instead, or every scope violation renders as the bare word "D2Error".

## Dependency injection

The host builds a **private Koin container per plugin** and seeds it with the plugin's own
`ScopedD2`, its `PluginMetadata` and its `Dhis2PluginContext`. Nothing host-owned is reachable —
`get<D2>()` does not resolve. So a module is just:

```kotlin
class MyPlugin : Dhis2Plugin {
    override fun provideKoinModule() = module {
        single<ScopeRepository> { ScopeRepositoryImpl(get()) }   // get() = the plugin's ScopedD2
        viewModel { ScopeViewModel(get()) }
    }

    @Composable
    override fun content(context: Dhis2PluginContext) {
        val viewModel: ScopeViewModel = koinViewModel()
        ScopeCard(state = viewModel.state.collectAsState().value)
    }
}
```

Koin and `lifecycle-viewmodel-compose` are **`compileOnly`** — see rule 1 under *Rules* below. They
come from the host at runtime; a second copy in the plugin DEX would not share the host's
`KoinIsolatedContext`, and the bundle build fails outright if `org/koin/` is packaged.

`koinViewModel()` is safe across a plugin reload. The host's `ViewModelStore` outlives the plugin's
composition and keys by class name, which is identical across two class loaders — but
`ViewModelProviderImpl` type-checks the cached instance with `KClass.isInstance`, creates a fresh one
on mismatch, and `ViewModelStore.put` clears the one it displaces. No explicit `key` is needed.

## Testing

`./gradlew :plugin:testAndroidHostTest` runs `commonTest` on the JVM. The
`android { withHostTestBuilder {}.configure {} }` line in `plugin/build.gradle.kts` is what registers
that task — without it AGP's KMP library plugin creates no test task and `commonTest` is silently
never compiled.

- **Unit** — ViewModels against a hand-written fake repository, and any pure logic. Turbine for
  `StateFlow`. Prefer fakes over mocks for the repository: it is your own interface, so a fake is
  shorter and reads better than stubbing.
- **DI** — resolve the plugin's module in a real Koin container so a missing binding fails a test
  rather than the device.
- **UI** — Compose tests in `app/src/androidTest`, rendering the plugin's real Composables through
  the harness.

Not possible yet: anything exercising a real `ScopedD2`. That needs the `plugin-sdk-test` artefact in
*Backlog*. Until then the repository implementations in `androidMain` are only covered on device, and
that boundary is exactly why they are kept as thin as possible.

## Rules (read before editing `plugin/build.gradle.kts` or `settings.gradle.kts`)

1. **`compileOnly` everything host-provided, except `compose.components.resources`.**
   The Capture App provides Compose/Material3/plugin-sdk/the DHIS2 SDK at runtime via
   `InMemoryDexClassLoader`'s parent delegation — bundling them causes DEX bloat
   and `ClassCastException`. **But** `compose.components.resources` must be
   `implementation` — it's the CMP plugin's opt-in signal to generate the `Res`
   accessor class. Swap it to `compileOnly` and `Res.*` imports stop resolving.
   `plugin-sdk` and `org.hisp.dhis:android-core` are added automatically by the
   `org.dhis2.mobile.plugin-bundle` plugin, at the host's versions — never declare them.
2. **Matching `composeMultiplatform` is not enough.** The host declares CMP `1.10.3` *and* androidx
   `compose = 1.10.6` separately, depending on the latter directly. CMP 1.10.3 brings
   `foundation-layout:1.10.5`, so the plugin compiles against 1.10.5 and runs against 1.10.6. Almost
   everything is identical, which is the trap: the first casualty is a *defaulted* overload whose
   `…$default` synthetic changed. `Modifier.weight(1f)` crashed the host with
   `NoSuchMethodError: weight$default` at composition. Prefer layout APIs without default arguments,
   and note `compose.foundation` is not even declared here — it arrives transitively, so its version
   floats.
3. **`settings.gradle.kts` needs JitPack and the snapshots repo.** Compiling against the DHIS2 SDK
   pulls `com.github.dhis2:sms-compression` from JitPack. Without it the build fails at dependency
   *resolution*, with an error that does not mention the SDK.
4. **Use `kotlin.multiplatform` + `com.android.kotlin.multiplatform.library`,
   not `com.android.library`.** AGP 9 disallows mixing plain Android library
   with KMP.
5. **Set `compose.resources { packageOfResClass = "…" }` explicitly.** Without
   it CMP derives the package from the root project name (which has spaces →
   backtick-escaped imports).
6. **Bump `version` to invalidate the device cache.** The Capture App
   caches by `{id}-{version}.zip`; rebuilding at the same version reuses the
   old cache. Symptom: "my code changes aren't showing." The bundle is otherwise
   reproducible, so an unchanged plugin keeps its SHA-256 and the dataStore
   config does not need re-editing.

## Data access

`context.sdk` is the DHIS2 SDK restricted to the scope the server granted. Repositories arrive
pre-filtered, and because SDK filters only ever accumulate, anything you add can only narrow
further — an out-of-scope query returns nothing rather than throwing.

```kotlin
val recent = sdk.trackedEntityInstances()
    .byProgramUids(listOf(programUid))
    .withTrackedEntityAttributeValues()
    .orderByCreated(RepositoryScope.OrderByDirection.DESC)
    .blockingGet()
```

**Two independent halves.** The tracker half (programs → TEIs, enrollments, events, search) and the
aggregate half (data sets → data elements → data values) are separate grants enforced by separate
mechanisms, so the plugin exercises both. A grant can expose data sets and no programs, or the
reverse; neither selection depends on the other. Note the aggregate indirection: `DataValue` has no
data set column, so the SDK resolves granted data **sets** to the data **elements** they contain and
the write guard checks the *element* and org unit — never the data set the administrator named.

**Nothing is hardcoded.** `loadScope()` reads `metadata.effectiveScope` for what the config *declared*
and asks the SDK what is actually *visible* under it, and the card shows both columns. That contrast
is the whole diagnostic: an out-of-scope read returns empty rather than failing, so a mistyped UID
and an empty database are indistinguishable without it. Everything else — the summary, the write
test, the probes — runs against whichever program the reader picks from the visible list.

`blocking*` calls must run off the main thread. `ScopedPluginRepository.io { }` is the single place
that happens, and it does one other job: it translates `D2Error(SCOPE_VIOLATION)` into the plugin's
own `ScopeViolation`. That translation is why the ViewModel can tell "refused" from "broken" without
importing the DHIS2 SDK — and so why it can live in `commonMain` and be unit-tested.

Accessors throw `D2Error(SCOPE_VIOLATION)` when the granted scope lacks the capability they need, so
the `Failed` and `Refused` states are real code paths, not decoration.

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
   fallback. `READ_METADATA` alone is enough to see something: the card lists what the grant
   exposes, so a config that grants nothing is visibly a config that grants nothing rather than a
   blank card.
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
