# CLAUDE.md — DHIS2 Android plugin sample

Produces a **signed zip bundle** plugin for the DHIS2 Android Capture App. This repo is
self-contained: everything needed to build, test and package a plugin is here, and nothing refers to
another checkout.

## Start here

1. **Specs live in `specs/`.** One file per feature, Given/When/Then. `specs/README.md` defines the
   format; `specs/example-program-summary.md` is a complete worked example describing the plugin in
   this repo today.
2. **Build a feature from a spec** with `/plugin-from-spec specs/<file>.md`. It restates the spec and
   stops for approval before writing code, then goes red → green → verified.
3. **`./verify.sh` is the definition of done.** Unit tests, signed bundle, a check that the bundle
   carries nothing the host owns, and the ready-to-post dataStore config. `--cold` additionally
   proves the project builds on a machine that has never seen it.
4. **Work happens on a branch, never on the default one.** The pipeline cuts `spec/<slug>` from the
   spec's filename before it edits anything, and commits the implementation only after you have
   reviewed and tried the result — including the device checklist, which is the half no test covers.
   The PR it opens is a draft, so that checklist is evidence a reviewer sees rather than takes on
   trust.
5. **Answering its questions changes the spec, not just the chat.** Whatever gets settled at the
   approval gate is folded back into `specs/<file>.md` and committed first, on its own. The test is
   that re-running the pipeline on the committed spec asks nothing new — a spec that only works
   alongside the conversation that produced it is not finished.

What no automated check here can cover: any read or write against DHIS2. A JVM unit test cannot
construct a `D2` — it needs an Android `Context`, a database and an HTTP stack — so those live under
`## Device scenarios` in a spec and are walked by hand. Keeping SDK access behind `PluginRepository`
is what keeps everything else automatable.

To walk them quickly, `./gradlew :app:installDebug` runs the plugin against a real server with real
data — see *Development harness* below, including the shorter list of things only the Capture App
can tell you.

## Layout

```
dhis2-android-plugin-sample/
├── specs/    # Feature specifications — the input to /plugin-from-spec
├── verify.sh # The definition of done
├── vendor/   # Vendored plugin artefacts so this repo builds standalone (temporary —
│             # see vendor/maven/README.md for how to remove it)
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
        ├── ProgramOverviewPlugin.kt   # entry point: provideKoinModule + content, nothing else
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

1. Put it in `commonMain` unless it needs a platform API. In practice only `ProgramOverviewPlugin` and
   `D2PluginRepository` belong in `androidMain`, because `D2` is the Android SDK.
2. Composables take plain data and callbacks — never a `Dhis2PluginContext`. That is what lets
   `@Preview` render the real UI without a server. The harness no longer needs this — it builds a
   real context against a real `D2` (see *Development harness*) — but a `@Preview` still does, and
   it is the faster loop for pure UI work.
3. **Stay short.** The host renders the slot in a non-scrolling `Column` above its own program list,
   so height taken here is height taken from the host and anything past the viewport is unreachable.
   `PluginCard` caps itself with `heightIn(max = …)` + `verticalScroll`.
4. A repository returns `Result`, never throws. An exception escaping into the host composition takes
   the enclosing screen with it, and Compose cannot express an error boundary around a composable
   call. This means catching `Throwable`, not just `D2Error` — see `io()`. A repository that only
   catches the SDK's own error type still lets an unexpected null while mapping a result reach the
   host.
5. **Never label a person with an arbitrary attribute.** A tracked entity's attribute values come
   back in no particular order, so reading "the first one with a value" produces a row labelled
   *Female*. Resolve the label from the attributes the **program** marks `displayInList`, in their
   configured sort order — the same ones the app itself lists a tracked entity under:

   ```kotlin
   d2.programModule().programTrackedEntityAttributes()
       .byProgram().eq(programUid)
       .byDisplayInList().isTrue
       .orderBySortOrder(RepositoryScope.OrderByDirection.ASC)
   ```

   Fall back to something a human recognises — an org unit name — never to a UID.
6. **When the card shows N of many, count in SQL and enrich only the N.** `blockingCount()` is a
   `COUNT(*)`; `blockingGet()` materialises rows. The cost is rarely the events themselves but what
   resolving each one drags in: an enrollment, a tracked entity *with attribute values*, an org unit.
   Order and cap **before** any of that, or a program with hundreds of overdue events reads hundreds
   of records to render three. `ProgramSummary` and `OverdueSummary` both carry a total beside a
   capped list for this reason.

   Note the SDK has no synchronous row limit — `blockingGet`, `blockingCount`, and a LiveData-based
   `getPaged` — so `take(n)` after a `blockingGet` is as good as it gets for the rows. Capping before
   *enrichment* is where the win actually is.
7. **Enforce a display budget where a test can reach it.** Capping in the repository is an
   efficiency measure — it avoids resolving rows that will never be shown — and it lives in
   `androidMain` behind `D2`, which no unit test can construct. The cap that keeps the card inside
   its height budget is a *different* promise, made to a host whose column does not scroll, and it
   belongs in the ViewModel where a fake repository can hand it too many rows and a test can watch
   what happens. Share the limit from `commonMain` and apply it in both places; the duplication is
   the point, not an oversight.

   Generally: when a rule matters for correctness and its only enforcement sits in `androidMain`, it
   is not enforced, it is hoped for.
8. `D2Error` carries no `message`. It is `data class D2Error(…) : Exception()` and passes nothing to
   the `Exception` constructor, so `Throwable.message` is **always null** — read `errorCode()` and
   `errorDescription()`, or every failure renders as the bare word "D2Error".

## Commands

```bash
./verify.sh                            # tests + bundle + checks — the definition of done
./verify.sh --cold                     # same, from an empty Gradle home and local Maven repo
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

## Design system

A plugin should look like the app it renders inside. The Capture App carries
`org.hisp.dhis.mobile:designsystem` on its runtime classpath, so declare it **`compileOnly`** and the
real components arrive from the host's class loader — the same arrangement as Compose, for the same
reason (rule 1 below).

- Guide: <https://developers.dhis2.org/docs/mobile/mobile-ui/overview>
- API reference: <https://dhis2.github.io/dhis2-mobile-ui/api/-mobile%20-u-i/org.hisp.dhis.mobile.ui.designsystem.component/index.html>

Declared in `commonMain` — it is a Compose Multiplatform library, so it belongs beside the other
`compose.*` entries rather than in `androidMain`:

```kotlin
compileOnly("org.hisp.dhis.mobile:designsystem:<the version the host ships>")
```

It resolves from the repositories already in `settings.gradle.kts` (the snapshots repo is what
serves it), and Gradle selects the `-android` variant automatically. Unlike `plugin-sdk` and
`android-core`, the bundle plugin does **not** pin this version for you — matching it to the host is
the author's job, which is what makes the skew warning below worth reading.

Consult the API reference when choosing a component rather than reaching for Material 3 directly —
it documents what exists (`Button`, `InputDateTime`, `InfoBar`, `ButtonStyle`, and the rest of
`org.hisp.dhis.mobile.ui.designsystem.component`).

Same skew caution as rule 3: the design system is on a snapshot, so a plugin compiled against an
older copy than the host ships can still meet `NoSuchMethodError` at composition. Prefer components
without defaulted parameters where there is a choice.

## Resources

```
plugin/src/commonMain/composeResources/
├── values/strings.xml           # default (English)
├── values-es/strings.xml        # Spanish (add more as values-{locale}/)
└── drawable/plugin_icon.xml
```

Access from code:

```kotlin
import org.dhis2.mobile.plugin.sample.plugin.generated.resources.Res
import org.dhis2.mobile.plugin.sample.plugin.generated.resources.plugin_title
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

## Development harness

`:app` signs in to a real DHIS2 and renders the real plugin against real data — no hand-written
samples. Configure it in `local.properties`, which is gitignored and never committed:

```properties
dhis2.serverUrl=http://10.0.2.2:8080     # 10.0.2.2 is the host machine, from an emulator
dhis2.username=admin
dhis2.password=district
dhis2.programUid=                        # optional; blank picks the first tracker programme
```

Then `./gradlew :app:installDebug`. On first run it instantiates `D2`, logs in, downloads metadata
and then **tracker data** — metadata alone brings programmes and stages but no enrolments, and a
plugin rendering real structure over zero rows looks like a plugin bug. That first run takes minutes;
afterwards the database is on the device and startup is immediate. Every step is named on screen, so
a slow run is distinguishable from a stuck one.

It renders `MyPlugin.content()` itself, not just `PluginCard`, by reproducing the host's private Koin
container (`PluginHost`) — a harness whose DI differs from the host's proves the wrong thing.

**What it cannot tell you.** It is not the Capture App, and these need the real host:

- the non-scrolling slot and the height budget
- the class-loader reload and its `ClassCastException`
- Compose resource resolution through `FileSystemResourceReader`
- that plugin bindings cannot leak into the host's container
- androidx Compose version skew — `NoSuchMethodError` reproduces only against the host's versions

So the harness shrinks the device checklist; it does not empty it.

**Use a development server.** The harness writes as well as reads.

## Local testing flow

1. Nothing to publish first — the plugin API and its Gradle plugin are vendored under
   `vendor/maven/`, so this project configures and builds on its own. If a build failure looks like
   a stale plugin API (a method that should exist but does not), read `vendor/maven/README.md`
   before assuming your code is wrong.
2. `./verify.sh`, or `./gradlew :plugin:buildPluginBundle` directly. `plugin-config.json` beside the bundle is
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
5. Install the Capture App (`dhis2Debug` variant) and log in. Plugins load when the home screen
   opens.

For UI-only previews without the Capture App: `./gradlew :app:installDebug`.

## Entry-point contract

`ProgramOverviewPlugin` must:

- Implement `org.dhis2.mobile.plugin.sdk.Dhis2Plugin`.
- Live in `src/androidMain`, because `Dhis2PluginContext.sdk` is `D2` — the DHIS2 *Android* SDK.
- Live at the FQCN the dataStore config names as `entryPoint`. The plugin declares none of it.
- Have a public no-arg constructor — the host instantiates via reflection.

## Backlog

- **Publish `plugin-sdk` and `plugin-sdk-gradle` to a real repository, then delete `vendor/`.**
  They are committed binaries with no upstream: when the plugin API changes, code here compiles
  against the stale copy and fails on device with `NoSuchMethodError` or `ClassCastException`.
  `vendor/maven/README.md` has the removal steps.
- Extract `buildPluginBundle` into a published Gradle plugin.
- Publish a `plugin-sdk-test` artefact. A *unit test* still has no way to construct a `D2` — it needs
  an Android `Context`, a database and an HTTP stack — so `commonTest` remains fake-repository
  territory. What changed is that an application module can: see `HarnessPluginContext`. An
  instrumented test in this repo could now drive a real `D2` against a test server, which is the
  route to shrinking the `## Device scenarios` half of every spec.
- Narrow the plugin's SDK access. This iteration hands over `D2` unrestricted; the next one restricts
  it to a server-declared subset, enforced inside the SDK rather than by the host.
- Have the plugin-bundle Gradle plugin pin the host's androidx Compose version the way it already
  pins `plugin-sdk` and `android-core`, so rule 3 stops being a manual concern. Note the sub-groups
  do not share one version line (`material3` is on 1.4.x while `ui`/`foundation` are on 1.10.x), so a
  group-wide force is wrong.
- Add a `jvm("desktop")` target and a `desktop/plugin.jar` bundle subdir once
  a Desktop host exists.
- Per-publisher cert allow-list in the Capture App's `PluginVerifier`.
